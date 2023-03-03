package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class VoIPStatusTextView extends FrameLayout {

    DottedTextView[] textView = new DottedTextView[2];
    DottedTextView reconnectTextView;
    VoIPTimerView timerView;

    CharSequence nextTextToSet;
    boolean animationInProgress;

    private boolean attachedToWindow;

    ValueAnimator animator;
    boolean timerShowing;

    public VoIPStatusTextView(@NonNull Context context) {
        super(context);
        for (int i = 0; i < 2; i++) {
            textView[i] = new DottedTextView(context);
            textView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView[i].setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
            textView[i].setTextColor(Color.WHITE);
            textView[i].setPadding(0, 0, AndroidUtilities.dp(24), 0);
            textView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            addView(textView[i]);
        }

        setClipChildren(false);
        setClipToPadding(false);

        reconnectTextView = new DottedTextView(context);
        reconnectTextView.setAnimating(false);
        reconnectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        //reconnectTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        reconnectTextView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), 0x10000000));
        reconnectTextView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(2), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        reconnectTextView.setTextColor(Color.WHITE);
        reconnectTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(reconnectTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

        reconnectTextView.setText(LocaleController.getString("CallWeakSignal", R.string.CallWeakSignal));
        //reconnectTextView.setPadding(0, 0, AndroidUtilities.dp(24), 0);
        reconnectTextView.setVisibility(View.GONE);

        timerView = new VoIPTimerView(context);
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

    }

    public void setCallEnded() {
        if (timerView != null) timerView.setCallEnded();
        if (reconnectTextView != null) reconnectTextView.setVisibility(GONE);
    }

    public void setText(String text, boolean ellipsis, boolean animated) {
        CharSequence nextString = text;

        textView[0].setAnimating(false);
        textView[1].setAnimating(false);

        textView[0].setAnimating(ellipsis);
        textView[1].setAnimating(ellipsis);

        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }

        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            animationInProgress = false;
            textView[0].setText(nextString);
            textView[0].setVisibility(View.VISIBLE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.GONE);

        } else {
            if (animationInProgress) {
                nextTextToSet = nextString;
                return;
            }

            if (timerShowing) {
                textView[0].setText(nextString);
                replaceViews(timerView, textView[0], null);
            } else {
                if (!textView[0].getText().equals(nextString)) {
                    textView[1].setText(nextString);
                    replaceViews(textView[0], textView[1], () -> {
                        DottedTextView v = textView[0];
                        textView[0] = textView[1];
                        textView[1] = v;
                    });
                }
            }
        }
    }

    public void showTimer(boolean animated) {
        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }
        if (timerShowing) {
            return;
        }
        timerView.updateTimer();
        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            timerShowing = true;
            animationInProgress = false;
            textView[0].setVisibility(View.GONE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.VISIBLE);
        } else {
            if (animationInProgress) {
                nextTextToSet = "timer";
                return;
            }
            timerShowing = true;
            replaceViews(textView[0], timerView, null);
        }

        //ellipsizeAnimator.removeView(textView[0]);
        //ellipsizeAnimator.removeView(textView[1]);
    }


    private void replaceViews(View out, View in, Runnable onEnd) {
        out.setVisibility(View.VISIBLE);
        in.setVisibility(View.VISIBLE);

        in.setTranslationY(AndroidUtilities.dp(15));
        in.setAlpha(0f);
        animationInProgress = true;
        animator = ValueAnimator.ofFloat(0, 1f);
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            float inScale = 0.4f + 0.6f * v;
            float outScale = 0.4f + 0.6f * (1f - v);
            in.setTranslationY(AndroidUtilities.dp(10) * (1f - v));
            in.setAlpha(v);
            in.setScaleX(inScale);
            in.setScaleY(inScale);

            out.setTranslationY(-AndroidUtilities.dp(10) * v);
            out.setAlpha(1f - v);
            out.setScaleX(outScale);
            out.setScaleY(outScale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                out.setVisibility(View.GONE);
                out.setAlpha(1f);
                out.setTranslationY(0);
                out.setScaleY(1f);
                out.setScaleX(1f);

                in.setAlpha(1f);
                in.setTranslationY(0);
                in.setVisibility(View.VISIBLE);
                in.setScaleY(1f);
                in.setScaleX(1f);

                if (onEnd != null) {
                    onEnd.run();
                }
                animationInProgress = false;
                if (nextTextToSet != null) {
                    if (nextTextToSet.equals("timer")) {
                        showTimer(true);
                    } else {
                        textView[1].setText(nextTextToSet);
                        replaceViews(textView[0], textView[1], () -> {
                            DottedTextView v = textView[0];
                            textView[0] = textView[1];
                            textView[1] = v;
                        });
                    }
                    nextTextToSet = null;
                }
            }
        });
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void setSignalBarCount(int count) {
        timerView.setSignalBarCount(count);
    }

    public void showReconnect(boolean showReconnecting, boolean animated) {
        if (!animated) {
            reconnectTextView.animate().setListener(null).cancel();
            reconnectTextView.setVisibility(showReconnecting ? View.VISIBLE : View.GONE);
        } else {
            if (showReconnecting) {
                if (reconnectTextView.getVisibility() != View.VISIBLE) {
                    reconnectTextView.setVisibility(View.VISIBLE);
                    reconnectTextView.setAlpha(0);
                    reconnectTextView.setScaleX(0.2f);
                    reconnectTextView.setScaleY(0.2f);
                }
                reconnectTextView.animate().setListener(null).cancel();
                reconnectTextView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(175).start();
            } else {
                reconnectTextView.animate().alpha(0).scaleX(0.2f).scaleY(0.2f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        reconnectTextView.setVisibility(View.GONE);
                    }
                }).setDuration(150).start();
            }
        }

        if (showReconnecting) {
            //ellipsizeAnimator.addView(reconnectTextView);
        } else {
           // ellipsizeAnimator.removeView(reconnectTextView);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        //ellipsizeAnimator.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        //ellipsizeAnimator.onDetachedFromWindow();
    }

    private class DottedTextView extends TextView {

        private class Point {
            float x,y,r;
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Point p1;
        private Point p2;
        private Point p3;
        private boolean animating = true;

        public DottedTextView(Context context) {
            super(context);
            p.setColor(Color.WHITE);
        }

        public void setAnimating(boolean a) {
            if (!a) {
                p1 = null;
                p2 = null;
                p3 = null;
            }
            animating = a;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (!animating) return;

            float fromX = getMeasuredWidth() - AndroidUtilities.dp(24) + AndroidUtilities.dp(4);
            float y = getMeasuredHeight() / 2f;
            float diff = 1f;

            if (p1 == null) {
                p1 = new Point();
                p1.y = y;
                p1.x = fromX;
                p1.r = 1;
            } else {
                p1.x += diff;
                if (p1.x > getMeasuredWidth()) {
                    p1.x = fromX;
                }
                if (p1.x > fromX + AndroidUtilities.dp(10)) {
                    p1.r = AndroidUtilities.lerp(AndroidUtilities.dpf2(2.5f), 1, 1f - (getMeasuredWidth() - p1.x) / AndroidUtilities.dp(10));
                } else {
                    p1.r = AndroidUtilities.lerp(1, AndroidUtilities.dpf2(2.5f), 1f - (getMeasuredWidth() - AndroidUtilities.dp(10)- p1.x) / AndroidUtilities.dp(10));
                }
            }

            if (p2 == null) {
                p2 = new Point();
                p2.y = y;
                p2.x = fromX + AndroidUtilities.dp(10);
                p2.r = 1;
            } else {
                p2.x += diff;
                if (p2.x > getMeasuredWidth()) {
                    p2.x = fromX;
                }
                if (p2.x > fromX + AndroidUtilities.dp(10)) {
                    p2.r = AndroidUtilities.lerp(AndroidUtilities.dpf2(2.5f), 1, 1f - (getMeasuredWidth() - p2.x) / AndroidUtilities.dp(10));
                } else {
                    p2.r = AndroidUtilities.lerp(1, AndroidUtilities.dpf2(2.5f), 1f - (getMeasuredWidth() - AndroidUtilities.dp(10)- p2.x) / AndroidUtilities.dp(10));
                }
            }

            if (p3 == null) {
                p3 = new Point();
                p3.y = y;
                p3.x = fromX + AndroidUtilities.dp(20) - 1;
                p3.r = 1;
            } else {
                p3.x += diff;
                if (p3.x > getMeasuredWidth()) {
                    p3.x = fromX;
                }
                if (p3.x > fromX + AndroidUtilities.dp(10)) {
                    p3.r = AndroidUtilities.lerp(AndroidUtilities.dpf2(2.5f), 1, 1f - (getMeasuredWidth() - p3.x) / AndroidUtilities.dp(10));
                } else {
                    p3.r = AndroidUtilities.lerp(1, AndroidUtilities.dpf2(2.5f), 1f - (getMeasuredWidth() - AndroidUtilities.dp(10)- p3.x) / AndroidUtilities.dp(10));
                }
            }

            canvas.drawCircle(p1.x, p1.y, p1.r, p);
            canvas.drawCircle(p2.x, p2.y, p2.r, p);
            canvas.drawCircle(p3.x, p3.y, p3.r, p);

            if (animating) {
                invalidate();
            }
        }
    }

}
