# TODOs

> 最後更新：2026-09-21
>
> 強化計畫來源：[`docs/ENHANCE_PLAN.md`](docs/ENHANCE_PLAN.md)
>
> 執行原則：依 P0 → P1 → P2 → P3 推進；只有程式、測試與驗收條件全部完成後才勾選。既有 Phase 1–10 為歷史工作紀錄，Phase 11 起為重新檢視後的目前 backlog。

## Completed Preparation Tasks
- [x] Register `RecorderPlugin` in `MainActivity.kt` <!-- id: 1 -->
- [x] Add unit tests for `RecorderPlugin` in Flutter/Dart layer <!-- id: 2 -->
- [x] Run unit tests to verify the project and plugin stubs <!-- id: 3 -->

## Phase 1: Verify & Test Android Core Logic (Mock VAD)
- [x] Add Android unit tests for `ScheduleStore` JSON persistence <!-- id: 4 -->
- [x] Add Android unit tests for `AudioFrameBuffer` circular buffer logic <!-- id: 5 -->
- [x] Add Android unit tests for `AudioFileWriter` WAV formatting <!-- id: 6 -->
- [x] Configure `app/build.gradle.kts` for Android unit testing (`test` target) <!-- id: 7 -->
- [x] Execute `./gradlew test` in Android project and verify all Kotlin tests pass <!-- id: 8 -->

## Phase 2: Integrate WebRTC VAD (Native JNI Integration)
- [x] Configure CMake or NDK build settings in Android module <!-- id: 9 -->
- [x] Download/extract WebRTC VAD C source code into `android/app/src/main/cpp` <!-- id: 10 -->
- [x] Implement JNI binding implementation `everlisten_vad.c` <!-- id: 11 -->
- [x] Compile and generate `libeverlisten_vad.so` for arm64-v8a and armeabi-v7a <!-- id: 12 -->
- [x] Verify `VADNativeBridge` functions correctly with NDK compiled library <!-- id: 13 -->
  - [x] Add `VADProcessorTest.kt` unit test to verify `MockVADProcessor` and native loading fallback <!-- id: 22 -->

## Phase 3: Android Background Survival & Optimization
- [x] Create `BootReceiver.kt` to reschedule Alarms on device reboot <!-- id: 14 -->
- [x] Declare `BootReceiver` and `RECEIVE_BOOT_COMPLETED` permission in `AndroidManifest.xml` <!-- id: 15 -->
- [x] Verify microphone foreground service type compatibility on API 30+ <!-- id: 16 -->
- [x] Profile memory usage and CPU footprint of `MockVADProcessor` vs `WebRTCVADProcessor` <!-- id: 17 -->

## Phase 4: Android Recording/Scheduling Completion
- [x] Harden `RecorderService` lifecycle, frame reads, file finalization, and mode validation <!-- id: 23 -->
- [x] Add Android microphone/notification permission request bridge <!-- id: 24 -->
- [x] Validate recording mode, sensitivity, storage, schedule repeat values, and future schedule times <!-- id: 25 -->
- [x] Handle exact-alarm permission and provide a settings handoff on Android 12+ <!-- id: 26 -->
- [x] Replace existing schedule alarms when updating a schedule with the same ID <!-- id: 27 -->
- [x] Fall back to inexact alarms during boot recovery when exact-alarm access is unavailable <!-- id: 28 -->
- [x] Add failure-path tests for invalid pre-roll capacity and schedule replacement/weekly recurrence <!-- id: 31 -->
- [x] Extract and test recording storage pruning; add WakeLock acquire/release cleanup <!-- id: 32 -->
- [x] Run the complete Android unit-test suite successfully with Android Studio's JDK <!-- id: 33 -->
- [ ] Complete physical-device validation for Detect, Monitoring, and Schedule modes *(deferred for this iteration)* <!-- id: 29 -->
- [ ] Add production storage, battery/background survival, and failure-path validation <!-- id: 30 -->

