package com.connectsdk.service.vizio;

import android.os.Build;

import com.connectsdk.service.webos.lgcast.common.utils.AppUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;

public enum VizioVirtualKeyCodes {
    NUMBER_0(0, 48),
    NUMBER_1(0, 49),
    NUMBER_2(0, 50),
    NUMBER_3(0, 51),
    NUMBER_4(0, 52),
    NUMBER_5(0, 53),
    NUMBER_6(0, 54),
    NUMBER_7(0, 55),
    NUMBER_8(0, 56),
    NUMBER_9(0, 57),
    DASH(0, 45),
    ENTER(3, 2),
    HOME(4, 15),
    EXIT(4, 3),
    THREED(12, 0),
    SOURCE(7, 1),
    VIEWMODE(6, 0),
    CLOSED_CAPTION(4, 4),
    ASPECT_RATIO(6, 2),
    TOP_MENU(4, 8),
    PLAY(2, 3),
    PAUSE(2, 2),
    REWIND(2, 1),
    FAST_FORWARD(2, 0),
    BACK(4, 0),
    VOLUME_UP(5, 1),
    VOLUME_DOWN(5, 0),
    MUTE(5, 4),
    CHANNEL_UP(8, 1),
    CHANNEL_DOWN(8, 0),
    POWER_ON(11, 1),
    POWER(11, 0),
    KEY_LEFT(3, 1),
    KEY_RIGHT(3, 7),
    KEY_UP(3, 8),
    KEY_DOWN(3, 0),
    INFO(4, 6),
    PREVIOUS_CHANNEL(8, 2),
    DELETE(0, 8);

    private final Integer code;
    private final Integer codeset;

    VizioVirtualKeyCodes(Integer num, Integer num2) {
        this.code = num2;
        this.codeset = num;
    }

    final String STRING_CHARSET_NAME = "UTF-8";

    public String getCode() {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("CODESET", this.codeset);
            jSONObject.put("CODE", this.code);
            jSONObject.put(AppUtil.ACTION, "KEYPRESS");
            JSONArray jSONArray = new JSONArray();
            jSONArray.put(jSONObject);
            JSONObject jSONObject2 = new JSONObject();
            jSONObject2.put("KEYLIST", jSONArray);
            return Build.VERSION.SDK_INT >= 9 ? new String(jSONObject2.toString().getBytes(Charset.forName(STRING_CHARSET_NAME))) : "";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
