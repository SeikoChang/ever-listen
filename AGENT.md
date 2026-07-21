# Ever Listen - Project Handoff & Current State

## Executive Summary
This is a Flutter + native plugins skeleton for a cross-platform voice recorder with three modes:
1. **Detect mode**: VAD-based (only record when speech detected)
2. **Monitoring mode**: Always-on background recording
3. **Schedule mode**: Record during user-specified time slots (one-time, daily, weekly)

Features include sensitivity tuning, pre-roll buffering, limited storage enforcement, scheduled recording, and platform-specific background handling.

**Current status**: **Android is complete through Phase 4** (all core logic, JNI, background survival, hardening done; device validation deferred). **Phase 5 iOS is in progress:** MethodChannel/EventChannel parity, AVAudioEngine capture, permission handling, schedule CRUD, and BGTaskScheduler dispatcher are implemented; iOS WebRTC VAD linking and full device validation remain. **Next: fix iOS string interpolation bug, link WebRTC VAD for iOS, then device validation on both platforms.**

---

## What Has Been Done

### ✅ Project Structure
- Flutter app skeleton at `flutter_voice_recorder/`
- Android (Kotlin) plugin with full implementation:
  - `RecorderPlugin.kt` (303 lines) — MethodChannel entry point with all 10 methods
  - `RecorderService.kt` (482 lines) — foreground service for background recording
  - `VADProcessor.kt` (118 lines) — Interface + MockVADProcessor (RMS) + WebRTCVADProcessor
  - `VADNativeBridge.kt` (22 lines) — JNI bridge with UnsatisfiedLinkError fallback
  - `AudioFrameBuffer.kt` (36 lines) — Circular pre-roll buffer
  - `AudioFileWriter.kt` (114 lines) — WAV file writer with RIFF header
  - `RecordingStorage.kt` (42 lines) — File pruning, storage enforcement
  - `ScheduleStore.kt` (159 lines) — Schedule persistence via SharedPreferences + AlarmManager
  - `ScheduleRecorderReceiver.kt` (44 lines) — BroadcastReceiver for AlarmManager triggers
  - `BootReceiver.kt` (49 lines) — Reschedules alarms on BOOT_COMPLETED
- iOS (Swift) plugin with substantial implementation:
  - `RecorderPlugin.swift` (515 lines) — MethodChannel + EventChannel parity, AVAudioEngine recorder, schedule CRUD, BGTaskScheduler
- Documentation:
  - `docs/design.md` — architecture, API (3 modes), platform notes
  - `docs/VAD_build_instructions.md` — WebRTC VAD build guide
  - `docs/manual_testing_zh.md` — Chinese manual testing guide

### ✅ Dart/Flutter Layer
- `lib/main.dart` (303 lines) — Functional UI with:
  - Mode toggle (Detect vs. Monitoring vs. Schedule)
  - Sensitivity slider (0.0–1.0)
  - Max storage input (MB)
  - Schedule time pickers, repeat dropdown, schedule list with delete
  - Start/Stop button and status display
- `lib/src/recorder_plugin.dart` (81 lines) — MethodChannel wrapper API:
  - `startRecording(mode)`, `stopRecording()`, `requestPermissions()`
  - `setSensitivity(s)`, `setMaxStorageMb(mb)`, `getStatus()`
  - `scheduleRecording(startTime, endTime, repeat, timezone)`, `cancelSchedule()`, `getSchedules()`
  - `events` stream — speechStarted/speechEnded/fileReady/scheduleStarted/scheduleEnded

### ✅ Python Prototype
- `tools/prototype/vad_recorder.py` — ready-to-run VAD detector using WebRTC VAD:
  - Detects speech with configurable sensitivity (0..1)
  - Pre-roll buffer (1s default) to capture audio before trigger
  - Minimum speech duration and silence duration thresholds
  - Automatic storage limit enforcement (deletes oldest files)
  - Outputs 16-bit PCM WAV files

### ✅ CI/CD
- GitHub Actions workflow (`.github/workflows/flutter-ci.yml`) runs `flutter analyze` on PRs

