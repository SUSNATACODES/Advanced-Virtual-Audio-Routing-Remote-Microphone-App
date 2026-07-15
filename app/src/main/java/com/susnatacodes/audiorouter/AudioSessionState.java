package com.susnatacodes.audiorouter;

public final class AudioSessionState {
    private AudioMode mode = AudioMode.MIC_ONLY;
    private float micGain = 1f;
    private float internalGain = 0f;
    private float remoteGain = 1f;
    private boolean pushToMuteHeld;
    private boolean remoteMicEnabled;

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

    public synchronized void setGains(float micGain, float internalGain, float remoteGain) {
        this.micGain = clamp01(micGain);
        this.internalGain = clamp01(internalGain);
        this.remoteGain = clamp01(remoteGain);
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

    public synchronized void setPushToMuteHeld(boolean pushToMuteHeld) {
        this.pushToMuteHeld = pushToMuteHeld;
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
