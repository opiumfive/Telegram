package org.telegram.ui.Components.MessageAnimations.Editor.data;

import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.List;

public class BackgroundGradientParams extends AnimationParams {

    public int color1;
    public int color2;
    public int color3;
    public int color4;
    public int tiltActivationAngle = 15;
    public float filterfactor = 0.1f;

    public BackgroundGradientParams() {
        super(AnimationItemType.Background);
    }

    protected void initDefault() {
        super.initDefault();
        initDefaultColors();
    }

    private void initDefaultColors() {
        color1 = Color.argb(255, 0xff, 0xf6, 0xc5);
        color2 = Color.argb(255, 0x72, 0x9c, 0x74);
        color3 = Color.argb(255, 0xf6, 0xe6, 0x7b);
        color4 = Color.argb(255, 0x26, 0x61, 0x44);
    }

    @Override
    protected void copyTo(AnimationParams setting) {
        super.copyTo(setting);
        BackgroundGradientParams s = (BackgroundGradientParams) setting;
        s.color1 = color1;
        s.color2 = color2;
        s.color3 = color3;
        s.color4 = color4;
    }

    @Override
    public AnimationParams clone() {
        BackgroundGradientParams setting = new BackgroundGradientParams();
        copyTo(setting);
        return setting;
    }

    @Override
    public boolean compare(AnimationParams setting) {
        BackgroundGradientParams bs = (BackgroundGradientParams) setting;
        if (color1 == bs.color1 && color2 == bs.color2 && color3 == bs.color3 && color4 == bs.color4) return super.compare(setting);
        return false;
    }

    @Override
    public List<String> exportAsStrings() {
        List<String> result = super.exportAsStrings();
        String prefix = animationType.alias();
        result.add(prefix + "_c1=" + color1);
        result.add(prefix + "_c2=" + color2);
        result.add(prefix + "_c3=" + color3);
        result.add(prefix + "_c4=" + color4);
        return result;
    }

    @Override
    public void restore() {
        initDefaultColors();
        super.restore();
    }

    @Override
    protected void save(String prefix, SharedPreferences.Editor data) {
        super.save(prefix, data);
        data.putInt(prefix + "color1", color1);
        data.putInt(prefix + "color2", color2);
        data.putInt(prefix + "color3", color3);
        data.putInt(prefix + "color4", color4);
    }

    @Override
    protected void load(String prefix, SharedPreferences data) {
        super.load(prefix, data);
        color1 = data.getInt(prefix + "color1", 0);
        color2 = data.getInt(prefix + "color2", 0);
        color3 = data.getInt(prefix + "color3", 0);
        color4 = data.getInt(prefix + "color4", 0);
    }
}
