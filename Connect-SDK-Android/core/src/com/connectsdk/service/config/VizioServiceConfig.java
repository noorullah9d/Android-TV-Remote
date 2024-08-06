package com.connectsdk.service.config;

import org.json.JSONObject;

public class VizioServiceConfig extends ServiceConfig{
    public static final String AUTH = "auth";
    public String auth;

    public VizioServiceConfig(String str) {
        super(str);
    }

    public String getAuth() {
        return this.auth;
    }

    public void setAuth(String str) {
        this.auth = str;
        notifyUpdate();
    }

    @Override // com.connectsdk.service.config.ServiceConfig
    public JSONObject toJSONObject() {
        JSONObject jSONObject = super.toJSONObject();
        try {
            jSONObject.put(AUTH, this.auth);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jSONObject;
    }

    public VizioServiceConfig(String str, String str2) {
        super(str);
        this.auth = str2;
    }

    public VizioServiceConfig(JSONObject jSONObject) {
        super(jSONObject);
        this.auth = jSONObject.optString(AUTH, null);
    }

}
