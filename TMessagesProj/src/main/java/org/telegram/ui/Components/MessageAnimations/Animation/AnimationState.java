package org.telegram.ui.Components.MessageAnimations.Animation;

import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationInterpolation;

public class AnimationState {

    public final AnimationInterpolation interpolatorParams;
    public float startValue;
    public float endValue;
    public float currentValue;

    public AnimationState(AnimationInterpolation interpolatorParams) {
        this.interpolatorParams = interpolatorParams;
    }

    public void updateValue(float animateValue) {
        this.currentValue = this.interpolatorParams.getAnimationValue(startValue, endValue, animateValue);
    }
}
