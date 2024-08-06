/*
 * DiscoveryManager
 * Connect SDK
 *
 * Copyright (c) 2014 LG Electronics.
 * Created by Hyun Kook Khang on 19 Jan 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.connectsdk.discovery;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

import com.connectsdk.DefaultPlatform;
import com.connectsdk.core.Util;
import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.ConnectableDeviceStore;
import com.connectsdk.device.DefaultConnectableDeviceStore;
import com.connectsdk.service.DLNAService;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.DeviceService.PairingType;
import com.connectsdk.service.NetcastTVService;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.config.ServiceConfig;
import com.connectsdk.service.config.ServiceConfig.ServiceConfigListener;
import com.connectsdk.service.config.ServiceDescription;

/**
 * ###Overview
 * <p>
 * At the heart of Connect SDK is DiscoveryManager, a multi-protocol service discovery engine with a pluggable architecture. Much of your initial experience with Connect SDK will be with the DiscoveryManager class, as it consolidates discovered service information into ConnectableDevice objects.
 * <p>
 * ###In depth
 * DiscoveryManager supports discovering services of differing protocols by using DiscoveryProviders. Many services are discoverable over [SSDP][0] and are registered to be discovered with the SSDPDiscoveryProvider class.
 * <p>
 * As services are discovered on the network, the DiscoveryProviders will notify DiscoveryManager. DiscoveryManager is capable of attributing multiple services, if applicable, to a single ConnectableDevice instance. Thus, it is possible to have a mixed-mode ConnectableDevice object that is theoretically capable of more functionality than a single service can provide.
 * <p>
 * DiscoveryManager keeps a running list of all discovered devices and maintains a filtered list of devices that have satisfied any of your CapabilityFilters. This filtered list is used by the DevicePicker when presenting the user with a list of devices.
 * <p>
 * Only one instance of the DiscoveryManager should be in memory at a time. To assist with this, DiscoveryManager has static method at sharedManager.
 * <p>
 * Example:
 *
 * @capability kMediaControlPlay
 * @code DiscoveryManager.init(getApplicationContext ());
 * DiscoveryManager discoveryManager = DiscoveryManager.getInstance();
 * discoveryManager.addListener(this);
 * discoveryManager.start();
 * @endcode [0]: http://tools.ietf.org/html/draft-cai-ssdp-v1-03
 */
public class DiscoveryManager implements ConnectableDeviceListener, DiscoveryProviderListener, ServiceConfigListener {

    /**
     * Describes a pairing level for a DeviceService. It's used by a DiscoveryManager and all
     * services.
     */
    public enum PairingLevel {
        /**
         * Specifies that pairing is off. DeviceService will never try to pair with a first
         * screen device.
         */
        OFF,

        /**
         * Specifies that pairing is protected. DeviceService will try to pair in protected mode
         * if it is required by a first screen device (webOS - Protected Permission).
         */
        PROTECTED,

        /**
         * Specifies that pairing is on. DeviceService will try to pair if it is required by a first
         * screen device.
         */
        ON
    }

    // @cond INTERNAL

    public static String CONNECT_SDK_VERSION = "1.6.0";

    private static DiscoveryManager instance;

    Context context;
    ConnectableDeviceStore connectableDeviceStore;

    int rescanInterval = 10;

    private ConcurrentHashMap<String, ConnectableDevice> allDevices;
    private ConcurrentHashMap<String, ConnectableDevice> compatibleDevices;

    ConcurrentHashMap<String, Class<? extends DeviceService>> deviceClasses;
    CopyOnWriteArrayList<DiscoveryProvider> discoveryProviders;

    private CopyOnWriteArrayList<DiscoveryManagerListener> discoveryListeners;
    List<CapabilityFilter> capabilityFilters;

    MulticastLock multicastLock;
    BroadcastReceiver receiver;
    boolean isBroadcastReceiverRegistered = false;

    Timer rescanTimer;

    PairingLevel pairingLevel;

    private boolean mSearching = false;

    // @endcond

    /**
     * If serviceIntegrationEnabled is false (default), all services look like in different devices.
     * If serviceIntegrationEnabled is true, services in a device are managed by one device instance.
     */
    private boolean serviceIntegrationEnabled = false;

