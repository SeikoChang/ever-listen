#include <jni.h>
#include <stdint.h>
#include <android/log.h>
#include "webrtc_vad.h"

#define TAG "everlisten_vad_jni"

JNIEXPORT jlong JNICALL
Java_com_seikochang_ever_1listen_VADNativeBridge_initVad(JNIEnv *env, jobject thiz, jint sample_rate, jint aggressiveness) {
    VadInst* handle = NULL;
    if (sample_rate != 16000 || WebRtcVad_Create(&handle) != 0 || handle == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to create VAD instance");
        return 0;
    }

    if (WebRtcVad_Init(handle) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to initialize VAD instance");
        WebRtcVad_Free(handle);
        return 0;
    }

    // Set mode: 0 (quality), 1 (low bitrate), 2 (aggressive), 3 (very aggressive)
    // This maps to the aggressiveness parameter.
    if (WebRtcVad_set_mode(handle, aggressiveness) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to set VAD mode to %d", aggressiveness);
        WebRtcVad_Free(handle);
        return 0;
    }
    
    // WebRTC VAD only supports 8000, 16000, 32000, 48000 Hz sample rates.
    // The C code currently uses 16000 directly, which matches the KOTLIN code.
    // If the sample rate was dynamic, we would need to set it here.
    // WebRtcVad_set_sample_rate(handle, sample_rate); // Not needed as it's fixed in C.

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "VAD instance created and initialized with mode %d", aggressiveness);
    return (jlong)(intptr_t)handle;
}

JNIEXPORT jboolean JNICALL
Java_com_seikochang_ever_1listen_VADNativeBridge_processFrame(JNIEnv *env, jobject thiz, jlong vad_handle, jbyteArray frame_bytes) {
    VadInst* handle = (VadInst*)(intptr_t)vad_handle;
    if (handle == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "VAD handle is null in processFrame");
        return JNI_FALSE;
    }

    if (frame_bytes == NULL) return JNI_FALSE;
    jsize frame_size_bytes = (*env)->GetArrayLength(env, frame_bytes);
    if (frame_size_bytes != 320 && frame_size_bytes != 640 && frame_size_bytes != 960) return JNI_FALSE;
    jbyte* pcm_data = (*env)->GetByteArrayElements(env, frame_bytes, NULL);
    if (pcm_data == NULL) return JNI_FALSE;
    
    // WebRTC VAD expects frames in 10, 20, or 30 ms lengths.
    // At 16000 Hz, 30ms frame is 480 samples. 16-bit PCM = 960 bytes.
    // The Kotlin code uses 480 samples, which is 960 bytes.
    // So the frame size in samples is frame_size_bytes / 2.
    jint frame_size_samples = frame_size_bytes / 2;

    // Decode little-endian PCM explicitly; a jbyte array need not be int16-aligned.
    int16_t samples[480];
    for (jint index = 0; index < frame_size_samples; ++index) {
        uint16_t value = (uint8_t)pcm_data[index * 2] |
            ((uint16_t)(uint8_t)pcm_data[index * 2 + 1] << 8);
        samples[index] = (int16_t)value;
    }
    (*env)->ReleaseByteArrayElements(env, frame_bytes, pcm_data, JNI_ABORT);
    int result = WebRtcVad_Process(handle, 16000, samples, frame_size_samples);

    if (result == -1) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "WebRtcVad_Process failed");
        return JNI_FALSE;
    }

    return (jboolean)(result == 1); // 1 for speech, 0 for noise
}

JNIEXPORT jboolean JNICALL
Java_com_seikochang_ever_1listen_VADNativeBridge_destroyVad(JNIEnv *env, jobject thiz, jlong vad_handle) {
    VadInst* handle = (VadInst*)(intptr_t)vad_handle;
    if (handle != NULL) {
        WebRtcVad_Free(handle);
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "VAD instance destroyed");
        return JNI_TRUE;
    }
    __android_log_print(ANDROID_LOG_WARN, TAG, "VAD handle was null during destroy");
    return JNI_FALSE;
}