## Phase 5: iOS Implementation & Integration
- [x] Implement initial MethodChannel and EventChannel handlers in `RecorderPlugin.swift` *(full parity is tracked in Phase 11)* <!-- id: 18 -->
- [x] Implement initial Swift `AVAudioEngine` record/tap and file output path <!-- id: 19 -->
- [ ] Build and link WebRTC VAD static library for iOS build <!-- id: 20 -->
- [x] Implement initial `BGTaskScheduler` dispatcher for persisted schedules <!-- id: 21 -->

## P0: Critical Bugs
- [x] Fix iOS `RecorderPlugin.swift` line 464 string interpolation bug — filename produces literal `(mode)_(Int(...)).caf` instead of interpolated values, causing all recordings to overwrite each other <!-- id: 34 -->

## Phase 6: Device Validation (Both Platforms)
- [ ] Android physical-device validation: Detect, Monitoring, Schedule modes across API 28-34 <!-- id: 35 -->
- [ ] Android battery/Doze/OEM testing (Samsung, Google, OnePlus) <!-- id: 36 -->
- [ ] iOS physical-device validation: Detect, Monitoring, Schedule modes (iOS 14+) <!-- id: 37 -->
- [ ] iOS BGTaskScheduler dispatch reliability testing <!-- id: 38 -->
- [ ] iOS AVAudioSession interruption handling (phone calls, alarms) <!-- id: 39 -->

## Phase 7: Production Hardening
- [ ] Cross-platform integration tests for feature parity <!-- id: 40 -->
- [ ] Add structured logging (File + console) on both platforms <!-- id: 41 -->
- [ ] Handle edge cases: missing microphone, low disk space, native lib failures <!-- id: 42 -->
- [ ] iOS App Store compliance: privacy policy, recording indicator, background audio justification <!-- id: 43 -->
- [ ] UI polish: VU meter, file list/playback, settings screen <!-- id: 44 -->

## Phase 8: Android Core Test Coverage (P0)

### RecorderPlugin.kt Tests (Robolectric)
- [x] Add unit tests for `handleStartRecording()` — valid mode, invalid mode, permission denied <!-- id: 45 -->
- [x] Add unit tests for `handleStopRecording()` — success and failure paths <!-- id: 46 -->
- [x] Add unit tests for `handleScheduleRecording()` — valid input, invalid startTime, invalid endTime, invalid repeat <!-- id: 47 -->
- [x] Add unit tests for `handleCancelSchedule()` — valid id, missing id <!-- id: 48 -->
- [x] Add unit tests for `handleGetSchedules()` — empty and populated schedule list <!-- id: 49 -->
- [x] Add unit tests for `handleRequestPermissions()` — granted and denied scenarios <!-- id: 50 -->
- [x] Add unit tests for `handleGetStatus()` — returns status snapshot with schedules <!-- id: 51 -->
- [x] Add unit tests for `handleSetSensitivity()` and `handleSetMaxStorage()` <!-- id: 52 -->

### RecorderService.kt Tests
- [x] Add unit tests for `startRecording()` / `stopRecording()` lifecycle with mock AudioRecord <!-- id: 53 -->
- [x] Add unit tests for detect mode: speech detection, pre-roll write, silence timeout, file close <!-- id: 54 -->
- [x] Add unit tests for monitor mode: continuous write, chunk rotation at 60s <!-- id: 55 -->
- [x] Add unit tests for schedule mode: scheduledSession flag, scheduleEnded event <!-- id: 56 -->
- [x] Add unit tests for `rotateCurrentFile()` — fileReady event emission <!-- id: 57 -->
- [x] Add unit tests for `enforceStorageLimit()` — storage pruning triggers <!-- id: 58 -->
- [x] Add unit tests for invalid mode in `startRecording()` — error event + stopSelf <!-- id: 59 -->

## Phase 9: Existing Test Gap Fill (P1)
- [x] VADProcessorTest: add sensitivity 0.0/1.0 edge cases, dynamic sensitivity switch <!-- id: 60 -->
- [x] RecordingStorageTest: add `totalBytes()`, `ensureDirectory()`, empty directory <!-- id: 61 -->
- [x] ScheduleStoreTest: add invalid input validation, timezone edge cases <!-- id: 62 -->
- [x] BootReceiverTest: add recurring schedule rescheduling <!-- id: 63 -->

