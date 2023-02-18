package org.telegram.ui.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/* Usage example:
ReactionViewAnimated animatedHero = new ReactionViewAnimated(ChatActivity.this, reaction, from, to, chatListView, message.getId());
animatedHero.start(new Animator.AnimatorListener() {
    @Override
    public void onAnimationStart(Animator animation) {
        popupReactionsAdapter.hideReactionForAnimation(position);
        scrimPopupWindow.dismiss();
        if (popupReactionsAdapter != null) popupReactionsAdapter.stop();
    }

    @Override
    public void onAnimationEnd(Animator animation) {
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }
});*/
@Deprecated // because faster animator ReactionViewAnimated2.java presented
public class ReactionViewAnimated extends ReactionView {

    private final ValueAnimator animator;
    private final ChatActivity activity;
    private float fromX, fromY, toX, toY, toScale;
    private boolean startedLottieAnimation = false;
    private long timeStarted = 0L;
    private BackupImageView imageViewEffects;
    private TLRPC.TL_availableReaction reaction;
    private RecyclerListView chatListView;
    private int messageId;

    public ReactionViewAnimated(ChatActivity activity, TLRPC.TL_availableReaction react, RectF from, RectF to, RecyclerListView chatListView, int messageId) {
        super(activity.getContentView().getContext());
        this.activity = activity;
        this.reaction = react;
        this.chatListView = chatListView;
        this.messageId = messageId;
        setVisibility(INVISIBLE);
        fromX = from.left;
        fromY = from.top;
        toX = to.left;
        toY = to.top;
        toScale = to.width() / from.width();
        activity.getContentView().addView(this);


        imageView.getImageReceiver().setAutoRepeat(3);
        setReaction(reaction, true);
        animator = ObjectAnimator.ofFloat(0, 1);
        animator.setDuration(250);
        animator.addUpdateListener(animation -> {
            animationMove((float) animation.getAnimatedValue());
        });
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
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

            }
        });
    }

    private void onReactionAnimationPlayed() {
        startedLottieAnimation = false;
        ChatMessageCell animateToView = findViewAndShowAnimation();
        if (animateToView != null) {
            fromX = toX;
            fromY = toY;

            toX = animateToView.getX();
            toY = animateToView.getY() - AndroidUtilities.dp(400);
        }
        ValueAnimator stubanim = ObjectAnimator.ofFloat(0, 1);
        stubanim.setDuration(250);
        stubanim.addUpdateListener(valueAnimator -> {
            imageViewEffects.setAlpha(1f - (float) valueAnimator.getAnimatedValue());
            ReactionViewAnimated.this.setScaleX(getValue(toScale, 0.1f, (float) valueAnimator.getAnimatedValue()));
            ReactionViewAnimated.this.setScaleY(getValue(toScale, 0.1f, (float) valueAnimator.getAnimatedValue()));
            if (animateToView != null) {
                ReactionViewAnimated.this.setTranslationX(getValue(fromX, toX, (float) valueAnimator.getAnimatedValue()));
                ReactionViewAnimated.this.setTranslationY(getValue(fromY, toY, (float) valueAnimator.getAnimatedValue()));
            }
        });
        stubanim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

            }
        });
        stubanim.start();
        postDelayed(() -> {
            stubanim.cancel();
            ReactionViewAnimated.this.setVisibility(GONE);
            imageViewEffects.setVisibility(View.GONE);
            activity.getContentView().removeView(imageViewEffects);
            activity.getContentView().removeView(ReactionViewAnimated.this);
        }, stubanim.getDuration());
    }

    private Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        if (startedLottieAnimation && imageView.getImageReceiver().getLottieAnimation() != null && !imageView.getImageReceiver().getLottieAnimation().isRunning() && System.currentTimeMillis() - timeStarted >= 200) {
            onReactionAnimationPlayed();
        } else {
            Choreographer.getInstance().postFrameCallback(ReactionViewAnimated.this.frameCallback);
        }
    };

    private void onFirstTransitionEnd() {
        if (listener != null) {
            listener.onAnimationEnd(animator);
        }

        animator.cancel();
        imageViewEffects = new BackupImageView(activity.getContentView().getContext());
        imageViewEffects.setLayerNum(1);
        imageViewEffects.setAspectFit(false);

        int width = (int) AndroidUtilities.px2dp(AndroidUtilities.getRealScreenSize().x - AndroidUtilities.dp(32));

        activity.getContentView().addView(imageViewEffects, LayoutHelper.createFrame(width, width, Gravity.CENTER, 0, 0, 100, 0));

        Drawable thumbDrawable = null;
        imageViewEffects.setImage(ImageLocation.getForDocument(reaction.effect_animation), "300_300", thumbDrawable, null);
        //imageViewEffects.getImageReceiver().setImage(ImageLocation.getForDocument(reaction.activate_animation), 100 + "_" + 100 + "_pcache", null, "tgs", set, 1);
        imageViewEffects.getImageReceiver().setAutoRepeat(2);

        imageView.getImageReceiver().setAllowStartLottieAnimation(true);
        imageView.getImageReceiver().startAnimation();
        imageViewEffects.getImageReceiver().setAllowStartLottieAnimation(true);
        imageViewEffects.getImageReceiver().startAnimation();
        startedLottieAnimation = true;
        timeStarted = System.currentTimeMillis();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private ChatMessageCell findViewAndShowAnimation() {
        ChatMessageCell bestView = null;
        for (int i = 0; i < chatListView.getChildCount(); i++) {
            View child = chatListView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                if (cell.getMessageObject().getId() == messageId) {
                    bestView = cell;
                    break;
                }
            }
        }

        return bestView;
    }

    Animator.AnimatorListener listener;

    public void start(Animator.AnimatorListener listener) {
        this.listener = listener;
        if (animator != null && !animator.isStarted()) {
            animator.start();
            postDelayed(this::onFirstTransitionEnd, animator.getDuration());
        }
    }

    private void animationMove(float animatedValue) {
        setTranslationX(getValue(fromX, toX, animatedValue));
        setTranslationY(getValue(fromY, toY, animatedValue));
        setScaleX(getValue(0.5f, toScale, animatedValue));
        setScaleY(getValue(0.5f, toScale, animatedValue));
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
