package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.GroupCreateUserCell;

public class SendAsSpanAnimated extends SendAsSpan  {

    private final ValueAnimator animator;
    private final ChatActivity activity;
    private float fromX, fromY, toX, toY;

    public SendAsSpanAnimated(ChatActivity activity, TLRPC.Peer peer, View from, View to, int popupHeight) {
        super(activity.getContentView().getContext(), null);
        this.activity = activity;
        setVisibility(INVISIBLE);
        fromX = from.getX() + AndroidUtilities.dp(17);
        fromY = activity.getChatActivityEnterView().getY() - popupHeight + from.getY() - AndroidUtilities.dp(17);
        toX = to.getX();
        toY = activity.getChatActivityEnterView().getY() - AndroidUtilities.dp(78);
        activity.getContentView().addView(this);
        setObject(activity.getAccountInstance(), -activity.getCurrentChat().id, peer);
        animator = ObjectAnimator.ofFloat(0, 1);
        animator.setDuration(300);
        animator.addUpdateListener(animation -> {
            animationMove((float) animation.getAnimatedValue());
        });
        animator.setInterpolator(new OvershootInterpolator(0.9f));
        animator.addListener(new AnimatorListenerAdapter() {

            boolean sentEnd = false;

            @Override
            public void onAnimationStart(Animator animation) {
                startAnimation();

                if (listener != null) {
                    listener.onAnimationStart(animation);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (listener != null) {
                    listener.onAnimationEnd(animation);
                }
                ValueAnimator stubanim = ObjectAnimator.ofFloat(0, 1);
                stubanim.setDuration(100);
                stubanim.addUpdateListener(valueAnimator -> {
                    setAlpha(1f - (float) valueAnimator.getAnimatedValue());
                    if ((float) valueAnimator.getAnimatedValue() > 0.5 && !sentEnd) {
                        sentEnd = true;

                    }
                });
                stubanim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setVisibility(INVISIBLE);
                        animator.cancel();
                        activity.getContentView().removeView(SendAsSpanAnimated.this);

                    }
                });
                stubanim.start();
            }
        });
    }

    Animator.AnimatorListener listener;

    public void start(Animator.AnimatorListener listener) {
        this.listener = listener;
        if (animator != null && !animator.isStarted()) {
            animator.start();
        }
    }

    private void animationMove(float animatedValue) {
        setTranslationX(getValue(fromX, toX, animatedValue));
        setTranslationY(getValue(fromY, toY, animatedValue));
    }

    public static int getValue(int start, int end, float f) {
        return Math.round(start * (1 - f) + end * f);
    }

    public static float getValue(float start, float end, float f) {
        return start * (1 - f) + end * f;
    }

    public void updateAnimation() {
    }

    private void startAnimation() {
        animationMove(0);
        setVisibility(VISIBLE);
    }
}