## Phase 10: iOS Test Coverage (P1)
- [x] Add `RecorderPlugin` unit-test source — MethodChannel handlers (start/stop/schedule) *(not yet included in RunnerTests target)* <!-- id: 64 -->
- [x] Add schedule normalization test source (once/daily/weekly) *(not yet executed by Xcode)* <!-- id: 65 -->
- [x] Add `AudioEngineRecorder` initialization and frame-processing test source *(not yet executed by Xcode)* <!-- id: 66 -->

## Phase 11: P0 — 上架阻斷與核心可信度

### 11.1 iOS 建置真相與 target 接線

- [x] 將 `ios/Runner/RecorderPlugin.swift` 加入 Runner target 的 Compile Sources <!-- id: 67 -->
- [x] 將 `ios/RunnerTests/RecorderPluginTests.swift` 加入 RunnerTests target 的 Compile Sources <!-- id: 68 -->
- [x] 檢查 bridging header、`vad_wrapper.c`、`webrtc_vad.c` 與 Swift/C symbols 的 target membership <!-- id: 69 -->
- [ ] 安裝並選取完整 Xcode Developer Directory，接受 license 並安裝可用的 iOS Simulator runtime <!-- id: 70 -->
- [ ] 執行 RunnerTests，確認既有 iOS 測試確實被發現且通過 <!-- id: 71 -->
- [ ] 執行 `flutter build ios --simulator --no-codesign` 並修復所有編譯、連結與 entitlement 問題 <!-- id: 72 -->

### 11.2 iOS 音訊與 VAD 正確性

- [ ] 以 `AVAudioConverter` 將硬體輸入轉為 mono PCM16 16 kHz *(已實作，待完整 Xcode typecheck/音檔測試)* <!-- id: 73 -->
- [ ] 將 VAD 輸入切成合法的 10/20/30 ms frame（160/320/480 samples） *(已實作，待完整 Xcode typecheck/音檔測試)* <!-- id: 74 -->
- [ ] 將錄音輸出 pipeline 與 VAD 分析 pipeline 分離，不再假設硬體輸入格式為 16 kHz *(已實作，待完整 Xcode typecheck)* <!-- id: 75 -->
- [ ] 加入 44.1 kHz、48 kHz、單聲道與多聲道轉換測試 <!-- id: 76 -->
- [ ] 使用固定音檔建立 deterministic VAD 測試，驗證語音/靜音及 sensitivity 邊界 <!-- id: 77 -->
- [ ] 驗證不會再出現 unsupported frame length/sample rate，並將錯誤回傳 Flutter *(已加入錯誤事件，待完整 Xcode/VAD 測試)* <!-- id: 78 -->

### 11.3 iOS 排程產品承諾與 BGTaskScheduler

- [x] 決定 iOS 排程採「通知後由使用者啟動」：以本機通知提醒，不自動開始錄音 <!-- id: 79 -->
- [x] 更新 iOS UI，不宣稱能準點無人值守錄音，改顯示「Add reminder」與提醒限制 <!-- id: 80 -->
- [x] 移除 BG task 以 timer 占用整段排程期間的設計 <!-- id: 81 -->
- [x] 移除方案 B 不再需要的 BG task registration 與 processing entitlement <!-- id: 82 -->
- [x] 移除方案 B 不再需要的 BGTask completion 流程，避免重複 completion 風險 <!-- id: 83 -->
- [x] 顯示提醒建立與通知權限拒絕狀態，提供可理解的復原訊息 <!-- id: 84 -->

### 11.4 Android RecorderService 資料與資源安全

