# Ever Listen 改善與強化計畫

更新日期：2026-09-21

## 1. 執行摘要

目前專案已具備 Android 原生錄音、VAD、儲存上限、排程，以及 iOS 對應實作的雛形；Flutter 分析與測試亦可通過。然而，現況仍不適合直接上架或對外承諾「可靠的背景定時錄音」。主要原因如下：

1. **iOS 專案目前不是可驗證的完整產品**：`RecorderPlugin.swift` 與 `RecorderPluginTests.swift` 未加入 Xcode target；本機也只有 Command Line Tools，尚未完成真實 iOS build/test。
2. **iOS VAD 音訊格式不正確**：輸入通常是 44.1/48 kHz，C wrapper 卻固定當作 16 kHz 處理，可能造成偵測準確度與 frame 長度錯誤。
3. **iOS BGTaskScheduler 無法保證準點執行**：Apple 由系統決定背景工作執行時機，因此不可把它包裝成可靠的準點錄音功能。
4. **Android 錄音生命週期仍有資料與資源風險**：VAD 觸發 frame 可能被重複寫入；讀取或停止失敗時可能殘留前景服務、wake lock 或錯誤狀態。
5. **產品閉環尚未完成**：使用者能開始錄音，卻缺少錄音清單、播放、匯出、刪除與明確的失敗復原，商業價值難以落地。

### 建議產品方向

短期建議採用 **Android-first、local-first、privacy-first 的「語音觸發錄音器」**：先讓 Android 版達到可信賴的技術 Beta；iOS 第一版只承諾使用者明確啟動後的錄音與背景延續，排程則標示為「系統允許時執行」或暫不提供。等核心可靠度、留存與付費意願成立後，再投資雲端同步、轉錄與搜尋。

## 2. 評估方式

| 欄位 | 定義 |
|---|---|
| 優先級 P0 | 上架阻斷、資料損失、核心承諾不成立、法規或平台政策風險 |
| 優先級 P1 | MVP 必備，直接影響使用者是否能完成主要工作 |
| 優先級 P2 | 提升品質、留存、維運效率與差異化 |
| 優先級 P3 | 有價值但應等待核心產品與商業模式驗證 |
| 商業價值 | 1（低）至 5（極高），綜合營收、留存、信任與風險降低 |
| 難度 | S：1–3 天；M：3–10 天；L：2–4 週；XL：跨月或需產品重設計 |

工期為單一熟悉 Flutter 與原生平台工程師的粗估，未含 App Store/Play 審查等待時間。

## 3. 已驗證的專案現況

| 範圍 | 現況 | 判定 |
|---|---|---|
| Flutter | `flutter analyze` 無問題；9 個測試通過 | 基礎健康，但測試範圍很小 |
| Android | 有 8 個 Kotlin 測試檔，涵蓋 buffer、writer、storage、schedule、VAD、receiver、plugin、service | 測試方向正確，仍缺真實音訊、前景服務與排程整合測試 |
| iOS | Swift plugin、VAD wrapper 與測試檔存在 | `RecorderPlugin.swift` 未加入 Runner Sources，測試檔未加入 RunnerTests Sources；不能視為已完成 |
| iOS 工具鏈 | `xcode-select` 指向 Command Line Tools，無法執行完整 `xcodebuild` | iOS build/test 尚未驗證 |
| CI | `.github/workflows/flutter-ci.yml` 只執行 `flutter pub get` 與 `flutter analyze` | 測試、Android 原生 build/test、iOS build/test 都沒有 gate |
| 文件 | README 仍接近 Flutter 範本；AGENT/TODO 曾高估 iOS 與 coverage 狀態 | 新成員與商業決策可能被誤導 |
| 發行 | Android release 使用 debug signing | 不可作正式發行流程 |

## 4. 優先改善清單

### P0：上架與核心可信度阻斷

