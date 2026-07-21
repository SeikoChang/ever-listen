# TODOs

## Completed Preparation Tasks
- [x] Register `RecorderPlugin` in `MainActivity.kt` <!-- id: 1 -->
- [x] Add unit tests for `RecorderPlugin` in Flutter/Dart layer <!-- id: 2 -->
- [x] Run unit tests to verify the project and plugin stubs <!-- id: 3 -->

## Phase 1: Verify & Test Android Core Logic (Mock VAD)
- [x] Add Android unit tests for `ScheduleStore` JSON persistence <!-- id: 4 -->
- [x] Add Android unit tests for `AudioFrameBuffer` circular buffer logic <!-- id: 5 -->
- [x] Add Android unit tests for `AudioFileWriter` WAV formatting <!-- id: 6 -->
- [x] Configure `app/build.gradle.kts` for Android unit testing (`test` target) <!-- id: 7 -->
- [x] Execute `./gradlew test` in Android project and verify all Kotlin tests pass <!-- id: 8 -->

## Phase 2: Integrate WebRTC VAD (Native JNI Integration)
- [x] Configure CMake or NDK build settings in Android module <!-- id: 9 -->
- [x] Download/extract WebRTC VAD C source code into `android/app/src/main/cpp` <!-- id: 10 -->
- [x] Implement JNI binding implementation `everlisten_vad.c` <!-- id: 11 -->
- [x] Compile and generate `libeverlisten_vad.so` for arm64-v8a and armeabi-v7a <!-- id: 12 -->
- [x] Verify `VADNativeBridge` functions correctly with NDK compiled library <!-- id: 13 -->
  - [x] Add `VADProcessorTest.kt` unit test to verify `MockVADProcessor` and native loading fallback <!-- id: 22 -->

## Phase 3: Android Background Survival & Optimization
- [x] Create `BootReceiver.kt` to reschedule Alarms on device reboot <!-- id: 14 -->
- [x] Declare `BootReceiver` and `RECEIVE_BOOT_COMPLETED` permission in `AndroidManifest.xml` <!-- id: 15 -->
- [x] Verify microphone foreground service type compatibility on API 30+ <!-- id: 16 -->
- [x] Profile memory usage and CPU footprint of `MockVADProcessor` vs `WebRTCVADProcessor` <!-- id: 17 -->

## Phase 4: Android Recording/Scheduling Completion
- [x] Harden `RecorderService` lifecycle, frame reads, file finalization, and mode validation <!-- id: 23 -->
- [x] Add Android microphone/notification permission request bridge <!-- id: 24 -->
- [x] Validate recording mode, sensitivity, storage, schedule repeat values, and future schedule times <!-- id: 25 -->
- [x] Handle exact-alarm permission and provide a settings handoff on Android 12+ <!-- id: 26 -->
- [x] Replace existing schedule alarms when updating a schedule with the same ID <!-- id: 27 -->
- [x] Fall back to inexact alarms during boot recovery when exact-alarm access is unavailable <!-- id: 28 -->
- [x] Add failure-path tests for invalid pre-roll capacity and schedule replacement/weekly recurrence <!-- id: 31 -->
- [x] Extract and test recording storage pruning; add WakeLock acquire/release cleanup <!-- id: 32 -->
- [x] Run the complete Android unit-test suite successfully with Android Studio's JDK <!-- id: 33 -->
- [ ] Complete physical-device validation for Detect, Monitoring, and Schedule modes *(deferred for this iteration)* <!-- id: 29 -->
- [ ] Add production storage, battery/background survival, and failure-path validation <!-- id: 30 -->

## Phase 5: iOS Implementation & Integration
- [x] Implement MethodChannel and EventChannel parity in `RecorderPlugin.swift` <!-- id: 18 -->
- [x] Implement initial Swift `AVAudioEngine` record/tap and file output path <!-- id: 19 -->
- [ ] Build and link WebRTC VAD static library for iOS build <!-- id: 20 -->
- [x] Implement initial `BGTaskScheduler` dispatcher for persisted schedules <!-- id: 21 -->

## P0: Critical Bugs
- [ ] Fix iOS `RecorderPlugin.swift` line 464 string interpolation bug — filename produces literal `(mode)_(Int(...)).caf` instead of interpolated values, causing all recordings to overwrite each other <!-- id: 34 -->

## Phase 6: Device Validation (Both Platforms)
- [ ] Android physical-device validation: Detect, Monitoring, Schedule modes across API 28-34 <!-- id: 35 -->
- [ ] Android battery/Doze/OEM testing (Samsung, Google, OnePlus) <!-- id: 36 -->
- [ ] iOS physical-device validation: Detect, Monitoring, Schedule modes (iOS 14+) <!-- id: 37 -->
- [ ] iOS BGTaskScheduler dispatch reliability testing <!-- id: 38 -->
- [ ] iOS AVAudioSession interruption handling (phone calls, alarms) <!-- id: 39 -->

## Phase 7: Production Hardening
- [ ] Cross-platform integration tests for feature parity <!-- id: 40 -->
- [ ] Add structured logging (File + console) on both platforms <!-- id: 41 -->
- [ ] Handle edge cases: missing microphone, low disk space, native lib failures <!-- id: 42 -->
- [ ] iOS App Store compliance: privacy policy, recording indicator, background audio justification <!-- id: 43 -->
- [ ] UI polish: VU meter, file list/playback, settings screen <!-- id: 44 -->