### ✅ Android Foundation (Complete through Phase 4)
- Android unit tests (6 test files): `AudioFileWriterTest`, `AudioFrameBufferTest`, `BootReceiverTest`, `RecordingStorageTest`, `ScheduleStoreTest`, `VADProcessorTest`
- WebRTC VAD C sources and JNI bindings integrated and built for `arm64-v8a` and `armeabi-v7a`
- `VADNativeBridge.kt` loads native lib with graceful fallback to `MockVADProcessor`
- `BootReceiver.kt` reschedules alarms after device reboot; cleans expired one-time schedules
- Foreground-service microphone compatibility on API 30+ verified
- CPU/memory profiling for mock vs. WebRTC VAD complete
- Storage management: WAV output, circular pre-roll buffer, file pruning by maxStorageMb
- Scheduling via AlarmManager + BroadcastReceiver (exact-alarm with inexact fallback)
- Permissions: RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, WAKE_LOCK

### ✅ License & Meta
- MIT License
- .gitignore configured for Flutter/Dart/Python
- Initial README

---

## What Remains To Be Done

### Priority 1 — iOS Bug Fix & WebRTC VAD Linking

**iOS RecorderPlugin.swift Bug** (P0):
- [ ] Fix string interpolation bug at line 464: filename uses literal `(mode)_(Int(...)).caf` instead of `"\(mode)_\(Int(...)).caf"` — all recordings overwrite each other

