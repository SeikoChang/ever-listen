# Pinned WebRTC VAD

Vendored unchanged from `wiseman/py-webrtcvad` tag 2.0.10, commit `ba13ceea9dd3f3a068e869b2e94413205bd87f7d`, `cbits/webrtc/`. See source.json for original archive and per-file SHA-256 checksums. No Python extension or Python runtime is required. WebRTC sources use BSD-3-Clause; the upstream combined license is retained in LICENSE.py-webrtcvad. Reproduce those notices in binary distributions.

Android CMake, iOS Runner and host quality tools compile the same 19 C translation units, with WEBRTC_POSIX and generic C signal processing. Avoid platform-specific hardcoded NDK include paths. The pinned version uses `int WebRtcVad_Create(VadInst **handle)` (not the newer pointer-returning API). Local JNI/iOS wrappers adapt that API and validate initialization. Run tools/quality/vad_baseline.py preflight after edits.
