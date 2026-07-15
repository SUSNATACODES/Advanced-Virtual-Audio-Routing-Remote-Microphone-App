package com.susnatacodes.audiorouter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

public final class AudioEngine {
    public static final int SAMPLE_RATE = 48000;
    private static final String TAG = "AudioEngine";
    private static final int FRAME_MS = 10;
    private static final int MONO_SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_MS / 1000;
    private static final int STEREO_SAMPLES_PER_FRAME = MONO_SAMPLES_PER_FRAME * 2;

    private final Context appContext;
    private final AudioSessionState state;
    private final Object lock = new Object();

    private MediaProjection mediaProjection;
    private AudioRecord micRecorder;
    private AudioRecord internalRecorder;
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;
    private AutomaticGainControl automaticGainControl;
    private Thread audioThread;
    private volatile boolean running;

    public AudioEngine(Context context, AudioSessionState state) {
        this.appContext = context.getApplicationContext();
        this.state = state;
    }

    public void configureInternalCapture(int resultCode, Intent resultData) {
        MediaProjectionManager manager =
                (MediaProjectionManager) appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        synchronized (lock) {
            releaseInternalRecorderLocked();
            if (mediaProjection != null) {
                mediaProjection.stop();
            }
            mediaProjection = manager.getMediaProjection(resultCode, resultData);
        }
    }

    public void start() {
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
            audioThread = new Thread(this::runAudioLoop, "router-audio-loop");
            audioThread.start();
        }
    }

    public void stop() {
        Thread threadToJoin;
        synchronized (lock) {
            running = false;
            threadToJoin = audioThread;
            audioThread = null;
        }
        if (threadToJoin != null) {
            try {
                threadToJoin.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            releaseMicRecorderLocked();
            releaseInternalRecorderLocked();
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        }
    }

    private void runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] micBuffer = new short[MONO_SAMPLES_PER_FRAME];
        short[] internalBuffer = new short[STEREO_SAMPLES_PER_FRAME];
        short[] mixedBuffer = new short[STEREO_SAMPLES_PER_FRAME];

        while (running) {
            try {
                ensureRecorders();
                AudioRecord localMic;
                AudioRecord localInternal;
                synchronized (lock) {
                    localMic = micRecorder;
                    localInternal = internalRecorder;
                }

                int micSamples = 0;
                int internalSamples = 0;
                float micGain = state.getMicGain();
                float internalGain = state.getInternalGain();

                if (localMic != null && micGain > 0f) {
                    micSamples = localMic.read(micBuffer, 0, micBuffer.length, AudioRecord.READ_BLOCKING);
                }
                if (localInternal != null && internalGain > 0f) {
                    internalSamples = localInternal.read(
                            internalBuffer, 0, internalBuffer.length, AudioRecord.READ_BLOCKING);
                }

                int frameCount = Math.max(
                        micSamples > 0 ? micSamples : 0,
                        internalSamples > 0 ? internalSamples / 2 : 0);

                if (frameCount == 0) {
                    sleepQuietly(10);
                    continue;
                }

                mixFrame(micBuffer, micSamples, internalBuffer, internalSamples, mixedBuffer,
                        frameCount, micGain, internalGain);

                // Hook mixedBuffer into recorder, streamer, local monitor, or desktop bridge here.
            } catch (SecurityException e) {
                Log.e(TAG, "Missing permission for audio capture", e);
                sleepQuietly(250);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Audio recorder state changed", e);
                synchronized (lock) {
                    releaseMicRecorderLocked();
                    releaseInternalRecorderLocked();
                }
                sleepQuietly(250);
            }
        }
    }

    private void ensureRecorders() {
        synchronized (lock) {
            boolean wantsMic = state.shouldCaptureLocalMic();
            boolean wantsInternal = state.shouldCaptureInternalAudio() && mediaProjection != null;

            if (!wantsMic) {
                releaseMicRecorderLocked();
            }
            if (!wantsInternal) {
                releaseInternalRecorderLocked();
            }
            if (!wantsMic && !wantsInternal) {
                return;
            }
            if (!hasRecordAudioPermission()) {
                throw new SecurityException("RECORD_AUDIO permission is required");
            }
            if (wantsMic && micRecorder == null) {
                micRecorder = buildMicRecorder();
                micRecorder.startRecording();
                enableVoiceEffects(micRecorder.getAudioSessionId());
            }
            if (wantsInternal && internalRecorder == null) {
                internalRecorder = buildInternalRecorder(mediaProjection);
                internalRecorder.startRecording();
            }
        }
    }

    private boolean hasRecordAudioPermission() {
        return appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private AudioRecord buildMicRecorder() {
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBuffer, MONO_SAMPLES_PER_FRAME * 2 * 4);
        return new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .build();
    }

    @SuppressLint("MissingPermission")
    private AudioRecord buildInternalRecorder(MediaProjection projection) {
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        AudioPlaybackCaptureConfiguration captureConfig =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBuffer, STEREO_SAMPLES_PER_FRAME * 2 * 4);
        return new AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setBufferSizeInBytes(bufferBytes)
                .build();
    }

    private void mixFrame(
            short[] micBuffer,
            int micSamples,
            short[] internalBuffer,
            int internalSamples,
            short[] mixedBuffer,
            int frameCount,
            float micGain,
            float internalGain) {
        int maxFrames = Math.min(frameCount, MONO_SAMPLES_PER_FRAME);
        for (int frame = 0; frame < maxFrames; frame++) {
            int mic = frame < micSamples ? Math.round(micBuffer[frame] * micGain) : 0;
            int stereoIndex = frame * 2;
            int left = stereoIndex < internalSamples
                    ? Math.round(internalBuffer[stereoIndex] * internalGain)
                    : 0;
            int right = stereoIndex + 1 < internalSamples
                    ? Math.round(internalBuffer[stereoIndex + 1] * internalGain)
                    : 0;
            mixedBuffer[stereoIndex] = clamp16(left + mic);
            mixedBuffer[stereoIndex + 1] = clamp16(right + mic);
        }
    }

    private void enableVoiceEffects(int audioSessionId) {
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId);
            if (noiseSuppressor != null) {
                noiseSuppressor.setEnabled(true);
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioSessionId);
            if (echoCanceler != null) {
                echoCanceler.setEnabled(true);
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(audioSessionId);
            if (automaticGainControl != null) {
                automaticGainControl.setEnabled(true);
            }
        }
    }

    private void releaseMicRecorderLocked() {
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
        if (echoCanceler != null) {
            echoCanceler.release();
            echoCanceler = null;
        }
        if (automaticGainControl != null) {
            automaticGainControl.release();
            automaticGainControl = null;
        }
        if (micRecorder != null) {
            try {
                micRecorder.stop();
            } catch (IllegalStateException ignored) {
                // Recorder may already be stopped by the platform.
            }
            micRecorder.release();
            micRecorder = null;
        }
    }

    private void releaseInternalRecorderLocked() {
        if (internalRecorder != null) {
            try {
                internalRecorder.stop();
            } catch (IllegalStateException ignored) {
                // Recorder may already be stopped by the platform.
            }
            internalRecorder.release();
            internalRecorder = null;
        }
    }

    private static short clamp16(int value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) value;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
