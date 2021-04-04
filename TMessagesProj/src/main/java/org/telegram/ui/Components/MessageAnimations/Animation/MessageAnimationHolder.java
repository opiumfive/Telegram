package org.telegram.ui.Components.MessageAnimations.Animation;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationItemType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamType;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParamsHolder;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationParams;

import java.util.ArrayList;

public class MessageAnimationHolder {

    protected final ChatActivity activity;
    protected final ArrayList<ChatMessageCellAnimated> views = new ArrayList<>();

    public MessageAnimationHolder(ChatActivity activity) {
        this.activity = activity;
    }

    public void addMessage(MessageObject messageObject) {
        messageObject.animateSendingMessage = true;
        ChatMessageCellAnimated chatMessageCellAnimated = new ChatMessageCellAnimated(this, messageObject);
        views.add(chatMessageCellAnimated);
    }

    public ChatMessageCellAnimated getMessageView(MessageObject messageObject) {
        for (ChatMessageCellAnimated view : views) {
            if (messageObject == view.getMessageObject()) return view;
        }
        return null;
    }

    protected AnimationParams getAnimationParamsForMessage(MessageObject messageObject) {
        switch (messageObject.type) {
            case 13:
                return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Sticker);
            case 15:
                if (messageObject.emojiAnimatedSticker == null) {
                    return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Sticker);
                } else {
                    return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Emoji);
                }
            case 8:
                return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Gif);
            case 2:
                return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.VoiceMessage);
            case 5:
                return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.VideoMessage);
            case 1:
            case 3:
                return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Attachment);
        }
        if (messageObject.linkDescription != null) {
            return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.Link);
        }
        if (messageObject.linesCount > activity.getChatActivityEnterView().getMessageEditText().getMaxLines()) {
            return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.LongText);
        }
        return AnimationParamsHolder.instance.getAnimationParamsForType(AnimationItemType.ShortText);
    }

    public void animateJustBackground(AnimationParamType paramType) {
        activity.getBackgroundView().startAnimation(paramType);
    }
}
