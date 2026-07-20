# Ever Listen - Project Handoff & Current State

## Executive Summary
This is a Flutter + native plugins skeleton for a cross-platform voice recorder with three modes:
1. **Detect mode**: VAD-based (only record when speech detected)
2. **Monitoring mode**: Always-on background recording
3. **Schedule mode**: Record during user-specified time slots (one-time, daily, weekly)

Features include sensitivity tuning, pre-roll buffering, limited storage enforcement, scheduled recording, and platform-specific background handling.

**Current status**: Flutter scaffolding and the Android foundation are complete. Android unit-test coverage, WebRTC VAD JNI integration, reboot recovery, foreground-service compatibility checks, initial profiling, and local storage/failure-path hardening are done. Android device and battery validation are deferred for this iteration. **Phase 5 iOS implementation is in progress:** channel parity, initial AVAudioEngine capture, permission handling, audio output, persisted schedule CRUD, and a BGTaskScheduler dispatcher are implemented; iOS VAD linking and full background/device validation remain.

---

## What Has Been Done

### ✅ Project Structure
- Flutter app skeleton at `flutter_voice_recorder/`
- Android (Kotlin) plugin stubs with:
  - `RecorderPlugin.kt` — MethodChannel entry point
  - `RecorderService.kt` — foreground service for background recording
  - `VADNativeBridge.kt` — JNI stub for native VAD/encoding
- iOS (Swift) plugin stubs with:
  - `RecorderPlugin.swift` — MethodChannel entry point
  - Skeleton for AVAudioEngine integration
- Documentation:
  - `docs/design.md` — architecture, API (3 modes), platform notes
  - `docs/VAD_build_instructions.md` — WebRTC VAD build guide

### ✅ Dart/Flutter Layer
- `lib/main.dart` — example UI with:
  - Mode toggle (Detect vs. Monitoring vs. Schedule)
  - Sensitivity slider (0.0–1.0)
  - Max storage input (MB)
  - Start/Stop button
  - Status display
