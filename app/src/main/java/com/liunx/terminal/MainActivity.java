package com.liunx.terminal;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

  private static final int PERMISSION_REQ = 1001;
  private static final int MAX_LINE_COUNT = 5000;    //最大行数
  private static final long UPDATE_DELAY_MS = 50;     //刷新合并窗口

  private EditText input;
  private TextView output;
  private ScrollView scroll;
  private ShellManager shell;

  //刷新节流
  private final StringBuilder displayBuffer = new StringBuilder();
  private final Handler uiHandler = new Handler(Looper.getMainLooper());
  private Runnable uiUpdateRunnable;

  @Override
  protected void onCreate(Bundle s) {
    super.onCreate(s);
    setContentView(R.layout.activity_main);

    input = findViewById(R.id.input_cmd);
    output = findViewById(R.id.output_text);
    scroll = findViewById(R.id.scroll_output);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      input.setShowSoftInputOnFocus(true);
    }

    if (hasStoragePermissions()) {
      initTerminal();
    } else {
      requestStoragePermissions();
    }

    //输入监听
    input.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND
          || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
        String cmd = input.getText().toString();
        if (!cmd.isEmpty() && shell != null) {
          shell.writeCommand(cmd);
          input.setText("");
        }
        input.postDelayed(this::showKeyboard, 50);
        return true;
      }
      return false;
    });

    showKeyboard();
  }

  private void showKeyboard() {
    input.requestFocus();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
    }
  }

  private boolean hasStoragePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return true;
    }
    return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
  }

  private void requestStoragePermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[]{
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
      }, PERMISSION_REQ);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == PERMISSION_REQ) {
      initTerminal();
    }
  }

  private void initTerminal() {
    shell = new ShellManager(this);
    shell.setOutputListener(new ShellManager.OutputListener() {
      @Override
      public void onNewOutput(String text) {
        //累积文本
        synchronized (displayBuffer) {
          displayBuffer.append(text);
        }
        //取消旧任务,调度新刷新
        if (uiUpdateRunnable != null) {
          uiHandler.removeCallbacks(uiUpdateRunnable);
        }
        uiUpdateRunnable = () -> {
          String chunk;
          synchronized (displayBuffer) {
            chunk = displayBuffer.toString();
            displayBuffer.setLength(0);
          }
          //更新文本
          output.append(chunk);

          //限制最大行数
          Editable editable = output.getEditableText();
          int lineCount = output.getLineCount();
          if (lineCount > MAX_LINE_COUNT) {
            int excess = lineCount - MAX_LINE_COUNT;
            int end = 0;
            for (int i = 0; i < excess; i++) {
              int next = editable.toString().indexOf('\n', end);
              if (next != -1) end = next + 1;
              else break;
            }
            if (end > 0) {
              editable.delete(0, end);
            }
          }

          //滚动到底部
          scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        };
        uiHandler.postDelayed(uiUpdateRunnable, UPDATE_DELAY_MS);
      }

      @Override
      public void onTerminalExit() {
        //进程退出时立即刷新剩余内容
        if (uiUpdateRunnable != null) {
          uiHandler.removeCallbacks(uiUpdateRunnable);
          uiUpdateRunnable.run();
          uiUpdateRunnable = null;
        }
        runOnUiThread(() -> finish());
      }
    });
    shell.start();
  }

  @Override
  protected void onResume() {
    super.onResume();
    showKeyboard();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (uiHandler != null && uiUpdateRunnable != null) {
      uiHandler.removeCallbacks(uiUpdateRunnable);
    }
    if (shell != null) shell.destroy();
  }
}