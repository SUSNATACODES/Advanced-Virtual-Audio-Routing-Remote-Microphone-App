package com.codex.audiorouter;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public final class RemoteMicDiscovery {
    private static final String TAG = "RemoteMicDiscovery";
    private static final String SERVICE_TYPE = "_audiomix._udp.";
    private static final String SERVICE_NAME = "AdvancedAudioRouter";

    private final Context appContext;
    private final int port;
    private NsdManager.RegistrationListener registrationListener;

    public RemoteMicDiscovery(Context context, int port) {
        this.appContext = context.getApplicationContext();
        this.port = port;
    }

    public void startAdvertising() {
        if (registrationListener != null) {
            return;
        }
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                Log.i(TAG, "Remote mic service registered: " + info.getServiceName());
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.e(TAG, "Remote mic registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {
                Log.i(TAG, "Remote mic service unregistered");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.e(TAG, "Remote mic unregistration failed: " + errorCode);
            }
        };

        NsdManager manager = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    public void stopAdvertising() {
        if (registrationListener == null) {
            return;
        }
        NsdManager manager = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        try {
            manager.unregisterService(registrationListener);
        } catch (IllegalArgumentException ignored) {
            // Android throws if the listener was already removed by the system.
        }
        registrationListener = null;
    }
}
