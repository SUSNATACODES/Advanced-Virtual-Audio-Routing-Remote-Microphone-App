package com.codex.audiorouter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
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

public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE_AUDIO = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;

    private AudioMode selectedMode = AudioMode.MIC_ONLY;
    private TextView statusValue;
    private Button internalModeButton;
    private Button mixModeButton;
    private Button micModeButton;
    private SeekBar micSeek;
    private SeekBar internalSeek;
    private SeekBar remoteSeek;
    private Switch remoteSwitch;
    private Button startButton;
    private Button projectionButton;
    private Button pushToTalkButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (hasRequiredPermissions()) {
            startRoutingUi();
        } else {
            requestNeededPermissions();
            statusValue.setText("Permissions needed");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendPushToMute(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE_AUDIO) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = serviceIntent(AudioRouterService.ACTION_SET_MEDIA_PROJECTION);
                intent.putExtra(AudioRouterService.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(AudioRouterService.EXTRA_RESULT_DATA, data);
                startRouterService(intent);
                statusValue.setText("Internal capture ready");
            } else {
                statusValue.setText("Internal capture off");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && hasRequiredPermissions()) {
            startRoutingUi();
        } else if (requestCode == REQUEST_PERMISSIONS) {
            statusValue.setText("Permissions needed");
        }
    }

    private void buildUi() {
        int pad = dp(20);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Advanced Audio Router");
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        statusValue = new TextView(this);
        statusValue.setText("Mic only");
        statusValue.setTextColor(Color.rgb(15, 118, 110));
        statusValue.setTextSize(15);
        statusValue.setPadding(0, dp(6), 0, dp(18));
        root.addView(statusValue);

        LinearLayout modeRow = row();
        internalModeButton = modeButton("Internal");
        mixModeButton = modeButton("Mix");
        micModeButton = modeButton("Mic");
        modeRow.addView(internalModeButton, weightParams());
        modeRow.addView(gap(dp(8)));
        modeRow.addView(mixModeButton, weightParams());
        modeRow.addView(gap(dp(8)));
        modeRow.addView(micModeButton, weightParams());
        root.addView(modeRow);

        internalModeButton.setOnClickListener(view -> setMode(AudioMode.INTERNAL_ONLY));
        mixModeButton.setOnClickListener(view -> setMode(AudioMode.MIC_AND_INTERNAL));
        micModeButton.setOnClickListener(view -> setMode(AudioMode.MIC_ONLY));

        root.addView(sectionLabel("Levels"));
        micSeek = addSlider(root, "Microphone", 100);
        internalSeek = addSlider(root, "Internal audio", 0);
        remoteSeek = addSlider(root, "Remote mic", 100);

        SeekBar.OnSeekBarChangeListener levelListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    sendLevels();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendLevels();
            }
        };
        micSeek.setOnSeekBarChangeListener(levelListener);
        internalSeek.setOnSeekBarChangeListener(levelListener);
        remoteSeek.setOnSeekBarChangeListener(levelListener);

        root.addView(sectionLabel("Capture"));
        projectionButton = primaryButton("Enable internal audio");
        projectionButton.setOnClickListener(view -> requestInternalAudioCapture());
        root.addView(projectionButton, fullWidthButtonParams());

        root.addView(gap(dp(10)));
        startButton = secondaryButton("Stop routing");
        startButton.setOnClickListener(view -> {
            Intent intent = serviceIntent(AudioRouterService.ACTION_STOP);
            startService(intent);
            statusValue.setText("Routing stopped");
        });
        root.addView(startButton, fullWidthButtonParams());

        root.addView(sectionLabel("Remote"));
        remoteSwitch = new Switch(this);
        remoteSwitch.setText("Remote mic receiver");
        remoteSwitch.setTextSize(16);
        remoteSwitch.setTextColor(Color.rgb(17, 24, 39));
        remoteSwitch.setPadding(0, dp(8), 0, dp(8));
        remoteSwitch.setOnCheckedChangeListener(this::onRemoteMicChanged);
        root.addView(remoteSwitch);

        root.addView(sectionLabel("Talk"));
        pushToTalkButton = secondaryButton("Hold to mute mic");
        pushToTalkButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                sendPushToMute(true);
                statusValue.setText("Mic muted");
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                sendPushToMute(false);
                statusValue.setText(selectedMode.label());
                return true;
            }
            return false;
        });
        root.addView(pushToTalkButton, fullWidthButtonParams());

        setContentView(scrollView);
    }

    private void setMode(AudioMode mode) {
        selectedMode = mode;
        micSeek.setProgress(Math.round(mode.defaultMicGain() * 100f));
        internalSeek.setProgress(Math.round(mode.defaultInternalGain() * 100f));
        updateModeButtons();

        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_MODE);
        intent.putExtra(AudioRouterService.EXTRA_MODE, mode.name());
        startRouterService(intent);
        sendLevels();
        statusValue.setText(mode.label());
    }

    private void updateModeButtons() {
        styleModeButton(internalModeButton, selectedMode == AudioMode.INTERNAL_ONLY);
        styleModeButton(mixModeButton, selectedMode == AudioMode.MIC_AND_INTERNAL);
        styleModeButton(micModeButton, selectedMode == AudioMode.MIC_ONLY);
    }

    private void sendStart() {
        startRouterService(serviceIntent(AudioRouterService.ACTION_START));
    }

    private void sendLevels() {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_LEVELS);
        intent.putExtra(AudioRouterService.EXTRA_MIC_GAIN, micSeek.getProgress() / 100f);
        intent.putExtra(AudioRouterService.EXTRA_INTERNAL_GAIN, internalSeek.getProgress() / 100f);
        intent.putExtra(AudioRouterService.EXTRA_REMOTE_GAIN, remoteSeek.getProgress() / 100f);
        startRouterService(intent);
    }

    private void sendPushToMute(boolean held) {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_PUSH_TO_TALK);
        intent.putExtra(AudioRouterService.EXTRA_HELD, held);
        startRouterService(intent);
    }

    private void onRemoteMicChanged(CompoundButton button, boolean checked) {
        Intent intent = serviceIntent(AudioRouterService.ACTION_SET_REMOTE_MIC);
        intent.putExtra(AudioRouterService.EXTRA_ENABLED, checked);
        startRouterService(intent);
        statusValue.setText(checked ? "Remote mic listening" : selectedMode.label());
    }

    private void requestInternalAudioCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE_AUDIO);
    }

    private void startRoutingUi() {
        sendStart();
        setMode(AudioMode.MIC_ONLY);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (SecurityException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Microphone and foreground service permissions are required.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(91, 100, 114));
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(24), 0, dp(8));
        return label;
    }

    private SeekBar addSlider(LinearLayout root, String labelText, int progress) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.rgb(17, 24, 39));
        label.setTextSize(16);
        label.setPadding(0, dp(8), 0, 0);
        root.addView(label);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(progress);
        root.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)));
        return seekBar;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(0, 0, 0, dp(4));
        return layout;
    }

    private Button modeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(Color.rgb(15, 118, 110), dp(8), 0));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(Color.rgb(17, 24, 39));
        button.setBackground(rounded(Color.WHITE, dp(8), Color.rgb(216, 222, 232)));
        return button;
    }

    private void styleModeButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : Color.rgb(17, 24, 39));
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(selected
                ? rounded(Color.rgb(15, 118, 110), dp(8), 0)
                : rounded(Color.WHITE, dp(8), Color.rgb(216, 222, 232)));
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

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, dp(48), 1f);
    }

    private LinearLayout.LayoutParams fullWidthButtonParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
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