- [ ] 建立 idempotent 的單一 shutdown/cleanup 路徑 *(已實作，待 Gradle 測試)* <!-- id: 85 -->
- [ ] 讓正常停止、read error、初始化失敗與未捕捉例外都釋放 `AudioRecord`、writer、wake lock 與 foreground service *(已實作，待 Gradle 測試)* <!-- id: 86 -->
- [ ] 即使尚未進入 recording，STOP intent 仍會正確 `stopSelf()` 並更新狀態 <!-- id: 87 -->
- [ ] 修正 detect mode 觸發時 current frame 同時存在於 pre-roll 與當前寫入所造成的重複資料 *(已實作，待 Gradle 測試)* <!-- id: 88 -->
- [ ] 以可辨識 sample pattern 逐 byte 驗證 pre-roll 與觸發 frame 輸出 *(測試已加入，待 Gradle 測試)* <!-- id: 89 -->
- [ ] 只有實體檔刪除成功後才扣除 storage 統計並送出 `storagePruned` *(已實作，待 Gradle 測試)* <!-- id: 90 -->
- [ ] 補齊 read error、stop error、AudioRecord 未初始化與 writer failure 回歸測試 *(測試已加入，待 Gradle 測試)* <!-- id: 91 -->

### 11.5 正式簽署、隱私與 API 對稱

- [ ] Android release 已移除 debug signing 並支援 Gradle property 注入，待正式 keystore/CI secret 驗證 <!-- id: 92 -->
- [ ] 由 CI secrets 或本機安全設定注入 Android release signing credentials <!-- id: 93 -->
- [ ] 建立可重現的 `flutter build appbundle --release` gate 與 artifact <!-- id: 94 -->
- [ ] 實作首次使用隱私與錄音同意說明，清楚解釋麥克風、背景執行與儲存用途 <!-- id: 95 -->
- [ ] Android 與 iOS 都提供明顯且持續的錄音中指示 <!-- id: 96 -->
- [ ] 實作權限拒絕、永久拒絕與前往系統設定的復原流程 <!-- id: 97 -->
- [x] 在 iOS MethodChannel 補齊 `requestPermissions`，維持 Dart/Android/iOS API 對稱 <!-- id: 98 -->
- [ ] 移除 iOS 關鍵路徑的 `try?` 靜默吞錯，定義穩定 error code 與診斷訊息 <!-- id: 99 -->
- [x] 讓 iOS reminder save/create 具交易語意；通知建立或持久化失敗不得留下看似有效的排程 <!-- id: 100 -->
- [ ] 建立隱私政策、錄音法規提醒、資料保留/刪除說明及 App Store/Play 揭露清單 <!-- id: 101 -->

## Phase 12: P1 — 可用且可收費的 MVP

### 12.1 錄音索引、復原與檔案庫

- [ ] 定義 recording metadata schema：ID、路徑、格式、建立時間、長度、大小、模式、schedule/session ID、狀態 <!-- id: 102 -->
- [ ] 建立 schema version 與 migration 策略 <!-- id: 103 -->
- [ ] App 啟動時掃描錄音目錄，協調遺失 metadata、孤兒檔案與未完成暫存檔 <!-- id: 104 -->
- [ ] 寫檔採暫存檔＋完成後原子 rename，避免半成品被列為可播放錄音 <!-- id: 105 -->
- [ ] 實作 WAV header 修復或將無法修復的檔案清楚標記為損壞 <!-- id: 106 -->
- [ ] 實作 Flutter 錄音清單，顯示時間、長度、大小、來源模式與狀態 <!-- id: 107 -->
- [ ] 實作錄音播放、暫停、seek 與播放錯誤處理 <!-- id: 108 -->
- [ ] 實作重新命名、單檔分享/匯出及批次匯出 <!-- id: 109 -->
- [ ] 實作單檔刪除與「刪除全部」，確保 metadata 與實體檔一致 <!-- id: 110 -->
- [ ] 顯示目前使用空間、容量上限及可錄時間估算 <!-- id: 111 -->

### 12.2 跨平台狀態機與 Flutter 可靠度

