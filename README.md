# Advanced Audio Router

Android Studio project for a low-latency audio routing app prototype.

## What this version includes

- Three routing modes:
  - Internal audio only
  - Microphone + internal audio
  - Microphone only
- Runtime permission flow for microphone, nearby devices, Bluetooth, and notifications.
- MediaProjection request flow for Android internal-audio capture.
- Foreground service for long-running audio routing.
- Java audio engine scaffold using `AudioRecord` and Android `AudioPlaybackCaptureConfiguration`.
- Separate mic, internal, and remote mic gain controls.
- Push-to-talk state.
- UDP remote microphone receiver scaffold.
- NSD local-network discovery scaffold.

## Important Android limitation

On stock Android, a normal app cannot become the system microphone for every other app. This project can capture, mix, record, and stream audio inside this app, but routing the mixed signal into WhatsApp, Discord, phone calls, or games as their microphone requires one of these:

- Root/system-level Android audio policy work.
- OEM/preinstalled privileged app integration.
- A desktop bridge that exposes the phone audio as a virtual microphone on PC/Mac/Linux.

## Open in Android Studio

1. Open Android Studio.
2. Choose `File > Open`.
3. Select this folder:

   `C:\Users\Dell\Documents\Codex\2026-07-16\advanced-virtual-audio-routing-remote-microphone`

4. Let Android Studio sync Gradle.
5. If prompted, install the requested Android SDK or Gradle plugin.
6. Run the `app` configuration on an Android 10+ device.

## Next engineering steps

- Add an Opus codec or WebRTC audio stack for the remote microphone path.
- Add WAV/AAC recording output.
- Add AudioTrack monitoring for local preview.
- Move the mixer to C++ with Oboe for lower latency.
- Add a desktop bridge app for true virtual-microphone behavior in third-party apps.