| 項目 | 商業價值 | 難度 | 問題與建議 | 完成條件 |
|---|---:|---:|---|---|
| 修正 iOS target membership | 5 | S | 把 `RecorderPlugin.swift` 加入 Runner Sources、`RecorderPluginTests.swift` 加入 RunnerTests Sources，確認 bridging header 與 C sources | `xcodebuild` 可編譯，RunnerTests 實際執行，Flutter iOS simulator build 通過 |
| 修正 iOS 音訊轉換與 VAD | 5 | M | 用 `AVAudioConverter` 統一轉為 mono PCM16 16 kHz，再以 160/320/480 samples（10/20/30 ms）送入 WebRTC VAD | 44.1/48 kHz 輸入皆通過測試；無 unsupported frame；以固定音檔驗證偵測結果 |
| 重定義 iOS 排程產品承諾 | 5 | L | BGTaskScheduler 只能 best effort，不能承諾準點。第一版應改為提醒＋使用者啟動，或清楚顯示「系統允許時執行」；不要用 BG task 長時間等待至錄音結束 | PRD、UI 文案與實作一致；不再聲稱準點無人值守錄音；失敗與延遲可見 |
| 修復 Android RecorderService 生命週期 | 5 | M | VAD 觸發時 current frame 同時存在於 pre-roll 與當前寫入，可能重複；read/stop/初始化失敗時 cleanup 不完整 | 所有出口都在 `finally` 或單一 shutdown 路徑釋放 AudioRecord、writer、wake lock、foreground 與狀態；補回歸測試 |
| 建立正式簽署與 release gate | 5 | S–M | release 目前使用 debug key，無法安全交付 | signing 由 CI secret/本機安全設定注入；`flutter build appbundle --release` 可重現；不提交金鑰 |
| 隱私、同意與資料生命週期 | 5 | M | 長時間錄音屬高信任功能，需要明確麥克風用途、錄音指示、保留期、刪除與匯出；避免在未驗證前加入雲端 | 首次使用說明、權限拒絕復原、錄音中常駐指示、一鍵刪除全部、隱私政策與商店揭露完成 |

### P1：形成可用、可收費的 MVP

| 項目 | 商業價值 | 難度 | 建議 |
|---|---:|---:|---|
| 錄音資料庫與檔案庫 | 5 | L | 提供錄音清單、長度、大小、建立時間、來源模式、播放、重新命名、分享、刪除；檔案與 metadata 需可復原 |
| 統一錄音狀態機 | 5 | M | 定義 idle / starting / recording / stopping / interrupted / error；命令可重入且具 session ID，避免 UI 與 native 狀態分裂 |
| Flutter 錯誤與生命週期處理 | 4 | M | 保存並取消 event subscription；捕捉 PlatformException；App resume 時呼叫 `getStatus()`；提供重試與設定頁導引 |
| 排程時區、DST 與衝突規則 | 5 | M–L | 不再用固定 24h/7d 毫秒推進；保存 IANA timezone 與 local wall-clock；定義重疊、錯過、重開機、權限取消後的行為 |
| Android exact alarm 完整處理 | 4 | M | 只在使用者真正要求準點時使用 exact alarm；檢查權限、處理權限狀態變更、重新排程，否則採 inexact/WorkManager |
| 錄音分段與壓縮 | 5 | L | WAV 16 kHz mono 16-bit 約 1.92 MB/分鐘，200 MB 僅約 104 分鐘。改用 AAC/Opus 或提供格式選項，並每 15–60 分鐘安全 rotate |
| 中斷與路由變更處理 | 4 | M | iOS 處理 interruption、route change、media services reset；Android 處理 audio focus、裝置切換與服務被系統終止 |
| 持久化狀態與事件 | 4 | M | EventChannel 只在 Flutter engine 存活時有效；將最後狀態、錯誤與完成事件寫入可靠儲存，App 回來後可補讀 |
| CI 擴充 | 5 | M | PR 必跑 format/check、Flutter test、Android unit test、Android debug/release build；macOS job 跑 iOS simulator build/test |

### P2：品質、留存與維運

