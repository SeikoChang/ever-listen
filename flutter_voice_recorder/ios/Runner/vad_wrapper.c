/*
 * vad_wrapper.c — Platform-independent C wrapper around WebRTC VAD.
 *
 * This file replaces the Android JNI binding (everlisten_vad.c).
 * It is compiled as part of the iOS Runner target alongside webrtc_vad.c.
 */

#include "vad_wrapper.h"
#include "webrtc_vad.h"

void *vad_init(int aggressiveness) {
    VadInst *handle = NULL;
    if (WebRtcVad_Create(&handle) != 0 || handle == NULL) return NULL;

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
    /* Swift converts hardware input to mono PCM16 at 16 kHz before this call. */
    return WebRtcVad_Process(handle, 16000, frame, frame_length);
}

void vad_destroy(void *handle) {
    if (handle != NULL) {
        WebRtcVad_Free(handle);
    }
}
