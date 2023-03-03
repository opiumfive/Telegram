package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.GestureDetector2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;

public class VoIPRatingView extends FrameLayout {

    private static final int STAR_SIZE_DP = 32;

    private final int numStars = 5;
    private int selectedRating = 0;
    private OnRatingChangeListener listener;
    private final List<RLottieImageView> stars = new ArrayList<>();

    public VoIPRatingView(Context context) {
        super(context);

        setClipChildren(false);
        setClipToPadding(false);

        for (int i = 0; i < numStars; i++) {
            RLottieImageView imageView = new RLottieImageView(context);
            imageView.setAnimation(R.raw.voip_star, STAR_SIZE_DP, STAR_SIZE_DP, i);
            addView(imageView, LayoutHelper.createFrame(STAR_SIZE_DP, STAR_SIZE_DP, Gravity.LEFT, i * (STAR_SIZE_DP + 10), 0, 0, 0));
            stars.add(imageView);
        }
    }

    long lastTouch;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && (System.currentTimeMillis() - lastTouch) > 500) {
            lastTouch = System.currentTimeMillis();
            float offset = AndroidUtilities.dp(-8);
            for (int i = 0; i < numStars; i++) {
                if (event.getX() > offset && event.getX() < offset + AndroidUtilities.dp(STAR_SIZE_DP + 10)) {
                    if (selectedRating != i + 1) {
                        selectedRating = i + 1;
                        if (listener != null) listener.onRatingChanged(selectedRating, event.getX());

                        for (int j = 0; j < numStars; j++) {
                            boolean set = j <= i;
                            RLottieImageView imageView = stars.get(j);
                            RLottieDrawable drawable = imageView.getAnimatedDrawable();
                            drawable.setPlayInDirectionOfCustomEndFrame(true);
                            imageView.stopAnimation();
                            int fr = drawable.getCurrentFrame();
                            int endFr = set ? 15 : 1;
                            if (Math.abs(fr - endFr) >= 2) {
                                drawable.setCustomEndFrame(set ? 15 : 1);
                                imageView.playAnimation();
                                imageView.invalidate();
                            }

                            if (set) {
                                imageView.animate().withEndAction(null).cancel();
                                imageView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).withEndAction(() -> imageView.animate().scaleX(1f).scaleY(1f).setDuration(300));
                            }
                        }
                        break;
                    }
                }
                offset += AndroidUtilities.dp(STAR_SIZE_DP + (i == 0 ? 5 : 10));
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
            lastTouch = 0;
        }
        return false;
    }

    public int getRating() {
        return selectedRating;
    }

    public void setOnRatingChangeListener(OnRatingChangeListener l) {
        listener = l;
    }

    public interface OnRatingChangeListener {
        void onRatingChanged(int newRating, float x);
    }
}
