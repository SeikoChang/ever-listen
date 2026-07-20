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

## Phase 4: iOS Implementation & Integration
- [ ] Implement MethodChannel and EventChannel stubs in `RecorderPlugin.swift` <!-- id: 18 -->
- [ ] Implement Swift `AVAudioEngine` record and tap functionality <!-- id: 19 -->
- [ ] Build and link WebRTC VAD static library for iOS build <!-- id: 20 -->
- [ ] Implement `BGTaskScheduler` interface in iOS plugin to support排程錄音 <!-- id: 21 -->