| 項目 | 商業價值 | 難度 | 建議 |
|---|---:|---:|---|
| 錄音可靠度觀測 | 4 | M | 收集不含音訊內容的 session 成功率、失敗原因、被中斷率、排程延遲、檔案損壞率；需允許 opt-out |
| 電量、容量與 VAD 品質指標 | 4 | M–L | 建立 1/8/24 小時耗電基準、不同噪音環境 false positive/negative、storage prune 正確性 |
| 崩潰後檔案修復 | 4 | M | WAV header 可能未 finalize；採暫存檔＋原子 rename，啟動時掃描與修復或標記損壞檔 |
| 安全儲存與備份策略 | 4 | M | iOS 設定適當 Data Protection；評估是否排除備份。Android 明確定義 app-private/no-backup/加密策略 |
| 可及性與在地化 | 3 | M | 補 Semantic labels、Dynamic Type、對比、繁中/英文；時間與時區顯示依 locale |
| 模組化 native 實作 | 3 | L | 將大類別拆成 RecorderEngine、StateMachine、Scheduler、Storage、PermissionManager，加入 dependency injection |
| 設定與預估 | 3 | M | 提供 sensitivity、pre-roll、分段長度、保留空間設定，並即時顯示可錄時間預估 |
| Android OEM 實機矩陣 | 4 | L | Pixel、Samsung、小米等測試鎖屏、Doze、重開機、權限回收與長時間錄音；結果納入支援聲明 |

### P3：確認需求後再做

| 項目 | 商業價值 | 難度 | 啟動條件 |
|---|---:|---:|---|
| 端對端加密雲端同步 | 4 | XL | 核心錄音留存成立、使用者明確需要多裝置，且完成資安與刪除政策 |
| 轉錄、摘要與搜尋 | 4 | XL | 有足夠活躍使用者、單位經濟可接受、同意與敏感資料處理流程完成 |
| 訂閱與用量方案 | 5 | L | Beta 顯示穩定週留存與明確付費意願；先避免以不可靠的 iOS 排程作為付費賣點 |
| 團隊/案件管理 | 3 | XL | 個人使用情境已驗證，再評估共享、權限、稽核與法遵成本 |

## 5. 平台別具體修復

### 5.1 Android

1. **RecorderService 單一 shutdown 路徑**
   - 將正常停止、AudioRecord read error、初始化失敗與例外統一導向 idempotent cleanup。
   - 只有在實際刪除成功後才更新 storage 統計與送出 `storagePruned`。
   - 即使尚未進入 recording，也要能處理 STOP intent 並 `stopSelf()`。

2. **修正 pre-roll 重複 frame**
   - 先判斷觸發與 flush，再決定是否把 current frame 加入 ring buffer，或 flush 後不要再寫同一 frame。
   - 測試應以可辨識 sample pattern 逐 byte 驗證輸出，不只驗長度。

3. **排程語意與持久化**
   - 「schedule」應是觸發方式，不應同時代表錄音策略；Schedule model 另加 `captureMode: detect | monitor`。
   - SharedPreferences 的 `|` 字串格式改為 JSON 或 Room，並加入 schema version。
   - PendingIntent 不依賴單純 `hashCode()`，改用持久化唯一整數。
   - 開機時若目前仍在排程有效區間，需依產品規則立即啟動或明確記錄 missed，不可直接跳到下一次。

4. **平台現代化**
   - 依 Flutter/AGP 官方遷移指引移除已棄用的 `android.builtInKotlin=false`、`android.newDsl=false` 與不必要的 Kotlin Android plugin。
   - 鎖定並記錄 JDK、Flutter、AGP、NDK 版本，在乾淨環境重建 native artifacts。

### 5.2 iOS

1. **先恢復可建置真相**
   - 安裝完整 Xcode，執行 `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`、接受 license、下載 simulator runtime。
   - 修正 `.pbxproj` target membership 後，再判定 Swift/C bridge 與單元測試是否真正可用。