- [ ] 定義 `idle / starting / recording / stopping / interrupted / error` 狀態與合法轉移 <!-- id: 112 -->
- [ ] 每次錄音建立唯一 session ID，讓 start/stop/event 可關聯且命令可重入 <!-- id: 113 -->
- [ ] Android 與 iOS 依共同狀態語意實作 native state machine <!-- id: 114 -->
- [ ] 持久化最後狀態、錯誤與完成事件，Flutter engine 重建後可補讀 <!-- id: 115 -->
- [ ] 為 EventChannel 建立 durable event journal、讀取後確認與去重策略 <!-- id: 116 -->
- [x] Flutter 保存並在 `dispose` 取消 event subscription，避免 `setState` after dispose <!-- id: 117 -->
- [x] 所有主要 async platform calls 捕捉錯誤，顯示 loading/error 並防止重複操作 <!-- id: 118 -->
- [x] App 啟動與 resume 初始同步 native `getStatus()`，不只依賴記憶體 `_monitoring` 旗標 <!-- id: 119 -->
- [ ] 用 typed recording/schedule/event models 取代 `Map<String, dynamic>` 與 magic strings <!-- id: 120 -->
- [ ] 評估並決定是否使用 Pigeon 產生跨平台 type-safe API <!-- id: 121 -->

### 12.3 排程模型、時區與 Android alarm

- [ ] 將 schedule 的觸發方式與錄音策略拆開，新增 `captureMode: detect | monitor` <!-- id: 122 -->
- [ ] 時區改存 IANA timezone ID，不使用 `CST` 等模糊縮寫 <!-- id: 123 -->
- [ ] 以 local wall-clock + timezone 計算 daily/weekly recurrence，正確處理 DST <!-- id: 124 -->
- [ ] 定義重疊排程的拒絕、合併或優先權規則並實作 <!-- id: 125 -->
- [ ] 定義 missed window 行為：立即啟動、標記 missed 或跳到下一次 <!-- id: 126 -->
- [ ] 修正 BootReceiver 在重開機時略過仍位於有效區間內排程的問題 <!-- id: 127 -->
- [ ] 將 SharedPreferences 的 `|` 編碼改為 JSON 或 Room，加入 schema version 與 migration <!-- id: 128 -->
- [ ] 將 PendingIntent request code 改為持久化唯一整數，避免 `hashCode()` 碰撞 <!-- id: 129 -->
- [ ] 只有使用者可感知且真的需要準點的排程使用 exact alarm，其餘採 inexact/WorkManager <!-- id: 130 -->
- [ ] 處理 exact-alarm 權限狀態變更 broadcast，重新排程或降級並通知使用者 <!-- id: 131 -->
- [ ] 測試 once/daily/weekly、DST、重疊、重開機、權限移除與時鐘變更 <!-- id: 132 -->

### 12.4 分段、壓縮與平台中斷

- [ ] 決定 MVP 錄音格式（WAV/AAC/Opus）、品質、相容性與容量取捨 <!-- id: 133 -->
- [ ] 實作可設定的 15–60 分鐘安全分段及跨 segment metadata <!-- id: 134 -->
- [ ] 實作 AAC/Opus 或其他選定壓縮格式，保留必要的 WAV 選項 <!-- id: 135 -->
- [ ] iOS detect mode 實作真正的 pre-roll buffer <!-- id: 136 -->
- [ ] iOS 處理 AVAudioSession interruption、route change 與 media services reset <!-- id: 137 -->
- [ ] Android 處理 audio focus、輸入裝置切換與服務被系統終止後的狀態 <!-- id: 138 -->
- [ ] 為來電、鬧鐘、藍牙/耳機插拔與輸入裝置消失建立回歸測試 <!-- id: 139 -->

### 12.5 CI 與整合測試

- [ ] CI 已加入 Dart format check、`flutter analyze` 與 `flutter test`，待 GitHub Actions 實際通過 <!-- id: 140 -->
- [ ] CI 已加入 Ubuntu + JDK 17 Android unit-test job，待 GitHub Actions 實際通過 <!-- id: 141 -->
- [ ] CI 建置 Android debug APK 與正式簽署的 release AAB <!-- id: 142 -->
- [ ] CI 已加入 macOS iOS simulator build/RunnerTests job，待 GitHub Actions 實際通過 <!-- id: 143 -->
- [ ] 建立跨平台 MethodChannel contract tests 與核心 feature parity tests <!-- id: 144 -->
- [ ] 產生並保存真實 coverage report，移除 AGENT/TODO 中的人工估計值 <!-- id: 145 -->
- [ ] 將所有 build/test gate 設為 pull request 必須通過的 checks <!-- id: 146 -->

