# Ever Listen - VAD build notes

Recommended VAD: WebRTC VAD (small, tested, C).

## Android
- Build WebRTC VAD as an .so for target ABIs (armeabi-v7a, arm64-v8a, x86, x86_64).
- Place .so in `android/app/src/main/jniLibs/<ABI>/libeverlisten_vad.so`
- Provide JNI wrappers in Kotlin to call init/process/destroy.

## iOS
- Build WebRTC VAD as a static library or compile into your plugin target.
- Provide ObjC/Swift bridging layer to call C functions.

## Alternative (quick prototype)
- Implement a lightweight RMS-based VAD in Kotlin/Swift for testing, then replace with WebRTC for production.

## Quick reference
- WebRTC VAD GitHub: https://github.com/google/webrtc-audio-processing
- Supported sample rates: 8000, 16000, 32000, 48000 Hz
- Frame sizes: 80, 160, 320, 480, 960 samples (for 10/20/30 ms frames)
