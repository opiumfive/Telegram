package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;


public class VoIPRateButtonView extends FrameLayout {

    private final Paint paintOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TextView overlayTextView;
    private int radius;
    private float overlayProgress;
    ValueAnimator overlayAnimator;

    public VoIPRateButtonView(@NonNull Context context, int radius) {
        super(context);
        this.radius = radius;
        overlayTextView = new TextView(context);
        overlayTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        overlayTextView.setGravity(Gravity.CENTER);
        overlayTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        overlayTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        overlayTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        overlayTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 77)));
        addView(overlayTextView);
        overlayTextView.setVisibility(GONE);

        paintOverlayPaint.setColor(Color.WHITE);
    }

    public void setOverlayTextColor(int palette) {
        if (overlayTextView!= null) {
            overlayTextView.setTextColor(palette);
        }
    }

    public void show() {
        if (overlayAnimator != null) {
            overlayAnimator.removeAllListeners();
            overlayAnimator.cancel();
        }
        overlayProgress = 0f;
        overlayAnimator = ValueAnimator.ofFloat(overlayProgress, 1f);
        overlayTextView.setAlpha(0f);
        overlayTextView.setVisibility(VISIBLE);
        overlayAnimator.addUpdateListener(animation -> {
            overlayProgress = (float) animation.getAnimatedValue();

            overlayTextView.setTranslationX(getMeasuredWidth() - overlayProgress * ( getMeasuredWidth() / 2f + overlayTextView.getMeasuredWidth() / 2f));
            if (overlayProgress > 0.3f) {
                overlayTextView.setAlpha(Math.min(1f, (overlayProgress - 0.3f) / 0.3f));
            }
            invalidate();
        });
        overlayAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

            }
        });
        overlayAnimator.setDuration(250);
        overlayAnimator.setInterpolator(new DecelerateInterpolator());
        overlayAnimator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        AndroidUtilities.rectTmp.set(getMeasuredWidth() - overlayProgress * getMeasuredWidth(), 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paintOverlayPaint);
        super.dispatchDraw(canvas);
    }

    public void setOverlayText(String text) {
        overlayTextView.setText(text);
    }
}
