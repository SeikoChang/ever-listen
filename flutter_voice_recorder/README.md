# Ever Listen Flutter app

This directory contains the Flutter UI and MethodChannel wrapper for Ever Listen's native recorder implementations.

## Run locally

```sh
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter run
```

The Flutter layer exposes recording, permissions, schedule CRUD, status, native events, and an app-settings recovery action through `lib/src/recorder_plugin.dart`.

## Platform behavior

- Android starts a foreground microphone service and restores schedules after reboot.
- iOS uses a local notification reminder for schedules. The notification does not start recording automatically; the user must start recording manually.
- Native platform builds and hardware behavior are validated separately from these Flutter tests.

See the repository [build matrix](../docs/BUILD_MATRIX.md), [task backlog](../TODOs.md), and [manual test plan](../docs/manual_testing_zh.md).