    public void setServiceIntegration(boolean value) {
        serviceIntegrationEnabled = value;
    }

    public boolean isServiceIntegrationEnabled() {
        return serviceIntegrationEnabled;
    }

    /**
     * Use device name and IP for identification of device,
     * because some devices have multiple device instances with same IP.
     * (i.e., a device including docker containers with host network setting.)
     * And if service integration is false (default), all services look like different devices.
     */
    private String getDeviceKey(ConnectableDevice device) {
        if (isServiceIntegrationEnabled()) return device.getFriendlyName() + device.getIpAddress();
        return device.getFriendlyName() + device.getIpAddress() + device.getServiceId();
    }

    private String getDeviceKey(ServiceDescription srvDesc) {
        if (isServiceIntegrationEnabled())
            return srvDesc.getFriendlyName() + srvDesc.getIpAddress();
        return srvDesc.getFriendlyName() + srvDesc.getIpAddress() + srvDesc.getServiceID();
    }

    /**
     * Initilizes the Discovery manager with a valid context.  This should be done as soon as possible and it should use getApplicationContext() as the Discovery manager could persist longer than the current Activity.
     *
     * @code DiscoveryManager.init(getApplicationContext ());
     * @endcode
     */
    public static synchronized void init(Context context) {
        instance = new DiscoveryManager(context);
    }

    public static synchronized void destroy() {
        if (instance != null) instance.onDestroy();
    }

    /**
     * Initilizes the Discovery manager with a valid context.  This should be done as soon as possible and it should use getApplicationContext() as the Discovery manager could persist longer than the current Activity.
     * <p>
     * This accepts a ConnectableDeviceStore to use instead of the default device store.
     *
     * @code MyConnectableDeviceStore myDeviceStore = new MyConnectableDeviceStore();
     * DiscoveryManager.init(getApplicationContext(), myDeviceStore);
     * @endcode
     */
    public static synchronized void init(Context context, ConnectableDeviceStore connectableDeviceStore) {
        instance = new DiscoveryManager(context, connectableDeviceStore);
    }

    /**
     * Get a shared instance of DiscoveryManager.
     */
    public static synchronized DiscoveryManager getInstance() {
        if (instance == null)
            throw new Error("Call DiscoveryManager.init(Context) first");

        return instance;
    }

    // @cond INTERNAL

    /**
     * Create a new instance of DiscoveryManager.
     * Direct use of this constructor is not recommended. In most cases,
     * you should use DiscoveryManager.getInstance() instead.
     */
    public DiscoveryManager(Context context) {
        this(context, new DefaultConnectableDeviceStore(context));
    }

