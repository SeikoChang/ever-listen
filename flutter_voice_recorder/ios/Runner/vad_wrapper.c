/*
 * vad_wrapper.c — Platform-independent C wrapper around WebRTC VAD.
 *
 * This file replaces the Android JNI binding (everlisten_vad.c).
 * It is compiled as part of the iOS Runner target alongside webrtc_vad.c.
 */

#include "vad_wrapper.h"
#include "webrtc_vad.h"

void *vad_init(int aggressiveness) {
    void *handle = WebRtcVad_Create();
    if (handle == NULL) return NULL;

    if (WebRtcVad_Init(handle) < 0) {
        WebRtcVad_Free(handle);
        return NULL;
    }

    if (WebRtcVad_set_mode(handle, aggressiveness) < 0) {
        WebRtcVad_Free(handle);
        return NULL;
    }

    return handle;
}

int vad_process(void *handle, const int16_t *frame, size_t frame_length) {
    if (handle == NULL || frame == NULL) return -1;
    /*
     * WebRTC VAD supports 8000, 16000, 32000, 48000 Hz.
     * AVAudioEngine input node typically provides 44100 or 48000 Hz.
     * We pass 16000 here because the original Android code uses 16000.
     *
     * If your AVAudioEngine format sample rate differs, you may need to
     * resample before calling this, or change the sample_rate parameter.
     */
    return WebRtcVad_Process(handle, 16000, frame, frame_length);
}

void vad_destroy(void *handle) {
    if (handle != NULL) {
        WebRtcVad_Free(handle);
    }
}
