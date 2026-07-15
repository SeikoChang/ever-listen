# Ever Listen 手動測試教學

這份文件說明如何在本機設定環境，並用 Android 實體裝置、Android Studio 模擬器、iOS 實體裝置與 iOS Simulator 手動測試 Ever Listen。

目前專案狀態仍是 Flutter app 加原生錄音功能開發中。Dart 層與 MethodChannel wrapper 可以用 `flutter test` 驗證；Android/iOS 原生錄音功能需要完整平台工程、權限與裝置環境才適合做實機驗證。

## 1. 測試前準備

### 1.1 必要工具

請先安裝：

- Flutter SDK
- Android Studio
- Xcode
- CocoaPods
- Git
- 一台 Android 實體裝置
- 一台 iPhone 或 iPad

macOS 上可以先確認工具是否可用：

```bash
flutter --version
flutter doctor
git --version
xcodebuild -version
pod --version
```

`flutter doctor` 應至少通過 Flutter、Android toolchain、Xcode 與 connected device 相關檢查。若有缺少 Android SDK、iOS signing、CocoaPods 或 Xcode command line tools，先依照 `flutter doctor` 的提示修正。

### 1.2 取得專案並安裝 Flutter dependencies

```bash
git clone git@github.com:SeikoChang/ever-listen.git
cd ever-listen/flutter_voice_recorder
flutter pub get
```

先確認 Dart/Flutter 層測試可以通過：

```bash
flutter analyze
flutter test
```

預期結果：

- `flutter analyze` 顯示 `No issues found`
- `flutter test` 顯示所有測試通過

### 1.3 目前手動測試範圍

建議手動測試以下功能：

- App 是否能啟動
- Detect / Monitor / Schedule 三個模式 UI 是否正常切換
- Sensitivity slider 是否可調整
- Max storage input 是否可輸入
- Start / Stop 是否能呼叫原生錄音流程
- Schedule 是否能建立、顯示、刪除
- Android foreground recording notification 是否出現
- 麥克風權限拒絕時是否能看到錯誤狀態
- 錄音檔是否出現在 app external files 的 `recordings/` 目錄

重要限制：

- Android schedule 目前使用 `AlarmManager` 作為 dependency-free bridge；若之後補齊 Gradle/WorkManager，需重新測試 WorkManager 行為。
- iOS 原生錄音與排程仍是 skeleton；目前 iOS 主要測 Flutter UI 與基本 app 啟動，原生錄音結果不應視為完成。
- Android emulator 可測 UI 與部分錄音流程，但 VAD/背景錄音/電池與 Doze 行為必須用實體裝置驗證。

## 2. Android Studio 模擬器測試

使用 Android Studio 建立模擬器可以快速檢查 UI、MethodChannel 呼叫與基本權限流程。

### 2.1 建立模擬器

1. 開啟 Android Studio。
2. 進入 `Device Manager`。
3. 點 `Create Device`。
4. 選一台常見手機，例如 Pixel 系列。
5. 選 Android API 版本。建議至少測：
   - API 28
   - API 31 或以上
   - API 34 或以上
6. 建立並啟動模擬器。

確認 Flutter 看得到模擬器：

```bash
flutter devices
```

### 2.2 在模擬器啟動 app

```bash
cd ever-listen/flutter_voice_recorder
flutter run
```

若有多個裝置，先查 device id：

```bash
flutter devices
```

再指定裝置：

```bash
flutter run -d <device-id>
```

### 2.3 模擬器測試項目

#### App 啟動

1. App 開啟後應看到 `Ever Listen`。
2. 預設模式應為 `Detect`。
3. 確認 `Sensitivity` slider 可拖動。
4. 確認 `Max storage (MB)` 可輸入數字。

#### Detect 模式

1. 選 `Detect`。
2. 點 `Start`。
3. Android 權限對話框出現時選允許麥克風。
4. 對模擬器輸入音訊或使用主機麥克風。
5. 觀察狀態是否變成 `recording` 或收到事件文字。
6. 點 `Stop`。

預期結果：

- App 不應 crash。
- 若模擬器音訊可用，狀態可能出現 `speechStarted`、`speechEnded`、`fileReady`。
- 若模擬器音訊不可用，應看到錯誤狀態，不應卡死。

#### Monitor 模式

1. 選 `Monitor`。
2. 點 `Start`。
3. 確認 foreground notification 出現。
4. 等待 10 到 20 秒。
5. 點 `Stop`。

預期結果：

- App 不應 crash。
- Android notification 應顯示錄音進行中。
- 停止後 notification 應消失。

#### Schedule 模式

1. 選 `Schedule`。
2. 點 `Start` row，設定開始時間為 1 到 2 分鐘後。
3. 點 `End` row，設定結束時間為開始後 2 到 5 分鐘。
4. 選 `Once`。
5. 點 `Add`。
6. 確認 schedule list 出現新增項目。
7. 點刪除圖示，確認項目移除。

