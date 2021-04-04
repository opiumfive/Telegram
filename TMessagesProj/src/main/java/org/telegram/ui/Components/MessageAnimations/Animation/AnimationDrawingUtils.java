package org.telegram.ui.Components.MessageAnimations.Animation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;


public class AnimationDrawingUtils {

    public static final AnimationDrawingUtils instance = new AnimationDrawingUtils();
    private final Paint paint;
    private final RectF rectF = new RectF();
    private final Rect rect = new Rect();
    private Canvas canvas;

    public AnimationDrawingUtils() {
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    private void updateStroke(int color, float size) {
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(size);
        this.paint.setColor(color);
    }

    public void getTextSize(String text, Point size, Paint paint) {
        if (text != null) {
            rect.set(0, 0, 100, 100);
            paint.getTextBounds(text, 0, text.length(), rect);
            size.set(rect.width(), rect.height());
        }
    }

    public void drawRoundRectGradient(float x, float y, float right, float bottom, float rx, float ry, int startColor, int endColor, float grSize) {
        float step = 1;
        this.updateStroke(startColor, step);
        this.rectF.set(x, y, right, bottom);
        for (float i = 0; i < grSize; i += step) {
            int color = getColor(startColor, endColor, i / (grSize - step));
            this.paint.setColor(color);
            this.canvas.drawRoundRect(this.rectF, rx, ry, this.paint);
            this.rectF.inset(step, step);
        }
    }

    public void drawRoundRectGradient(Rect rect, float rx, float ry, int startColor, int endColor, float grSize) {
        drawRoundRectGradient(rect.left, rect.top, rect.right, rect.bottom, rx, ry, startColor, endColor, grSize);
    }

    public static int getColor(int start, int end, float f) {
        f = Math.max(Math.min(f, 1), 0);
        float f2 = 1.0f - f;
        return Color.argb((int) (Color.alpha(end) * f + Color.alpha(start) * f2), (int) (Color.red(end) * f + Color.red(start) * f2), (int) (Color.green(end) * f + Color.green(start) * f2), (int) (Color.blue(end) * f + Color.blue(start) * f2));
    }

    public static int setColorAlpha(int color, int alpha) {
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    public static void roundRect(Path path, Rect r, float r1, float r2, float r3, float r4) {
        path.reset();
        float x = r.left, y = r.top, w = r.width(), h = r.height();
        path.moveTo(x, y + r1);
        path.quadTo(x, y, x + r1, y);
        path.lineTo(x + w - r2, y);

        path.quadTo(x + w, y, x + w, y + r2);
        path.lineTo(x + w, y + h - r3);

        path.quadTo(x + w, y + h, x + w - r3, y + h);
        path.lineTo(x + r4, y + h);

        path.quadTo(x, y + h, x, y + h - r4);
        path.lineTo(x, y + r1);

        path.close();
    }

}