    /**
     * Create a new instance of DiscoveryManager.
     * Direct use of this constructor is not recommended. In most cases,
     * you should use DiscoveryManager.getInstance() instead.
     */
    public DiscoveryManager(Context context, ConnectableDeviceStore connectableDeviceStore) {
        this.context = context;
        this.connectableDeviceStore = connectableDeviceStore;

        allDevices = new ConcurrentHashMap<String, ConnectableDevice>(8, 0.75f, 2);
        compatibleDevices = new ConcurrentHashMap<String, ConnectableDevice>(8, 0.75f, 2);

        deviceClasses = new ConcurrentHashMap<String, Class<? extends DeviceService>>(4, 0.75f, 2);
        discoveryProviders = new CopyOnWriteArrayList<DiscoveryProvider>();

        discoveryListeners = new CopyOnWriteArrayList<DiscoveryManagerListener>();

        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifiMgr.createMulticastLock(Util.T);
        multicastLock.setReferenceCounted(true);

        capabilityFilters = new ArrayList<CapabilityFilter>();
        pairingLevel = PairingLevel.OFF;

        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                    switch (networkInfo.getState()) {
                        case CONNECTED:
                            if (mSearching) {
                                for (DiscoveryProvider provider : discoveryProviders) {
                                    provider.restart();
                                }
                            }

                            break;

                        case DISCONNECTED:
                            Log.w(Util.T, "Network connection is disconnected");

                            for (DiscoveryProvider provider : discoveryProviders) {
                                provider.reset();
                            }

                            allDevices.clear();

                            for (ConnectableDevice device : compatibleDevices.values()) {
                                handleDeviceLoss(device);
                            }
                            compatibleDevices.clear();

                            break;

                        case CONNECTING:
                            break;
                        case DISCONNECTING:
                            break;
                        case SUSPENDED:
                            break;
                        case UNKNOWN:
                            break;
                    }
                }
            }
        };

        registerBroadcastReceiver();
    }
    // @endcond

    private void registerBroadcastReceiver() {
        if (!isBroadcastReceiverRegistered) {
            isBroadcastReceiverRegistered = true;

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            context.registerReceiver(receiver, intentFilter);
        }
    }

    private void unregisterBroadcastReceiver() {
        if (isBroadcastReceiverRegistered) {
            isBroadcastReceiverRegistered = false;

            context.unregisterReceiver(receiver);
        }
    }

    /**
     * Listener which should receive discovery updates. It is not necessary to set this listener property unless you are implementing your own device picker. Connect SDK provides a default DevicePicker which acts as a DiscoveryManagerListener, and should work for most cases.
     * <p>
     * If you have provided a capabilityFilters array, the listener will only receive update messages for ConnectableDevices which satisfy at least one of the CapabilityFilters. If no capabilityFilters array is provided, the listener will receive update messages for all ConnectableDevice objects that are discovered.
     */
    public void addListener(DiscoveryManagerListener listener) {
        // notify listener of all devices so far
        for (ConnectableDevice device : compatibleDevices.values()) {
            listener.onDeviceAdded(this, device);
        }
        discoveryListeners.add(listener);
    }

    /**
     * Removes a previously added listener
     */
    public void removeListener(DiscoveryManagerListener listener) {
        discoveryListeners.remove(listener);
    }

    public void setCapabilityFilters(CapabilityFilter... capabilityFilters) {
        setCapabilityFilters(Arrays.asList(capabilityFilters));
    }

    public void setCapabilityFilters(List<CapabilityFilter> capabilityFilters) {
        this.capabilityFilters = capabilityFilters;

        for (ConnectableDevice device : compatibleDevices.values()) {
            handleDeviceLoss(device);
        }

        compatibleDevices.clear();

        for (ConnectableDevice device : allDevices.values()) {
            if (deviceIsCompatible(device)) {
                compatibleDevices.put(device.getIpAddress(), device);
                handleDeviceAdd(device);
            }
        }
    }

    /**
     * Returns the list of capability filters.
     */
    public List<CapabilityFilter> getCapabilityFilters() {
        return capabilityFilters;
    }

    public boolean deviceIsCompatible(ConnectableDevice device) {
        if (capabilityFilters == null || capabilityFilters.size() == 0) {
            return true;
        }

        boolean isCompatible = false;

        for (CapabilityFilter filter : this.capabilityFilters) {
            if (device.hasCapabilities(filter.capabilities)) {
                isCompatible = true;
                break;
            }
        }

        return isCompatible;
    }
    // @cond INTERNAL

    /**
     * Registers a commonly-used set of DeviceServices with DiscoveryManager. This method will be called on first call of startDiscovery if no DeviceServices have been registered.
     * <p>
     * - CastDiscoveryProvider
     * + CastService
     * - SSDPDiscoveryProvider
     * + DIALService
     * + DLNAService (limited to LG TVs, currently)
     * + NetcastTVService
     * + RokuService
     * + WebOSTVService
     * + MultiScreenService
     * - ZeroconfDiscoveryProvider
     * + AirPlayService
     */
    @SuppressWarnings("unchecked")
    public void registerDefaultDeviceTypes() {
        final HashMap<String, String> devicesList = DefaultPlatform.getDeviceServiceMap();

        for (HashMap.Entry<String, String> entry : devicesList.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            try {
                registerDeviceService((Class<DeviceService>) Class.forName(key), (Class<DiscoveryProvider>) Class.forName(value));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Registers a DeviceService with DiscoveryManager and tells it which DiscoveryProvider to use to find it. Each DeviceService has a JSONObject of discovery parameters that its DiscoveryProvider will use to find it.
     *
     * @param cls    Class for object that should be instantiated when DeviceService is found
     * @param cls2 Class for object that should discover this DeviceService. If a DiscoveryProvider of this class already exists, then the existing DiscoveryProvider will be used.
     */
    public void registerDeviceService(Class<? extends DeviceService> cls, Class<? extends DiscoveryProvider> cls2) {
        DiscoveryProvider discoveryProvider;
        if (DeviceService.class.isAssignableFrom(cls) && DiscoveryProvider.class.isAssignableFrom(cls2)) {
            try {
                Iterator<DiscoveryProvider> it = this.discoveryProviders.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        discoveryProvider = null;
                        break;
                    }
                    discoveryProvider = it.next();
                    if (discoveryProvider.getClass().isAssignableFrom(cls2)) {
                        break;
                    }
                }
                if (discoveryProvider == null) {
                    discoveryProvider = cls2.getConstructor(Context.class).newInstance(this.context);
                    discoveryProvider.addListener(this);
                    this.discoveryProviders.add(discoveryProvider);
                }
                DiscoveryFilter discoveryFilter = (DiscoveryFilter) cls.getMethod("discoveryFilter", new Class[0]).invoke(null, new Object[0]);
                this.deviceClasses.put(discoveryFilter.getServiceId(), cls);
                discoveryProvider.addDeviceFilter(discoveryFilter);
                if (!this.mSearching) {
                    return;
                }
                discoveryProvider.restart();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e2) {
                e2.printStackTrace();
            } catch (InstantiationException e3) {
                e3.printStackTrace();
            } catch (NoSuchMethodException e4) {
                e4.printStackTrace();
            } catch (SecurityException e5) {
                e5.printStackTrace();
            } catch (RuntimeException e6) {
                e6.printStackTrace();
            } catch (InvocationTargetException e7) {
                e7.printStackTrace();
            }
        }
    }

    /**
     * Unregisters a DeviceService with DiscoveryManager. If no other DeviceServices are set to being discovered with the associated DiscoveryProvider, then that DiscoveryProvider instance will be stopped and shut down.
     *
     * @param cls    Class for DeviceService that should no longer be discovered
     * @param cls2 Class for DiscoveryProvider that is discovering DeviceServices of deviceClass type
     */
    public void unregisterDeviceService(Class<?> cls, Class<?> cls2) {
        DiscoveryProvider discoveryProvider;
        if (DeviceService.class.isAssignableFrom(cls) && DiscoveryProvider.class.isAssignableFrom(cls2)) {
            try {
                Iterator<DiscoveryProvider> it = this.discoveryProviders.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        discoveryProvider = null;
                        break;
                    }
                    discoveryProvider = it.next();
                    if (discoveryProvider.getClass().isAssignableFrom(cls2)) {
                        break;
                    }
                }
                if (discoveryProvider == null) {
                    return;
                }
                DiscoveryFilter discoveryFilter = (DiscoveryFilter) cls.getMethod("discoveryFilter", new Class[0]).invoke(null, new Object[0]);
                if (this.deviceClasses.remove(discoveryFilter.getServiceId()) == null) {
                    return;
                }
                discoveryProvider.removeDeviceFilter(discoveryFilter);
                if (!discoveryProvider.isEmpty()) {
                    return;
                }
                discoveryProvider.stop();
                this.discoveryProviders.remove(discoveryProvider);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e2) {
                e2.printStackTrace();
            } catch (NoSuchMethodException e3) {
                e3.printStackTrace();
            } catch (SecurityException e4) {
                e4.printStackTrace();
            } catch (InvocationTargetException e5) {
                e5.printStackTrace();
            }
        }
    }
    // @endcond

    /**
     * Start scanning for devices on the local network.
     */
    public void start() {
        if (mSearching)
            return;

        if (discoveryProviders == null) {
            return;
        }

        mSearching = true;
        multicastLock.acquire();

        Util.runOnUI(new Runnable() {

            @Override
            public void run() {
                if (discoveryProviders.size() == 0) {
                    registerDefaultDeviceTypes();
                }

                ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                if (mWifi.isConnected()) {
                    for (DiscoveryProvider provider : discoveryProviders) {
                        provider.start();
                    }
                } else {
                    Log.w(Util.T, "Wifi is not connected yet");

                    Util.runOnUI(new Runnable() {

                        @Override
                        public void run() {
                            for (DiscoveryManagerListener listener : discoveryListeners)
                                listener.onDiscoveryFailed(DiscoveryManager.this, new ServiceCommandError(0, "No wifi connection", null));
                        }
                    });
                }
            }
        });
    }

    /**
     * Stop scanning for devices.
     */
    public void stop() {
        if (!mSearching)
            return;

        mSearching = false;

        for (DiscoveryProvider provider : discoveryProviders) {
            provider.stop();
        }

        if (multicastLock.isHeld()) {
            multicastLock.release();
        }
    }

    /**
     * ConnectableDeviceStore object which loads & stores references to all discovered devices. Pairing codes/keys, SSL certificates, recent access times, etc are kept in the device store.
     * <p>
     * ConnectableDeviceStore is a protocol which may be implemented as needed. A default implementation, DefaultConnectableDeviceStore, exists for convenience and will be used if no other device store is provided.
     * <p>
     * In order to satisfy user privacy concerns, you should provide a UI element in your app which exposes the ConnectableDeviceStore removeAll method.
     * <p>
     * To disable the ConnectableDeviceStore capabilities of Connect SDK, set this value to nil. This may be done at the time of instantiation with `DiscoveryManager.init(context, null);`.
     */
    public void setConnectableDeviceStore(ConnectableDeviceStore connectableDeviceStore) {
        this.connectableDeviceStore = connectableDeviceStore;
    }

    /**
     * ConnectableDeviceStore object which loads & stores references to all discovered devices. Pairing codes/keys, SSL certificates, recent access times, etc are kept in the device store.
     * <p>
     * ConnectableDeviceStore is a protocol which may be implemented as needed. A default implementation, DefaultConnectableDeviceStore, exists for convenience and will be used if no other device store is provided.
     * <p>
     * In order to satisfy user privacy concerns, you should provide a UI element in your app which exposes the ConnectableDeviceStore removeAll method.
     * <p>
     * To disable the ConnectableDeviceStore capabilities of Connect SDK, set this value to nil. This may be done at the time of instantiation with `DiscoveryManager.init(context, null);`.
     */
    public ConnectableDeviceStore getConnectableDeviceStore() {
        return connectableDeviceStore;
    }

    // @cond INTERNAL
    public void handleDeviceAdd(ConnectableDevice connectableDevice) {
        if (!deviceIsCompatible(connectableDevice)) {
            return;
        }
        this.compatibleDevices.put(connectableDevice.getIpAddress(), connectableDevice);
        Iterator<DiscoveryManagerListener> it = this.discoveryListeners.iterator();
        while (it.hasNext()) {
            it.next().onDeviceAdded(this, connectableDevice);
        }

    }

    public void handleDeviceUpdate(ConnectableDevice connectableDevice) {
        if (deviceIsCompatible(connectableDevice)) {
            if (connectableDevice.getIpAddress() != null && this.compatibleDevices.containsKey(connectableDevice.getIpAddress())) {
                Iterator<DiscoveryManagerListener> it = this.discoveryListeners.iterator();
                while (it.hasNext()) {
                    it.next().onDeviceUpdated(this, connectableDevice);
                }
                return;
            }
            handleDeviceAdd(connectableDevice);
            return;
        }
        this.compatibleDevices.remove(connectableDevice.getIpAddress());
        handleDeviceLoss(connectableDevice);
    }

    public void handleDeviceLoss(ConnectableDevice device) {
        for (DiscoveryManagerListener listenter : discoveryListeners) {
            listenter.onDeviceRemoved(this, device);
        }

        device.disconnect();
    }

    public boolean isNetcast(ServiceDescription description) {
        boolean isNetcastTV = false;

        String modelName = description.getModelName();
        String modelDescription = description.getModelDescription();

        if (modelName != null && modelName.toUpperCase(Locale.US).equals("LG TV")) {
            if (modelDescription != null && !(modelDescription.toUpperCase(Locale.US).contains("WEBOS"))) {
                if (description.getServiceID().equals(NetcastTVService.ID)) {
                    isNetcastTV = true;
                }
            }
        }

        return isNetcastTV;
    }

    // @endcond

    /**
     * List of all devices discovered by DiscoveryManager. Each ConnectableDevice object is keyed against its current IP address.
     */
    public Map<String, ConnectableDevice> getAllDevices() {
        return allDevices;
    }

    /**
     * Returns the device which is matched with deviceId.
     * Returns null if deviceId is null.
     */
    public ConnectableDevice getDeviceById(String deviceId) {
        if (deviceId != null) {
            for (ConnectableDevice dvc : allDevices.values()) {
                if (deviceId.equals(dvc.getId()) == true)
                    return dvc;
            }
        }

        return null;
    }

    /**
     * Returns the device which is matched with deviceId.
     * Returns null if deviceId is null.
     */
    public ConnectableDevice getDeviceByIpAddress(String ipAddress) {
        if (ipAddress != null) {
            for (ConnectableDevice dvc : allDevices.values()) {
                if (ipAddress.equals(dvc.getIpAddress()) == true)
                    return dvc;
            }
        }

        return null;
    }

    /**
     * Filtered list of discovered ConnectableDevices, limited to devices that match at least one of the CapabilityFilters in the capabilityFilters array. Each ConnectableDevice object is keyed against its current IP address.
     */
    public Map<String, ConnectableDevice> getCompatibleDevices() {
        return compatibleDevices;
    }

    /**
     * The pairingLevel property determines whether capabilities that require pairing (such as entering a PIN) will be available.
     * <p>
     * If pairingLevel is set to ConnectableDevicePairingLevelOn, ConnectableDevices that require pairing will prompt the user to pair when connecting to the ConnectableDevice.
     * <p>
     * If pairingLevel is set to ConnectableDevicePairingLevelOff (the default), connecting to the device will avoid requiring pairing if possible but some capabilities may not be available.
     */
    public PairingLevel getPairingLevel() {
        return pairingLevel;
    }

    /**
     * The pairingLevel property determines whether capabilities that require pairing (such as entering a PIN) will be available.
     * <p>
     * If pairingLevel is set to ConnectableDevicePairingLevelOn, ConnectableDevices that require pairing will prompt the user to pair when connecting to the ConnectableDevice.
     * <p>
     * If pairingLevel is set to ConnectableDevicePairingLevelOff (the default), connecting to the device will avoid requiring pairing if possible but some capabilities may not be available.
     */
    public void setPairingLevel(PairingLevel pairingLevel) {
        this.pairingLevel = pairingLevel;
    }

    // @cond INTERNAL
    public Context getContext() {
        return context;
    }

    public void onDestroy() {
        unregisterBroadcastReceiver();
    }

    public List<DiscoveryProvider> getDiscoveryProviders() {
        return new ArrayList<DiscoveryProvider>(discoveryProviders);
    }

    @Override
    public void onServiceConfigUpdate(ServiceConfig serviceConfig) {
        if (connectableDeviceStore == null) {
            return;
        }
        for (ConnectableDevice device : getAllDevices().values()) {
            if (null != device.getServiceWithUUID(serviceConfig.getServiceUUID())) {
                connectableDeviceStore.updateDevice(device);
            }
        }
    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {
        handleDeviceUpdate(device);
    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device) {
    }

    @Override
    public void onDeviceReady(ConnectableDevice device) {
    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, PairingType pairingType) {
    }

    @Override
    public void onServiceAdded(DiscoveryProvider provider, ServiceDescription serviceDescription) {
        String str = Util.T;
        Log.d(str, "Service added: " + serviceDescription.getFriendlyName() + " (" + serviceDescription.getServiceID() + ")");
        boolean z = true;
        boolean containsKey = allDevices.containsKey(serviceDescription.getIpAddress());
        ConnectableDevice connectableDevice = null;
        if (containsKey) {
            ConnectableDeviceStore connectableDeviceStore = this.connectableDeviceStore;
            if (connectableDeviceStore != null && (connectableDevice = connectableDeviceStore.getDevice(serviceDescription.getUUID())) != null) {
                this.allDevices.put(serviceDescription.getIpAddress(), connectableDevice);
                connectableDevice.setIpAddress(serviceDescription.getIpAddress());
            }
        } else {
            connectableDevice = this.allDevices.get(serviceDescription.getIpAddress());
        }
        if (connectableDevice == null) {
            connectableDevice = new ConnectableDevice(serviceDescription);
            connectableDevice.setIpAddress(serviceDescription.getIpAddress());
            this.allDevices.put(serviceDescription.getIpAddress(), connectableDevice);
        } else {
            z = containsKey;
        }
        try {
            connectableDevice.setManufacturer(serviceDescription.getManufacturer());
        } catch (Exception unused) {
        }
        connectableDevice.setFriendlyName(serviceDescription.getFriendlyName());
        connectableDevice.setLastDetection(Util.getTime());
        connectableDevice.setLastKnownIPAddress(serviceDescription.getIpAddress());
        addServiceDescriptionToDevice(serviceDescription, connectableDevice);
        if (connectableDevice.getServices().size() == 0) {
            this.allDevices.remove(serviceDescription.getIpAddress());
        } else if (z) {
            handleDeviceAdd(connectableDevice);
        } else {
            handleDeviceUpdate(connectableDevice);
        }
    }

    @Override
    public void onServiceRemoved(DiscoveryProvider provider, ServiceDescription serviceDescription) {
        if (serviceDescription == null) {
            Log.w(Util.T, "onServiceRemoved: unknown service description");
            return;
        }
        String str = Util.T;
        Log.d(str, "onServiceRemoved: friendlyName: " + serviceDescription.getFriendlyName());
        ConnectableDevice connectableDevice = this.allDevices.get(serviceDescription.getIpAddress());
        if (connectableDevice == null) {
            return;
        }
        connectableDevice.removeServiceWithId(serviceDescription.getServiceID());
        if (connectableDevice.getServices().isEmpty()) {
            this.allDevices.remove(serviceDescription.getIpAddress());
            handleDeviceLoss(connectableDevice);
            return;
        }
        handleDeviceUpdate(connectableDevice);
    }

    @Override
    public void onServiceDiscoveryFailed(DiscoveryProvider provider, ServiceCommandError error) {
        Log.w(Util.T, "DiscoveryProviderListener, Service Discovery Failed");
    }

    @SuppressWarnings("unchecked")
    public void addServiceDescriptionToDevice(ServiceDescription serviceDescription, ConnectableDevice connectableDevice) {
        boolean z;
        boolean z2;
        String str = Util.T;
        Log.d(str, "Adding service " + serviceDescription.getServiceID() + " to device with address " + connectableDevice.getIpAddress() + " and id " + connectableDevice.getId());
        Class<? extends DeviceService> cls = this.deviceClasses.get(serviceDescription.getServiceID());
        if (cls == null) {
            return;
        }
        if (cls == DLNAService.class) {
            if (serviceDescription.getLocationXML() == null) {
                return;
            }
        } else if (cls == NetcastTVService.class && !isNetcast(serviceDescription)) {
            return;
        }
        ServiceConfig serviceConfig = null;
        ConnectableDeviceStore connectableDeviceStore = this.connectableDeviceStore;
        if (connectableDeviceStore != null) {
            serviceConfig = connectableDeviceStore.getServiceConfig(serviceDescription);
        }
        if (serviceConfig == null) {
            serviceConfig = new ServiceConfig(serviceDescription);
        }
        serviceConfig.setListener(this);
        Iterator<DeviceService> it = connectableDevice.getServices().iterator();
        while (true) {
            z = true;
            z2 = false;
            if (!it.hasNext()) {
                z = false;
                break;
            }
            DeviceService next = it.next();
            if (next.getServiceDescription().getServiceID().equals(serviceDescription.getServiceID())) {
                if (next.getServiceDescription().getUUID().equals(serviceDescription.getUUID())) {
                    z2 = true;
                }
            }
        }
        if (z) {
            if (z2) {
                connectableDevice.setServiceDescription(serviceDescription);
                DeviceService serviceByName = connectableDevice.getServiceByName(serviceDescription.getServiceID());
                if (serviceByName == null) {
                    return;
                }
                serviceByName.setServiceDescription(serviceDescription);
                return;
            }
            connectableDevice.removeServiceByName(serviceDescription.getServiceID());
        }
        DeviceService service = DeviceService.getService(cls, serviceDescription, serviceConfig);
        if (service == null) {
            return;
        }
        service.setServiceDescription(serviceDescription);
        connectableDevice.addService(service);
    }
    // @endcond
}
