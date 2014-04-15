package com.bourke.floatingbar;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ProgressBar;

public class ProgressBarService extends Service {

    private WindowManager windowManager;
    private ProgressBar mProgressBar;

    private int mProgressStatus = 0;

    private final int HOLO_GREEN = Color.rgb(153, 204, 0);

    @Override public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mProgressBar = new ProgressBar(getBaseContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        mProgressBar.getProgressDrawable().setColorFilter(HOLO_GREEN, PorterDuff.Mode.SRC_IN);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.x = 0;
        params.y = 0;

        mProgressBar.setProgress(100);

        windowManager.addView(mProgressBar, params);
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (mProgressBar != null) windowManager.removeView(mProgressBar);
    }
}