- `lib/src/recorder_plugin.dart` — MethodChannel wrapper API:
  - `startRecording(mode)` — start in detect, monitor, or schedule mode
  - `stopRecording()` — stop recording
  - `setSensitivity(s)` — set VAD sensitivity
  - `setMaxStorageMb(mb)` — configure storage limit
  - `scheduleRecording(startTime, endTime, repeat, timezone)` — schedule recording
  - `getStatus()` — query current status
  - `events` stream — receive speechStarted/speechEnded/fileReady/**scheduleStarted/scheduleEnded** events

### ✅ Python Prototype
- `tools/prototype/vad_recorder.py` — ready-to-run VAD detector using WebRTC VAD:
  - Detects speech with configurable sensitivity (0..1)
  - Pre-roll buffer (1s default) to capture audio before trigger
  - Minimum speech duration and silence duration thresholds
  - Automatic storage limit enforcement (deletes oldest files)
  - Outputs 16-bit PCM WAV files
  - Usage: `python vad_recorder.py --sensitivity 0.6 --max-storage-mb 200`

### ✅ CI/CD
- GitHub Actions workflow (`.github/workflows/flutter-ci.yml`) runs `flutter analyze` on PRs

### ✅ Android Foundation
- Android unit tests cover `ScheduleStore`, `AudioFrameBuffer`, and `AudioFileWriter`
- Android local test configuration is in place and `./gradlew test` passes
- WebRTC VAD C sources and JNI bindings are integrated
- Native VAD libraries have been built for `arm64-v8a` and `armeabi-v7a`
- `VADProcessorTest.kt` verifies native loading fallback to `MockVADProcessor`
- `BootReceiver.kt` reschedules alarms after device reboot
- Foreground-service microphone compatibility on API 30+ has been verified
- Initial CPU and memory profiling for mock versus WebRTC VAD is complete

### ✅ License & Meta
- MIT License
- .gitignore configured for Flutter/Dart/Python
- Initial README

---

## What Remains to Be Done

### Priority 1 — Core Android Implementation (Detect + Monitoring + Schedule Modes)

**RecorderPlugin.kt** — Implement MethodCall handlers:
- [ ] `startRecording()`: Start AudioRecord or foreground service, initialize VAD
- [ ] `stopRecording()`: Clean up audio capture, close files, stop service
- [ ] `setSensitivity()`: Pass sensitivity to VAD layer (convert 0..1 to WebRTC aggressiveness)
- [ ] `setMaxStorageMb()`: Configure storage limit
- [ ] `scheduleRecording()`: **NEW** — Set up WorkManager scheduled task
- [ ] `cancelSchedule()`: **NEW** — Cancel scheduled recording
- [ ] `getSchedules()`: **NEW** — List all scheduled recordings
- [ ] `getStatus()`: Return map with recording state, current file, storage used, active schedules
- [ ] Set up EventChannel.StreamHandler to emit events: `"speechStarted"`, `"speechEnded"`, `"fileReady:/path"`, `"scheduleStarted"`, `"scheduleEnded"`

**RecorderService.kt** — Implement foreground service:
- [ ] Start AudioRecord with PCM 16-bit mono at 16 kHz
- [ ] Frame buffer management (30 ms frames for VAD)
- [ ] Call VAD native bridge on each frame
- [ ] On speech detect: prepare output file, use pre-roll buffer from ring buffer
- [ ] On silence timeout: close current file, emit fileReady event
- [ ] Handle service lifecycle (onStartCommand, onDestroy)
- [ ] Request RECORD_AUDIO permission at runtime (API 30+)
- [ ] **NEW**: Accept recording mode (detect/monitor/schedule) and operate accordingly

**ScheduleRecorderWorker.kt** — **NEW** WorkManager scheduled task:
- [ ] Extends Worker or CoroutineWorker
- [ ] Receives schedule parameters (startTime, endTime, recording mode, sensitivity)
- [ ] Starts RecorderService when schedule time arrives
- [ ] Stops recording when schedule time ends
- [ ] Emits scheduleStarted/scheduleEnded events
- [ ] Handles recurring schedules (daily, weekly)
- [ ] Survives app closure and device reboot

**VADNativeBridge.kt** — JNI bridge to native VAD:
- [ ] Load WebRTC VAD C library (libeverlisten_vad.so)
- [ ] Implement `external` functions:
  - `initVad(sampleRate: Int, aggressiveness: Int): Boolean`
  - `processFrame(bytes: ByteArray): Boolean` — returns true if speech detected
  - `destroyVad(): Boolean`
- [ ] Handle errors gracefully (fallback to RMS-based VAD if native lib unavailable)

**Build integration** (Android):
- [ ] Compile WebRTC VAD C code to .so for target ABIs (armeabi-v7a, arm64-v8a, x86, x86_64)
- [ ] Place in `android/app/src/main/jniLibs/<ABI>/libeverlisten_vad.so`
- [ ] Update `android/app/build.gradle` to reference native library and WorkManager dependency
- [ ] Configure AndroidManifest.xml:
  - [ ] Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
  - [ ] Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` (API 31+)
  - [ ] Add `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />` (API 31+, for precise scheduling)
  - [ ] Declare RecorderService in manifest
  - [ ] Declare ScheduleRecorderWorker in manifest

**Storage management** (Android):
- [ ] Implement file chunking (30–60s per file, or user-configurable)
- [ ] Compress recordings (Opus or AAC) to save space
- [ ] Implement circular buffer: delete oldest files when total exceeds maxStorageMb
- [ ] Thread-safe file operations (background recording may run on separate thread)

**Permissions** (Android runtime):
- [ ] Prompt user for RECORD_AUDIO permission on first start
- [ ] Handle permission denial gracefully (show error, disable recording)
- [ ] Request FOREGROUND_SERVICE permission if API >= 31
- [ ] Request SCHEDULE_EXACT_ALARM permission if API >= 31

**UI/UX** (Flutter):
- [ ] Add Schedule tab/screen to example UI:
  - [ ] Date/time picker (start and end times)
  - [ ] Repeat selector (once, daily, weekly)
  - [ ] Timezone selector
  - [ ] List of active schedules with delete buttons
  - [ ] Show current/next scheduled recording status

### Priority 2 — Core iOS Implementation (After Android is stable)

**RecorderPlugin.swift** — Implement MethodCall handlers:
- [ ] Mirror Android handlers (same method names, similar logic)
- [ ] Set up EventChannel.StreamHandler for events
- [ ] Handle schedule mode via BGTaskScheduler

**Audio capture** (iOS):
- [ ] Configure AVAudioSession (category: .playAndRecord or .record, mode: .default)
- [ ] Set AVAudioSession active and handle interruptions (phone calls, alarms)
- [ ] Use AVAudioEngine to tap inputNode for low-latency frame access
- [ ] Buffer frames (30 ms at 16 kHz) for VAD processing

**VAD integration** (iOS):
- [ ] Compile WebRTC VAD as static lib or framework
- [ ] Provide Swift bridging header to call C functions
- [ ] Process frames and detect speech similar to Android

**Background recording** (iOS):
- [ ] Add Background Modes → Audio in Xcode (Target → Signing & Capabilities)
- [ ] Ensure AVAudioSession is configured to persist across app suspend
- [ ] Show persistent recording indicator (app badge, status bar) per App Store guidelines
- [ ] Test behavior when app is backgrounded, interrupted by phone call, etc.

**Storage management** (iOS):
- [ ] Similar to Android: chunk, compress, enforce storage limit
- [ ] Use FileManager for file operations (thread-safe)
- [ ] Store recordings in app's Documents or tmp directory (respect sandbox)

**Permissions** (iOS):
- [ ] Prompt user for microphone access via system dialog (first launch)
- [ ] Handle permission denial gracefully
- [ ] Ensure privacy policy mentions background recording and scheduling

### Priority 3 — Testing & Validation

**Unit & integration tests**:
- [ ] Test VAD detection on sample audio files (speech, silence, noise)
- [ ] Test pre-roll buffer behavior
- [ ] Test storage limit enforcement
- [ ] Test sensitivity mapping (0..1 → aggressiveness)
- [ ] Test mode transitions (Detect → Monitoring → Schedule, etc.)
- [ ] **NEW**: Test schedule creation, triggers, and cleanup

**Device testing**:
- [ ] Android: test on physical device (or emulator) across API levels 28–34
  - [ ] Verify Detect mode: speech trigger, VAD accuracy, pre-roll
  - [ ] Verify Monitoring mode: continuous recording, foreground notification
  - [ ] **NEW**: Verify Schedule mode: task triggers at correct time, records as configured
  - [ ] Verify background recording persists after app backgrounding
  - [ ] Test battery impact in all modes
  - [ ] Test Doze/battery optimization handling
- [ ] iOS: test on physical device (or simulator) iOS 14+
  - [ ] Mirror Android tests for iOS equivalents

**User experience**:
- [ ] Improve UI (polish sensitivity slider, add VU meter or speech detection indicator)
- [ ] Add settings screen (output format, chunk duration, pre-roll time, etc.)
- [ ] Add file list/playback view
- [ ] Add cloud upload or sync options

### Priority 4 — Production Hardening

**Error handling**:
- [ ] Handle audio permission denial
- [ ] Handle missing microphone gracefully
- [ ] Handle low disk space scenarios
- [ ] Handle native library loading failures
- [ ] Graceful degradation if VAD unavailable
- [ ] **NEW**: Handle failed scheduled task execution, retry logic

**Logging & debugging**:
- [ ] Add structured logging (File + console) for troubleshooting
- [ ] Log VAD detection confidence scores
- [ ] Log storage events (file creation, deletion)
- [ ] **NEW**: Log schedule triggers and executions
- [ ] Expose debug UI to view logs on device

**Platform-specific compliance**:
- [ ] iOS: App Store review — ensure privacy policy, visible recording indicator, justification for background audio
- [ ] Android: Test on multiple OEMs (Samsung, Google, OnePlus, etc.) for Doze/background behavior
- [ ] Android 12+: Verify approximate location access not used (privacy)

**Performance optimization**:
- [ ] Profile CPU usage during VAD processing
- [ ] Optimize frame buffering to reduce memory allocations
- [ ] Test battery impact in all modes
- [ ] Implement power-efficient audio buffering (e.g., use native callbacks, avoid Java allocation loop)

---

## Development Roadmap (Phases) — Current Status

### Phase 1: Verify & Test Android Core Logic (Mock VAD) — Complete
1. Add Android unit tests for `ScheduleStore` JSON persistence and helper utilities.
2. Add Android unit tests for `AudioFrameBuffer` circular pre-roll buffer.
3. Add Android unit tests for `AudioFileWriter` WAV chunk and header formatting.
4. Verify that Android local Kotlin unit tests compile and pass successfully.
5. Verify application fallback to `MockVADProcessor` (RMS-based VAD) functions correctly without crash when JNI WebRTC VAD is missing.

### Phase 2: Integrate WebRTC VAD (Native JNI Integration) — Complete
1. Set up CMake/NDK build infrastructure under `flutter_voice_recorder/android/app/build.gradle.kts` and define JNI exports.
2. Fetch and import WebRTC VAD source C code files into the project.
3. Compile and build `libeverlisten_vad.so` for `arm64-v8a` and `armeabi-v7a`.
4. Connect and verify the JNI entry points via `VADNativeBridge`, including fallback coverage through `VADProcessorTest.kt`.

### Phase 3: Android Background Survival & Optimization — Complete
1. Register a boot receiver (`BootReceiver`) to automatically reschedule exact alarms after device reboot (using `android.intent.action.BOOT_COMPLETED`).
2. Test microphone foreground service permission requirements on Android 11+ (API 30+).
3. Perform optimization profiling on CPU utilization and memory footprint for mock versus WebRTC VAD.

### Phase 4: Android Recording/Scheduling Completion — In Progress
1. ✅ Harden `RecorderService.kt` lifecycle, audio reads, file finalization, and mode validation.
2. ✅ Add Android microphone/notification permission requests and exact-alarm settings handoff.
3. ✅ Validate schedule inputs, replace same-ID alarms, and degrade safely when exact-alarm access is unavailable.
4. ⏸️ Physical-device validation for Detect, Monitoring, and Schedule modes is deferred for this iteration.
5. ✅ Complete local production storage management, permission/error handling, and WakeLock cleanup checks; device/battery validation remains deferred.

### Phase 5: iOS Implementation & Integration (3 weeks)
1. Implement full MethodChannel and EventChannel method stubs in `RecorderPlugin.swift`.
2. Implement audio capture using `AVAudioEngine` and tap input node buffer.
3. Build and integrate WebRTC VAD static library for iOS and link via Bridging Header.
4. Implement background recording modes and schedule management using `BGTaskScheduler`.
5. Run cross-platform integration tests to verify both platforms function correctly.

---

## Architecture & Key Design Decisions

### Why Flutter + native plugins?
- **Single UI codebase** for iOS + Android (code reuse, faster iteration)
- **Native performance** for real-time audio (AudioRecord, AVAudioEngine)
- **Access to native background services** (foreground service on Android, background modes on iOS)
- **Access to scheduling services** (WorkManager on Android, BGTaskScheduler on iOS)
- **Flexibility** to use WebRTC VAD C library compiled per platform
- **Industry standard** for cross-platform mobile apps with native requirements

### Why MethodChannel for plugin API?
- Clean, typed API (methods + events)
- Flutter SDK built-in support (no external dependencies)
- Easy to extend later (e.g., add platform-specific methods)

### Why WebRTC VAD?
- Small, battle-tested C library from Google
- Robust detection (handles noise, accents, speech rates)
- Configurable aggressiveness (0–3) for sensitivity tuning
- Supported sample rates: 8k, 16k, 32k, 48k Hz
- Efficient (no ML model, lightweight)

### Pre-roll buffer design
- Circular buffer in memory (configurable size, default 1–1.5s of audio)
- On speech detection, prepend pre-roll to output file (user hears full speech start)
- Prevents missing the beginning of speech due to VAD latency

### Storage management
- Chunk recordings (e.g., 30–60s per file) for resilience
- Compress with Opus or AAC to reduce footprint
- Enforce max storage via circular buffer (delete oldest files first)
- Configurable via UI (sensitivity, limits)

### Schedule mode using WorkManager (Android) / BGTaskScheduler (iOS)
- **Why WorkManager?** Reliable task scheduling even if app is closed or device reboots. Handles system constraints (Doze mode, battery optimization).
- **Why BGTaskScheduler (iOS)?** Closest iOS equivalent to WorkManager; reliable for background work.
- Supports recurring schedules (daily, weekly) via WorkManager PeriodicWorkRequest
- Clean separation: schedule logic in ScheduleRecorderWorker, recording logic in RecorderService

---

## How to Continue Development Locally

### Step 1: Clone & set up
```bash
git clone git@github.com:SeikoChang/ever-listen.git
cd ever-listen

# Install Flutter dependencies
cd flutter_voice_recorder
flutter pub get

# Verify Flutter environment
flutter doctor
```

### Step 2: Android setup
```bash
# Ensure Android SDK/NDK installed (flutter doctor will tell you)
# Open Android project in Android Studio
cd ../android
# or use VS Code with Flutter extension
```

### Step 3: Complete Android recording integration
- Start in `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/RecorderService.kt`
- Implement AudioRecord frame capture loop
- Test with simple log-to-file stub first

### Step 4: Finish Android plugin and scheduling integration
- Connect `RecorderPlugin.kt` to the service and event channel
- Implement `ScheduleRecorderWorker.kt` and recurring schedule handling
- Validate reboot recovery and foreground-service behavior on device

### Step 5: Complete Android modes and storage behavior
- Refactor RecorderService to support always-on mode
- Set up foreground service

### Step 6: Add and validate Schedule mode
- Add WorkManager to `build.gradle`
- Implement ScheduleRecorderWorker
- Connect to RecorderPlugin

### Step 7: Test on device
```bash
flutter run  # connects to Android device/emulator
# Use example UI to test all three modes
```

### Step 8: Iteratively improve and test
- Tune VAD parameters based on real recordings
- Optimize battery impact
- Stress test schedules
- Move to iOS (Phase 5) only after Android recording and scheduling are stable and tested

---

## Reference Files & Endpoints

### Key implementation files (TODOs marked in code)
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/RecorderPlugin.kt` — MethodChannel methods
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/RecorderService.kt` — foreground service lifecycle & audio capture
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/VADNativeBridge.kt` — JNI bridge
- **NEW**: `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/ScheduleRecorderWorker.kt` — WorkManager task (create this!)
- `flutter_voice_recorder/ios/Runner/RecorderPlugin.swift` — iOS MethodChannel methods
- `flutter_voice_recorder/lib/src/recorder_plugin.dart` — Dart plugin API wrapper
- `flutter_voice_recorder/lib/main.dart` — example UI

### Documentation
- `docs/design.md` — detailed architecture, API (3 modes), platform notes
- `docs/VAD_build_instructions.md` — WebRTC VAD compilation guide
- `tools/prototype/vad_recorder.py` — Python prototype for VAD tuning

### Resources
- [Flutter plugin development guide](https://flutter.dev/docs/development/packages-and-plugins/developing-packages)
- [Android AudioRecord documentation](https://developer.android.com/reference/android/media/AudioRecord)
- [WorkManager library](https://developer.android.com/topic/libraries/architecture/workmanager)
- [iOS AVAudioEngine documentation](https://developer.apple.com/documentation/avfoundation/avaudioengine)
- [iOS BGTaskScheduler documentation](https://developer.apple.com/documentation/backgroundtasks/bgtaskscheduler)
- [WebRTC VAD GitHub](https://github.com/google/webrtc-audio-processing)
- [Kotlin JNI guide](https://kotlinlang.org/docs/native-c-interop.html)

---

## Questions & Known Challenges

### Challenge: VAD accuracy in noisy environments
- WebRTC VAD works well for clean speech but may struggle with background noise (cafe, traffic)
- **Mitigation**: Make aggressiveness tunable; consider ensemble methods (RMS + WebRTC) for noisy cases

### Challenge: iOS App Store background recording approval
- Apple is strict about background audio permissions and requires clear user justification
- **Mitigation**: Clear privacy policy, prominent UI indicator when recording, prominent on-device consent prompt

### Challenge: Android background service in Doze mode
- Android 6+ throttles background services when device is idle (Doze)
- **Mitigation**: Use foreground service (persistent notification keeps app running); ask user to disable battery optimization for app if needed; use WorkManager for schedules (it handles Doze)

### Challenge: Battery drain in monitoring mode
- Continuous recording on a mobile device drains battery quickly
- **Mitigation**: Measure and optimize audio buffering; consider reducing sample rate or frame rate if needed; add UI to show battery impact

### Challenge: WorkManager scheduling precision
- WorkManager is reliable but not always exact-time (respects battery optimization, Doze)
- **Mitigation**: Use SCHEDULE_EXACT_ALARM permission (API 31+) for precise timing; inform users schedules are best-effort

---

## Contact & Support

For questions or clarifications on the design, refer to:
- `docs/design.md` — architecture & API
- `docs/VAD_build_instructions.md` — build setup
- `tools/prototype/vad_recorder.py` — VAD logic reference

For Copilot: use the `get-agent-logs` tool to check any prior work on this repo.