**WebRTC VAD for iOS** (TODO #20):
- [ ] Compile WebRTC VAD C code as static library (.a) or framework for iOS
- [ ] Create Swift bridging header to expose C functions (`initVad`, `processFrame`, `destroyVad`)
- [ ] Integrate into `RecorderPlugin.swift` to replace/augment RMS-based VAD
- [ ] Verify native VAD loading on iOS simulator and physical device

### Priority 2 — Device Validation

**Android Device Testing** (deferred from Phase 4):
- [ ] Physical-device validation for Detect, Monitoring, and Schedule modes across API 28-34
- [ ] Verify background recording persists after app backgrounding
- [ ] Test battery impact in all modes
- [ ] Test Doze/battery optimization handling on various OEMs (Samsung, Google, OnePlus)
- [ ] Verify schedule triggers at correct times on physical device

**iOS Device Testing**:
- [ ] Test on physical device (iOS 14+) — Detect, Monitoring, Schedule modes
- [ ] Verify background recording with AVAudioEngine when app is backgrounded
- [ ] Test BGTaskScheduler dispatch reliability
- [ ] Verify schedule persistence across app kills and device reboots
- [ ] Test AVAudioSession interruption handling (phone calls, alarms)

### Priority 3 — Production Hardening

**Error handling**:
- [ ] Handle audio permission denial gracefully on both platforms
- [ ] Handle missing microphone / low disk space scenarios
- [ ] Handle native library loading failures (iOS fallback path)
- [ ] Handle failed scheduled task execution, retry logic
- [ ] Graceful degradation if VAD unavailable

**Logging & debugging**:
- [ ] Add structured logging (File + console) for troubleshooting
- [ ] Log VAD detection confidence scores
- [ ] Log storage events (file creation, deletion)
- [ ] Log schedule triggers and executions
- [ ] Expose debug UI to view logs on device

**Platform-specific compliance**:
- [ ] iOS: App Store review — privacy policy, visible recording indicator, justification for background audio
- [ ] Android: Test on multiple OEMs for Doze/background behavior
- [ ] Android 12+: Verify approximate location access not used (privacy)

**Performance optimization**:
- [ ] Profile CPU usage during VAD processing on device
- [ ] Optimize frame buffering to reduce memory allocations
- [ ] Test battery impact in all modes
- [ ] Implement power-efficient audio buffering (native callbacks, avoid allocation loop)

### Priority 4 — UI/UX Polish

**UI improvements**:
- [ ] Add VU meter or speech detection indicator
- [ ] Add settings screen (output format, chunk duration, pre-roll time, etc.)
- [ ] Add file list/playback view
- [ ] Add cloud upload or sync options
- [ ] Improve overall UI polish

### Priority 5 — Testing

**Unit & integration tests**:
- [ ] Test VAD detection on sample audio files (speech, silence, noise)
- [ ] Test pre-roll buffer behavior
- [ ] Test storage limit enforcement
- [ ] Test sensitivity mapping (0..1 → aggressiveness)
- [ ] Test mode transitions (Detect → Monitoring → Schedule)
- [ ] Test schedule creation, triggers, and cleanup

**Cross-platform integration tests**:
- [ ] Run integration tests on both platforms to verify feature parity
- [ ] Verify EventChannel events fire correctly on both platforms

---

## Development Roadmap (Phases) — Current Status

### Phase 1: Verify & Test Android Core Logic (Mock VAD) — ✅ Complete
1. ✅ Add Android unit tests for `ScheduleStore` JSON persistence and helper utilities.
2. ✅ Add Android unit tests for `AudioFrameBuffer` circular pre-roll buffer.
3. ✅ Add Android unit tests for `AudioFileWriter` WAV chunk and header formatting.
4. ✅ Verify that Android local Kotlin unit tests compile and pass successfully.
5. ✅ Verify application fallback to `MockVADProcessor` (RMS-based VAD) functions correctly without crash when JNI WebRTC VAD is missing.

### Phase 2: Integrate WebRTC VAD (Native JNI Integration) — ✅ Complete
1. ✅ Set up CMake/NDK build infrastructure under `flutter_voice_recorder/android/app/build.gradle.kts` and define JNI exports.
2. ✅ Fetch and import WebRTC VAD source C code files into the project.
3. ✅ Compile and build `libeverlisten_vad.so` for `arm64-v8a` and `armeabi-v7a`.
4. ✅ Connect and verify the JNI entry points via `VADNativeBridge`, including fallback coverage through `VADProcessorTest.kt`.

### Phase 3: Android Background Survival & Optimization — ✅ Complete
1. ✅ Register a boot receiver (`BootReceiver`) to automatically reschedule exact alarms after device reboot (using `android.intent.action.BOOT_COMPLETED`).
2. ✅ Test microphone foreground service permission requirements on Android 11+ (API 30+).
3. ✅ Perform optimization profiling on CPU utilization and memory footprint for mock versus WebRTC VAD.

### Phase 4: Android Recording/Scheduling Completion — ✅ Complete
1. ✅ Harden `RecorderService.kt` lifecycle, audio reads, file finalization, and mode validation.
2. ✅ Add Android microphone/notification permission requests and exact-alarm settings handoff.
3. ✅ Validate schedule inputs, replace same-ID alarms, and degrade safely when exact-alarm access is unavailable.
4. ⏸️ Physical-device validation for Detect, Monitoring, and Schedule modes is deferred for this iteration.
5. ✅ Complete local production storage management, permission/error handling, and WakeLock cleanup checks; device/battery validation remains deferred.

### Phase 5: iOS Implementation & Integration — 🔄 In Progress
1. ✅ Implement full MethodChannel and EventChannel method stubs in `RecorderPlugin.swift`.
2. ✅ Implement audio capture using `AVAudioEngine` and tap input node buffer.
3. 🔴 **TODO**: Build and integrate WebRTC VAD static library for iOS and link via Bridging Header.
4. ✅ Implement initial `BGTaskScheduler` dispatcher for persisted schedules.
5. ⏸️ Cross-platform integration tests deferred until iOS VAD and device validation are complete.

---

## Architecture & Key Design Decisions

### Why Flutter + native plugins?
- **Single UI codebase** for iOS + Android (code reuse, faster iteration)
- **Native performance** for real-time audio (AudioRecord, AVAudioEngine)
- **Access to native background services** (foreground service on Android, background modes on iOS)
- **Access to scheduling services** (AlarmManager on Android, BGTaskScheduler on iOS)
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
- Compress with Opus or AAC to reduce footprint (future)
- Enforce max storage via circular buffer (delete oldest files first)
- Configurable via UI (sensitivity, limits)

### Schedule mode using AlarmManager (Android) / BGTaskScheduler (iOS)
- **Why AlarmManager?** Exact-time scheduling with BroadcastReceiver; simpler than WorkManager for precise triggers. Uses `SCHEDULE_EXACT_ALARM` permission on API 31+ with inexact-alarm fallback.
- **Why BGTaskScheduler (iOS)?** Closest iOS equivalent for reliable background task scheduling.
- Supports recurring schedules (daily, weekly) via AlarmManager rescheduling / BGTaskScheduler re-registration
- Clean separation: scheduling logic in receivers/stores, recording logic in services/recorders

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

### Step 2: Fix iOS bug (P0)
- Fix the string interpolation bug in `ios/Runner/RecorderPlugin.swift` line 464

### Step 3: Build WebRTC VAD for iOS
- Follow `docs/VAD_build_instructions.md` for iOS static library build
- Link via bridging header in `ios/Runner/Runner-Bridging-Header.h`

### Step 4: Android device testing
```bash
cd android
./gradlew test  # verify unit tests pass
cd ..
flutter run  # connect Android device
# Test Detect, Monitoring, and Schedule modes
```

### Step 5: iOS device testing
```bash
open ios/Runner.xcworkspace  # Open in Xcode
# Configure signing, select device, run
# Test Detect, Monitoring, and Schedule modes
```

### Step 6: Polish and productionize
- Tune VAD parameters based on real recordings
- Optimize battery impact
- Add UI polish (VU meter, file list, settings)
- Stress test schedules
- Prepare for App Store / Play Store submission

---

## Reference Files & Endpoints

### Key implementation files
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/RecorderPlugin.kt` — MethodChannel methods (303 lines)
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/RecorderService.kt` — Foreground service lifecycle & audio capture (482 lines)
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/VADNativeBridge.kt` — JNI bridge with fallback (22 lines)
- `flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/ScheduleStore.kt` — Schedule persistence + AlarmManager (159 lines)
- `flutter_voice_recorder/android/app/src/main/cpp/` — WebRTC VAD C sources and JNI bindings
- `flutter_voice_recorder/ios/Runner/RecorderPlugin.swift` — iOS MethodChannel + EventChannel + AVAudioEngine recorder (515 lines)
- `flutter_voice_recorder/lib/src/recorder_plugin.dart` — Dart plugin API wrapper (81 lines)
- `flutter_voice_recorder/lib/main.dart` — Example UI (303 lines)

### Documentation
- `docs/design.md` — Detailed architecture, API (3 modes), platform notes
- `docs/VAD_build_instructions.md` — WebRTC VAD compilation guide
- `docs/manual_testing_zh.md` — Chinese manual testing guide
- `tools/prototype/vad_recorder.py` — Python VAD logic reference

### Resources
- [Flutter plugin development guide](https://flutter.dev/docs/development/packages-and-plugins/developing-packages)
- [Android AudioRecord documentation](https://developer.android.com/reference/android/media/AudioRecord)
- [Android AlarmManager documentation](https://developer.android.com/reference/android/app/AlarmManager)
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
- **Mitigation**: Use foreground service (persistent notification keeps app running); ask user to disable battery optimization for app if needed; use AlarmManager for schedules (handles Doze)

### Challenge: Battery drain in monitoring mode
- Continuous recording on a mobile device drains battery quickly
- **Mitigation**: Measure and optimize audio buffering; consider reducing sample rate or frame rate if needed; add UI to show battery impact

### Challenge: Schedule precision
- AlarmManager exact-alarm requires user-granted permission on API 31+
- **Mitigation**: Use `SCHEDULE_EXACT_ALARM` permission with settings handoff; fallback to inexact alarms; inform users schedules are best-effort

### Known Issue: iOS string interpolation bug
- `RecorderPlugin.swift` line 464: filename string uses literal `(mode)_(Int(...)).caf` instead of `"\(mode)_\(Int(...)).caf"`
- All recordings produce the same filename, causing overwrites
- **Fix**: Add `\` escapes for interpolation: `let name = "\(mode)_\(Int(Date().timeIntervalSince1970 * 1000)).caf"`

---

## Contact & Support

For questions or clarifications on the design, refer to:
- `docs/design.md` — architecture & API
- `docs/VAD_build_instructions.md` — build setup
- `tools/prototype/vad_recorder.py` — VAD logic reference
