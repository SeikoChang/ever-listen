# Ever Listen - Project Handoff & Current State

## Executive Summary
This is a Flutter + native plugins skeleton for a cross-platform voice recorder with two modes:
1. **Detect mode**: VAD-based (only record when speech detected)
2. **Monitoring mode**: Always-on background recording

Features include sensitivity tuning, pre-roll buffering, limited storage enforcement, and platform-specific background handling.

**Current status**: Skeleton/scaffolding complete. Next phase: implement native audio capture and VAD integration.

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
  - `docs/design.md` — architecture, API, platform notes
  - `docs/VAD_build_instructions.md` — WebRTC VAD build guide

### ✅ Dart/Flutter Layer
- `lib/main.dart` — example UI with:
  - Mode toggle (Detect vs. Monitoring)
  - Sensitivity slider (0.0–1.0)
  - Max storage input (MB)
  - Start/Stop button
  - Status display
- `lib/src/recorder_plugin.dart` — MethodChannel wrapper API:
  - `startRecording(mode)` — start in detect or monitor mode
  - `stopRecording()` — stop recording
  - `setSensitivity(s)` — set VAD sensitivity
  - `setMaxStorageMb(mb)` — configure storage limit
  - `getStatus()` — query current status
  - `events` stream — receive speechStarted/speechEnded/fileReady events

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

### ✅ License & Meta
- MIT License
- .gitignore configured for Flutter/Dart/Python
- Initial README

---

## What Remains to Be Done

### Priority 1 — Core Native Implementation (Android)

**RecorderPlugin.kt** — Implement MethodCall handlers:
- [ ] `startRecording()`: Start AudioRecord or foreground service, initialize VAD
- [ ] `stopRecording()`: Clean up audio capture, close files, stop service
- [ ] `setSensitivity()`: Pass sensitivity to VAD layer (convert 0..1 to WebRTC aggressiveness)
- [ ] `setMaxStorageMb()`: Configure storage limit
- [ ] `getStatus()`: Return map with recording state, current file, storage used
- [ ] Set up EventChannel.StreamHandler to emit events: `"speechStarted"`, `"speechEnded"`, `"fileReady:/path"`

