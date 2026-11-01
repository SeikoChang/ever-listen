# 建置工具矩陣

本文件記錄目前 repository 的建置來源與驗證狀態，避免本機與 CI 使用未宣告的工具版本。

| 項目 | 目前設定/觀察值 | 來源 | 狀態 |
|---|---|---|---|
| Flutter / Dart | CI 固定 Flutter `3.41.4` stable（本機已確認 Dart `3.11.1`）；`pubspec.yaml` 要求 Dart `>=3.0.0 <4.0.0` | `.github/workflows/flutter-ci.yml`, `pubspec.yaml` | Flutter/Dart gates 已宣告，待 CI 實際通過 |
| Android Gradle Plugin | `9.0.1` | `android/settings.gradle.kts` | 已固定 |
| Gradle distribution | `9.1.0-all` | `android/gradle/wrapper/gradle-wrapper.properties` | CI installs `9.1.0`; wrapper launchers are generated for Flutter packaging |
| Kotlin | 使用 AGP 9 built-in Kotlin；不再套用 Kotlin Android plugin | `android/app/build.gradle.kts` | 已完成 migration，待 Gradle CI 驗證 |
| Android native coverage | Debug local unit tests generate an AGP JaCoCo HTML report, uploaded by CI | `.github/workflows/flutter-ci.yml`, `android/app/build.gradle.kts` | 待 GitHub Actions 通過 |
| Java | CI 使用 Temurin 17；本機建議使用 Android Studio JBR 17 | `.github/workflows/flutter-ci.yml` | CI 已宣告 |
| Android SDK / compile SDK | 由 Flutter Gradle extension 的 `flutter.compileSdkVersion` 提供 | `android/app/build.gradle.kts` | 依 Flutter SDK 解析 |
| Android NDK | 由 `flutter.ndkVersion` 提供 | `android/app/build.gradle.kts` | 依 Flutter SDK 解析，尚未建立 clean-machine gate |
| iOS | macOS CI 執行 simulator build、RunnerTests 與 Xcode coverage collection | `.github/workflows/flutter-ci.yml` | 待 GitHub Actions 通過 |

## Native coverage reports

Coverage is collected from the existing native test suites. Android enables AGP unit-test coverage for the debug variant and uploads the HTML JaCoCo report as the `ever-listen-android-native-coverage` workflow artifact. The report covers Kotlin/Java code exercised by local unit tests; it does not cover JNI C/C++ code. The report is generated at `flutter_voice_recorder/build/app/reports/coverage/test/debug/index.html`.

The Android job installs the configured Gradle distribution directly because the repository checkout does not include the ignored Gradle wrapper launchers. After generating the native report, CI creates ephemeral wrapper launchers so the existing Flutter APK and App Bundle build commands can run. The workflow runs on `main`, pull requests targeting `main`, and pushes to `taskboard/**` branches so isolated Taskboard worktrees can receive CI validation without merging.

The iOS simulator test job enables Xcode coverage and uploads the `.xcresult` bundle plus an `xccov` JSON summary as the `ever-listen-ios-native-coverage` artifact. The JSON summary is available at `flutter_voice_recorder/build/coverage/ios-native-coverage.json`; the result bundle contains the detailed Xcode test and coverage data.

These artifacts are produced only when the corresponding tests succeed. They reflect the code exercised by the host/simulator test suite, not physical-device behavior. Android and iOS reports have different formats and scopes; do not combine their percentages or use them as an enforced threshold. GitHub Actions applies the repository's configured artifact retention period.

### Generate locally

Android, with Gradle `9.1.0` on `PATH`, from `flutter_voice_recorder/android`:

```sh
gradle :app:createDebugUnitTestCoverageReport --no-daemon --console=plain
```

This task runs the debug unit-test suite and generates its coverage report.

Open `flutter_voice_recorder/build/app/reports/coverage/test/debug/index.html`.

iOS, from `flutter_voice_recorder` on macOS with Xcode installed:

```sh
mkdir -p build/coverage
xcodebuild test \
  -project ios/Runner.xcodeproj \
  -scheme Runner \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -parallel-testing-enabled NO \
  -enableCodeCoverage YES \
  -resultBundlePath build/coverage/ios-native-coverage.xcresult \
  CODE_SIGNING_ALLOWED=NO
xcrun xccov view --report --json \
  build/coverage/ios-native-coverage.xcresult \
  > build/coverage/ios-native-coverage.json
```

The iOS report requires a successful simulator test run and full Xcode installation.

## 使用方式

Android unit tests：

```sh
cd flutter_voice_recorder/android
gradle :app:createDebugUnitTestCoverageReport --no-daemon --console=plain
```

Flutter checks：

```sh
cd flutter_voice_recorder
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

目前不應把本機 Gradle daemon/cache 的成功或失敗視為 release gate；正式判定以 CI 的乾淨 runner 結果為準。

## Android release signing

`android/app/build.gradle.kts` accepts these Gradle properties:

```text
releaseStoreFile=/secure/path/ever-listen-upload.jks
releaseStorePassword=...
releaseKeyAlias=...
releaseKeyPassword=...
```

本機可透過 `~/.gradle/gradle.properties` 或 `-Pname=value` 注入；CI 應使用 encrypted secrets 與暫存 keystore 檔案。repository 只接受未含憑證的設定，不接受 `.jks`、`.keystore` 或 `key.properties`。
