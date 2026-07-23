#ifndef VAD_WRAPPER_H
#define VAD_WRAPPER_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize a WebRTC VAD instance.
 *
 * @param aggressiveness  0 (most permissive) – 3 (most strict)
 *                        0 = Normal
 *                        1 = Low bitrate
 *                        2 = Aggressive
 *                        3 = Very aggressive
 * @return Opaque handle, or NULL on failure.
 */
void *vad_init(int aggressiveness);

/**
 * Process one frame of 16-bit PCM audio.
 *
 * @param handle       Opaque VAD handle from vad_init().
 * @param frame        Pointer to 16-bit PCM samples.
 * @param frame_length Number of samples (not bytes).
 *                     Recommended: 80, 160, 240, 320, 480, 640
 *                     (10 ms – 30 ms at 8 kHz – 48 kHz).
 * @return  1  – Speech detected
 *          0  – Non-speech (noise / silence)
 *         -1  – Error
 */
int vad_process(void *handle, const int16_t *frame, size_t frame_length);

/**
 * Free the VAD instance.
 */
void vad_destroy(void *handle);

#ifdef __cplusplus
}
#endif

#endif /* VAD_WRAPPER_H */
