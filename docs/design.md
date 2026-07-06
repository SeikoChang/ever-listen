# Ever Listen - Design & Architecture

## Architecture Overview
- **Flutter UI (Dart)**: Calls native plugin via MethodChannel for start/stop and configuration.
- **Native code (Kotlin/Swift)**: Handles low-level audio capture, VAD processing, file writing and background lifecycle.
- **VAD Processing**: Operates on short frames (10/20/30 ms). Uses a pre-roll buffer to include audio before trigger.
- **Storage Manager**: Enforces max storage by deleting oldest files when limit is exceeded.

## Plugin API (Dart)

```dart
// Start recording in detect, monitor, or schedule mode
await recorder.startRecording(mode: 'detect' | 'monitor' | 'schedule');

// Stop recording
await recorder.stopRecording();

// Set sensitivity (0.0 = least, 1.0 = most)
await recorder.setSensitivity(0.6);

// Set max storage in MB
await recorder.setMaxStorageMb(200);

// Schedule recording (for schedule mode)
// startTime and endTime are ISO 8601 strings or Unix timestamps
await recorder.scheduleRecording({
  'startTime': '2026-06-25T14:00:00Z',
  'endTime': '2026-06-25T14:30:00Z',
  'repeat': 'once' | 'daily' | 'weekly',  // optional
  'timezone': 'UTC',  // optional
});

// Get current status
Map<String, dynamic>? status = await recorder.getStatus();

// Listen to events
recorder.events.listen((String event) {
  // 'speechStarted', 'speechEnded', 'fileReady:/path/to/file',
  // 'scheduleStarted', 'scheduleEnded'
});
```

## Recording Modes

### Detect Mode (VAD-based)
- Only records when speech is detected.
- Uses voice activity detection to identify speech frames.
- Pre-roll buffer captures audio before speech trigger.
- Minimum speech duration and silence duration thresholds prevent spurious recordings.

### Monitoring Mode (Always-On)
- Records continuously in the background.
- Respects platform constraints (foreground service on Android, background modes on iOS).
- Chunks recordings to manage memory and storage.

### Schedule Mode (Time-based)
- Records during user-specified time slots.
- Supports one-time schedules or recurring (daily, weekly).
- Uses platform-specific schedulers (WorkManager on Android, BGTaskScheduler on iOS).
- Can combine with VAD (record only speech) or always-on within the time slot.
- Shows notification when scheduled recording is active.

## Platform-Specific Implementation Notes

### Android
- Use a **foreground service** with a persistent notification for monitoring mode.
- Request permissions: `RECORD_AUDIO` and `FOREGROUND_SERVICE` (API 31+).
- Use `AudioRecord` for low-level PCM capture at 16 kHz.
- Handle Doze mode: ask users to whitelist the app if continuous recording needed.
- Use **WorkManager** for reliable schedule execution even if app is closed.

### iOS
- App Store requires explicit justification for background recording and a visible indicator.
- Add **Background Modes → Audio** in Xcode (Target → Signing & Capabilities).
- Use `AVAudioSession` with category `.playAndRecord` or `.record`.
- Set `AVAudioSession.sharedInstance().setActive(true)` to enable background audio.
- Use **BackgroundTasks** framework (BGTaskScheduler) for scheduled recording.

## Performance & Memory Considerations
- Frame-level processing (30 ms frames) requires efficient buffering.
- Use a circular buffer (pre-roll) to keep the last N frames before trigger.
- Compress recordings (Opus/AAC) to reduce storage footprint.
- Monitor CPU usage during VAD processing; offload to native code.

## Privacy & Compliance
- Always show a visible indicator when recording (notification, UI badge).
- Get explicit user consent before enabling monitoring mode or creating schedules.
- Provide easy toggles to disable recording and clear stored files.
- For Schedule mode: show clear notification when scheduled recording is active.
- Consider local-only mode vs. cloud backup; show clear consent for cloud upload.
- Provide a privacy policy explaining data handling and scheduled recording.
