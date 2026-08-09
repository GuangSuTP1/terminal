package com.liunx.terminal;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ShellManager {

    public interface OutputListener {
        void onNewOutput(String text);
        void onTerminalExit();
    }

    private final Context context;
    private Process process;
    private PrintWriter writer;
    private Thread thread;
    private OutputListener listener;

    public ShellManager(Context c) { this.context = c; }
    public void setOutputListener(OutputListener l) { this.listener = l; }

    public void start() {
        try {
            File script = prepareScript();
            if (script == null) {
                if (listener != null) listener.onTerminalExit();
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(
                    script.getAbsolutePath(), "-q", "-c", "/system/bin/sh -i", "/dev/null");
            pb.environment().clear();
            pb.environment().put("PATH", "/system/bin");
            pb.environment().put("SHELL", "/system/bin/sh");
            pb.environment().put("TERM", "dumb");
            pb.redirectErrorStream(true);
            pb.directory(new File("/storage/emulated/0"));
            process = pb.start();
            writer = new PrintWriter(process.getOutputStream(), true);
            startReader(new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            if (listener != null) listener.onTerminalExit();
        }
    }

    private File prepareScript() {
        File f = new File(context.getCacheDir(), "script");
        try (InputStream in = context.getAssets().open("script");
             OutputStream out = new FileOutputStream(f, false)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            f.setExecutable(true);
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    private void startReader(BufferedReader r) {
        thread = new Thread(() -> {
            try {
                char[] buf = new char[4096];
                int len;
                while ((len = r.read(buf)) != -1 && listener != null)
                    listener.onNewOutput(new String(buf, 0, len));
            } catch (IOException ignored) {
            } finally {
                if (listener != null) listener.onTerminalExit();
                try { r.close(); } catch (IOException ignored) {}
            }
        });
        thread.start();
    }

    public void writeCommand(String cmd) {
        if (writer != null) {
            writer.println(cmd);
            writer.flush();
        }
    }

    public void writeCharacter(char c) {
        if (writer != null) {
            writer.print(c);
            writer.flush();
        }
    }

    public void destroy() {
        if (thread != null) thread.interrupt();
        if (process != null) process.destroy();
    }
}