## Phase 13: P2 — 品質、留存與維運

### 13.1 可觀測性與品質基準

- [ ] 定義不收集音訊內容的 privacy-safe telemetry schema 並提供 opt-out <!-- id: 147 -->
- [ ] 記錄 session 成功率、失敗原因、中斷率、排程延遲與檔案損壞率 <!-- id: 148 -->
- [ ] 建立安靜、會議室、街道與車內的 VAD 測試音檔集 <!-- id: 149 -->
- [ ] 量測 VAD false positive/negative 並為各 sensitivity 建立基準 <!-- id: 150 -->
- [ ] 建立 1/8/24 小時 CPU、記憶體、耗電與儲存成長基準 <!-- id: 151 -->
- [ ] 測試 storage prune 在刪除失敗、檔案使用中與容量不足時的一致性 <!-- id: 152 -->

### 13.2 資料保護與產品品質

- [ ] iOS 設定適當的 Data Protection，決定錄音是否排除 iCloud/device backup <!-- id: 153 -->
- [ ] Android 明確實作 app-private、no-backup 或加密儲存策略 <!-- id: 154 -->
- [ ] 文件化安全刪除限制、備份行為與裝置遺失風險 <!-- id: 155 -->
- [ ] 補齊 Semantic labels、鍵盤/螢幕閱讀器操作、Dynamic Type 與色彩對比 <!-- id: 156 -->
- [ ] 建立繁體中文與英文在地化，時間、日期與容量依 locale 顯示 <!-- id: 157 -->
- [ ] 提供 sensitivity、pre-roll、分段長度、保留容量與錄音格式設定 <!-- id: 158 -->
- [ ] 在設定變更前顯示預估耗電、容量與可能影響 <!-- id: 159 -->

### 13.3 架構、工具鏈與文件

- [ ] 將 Android 大型 service/plugin 拆為 RecorderEngine、StateMachine、Scheduler、Storage、PermissionManager <!-- id: 160 -->
- [ ] 將 iOS 大型 plugin/recorder 依相同職責拆分並導入 dependency injection <!-- id: 161 -->
- [ ] 移除 `android.builtInKotlin=false`、`android.newDsl=false` 與 AGP 9 不需要的 Kotlin Android plugin <!-- id: 162 -->
- [ ] 鎖定並文件化 JDK、Flutter、Dart、AGP、Gradle、Kotlin、NDK 與 Xcode 版本 <!-- id: 163 -->
- [ ] 在乾淨環境重新建置 Android native artifacts，排除 NDK/CMake 警告 <!-- id: 164 -->
- [ ] 重寫根目錄與 Flutter README：產品目的、架構、建置、測試、限制及隱私 <!-- id: 165 -->
- [ ] 更新 `AGENT.md`、`TODOs.md` 與實際 build/test 狀態，建立每次 release 的文件檢查 <!-- id: 166 -->
- [ ] 清理或移出 `result.txt`、`result_short.txt`、`docs/tmp*` 等暫存/產出物並補 `.gitignore` <!-- id: 167 -->

### 13.4 實機與 OEM 測試矩陣

- [ ] Android Pixel：鎖屏、Doze、重開機、權限回收、低容量與 8/24 小時錄音 <!-- id: 168 -->
- [ ] Android Samsung：鎖屏、Doze、重開機、權限回收、低容量與 8/24 小時錄音 <!-- id: 169 -->
- [ ] Android 小米/其他高限制 OEM：背景限制與使用者設定引導 <!-- id: 170 -->
- [ ] iPhone 實機：背景錄音、螢幕鎖定、來電、路由變更、低容量與長時間錄音 <!-- id: 171 -->
- [ ] 依測試結果建立支援裝置/OS 範圍與已知限制文件 <!-- id: 172 -->

