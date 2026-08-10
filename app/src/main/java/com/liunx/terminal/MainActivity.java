package com.liunx.terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

  private static final int PERMISSION_REQ = 1001;
  private static final int MAX_LINE_COUNT = 5000;
  private static final long UPDATE_DELAY_MS = 50;

  private EditText input;
  private TextView output;
  private ScrollView scroll;
  private ShellManager shell;

  private final List<String> history = new ArrayList<>();
  private static final int MAX_HISTORY_SIZE = 50;

  private final StringBuilder displayBuffer = new StringBuilder();
  private final Handler uiHandler = new Handler(Looper.getMainLooper());
  private Runnable uiUpdateRunnable;

  @Override
  protected void onCreate(Bundle s) {
    super.onCreate(s);
    setContentView(R.layout.activity_main);

    input = findViewById(R.id.input_cmd);
    output = findViewById(R.id.output_text);
    output.setMinWidth(getResources().getDisplayMetrics().widthPixels);
    scroll = findViewById(R.id.scroll_output);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      input.setShowSoftInputOnFocus(true);
    }

    if (hasStoragePermissions()) {
      initTerminal();
    } else {
      requestStoragePermissions();
    }

    input.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND
          || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
              && event.getAction() == KeyEvent.ACTION_DOWN)) {
        String cmd = input.getText().toString();
        if (!cmd.isEmpty() && shell != null) {
          shell.writeCommand(cmd);
          addHistory(cmd);
          input.setText("");
        }
        input.postDelayed(this::showKeyboard, 50);
        return true;
      }
      return false;
    });

    //输出区域长按菜单
    View.OnLongClickListener outputLongClickListener = v -> {
      showControlMenu();
      return true;
    };
    scroll.setOnLongClickListener(outputLongClickListener);
    output.setOnLongClickListener(outputLongClickListener);

    //输入框长按弹出历史命令
    input.setOnLongClickListener(v -> {
      showHistoryDialog();
      return true;
    });

    showKeyboard();
  }

  private void addHistory(String cmd) {
    if (!history.isEmpty() && history.get(history.size() - 1).equals(cmd)) {
      return;
    }
    history.add(cmd);
    if (history.size() > MAX_HISTORY_SIZE) {
      history.remove(0);
    }
  }

  private void showHistoryDialog() {
    if (history.isEmpty()) {
      return;
    }
    String[] items = new String[history.size()];
    for (int i = 0; i < history.size(); i++) {
      items[i] = history.get(history.size() - 1 - i);
    }
    new AlertDialog.Builder(this)
        .setTitle("历史命令")
        .setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            int realIndex = history.size() - 1 - which;
            String cmd = history.get(realIndex);
            input.setText(cmd);
            input.setSelection(cmd.length());
            showKeyboard();
          }
        })
        .setNegativeButton("取消", null)
        .show();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
        showControlMenu();
        return true;  //电视端焦点切换不到控制台,所以用菜单键来弥补
      }
    }
    return super.dispatchKeyEvent(event);
  }

  private void showControlMenu() {
    final String[] items = {"Ctrl+C", "Ctrl+D"};
    new AlertDialog.Builder(this)
        .setTitle("菜单")
        .setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (shell == null) return;
            switch (which) {
              case 0: shell.writeCharacter((char) 0x03); break;
              case 1: shell.writeCharacter((char) 0x04); break;
            }
          }
        })
        .setNegativeButton("取消", null)
        .show();
  }

  private void showKeyboard() {
    input.requestFocus();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
    }
  }

  private boolean hasStoragePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
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
    if (requestCode == PERMISSION_REQ) initTerminal();
  }

  private void initTerminal() {
    shell = new ShellManager(this);
    shell.setOutputListener(new ShellManager.OutputListener() {
      @Override
      public void onNewOutput(String text) {
        synchronized (displayBuffer) {
          displayBuffer.append(text);
        }
        if (uiUpdateRunnable != null) uiHandler.removeCallbacks(uiUpdateRunnable);
        uiUpdateRunnable = () -> updateOutputUI();
        uiHandler.postDelayed(uiUpdateRunnable, UPDATE_DELAY_MS);
      }

      @Override
      public void onTerminalExit() {
        uiHandler.post(() -> {
          updateOutputUI();
          runOnUiThread(() -> finish());
        });
      }
    });
    shell.start();
  }

  private void updateOutputUI() {
    String chunk;
    synchronized (displayBuffer) {
      if (displayBuffer.length() == 0) return;
      chunk = displayBuffer.toString();
      displayBuffer.setLength(0);
    }
    output.append(chunk);
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
      if (end > 0) editable.delete(0, end);
    }
    scroll.post(() -> {
      scroll.fullScroll(ScrollView.FOCUS_DOWN);
      if (!input.hasFocus()) {
        input.requestFocus();
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    showKeyboard();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (uiHandler != null && uiUpdateRunnable != null) uiHandler.removeCallbacks(uiUpdateRunnable);
    if (shell != null) shell.destroy();
  }
}