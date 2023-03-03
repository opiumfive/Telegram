package org.telegram.ui.Components.voip;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Region;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.IntDef;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.VoIPFragment;

import java.lang.annotation.Retention;

public class VoIPSmoothMotionView extends View {

    @Retention(SOURCE)
    @IntDef({INITIATING, ESTABLISHED, WEAK_SIGNAL})
    public @interface State {
    }

    public static final int INITIATING = 0;
    public static final int ESTABLISHED = 1;
    public static final int WEAK_SIGNAL = 2;

    private @State
    int state = INITIATING;
    private long lastUpdateTime;
    private @VoIPFragment.Gradients.Palette
    int nextPalette = VoIPFragment.Gradients.NONE;
    private boolean fastTransition = false;
    private ValueAnimator valueAnimator = null;
    private ValueAnimator valueAnimator2 = null;
    private final Path roundRectPath = new Path();
    private float cX = 0;
    private float cY = 0;
    private float outBackgroundRadius = 0;
    private float addRad = 3;
    boolean circular = false;

    public void pause(boolean immediate) {
        //TODO
    }

    public void resume() {
        //TODO
    }

    public void setState(@State int newState, PointF circular) {
        state = newState;

        switch (state) {
            case INITIATING:
                nextPalette = VoIPFragment.Gradients.BLUE_VIOLET;
                break;
            case ESTABLISHED:
                nextPalette = VoIPFragment.Gradients.GREEN;

                break;
            case WEAK_SIGNAL:
                nextPalette = VoIPFragment.Gradients.ORANGE_RED;
                break;
        }


        if (circular != null) {
            this.circular = true;
            AndroidUtilities.cancelRunOnUIThread(cycleBackgroundAnimation);
            cX = circular.x;
            cY = circular.y;
            addRad = getMeasuredHeight() / 750f * 16f;
            switchPalette();
            MotionBackgroundDrawable mTo = getNextBackground();
            mTo.setColors(
                    VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 0),
                    VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 1),
                    VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 2),
                    VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 3),
                    0, false);
            mTo.setBackgroundAlpha(1f);
            getCurrentBackground().setBackgroundAlpha(1f);
        } else {

            phase = 5;
            fastTransition = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        float scale = 2f;
        canvas.scale(scale, scale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);

        MotionBackgroundDrawable cur = getCurrentBackground();
        MotionBackgroundDrawable next = getNextBackground();

       // if (cur.getBackgroundAlpha() != 0f) {
            cur.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            cur.draw(canvas);
       // }

        if (circular) {
            outBackgroundRadius += addRad;
            if (outBackgroundRadius > getMeasuredHeight() / 2f) {
                circular = false;
                fastTransition = false;
                outBackgroundRadius = 0f;

                cur.setBackgroundAlpha(0f);
                phase = 0;
                current1 = !current1;

                AndroidUtilities.runOnUIThread(cycleBackgroundAnimation, 1000);
            }
        }

        if (circular) {
            canvas.save();
            roundRectPath.rewind();
            roundRectPath.addCircle(cX, cY, outBackgroundRadius, Path.Direction.CW);
            canvas.clipPath(roundRectPath);
        }

        //if (next.getBackgroundAlpha() != 0f) {
            next.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            next.draw(canvas);
        //}


        if (circular) canvas.restore();

        canvas.restore();

        if (cycleAnimationInProgress) {
            invalidate();
        }
    }

    public VoIPSmoothMotionView(Context context) {
        super(context);

        backgroundImage = new MotionBackgroundDrawable();
        backgroundImage.setColors(
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 0),
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 1),
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 2),
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 3),
                0, true);
        backgroundImage.setIndeterminateAnimation(true);
        backgroundImage2 = new MotionBackgroundDrawable();
        backgroundImage2.setColors(
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 0),
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 1),
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 2),
                VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 3),
                0, false);
        backgroundImage2.setIndeterminateAnimation(true);
        getNextBackground().setPosAnimationProgress(0f);
        getCurrentBackground().setPosAnimationProgress(0f);
        backgroundImage2.setBackgroundAlpha(0f);
    }

    private final MotionBackgroundDrawable backgroundImage;
    private final MotionBackgroundDrawable backgroundImage2;

    private boolean cycleAnimationInProgress = false;
    private @VoIPFragment.Gradients.Palette
    int palette = VoIPFragment.Gradients.BLUE_VIOLET;
    private int phase = 0;

    private final Runnable cycleBackgroundAnimation = new Runnable() {
        @Override
        public void run() {
            phase++;


            if (phase == 6 && !circular) {
                int oldPalette = palette;
                switchPalette();

                if (oldPalette != palette) {
                    MotionBackgroundDrawable mTo = getNextBackground();
                    mTo.setColors(
                            VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 0),
                            VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 1),
                            VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 2),
                            VoIPFragment.Gradients.color(palette, VoIPFragment.Gradients.MAIN, 3),
                            0, false);

                    if (valueAnimator != null) {
                        valueAnimator.cancel();
                        valueAnimator = null;
                    }
                    if (valueAnimator2 != null) {
                        valueAnimator2.cancel();
                        valueAnimator2 = null;
                    }

                    if (!circular) {
                        valueAnimator = ValueAnimator.ofFloat(1f, 0f);
                        valueAnimator.addUpdateListener(animation -> {
                            float val = (float) animation.getAnimatedValue();
                            //if (circular) {
                            //    getCurrentBackground().setBackgroundAlpha(1f);
                            //} else {
                            //    getCurrentBackground().setBackgroundAlpha(val);
                            //}
                            if (!circular) {
                                getCurrentBackground().setBackgroundAlpha(val);
                            }
                            //getNextBackground().setBackgroundAlpha(val);
                        });
                        valueAnimator.setInterpolator(new AccelerateInterpolator());

                        valueAnimator.start();

                        valueAnimator2 = ValueAnimator.ofFloat(0f, 1f);
                        valueAnimator2.addUpdateListener(animation -> {
                            float val = (float) animation.getAnimatedValue();
                            //getCurrentBackground().setBackgroundAlpha(1 - val);
                            if (!circular) {
                                getNextBackground().setBackgroundAlpha(val);
                            }
                        });
                        valueAnimator2.setInterpolator(new DecelerateInterpolator());


                        if (fastTransition) {
                            fastTransition = false;
                            valueAnimator2.setDuration(500);
                            valueAnimator.setDuration(500);
                        } else {
                            valueAnimator2.setDuration(1950);
                            valueAnimator.setDuration(1950);
                        }

                        valueAnimator2.start();
                    }
                }
            }

            if (phase == 8) {
                current1 = !current1;
                phase = 0;
            }

            if (cycleAnimationInProgress && !circular) {
                AndroidUtilities.runOnUIThread(this, 1000);
            }
        }
    };

    boolean current1 = true;

    private MotionBackgroundDrawable getCurrentBackground() {
        if (current1) return backgroundImage;
        return backgroundImage2;
    }

    private MotionBackgroundDrawable getNextBackground() {
        if (current1) return backgroundImage2;
        return backgroundImage;
    }

    private void switchPalette() {
        if (state != WEAK_SIGNAL) {
            if (nextPalette == VoIPFragment.Gradients.NONE) {
                int mod = 2;
                if (state == ESTABLISHED) mod = 3;
                palette = ++palette % mod;
            } else {
                palette = nextPalette;
            }
        } else {
            palette = VoIPFragment.Gradients.ORANGE_RED;
        }
        nextPalette = VoIPFragment.Gradients.NONE;
    }

    public void runCycleAnimation() {
        cycleAnimationInProgress = true;
        invalidate();
        AndroidUtilities.runOnUIThread(cycleBackgroundAnimation, 1000);
    }

    public void stopCycleAnimation() {
        cycleAnimationInProgress = false;
        AndroidUtilities.cancelRunOnUIThread(cycleBackgroundAnimation);
    }

    
}