## Phase 14: P3 — 商業驗證後的選配功能

- [ ] 以 20–50 位目標使用者執行封閉 Beta，驗證田野紀錄、個人語音筆記與照護事件紀錄情境 <!-- id: 173 -->
- [ ] 追蹤 D7 留存、每週有效錄音次數、匯出率與付費意願 <!-- id: 174 -->
- [ ] 驗證核心留存與多裝置需求後，再設計端對端加密雲端同步 <!-- id: 175 -->
- [ ] 完成資安、同意、刪除、成本與資料區域評估後，再開發雲端同步 <!-- id: 176 -->
- [ ] 驗證單位經濟與敏感資料流程後，再開發轉錄、摘要與搜尋 <!-- id: 177 -->
- [ ] 設計 Free/Pro entitlement：歷史、儲存、多排程、進階設定、批次匯出與格式 <!-- id: 178 -->
- [ ] 在 Beta 具有穩定留存與付費訊號後才接入訂閱與用量方案 <!-- id: 179 -->
- [ ] 個人使用情境成立後，再評估團隊共享、角色權限、稽核與案件管理 <!-- id: 180 -->

## 方案 B 後續文件與通知狀態

- [ ] 更新 PRD、App Store/Play 商店文案與隱私說明，明確寫出 iOS 是通知提醒、不會自動開始錄音 <!-- id: 198 -->
- [x] iOS native 回報通知送達與使用者點擊事件到 Flutter <!-- id: 199 -->
- [x] 在 Flutter 顯示已送達/未回應狀態，提供手動開始錄音的復原操作 <!-- id: 200 -->

## Phase 15: Beta 發行 Gate

- [ ] Flutter analyze/test、Android unit test/build、iOS simulator build/test 全部由 CI 通過 <!-- id: 181 -->
- [ ] 從乾淨環境完成 Android release AAB 與 iOS archive/no-codesign build，Android 未使用 debug signing <!-- id: 182 -->
- [ ] 連續 8 小時錄音、容量滿、權限拒絕、App 被殺、裝置重開機與中斷案例全數有測試紀錄 <!-- id: 183 -->
- [ ] 所有結束/失敗路徑均不殘留麥克風、foreground service、wake lock 或未完成 BG task <!-- id: 184 -->
- [ ] 錄音可列出、播放、匯出與刪除，metadata 與實體檔維持一致 <!-- id: 185 -->
- [ ] iOS UI 清楚揭露 best-effort 限制；Android exact alarm 權限與降級行為可理解 <!-- id: 186 -->
- [ ] 隱私政策、權限用途、錄音指示、資料保留與刪除流程通過人工檢查 <!-- id: 187 -->
- [ ] 成功產生且可播放的 recording session 比率達 99% 以上 <!-- id: 188 -->
- [ ] 無使用者操作下的檔案遺失案例為 0；任何案例皆阻擋發行 <!-- id: 189 -->
- [ ] crash-free sessions 達 99.5% 以上 <!-- id: 190 -->
- [ ] Android 排程成功率達既定門檻並依裝置公開限制 <!-- id: 191 -->
- [ ] README、AGENT、TODO、版本與實際 build/test 狀態一致 <!-- id: 192 -->

## 暫緩項目（不得早於觸發條件）

- [ ] 錄音可靠度與檔案庫完成前，不進行純視覺的大型 UI 改版 <!-- id: 193 -->
- [ ] 同意、加密、刪除與成本模型完成前，不上傳錄音到雲端 <!-- id: 194 -->
- [ ] 真實使用資料與 VAD baseline 足夠前，不自行訓練語音模型 <!-- id: 195 -->
- [ ] 不宣稱 iOS 與 Android 具有相同的準點背景排程能力 <!-- id: 196 -->
- [ ] 不以測試檔數量或人工估計 coverage 作為完成依據 <!-- id: 197 -->
