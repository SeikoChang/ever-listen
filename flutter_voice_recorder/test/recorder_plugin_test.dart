import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_voice_recorder/src/recorder_plugin.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final RecorderPlugin recorder = RecorderPlugin();
  final List<MethodCall> log = <MethodCall>[];

  // Set up mock method channel handler
  setUp(() {
    log.clear();
    
    // In newer Flutter versions, use TestDefaultBinaryMessengerBinding
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('ever_listen/recorder'),
      (MethodCall methodCall) async {
        log.add(methodCall);
        switch (methodCall.method) {
          case 'startRecording':
            return null;
          case 'stopRecording':
            return null;
          case 'setSensitivity':
            return null;
          case 'setMaxStorageMb':
            return null;
          case 'getStatus':
            return {
              'running': true,
              'mode': 'detect',
              'sensitivity': 0.6,
              'maxStorageMb': 200,
              'currentFilePath': '/path/to/file.wav',
              'storageUsedMb': 12.5,
              'schedules': []
            };
          case 'scheduleRecording':
            return {
              'id': methodCall.arguments['id'] ?? 'test-id',
              'startTimeMillis': methodCall.arguments['startTimeMillis'],
              'endTimeMillis': methodCall.arguments['endTimeMillis'],
              'repeat': methodCall.arguments['repeat'],
              'timezone': methodCall.arguments['timezone'],
              'mode': methodCall.arguments['mode'],
              'sensitivity': methodCall.arguments['sensitivity'],
              'maxStorageMb': methodCall.arguments['maxStorageMb'],
            };
          case 'cancelSchedule':
            return {'cancelled': true, 'id': methodCall.arguments['id']};
          case 'getSchedules':
            return [
              {
                'id': 'schedule-1',
                'startTimeMillis': 1700000000000,
                'endTimeMillis': 1700000300000,
                'repeat': 'once',
                'timezone': 'UTC',
                'mode': 'schedule',
                'sensitivity': 0.6,
                'maxStorageMb': 200,
              }
            ];
          default:
            return null;
        }
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('ever_listen/recorder'),
      null,
    );
  });

  test('startRecording invokes method channel with correct arguments', () async {
    await recorder.startRecording(
      mode: 'monitor',
      sensitivity: 0.8,
      maxStorageMb: 500,
    );

    expect(log, hasLength(1));
    expect(log.single.method, 'startRecording');
    expect(log.single.arguments, {
      'mode': 'monitor',
      'sensitivity': 0.8,
      'maxStorageMb': 500,
    });
  });

  test('stopRecording invokes method channel', () async {
    await recorder.stopRecording();

    expect(log, hasLength(1));
    expect(log.single.method, 'stopRecording');
    expect(log.single.arguments, null);
  });

  test('setSensitivity invokes method channel', () async {
    await recorder.setSensitivity(0.5);

    expect(log, hasLength(1));
    expect(log.single.method, 'setSensitivity');
    expect(log.single.arguments, {'sensitivity': 0.5});
  });

  test('setMaxStorageMb invokes method channel', () async {
    await recorder.setMaxStorageMb(100);

    expect(log, hasLength(1));
    expect(log.single.method, 'setMaxStorageMb');
    expect(log.single.arguments, {'maxStorageMb': 100});
  });

  test('getStatus returns structured status map', () async {
    final status = await recorder.getStatus();

    expect(log, hasLength(1));
    expect(log.single.method, 'getStatus');
    expect(status, {
      'running': true,
      'mode': 'detect',
      'sensitivity': 0.6,
      'maxStorageMb': 200,
      'currentFilePath': '/path/to/file.wav',
      'storageUsedMb': 12.5,
      'schedules': []
    });
  });

  test('scheduleRecording invokes method channel and returns map', () async {
    final start = DateTime.fromMillisecondsSinceEpoch(1700000000000);
    final end = DateTime.fromMillisecondsSinceEpoch(1700000300000);

    final res = await recorder.scheduleRecording(
      startTime: start,
      endTime: end,
      repeat: 'daily',
      timezone: 'GMT',
      mode: 'schedule',
      sensitivity: 0.7,
      maxStorageMb: 150,
    );

    expect(log, hasLength(1));
    expect(log.single.method, 'scheduleRecording');
    expect(res['startTimeMillis'], 1700000000000);
    expect(res['endTimeMillis'], 1700000300000);
    expect(res['repeat'], 'daily');
    expect(res['timezone'], 'GMT');
    expect(res['mode'], 'schedule');
    expect(res['sensitivity'], 0.7);
    expect(res['maxStorageMb'], 150);
  });

  test('cancelSchedule invokes method channel and returns cancelled status', () async {
    final cancelled = await recorder.cancelSchedule('schedule-1');

    expect(log, hasLength(1));
    expect(log.single.method, 'cancelSchedule');
    expect(log.single.arguments, {'id': 'schedule-1'});
    expect(cancelled, true);
  });

  test('getSchedules returns list of schedules', () async {
    final schedules = await recorder.getSchedules();

    expect(log, hasLength(1));
    expect(log.single.method, 'getSchedules');
    expect(schedules, hasLength(1));
    expect(schedules.first['id'], 'schedule-1');
  });
}
