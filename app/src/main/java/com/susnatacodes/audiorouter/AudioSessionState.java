package com.susnatacodes.audiorouter;

public final class AudioSessionState {
    public static final int VOICE_FX_NORMAL = 0;
    public static final int VOICE_FX_DEEP = 1;
    public static final int VOICE_FX_BRIGHT = 2;
    public static final int VOICE_FX_ROBOT = 3;

    private AudioMode mode = AudioMode.MIC_ONLY;
    private float micGain = 1f;
    private float internalGain = 0f;
    private float remoteGain = 1f;
    private float masterGain = 1f;
    private float eqLowDb = 0f;
    private float eqMidDb = 0f;
    private float eqHighDb = 0f;
    private int latencyTargetMs = 40;
    private int voiceFxMode = VOICE_FX_NORMAL;
    private boolean pushToMuteHeld;
    private boolean remoteMicEnabled;
    private boolean recordingEnabled;
    private boolean noiseSuppressionEnabled = true;
    private boolean echoCancellationEnabled = true;
    private boolean automaticGainEnabled = true;
    private boolean compressorEnabled = true;
    private boolean batterySaverEnabled;
    private boolean monitorEnabled;

    public synchronized AudioMode getMode() {
        return mode;
    }

    public synchronized void setMode(AudioMode mode) {
        this.mode = mode;
        this.micGain = mode.defaultMicGain();
        this.internalGain = mode.defaultInternalGain();
    }

    public synchronized float getMicGain() {
        if (pushToMuteHeld || mode == AudioMode.INTERNAL_ONLY) {
            return 0f;
        }
        return micGain;
    }

    public synchronized float getInternalGain() {
        return internalGain;
    }

    public synchronized float getRemoteGain() {
        return remoteMicEnabled ? remoteGain : 0f;
    }

    public synchronized float getMasterGain() {
        return masterGain;
    }

    public synchronized float getEqLowDb() {
        return eqLowDb;
    }

    public synchronized float getEqMidDb() {
        return eqMidDb;
    }

    public synchronized float getEqHighDb() {
        return eqHighDb;
    }

    public synchronized int getLatencyTargetMs() {
        return latencyTargetMs;
    }

    public synchronized int getVoiceFxMode() {
        return voiceFxMode;
    }

    public synchronized void setGains(float micGain, float internalGain, float remoteGain) {
        this.micGain = clamp01(micGain);
        this.internalGain = clamp01(internalGain);
        this.remoteGain = clamp01(remoteGain);
    }

    public synchronized void setAdvancedControls(
            float masterGain,
            float eqLowDb,
            float eqMidDb,
            float eqHighDb,
            int latencyTargetMs,
            int voiceFxMode,
            boolean recordingEnabled,
            boolean noiseSuppressionEnabled,
            boolean echoCancellationEnabled,
            boolean automaticGainEnabled,
            boolean compressorEnabled,
            boolean batterySaverEnabled,
            boolean monitorEnabled) {
        this.masterGain = clamp(masterGain, 0f, 1.5f);
        this.eqLowDb = clamp(eqLowDb, -12f, 12f);
        this.eqMidDb = clamp(eqMidDb, -12f, 12f);
        this.eqHighDb = clamp(eqHighDb, -12f, 12f);
        this.latencyTargetMs = Math.max(20, Math.min(200, latencyTargetMs));
        this.voiceFxMode = Math.max(VOICE_FX_NORMAL, Math.min(VOICE_FX_ROBOT, voiceFxMode));
        this.recordingEnabled = recordingEnabled;
        this.noiseSuppressionEnabled = noiseSuppressionEnabled;
        this.echoCancellationEnabled = echoCancellationEnabled;
        this.automaticGainEnabled = automaticGainEnabled;
        this.compressorEnabled = compressorEnabled;
        this.batterySaverEnabled = batterySaverEnabled;
        this.monitorEnabled = monitorEnabled;
    }

    public synchronized void setRemoteMicEnabled(boolean remoteMicEnabled) {
        this.remoteMicEnabled = remoteMicEnabled;
    }

    public synchronized boolean isRemoteMicEnabled() {
        return remoteMicEnabled;
    }

    public synchronized boolean shouldCaptureLocalMic() {
        return mode != AudioMode.INTERNAL_ONLY;
    }

    public synchronized boolean shouldCaptureInternalAudio() {
        return mode != AudioMode.MIC_ONLY;
    }

    public synchronized boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    public synchronized boolean isNoiseSuppressionEnabled() {
        return noiseSuppressionEnabled;
    }

    public synchronized boolean isEchoCancellationEnabled() {
        return echoCancellationEnabled;
    }

    public synchronized boolean isAutomaticGainEnabled() {
        return automaticGainEnabled;
    }

    public synchronized boolean isCompressorEnabled() {
        return compressorEnabled;
    }

    public synchronized boolean isBatterySaverEnabled() {
        return batterySaverEnabled;
    }

    public synchronized boolean isMonitorEnabled() {
        return monitorEnabled;
    }

    public synchronized void setPushToMuteHeld(boolean pushToMuteHeld) {
        this.pushToMuteHeld = pushToMuteHeld;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                masterGain,
                eqLowDb,
                eqMidDb,
                eqHighDb,
                latencyTargetMs,
                voiceFxMode,
                recordingEnabled,
                noiseSuppressionEnabled,
                echoCancellationEnabled,
                automaticGainEnabled,
                compressorEnabled,
                batterySaverEnabled,
                monitorEnabled);
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public static final class Snapshot {
        public final float masterGain;
        public final float eqLowDb;
        public final float eqMidDb;
        public final float eqHighDb;
        public final int latencyTargetMs;
        public final int voiceFxMode;
        public final boolean recordingEnabled;
        public final boolean noiseSuppressionEnabled;
        public final boolean echoCancellationEnabled;
        public final boolean automaticGainEnabled;
        public final boolean compressorEnabled;
        public final boolean batterySaverEnabled;
        public final boolean monitorEnabled;

        private Snapshot(
                float masterGain,
                float eqLowDb,
                float eqMidDb,
                float eqHighDb,
                int latencyTargetMs,
                int voiceFxMode,
                boolean recordingEnabled,
                boolean noiseSuppressionEnabled,
                boolean echoCancellationEnabled,
                boolean automaticGainEnabled,
                boolean compressorEnabled,
                boolean batterySaverEnabled,
                boolean monitorEnabled) {
            this.masterGain = masterGain;
            this.eqLowDb = eqLowDb;
            this.eqMidDb = eqMidDb;
            this.eqHighDb = eqHighDb;
            this.latencyTargetMs = latencyTargetMs;
            this.voiceFxMode = voiceFxMode;
            this.recordingEnabled = recordingEnabled;
            this.noiseSuppressionEnabled = noiseSuppressionEnabled;
            this.echoCancellationEnabled = echoCancellationEnabled;
            this.automaticGainEnabled = automaticGainEnabled;
            this.compressorEnabled = compressorEnabled;
            this.batterySaverEnabled = batterySaverEnabled;
            this.monitorEnabled = monitorEnabled;
        }
    }
}
