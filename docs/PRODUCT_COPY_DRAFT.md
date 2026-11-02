# Ever Listen 產品與商店文案草稿

> 狀態：工作草稿，尚未核准發布。此文件用於統一 PRD 摘要及 Android／iOS 商店文案；不代表已修改 App Store 或 Google Play 後台。

## 產品需求摘要

Ever Listen 提供 Detect（語音活動偵測）與 Monitoring（連續錄音）模式。排程行為因平台而異，產品說明、操作提示與商店文案必須清楚區分：

- **Android：** 排程可在設定時段開始錄音，並於時段結束時停止。Android 12 及以上版本，App 會先檢查精確鬧鐘（exact-alarm）權限；未授權時會要求使用者前往設定，並拒絕建立排程。若系統重啟後恢復既有排程、週期排程結束後續排時權限不可用，或權限在檢查與設定鬧鐘之間遭撤銷，App 會改用非精確鬧鐘，實際觸發時間可能延遲。
- **iOS：** 排程只會建立本機通知提醒，不會自動開始錄音。使用者必須開啟 App 並點按 **Start now** 才會開始錄音；通知是否送達及顯示方式也會受系統通知設定影響。

**產品限制：** 不可將 iOS 排程描述為無人看管的錄音，也不可暗示 Android 與 iOS 的排程能力及準時程度相同。

## Google Play 文案草稿（Android）

### 簡短說明

透過語音偵測、連續錄音與 Android 排程錄音，記錄重要聲音。

### 完整說明

Ever Listen 是一款語音錄音 App，提供語音活動偵測（Detect）與連續錄音（Monitoring）。你也可以設定錄音時段，讓 Android 在排程觸發時開始錄音，並於時段結束時停止。

在 Android 12 及以上版本，App 會先檢查精確鬧鐘（exact-alarm）權限；未授權時，App 無法建立排程。若系統重啟後恢復既有排程、週期排程結束後續排時權限不可用，或權限在檢查與設定鬧鐘之間遭撤銷，系統可能改用非精確鬧鐘，觸發時間可能延遲。錄音開始時，App 會使用前景服務顯示持續通知。

錄音功能需要麥克風權限。排程、權限與背景執行行為可能因 Android 版本及裝置設定而異。

## App Store 文案草稿（iOS）

### 簡短說明

語音偵測與連續錄音；排程通知會提醒你親自開始錄音。

### 完整說明

Ever Listen 提供語音活動偵測（Detect）與連續錄音（Monitoring），讓你依需要開始錄音。

在 iPhone 上，排程會建立本機通知提醒。通知不會自動開始錄音；收到提醒後，請開啟 App 並點按 **Start now**。通知送達時間與顯示方式取決於系統通知設定。

錄音功能需要麥克風權限。iOS 排程僅提供提醒，不支援無人看管的自動錄音。

## English draft

### Google Play — Android

**Short description**

Capture important sounds with speech detection, continuous recording, and Android schedules.

**Full description**

Ever Listen offers Detect mode for speech-triggered recording and Monitoring mode for continuous recording. You can also set a recording window for Android to start recording when the schedule is triggered and stop when the window ends.

On Android 12 and later, the app checks for exact-alarm access before creating a schedule; without access, it asks the user to grant access and cannot create the schedule. If access is unavailable while Android restores an existing schedule after reboot or continues a recurring schedule, or if access is revoked between the check and alarm setup, the app may fall back to an inexact alarm and the trigger may be delayed. While recording, the app shows an ongoing foreground-service notification.

Microphone permission is required to record audio. Scheduling and background behavior may vary with Android version, device settings, and alarm access.

### App Store — iOS

**Short description**

Speech detection and continuous recording, with reminders to start scheduled sessions yourself.

**Full description**

Ever Listen offers Detect mode for speech-triggered recording and Monitoring mode for continuous recording.

On iPhone, a schedule creates a local notification reminder. The notification does not start recording. Open Ever Listen and tap **Start now** to begin. Notification delivery and presentation depend on your system notification settings.

Microphone permission is required to record audio. iOS schedules are reminders and do not provide unattended automatic recording.

## 隱私說明：待核實，不可直接發布

目前尚無經核准的完整資料處理事實，因此此草稿不宣稱錄音僅儲存在裝置、沒有遙測、不會傳輸資料、已加密或會自動刪除。撰寫對外隱私說明前，須確認並記錄以下事項：

- 錄音、排程與診斷資料的實際儲存位置及備份行為。
- App 是否收集分析、當機或遙測資料，以及是否傳送至任何服務。
- 資料保留期限、單檔／全部刪除行為，以及 App 移除後的處理方式。
- 麥克風、通知及其他權限各自用於哪些功能。
- 隱私政策負責人、聯絡管道及適用的法務審核要求。

目前可從倉庫確認，iOS 麥克風權限說明文字指出錄音功能會使用麥克風；但這不足以推斷完整的資料收集、傳輸、保存或刪除政策。核實前，商店隱私欄位應由發布負責人依據實際 App 行為另行填寫。

## 發布前待確認

- 倉庫尚無獨立 PRD、正式商店欄位或經核准的隱私政策；此文件先作為 repository 草稿與文案來源。
- README 使用產品名稱 **Ever Listen**，而 iOS Info.plist 目前顯示名稱為 **Flutter Voice Recorder**。發布前須確認正式商店名稱。
- 發布負責人須依目標地區的商店欄位、字數限制、地區及隱私問卷逐項審核；本草稿尚未完成這些檢查，也不構成法律意見。

## 倉庫行為依據

- [根目錄 README](../README.md)：目前平台行為摘要。
- [Android ScheduleStore](../flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/ScheduleStore.kt)：非精確鬧鐘的權限檢查與降級邏輯。
- [Android ScheduleRecorderReceiver](../flutter_voice_recorder/android/app/src/main/kotlin/com/seikochang/ever_listen/ScheduleRecorderReceiver.kt)：排程觸發時啟動／停止錄音服務，並在結束後續排週期排程。
- [iOS RecorderPlugin](../flutter_voice_recorder/ios/Runner/RecorderPlugin.swift) 與 [AppDelegate](../flutter_voice_recorder/ios/Runner/AppDelegate.swift)：排程通知送達與開啟事件。
- [Flutter UI](../flutter_voice_recorder/lib/main.dart)：iOS 提醒說明與 **Start now** 操作。
- [iOS Info.plist](../flutter_voice_recorder/ios/Runner/Info.plist)：麥克風權限說明文字。
