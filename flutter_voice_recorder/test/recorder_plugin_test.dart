import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_voice_recorder/src/recorder_plugin.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('ever_listen/recorder');
  final calls = <MethodCall>[];
  late RecorderPlugin recorder;

  setUp(() {
    recorder = RecorderPlugin();
    calls.clear();

    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);

      switch (call.method) {
        case 'getStatus':
          return {
            'running': true,
            'mode': 'detect',
            'sensitivity': 0.7,
            'maxStorageMb': 128,
            'currentFilePath': '/tmp/current.wav',
            'storageUsedMb': 2.5,
            'schedules': <Map<String, Object?>>[],
          };
        case 'scheduleRecording':
          final args = Map<String, Object?>.from(call.arguments as Map);
          return {
            'id': 'schedule-1',
            ...args,
          };
        case 'cancelSchedule':
          return {
            'id': (call.arguments as Map)['id'],
            'cancelled': true,
          };
        case 'getSchedules':
          return [
            {
              'id': 'schedule-1',
              'startTimeMillis': 1000,
              'endTimeMillis': 2000,
              'repeat': 'once',
              'timezone': 'UTC',
              'mode': 'schedule',
              'sensitivity': 0.6,
              'maxStorageMb': 200,
            }
          ];
      }

      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('startRecording sends mode, sensitivity, and storage limit', () async {
    await recorder.startRecording(
      mode: 'monitor',
      sensitivity: 0.8,
      maxStorageMb: 512,
    );

    expect(calls, hasLength(1));
    expect(calls.single.method, 'startRecording');
    expect(calls.single.arguments, {
      'mode': 'monitor',
      'sensitivity': 0.8,
      'maxStorageMb': 512,
    });
  });

  test('stopRecording sends stop method', () async {
    await recorder.stopRecording();

    expect(calls, hasLength(1));
    expect(calls.single.method, 'stopRecording');
    expect(calls.single.arguments, isNull);
  });

  test('setters send expected payloads', () async {
    await recorder.setSensitivity(0.4);
    await recorder.setMaxStorageMb(64);

    expect(calls, hasLength(2));
    expect(calls[0].method, 'setSensitivity');
    expect(calls[0].arguments, {'sensitivity': 0.4});
    expect(calls[1].method, 'setMaxStorageMb');
    expect(calls[1].arguments, {'maxStorageMb': 64});
  });

  test('getStatus returns a typed map', () async {
    final status = await recorder.getStatus();

    expect(status, isNotNull);
    expect(status!['running'], isTrue);
    expect(status['mode'], 'detect');
    expect(status['storageUsedMb'], 2.5);
  });

  test('scheduleRecording sends timestamp and configuration payload', () async {
    final startTime = DateTime.fromMillisecondsSinceEpoch(1000);
    final endTime = DateTime.fromMillisecondsSinceEpoch(2000);

    final schedule = await recorder.scheduleRecording(
      startTime: startTime,
      endTime: endTime,
      repeat: 'daily',
      timezone: 'Asia/Taipei',
      mode: 'schedule',
      sensitivity: 0.75,
      maxStorageMb: 300,
    );

    expect(calls, hasLength(1));
    expect(calls.single.method, 'scheduleRecording');
    expect(calls.single.arguments, {
      'startTimeMillis': 1000,
      'endTimeMillis': 2000,
      'repeat': 'daily',
      'timezone': 'Asia/Taipei',
      'mode': 'schedule',
      'sensitivity': 0.75,
      'maxStorageMb': 300,
    });
    expect(schedule['id'], 'schedule-1');
    expect(schedule['repeat'], 'daily');
  });

  test('cancelSchedule returns cancellation result', () async {
    final cancelled = await recorder.cancelSchedule('schedule-1');

    expect(cancelled, isTrue);
    expect(calls, hasLength(1));
    expect(calls.single.method, 'cancelSchedule');
    expect(calls.single.arguments, {'id': 'schedule-1'});
  });

  test('getSchedules returns typed schedule maps', () async {
    final schedules = await recorder.getSchedules();

    expect(schedules, hasLength(1));
    expect(schedules.single['id'], 'schedule-1');
    expect(schedules.single['repeat'], 'once');
  });
}
