package com.elishajkmiller.formulaheist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

@SuppressWarnings("deprecation")   // the immersive flags still work on every phone we target
public class Main extends Activity {

    private Game game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        installCrashLog();
        game = new Game(this);
        setContentView(game);
        showLastCrash();
    }

    /** a crash writes its stack trace to a file, and the next launch shows it, so it can be screenshotted */
    private void installCrashLog() {
        final File f = new File(getFilesDir(), "crash.txt");
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    sw.write("thread " + t.getName() + "\n");
                    e.printStackTrace(new PrintWriter(sw));
                    FileOutputStream o = new FileOutputStream(f);
                    o.write(sw.toString().getBytes("UTF-8"));
                    o.close();
                } catch (Throwable x) { }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    private void showLastCrash() {
        final File f = new File(getFilesDir(), "crash.txt");
        if (!f.exists()) return;
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 4000)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(b);
            in.close();
            TextView tv = new TextView(this);
            tv.setText(new String(b, 0, Math.max(0, n), "UTF-8"));
            tv.setTextSize(10f);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(24, 24, 24, 24);
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            new AlertDialog.Builder(this).setTitle("LAST CRASH").setView(sv)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { f.delete(); }
                    }).show();
        } catch (Throwable x) { f.delete(); }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.stop();
    }
}
