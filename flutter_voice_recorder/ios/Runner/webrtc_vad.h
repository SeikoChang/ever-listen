/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * WebRTC Voice Activity Detector.
 * Functionalities:
 * - Aggressiveness mode.
 * - Frame length.
 * - Sample rate.
 */

#ifndef WEBRTC_VAD_WEBRTC_VAD_H_
#define WEBRTC_VAD_WEBRTC_VAD_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Contains the valid framelengths for WebRtcVad_Process().
// 10, 20 or 30 ms.
enum {
  kFrameLength10Ms = 10,
  kFrameLength20Ms = 20,
  kFrameLength30Ms = 30
};

// Contains the valid sample rates for WebRtcVad_Process().
// 8000, 16000, 32000 or 48000 Hz.
enum {
  kSampleRate8kHz = 8000,
  kSampleRate16kHz = 16000,
  kSampleRate32kHz = 32000,
  kSampleRate48kHz = 48000
};

// WebRtcVad_Create()
// Creates a VAD instance, which also allocates the memory needed by the
// instance.
//
// - Returns: A pointer to the VAD instance, or NULL on failure.
void* WebRtcVad_Create(void);

// WebRtcVad_Free()
// Frees the memory allocated by WebRtcVad_Create().
//
// - vad_instance: [IN/OUT] The VAD instance handle.
void WebRtcVad_Free(void* vad_instance);

// WebRtcVad_Init()
// Initializes a VAD instance.
//
// - vad_instance: [IN/OUT] The VAD instance handle.
//
// - Returns: 0 on success, or -1 on failure.
int WebRtcVad_Init(void* vad_instance);

// WebRtcVad_set_mode()
// Sets the VAD operating mode. A more aggressive mode means that VAD is more
// likely to report speech when non-speech is present, but also less likely
// to miss actual speech.
//
// - vad_instance: [IN/OUT] The VAD instance handle.
// - mode        : [IN] Aggressiveness mode (0, 1, 2, or 3).
//                 0 - Normal
//                 1 - Low bitrate
//                 2 - Aggressive
//                 3 - Very aggressive
//
// - Returns: 0 on success, or -1 on failure.
int WebRtcVad_set_mode(void* vad_instance, int mode);

// WebRtcVad_Process()
// Calculates a VAD decision for the |audio_frame|.
//
// - vad_instance: [IN/OUT] The VAD instance handle.
// - sample_rate : [IN] The sample rate of the audio frame in Hz.
//                       Valid values are 8000, 16000, 32000 or 48000.
// - frame_length: [IN] The length of the audio frame in samples.
//                       Valid values are 80, 160, 240, 320, 480 or 640.
//                       NOTE: Our algorithms are designed to operate on
//                       10 ms frames, so a frame_length corresponding to
//                       10, 20 or 30 ms is recommended.
// - audio_frame : [IN] The audio frame.
//
// - Returns: 1 - Speech
//            0 - Non-speech
//           -1 - Error
int WebRtcVad_Process(void* vad_instance, int sample_rate,
                      const int16_t* audio_frame, size_t frame_length);

#ifdef __cplusplus
}
#endif

#endif  // WEBRTC_VAD_WEBRTC_VAD_H_
