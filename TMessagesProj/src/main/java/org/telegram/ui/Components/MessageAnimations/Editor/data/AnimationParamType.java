package org.telegram.ui.Components.MessageAnimations.Editor.data;

public enum AnimationParamType {
    XPosition("xp"), YPosition("yp"),
    BubbleShape("bs"), TextScale("ts"), ColorChange("cc"),
    TimeAppears("ta"),
    EmojiScale("es"), VoiceScale("vs"), GifScale("gs"), AttachScale("as"), // TODO remake to just scale :D
    BackgroundGradientChangeOnSendMessage("bc1"),
    BackgroundGradientChangeOnOpenChat("bc2"),
    BackgroundGradientChangeOnJumpToMessage("bc3");

    private String value;

    AnimationParamType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String alias() {
        return this.getValue();
    }

    public static AnimationParamType fromAlias(String value) {
        for(AnimationParamType v : values()) if(v.getValue().equalsIgnoreCase(value)) return v;
        throw new IllegalArgumentException();
    }
}


