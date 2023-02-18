package org.telegram.ui.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class ReactionView extends FrameLayout {

    protected BackupImageView imageView;
    protected boolean loaded = false;

    public ReactionView(Context context) {
        super(context);

        imageView = new BackupImageView(getContext());
        imageView.setLayerNum(1);
        imageView.setAspectFit(false);
        addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
    }

    public void setReaction(TLRPC.TL_availableReaction reaction, boolean active) {
        TLRPC.Document reactionEmoji = active ? reaction.activate_animation : reaction.select_animation;
        Drawable thumbDrawable = null;
        //thumbDrawable = Emoji.getEmojiDrawable(reaction.reaction);

        if (!active) {
            imageView.getImageReceiver().setAutoRepeat(3);
            //imageView.setImage(ImageLocation.getForDocument(reactionEmoji), "36_36", thumbDrawable, null);
            String parentObject = null;

            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(reactionEmoji.thumbs, 90);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(reactionEmoji, Theme.key_emptyListPlaceholder, 0.2f);
            if (svgThumb != null) {
                imageView.setImage(ImageLocation.getForDocument(reactionEmoji), "36_36", null, svgThumb, parentObject);
            } else if (thumb != null) {
                imageView.setImage(ImageLocation.getForDocument(reactionEmoji), "36_36", ImageLocation.getForDocument(thumb, reactionEmoji), null, 0, parentObject);
            } else {
                imageView.setImage(ImageLocation.getForDocument(reactionEmoji), "36_36", null, null, parentObject);
            }
        } else {
            imageView.setImage(ImageLocation.getForDocument(reactionEmoji), "100_100", thumbDrawable, null);
        }
        loaded = true;
    }

    public void animateOneTime() {
        loaded = true;
        if (!imageView.getImageReceiver().isAnimationRunning() && loaded) {
            imageView.getImageReceiver().setAllowStartLottieAnimation(true);
            imageView.getImageReceiver().startAnimation();
        }
    }
}

