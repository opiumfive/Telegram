package org.telegram.ui.Components.MessageAnimations.Animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationInterpolation;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationItemType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParams;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class ChatMessageCellAnimated extends ChatMessageCell {

    private final MessageAnimationHolder messageAnimationHolder;
    private final ChatActivity activity;
    private final AnimationParams animationParams;
    private final ArrayList <AnimationState> animationData = new ArrayList<>();
    private final Rect viewRect = new Rect();
    private final Rect startRect = new Rect();
    private final Rect endRect = new Rect();
    private final Rect currentRect = new Rect();
    private final Rect messageRect = new Rect();
    private final Rect listViewRect = new Rect();
    private final Rect clipRect = new Rect();

    private final Paint backPaint;
    private final Path clipPath = new Path();
    private final Point startReplyOffset = new Point();
    private final int padding = AndroidUtilities.dp(12);
    private final ValueAnimator animator;
    private AnimationState textPositionState;

    private int additionalHeight = 0;
    private int messageBackWidth;
    private float startClipRadius;
    private float endClipRadius;
    private float currentClipRadius;

    private float startTextSize;
    private float endTextSize;
    private int startBackWidth;
    private int endBackWidth;

    private float currentAnimFactor = 0f;
    private int attachmentCount = 0;
    public int lastTop = 0;

    private ChatMessageCell messageCell;

    public ChatMessageCellAnimated(MessageAnimationHolder messageAnimationHolder, MessageObject messageObject) {
        super(messageAnimationHolder.activity.getContentView().getContext());
        this.messageAnimationHolder = messageAnimationHolder;
        this.activity = messageAnimationHolder.activity;
        this.animationParams = messageAnimationHolder.getAnimationParamsForMessage(messageObject);
        this.setVisibility(View.INVISIBLE);

        for (AnimationInterpolation info: animationParams) animationData.add(new AnimationState(info));

        this.setDelegate(new ChatMessageCellDelegate() {
            @Override
            public TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
                return activity.getTextSelectionHelper();
            }
        });

        activity.getContentView().addView(this);

        animator = ObjectAnimator.ofFloat(0, 1);
        animator.addUpdateListener(animation -> {
            animationMove((float) animation.getAnimatedValue());
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                startAnimation();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (messageCell == null) return;

                if (animationParams.animationType == AnimationItemType.VideoMessage) {
                    messageCell.setAlpha(1.0f);
                    messageCell.getTransitionParams().ignoreAlpha = false;
                    ValueAnimator stubanim = ObjectAnimator.ofFloat(0, 1);
                    stubanim.setDuration(100);
                    stubanim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            messageCell.endAnimationMove();
                        }
                    });
                    stubanim.start();
                } else {
                    ValueAnimator stubanim = ObjectAnimator.ofFloat(0, 1);
                    stubanim.setDuration(animationParams.getDurationMs() / 2);
                    stubanim.addUpdateListener(valueAnimator -> {
                        boolean shouldRm = false;
                        if (messageRect.top != lastTop) {
                            lastTop = messageRect.top;
                        } else {
                            shouldRm = true;
                        }
                        if (shouldRm) messageCell.endAnimationMove();
                    });
                    stubanim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            messageCell.endAnimationMove();
                        }
                    });
                    stubanim.start();
                }
            }
        });
        this.setMessageObject(messageObject, null, false, false);
        additionalHeight = (messageObject.replyMessageObject != null ? activity.getChatActivityEnterView().getAddedHeight() : 0);

        animator.setDuration(animationParams.getDurationMs());
        this.activity.getChatListItemAnimator().setScrollDuration(animationParams.getDurationForListAnimator());

        attachmentCount = activity.getAttachmentCount();

        switch (animationParams.animationType) {
            case VoiceMessage:
                getRadialProgress().setOverrideAlpha(0);
                activity.getChatActivityEnterView().getTextEditBounds(viewRect);
                break;
            case VideoMessage:
                activity.getCameraViewBounds(viewRect);
                break;
            case Gif:
            case Sticker:
                activity.getChatActivityEnterView().getStickerBounds(viewRect);
                break;
            case Attachment:
                activity.getFirstPhotosRect(viewRect);
                break;
            default:
                activity.getChatActivityEnterView().getTextEditBounds(viewRect);
                break;
        }

        this.backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.backPaint.setStyle(Paint.Style.FILL);
        if (animationParams.animationType == AnimationItemType.Gif) {
            backPaint.setColor(Theme.getColor(Theme.key_chat_outBubble));
        }

    }

    public static int getValue(int start, int end, float f) {
        return Math.round(start * (1 - f) + end * f);
    }

    public static float getValue(float start, float end, float f) {
        return start * (1 - f) + end * f;
    }

    public AnimationItemType getAnimationType() {
        return animationParams.animationType;
    }

    public void setMessageCell(ChatMessageCell messageCell) {
        if (this.messageCell == messageCell) {
            return;
        }
        this.messageCell = messageCell;

        this.messageRect.set(messageCell.getLeft(), messageCell.getTop(), messageCell.getRight(), messageCell.getBottom());
        this.messageBackWidth = messageCell.getBackgroundWidth();

        switch (animationParams.animationType) {
            case Sticker:
            case Emoji:
            case Gif:
            case Attachment:
                ImageLoader.getInstance().loadImageForImageReceiver(messageCell.getPhotoImage());
                break;
            case VideoMessage:
                activity.getInstantCameraView().getPaint().setAlpha(0);
                break;
        }
        animator.start();
    }

    public void updateAnimation() {
        if (animationParams.animationType != AnimationItemType.VideoMessage) {
            if (messageRect.top >= messageCell.getTop() + messageCell.getTranslationY()) messageCell.endAnimationMove();
        }
    }

    private int startBackColor;
    private int endBackColor;
    private int startReplyCaptionColor;
    private int endReplyCaptionColor;
    private int currentReplyCaptionColor;
    private int startReplyTextColor;
    private int endReplyTextColor;
    private int currentReplyTextColor;
    private int startReplyLineColor;
    private int endReplyLineColor;
    private int currentReplyLineColor;
    private int startUrlColor;
    private int endUrlColor;
    private int currentUrlColor;
    private int startDomainNameColor;
    private int endDomainNameColor;
    private int currentDomainNameColor;
    private int startPreviewLineColor;
    private int endPreviewLineColor;
    private int currentPreviewLineColor;

    private void startAnimation() {
        int x = viewRect.right - messageRect.width();
        int y = 0;
        for (AnimationState data: animationData) {
            switch (data.interpolatorParams.type) {
                case XPosition:
                    if (animationParams.animationType == AnimationItemType.VideoMessage) {
                        x += padding;
                    }
                    data.startValue = x;
                    data.endValue = messageRect.left - activity.getChatListView().getLeft();
                    break;
                case YPosition:
                    int correction = 0;

                    if (animationParams.animationType == AnimationItemType.ShortText ||
                            animationParams.animationType == AnimationItemType.LongText ||
                            animationParams.animationType == AnimationItemType.Link || animationParams.animationType == AnimationItemType.VoiceMessage) {

                        boolean isFirstInBlock = false; //TODO
                        if ((getMessageObject().replyMessageObject == null || isFirstInBlock)) {
                            correction = AndroidUtilities.dp(1);
                        }
                    }

                    int endY = messageRect.top + activity.getChatListView().getTop() + additionalHeight - correction;
                    switch (animationParams.animationType) {
                        case Link:
                            y = viewRect.top;
                            if (getMessageObject().replyMessageObject != null)
                                y -= (additionalHeight - padding);
                            else {
                                y += padding / 2;
                                endY += activity.getChatActivityEnterView().getAddedHeight();
                            }
                            break;
                        default:
                            y = viewRect.bottom - messageRect.height() - AndroidUtilities.dp(1);
                            if (animationParams.animationType == AnimationItemType.Emoji || animationParams.animationType == AnimationItemType.VideoMessage) {
                                y += padding / 2;
                            }
                            break;
                    }
                    data.startValue = y;
                    data.endValue = endY;

                    break;
                case ColorChange:

                    startBackColor = Theme.getColor(Theme.key_windowBackgroundWhite);
                    endBackColor = Theme.getColor(Theme.key_chat_outBubble);

                    if (animationParams.animationType == AnimationItemType.Link) {
                        startUrlColor = Theme.getColor(Theme.key_dialogTextBlack);
                        startDomainNameColor = Theme.getColor(Theme.key_chat_replyPanelName);
                        startPreviewLineColor = Theme.getColor(Theme.key_chat_replyPanelLine);

                        endUrlColor = Theme.getColor(Theme.key_chat_messageLinkOut);
                        endDomainNameColor = Theme.getColor(Theme.key_chat_outSiteNameText);
                        endPreviewLineColor = Theme.getColor(Theme.key_chat_outPreviewLine);
                    }
                    if (getMessageObject().replyMessageObject != null) {
                        startReplyCaptionColor = Theme.getColor(Theme.key_chat_replyPanelName);
                        startReplyTextColor = Theme.getColor(Theme.key_chat_replyPanelMessage);
                        startReplyLineColor = Theme.getColor(Theme.key_chat_replyPanelLine);

                        if (getMessageObject().shouldDrawWithoutBackground()) {
                            startReplyCaptionColor = AnimationDrawingUtils.setColorAlpha(startReplyCaptionColor, 0);
                            startReplyTextColor = AnimationDrawingUtils.setColorAlpha(startReplyTextColor, 0);
                            startReplyLineColor = AnimationDrawingUtils.setColorAlpha(startReplyLineColor, 0);

                            endReplyCaptionColor = Theme.getColor(Theme.key_chat_stickerReplyNameText);
                            endReplyTextColor = Theme.getColor(Theme.key_chat_stickerReplyMessageText);
                            endReplyLineColor = Theme.getColor(Theme.key_chat_stickerReplyLine);
                        } else {
                            endReplyCaptionColor = Theme.getColor(Theme.key_chat_outReplyNameText);
                            endReplyTextColor = Theme.getColor(Theme.key_chat_outReplyMessageText);
                            endReplyLineColor = Theme.getColor(Theme.key_chat_outReplyLine);
                        }
                    }
                    data.startValue = 0;
                    data.endValue = 1;
                    break;
                case TimeAppears:
                    data.startValue = 0;
                    data.endValue = 1;
                    break;
                case AttachScale:
                    ImageReceiver image3 = messageCell.getPhotoImage();
                    if (image3 != null) {
                        float scale = 0.33f;

                        float imageHeight = image3.getImageHeight();
                        float imageWidth = image3.getImageWidth();
                        float sx = messageRect.width() - viewRect.width() + activity.getChatActivityEnterView().getMessageEditText().getPaddingLeft();
                        float sy = viewRect.height();

                        startRect.set((int) sx, (int) sy, (int)(sx + scale * imageWidth), (int)(sy + scale * imageHeight));

                        int ex = (int) image3.getImageX() + messageRect.width() - messageBackWidth - padding / 2;
                        endRect.set(ex, (int) image3.getImageY(), (int)(ex + imageWidth), (int)(image3.getImageY() + imageHeight));
                    }
                    if (getMessageObject().replyMessageObject != null) {
                        startReplyOffset.set(messageRect.width() - viewRect.width() - AndroidUtilities.dp(23), messageRect.height() - viewRect.height() - padding - additionalHeight);
                    }
                    data.startValue = 0;
                    data.endValue = 1;
                    break;
                case GifScale:
                    ImageReceiver image2 = messageCell.getPhotoImage();
                    if (image2 != null) {
                        float scale = 0.33f;

                        float imageHeight = image2.getImageHeight();
                        float imageWidth = image2.getImageWidth();
                        float sx = messageRect.width() - viewRect.width() + activity.getChatActivityEnterView().getMessageEditText().getPaddingLeft();
                        float sy = viewRect.height();

                        startRect.set((int) sx, (int) sy, (int)(sx + scale * imageWidth), (int)(sy + scale * imageHeight));

                        int ex = (int) image2.getImageX() + messageRect.width() - messageBackWidth - padding / 2;
                        endRect.set(ex, (int) image2.getImageY(), (int)(ex + imageWidth), (int)(image2.getImageY() + imageHeight));
                    }
                    if (getMessageObject().replyMessageObject != null) {
                        startReplyOffset.set(messageRect.width() - viewRect.width() - AndroidUtilities.dp(23), messageRect.height() - viewRect.height() - padding - additionalHeight);
                    }
                    data.startValue = 0;
                    data.endValue = 1;
                    break;
                case EmojiScale:
                    ImageReceiver image = messageCell.getPhotoImage();
                    if (image != null) {
                        float scale = 0.22f;
                        if (animationParams.animationType == AnimationItemType.Sticker) {
                            scale = 0.33f;
                        }

                        float imageHeight = image.getImageHeight();
                        float imageWidth = image.getImageWidth();
                        float sx = messageRect.width() - viewRect.width() + activity.getChatActivityEnterView().getMessageEditText().getPaddingLeft();
                        float sy = messageRect.height() - viewRect.height() + activity.getChatActivityEnterView().getMessageEditText().getPaddingTop();

                        if (animationParams.animationType == AnimationItemType.Sticker) {
                            sy = viewRect.height();
                        }

                        startRect.set((int) sx, (int) sy, (int)(sx + scale * imageWidth), (int)(sy + scale * imageHeight));

                        int ex = (int) image.getImageX() + messageRect.width() - messageBackWidth - padding / 2;
                        endRect.set(ex, (int) image.getImageY(), (int)(ex + imageWidth), (int)(image.getImageY() + imageHeight));
                    }
                    if (getMessageObject().replyMessageObject != null) {
                        startReplyOffset.set(messageRect.width() - viewRect.width() - AndroidUtilities.dp(23), messageRect.height() - viewRect.height() - padding - additionalHeight);
                    }
                    data.startValue = 0;
                    data.endValue = 1;
                    break;
                case BubbleShape:
                    data.startValue = 0;
                    data.endValue = 1;

                    boolean hasAttaches = getMessageObject().type == 1 || getMessageObject().type == 3;

                    if (animationParams.animationType == AnimationItemType.Link || hasAttaches || animationParams.animationType == AnimationItemType.Gif) {
                        startRect.set(messageRect.width() - viewRect.width() + activity.getChatActivityEnterView().getMessageEditText().getPaddingLeft() - padding / 2,
                                activity.getChatActivityEnterView().getMessageEditText().getPaddingTop() - padding,
                                messageRect.width() - padding,
                                activity.getChatActivityEnterView().getMessageEditText().getHeight() - activity.getChatActivityEnterView().getMessageEditText().getPaddingBottom() + padding);
                        if (getMessageObject().replyMessageObject != null) {
                            startRect.top += additionalHeight;
                            startRect.bottom += additionalHeight;
                        } else {
                            startRect.top += padding / 2;
                        }
                    } else {
                        startRect.set(messageRect.width() - viewRect.width() + activity.getChatActivityEnterView().getMessageEditText().getPaddingLeft() - padding / 2,
                                messageRect.height() - viewRect.height() + activity.getChatActivityEnterView().getMessageEditText().getPaddingTop(),
                                messageRect.width() - padding,
                                messageRect.height());
                    }

                    endRect.set(messageRect.width() - messageBackWidth,
                            -padding,
                            messageRect.width(),
                            messageRect.height());

                    startBackWidth = viewRect.width() + padding;
                    endBackWidth = messageBackWidth;

                    startClipRadius = padding;
                    endClipRadius = padding;
                    break;
                case TextScale:
                    this.textPositionState = data;
                    startTextSize = activity.getChatActivityEnterView().getMessageEditText().getTextSize();
                    endTextSize = Theme.chat_msgTextPaint.getTextSize();
                    data.startValue = 0;
                    data.endValue = 1;
                    break;
                case VoiceScale:
                    break;
            }
        }
        animationMove(0);
        setVisibility(VISIBLE);
        activity.getBackgroundView().startAnimation(AnimationParamType.BackgroundGradientChangeOnSendMessage);
    }

    private void animationMove(float value) {
        currentAnimFactor = value;
        int x = getLeft();
        int y = getTop();
        for (AnimationState data: animationData) {
            data.updateValue(value);
            switch (data.interpolatorParams.type) {
                case XPosition:
                    x = (int) data.currentValue;
                    break;
                case YPosition:
                    y = (int) data.currentValue;
                    break;
                case ColorChange:
                    backPaint.setColor(AnimationDrawingUtils.getColor(startBackColor, endBackColor, data.currentValue));

                    if (animationParams.animationType == AnimationItemType.Link) {
                        currentUrlColor = AnimationDrawingUtils.getColor(startUrlColor, endUrlColor, data.currentValue);
                        currentDomainNameColor = AnimationDrawingUtils.getColor(startDomainNameColor, endDomainNameColor, data.currentValue);
                        currentPreviewLineColor = AnimationDrawingUtils.getColor(startPreviewLineColor, endPreviewLineColor, data.currentValue);
                    }
                    if (getMessageObject().replyMessageObject != null) {
                        currentReplyCaptionColor = AnimationDrawingUtils.getColor(startReplyCaptionColor, endReplyCaptionColor, data.currentValue);
                        currentReplyTextColor = AnimationDrawingUtils.getColor(startReplyTextColor, endReplyTextColor, data.currentValue);
                        currentReplyLineColor = AnimationDrawingUtils.getColor(startReplyLineColor, endReplyLineColor, data.currentValue);
                    }
                    break;
                case TimeAppears:
                    setTimeAlpha(data.currentValue);
                    break;
                case GifScale:
                case AttachScale:
                case EmojiScale:
                case VoiceScale:
                    currentRect.left = getValue(startRect.left, endRect.left, data.currentValue);
                    currentRect.top = getValue(startRect.top, endRect.top, data.currentValue);
                    currentRect.right = getValue(startRect.right, endRect.right, data.currentValue);
                    currentRect.bottom = getValue(startRect.bottom, endRect.bottom, data.currentValue);
                    if (getMessageObject().replyMessageObject != null) {
                        replyStartOffsetX = getValue(startReplyOffset.x, 0, data.currentValue);
                        replyStartOffsetY = getValue(startReplyOffset.y, 0, data.currentValue);
                        replyBackAlpha = data.currentValue;
                    }

                    break;
                case BubbleShape:
                    backgroundWidth = getValue(startBackWidth, endBackWidth, data.currentValue);
                    currentClipRadius = getValue(startClipRadius, endClipRadius, data.currentValue);

                    currentRect.left = getValue(startRect.left, endRect.left, data.currentValue);
                    currentRect.top = getValue(startRect.top, endRect.top, data.currentValue);
                    currentRect.right = getValue(startRect.right, endRect.right, data.currentValue);
                    currentRect.bottom = Math.min(viewRect.bottom - y - padding / 2, getValue(startRect.bottom, endRect.bottom, data.currentValue));
                    break;
                case TextScale:
                    break;
            }
        }

        layout(x, y, x + messageRect.width(), y + messageRect.height());
        invalidate();
    }

    public void endAnimationMove() {
        setVisibility(INVISIBLE);
        animator.cancel();
        messageAnimationHolder.views.remove(this);
        activity.getContentView().removeView(this);

        if (animationParams.animationType == AnimationItemType.VoiceMessage) {
            getRadialProgress().setOverrideAlpha(1f);
        }
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        if (animationParams.animationType == AnimationItemType.Attachment && attachmentCount > 1) return;
        Theme.MessageDrawable current = getCurrentBackgroundDrawable();

        if (animationParams.animationType == AnimationItemType.Gif && currentAnimFactor <= 0.8f) return;
        if (animationParams.animationType == AnimationItemType.Attachment && attachmentCount == 1 && currentAnimFactor <= 0.5f) return;

        if (current != null) {
            if (currentAnimFactor > 0.5f) current.draw(canvas);
            current.draw(canvas, backPaint);
        }
    }

    @SuppressLint("CheckResult")
    @Override
    protected void onDraw(Canvas canvas) {
        // skip channel messages
        if (ChatObject.isChannel(currentChat)) return;

        if (currentAnimFactor > 0.1f && animationParams.animationType == AnimationItemType.VoiceMessage) {
            if (!messageCell.getVoiceTransitionInProgress()) {
                getRadialProgress().setOverrideAlpha(1f);
            }
        }

        canvas.save();

        if (textPositionState != null) Theme.chat_msgTextPaint.setTextSize(getValue(startTextSize, endTextSize, textPositionState.currentValue));

        this.listViewRect.set(this.activity.getChatListView().getLeft(), this.activity.getActionBar().getBottom(),
                this.activity.getChatListView().getRight(), this.activity.getChatActivityEnterView().getBottom());
        this.clipRect.set(0, this.getTop(), this.activity.getChatListView().getWidth(), this.getBottom());
        final Rect clipRect = this.clipRect;
        clipRect.setIntersect(clipRect, this.listViewRect);
        final Rect clipRect2 = this.clipRect;
        clipRect2.top -= this.getTop();
        clipRect2.bottom += this.getBottom();
        canvas.clipRect(this.clipRect);

        switch (animationParams.animationType) {
            case Link:
            case LongText:
            case ShortText:
            case Attachment:
                AnimationDrawingUtils.roundRect(clipPath, currentRect, currentClipRadius, currentClipRadius, currentClipRadius, currentClipRadius);
                canvas.clipPath(clipPath);
                break;
        }

        super.onDraw(canvas);

        if (textPositionState != null) Theme.chat_msgTextPaint.setTextSize(endTextSize);

        canvas.restore();
    }

    @Override
    protected void drawContent(Canvas canvas) {
        if (this.animationParams.animationType == AnimationItemType.Emoji || this.animationParams.animationType == AnimationItemType.Sticker ||
                this.animationParams.animationType == AnimationItemType.Gif || this.animationParams.animationType == AnimationItemType.Attachment) {
            ImageReceiver image = messageCell.getPhotoImage();
            if (image != null) {
                image.setImageCoords(currentRect.left, currentRect.top, currentRect.width(), currentRect.height());
                image.setCurrentAlpha(1);
                image.draw(canvas);
            }
        } else {
            if (animationParams.animationType == AnimationItemType.Link) {
                Theme.chat_replyNamePaint.setColor(currentDomainNameColor);
                Theme.chat_replyLinePaint.setColor(currentPreviewLineColor);
                Theme.chat_msgTextPaint.linkColor = currentUrlColor;
            }
            super.drawContent(canvas);
        }
    }

    @Override
    public void drawNamesLayout(Canvas canvas, float alpha) {
        if (getMessageObject().replyMessageObject != null) {
            Theme.chat_replyNamePaint.setColor(currentReplyCaptionColor);
            Theme.chat_replyTextPaint.setColor(currentReplyTextColor);
            Theme.chat_replyLinePaint.setColor(currentReplyLineColor);
        }

        super.drawNamesLayout(canvas, alpha);
    }
}
