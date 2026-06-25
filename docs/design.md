# Ever Listen - Design & Architecture

## Architecture Overview
- **Flutter UI (Dart)**: Calls native plugin via MethodChannel for start/stop and configuration.
- **Native code (Kotlin/Swift)**: Handles low-level audio capture, VAD processing, file writing and background lifecycle.
- **VAD Processing**: Operates on short frames (10/20/30 ms). Uses a pre-roll buffer to include audio before trigger.
- **Storage Manager**: Enforces max storage by deleting oldest files when limit is exceeded.

## Plugin API (Dart)

```dart
// Start recording in detect or monitoring mode
await recorder.startRecording(mode: 'detect' | 'monitor');

// Stop recording
await recorder.stopRecording();

// Set sensitivity (0.0 = least, 1.0 = most)
await recorder.setSensitivity(0.6);

// Set max storage in MB
await recorder.setMaxStorageMb(200);

// Get current status
Map<String, dynamic>? status = await recorder.getStatus();

// Listen to events
recorder.events.listen((String event) {
  // 'speechStarted', 'speechEnded', 'fileReady:/path/to/file'
});