2. **音訊 pipeline**
   - AVAudioEngine input → AVAudioConverter → mono PCM16 16 kHz → 20 ms frame → VAD。
   - 錄音輸出與 VAD 分流；不要假設硬體輸入格式就是 VAD 格式。
   - 補上 pre-roll、固定分段、寫入失敗回報與 crash-safe finalize。

3. **BGTaskScheduler 重新定位**
   - BG task 只做短期維護或「有機會執行」的工作，不應啟動後以 timer 占住整段排程。
   - task registration 放在 app launch 的正確位置；避免 expiration handler 與正常完成重複呼叫 completion。
   - 若商業需求是準點錄音，iOS 應改為通知使用者啟動，或明確限制平台支援程度。

4. **API 對稱與錯誤處理**
   - Dart/Android 已有 `requestPermissions`，iOS MethodChannel 必須補齊或移除共同 API。
   - 移除重要路徑的 `try?` 靜默吞錯；回傳可處理的 error code 並保留診斷資料。
   - schedule save 與 submit 要有交易語意；submit 失敗不得留下看似有效的排程。

### 5.3 Flutter

1. 用 typed model 取代 `Map<String, dynamic>` 與 magic strings；可評估 Pigeon 產生跨平台介面。
2. 將 demo 單頁拆成錄音控制、錄音庫、排程、設定與權限 onboarding。
3. 所有 async 操作顯示 loading/error；避免重複點擊；dispose 時取消 subscription。
4. App 啟動與 resume 都從 native durable state 重建畫面，不依賴 `_monitoring` 這類記憶體旗標。
5. 時區使用 IANA ID，而不是可能模糊的 `CST` 等縮寫。

## 6. 建議的 8 週執行路線

| 週次 | 目標 | 可交付成果 |
|---|---|---|
| 第 1 週 | 建置真相與阻斷修復 | iOS target 接線、完整 Xcode build/test；Android cleanup 與 pre-roll 修復；CI 開始跑 tests |
| 第 2 週 | 音訊正確性 | iOS 16 kHz converter；兩平台固定音檔 VAD 測試；crash-safe 分段寫檔 |
| 第 3–4 週 | MVP 產品閉環 | 錄音索引、清單、播放、分享、刪除；權限與錯誤 UX；狀態持久化 |
| 第 5 週 | 排程可靠度 | timezone/DST、衝突、重開機、權限狀態；iOS 改為 best-effort/提醒模式 |
| 第 6 週 | 隱私與發行 | privacy flow、資料保護、release signing、商店素材與政策檢查 |
| 第 7 週 | 長時間與 OEM 測試 | 8/24 小時錄音、低容量、Doze、來電/耳機/route change、崩潰復原 |
| 第 8 週 | 封閉 Beta | 20–50 位目標使用者、問題分級、核心指標與 go/no-go 決策 |

若只有一位工程師，建議先做 Android 1–6 週範圍，iOS 作為獨立里程碑，總時程抓 10–14 週較合理。

## 7. 商業驗證與指標

### MVP 客群假設

優先驗證需要長時間、免手持、可離線錄音的使用者，例如田野紀錄、個人語音筆記、照護環境事件紀錄。避免在未完成法律審查前宣傳監聽他人或隱蔽錄音。

### 北極星與品質指標

| 類型 | 指標 | Beta 建議門檻 |
|---|---|---|
| 核心成功 | 成功產生且可播放的錄音 session | ≥ 99% |
| 資料安全 | 無使用者操作下的檔案遺失率 | 0；任何案例皆列 P0 |
| 排程 | Android 準點成功率與延遲 p95 | 依裝置分群；先達 ≥ 95%，並公開限制 |
| 穩定性 | crash-free sessions | ≥ 99.5% |
| VAD | false positive/negative | 以安靜、車內、街道、會議室測試集建立基準，不用主觀感受判定 |
| 效率 | 每小時耗電與儲存量 | 先建立基準，再設定裝置級門檻 |
| 商業 | D7 留存、每週有效錄音次數、匯出率 | 用來判斷是否值得做雲端與訂閱 |

