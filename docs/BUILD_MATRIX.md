# 建置工具矩陣

本文件記錄目前 repository 的建置來源與驗證狀態，避免本機與 CI 使用未宣告的工具版本。

| 項目 | 目前設定/觀察值 | 來源 | 狀態 |
|---|---|---|---|
| Flutter / Dart | CI 固定 Flutter `3.41.4` stable（本機已確認 Dart `3.11.1`）；`pubspec.yaml` 要求 Dart `>=3.0.0 <4.0.0` | `.github/workflows/flutter-ci.yml`, `pubspec.yaml` | Flutter/Dart gates 已宣告，待 CI 實際通過 |
| Android Gradle Plugin | `9.0.1` | `android/settings.gradle.kts` | 已固定 |
| Gradle wrapper | `9.1.0-all` | `android/gradle/wrapper/gradle-wrapper.properties` | 已固定 |
| Kotlin | 使用 AGP 9 built-in Kotlin；不再套用 Kotlin Android plugin | `android/app/build.gradle.kts` | 已完成 migration，待 Gradle CI 驗證 |
| Java | CI 使用 Temurin 17；本機建議使用 Android Studio JBR 17 | `.github/workflows/flutter-ci.yml` | CI 已宣告 |
| Android SDK / compile SDK | 由 Flutter Gradle extension 的 `flutter.compileSdkVersion` 提供 | `android/app/build.gradle.kts` | 依 Flutter SDK 解析 |
| Android NDK | 由 `flutter.ndkVersion` 提供 | `android/app/build.gradle.kts` | 依 Flutter SDK 解析，尚未建立 clean-machine gate |
| iOS | macOS CI 執行 simulator build 與 RunnerTests；本機完整 Xcode 驗證目前不可用 | `.github/workflows/flutter-ci.yml` | 待 GitHub Actions 通過 |

## 使用方式

Android unit tests：

```sh
cd flutter_voice_recorder/android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test --no-daemon --console=plain
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
