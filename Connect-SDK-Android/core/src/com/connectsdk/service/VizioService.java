package com.connectsdk.service;

import android.os.Build;
import android.text.TextUtils;

import com.amazon.whisperplay.amazonInternal.Account;
import com.bumptech.glide.load.Key;
import com.connectsdk.core.ChannelInfo;
import com.connectsdk.core.Util;
import com.connectsdk.discovery.DiscoveryFilter;
import com.connectsdk.service.capability.CapabilityMethods;
import com.connectsdk.service.capability.KeyControl;
import com.connectsdk.service.capability.Launcher;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.PowerControl;
import com.connectsdk.service.capability.TVControl;
import com.connectsdk.service.capability.VizioKeyCode;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommand;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.command.ServiceSubscription;
import com.connectsdk.service.config.ServiceConfig;
import com.connectsdk.service.config.ServiceDescription;
import com.connectsdk.service.config.VizioServiceConfig;
import com.connectsdk.service.vizio.VizioVirtualKeyCodes;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class VizioService extends DeviceService implements PowerControl, KeyControl, TVControl, MediaControl, VolumeControl {
    public static final String ID = "Vizio";
    public static final String VIZIO_ERROR_CONNECT_BLOCKED = "Pin has been already displayed. Please wait when will be dismissed or enter already displayed PIN";
    public static final String VIZIO_ERROR_CONNECT_EMPTY_PAIRING_PIN = "User has entered empty pairing pin code";
    public static final String VIZIO_ERROR_CONNECT_INVALID_PIN_CODE = "Invalid PIN code";
    public static final String VIZIO_ERROR_CONNECT_NETWROK = "Network error has been occurred";
    public static final String VIZIO_ERROR_GENERAL_EXCEPTION = "General exception has been occurred";
    private static final String kKeyCommand = "/key_command/";
    private static final String kLaunchAppCommand = "/app/launch";
    private static final String kPairingCancel = "/pairing/cancel";
    private static final String kPairingPair = "/pairing/pair";
    private static final String kPairingStart = "/pairing/start";
    private static final String kResultDictonary = "RESULT";
    private static final String kResultStatus = "STATUS";
    private static final String kStatusBlocked = "BLOCKED";
    private static final String kStatusRequiresPairing = "REQUIRES_PAIRING";
    private static final String kStatusSuccess = "SUCCESS";
    public String pairingRequestToken;
    public State state;

    public enum State {
        INITIAL,
        CONNECTING,
        PAIRING,
        PAIRED
    }

    @Override
    public void get3DEnabled(State3DModeListener state3DModeListener) {
    }

    @Override
    public void getChannelList(ChannelListListener channelListListener) {
    }

    @Override
    public void getCurrentChannel(ChannelListener channelListener) {
    }

    @Override
    public void getDuration(DurationListener durationListener) {
    }

    @Override
    public KeyControl getKeyControl() {
        return this;
    }

    @Override
    public MediaControl getMediaControl() {
        return this;
    }

    @Override
    public void getMute(MuteListener muteListener) {
    }

    @Override
    public void getPlayState(PlayStateListener playStateListener) {
    }

    @Override
    public void getPosition(PositionListener positionListener) {
    }

    @Override
    public PowerControl getPowerControl() {
        return this;
    }

    @Override
    public void getProgramInfo(ProgramInfoListener programInfoListener) {
    }

    @Override
    public void getProgramList(ProgramListListener programListListener) {
    }

    @Override
    public TVControl getTVControl() {
        return null;
    }

    @Override
    public CapabilityPriorityLevel getTVControlCapabilityLevel() {
        return null;
    }

    @Override
    public void getVolume(VolumeListener volumeListener) {
    }

    @Override
    public VolumeControl getVolumeControl() {
        return this;
    }

    @Override
    public boolean isConnectable() {
        return true;
    }

    @Override
    public void next(ResponseListener<Object> responseListener) {
    }

    @Override
    public void previous(ResponseListener<Object> responseListener) {
    }

    @Override
    public void seek(long j, ResponseListener<Object> responseListener) {
    }

    @Override
    public void sendKeyCode(KeyControl.KeyCode keyCode, ResponseListener<Object> responseListener) {
    }

    @Override
    public void set3DEnabled(boolean z, ResponseListener<Object> responseListener) {
    }

    @Override
    public void setChannel(ChannelInfo channelInfo, ResponseListener<Object> responseListener) {
    }

    @Override
    public void setMute(boolean z, ResponseListener<Object> responseListener) {
    }

    @Override
    public void setVolume(float f, ResponseListener<Object> responseListener) {
    }

    @Override
    public ServiceSubscription<State3DModeListener> subscribe3DEnabled(State3DModeListener state3DModeListener) {
        return null;
    }

    @Override
    public ServiceSubscription<ChannelListener> subscribeCurrentChannel(ChannelListener channelListener) {
        return null;
    }

    @Override
    public ServiceSubscription<MuteListener> subscribeMute(MuteListener muteListener) {
        return null;
    }

    @Override
    public ServiceSubscription<PlayStateListener> subscribePlayState(PlayStateListener playStateListener) {
        return null;
    }

    @Override
    public ServiceSubscription<ProgramInfoListener> subscribeProgramInfo(ProgramInfoListener programInfoListener) {
        return null;
    }

    @Override
    public ServiceSubscription<ProgramListListener> subscribeProgramList(ProgramListListener programListListener) {
        return null;
    }

    @Override
    public ServiceSubscription<VolumeListener> subscribeVolume(VolumeListener volumeListener) {
        return null;
    }

    public VizioService(ServiceDescription serviceDescription, ServiceConfig serviceConfig) {
        super(serviceDescription, serviceConfig);
        this.state = State.INITIAL;
    }

    public VizioService(ServiceConfig serviceConfig) {
        super(serviceConfig);
        this.state = State.INITIAL;
    }

    @Override
    public void setServiceDescription(ServiceDescription serviceDescription) {
        super.setServiceDescription(serviceDescription);
        this.pairingType = PairingType.PIN_CODE;
        this.state = State.INITIAL;
    }

    public static DiscoveryFilter discoveryFilter() {
        return new DiscoveryFilter(ID, "_viziocast._tcp.local.");
    }

    @Override
    public boolean isConnected() {
        return this.state == State.PAIRED;
    }

    private ServiceCommand<ResponseListener<Object>> buidCommand(ResponseListener<Object> responseListener, String str, String str2, Boolean bool) {
        ServiceCommand<ResponseListener<Object>> serviceCommand = new ServiceCommand<>(this, "https://" + getServiceDescription().getIpAddress() + ":7345" + str, str2, responseListener);
        serviceCommand.setHttpMethod("PUT");
        if (str2 != null) {
            serviceCommand.setHeader("Content-Type", "application/json");
        }
        if (bool.booleanValue()) {
            serviceCommand.setHeader("AUTH", ((VizioServiceConfig) this.serviceConfig).auth);
        }
        return serviceCommand;
    }

    public void onClose() {
        Util.runOnUI(new Runnable() {
            @Override
            public void run() {
                VizioService vizioService = VizioService.this;
                DeviceServiceListener deviceServiceListener = vizioService.listener;
                if (deviceServiceListener != null) {
                    deviceServiceListener.onDisconnect(vizioService, null);
                }
            }
        });
        this.state = State.INITIAL;
    }

    public void onConnectFail(final String str) {
        Util.runOnUI(new Runnable() {
            @Override
            public void run() {
                if (VizioService.this.listener != null) {
                    Error error = new Error(str);
                    VizioService vizioService = VizioService.this;
                    vizioService.listener.onConnectionFailure(vizioService, error);
                }
            }
        });
        this.state = State.INITIAL;
    }

    public void onConnectSucceded() {
        this.state = State.PAIRED;
        reportConnected(true);
    }

    public void onPairingRequired() {
        Util.runOnUI(new Runnable() {
            @Override
            public void run() {
                VizioService vizioService = VizioService.this;
                DeviceServiceListener deviceServiceListener = vizioService.listener;
                if (deviceServiceListener != null) {
                    deviceServiceListener.onPairingRequired(vizioService, vizioService.pairingType, null);
                }
            }
        });
    }

    @Override
    public void sendPairingKey(String str) {
        if (str == null || str.length() == 0) {
            onConnectFail(VIZIO_ERROR_CONNECT_EMPTY_PAIRING_PIN);
            cancelPairing();
            return;
        }
        this.state = State.PAIRING;
        buidCommand(new ResponseListener<Object>() {
            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                VizioService.this.cancelPairing();
                VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_CONNECT_NETWROK);
            }

            @Override
            public void onSuccess(Object obj) {
                JSONObject jSONObject = (JSONObject) obj;
                VizioService vizioService = VizioService.this;
                if (vizioService.succeded(vizioService.status(jSONObject))) {
                    try {
                        VizioService.this.state = State.PAIRED;
                        ((VizioServiceConfig) VizioService.this.serviceConfig).auth = jSONObject.getJSONObject("ITEM").getString("AUTH_TOKEN");
                        VizioService.this.onConnectSucceded();
                        return;
                    } catch (Exception unused) {
                        VizioService.this.cancelPairing();
                        VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_GENERAL_EXCEPTION);
                        return;
                    }
                }
                VizioService.this.cancelPairing();
                VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_CONNECT_INVALID_PIN_CODE);
            }
        }, kPairingPair, sendPairingPayload(str), Boolean.FALSE).send();
    }

    public boolean succeded(String str) {
        return str.equalsIgnoreCase(kStatusSuccess);
    }

    @Override
    public void connect() {
        if (this.state != State.INITIAL) {
            return;
        }
        ServiceConfig serviceConfig = this.serviceConfig;
        if (!(serviceConfig instanceof VizioServiceConfig)) {
            ServiceConfig.ServiceConfigListener listener = serviceConfig.getListener();
            VizioServiceConfig vizioServiceConfig = new VizioServiceConfig(this.serviceConfig.getServiceUUID());
            this.serviceConfig = vizioServiceConfig;
            vizioServiceConfig.setListener(listener);
        }
        String auth = ((VizioServiceConfig) this.serviceConfig).getAuth();
        if (auth == null || auth.length() == 0) {
            showPairingKeyOnTV();
        } else {
            onConnectSucceded();
        }
    }

    private void showPairingKeyOnTV() {
        this.state = State.CONNECTING;
        buidCommand(new ResponseListener<Object>() {
            @Override
            public void onError(ServiceCommandError serviceCommandError) {
                VizioService.this.cancelPairing();
                VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_CONNECT_NETWROK);
            }

            @Override
            public void onSuccess(Object obj) {
                try {
                    JSONObject jSONObject = (JSONObject) obj;
                    VizioService vizioService = VizioService.this;
                    if (vizioService.succeded(vizioService.status(jSONObject))) {
                        VizioService.this.state = State.PAIRED;
                        vizioService.pairingRequestToken = jSONObject.getJSONObject("ITEM").getString("PAIRING_REQ_TOKEN");
                        VizioService.this.onPairingRequired();
                    } else if (!vizioService.isBlocked(vizioService.status(jSONObject))) {
                        VizioService.this.cancelPairing();
                        VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_CONNECT_INVALID_PIN_CODE);
                    } else {
                        if (vizioService.pairingRequestToken != null && vizioService.pairingRequestToken.length() > 0) {
                            VizioService.this.onPairingRequired();
                        }
                        VizioService.this.cancelPairing();
                        VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_CONNECT_BLOCKED);
                        VizioService.this.onPairingRequired();
                    }
                } catch (Exception unused) {
                    VizioService.this.cancelPairing();
                    VizioService.this.onConnectFail(VizioService.VIZIO_ERROR_GENERAL_EXCEPTION);
                }
            }
        }, kPairingStart, defaultPairingPayload(), Boolean.FALSE).send();
    }

    public void setupHTTPS() {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String str, SSLSession sSLSession) {
                return !TextUtils.isEmpty(str);
            }
        });
        TrustManager[] trustManagerArr = {new X509TrustManager() {
            private final X509Certificate[] _AcceptedIssuers = new X509Certificate[0];

            @Override
            public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return _AcceptedIssuers;
            }
        }};
        try {
            SSLContext sSLContext = SSLContext.getInstance("TLS");
            sSLContext.init(null, trustManagerArr, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sSLContext.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONObject result(JSONObject jSONObject) {
        try {
            return jSONObject.getJSONObject(kResultStatus);
        } catch (Exception unused) {
            return null;
        }
    }

    private String sendPairingPayload(String str) {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("DEVICE_ID", Util.uniqueID());
            jSONObject.put("CHALLENGE_TYPE", 1);
            jSONObject.put("RESPONSE_VALUE", str);
            jSONObject.put("PAIRING_REQ_TOKEN", Integer.valueOf(this.pairingRequestToken));
            return jsonToString(jSONObject);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String defaultPairingPayload() {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("DEVICE_ID", Util.uniqueID());
            jSONObject.put(Account.DEVICE_NAME, Build.MODEL);
            return jsonToString(jSONObject);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isBlocked(String str) {
        return str.equalsIgnoreCase(kStatusBlocked);
    }

    private String jsonToString(JSONObject jSONObject) {
        return Build.VERSION.SDK_INT >= 9 ? new String(jSONObject.toString().getBytes(Charset.forName(Key.STRING_CHARSET_NAME))) : "";
    }

    public boolean requiresPairing(String str) {
        return str.equalsIgnoreCase(kStatusRequiresPairing);
    }

    public String status(JSONObject jSONObject) {
        try {
            return result(jSONObject).getString(kResultDictonary);
        } catch (Exception unused) {
            return null;
        }
    }

    @Override
    public void disconnect() {
        State state = this.state;
        State state2 = State.INITIAL;
        if (state != state2) {
            this.state = state2;
            onClose();
        }
    }

    @Override
    public void cancelPairing() {
        this.state = State.INITIAL;
        buidCommand(null, kPairingCancel, defaultPairingPayload(), Boolean.FALSE).send();
    }

    @Override
    public void sendCommand(final ServiceCommand<?> serviceCommand) {
        Util.runInBackground(new Runnable() {
            @Override
            public void run() {
                ServiceCommand serviceCommand2 = serviceCommand;
                String str = (String) serviceCommand2.getPayload();
                try {
                    URL url = new URL(serviceCommand2.getTarget());
                    VizioService.this.setupHTTPS();
                    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
                    HashMap<String, String> headers = serviceCommand2.getHeaders();
                    if (!headers.isEmpty()) {
                        for (String str2 : headers.keySet()) {
                            httpsURLConnection.setRequestProperty(str2, headers.get(str2));
                        }
                    }
                    httpsURLConnection.setRequestMethod(serviceCommand2.getHttpMethod());
                    OutputStream outputStream = httpsURLConnection.getOutputStream();
                    byte[] bytes = str.getBytes(Key.STRING_CHARSET_NAME);
                    outputStream.write(bytes, 0, bytes.length);
                    int responseCode = httpsURLConnection.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream(), Key.STRING_CHARSET_NAME));
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            String readLine = bufferedReader.readLine();
                            if (readLine == null) {
                                break;
                            }
                            sb.append(readLine.trim());
                        }
                        Util.postSuccess(serviceCommand2.getResponseListener(), new JSONObject(sb.toString()));
                    } else {
                        Util.postError(serviceCommand2.getResponseListener(), ServiceCommandError.getError(responseCode));
                    }
                    httpsURLConnection.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    Util.postError(serviceCommand2.getResponseListener(), new ServiceCommandError(0, e.getMessage(), null));
                }
            }
        });
    }

    public void sendVizeoKeyCode(VizioKeyCode vizioKeyCode, ResponseListener<Object> responseListener) {
        sendKeyCode(vizioKeyCode, responseListener);
    }

    public static class AnonymousClass10 {
        static final int[] $SwitchMap$com$connectsdk$service$capability$VizioKeyCode;

        static {
            int[] iArr = new int[VizioKeyCode.values().length];
            $SwitchMap$com$connectsdk$service$capability$VizioKeyCode = iArr;
            try {
                iArr[VizioKeyCode.POWER.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.POWER_ON.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_0.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_1.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_2.ordinal()] = 5;
            } catch (NoSuchFieldError unused5) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_3.ordinal()] = 6;
            } catch (NoSuchFieldError unused6) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_4.ordinal()] = 7;
            } catch (NoSuchFieldError unused7) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_5.ordinal()] = 8;
            } catch (NoSuchFieldError unused8) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_6.ordinal()] = 9;
            } catch (NoSuchFieldError unused9) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_7.ordinal()] = 10;
            } catch (NoSuchFieldError unused10) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_8.ordinal()] = 11;
            } catch (NoSuchFieldError unused11) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.NUM_9.ordinal()] = 12;
            } catch (NoSuchFieldError unused12) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.DASH.ordinal()] = 13;
            } catch (NoSuchFieldError unused13) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.KEY_LEFT.ordinal()] = 14;
            } catch (NoSuchFieldError unused14) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.KEY_RIGHT.ordinal()] = 15;
            } catch (NoSuchFieldError unused15) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.KEY_UP.ordinal()] = 16;
            } catch (NoSuchFieldError unused16) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.KEY_DOWN.ordinal()] = 17;
            } catch (NoSuchFieldError unused17) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.HOME.ordinal()] = 18;
            } catch (NoSuchFieldError unused18) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.TOP_MENU.ordinal()] = 19;
            } catch (NoSuchFieldError unused19) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.BACK.ordinal()] = 20;
            } catch (NoSuchFieldError unused20) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.ENTER.ordinal()] = 21;
            } catch (NoSuchFieldError unused21) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.EXIT.ordinal()] = 22;
            } catch (NoSuchFieldError unused22) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.SOURCE.ordinal()] = 23;
            } catch (NoSuchFieldError unused23) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.THREED.ordinal()] = 24;
            } catch (NoSuchFieldError unused24) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.CONFIRM.ordinal()] = 25;
            } catch (NoSuchFieldError unused25) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.CLOSED_CAPTION.ordinal()] = 26;
            } catch (NoSuchFieldError unused26) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.PLAY.ordinal()] = 27;
            } catch (NoSuchFieldError unused27) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.PAUSE.ordinal()] = 28;
            } catch (NoSuchFieldError unused28) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.FAST_FORWARD.ordinal()] = 29;
            } catch (NoSuchFieldError unused29) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.REWIND.ordinal()] = 30;
            } catch (NoSuchFieldError unused30) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.VOLUME_UP.ordinal()] = 31;
            } catch (NoSuchFieldError unused31) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.VOLUME_DOWN.ordinal()] = 32;
            } catch (NoSuchFieldError unused32) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.MUTE.ordinal()] = 33;
            } catch (NoSuchFieldError unused33) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.CHANNEL_UP.ordinal()] = 34;
            } catch (NoSuchFieldError unused34) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.CHANNEL_DOWN.ordinal()] = 35;
            } catch (NoSuchFieldError unused35) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.ADJUST.ordinal()] = 36;
            } catch (NoSuchFieldError unused36) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.VIEWMODE.ordinal()] = 37;
            } catch (NoSuchFieldError unused37) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.ASPECT_RATIO.ordinal()] = 38;
            } catch (NoSuchFieldError unused38) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.INFO.ordinal()] = 39;
            } catch (NoSuchFieldError unused39) {
            }
            try {
                $SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.PREVIOUS_CHANNEL.ordinal()] = 40;
            } catch (NoSuchFieldError unused40) {
            }
        }
    }

    public void sendKeyCode(VizioKeyCode vizioKeyCode, final ResponseListener<Object> responseListener) {
        String code;
        switch (AnonymousClass10.$SwitchMap$com$connectsdk$service$capability$VizioKeyCode[VizioKeyCode.values()[vizioKeyCode.ordinal()].ordinal()]) {
            case 1:
                code = VizioVirtualKeyCodes.POWER.getCode();
                break;
            case 2:
                code = VizioVirtualKeyCodes.POWER_ON.getCode();
                break;
            case 3:
                code = VizioVirtualKeyCodes.NUMBER_0.getCode();
                break;
            case 4:
                code = VizioVirtualKeyCodes.NUMBER_1.getCode();
                break;
            case 5:
                code = VizioVirtualKeyCodes.NUMBER_2.getCode();
                break;
            case 6:
                code = VizioVirtualKeyCodes.NUMBER_3.getCode();
                break;
            case 7:
                code = VizioVirtualKeyCodes.NUMBER_4.getCode();
                break;
            case 8:
                code = VizioVirtualKeyCodes.NUMBER_5.getCode();
                break;
            case 9:
                code = VizioVirtualKeyCodes.NUMBER_6.getCode();
                break;
            case 10:
                code = VizioVirtualKeyCodes.NUMBER_7.getCode();
                break;
            case 11:
                code = VizioVirtualKeyCodes.NUMBER_8.getCode();
                break;
            case 12:
                code = VizioVirtualKeyCodes.NUMBER_9.getCode();
                break;
            case 13:
                code = VizioVirtualKeyCodes.DASH.getCode();
                break;
            case 14:
                code = VizioVirtualKeyCodes.KEY_LEFT.getCode();
                break;
            case 15:
                code = VizioVirtualKeyCodes.KEY_RIGHT.getCode();
                break;
            case 16:
                code = VizioVirtualKeyCodes.KEY_UP.getCode();
                break;
            case 17:
                code = VizioVirtualKeyCodes.KEY_DOWN.getCode();
                break;
            case 18:
                code = VizioVirtualKeyCodes.HOME.getCode();
                break;
            case 19:
                code = VizioVirtualKeyCodes.TOP_MENU.getCode();
                break;
            case 20:
                code = VizioVirtualKeyCodes.BACK.getCode();
                break;
            case 21:
                code = VizioVirtualKeyCodes.ENTER.getCode();
                break;
            case 22:
                code = VizioVirtualKeyCodes.EXIT.getCode();
                break;
            case 23:
                code = VizioVirtualKeyCodes.SOURCE.getCode();
                break;
            case 24:
                code = VizioVirtualKeyCodes.THREED.getCode();
                break;
            case 25:
            case 26:
                code = VizioVirtualKeyCodes.CLOSED_CAPTION.getCode();
                break;
            case 27:
                code = VizioVirtualKeyCodes.PLAY.getCode();
                break;
            case 28:
                code = VizioVirtualKeyCodes.PAUSE.getCode();
                break;
            case 29:
                code = VizioVirtualKeyCodes.FAST_FORWARD.getCode();
                break;
            case 30:
                code = VizioVirtualKeyCodes.REWIND.getCode();
                break;
            case 31:
                code = VizioVirtualKeyCodes.VOLUME_UP.getCode();
                break;
            case 32:
                code = VizioVirtualKeyCodes.VOLUME_DOWN.getCode();
                break;
            case 33:
                code = VizioVirtualKeyCodes.MUTE.getCode();
                break;
            case 34:
                code = VizioVirtualKeyCodes.CHANNEL_UP.getCode();
                break;
            case 35:
                code = VizioVirtualKeyCodes.CHANNEL_DOWN.getCode();
                break;
            case 36:
            case 37:
                code = VizioVirtualKeyCodes.VIEWMODE.getCode();
                break;
            case 38:
                code = VizioVirtualKeyCodes.ASPECT_RATIO.getCode();
                break;
            case 39:
                code = VizioVirtualKeyCodes.INFO.getCode();
                break;
            case 40:
                code = VizioVirtualKeyCodes.PREVIOUS_CHANNEL.getCode();
                break;
            default:
                code = null;
                break;
        }
        if (code != null) {
            buidCommand(new ResponseListener<Object>() {
                @Override
                public void onError(ServiceCommandError serviceCommandError) {
                    ResponseListener responseListener2 = responseListener;
                    if (responseListener2 != null) {
                        responseListener2.onError(serviceCommandError);
                    }
                }

                @Override
                public void onSuccess(Object obj) {
                    VizioService vizioService = VizioService.this;
                    if (vizioService.requiresPairing(vizioService.status((JSONObject) obj))) {
                        ((VizioServiceConfig) vizioService.serviceConfig).auth = null;
                        vizioService.pairingRequestToken = null;
                        vizioService.disconnect();
                    }
                }
            }, kKeyCommand, code, Boolean.TRUE).send();
        }
    }

    @Override
    public void up(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.KEY_UP, responseListener);
    }

    @Override
    public void down(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.KEY_DOWN, responseListener);
    }

    @Override
    public void fastForward(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.FAST_FORWARD, responseListener);
    }

    @Override
    public void home(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.HOME, responseListener);
    }

    @Override
    public void left(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.KEY_LEFT, responseListener);
    }

    @Override
    public void ok(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.ENTER, responseListener);
    }

    @Override
    public void pause(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.PAUSE, responseListener);
    }

    @Override
    public void play(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.PLAY, responseListener);
    }

    @Override
    public void powerOff(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.POWER, responseListener);
    }

    @Override
    public void powerOn(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.POWER, responseListener);
    }

    @Override
    public void rewind(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.REWIND, responseListener);
    }

    @Override
    public void right(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.KEY_RIGHT, responseListener);
    }

    public void info(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.INFO, responseListener);
    }

    @Override
    public void stop(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.STOP, responseListener);
    }

    @Override
    public void back(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.BACK, responseListener);
    }

    @Override
    public void volumeDown(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.VOLUME_DOWN, responseListener);
    }

    @Override
    public void channelDown(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.CHANNEL_DOWN, responseListener);
    }

    @Override
    public void channelUp(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.CHANNEL_UP, responseListener);
    }

    public void volumeMute(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.MUTE, responseListener);
    }

    @Override
    public CapabilityPriorityLevel getVolumeControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public void volumeUp(ResponseListener<Object> responseListener) {
        sendKeyCode(VizioKeyCode.VOLUME_UP, responseListener);
    }

    public CapabilityPriorityLevel getLauncherCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public CapabilityPriorityLevel getMediaControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public CapabilityPriorityLevel getPowerControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public CapabilityPriorityLevel getPriorityLevel(Class<? extends CapabilityMethods> cls) {
        if (cls.equals(PowerControl.class)) {
            return getPowerControlCapabilityLevel();
        }
        if (cls.equals(KeyControl.class)) {
            return getKeyControlCapabilityLevel();
        }
        if (cls.equals(MediaControl.class)) {
            return getMediaControlCapabilityLevel();
        }
        if (cls.equals(TVControl.class)) {
            return getMediaControlCapabilityLevel();
        }
        if (cls.equals(Launcher.class)) {
            return getLauncherCapabilityLevel();
        }
        if (cls.equals(VolumeControl.class)) {
            return getVolumeControlCapabilityLevel();
        }
        return CapabilityPriorityLevel.NOT_SUPPORTED;
    }

    public CapabilityPriorityLevel getTextInputControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

    @Override
    public CapabilityPriorityLevel getKeyControlCapabilityLevel() {
        return CapabilityPriorityLevel.HIGH;
    }

}
