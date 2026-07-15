package com.codex.audiorouter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class AudioRouterService extends Service {
    public static final String ACTION_START = "com.codex.audiorouter.START";
    public static final String ACTION_STOP = "com.codex.audiorouter.STOP";
    public static final String ACTION_SET_MODE = "com.codex.audiorouter.SET_MODE";
    public static final String ACTION_SET_LEVELS = "com.codex.audiorouter.SET_LEVELS";
    public static final String ACTION_SET_MEDIA_PROJECTION = "com.codex.audiorouter.SET_MEDIA_PROJECTION";
    public static final String ACTION_SET_REMOTE_MIC = "com.codex.audiorouter.SET_REMOTE_MIC";
    public static final String ACTION_SET_PUSH_TO_TALK = "com.codex.audiorouter.SET_PUSH_TO_TALK";

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
            startForeground(NOTIFICATION_ID, buildNotification());
            audioEngine.start();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());

        if (ACTION_SET_MODE.equals(action)) {
            String modeName = intent.getStringExtra(EXTRA_MODE);
            if (modeName != null) {
                state.setMode(AudioMode.valueOf(modeName));
            }
        } else if (ACTION_SET_LEVELS.equals(action)) {
            state.setGains(
                    intent.getFloatExtra(EXTRA_MIC_GAIN, 1f),
                    intent.getFloatExtra(EXTRA_INTERNAL_GAIN, 1f),
                    intent.getFloatExtra(EXTRA_REMOTE_GAIN, 1f));
        } else if (ACTION_SET_MEDIA_PROJECTION.equals(action)) {
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            if (resultData != null) {
                audioEngine.configureInternalCapture(resultCode, resultData);
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
        } else if (ACTION_SET_PUSH_TO_TALK.equals(action)) {
            state.setPushToMuteHeld(intent.getBooleanExtra(EXTRA_HELD, false));
        }

        audioEngine.start();
        updateNotification();
        return START_STICKY;
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
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags);
        String content = state.getMode().label();
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_stat_router)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