### 初步付費設計

- 免費：手動錄音、基本 VAD、有限歷史或儲存。
- Pro：多排程、進階 sensitivity/pre-roll、較長保留、批次匯出與進階格式。
- 不建議一開始以雲端容量為主：會立即增加隱私、資安、成本與刪除義務。
- 不建議把 iOS 準點背景排程列為 Pro 賣點，因平台本身不能保證。

## 8. 發行 Gate

正式 Beta 前必須同時滿足：

- Flutter analyze/test、Android unit test/build、iOS simulator build/test 全部由 CI 執行。
- 至少一次乾淨環境 release build；Android 不使用 debug signing。
- 連續 8 小時錄音、容量滿、權限拒絕、App 被殺、裝置重開機與中斷案例均有結果。
- 任何結束路徑都不殘留麥克風、foreground service、wake lock 或未完成 BG task。
- 錄音可被列出、播放、匯出與刪除；刪除後 metadata 與實體檔一致。
- iOS 排程 UI 清楚揭露 best-effort；Android exact alarm 權限與降級策略可理解。
- 隱私政策、權限用途、錄音指示、資料保留與刪除流程通過人工檢查。
- README、AGENT、TODO 與實際 build/test 狀態一致；移除估算式 coverage，改用 CI 產生的報告。

## 9. 建議立即建立的前 10 張工作票

1. **P0 — 將 iOS plugin/test 加入 Xcode targets 並建立 simulator build gate。**
2. **P0 — 完成 iOS 16 kHz mono PCM converter 與 deterministic VAD tests。**
3. **P0 — 重構 Android RecorderService cleanup，修復 pre-roll duplicate frame。**
4. **P0 — 決定並文件化 iOS schedule 的 best-effort 產品語意。**
5. **P0 — 建立 Android release signing 與 release CI artifact。**
6. **P1 — 建立 recording metadata schema、檔案掃描與 crash recovery。**
7. **P1 — 實作 Flutter 錄音庫：播放、分享、刪除、容量顯示。**
8. **P1 — 修正 recurrence 的 timezone/DST、missed window 與 overlap policy。**
9. **P1 — 建立跨平台 recorder state machine 與 durable event journal。**
10. **P1 — 建立長時間、低容量、重開機、中斷與權限回收測試矩陣。**

## 10. 暫不建議投入

- 在錄音可靠度與檔案庫完成前做 UI 大改版。
- 在同意、加密、刪除與成本模型完成前上傳音訊到雲端。
- 在真實使用資料不足前自行訓練 VAD/語音模型。
- 為了表面上的跨平台一致，宣稱 iOS 與 Android 具備相同的準點背景排程能力。
- 以未實際執行的測試檔數量或人工估計 coverage 作為完成依據。

## 11. 平台限制參考

- Apple：[`BGTaskRequest.earliestBeginDate`](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate) 只代表「不早於」指定時間，系統不保證在該時間執行。
- Apple：[`Choosing Background Strategies for Your App`](https://developer.apple.com/documentation/BackgroundTasks/choosing-background-strategies-for-your-app) 說明背景工作的執行時機由系統決定。
- Apple：[`AVAudioSession`](https://developer.apple.com/documentation/avfaudio/avaudiosession) 與 [record category](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/record) 應作為背景錄音、中斷與 audio session 行為的實作依據。
- Android：[`Schedule alarms`](https://developer.android.com/develop/background-work/services/alarms) 建議只為使用者可感知且需要精確時間的功能使用 exact alarm，並處理權限與重新排程。
- Android：[`Android 14 behavior changes`](https://developer.android.com/about/versions/14/behavior-changes-all) 說明多數新安裝 App 的 exact alarm 權限預設不核准。

---

本計畫的排序原則是：**先證明不遺失錄音、狀態可信且平台承諾成立，再增加功能與營收層。**
