# Phase 8: Android Core Test Coverage Plan

## Infrastructure
- Existing: Robolectric 4.11.1 (SDK 33), JUnit 4.13.2, Mockito 5.8.0 + mockito-kotlin 5.2.1
- `isIncludeAndroidResources = true`, `isReturnDefaultValues = true` configured
- No new dependencies needed

## File 1: ShadowAudioRecord.kt (new)
**Purpose**: Control AudioRecord behavior in Robolectric tests so RecorderService can run without real hardware.
- Shadow `android.media.AudioRecord`
- Expose `setState(Int)` to control `.state` getter (STATE_INITIALIZED=1, ERROR=0)
- Expose `setFrameData(ByteArray)` to control `read()` return value
- Expose `setReadCount(Int)` to limit successful reads before returning -1 (EOF)
- All other methods delegate to real instance via `Real` annotation

## File 2: RecorderPluginTest.kt (TODOs #45-52)
**Pattern**: Robolectric Context + direct `onMethodCall` invocation + capture `Result` via test wrapper

### Tests
| # | Test | Verifies |
|---|------|----------|
| 45 | startRecording valid mode | Starts foreground service with correct extras, returns success |
| 45 | startRecording invalid mode | Returns INVALID_MODE error |
| 45 | startRecording permission denied | Returns PERMISSION_DENIED error |
| 46 | stopRecording | Starts service with STOP_RECORDING action, returns success |
| 47 | scheduleRecording valid | Calls ScheduleStore.add, returns schedule map |
| 47 | scheduleRecording past start time | Returns INVALID_SCHEDULE error |
| 47 | scheduleRecording end <= start | Returns INVALID_SCHEDULE error |
| 47 | scheduleRecording invalid repeat | Returns INVALID_SCHEDULE error |
| 48 | cancelSchedule valid id | Calls ScheduleStore.cancel, returns cancelled=true |
| 48 | cancelSchedule missing id | Returns INVALID_ARGUMENT error |
| 49 | getSchedules empty | Returns empty list |
| 49 | getSchedules populated | Returns schedule list from ScheduleStore |
| 50 | requestPermissions no activity | Returns NO_ACTIVITY error |
| 50 | requestPermissions already granted | Returns {microphone: true, notifications: true} |
| 51 | getStatus | Returns status snapshot with schedules |
| 52 | setSensitivity | Starts service with SET_SENSITIVITY action |
| 52 | setMaxStorageMb | Starts service with SET_MAX_STORAGE action |

## File 3: RecorderServiceTest.kt (TODOs #53-59)
**Pattern**: Robolectric ServiceTest + ShadowAudioRecord + real AudioFileWriter/RecordingStorage

### Tests
| # | Test | Verifies |
|---|------|----------|
| 53 | startRecording creates AudioRecord | Service starts, notification shown, statusRunning=true |
| 53 | stopRecording cleanup | isRecording=false, statusRunning=false, thread joined, file finalized |
| 54 | detect mode speech detection | Silence→Speech→Silence frame pattern creates file with pre-roll + speech frames |
| 54 | detect mode emits speechStarted/speechEnded/fileReady | Event sink receives correct event sequence |
| 55 | monitor mode continuous write | All frames written to file, no VAD processing |
| 55 | monitor mode chunk rotation | File rotated after MONITOR_CHUNK_DURATION_MS |
| 56 | schedule mode | scheduledSession flag set, scheduleStarted/scheduleEnded events emitted |
| 57 | rotateCurrentFile | Closes file, emits fileReady event with correct path |
| 58 | enforceStorageLimit | Prunes files when over limit, emits storagePruned events |
| 59 | invalid mode error | Emits error event, stops self |

### Frame Data Strategy
- Silence frame: ByteArray(960) of zeros → RMS=0 → below threshold 110 (sensitivity=0.6)
- Speech frame: alternating +32512/-32768 → RMS≈23170 → above threshold
- ShadowAudioRecord returns frames in sequence, then -1 to exit loop

### Threading
- audioCaptureLoop runs on background thread
- Use `shadowOf(looper.main).idleUntilRemaining(0)` for event delivery
- Use `service.recordingThread?.join(10000)` to wait for loop exit
- Assert on filesystem (file exists, size) and captured events