再測一次不刪除：

1. 建立一筆即將開始的 schedule。
2. 等到開始時間。
3. 觀察 notification 與狀態。
4. 等到結束時間。
5. 確認錄音停止。

注意：模擬器對 exact alarm、背景執行與麥克風行為不一定代表實體裝置。

## 3. Android 實體裝置測試

Android 實機是主要驗證目標，尤其是麥克風、foreground service、背景錄音、排程與儲存空間。

### 3.1 設定 Android 裝置

1. 開啟手機 `Settings`。
2. 進入 `About phone`。
3. 連點 `Build number` 七次啟用 Developer options。
4. 回到設定，進入 `Developer options`。
5. 開啟 `USB debugging`。
6. 用 USB 連接電腦。
7. 手機跳出 RSA fingerprint 授權時點允許。

確認 adb 與 Flutter 看得到裝置：

```bash
adb devices
flutter devices
```

若 `adb devices` 顯示 `unauthorized`，重新插拔 USB 並在手機上允許授權。

### 3.2 安裝並啟動

```bash
cd ever-listen/flutter_voice_recorder
flutter run -d <android-device-id>
```

若要看 Android log：

```bash
adb logcat | grep RecorderService
```

或看 plugin log：

```bash
adb logcat | grep ever_listen
```

### 3.3 權限測試

#### 允許麥克風

1. 第一次按 `Start`。
2. 系統要求麥克風權限時選允許。
3. 確認錄音可開始。

#### 拒絕麥克風

1. 卸載 app 或到系統設定清除權限。
2. 重新啟動 app。
3. 按 `Start`。
4. 權限要求出現時選拒絕。

預期結果：

- App 不應 crash。
- 狀態應能反映錯誤或無法開始錄音。

### 3.4 Detect 模式實機測試

1. 選 `Detect`。
2. Sensitivity 設為 `0.6`。
3. Max storage 設為 `200`。
4. 點 `Start`。
5. 先保持安靜 5 秒。
6. 說一段 5 到 10 秒的話。
7. 停止說話，等待 2 秒。
8. 點 `Stop`。

預期結果：

- 說話時狀態應出現 speech-related event。
- 停止說話後應產生 `fileReady`。
- 檔案應寫入 app 的 external files `recordings/` 目錄。

可以用 adb 檢查錄音檔：

```bash
adb shell run-as com.seikochang.ever_listen ls -R files
```

若 package name 不同，請用實際 Android application id 替換 `com.seikochang.ever_listen`。

### 3.5 Monitor 模式實機測試

1. 選 `Monitor`。
2. 點 `Start`。
3. 確認 foreground notification 出現。
4. 按 Home 將 app 放到背景。
5. 等待 1 到 2 分鐘。
6. 回到 app。
7. 點 `Stop`。

預期結果：

- 背景期間 notification 持續存在。
- app 回到前景後仍可停止錄音。
- 至少產生一個 monitor WAV chunk。

進一步測試：

- 鎖螢幕後錄 1 到 2 分鐘。
- 切換到其他 app 後錄 1 到 2 分鐘。
- 低電量模式下測一次。

### 3.6 Schedule 模式實機測試

1. 選 `Schedule`。
2. 設定開始時間為目前時間 2 分鐘後。
3. 設定結束時間為開始後 3 分鐘。
4. Repeat 選 `Once`。
5. 點 `Add`。
6. 按 Home，讓 app 到背景。
7. 等待開始時間。
8. 確認 notification 出現。
9. 等待結束時間。
10. 確認 notification 消失。
11. 回到 app，確認狀態與 schedule list。

Recurring 測試：

- Daily：建立 daily schedule 後，先確認今日觸發；隔日再確認是否 reschedule。
- Weekly：建立 weekly schedule 後，確認 list 保留且下次時間正確。

注意：Android 12 以上 exact alarm 權限可能影響準時觸發。若 schedule 沒有準時啟動，檢查系統設定中的 alarm 權限與電池最佳化。

### 3.7 儲存空間測試

1. Max storage 設成小值，例如 `1` MB。
2. 用 `Monitor` 模式錄幾分鐘。
3. 停止錄音。
4. 檢查舊檔是否被清掉。

預期結果：

- 總錄音檔大小應接近或低於設定值。
- 狀態可能出現 `storagePruned` 事件。

## 4. iOS Simulator 測試

iOS Simulator 適合測 Flutter UI、基本頁面流程與 schedule form。它不適合驗證真實麥克風、背景錄音與 iOS background audio 行為。

### 4.1 啟動 Simulator

```bash
open -a Simulator
flutter devices
```

或從 Xcode：

1. 開啟 Xcode。
2. 選 `Xcode > Open Developer Tool > Simulator`。
3. 選擇一台 iPhone simulator。

