package com.susnatacodes.audiorouter;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;

public final class AudioFilePlayer {
    private static final String TAG = "AudioFilePlayer";

    private final Context appContext;
    private MediaPlayer mediaPlayer;
    private String activeUri;
    private boolean playWhenReady;
    private float gain = 1f;

    public AudioFilePlayer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public synchronized void update(String uriString, boolean shouldPlay, boolean loop, float gain) {
        this.playWhenReady = shouldPlay;
        this.gain = clamp01(gain);

        if (uriString == null || uriString.trim().isEmpty()) {
            releaseLocked();
            return;
        }

        if (!uriString.equals(activeUri)) {
            releaseLocked();
            activeUri = uriString;
            createPlayerLocked(uriString);
        }

        if (mediaPlayer == null) {
            return;
        }

        mediaPlayer.setLooping(loop);
        mediaPlayer.setVolume(this.gain, this.gain);

        if (shouldPlay && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to start selected file", e);
                releaseLocked();
            }
        } else if (!shouldPlay && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
        }
    }

    public synchronized void stop() {
        releaseLocked();
    }

    private void createPlayerLocked(String uriString) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(appContext, Uri.parse(uriString));
            player.setLooping(false);
            player.setVolume(gain, gain);
            player.setOnPreparedListener(prepared -> {
                synchronized (AudioFilePlayer.this) {
                    if (playWhenReady) {
                        prepared.start();
                    }
                }
            });
            player.setOnCompletionListener(completed -> {
                synchronized (AudioFilePlayer.this) {
                    playWhenReady = false;
                }
            });
            player.setOnErrorListener((failed, what, extra) -> {
                Log.e(TAG, "Selected audio file failed: " + what + "/" + extra);
                synchronized (AudioFilePlayer.this) {
                    releaseLocked();
                }
                return true;
            });
            mediaPlayer = player;
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Unable to load selected file", e);
            releaseLocked();
        }
    }

    private void releaseLocked() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        activeUri = null;
        playWhenReady = false;
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
