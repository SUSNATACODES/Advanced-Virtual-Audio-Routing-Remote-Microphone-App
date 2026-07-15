package com.susnatacodes.audiorouter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class AudioRouterService extends Service {
    public static final String ACTION_START = "com.susnatacodes.audiorouter.START";
    public static final String ACTION_STOP = "com.susnatacodes.audiorouter.STOP";
    public static final String ACTION_SET_MODE = "com.susnatacodes.audiorouter.SET_MODE";
    public static final String ACTION_SET_LEVELS = "com.susnatacodes.audiorouter.SET_LEVELS";
    public static final String ACTION_SET_MEDIA_PROJECTION =
            "com.susnatacodes.audiorouter.SET_MEDIA_PROJECTION";
    public static final String ACTION_SET_REMOTE_MIC =
            "com.susnatacodes.audiorouter.SET_REMOTE_MIC";
    public static final String ACTION_SET_PUSH_TO_TALK =
            "com.susnatacodes.audiorouter.SET_PUSH_TO_TALK";

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_MIC_GAIN = "mic_gain";
    public static final String EXTRA_INTERNAL_GAIN = "internal_gain";
    public static final String EXTRA_REMOTE_GAIN = "remote_gain";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_HELD = "held";

    private static final int NOTIFICATION_ID = 301;
    private static final String CHANNEL_ID = "audio_router";

    private final AudioSessionState state = new AudioSessionState();
    private AudioEngine audioEngine;
    private RemoteMicServer remoteMicServer;
    private RemoteMicDiscovery remoteMicDiscovery;
    private boolean internalCaptureReady;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        audioEngine = new AudioEngine(this, state);
        remoteMicServer = new RemoteMicServer(38420);
        remoteMicDiscovery = new RemoteMicDiscovery(this, 38420);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            startForegroundCompat();
            audioEngine.start();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundCompat();

        if (ACTION_SET_MODE.equals(action)) {
            String modeName = intent.getStringExtra(EXTRA_MODE);
            if (modeName != null) {
                state.setMode(AudioMode.valueOf(modeName));
                startForegroundCompat();
            }
        } else if (ACTION_SET_LEVELS.equals(action)) {
            state.setGains(
                    intent.getFloatExtra(EXTRA_MIC_GAIN, 1f),
                    intent.getFloatExtra(EXTRA_INTERNAL_GAIN, 1f),
                    intent.getFloatExtra(EXTRA_REMOTE_GAIN, 1f));
        } else if (ACTION_SET_MEDIA_PROJECTION.equals(action)) {
            Intent resultData = readProjectionResult(intent);
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            if (resultData != null) {
                audioEngine.configureInternalCapture(resultCode, resultData);
                internalCaptureReady = true;
                startForegroundCompat();
            }
        } else if (ACTION_SET_REMOTE_MIC.equals(action)) {
            boolean enabled = intent.getBooleanExtra(EXTRA_ENABLED, false);
            state.setRemoteMicEnabled(enabled);
            if (enabled) {
                remoteMicServer.start();
                remoteMicDiscovery.startAdvertising();
            } else {
                remoteMicServer.stop();
                remoteMicDiscovery.stopAdvertising();
            }
            startForegroundCompat();
        } else if (ACTION_SET_PUSH_TO_TALK.equals(action)) {
            state.setPushToMuteHeld(intent.getBooleanExtra(EXTRA_HELD, false));
        }

        audioEngine.start();
        updateNotification();
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Intent readProjectionResult(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    @Override
    public void onDestroy() {
        audioEngine.stop();
        remoteMicServer.stop();
        remoteMicDiscovery.stopAdvertising();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_router)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(state.getMode().label())
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        int serviceType = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && state.shouldCaptureLocalMic()) {
            serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }
        if (internalCaptureReady) {
            serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        }
        if (state.isRemoteMicEnabled()) {
            serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
        }
        if (serviceType != 0) {
            startForeground(NOTIFICATION_ID, notification, serviceType);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
