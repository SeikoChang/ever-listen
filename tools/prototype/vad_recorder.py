#!/usr/bin/env python3
"""
Simple VAD-based recorder prototype.

Features:
- Detects speech with webrtcvad
- Pre-roll buffer so recordings include audio before trigger
- Sensitivity mapping (user_sensitivity 0..1 -> aggressiveness 0..3)
- Limited storage: keep only recent `max_storage_mb`
- Output WAV files (16-bit PCM mono, 16kHz)

Usage:
  pip install webrtcvad sounddevice soundfile numpy
  python vad_recorder.py --sensitivity 0.6 --max-storage-mb 200
"""

import collections
import os
import queue
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import sounddevice as sd
import soundfile as sf
import webrtcvad

# CONFIG (tune these)
SAMPLE_RATE = 16000           # WebRTC VAD supported rates: 8000, 16000, 32000, 48000
FRAME_MS = 30                 # frame length in ms (10/20/30)
FRAME_SIZE = int(SAMPLE_RATE * FRAME_MS / 1000)  # samples per frame
CHANNELS = 1
FORMAT = 'int16'
PRE_ROLL_SEC = 1.0            # seconds to keep before trigger
MIN_SPEECH_SEC = 0.3          # require at least this much speech to start saving
MIN_SILENCE_AFTER_SPEECH = 1.0 # stop when silence this long
CHUNK_SECONDS = 30            # maximum file length in seconds
OUTPUT_DIR = Path("recordings")
MAX_STORAGE_MB = 200          # delete oldest when over this limit

# Map a user-friendly sensitivity (0..1) to WebRTC aggressiveness 3..0
def sensitivity_to_aggressiveness(s):
    s = min(max(float(s), 0.0), 1.0)
    # user_sensitivity 1.0 -> most sensitive (aggressiveness 0)
    # user_sensitivity 0.0 -> least sensitive (aggressiveness 3)
    return int(round((1.0 - s) * 3.0))

class VADRecorder:
    def __init__(self, sample_rate=SAMPLE_RATE, frame_ms=FRAME_MS, sensitivity=0.6):
        self.sample_rate = sample_rate
        self.frame_ms = frame_ms
        self.frame_size = int(sample_rate * frame_ms / 1000)
        self.vad = webrtcvad.Vad(sensitivity_to_aggressiveness(sensitivity))
        self.q = queue.Queue()
        self.recording = False
        self.stop_flag = threading.Event()
        self.output_dir = OUTPUT_DIR
        self.output_dir.mkdir(exist_ok=True)
        self.pre_roll_frames = int(PRE_ROLL_SEC * 1000 / frame_ms)
        self.pre_roll = collections.deque(maxlen=self.pre_roll_frames)
        self.speech_frames = 0
        self.silence_frames = 0

    def audio_callback(self, indata, frames, time_info, status):
        if status:
            print("Audio status:", status, file=sys.stderr)
        # convert float32 [-1,1] to int16
        audio = (indata[:, 0] * 32767).astype(np.int16).tobytes()
        self.q.put(audio)

    def frame_generator(self):
        while not self.stop_flag.is_set():
            try:
                frame = self.q.get(timeout=0.1)
            except queue.Empty:
                continue
            yield frame

    def run(self):
        print("Starting VAD recorder. Press Ctrl+C to stop.")
        stream = sd.InputStream(channels=CHANNELS, samplerate=self.sample_rate,
                                dtype='float32', blocksize=self.frame_size,
                                callback=self.audio_callback)
        with stream:
            frames_iter = self.frame_generator()
            current_chunk = bytearray()
            chunk_start_time = None
            for frame in frames_iter:
                is_speech = self.vad.is_speech(frame, sample_rate=self.sample_rate)
                # maintain pre-roll
                self.pre_roll.append(frame)
                if is_speech:
                    self.speech_frames += 1
                    self.silence_frames = 0
                else:
                    if self.speech_frames > 0:
                        self.silence_frames += 1

                # start recording when we have enough speech in a row
                if not self.recording:
                    if self.speech_frames * (self.frame_ms / 1000.0) >= MIN_SPEECH_SEC:
                        # start recording, include pre-roll
                        print("Speech detected -> start recording")
                        self.recording = True
                        chunk_start_time = time.time()
                        for pf in self.pre_roll:
                            current_chunk.extend(pf)
                        self.pre_roll.clear()
                        # clear counters
                        self.speech_frames = 0
                        self.silence_frames = 0
                else:
                    # append current frame
                    current_chunk.extend(frame)
                    chunk_elapsed = time.time() - chunk_start_time
                    # stop when we detect sufficient silence after speech OR reach chunk size
                    if (self.silence_frames * (self.frame_ms / 1000.0) >= MIN_SILENCE_AFTER_SPEECH) \
                       or (chunk_elapsed >= CHUNK_SECONDS):
                        timestamp = datetime.utcnow().strftime("%Y%m%dT%H%M%S")
                        filename = self.output_dir / f"rec_{timestamp}.wav"
                        # write as 16kHz mono int16
                        data = np.frombuffer(bytes(current_chunk), dtype=np.int16)
                        sf.write(str(filename), data, self.sample_rate, subtype='PCM_16')
                        print(f"Saved {filename} ({len(data)/self.sample_rate:.2f}s)")
                        self._enforce_storage_limit(MAX_STORAGE_MB)
                        # reset
                        current_chunk = bytearray()
                        chunk_start_time = None
                        self.recording = False
                        self.speech_frames = 0
                        self.silence_frames = 0

    def _enforce_storage_limit(self, max_mb):
        max_bytes = max_mb * 1024 * 1024
        files = sorted(self.output_dir.glob("*.wav"), key=lambda p: p.stat().st_mtime)
        total = sum(f.stat().st_size for f in files)
        while total > max_bytes and files:
            f = files.pop(0)
            try:
                size = f.stat().st_size
                f.unlink()
                total -= size
                print(f"Deleted {f} to free space")
            except Exception as e:
                print("Failed to delete", f, e)
                break

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--sensitivity", type=float, default=0.6,
                        help="0..1 user sensitivity (1=most sensitive)")
    parser.add_argument("--max-storage-mb", type=int, default=MAX_STORAGE_MB)
    args = parser.parse_args()
    MAX_STORAGE_MB = args.max_storage_mb

    vadr = VADRecorder(sensitivity=args.sensitivity)
    try:
        vadr.run()
    except KeyboardInterrupt:
        print("Stopping...")
        vadr.stop_flag.set()
        time.sleep(0.5)
