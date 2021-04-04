package org.telegram.ui.Components.MessageAnimations.Editor.data;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.MessagesController;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class AnimationParamsHolder implements Iterable<AnimationParams> {

    public final static AnimationParamsHolder instance = new AnimationParamsHolder();
    private final ArrayList<AnimationParams> animationParams = new ArrayList<>();

    private AnimationParamsHolder() {
        animationParams.add(new BackgroundGradientParams());
        animationParams.add(new AnimationParams(AnimationItemType.ShortText));
        animationParams.add(new AnimationParams(AnimationItemType.LongText));
        animationParams.add(new AnimationParams(AnimationItemType.Link));
        animationParams.add(new AnimationParams(AnimationItemType.Emoji));
        animationParams.add(new AnimationParams(AnimationItemType.Sticker));
        animationParams.add(new AnimationParams(AnimationItemType.VoiceMessage));
        animationParams.add(new AnimationParams(AnimationItemType.VideoMessage));
        animationParams.add(new AnimationParams(AnimationItemType.Gif));
        animationParams.add(new AnimationParams(AnimationItemType.Attachment));
    }

    public AnimationParams getAnimationParamsForType(AnimationItemType type) {
        return animationParams.get(type.ordinal());
    }

    public Iterator<AnimationParams> iterator() {
        return animationParams.iterator();
    }

    public void restore() {
        for (AnimationParams params : animationParams) {
            params.restore();
            SharedPreferences.Editor data = MessagesController.getGlobalMainSettings().edit();
            params.save(params.getPrefix(), data);
            data.apply();
        }
    }

    public boolean importParams(String gzippedEncoded) {
        String decompressed = null;

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decode(URLDecoder.decode(gzippedEncoded, "utf-8"), Base64.DEFAULT));
            GZIPInputStream gis = new GZIPInputStream(bis);
            BufferedReader br = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            gis.close();
            bis.close();
            decompressed = sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(decompressed)) return false;

        String[] from = decompressed.split(";");

        for (String param : from) {
            try {
                String[] kv = param.split("=");
                String value = kv[1];
                String keysSum = kv[0];
                String[] keys = keysSum.split("_");
                AnimationItemType animationItemType = AnimationItemType.fromAlias(keys[0]);
                AnimationParams params = getAnimationParamsForType(animationItemType);
                switch (keys[1]) {
                    case "d":
                        params.setDuration(Float.parseFloat(value));
                        break;
                    case "c1":
                        ((BackgroundGradientParams)params).color1 = Integer.parseInt(value);
                        break;
                    case "c2":
                        ((BackgroundGradientParams)params).color2 = Integer.parseInt(value);
                        break;
                    case "c3":
                        ((BackgroundGradientParams)params).color3 = Integer.parseInt(value);
                        break;
                    case "c4":
                        ((BackgroundGradientParams)params).color4 = Integer.parseInt(value);
                        break;
                    default:
                        AnimationParamType animationParamType = AnimationParamType.fromAlias(keys[1]);
                        AnimationInterpolation interpolation = params.getInterpolation(animationParamType);
                        switch (keys[2]) {
                            case "sd":
                                interpolation.startDuration = Float.parseFloat(value);
                                break;
                            case "ed":
                                interpolation.endDuration = Float.parseFloat(value);
                                break;
                            case "si":
                                interpolation.startInterpolation = Float.parseFloat(value);
                                break;
                            case "ei":
                                interpolation.endInterpolation = Float.parseFloat(value);
                                break;
                            case "db":
                                interpolation.duration = Float.parseFloat(value);
                                break;
                        }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (AnimationParams params : animationParams) {
            SharedPreferences.Editor data = MessagesController.getGlobalMainSettings().edit();
            params.save(params.getPrefix(), data);
            data.apply();
        }

        return true;
    }

    public String exportParams() {
        try {
            List<String> result = new ArrayList<>();
            for (AnimationParams params : animationParams) {
                result.addAll(params.exportAsStrings());
            }
            String str = TextUtils.join(";", result);
            Log.e("testexport", str);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(str.length());
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(str.getBytes());
            gzip.close();
            byte[] compressed = bos.toByteArray();
            bos.close();
            String base64 = Base64.encodeToString(compressed, Base64.DEFAULT);
            String res = URLEncoder.encode(base64, "utf-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
            return res;
        } catch (Exception e) {
            return null;
        }
    }
}
