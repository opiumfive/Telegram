package org.telegram.ui.Components.MessageAnimations.Editor.data;

import android.content.SharedPreferences;

public class AnimationInterpolation {

    public final AnimationParamType type;
    public float startInterpolation;
    public float endInterpolation;
    public float startDuration;
    public float endDuration;
    public float duration; // for background duration only

    public AnimationInterpolation(AnimationParamType type, boolean init) {
        this.type = type;
        if (init) this.reset();
    }

    public AnimationInterpolation(AnimationParamType type) {
        this(type, true);
    }

    public AnimationInterpolation clone() {
        AnimationInterpolation info = new AnimationInterpolation(type);
        info.startInterpolation = startInterpolation;
        info.endInterpolation = endInterpolation;
        info.startDuration = startDuration;
        info.endDuration = endDuration;
        info.duration = duration;
        return info;
    }

    public void apply(AnimationInterpolation info) {
        startInterpolation = info.startInterpolation;
        endInterpolation = info.endInterpolation;
        startDuration = info.startDuration;
        endDuration = info.endDuration;
        duration = info.duration;
    }

    public void save(String prefix, SharedPreferences.Editor data) {
        data.putFloat(prefix + "startDuration", startDuration);
        data.putFloat(prefix + "endDuration", endDuration);
        data.putFloat(prefix + "startInterpolation", startInterpolation);
        data.putFloat(prefix + "endInterpolation", endInterpolation);
        data.putFloat(prefix + "durationBackground", duration);
    }

    public void load(String prefix, SharedPreferences data) {
        startDuration = data.getFloat(prefix + "startDuration", 0);
        endDuration = data.getFloat(prefix + "endDuration", 1);
        startInterpolation = data.getFloat(prefix + "startInterpolation", 0);
        endInterpolation = data.getFloat(prefix + "endInterpolation", 1);
        duration = data.getFloat(prefix + "durationBackground", 60f);
    }

    public boolean compare(AnimationInterpolation info) {
        return startDuration == info.startDuration && endDuration == info.endDuration &&
                startInterpolation == info.startInterpolation && endInterpolation == info.endInterpolation &&
                duration == info.duration;
    }

    public void reset() {
        switch (this.type) {
            case YPosition:
                this.startInterpolation = 0.66f;
                this.endInterpolation = 0.66f;
                this.startDuration = 0;
                this.endDuration = 1;
                break;
            case XPosition:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0;
                this.endDuration = 1f;
                break;
            case BubbleShape:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0;
                this.endDuration = 0.33f;
                break;
            case TextScale:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0;
                this.endDuration = 0.33f;
                break;
            case ColorChange:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 0.33f;
                this.startDuration = 0;
                this.endDuration = 0.5f;
                break;
            case TimeAppears:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 0.33f;
                this.startDuration = 0;
                this.endDuration = 0.5f;
                break;
            case EmojiScale:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0.166f;
                this.endDuration = 0.50f;
                break;
            case VoiceScale:
                this.startInterpolation = 0;
                this.endInterpolation = 1;
                this.startDuration = 0f;
                this.endDuration = 1;
                break;
            case GifScale:
                this.startInterpolation = 0;
                this.endInterpolation = 1;
                this.startDuration = 0f;
                this.endDuration = 1;
                break;
            case AttachScale:
                this.startInterpolation = 0;
                this.endInterpolation = 1;
                this.startDuration = 0f;
                this.endDuration = 1;
                break;
            case BackgroundGradientChangeOnSendMessage:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0f;
                this.endDuration = 1;
                this.duration = 60f;
                break;
            case BackgroundGradientChangeOnOpenChat:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0f;
                this.endDuration = 1;
                this.duration = 60f;
                break;
            case BackgroundGradientChangeOnJumpToMessage:
                this.startInterpolation = 0.33f;
                this.endInterpolation = 1;
                this.startDuration = 0f;
                this.endDuration = 1;
                this.duration = 60f;
                break;
        }
    }

    private float getBezierCoordinateX(float time) {
        float start = (startInterpolation - startInterpolation * 0.01f);
        float end = 1 - (endInterpolation - endInterpolation * 0.01f);
        float x = time;
        float z;
        for (int i = 1; i < 14; i++) {
            float c = 3 * start;
            float b = 3 * (end - start) - c;
            float a = 1 - c - b;
            z = x * (c + x * (b + x * a)) - time;
            if (Math.abs(z) < 1e-3) break;
            x -= z / (c + x * (2 * b + 3 * a * x));
        }
        return x;
    }

    private static float getBezierCoordinateY(float time) {
        float c = 0.03f;
        float b = 3 * 0.98f - c;
        float a = 1 - c - b;
        return time * (c + time * (b + time * a));
    }

    public float interpolate(float start, float end, float f) {
        return start + getBezierCoordinateY(getBezierCoordinateX(f)) * (end - start);
    }

    public float getAnimationValue(float start, float end, float animateValue) {
        if (animateValue >= startDuration && animateValue <= endDuration) {
            float max = endDuration - startDuration;
            float pos = animateValue - startDuration;
            return interpolate(start, end, pos / max);
        }
        if (animateValue < startDuration) return start;
        return end;
    }
}
