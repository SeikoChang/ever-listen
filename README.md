# README.md

# Ever Listen (flutter-voice-recorder)

A Flutter skeleton app and native plugin stubs for an always-on / VAD-based voice recorder.

This repository contains a minimal Flutter app (flutter_voice_recorder) with a MethodChannel-based native plugin API and native stubs for Android (Kotlin) and iOS (Swift). It also includes a Python prototype for tuning VAD parameters.

Quick start
- Install Flutter: https://flutter.dev/docs/get-started/install
- From the repo root run:
  - cd flutter_voice_recorder
  - flutter pub get
  - flutter run

Important notes
- Native audio capture, background recording and VAD are implemented as platform-native code. The provided native files are skeletons/stubs with guidance and TODOs — you must implement or link an actual VAD library (e.g., WebRTC VAD) and encoding pipeline for production.
- Android: foreground service sample provided as a stub. You must request RECORD_AUDIO and FOREGROUND_SERVICE permissions and configure the AndroidManifest accordingly.
- iOS: AVAudioEngine tap stub provided. Add Background Modes -> Audio in Xcode for background recording.

License: MIT
