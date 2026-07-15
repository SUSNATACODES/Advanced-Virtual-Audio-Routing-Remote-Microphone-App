package com.susnatacodes.audiorouter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint({"ClickableViewAccessibility", "SetTextI18n"})
public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE_AUDIO = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    private static final int REQUEST_PICK_AUDIO = 1003;

    private static final int BG = Color.rgb(7, 11, 22);
    private static final int SURFACE = Color.rgb(17, 25, 43);
    private static final int SURFACE_ALT = Color.rgb(24, 35, 58);
    private static final int LINE = Color.rgb(43, 57, 84);
    private static final int TEXT = Color.rgb(244, 247, 251);
    private static final int MUTED = Color.rgb(151, 164, 190);
    private static final int ACCENT = Color.rgb(28, 200, 160);
    private static final int CYAN = Color.rgb(93, 184, 246);
    private static final int WARNING = Color.rgb(242, 184, 75);
    private static final int DANGER = Color.rgb(238, 95, 118);

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            updateLiveStatus();
            statusHandler.postDelayed(this, 1000);
        }
    };

    private AudioMode selectedMode = AudioMode.MIC_ONLY;
    private boolean routingActive;
    private boolean internalCaptureReady;
    private boolean filePlaying;
    private Uri selectedFileUri;
    private String selectedFileName = "No audio selected";
    private int voiceFxMode = AudioSessionState.VOICE_FX_NORMAL;

    private TextView statusValue;
    private TextView routeBadge;
    private TextView latencyValue;
    private TextView captureValue;
    private TextView remoteValue;
    private TextView recordingValue;
    private TextView batteryValue;
    private TextView fileValue;
    private TextView fileStatusValue;
    private Button internalModeButton;
    private Button mixModeButton;
    private Button micModeButton;
    private Button startButton;
    private Button projectionButton;
    private Button pushToTalkButton;
    private Button chooseFileButton;
    private Button playFileButton;
    private Button stopFileButton;
    private Button voiceCleanButton;
    private Button voiceDeepButton;
    private Button voiceBrightButton;
    private Button voiceRobotButton;
    private SeekBar micSeek;
    private SeekBar internalSeek;
    private SeekBar remoteSeek;
    private SeekBar fileSeek;
    private SeekBar masterSeek;
    private SeekBar latencySeek;
    private SeekBar eqLowSeek;
    private SeekBar eqMidSeek;
    private SeekBar eqHighSeek;
    private Switch remoteSwitch;
    private Switch fileLoopSwitch;
    private Switch recordingSwitch;
    private Switch noiseSwitch;
    private Switch echoSwitch;
    private Switch autoGainSwitch;
    private Switch compressorSwitch;
    private Switch batterySwitch;
    private Switch monitorSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        statusHandler.post(statusTicker);
        if (hasRequiredPermissions()) {
            startRoutingUi();
        } else {
            requestNeededPermissions();
            statusValue.setText(R.string.status_permissions_needed);
        }
    }

    @Override
    protected void onDestroy() {
        statusHandler.removeCallbacks(statusTicker);
        if (routingActive) {
            sendPushToMute(false);
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE_AUDIO) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = serviceIntent(AudioRouterService.ACTION_SET_MEDIA_PROJECTION);
                intent.putExtra(AudioRouterService.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(AudioRouterService.EXTRA_RESULT_DATA, data);
                if (routingActive) {
                    startRouterService(intent);
                }
                internalCaptureReady = true;
                statusValue.setText(R.string.status_internal_capture_ready);
            } else {
                internalCaptureReady = false;
                statusValue.setText(R.string.status_internal_capture_off);
            }
            updateLiveStatus();
        } else if (requestCode == REQUEST_PICK_AUDIO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                selectedFileUri = data.getData();
                selectedFileName = resolveFileName(selectedFileUri);
                persistFilePermission(data, selectedFileUri);
                filePlaying = false;
                if (fileValue != null) {
                    fileValue.setText(selectedFileName);
                }
                if (fileStatusValue != null) {
                    fileStatusValue.setText("Ready");
                }
                statusValue.setText("File selected: " + selectedFileName);
                sendFileDeck();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && hasRequiredPermissions()) {
            startRoutingUi();
        } else if (requestCode == REQUEST_PERMISSIONS) {
            statusValue.setText(R.string.status_permissions_needed);
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(22));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeroPanel(), blockParams());
        root.addView(buildModePanel(), blockParams());
        root.addView(buildMixerPanel(), blockParams());
        root.addView(buildFileDeckPanel(), blockParams());
        root.addView(buildTransportPanel(), blockParams());
        root.addView(buildProcessingPanel(), blockParams());
        root.addView(buildEqPanel(), blockParams());
        root.addView(buildRemotePanel(), blockParams());
        root.addView(buildTalkPanel(), blockParams());

        setContentView(scrollView);
    }

    private LinearLayout buildHeroPanel() {
        LinearLayout panel = panel(null);
        panel.setBackground(gradient(Color.rgb(12, 23, 43), Color.rgb(11, 83, 79), dp(12)));

        TextView title = text(getString(R.string.app_name), 25, TEXT, Typeface.BOLD);
        panel.addView(title);

        routeBadge = chip("Routing engine ready", ACCENT);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
        chipParams.setMargins(0, dp(12), 0, dp(12));
        panel.addView(routeBadge, chipParams);

        statusValue = text(getString(R.string.status_mic_only), 15, Color.rgb(211, 250, 238), Typeface.BOLD);
        panel.addView(statusValue);

        LinearLayout gridOne = row();
        latencyValue = statChip(gridOne, "Latency", "40 ms target", CYAN);
        captureValue = statChip(gridOne, "Internal", "Permission needed", WARNING);
        panel.addView(gridOne);

        LinearLayout gridTwo = row();
        remoteValue = statChip(gridTwo, "Remote mic", "Offline", MUTED);
        recordingValue = statChip(gridTwo, "Recorder", "Standby", MUTED);
        panel.addView(gridTwo);

        LinearLayout gridThree = row();
        batteryValue = statChip(gridThree, "Power", "Low latency", ACCENT);
        fileStatusValue = statChip(gridThree, "File deck", "No file", MUTED);
        panel.addView(gridThree);

        return panel;
    }

    private LinearLayout buildModePanel() {
        LinearLayout panel = panel("Routing mode");
        LinearLayout modeRow = row();
        internalModeButton = modeTile("Internal\nOnly");
        mixModeButton = modeTile("Mic +\nInternal");
        micModeButton = modeTile("Mic\nOnly");
        modeRow.addView(internalModeButton, weightHeightParams(66));
        modeRow.addView(gap(dp(8)));
        modeRow.addView(mixModeButton, weightHeightParams(66));
        modeRow.addView(gap(dp(8)));
        modeRow.addView(micModeButton, weightHeightParams(66));
        panel.addView(modeRow);

        internalModeButton.setOnClickListener(view -> setMode(AudioMode.INTERNAL_ONLY));
        mixModeButton.setOnClickListener(view -> setMode(AudioMode.MIC_AND_INTERNAL));
        micModeButton.setOnClickListener(view -> setMode(AudioMode.MIC_ONLY));
        updateModeButtons();
        return panel;
    }

    private LinearLayout buildMixerPanel() {
        LinearLayout panel = panel("Mixer");
        micSeek = addMixerSlider(panel, "Microphone", 100, ACCENT, this::sendLevels);
        internalSeek = addMixerSlider(panel, "Internal audio", 0, CYAN, this::sendLevels);
        remoteSeek = addMixerSlider(panel, "Remote mic", 100, WARNING, this::sendLevels);
        fileSeek = addMixerSlider(panel, "File deck", 85, CYAN, this::sendFileDeck);
        masterSeek = addMixerSlider(panel, "Master output", 100, ACCENT, this::sendAdvanced);
        masterSeek.setMax(150);
        return panel;
    }

    private LinearLayout buildFileDeckPanel() {
        LinearLayout panel = panel("Audio file deck");

        LinearLayout fileCard = new LinearLayout(this);
        fileCard.setOrientation(LinearLayout.VERTICAL);
        fileCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        fileCard.setBackground(rounded(SURFACE_ALT, dp(8), LINE));

        fileCard.addView(text("Selected audio", 12, MUTED, Typeface.BOLD));
        fileValue = text(selectedFileName, 16, TEXT, Typeface.BOLD);
        fileValue.setPadding(0, dp(4), 0, 0);
        fileCard.addView(fileValue);
        TextView hint = text("Pick music, effects, backing tracks, or class audio from your phone.", 12, MUTED, Typeface.NORMAL);
        hint.setPadding(0, dp(6), 0, 0);
        fileCard.addView(hint);
        panel.addView(fileCard, blockParams());

        LinearLayout rowOne = row();
        chooseFileButton = primaryButton("Choose audio");
        chooseFileButton.setOnClickListener(view -> chooseAudioFile());
        rowOne.addView(chooseFileButton, weightHeightParams(52));
        rowOne.addView(gap(dp(10)));

        playFileButton = secondaryButton("Play");
        playFileButton.setOnClickListener(view -> playSelectedFile());
        rowOne.addView(playFileButton, weightHeightParams(52));
        panel.addView(rowOne);

        LinearLayout rowTwo = row();
        stopFileButton = secondaryButton("Stop file");
        stopFileButton.setOnClickListener(view -> stopSelectedFile());
        rowTwo.addView(stopFileButton, weightHeightParams(52));
        rowTwo.addView(gap(dp(10)));

        Button clearFileButton = secondaryButton("Clear");
        clearFileButton.setOnClickListener(view -> clearSelectedFile());
        rowTwo.addView(clearFileButton, weightHeightParams(52));
        LinearLayout.LayoutParams rowTwoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowTwoParams.setMargins(0, dp(10), 0, 0);
        panel.addView(rowTwo, rowTwoParams);

        fileLoopSwitch = switchRow(panel, "Loop selected file", "Keep backing track or sound bed repeating");
        fileLoopSwitch.setOnCheckedChangeListener((button, checked) -> sendFileDeck());
        return panel;
    }

    private LinearLayout buildTransportPanel() {
        LinearLayout panel = panel("Transport");
        LinearLayout rowOne = row();
        projectionButton = primaryButton("Enable internal capture");
        projectionButton.setOnClickListener(view -> requestInternalAudioCapture());
        rowOne.addView(projectionButton, weightHeightParams(52));
        rowOne.addView(gap(dp(10)));

        startButton = secondaryButton("Stop routing");
        startButton.setOnClickListener(view -> toggleRouting());
        rowOne.addView(startButton, weightHeightParams(52));
        panel.addView(rowOne);

        recordingSwitch = switchRow(panel, "Record mixed output", "Saves WAV to app music folder");
        recordingSwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);

        monitorSwitch = switchRow(panel, "Local monitor", "Prepared for headphone preview");
        monitorSwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);

        latencySeek = addMixerSlider(panel, "Latency target", 20, CYAN, this::sendAdvanced);
        latencySeek.setMax(180);
        return panel;
    }

    private LinearLayout buildProcessingPanel() {
        LinearLayout panel = panel("Voice processing");
        LinearLayout voiceRow = row();
        voiceCleanButton = voiceButton("Clean");
        voiceDeepButton = voiceButton("Deep");
        voiceBrightButton = voiceButton("Bright");
        voiceRobotButton = voiceButton("Robot");
        voiceRow.addView(voiceCleanButton, weightHeightParams(44));
        voiceRow.addView(gap(dp(6)));
        voiceRow.addView(voiceDeepButton, weightHeightParams(44));
        voiceRow.addView(gap(dp(6)));
        voiceRow.addView(voiceBrightButton, weightHeightParams(44));
        voiceRow.addView(gap(dp(6)));
        voiceRow.addView(voiceRobotButton, weightHeightParams(44));
        panel.addView(voiceRow);

        voiceCleanButton.setOnClickListener(view -> setVoiceFx(AudioSessionState.VOICE_FX_NORMAL));
        voiceDeepButton.setOnClickListener(view -> setVoiceFx(AudioSessionState.VOICE_FX_DEEP));
        voiceBrightButton.setOnClickListener(view -> setVoiceFx(AudioSessionState.VOICE_FX_BRIGHT));
        voiceRobotButton.setOnClickListener(view -> setVoiceFx(AudioSessionState.VOICE_FX_ROBOT));
        updateVoiceButtons();

        noiseSwitch = switchRow(panel, "Noise suppression", "Reduce fan and room noise");
        echoSwitch = switchRow(panel, "Echo cancellation", "Control speaker feedback");
        autoGainSwitch = switchRow(panel, "Auto gain", "Keep voice level steady");
        compressorSwitch = switchRow(panel, "Output limiter", "Protect listeners from clipping");
        noiseSwitch.setChecked(true);
        echoSwitch.setChecked(true);
        autoGainSwitch.setChecked(true);
        compressorSwitch.setChecked(true);
        noiseSwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);
        echoSwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);
        autoGainSwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);
        compressorSwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);
        return panel;
    }

    private LinearLayout buildEqPanel() {
        LinearLayout panel = panel("Output EQ");
        eqLowSeek = addEqSlider(panel, "Low", this::sendAdvanced);
        eqMidSeek = addEqSlider(panel, "Mid", this::sendAdvanced);
        eqHighSeek = addEqSlider(panel, "High", this::sendAdvanced);
        return panel;
    }

    private LinearLayout buildRemotePanel() {
        LinearLayout panel = panel("Remote microphone");
        remoteSwitch = switchRow(panel, "Receiver", "Advertise on local network, UDP port 38420");
        remoteSwitch.setOnCheckedChangeListener(this::onRemoteMicChanged);

        batterySwitch = switchRow(panel, "Battery saver", "Lower background work when mobile");
        batterySwitch.setOnCheckedChangeListener(this::onAdvancedSwitchChanged);
        return panel;
    }

    private LinearLayout buildTalkPanel() {
        LinearLayout panel = panel("Talk control");
        pushToTalkButton = dangerButton("Hold to mute mic");
        pushToTalkButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                sendPushToMute(true);
                statusValue.setText(R.string.status_mic_muted);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
                sendPushToMute(false);
                statusValue.setText(selectedMode.label());
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                sendPushToMute(false);
                statusValue.setText(selectedMode.label());
                return true;
            }
            return false;
        });
        panel.addView(pushToTalkButton, fullWidthButtonParams());
        return panel;
    }

    private void setMode(AudioMode mode) {
        selectedMode = mode;
        micSeek.setProgress(Math.round(mode.defaultMicGain() * 100f));
        internalSeek.setProgress(Math.round(mode.defaultInternalGain() * 100f));
        updateModeButtons();

        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_MODE);
        intent.putExtra(AudioRouterService.EXTRA_MODE, mode.name());
        if (routingActive) {
            startRouterService(intent);
        }
        sendLevels();
        statusValue.setText(mode.label());
        updateLiveStatus();
    }

    private void updateModeButtons() {
        styleModeButton(internalModeButton, selectedMode == AudioMode.INTERNAL_ONLY);
        styleModeButton(mixModeButton, selectedMode == AudioMode.MIC_AND_INTERNAL);
        styleModeButton(micModeButton, selectedMode == AudioMode.MIC_ONLY);
    }

    private void setVoiceFx(int mode) {
        voiceFxMode = mode;
        updateVoiceButtons();
        sendAdvanced();
    }

    private void updateVoiceButtons() {
        styleVoiceButton(voiceCleanButton, voiceFxMode == AudioSessionState.VOICE_FX_NORMAL);
        styleVoiceButton(voiceDeepButton, voiceFxMode == AudioSessionState.VOICE_FX_DEEP);
        styleVoiceButton(voiceBrightButton, voiceFxMode == AudioSessionState.VOICE_FX_BRIGHT);
        styleVoiceButton(voiceRobotButton, voiceFxMode == AudioSessionState.VOICE_FX_ROBOT);
    }

    private void startRoutingUi() {
        routingActive = true;
        sendStart();
        setMode(AudioMode.MIC_ONLY);
        sendAdvanced();
        sendFileDeck();
        updateTransportButton();
    }

    private void toggleRouting() {
        if (routingActive) {
            Intent intent = serviceIntent(AudioRouterService.ACTION_STOP);
            startService(intent);
            routingActive = false;
            statusValue.setText(R.string.status_routing_stopped);
        } else if (hasRequiredPermissions()) {
            routingActive = true;
            sendStart();
            sendLevels();
            sendAdvanced();
            sendFileDeck();
            statusValue.setText(selectedMode.label());
        } else {
            requestNeededPermissions();
        }
        updateTransportButton();
        updateLiveStatus();
    }

    private void updateTransportButton() {
        if (startButton == null) {
            return;
        }
        startButton.setText(routingActive ? "Stop routing" : "Start routing");
        startButton.setBackground(routingActive
                ? rounded(SURFACE_ALT, dp(8), LINE)
                : rounded(ACCENT, dp(8), 0));
        startButton.setTextColor(routingActive ? TEXT : Color.rgb(3, 22, 19));
    }

    private void sendStart() {
        startRouterService(serviceIntent(AudioRouterService.ACTION_START));
    }

    private void sendLevels() {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_LEVELS);
        intent.putExtra(AudioRouterService.EXTRA_MIC_GAIN, micSeek.getProgress() / 100f);
        intent.putExtra(AudioRouterService.EXTRA_INTERNAL_GAIN, internalSeek.getProgress() / 100f);
        intent.putExtra(AudioRouterService.EXTRA_REMOTE_GAIN, remoteSeek.getProgress() / 100f);
        if (routingActive) {
            startRouterService(intent);
        }
        updateLiveStatus();
    }

    private void sendAdvanced() {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_ADVANCED);
        intent.putExtra(AudioRouterService.EXTRA_MASTER_GAIN, masterSeek.getProgress() / 100f);
        intent.putExtra(AudioRouterService.EXTRA_EQ_LOW_DB, eqDb(eqLowSeek));
        intent.putExtra(AudioRouterService.EXTRA_EQ_MID_DB, eqDb(eqMidSeek));
        intent.putExtra(AudioRouterService.EXTRA_EQ_HIGH_DB, eqDb(eqHighSeek));
        intent.putExtra(AudioRouterService.EXTRA_LATENCY_TARGET_MS, latencyMs());
        intent.putExtra(AudioRouterService.EXTRA_VOICE_FX, voiceFxMode);
        intent.putExtra(AudioRouterService.EXTRA_RECORDING, recordingSwitch.isChecked());
        intent.putExtra(AudioRouterService.EXTRA_NOISE_SUPPRESSION, noiseSwitch.isChecked());
        intent.putExtra(AudioRouterService.EXTRA_ECHO_CANCELLATION, echoSwitch.isChecked());
        intent.putExtra(AudioRouterService.EXTRA_AUTO_GAIN, autoGainSwitch.isChecked());
        intent.putExtra(AudioRouterService.EXTRA_COMPRESSOR, compressorSwitch.isChecked());
        intent.putExtra(AudioRouterService.EXTRA_BATTERY_SAVER, batterySwitch.isChecked());
        intent.putExtra(AudioRouterService.EXTRA_MONITOR, monitorSwitch.isChecked());
        if (routingActive) {
            startRouterService(intent);
        }
        updateLiveStatus();
    }

    private void sendFileDeck() {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_FILE_DECK);
        intent.putExtra(AudioRouterService.EXTRA_FILE_URI,
                selectedFileUri == null ? null : selectedFileUri.toString());
        intent.putExtra(AudioRouterService.EXTRA_FILE_NAME, selectedFileName);
        intent.putExtra(AudioRouterService.EXTRA_FILE_GAIN,
                fileSeek == null ? 0.85f : fileSeek.getProgress() / 100f);
        intent.putExtra(AudioRouterService.EXTRA_FILE_PLAYING, filePlaying);
        intent.putExtra(AudioRouterService.EXTRA_FILE_LOOP,
                fileLoopSwitch != null && fileLoopSwitch.isChecked());
        if (routingActive) {
            startRouterService(intent);
        }
        updateLiveStatus();
    }

    private void sendPushToMute(boolean held) {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_PUSH_TO_TALK);
        intent.putExtra(AudioRouterService.EXTRA_HELD, held);
        if (routingActive) {
            startRouterService(intent);
        }
    }

    private void onRemoteMicChanged(CompoundButton button, boolean checked) {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_REMOTE_MIC);
        intent.putExtra(AudioRouterService.EXTRA_ENABLED, checked);
        if (routingActive) {
            startRouterService(intent);
        }
        sendAdvanced();
        statusValue.setText(checked ? getString(R.string.status_remote_mic_listening) : selectedMode.label());
        updateLiveStatus();
    }

    private void onAdvancedSwitchChanged(CompoundButton button, boolean checked) {
        sendAdvanced();
    }

    private void chooseAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_AUDIO);
    }

    private void playSelectedFile() {
        if (selectedFileUri == null) {
            statusValue.setText("Choose an audio file first");
            updateLiveStatus();
            return;
        }
        if (!routingActive) {
            if (!hasRequiredPermissions()) {
                requestNeededPermissions();
                return;
            }
            routingActive = true;
            sendStart();
            sendLevels();
            sendAdvanced();
            updateTransportButton();
        }
        filePlaying = true;
        sendFileDeck();
        statusValue.setText("Playing: " + selectedFileName);
    }

    private void stopSelectedFile() {
        filePlaying = false;
        sendFileDeck();
        statusValue.setText("File stopped");
    }

    private void clearSelectedFile() {
        filePlaying = false;
        selectedFileUri = null;
        selectedFileName = "No audio selected";
        if (fileValue != null) {
            fileValue.setText(selectedFileName);
        }
        sendFileDeck();
        statusValue.setText(selectedFileName);
    }

    private void requestInternalAudioCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE_AUDIO);
    }

    private void updateLiveStatus() {
        if (routeBadge != null) {
            routeBadge.setText(routingActive ? "LIVE - " + selectedMode.label() : "ROUTER STOPPED");
            routeBadge.setTextColor(routingActive ? Color.rgb(5, 37, 32) : Color.rgb(51, 22, 22));
            routeBadge.setBackground(rounded(routingActive ? ACCENT : DANGER, dp(18), 0));
        }
        if (latencyValue != null) {
            latencyValue.setText(latencyMs() + " ms target");
        }
        if (captureValue != null) {
            captureValue.setText(internalCaptureReady ? "Internal ready" : "Mic path ready");
        }
        if (remoteValue != null) {
            remoteValue.setText(remoteSwitch != null && remoteSwitch.isChecked() ? "Listening :38420" : "Offline");
        }
        if (recordingValue != null) {
            recordingValue.setText(recordingSwitch != null && recordingSwitch.isChecked() ? "WAV armed" : "Standby");
        }
        if (batteryValue != null) {
            batteryValue.setText(batterySwitch != null && batterySwitch.isChecked() ? "Saver on" : "Low latency");
        }
        if (fileStatusValue != null) {
            if (selectedFileUri == null) {
                fileStatusValue.setText("No file");
            } else if (filePlaying) {
                fileStatusValue.setText(fileLoopSwitch != null && fileLoopSwitch.isChecked()
                        ? "Playing loop" : "Playing");
            } else {
                fileStatusValue.setText("Ready");
            }
        }
    }

    private String resolveFileName(Uri uri) {
        String fallback = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (SecurityException ignored) {
            // Fall back to URI path if the provider withholds display metadata.
        }
        return fallback == null ? "Selected audio" : fallback;
    }

    private void persistFilePermission(Intent data, Uri uri) {
        if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers grant temporary access only; playback still works during this session.
        }
    }

    private int latencyMs() {
        return latencySeek == null ? 40 : latencySeek.getProgress() + 20;
    }

    private float eqDb(SeekBar seekBar) {
        return seekBar == null ? 0f : seekBar.getProgress() - 12f;
    }

    private boolean hasRequiredPermissions() {
        return collectMissingPermissions().isEmpty();
    }

    private void requestNeededPermissions() {
        List<String> permissions = collectMissingPermissions();
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private List<String> collectMissingPermissions() {
        List<String> permissions = new ArrayList<>();
        addPermissionIfMissing(permissions, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermissionIfMissing(permissions, Manifest.permission.POST_NOTIFICATIONS);
            addPermissionIfMissing(permissions, Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addPermissionIfMissing(permissions, Manifest.permission.BLUETOOTH_CONNECT);
        }
        return permissions;
    }

    private void addPermissionIfMissing(List<String> permissions, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private Intent serviceIntent(String action) {
        Intent intent = new Intent(this, AudioRouterService.class);
        intent.setAction(action);
        return intent;
    }

    private void startRouterService(Intent intent) {
        try {
            startForegroundService(intent);
        } catch (SecurityException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permission_title)
                    .setMessage(R.string.dialog_permission_message)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show();
        }
    }

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(rounded(SURFACE, dp(10), LINE));
        if (title != null) {
            TextView label = text(title, 14, MUTED, Typeface.BOLD);
            label.setAllCaps(true);
            label.setLetterSpacing(0.08f);
            label.setPadding(0, 0, 0, dp(10));
            panel.addView(label);
        }
        return panel;
    }

    private TextView statChip(LinearLayout parent, String label, String value, int accent) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setPadding(dp(12), dp(10), dp(12), dp(10));
        chip.setBackground(rounded(SURFACE_ALT, dp(8), LINE));

        TextView labelView = text(label, 11, MUTED, Typeface.BOLD);
        labelView.setAllCaps(true);
        TextView valueView = text(value, 14, accent, Typeface.BOLD);
        valueView.setPadding(0, dp(4), 0, 0);
        chip.addView(labelView);
        chip.addView(valueView);

        LinearLayout.LayoutParams params = weightHeightParams(64);
        params.setMargins(0, dp(5), dp(6), dp(5));
        parent.addView(chip, params);
        return valueView;
    }

    private SeekBar addMixerSlider(LinearLayout parent, String label, int progress, int accent, Runnable onChange) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(8));
        card.setBackground(rounded(SURFACE_ALT, dp(8), LINE));

        LinearLayout labelRow = row();
        TextView title = text(label, 15, TEXT, Typeface.BOLD);
        TextView value = text(progress + "%", 13, accent, Typeface.BOLD);
        labelRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(value);
        card.addView(labelRow);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(progress);
        seekBar.setPadding(0, dp(6), 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int current, boolean fromUser) {
                value.setText(current + "%");
                if (fromUser) {
                    onChange.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                onChange.run();
            }
        });
        card.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        parent.addView(card, params);
        return seekBar;
    }

    private SeekBar addEqSlider(LinearLayout parent, String label, Runnable onChange) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(8));
        card.setBackground(rounded(SURFACE_ALT, dp(8), LINE));

        LinearLayout labelRow = row();
        TextView title = text(label, 15, TEXT, Typeface.BOLD);
        TextView value = text("0 dB", 13, CYAN, Typeface.BOLD);
        labelRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(value);
        card.addView(labelRow);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(24);
        seekBar.setProgress(12);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int current, boolean fromUser) {
                int db = current - 12;
                value.setText(String.format(Locale.US, "%+d dB", db));
                if (fromUser) {
                    onChange.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                onChange.run();
            }
        });
        card.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        parent.addView(card, params);
        return seekBar;
    }

    private Switch switchRow(LinearLayout parent, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rounded(SURFACE_ALT, dp(8), LINE));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15, TEXT, Typeface.BOLD));
        copy.addView(text(subtitle, 12, MUTED, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        row.addView(toggle);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        parent.addView(row, params);
        return toggle;
    }

    private Button modeTile(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private Button voiceButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private Button primaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.rgb(3, 22, 19));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(ACCENT, dp(8), 0));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(TEXT);
        button.setBackground(rounded(SURFACE_ALT, dp(8), LINE));
        return button;
    }

    private Button dangerButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(DANGER, dp(8), 0));
        return button;
    }

    private Button baseButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        return button;
    }

    private void styleModeButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? Color.rgb(3, 22, 19) : TEXT);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(selected
                ? rounded(ACCENT, dp(10), 0)
                : rounded(SURFACE_ALT, dp(10), LINE));
    }

    private void styleVoiceButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? Color.rgb(3, 22, 19) : TEXT);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(selected
                ? rounded(CYAN, dp(8), 0)
                : rounded(SURFACE_ALT, dp(8), LINE));
    }

    private TextView chip(String text, int color) {
        TextView chip = text(text, 12, Color.rgb(3, 22, 19), Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(rounded(color, dp(18), 0));
        return chip;
    }

    private TextView text(String content, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable gradient(int startColor, int endColor, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.rgb(44, 89, 91));
        return drawable;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private LinearLayout.LayoutParams weightHeightParams(int heightDp) {
        return new LinearLayout.LayoutParams(0, dp(heightDp), 1f);
    }

    private LinearLayout.LayoutParams fullWidthButtonParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54));
    }

    private Space gap(int width) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return space;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
