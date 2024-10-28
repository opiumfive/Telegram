package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import org.telegram.messenger.AndroidUtilities;

public class BouncingArrowsDrawable extends Drawable implements Animatable {

    private static final int DURATION = 1000;

    private final Paint paint;
    private final Path path;
    private final Interpolator interpolator;

    private long startTime;
    private boolean running;

    public BouncingArrowsDrawable() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setColor(Color.WHITE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        path = new Path();
        path.reset();

        path.moveTo(AndroidUtilities.dp(3), AndroidUtilities.dp(6) - AndroidUtilities.dp(2));
        path.lineTo(AndroidUtilities.dp(8), AndroidUtilities.dp(6) + AndroidUtilities.dp(2));
        path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(6) - AndroidUtilities.dp(2));

        interpolator = new AccelerateDecelerateInterpolator();
    }

    @Override
    public void draw(Canvas canvas) {
        float fraction = (System.currentTimeMillis() - startTime) / (float) DURATION;
        fraction = Math.min(fraction, 1);
        fraction = interpolator.getInterpolation(fraction);

        if (fraction >= 1) {
            fraction = 0;
            startTime = System.currentTimeMillis();
        }

        float yOffset = (float) (AndroidUtilities.dp(1) * 2 * Math.sin(fraction * Math.PI));

        canvas.save();
        canvas.translate(getBounds().centerX() - AndroidUtilities.dp(4), getBounds().centerY() + yOffset - AndroidUtilities.dp(9));
        canvas.drawPath(path, paint);
        canvas.restore();

        canvas.save();
        canvas.translate(getBounds().centerX() - AndroidUtilities.dp(4), getBounds().centerY() + yOffset - AndroidUtilities.dp(2.5f));
        canvas.drawPath(path, paint);
        canvas.restore();

        if (running && fraction < 1) {
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void start() {
        startTime = System.currentTimeMillis();
        running = true;
        invalidateSelf();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24);
    }
}
