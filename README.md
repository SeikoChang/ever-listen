# Ever Listen

Ever Listen is a Flutter voice recorder with native Android and iOS capture paths.
It supports Detect (VAD), Monitoring, and scheduled recording workflows.

## Current product behavior

- Android uses a foreground microphone service, local WAV output, pre-roll buffering, storage pruning, and AlarmManager schedules.
- iOS uses AVAudioEngine and local notifications for schedule reminders. An iOS reminder never starts recording automatically; the user must tap “Start now”.
- Permission failures expose a path to the platform app settings.
- Android release builds never fall back to debug signing. Credentials are injected through Gradle properties or CI secrets.

## Development

```sh
cd flutter_voice_recorder
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

Android unit tests:

```sh
cd flutter_voice_recorder/android
./gradlew test --no-daemon --console=plain
```

See [docs/BUILD_MATRIX.md](docs/BUILD_MATRIX.md) for the toolchain, signing, and CI expectations.

## Verification limits

The repository has Flutter tests and Android/iOS native unit-test suites. Full Android Gradle execution depends on a working local Gradle/JDK cache, while iOS simulator/Xcode verification runs in the macOS CI job. Physical-device, long-duration, OEM battery, and legal/privacy validation are not covered by automated tests.

## Documentation

- [Enhancement plan](docs/ENHANCE_PLAN.md)
- [Task backlog](TODOs.md)
- [Build matrix](docs/BUILD_MATRIX.md)
- [Chinese manual test plan](docs/manual_testing_zh.md)
- [VAD build instructions](docs/VAD_build_instructions.md)

License: MIT
