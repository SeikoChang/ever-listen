// Stub JNI bridge to native VAD (e.g., WebRTC VAD) and any native encoders (Opus/AAC).
// TODO: implement `external` native methods and compile/link the native library (.so) into Android build

package com.seikochang.ever_listen

object VADNativeBridge {
    init {
        // System.loadLibrary("everlisten_vad")
    }

    // Example native signatures (implement in C/C++):
    // external fun init_vad(sampleRate: Int, aggressiveness: Int)
    // external fun process_frame(bytes: ByteArray): Boolean
    // external fun destroy_vad()
}
