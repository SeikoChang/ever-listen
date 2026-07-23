# iOS WebRTC VAD 整合手把手教學

> **目標**：將 Android 已驗證的 WebRTC VAD C 程式碼整合到 iOS Runner target，取代 RMS-based VAD。

---

## 目錄

1. [前置條件](#前置條件)
2. [專案結構變化](#專案結構變化)
3. [步驟 1：取得 WebRTC VAD 原始碼](#步驟-1取得-webrtc-vad-原始碼)
4. [步驟 2：vad_wrapper 純 C 封裝層](#步驟-2vad_wrapper-純-c-封裝層)
5. [步驟 3：更新 Swift Bridging Header](#步驟-3更新-swift-bridging-header)
6. [步驟 4：將 C 檔案加入 Xcode 編譯](#步驟-4將-c-檔案加入-xcode-編譯)
7. [步驟 5：驗證編譯](#步驟-5驗證編譯)
8. [步驟 6：確認 WebRTCVADProcessor 運作](#步驟-6確認-webrtcvadprocessor-運作)
9. [排錯指南](#排錯指南)

---

## 前置條件

| 項目 | 需求 |
|------|------|
| macOS | 13.0 (Ventura) 或更高 |
| Xcode | 15.0 或更高 |
| iOS SDK | iOS 14.0+ |
| Flutter | 與專案目前版本一致 |

---

## 專案結構變化

完成後的 `ios/Runner/` 目錄：

```
ios/Runner/
├── RecorderPlugin.swift          ← 已修改 (WebRTCVADProcessor 類別 + 整合)
├── Runner-Bridging-Header.h      ← 已修改 (加入 vad_wrapper.h import)
├── vad_wrapper.h                 ← 新增 (純 C 封裝層 header)
├── vad_wrapper.c                 ← 新增 (純 C 封裝層實作)
├── webrtc_vad.h                  ← 新增 (從 Android 複製)
└── webrtc_vad.c                  ← 新增 (從網路下載)
```

> **注意**：`vad_wrapper.h`、`vad_wrapper.c`、`RecorderPlugin.swift` 的修改、`Runner-Bridging-Header.h` 的修改已經在 git 中。你只需要取得 `webrtc_vad.c` / `webrtc_vad.h`，並在 Xcode 中將 C 檔案加入 Compile Sources。

---

## 步驟 1：取得 WebRTC VAD 原始碼

`webrtc_vad.c` 和 `webrtc_vad.h` 是 Google WebRTC 專案的原始碼。`webrtc_vad.h` 已在 git 中，但 `webrtc_vad.c` 從來沒有被 git track（一直是 placeholder），需要重新取得。

### 1.1 確認 `webrtc_vad.h` 是否正確

```bash
# 應該約 3 KB，包含 WebRtcVad_Create、WebRtcVad_Process 等宣告
wc -c flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.h
# 預期：~3136 bytes

head -5 flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.h
# 應該看到 WebRTC 版權宣告
```

如果正確，複製到 iOS：

```bash
cp flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.h \
   flutter_voice_recorder/ios/Runner/
```

### 1.2 取得 `webrtc_vad.c`（核心實作，約 30~60 KB）

**方法 A：從 Chromium 原始碼下載（推薦）**

```bash
curl -o flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.c \
  "https://chromium.googlesource.com/external/webrtc/+archive/main/webrtc/modules/audio_processing/vad/webrtc_vad.c"

wc -c flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.c
# 預期：30000~60000 bytes
```

**方法 B：從 GitHub mirror 下載**

```bash
curl -o flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.c \
  "https://raw.githubusercontent.com/nicbarker/webrtc-audio-processing/master/src/webrtc_vad.c"
```

**方法 C：搜尋本地備份**

如果你在 Android Studio project 中還留有原始檔案：

```bash
find ~ -name "webrtc_vad.c" -size +10k 2>/dev/null
```

### 1.3 複製到 iOS 並加入 git

```bash
# 複製到 iOS
cp flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.c \
   flutter_voice_recorder/ios/Runner/

# 確認兩份檔案大小一致
wc -c flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.c
wc -c flutter_voice_recorder/ios/Runner/webrtc_vad.c

# 重要：將 webrtc_vad.c 加入 git（避免下次又遺失）
git add flutter_voice_recorder/android/app/src/main/cpp/webrtc_vad.c
git commit -m "fix: add missing webrtc_vad.c source file"
```

> **授權提醒**：`webrtc_vad.c` 的授權為 BSD-3-Clause，專案根目錄應包含相應的授權宣告。

### 1.4 確認 `vad_wrapper` 檔案存在

`vad_wrapper.h` 和 `vad_wrapper.c` 是專案新增的檔案，應該已經存在：

```bash
ls ios/Runner/vad_wrapper.{c,h}
```

---

## 步驟 2：vad_wrapper 純 C 封裝層

Android 的 JNI binding (`everlisten_vad.c`) 依賴 `jni.h` 和 `jbyteArray`，iOS 無法使用。因此需要一個**平台無關的純 C 封裝層**。

### `vad_wrapper.h`（已放在 `ios/Runner/`）

```c
#ifndef VAD_WRAPPER_H
#define VAD_WRAPPER_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void *vad_init(int aggressiveness);
int vad_process(void *handle, const int16_t *frame, size_t frame_length);
void vad_destroy(void *handle);

#ifdef __cplusplus
}
#endif

#endif /* VAD_WRAPPER_H */
```

### `vad_wrapper.c`（已放在 `ios/Runner/`）

```c
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
    return WebRtcVad_Process(handle, 16000, frame, frame_length);
}

void vad_destroy(void *handle) {
    if (handle != NULL) {
        WebRtcVad_Free(handle);
    }
}
```

> **設計說明**：
> - `vad_init(aggressiveness)` — 建立並初始化 VAD 實例，aggressiveness 0~3
> - `vad_process(handle, frame, length)` — 處理一幀 16-bit PCM，回傳 1=語音、0=非語音、-1=錯誤
> - `vad_destroy(handle)` — 釋放 VAD 實例記憶體
> - 樣本率固定為 16000 Hz（與 Android 端一致）

---

## 步驟 3：更新 Swift Bridging Header

Bridging Header 讓 Swift 程式碼可以呼叫 C 函式。

確認 `ios/Runner/Runner-Bridging-Header.h` 包含 `vad_wrapper.h` 的 import：

```objc
#import "GeneratedPluginRegistrant.h"
#import "vad_wrapper.h"
```

> 這個修改已經在 git 中，你可以檢查一下內容是否正確。

---

## 步驟 4：將 C 檔案加入 Xcode 編譯

這是**最關鍵的步驟**——Xcode 必須知道要編譯這些 `.c` 檔案。

### 4.1 將檔案加入 Xcode Project Navigator

1. 在終端機執行：
   ```bash
   cd flutter_voice_recorder
   open ios/Runner.xcworkspace
   ```
2. 在 Xcode 左側 Project Navigator 中，找到 **Runner** group
3. 將以下 4 個檔案從 Finder 拖曳到 Xcode 的 **Runner** group：
   - `webrtc_vad.c`
   - `webrtc_vad.h`
   - `vad_wrapper.c`
   - `vad_wrapper.h`
4. 彈出對話框中：
   - ✅ 勾選 "Add to targets: Runner"
   - ❌ 不要勾選 "Create groups"
   - ✅ 勾選 "Copy items if needed"（可選）

### 4.2 確認 Compile Sources

1. 在 Xcode 中，點擊左側 **Runner** target（藍色圖示）
2. 選擇 **Build Phases** tab
3. 展開 **Compile Sources** 區塊
4. 確認以下檔案在列表中：
   - ✅ `webrtc_vad.c`
   - ✅ `vad_wrapper.c`
5. 如果不在列表中，點擊 `+` 按鈕手動加入

### 4.3 確認 Header Search Paths（通常不需要）

如果你的專案結構標準，不需要修改 Header Search Paths。Xcode 預設會在同一個 group 中尋找 header。

如果有問題，可以加入：
- **Build Settings** → **Header Search Paths** → 加入 `$(PROJECT_DIR)/Runner`

---

## 步驟 5：驗證編譯

### 5.1 在 Xcode 中編譯

1. 在 Xcode 中選擇 **Runner** target
2. 選擇模擬器（推薦 iPhone 15 或更新型號）
3. 按 `Cmd + B` 編譯

**預期結果**：編譯成功，無錯誤。

### 5.2 如果編譯失敗

常見錯誤及解法：

| 錯誤訊息 | 原因 | 解法 |
|----------|------|------|
| `file not found: 'vad_wrapper.h'` | Header 搜尋路徑錯誤 | 確認 Bridging Header 已正確 import |
| `file not found: 'webrtc_vad.h'` | `webrtc_vad.h` 未放在正確位置 | 確認步驟 1 已複製 |
| `undefined symbol: WebRtcVad_Create` | `webrtc_vad.c` 未加入 Compile Sources | 回步驟 4.2 確認 |
| `no such module 'Runner'` | 測試 target 設定錯誤 | 確認 test target 的 `@testable import Runner` |
| `incompatible pointer types` | C 函式宣告與 Bridging Header 不匹配 | 確認 `vad_wrapper.h` 宣告與 `.c` 實作一致 |
| `ld: symbol(s) not found` | 連結錯誤 | 確認 `.c` 檔案在 Compile Sources 中 |

### 5.3 用命令行編譯（可選）

```bash
cd flutter_voice_recorder
flutter build ios --simulator
```

---

## 步驟 6：確認 WebRTCVADProcessor 運作

### 6.1 程式碼架構說明

整合完成後的呼叫鏈：

```
Flutter (Dart)
  → MethodChannel: startRecording(mode: "detect")
    → RecorderPlugin.startRecording()
      → AudioEngineRecorder(mode, sensitivity, ...)
        → WebRTCVADProcessor.init()  ← vad_init(2)
          → vad_init() → WebRtcVad_Create()
            → WebRtcVad_Init()
              → WebRtcVad_set_mode()

每幀處理：
  → AVAudioEngine tap callback
    → AudioEngineRecorder.process(buffer)
      → vadProcessor?.process(buffer)  ← WebRTC VAD
        → float → int16 轉換
          → vad_process() → WebRtcVad_Process()
      → ?? RMS fallback (如果 VAD 不可用)
```

### 6.2 WebRTCVADProcessor 類別說明

在 `RecorderPlugin.swift` 中新增的 `WebRTCVADProcessor` 類別：

- **`init()`**：建立 WebRTC VAD 實例（預設 aggressiveness = 2）
- **`setSensitivity(_:)`**：將 0.0~1.0 的 sensitivity 映射到 aggressiveness 0~3
- **`process(_:)`**：將 `AVAudioPCMBuffer` 轉換為 `Int16` 陣列並送進 VAD
- **Graceful fallback**：如果 `vad_init()` 回傳 NULL，`process()` 回傳 `nil`，觸發 RMS fallback

### 6.3 驗證清單

- [ ] 編譯成功，無警告或錯誤
- [ ] 在 Detect mode 下，對麥克風說話能觸發 `speechStarted` event
- [ ] 停止說話後，能觸發 `speechEnded` event
- [ ] 在 Monitoring mode 下，持續錄音正常
- [ ] 在 Schedule mode 下，排程觸發正常
- [ ] 調整 sensitivity slider 能影響偵測靈敏度
- [ ] 如果 `vad_init()` 失敗，自動 fallback 到 RMS（不會 crash）

### 6.4 測試步驟

1. 在模擬器或實體裝置上執行 App
2. 切換到 **Detect mode**
3. 點擊 **Start**
4. 對麥克風說話
5. 觀察 Xcode Console 輸出：
   - 如果看到 `WebRTC VAD init failed` → 使用 RMS fallback
   - 如果沒有這個訊息 → WebRTC VAD 正常運作
6. 調整 sensitivity slider，觀察偵測行為變化

---

## 排錯指南

### 問題 1：編譯時找不到 `WebRtcVad_Create`

**原因**：`webrtc_vad.c` 沒有被編譯。

**解法**：
```
Xcode → Runner target → Build Phases → Compile Sources
```
確認 `webrtc_vad.c` 和 `vad_wrapper.c` 都在列表中。

### 問題 2：執行時 `vad_init()` 回傳 NULL

**可能原因**：
- `webrtc_vad.c` 編譯但連結失敗
- 記憶體不足（極少見）

**解法**：
- 確認編譯無錯誤
- 在 `WebRTCVADProcessor.init()` 加入 `os_log` 除錯
- 如果 init 失敗，程式會自動 fallback 到 RMS，不會 crash

### 問題 3：樣本率不匹配

**問題**：`vad_process()` 固定使用 16000 Hz，但 AVAudioEngine 的 input node 可能輸出 44100 或 48000 Hz。

**影響**：WebRTC VAD 內部會根據樣本率和 frame length 判斷幀的時長。如果樣本率不匹配，VAD 可能誤判。

**解法（未來優化）**：
1. 在 `vad_wrapper.c` 中將 `16000` 改為實際樣本率
2. 或在 Swift 端做 resample（推薦使用 `AVAudioConverter`）

### 問題 4：實體裝置上無法偵測語音

**可能原因**：
- 麥克風權限未授予
- VAD aggressiveness 設定過高
- 環境噪音過大

**解法**：
- 確認系統設定 → 隱私 → 麥克風 → App 已開啟
- 降低 aggressiveness（調高 sensitivity slider）
- 在安靜環境測試

---

## 檔案變更摘要

| 檔案 | 動作 | 說明 |
|------|------|------|
| `ios/Runner/webrtc_vad.c` | 手動下載 | WebRTC VAD 原始碼（從網路下載） |
| `ios/Runner/webrtc_vad.h` | 手動複製 | WebRTC VAD header（從 Android 複製） |
| `ios/Runner/vad_wrapper.h` | ✅ 已在 git | 純 C 封裝層 header |
| `ios/Runner/vad_wrapper.c` | ✅ 已在 git | 純 C 封裝層實作 |
| `ios/Runner/Runner-Bridging-Header.h` | ✅ 已在 git | 加入 `#import "vad_wrapper.h"` |
| `ios/Runner/RecorderPlugin.swift` | ✅ 已在 git | 加入 `WebRTCVADProcessor` 類別 + 整合到 `AudioEngineRecorder` |
| `Runner.xcodeproj/project.pbxproj` | ⚠️ 手動操作 | 將 C 檔案加入 Compile Sources |

---

## 後續優化建議

1. **樣本率適配**：將 `vad_process` 的 sample_rate 改為動態參數，根據 AVAudioEngine 實際輸出樣本率調整
2. **效能分析**：在 Instruments 中測量 VAD 處理的 CPU 使用率
3. **日誌**：加入 VAD 置信度分數的結構化日誌
4. **A/B 測試**：比較 WebRTC VAD vs RMS VAD 在不同環境下的準確率
5. **resample**：如果 AVAudioEngine 輸出 44100 Hz，加入 `AVAudioConverter` 轉到 16000 Hz 以提升 VAD 準確度
