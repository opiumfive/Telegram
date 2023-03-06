package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.Keep;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.WaveDrawable;

public class VoIPWavesView extends View {

    private float scale = 1.4f;
    private float amplitude;
    private float animateToAmplitude;
    private float animateAmplitudeDiff;
    private long lastUpdateTime;
    private final boolean isWithShadows;
    private final BlobDrawable tinyWaveDrawable;
    private final BlobDrawable bigWaveDrawable;
    private float wavesEnterAnimation = 1f;
    private boolean showWaves = true;
    private float drawingCx, drawingCy;
    private int size;
    private int maxSize;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VoIPWavesView(Context context, int minSizeDp) {
        this(context, minSizeDp, (int) (minSizeDp * 1.15f), 4, 4, true);
    }

    public VoIPWavesView(Context context, int minSizeDp, int maxSizeDp, int blobSize1, int blobSize2, boolean isWithShadows) {
        super(context);

        size = minSizeDp;
        maxSize = minSizeDp;
        this.isWithShadows = false;

        tinyWaveDrawable = new BlobDrawable(blobSize1, true);
        bigWaveDrawable = new BlobDrawable(blobSize2, true);

        tinyWaveDrawable.minRadius = AndroidUtilities.dp(size / 2);
        tinyWaveDrawable.maxRadius = AndroidUtilities.dp(maxSize / 2);
        tinyWaveDrawable.generateBlob();

        bigWaveDrawable.minRadius = AndroidUtilities.dp(size / 2);
        bigWaveDrawable.maxRadius = AndroidUtilities.dp(maxSize / 2);
        bigWaveDrawable.generateBlob();

        updateColors();
    }

    public void setAmplitude(double value, boolean force) {
        if (isPaused && !force) return;
        bigWaveDrawable.setValue((float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value * 0.60) / WaveDrawable.MAX_AMPLITUDE), true);
        tinyWaveDrawable.setValue((float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value) / WaveDrawable.MAX_AMPLITUDE), false);

        animateToAmplitude = (float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value) / WaveDrawable.MAX_AMPLITUDE);
        animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500.0f * WaveDrawable.animationSpeedCircle);

        invalidate();
    }

    public float getScale() {
        return scale;
    }

    @Keep
    public void setScale(float value) {
        scale = value;
        invalidate();
    }



    @Override
    protected void onDraw(Canvas canvas) {



        int cx = getMeasuredWidth() / 2;
        int cy = getMeasuredHeight() / 2;

        drawingCx = cx;
        drawingCy = cy;

        if (isPaused && animateToAmplitude == amplitude) {
            int color = Color.WHITE;
            int colorWave1 = ColorUtils.setAlphaComponent(color, 36);
            int colorWave2 = ColorUtils.setAlphaComponent(color, 20);
            paint.setColor(colorWave1);
            canvas.drawCircle(drawingCx, drawingCy, AndroidUtilities.dp(72), paint);

            paint.setColor(colorWave2);
            canvas.drawCircle(drawingCx, drawingCy, AndroidUtilities.dp(82), paint);
            return;
        }

        long dt = System.currentTimeMillis() - lastUpdateTime;
        if (animateToAmplitude != amplitude) {
            amplitude += animateAmplitudeDiff * dt;
            if (animateAmplitudeDiff > 0) {
                if (amplitude > animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            } else {
                if (amplitude < animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            }
        }

        bigWaveDrawable.updateAmplitude(dt);
        bigWaveDrawable.update(bigWaveDrawable.amplitude * 1.5f, 1.1f);
        tinyWaveDrawable.updateAmplitude(dt);
        tinyWaveDrawable.update(tinyWaveDrawable.amplitude * 1.5f, 1.1f);

        lastUpdateTime = System.currentTimeMillis();

        if (showWaves && wavesEnterAnimation != 1f) {
            wavesEnterAnimation += 0.04f;
            if (wavesEnterAnimation > 1f) {
                wavesEnterAnimation = 1f;
            }
        }

        float enter = CubicBezierInterpolator.EASE_OUT.getInterpolation(wavesEnterAnimation);
        canvas.save();
        float s = scale * enter * (BlobDrawable.SCALE_BIG + bigWaveDrawable.amplitude);
        canvas.scale(s, s, cx, cy);
        bigWaveDrawable.draw(cx, cy, canvas, bigWaveDrawable.paint);
        canvas.restore();
        s = scale * enter * (BlobDrawable.SCALE_SMALL_MIN + tinyWaveDrawable.amplitude);
        canvas.save();
        canvas.scale(s, s, cx, cy);
        tinyWaveDrawable.draw(cx, cy, canvas, tinyWaveDrawable.paint);
        canvas.restore();

        invalidate();
    }

    public void updateColors() {
        int color = Color.WHITE;
        int colorWave1 = ColorUtils.setAlphaComponent(color, 36);
        int colorWave2 = ColorUtils.setAlphaComponent(color, 20);


        bigWaveDrawable.paint.setColor(colorWave1);
        tinyWaveDrawable.paint.setColor(colorWave2);

        if (isWithShadows) {
            bigWaveDrawable.paint.setShadowLayer(AndroidUtilities.dp(4), -AndroidUtilities.dp(4), AndroidUtilities.dp(4), colorWave1);
            tinyWaveDrawable.paint.setShadowLayer(AndroidUtilities.dp(4), -AndroidUtilities.dp(4), AndroidUtilities.dp(4), colorWave2);
        }
    }

    public void showWaves(boolean b, boolean animated) {
        if (!animated) {
            wavesEnterAnimation = b ? 1f : 0.5f;
        }
        showWaves = b;
    }

    boolean isPaused = false;
    boolean isImmediate = false;

    public void pause(boolean immediate) {
        isPaused = true;
        isImmediate = immediate;
        if (immediate) {
            animateToAmplitude = 0;
            amplitude = 0;
            animateAmplitudeDiff = 0;
        }
        setAmplitude(0, true);

    }

    public void resume() {
        isPaused = false;
        isImmediate = false;
        setAmplitude(100, false);
    }
}
