package org.telegram.ui.Components.MessageAnimations.Editor.data;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AnimationParams implements Iterable<AnimationInterpolation> {

    public final AnimationItemType animationType;
    private final ArrayList<AnimationInterpolation> animationInfos = new ArrayList<>();
    private float duration;

    private AnimationParams(AnimationItemType animationType, boolean init) {
        this.animationType = animationType;

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (preferences.getBoolean(getPrefix(), false)) {
            load(getPrefix(), preferences);
        } else if (init) {
            initDefault();
        }
    }

    public AnimationParams(AnimationItemType animationType) {
        this(animationType, true);
    }

    protected void initDefault() {
        if (animationType == AnimationItemType.Background) {
            animationInfos.add(new AnimationInterpolation(AnimationParamType.BackgroundGradientChangeOnSendMessage));
            animationInfos.add(new AnimationInterpolation(AnimationParamType.BackgroundGradientChangeOnOpenChat));
            animationInfos.add(new AnimationInterpolation(AnimationParamType.BackgroundGradientChangeOnJumpToMessage));
        } else {
            animationInfos.add(new AnimationInterpolation(AnimationParamType.YPosition));
            animationInfos.add(new AnimationInterpolation(AnimationParamType.XPosition));
            animationInfos.add(new AnimationInterpolation(AnimationParamType.TimeAppears));
            animationInfos.add(new AnimationInterpolation(AnimationParamType.ColorChange));

            switch (this.animationType) {
                case ShortText:
                case LongText:
                case Link:
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.BubbleShape));
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.TextScale));
                    break;
                case Emoji:
                case Sticker:
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.EmojiScale));
                    break;
                case VideoMessage:
                    break;
                case Gif:
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.BubbleShape));
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.GifScale));
                    break;
                case Attachment:
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.AttachScale));
                    break;
                case VoiceMessage:
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.BubbleShape));
                    animationInfos.add(new AnimationInterpolation(AnimationParamType.VoiceScale));
                    break;
            }
        }
        initDefaultDuration();
    }

    private void initDefaultDuration() {
        duration = animationType == AnimationItemType.Background || animationType == AnimationItemType.Attachment ? 60f : 30f;
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public long getDurationMs() {
        return (long) (1000 * duration / 60);
    }

    public long getDurationForListAnimator() {
        long dur = getDurationMs();


        AnimationInterpolation animationInterpolation = getInterpolation(AnimationParamType.YPosition);
        if (animationInterpolation != null && animationInterpolation.endDuration <= 0.90f) {
            return (long) (animationInterpolation.endDuration * getDurationMs());
        }

        if (dur > 1900) return dur - 950;
        if (dur > 1000) return dur - 350;

        return dur;
    }

    public Iterator<AnimationInterpolation> iterator() {
        return animationInfos.iterator();
    }

    public AnimationInterpolation getInterpolation(AnimationParamType type) {
        for (AnimationInterpolation info : animationInfos) {
            if (info.type == type) return info;
        }
        return null;
    }

    public boolean compare(AnimationParams setting) {
        if (duration == setting.duration) {
            for (AnimationInterpolation info : animationInfos) {
                if (!info.compare(setting.getInterpolation(info.type))) return false;
            }
            return true;
        }
        return false;
    }

    protected void copyTo(AnimationParams setting) {
        setting.duration = this.duration;
        setting.animationInfos.clear();
        for (AnimationInterpolation info : animationInfos) {
            setting.animationInfos.add(info.clone());
        }
    }

    public AnimationParams clone() {
        AnimationParams setting = new AnimationParams(animationType, false);
        copyTo(setting);
        return setting;
    }

    public void apply(AnimationParams setting) {
        setting.copyTo(this);
        SharedPreferences.Editor data = MessagesController.getGlobalMainSettings().edit();
        save(getPrefix(), data);
        data.apply();
    }

    public void restore() {
        for (AnimationInterpolation info : animationInfos) info.reset();
        initDefaultDuration();
    }

    public List<String> exportAsStrings() {
        List<String> result = new ArrayList<>(animationInfos.size() + 1);
        String prefix = animationType.alias();
        result.add(prefix + "_d=" + duration);
        for (int i = 0; i < animationInfos.size(); i++) {
            AnimationInterpolation info = animationInfos.get(i);
            result.add(prefix + "_" + info.type.alias() + "_sd=" + info.startDuration);
            result.add(prefix + "_" + info.type.alias() + "_ed=" + info.endDuration);
            result.add(prefix + "_" + info.type.alias() + "_si=" + info.startInterpolation);
            result.add(prefix + "_" + info.type.alias() + "_ei=" + info.endInterpolation);
            if (info.type == AnimationParamType.BackgroundGradientChangeOnJumpToMessage ||
                    info.type == AnimationParamType.BackgroundGradientChangeOnSendMessage ||
                    info.type == AnimationParamType.BackgroundGradientChangeOnOpenChat) {
                result.add(prefix + "_" + info.type.alias() + "_db=" + info.duration);
            }
        }

        return result;
    }

    protected String getPrefix() {
        return "MessageAnimations_" + animationType;
    }

    protected void save(String prefix, SharedPreferences.Editor data) {
        data.putBoolean(prefix, true);
        data.putFloat(prefix + "duration", duration);
        data.putInt(prefix + "size", animationInfos.size());
        for (int i = 0; i < animationInfos.size(); i++) {
            AnimationInterpolation info = animationInfos.get(i);
            data.putInt(prefix + i + "type", info.type.ordinal());
            info.save(prefix + i, data);
        }
    }

    protected void load(String prefix, SharedPreferences data) {
        duration = data.getFloat(prefix + "duration", 30);
        int size = data.getInt(prefix + "size", 0);
        for (int i = 0; i < size; i++) {
            AnimationParamType type = AnimationParamType.values()[data.getInt(prefix + i + "type", 0)];
            AnimationInterpolation info = new AnimationInterpolation(type, false);
            info.load(prefix + i, data);
            animationInfos.add(info);
        }
    }
}

