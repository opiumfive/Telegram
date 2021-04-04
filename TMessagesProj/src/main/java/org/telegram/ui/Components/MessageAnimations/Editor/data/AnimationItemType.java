package org.telegram.ui.Components.MessageAnimations.Editor.data;

public enum AnimationItemType {

    Background("b"),
    ShortText("st"),
    LongText("lt"),
    Link("l"),
    Emoji("e"),
    Sticker("s"),
    VoiceMessage("v1"),
    VideoMessage("v2"),
    Gif("g"),
    Attachment("a");

    private String value;

    AnimationItemType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String alias() {
        return this.getValue();
    }

    public static AnimationItemType fromAlias(String value) {
        for(AnimationItemType v : values()) if(v.getValue().equalsIgnoreCase(value)) return v;
        throw new IllegalArgumentException();
    }
}