**RecorderService.kt** — Implement foreground service:
- [ ] Start AudioRecord with PCM 16-bit mono at 16 kHz
- [ ] Frame buffer management (30 ms frames for VAD)
- [ ] Call VAD native bridge on each frame
- [ ] On speech detect: prepare output file, use pre-roll buffer from ring buffer
- [ ] On silence timeout: close current file, emit fileReady event
- [ ] Handle service lifecycle (onStartCommand, onDestroy)
- [ ] Request RECORD_AUDIO permission at runtime (API 30+)

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
- [ ] Update `android/app/build.gradle` to reference native library
- [ ] Configure AndroidManifest.xml:
  - [ ] Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
  - [ ] Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` (API 31+)
  - [ ] Declare RecorderService in manifest

**Storage management** (Android):
- [ ] Implement file chunking (30–60s per file, or user-configurable)
- [ ] Compress recordings (Opus or AAC) to save space
- [ ] Implement circular buffer: delete oldest files when total exceeds maxStorageMb
- [ ] Thread-safe file operations (background recording may run on separate thread)

**Permissions** (Android runtime):
- [ ] Prompt user for RECORD_AUDIO permission on first start
- [ ] Handle permission denial gracefully (show error, disable recording)
- [ ] Request FOREGROUND_SERVICE permission if API >= 31

### Priority 1 — Core Native Implementation (iOS)

**RecorderPlugin.swift** — Implement MethodCall handlers:
- [ ] Mirror Android handlers (same method names, similar logic)
- [ ] Set up EventChannel.StreamHandler for events

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
- [ ] Ensure privacy policy mentions background recording

### Priority 2 — Testing & Validation

**Unit & integration tests**:
- [ ] Test VAD detection on sample audio files (speech, silence, noise)
- [ ] Test pre-roll buffer behavior
- [ ] Test storage limit enforcement
- [ ] Test sensitivity mapping (0..1 → aggressiveness)
- [ ] Test mode transitions (Detect → Monitoring, vice versa)

**Device testing**:
- [ ] Android: test on physical device (or emulator) across API levels 28–34
  - [ ] Verify foreground service notification displays
  - [ ] Verify background recording persists after app backgrounding
  - [ ] Test battery impact in monitoring mode
  - [ ] Test Doze/battery optimization handling
- [ ] iOS: test on physical device (or simulator) iOS 14+
  - [ ] Verify background recording indicator
  - [ ] Test interruption handling (phone call, alarm)
  - [ ] Verify storage enforcement on low-disk scenarios

**User experience**:
- [ ] Improve UI (polish sensitivity slider, add VU meter or speech detection indicator)
- [ ] Add settings screen (output format, chunk duration, pre-roll time, etc.)
- [ ] Add file list/playback view
- [ ] Add cloud upload or sync options

### Priority 3 — Production Hardening

**Error handling**:
- [ ] Handle audio permission denial
- [ ] Handle missing microphone gracefully
- [ ] Handle low disk space scenarios
- [ ] Handle native library loading failures
- [ ] Graceful degradation if VAD unavailable

**Logging & debugging**:
- [ ] Add structured logging (File + console) for troubleshooting
- [ ] Log VAD detection confidence scores
- [ ] Log storage events (file creation, deletion)
- [ ] Expose debug UI to view logs on device

**Platform-specific compliance**:
- [ ] iOS: App Store review — ensure privacy policy, visible recording indicator, justification for background audio
- [ ] Android: Test on multiple OEMs (Samsung, Google, OnePlus, etc.) for Doze/background behavior
- [ ] Android 12+: Verify approximate location access not used (privacy)

**Performance optimization**:
- [ ] Profile CPU usage during VAD processing
- [ ] Optimize frame buffering to reduce memory allocations
- [ ] Test battery impact in both Detect and Monitoring modes
- [ ] Implement power-efficient audio buffering (e.g., use native callbacks, avoid Java allocation loop)

---

## Development Roadmap (Phases)

### Phase 1: Android Detect Mode (2–3 weeks)
1. Implement AudioRecord capture in RecorderService
2. Integrate WebRTC VAD C library (compile for ARM64, ARM)
3. Implement pre-roll buffer and frame processing
4. Test VAD on real device; tune sensitivity thresholds
5. Implement file writing (WAV or Opus compression)
6. Connect RecorderPlugin MethodChannel to RecorderService
7. Run example UI and verify start/stop/setSensitivity work

### Phase 2: Android Monitoring Mode & Storage (1–2 weeks)
1. Refactor RecorderService to support always-on mode
2. Implement foreground service with notification
3. Test background recording after app suspend
4. Implement storage manager (circular buffer, file deletion)
5. Test storage enforcement on device with limited space

### Phase 3: iOS Implementation (3–4 weeks)
1. Mirror Android implementation in Swift
2. Integrate AVAudioEngine + VAD
3. Configure Background Modes and test background recording
4. Implement storage manager for iOS
5. Test on physical device (simulator may not support background audio)

### Phase 4: Testing & Hardening (2 weeks)
1. Run comprehensive device tests (Android + iOS)
2. Stress test: long monitoring sessions, rapid mode switching, low disk space
3. Refine UI based on testing feedback
4. Prepare for App Store / Play Store submission

### Phase 5: Deployment & Polish (1–2 weeks)
1. Create App Store and Play Store listings
2. Handle App Store review feedback (privacy, background recording justification)
3. Publish v1.0

---

## Architecture & Key Design Decisions

### Why Flutter + native plugins?
- **Single UI codebase** for iOS + Android (code reuse, faster iteration)
- **Native performance** for real-time audio (AudioRecord, AVAudioEngine)
- **Access to native background services** (foreground service on Android, background modes on iOS)
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

---

## How to Continue Development Locally

### Step 1: Clone & set up
```bash
git clone git@github.com:SeikoChang/ever-listen.git
cd ever-listen
git checkout feature/init  # (or review & merge the PR first)

# Install Flutter dependencies
cd flutter_voice_recorder
flutter pub get

# Verify Flutter environment
flutter doctor