### 4.2 在 Simulator 啟動 app

```bash
cd ever-listen/flutter_voice_recorder
flutter run -d <ios-simulator-id>
```

### 4.3 Simulator 測試項目

1. App 是否正常啟動。
2. Detect / Monitor / Schedule segmented control 是否可切換。
3. Sensitivity slider 是否可拖動。
4. Max storage 是否可輸入。
5. Schedule start/end picker 是否可開啟。
6. Repeat 是否可切換。
7. Add schedule 是否能更新畫面。
8. Delete schedule 是否能移除項目。

預期結果：

- UI 不應 crash。
- 目前 iOS 原生 plugin 尚未完整實作，按 Start 後的錄音結果不作為通過標準。

## 5. iOS 實體裝置測試

iOS 實體裝置測試需要 Apple Developer signing。若只是本機開發，可以使用個人 Apple ID 進行 development signing。

### 5.1 Xcode 與 signing 設定

1. 用 Xcode 開啟 `flutter_voice_recorder/ios`。
2. 選擇 Runner target。
3. 進入 `Signing & Capabilities`。
4. 選擇你的 Team。
5. 確認 Bundle Identifier 唯一。
6. 接上 iPhone。
7. 在 Xcode 選擇實體 iPhone 作為 run destination。

若需要背景錄音，之後要加入：

- `Background Modes`
- 勾選 `Audio, AirPlay, and Picture in Picture`
- `Info.plist` 加入 microphone usage description

目前 iOS 原生錄音還是 skeleton，因此這些是後續完整測試前的必要設定。

### 5.2 用 Flutter 啟動 iOS 實體裝置

```bash
cd ever-listen/flutter_voice_recorder
flutter devices
flutter run -d <ios-device-id>
```

第一次安裝時，iPhone 可能需要：

1. 到 `Settings > General > VPN & Device Management`。
2. 信任你的 development certificate。
3. 重新執行 `flutter run`。

### 5.3 iOS 實機測試項目

目前可測：

- App 啟動
- UI 流程
- Schedule form
- 權限提示是否有正確規劃

待 iOS 原生錄音實作後再測：

- 麥克風權限允許/拒絕
- Detect mode VAD
- Monitor mode 背景錄音
- iOS interruption，例如來電、鬧鐘、Siri
- 鎖螢幕錄音
- App 切背景錄音
- iOS background audio indicator
- BGTaskScheduler schedule 行為

## 6. 建議測試矩陣

每次大改版至少跑：

| 平台 | 裝置 | 測試重點 |
| --- | --- | --- |
| Android | Emulator API 28 | UI、基本權限、基本啟動 |
| Android | Emulator API 34+ | foreground service permission、schedule UI |
| Android | Pixel 或其他實機 | Detect、Monitor、Schedule、背景、notification |
| Android | Samsung 或其他 OEM | Doze、電池最佳化、背景限制 |
| iOS | Simulator | UI、schedule form |
| iOS | iPhone 實機 | signing、權限、後續原生錄音 |

## 7. 每次手動測試紀錄範本

建議每次測試都記錄：

```text
Date:
Tester:
Git commit:
Flutter version:
Device:
OS version:

Test items:
- flutter analyze:
- flutter test:
- App launch:
- Detect mode:
- Monitor mode:
- Schedule mode:
- Permission denied:
- Background behavior:
- Storage pruning:

Issues found:
1.
2.

Logs / screenshots:
```

取得目前 commit：

```bash
git rev-parse --short HEAD
```

取得 Flutter 版本：

```bash
flutter --version
```

## 8. 常見問題

### `flutter run` 找不到 Android 裝置

檢查：

```bash
adb devices
flutter devices
```

若顯示 unauthorized，重新插拔 USB，並在手機上允許 debugging。

### Android 按 Start 後沒有錄音

檢查：

- 麥克風權限是否允許
- foreground notification 是否出現
- `adb logcat | grep RecorderService`
- 是否使用模擬器且沒有可用音訊輸入
- AndroidManifest 是否有宣告 microphone 與 foreground service 權限

### Schedule 沒有準時觸發

檢查：

- 裝置時間是否正確
- App 是否有 exact alarm 權限
- 電池最佳化是否限制 app
- 是否在 OEM 自訂省電模式中被限制
- logcat 是否有 receiver 或 service 相關 log

### iOS 無法安裝到實體裝置

檢查：

- Xcode signing team
- Bundle Identifier 是否唯一
- iPhone 是否信任 development certificate
- iOS deployment target 是否支援該裝置

### `flutter test` 失敗

先重跑：

```bash
flutter clean
flutter pub get
flutter test
```

若仍失敗，先確認失敗的是 Dart test、platform channel mock，還是 Flutter SDK/套件版本問題。
