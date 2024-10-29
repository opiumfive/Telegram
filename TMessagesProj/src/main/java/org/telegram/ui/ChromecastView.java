package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.mediarouter.app.MediaRouteButton;

@SuppressLint("ViewConstructor")
public class ChromecastView extends MediaRouteButton {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean selfTouch;

    public ChromecastView(@NonNull Context context, int color, boolean selfTouch) {
        super(context);
        this.selfTouch = selfTouch;
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        paint.setColor(color);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), null, Canvas.ALL_SAVE_FLAG);
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (selfTouch) {
            return super.onTouchEvent(event);
        }
        return false;
    }
}