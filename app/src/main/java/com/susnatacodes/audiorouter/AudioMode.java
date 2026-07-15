package com.susnatacodes.audiorouter;

public enum AudioMode {
    INTERNAL_ONLY("Internal only"),
    MIC_AND_INTERNAL("Mic + internal"),
    MIC_ONLY("Mic only");

    private final String label;

    AudioMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public float defaultMicGain() {
        if (this == INTERNAL_ONLY) {
            return 0f;
        }
        return 1f;
    }

    public float defaultInternalGain() {
        if (this == MIC_ONLY) {
            return 0f;
        }
        return 1f;
    }
}
