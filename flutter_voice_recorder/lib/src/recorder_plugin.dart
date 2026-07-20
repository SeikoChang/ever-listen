import 'dart:async';
import 'package:flutter/services.dart';

class RecorderPlugin {
  static const MethodChannel _channel = MethodChannel('ever_listen/recorder');
  static const EventChannel _eventChannel = EventChannel('ever_listen/events');

  Stream<Map<String, dynamic>> get events => _eventChannel
      .receiveBroadcastStream()
      .map((event) => Map<String, dynamic>.from(event as Map));

  Future<void> startRecording({
    String mode = 'detect',
    double sensitivity = 0.6,
    int maxStorageMb = 200,
  }) async {
    await _channel.invokeMethod('startRecording', {
      'mode': mode,
      'sensitivity': sensitivity,
      'maxStorageMb': maxStorageMb,
    });
  }

  Future<void> stopRecording() async {
    await _channel.invokeMethod('stopRecording');
  }

  Future<Map<String, dynamic>> requestPermissions() async {
    final res = await _channel.invokeMethod('requestPermissions');
    return Map<String, dynamic>.from(res as Map);
  }

  Future<void> setSensitivity(double s) async {
    await _channel.invokeMethod('setSensitivity', {'sensitivity': s});
  }

  Future<void> setMaxStorageMb(int mb) async {
    await _channel.invokeMethod('setMaxStorageMb', {'maxStorageMb': mb});
  }

  Future<Map<String, dynamic>?> getStatus() async {
    final res = await _channel.invokeMethod('getStatus');
    if (res == null) return null;
    return Map<String, dynamic>.from(res);
  }

  Future<Map<String, dynamic>> scheduleRecording({
    required DateTime startTime,
    required DateTime endTime,
    String repeat = 'once',
    String timezone = 'UTC',
    String mode = 'schedule',
    double sensitivity = 0.6,
    int maxStorageMb = 200,
  }) async {
    final res = await _channel.invokeMethod('scheduleRecording', {
      'startTimeMillis': startTime.millisecondsSinceEpoch,
      'endTimeMillis': endTime.millisecondsSinceEpoch,
      'repeat': repeat,
      'timezone': timezone,
      'mode': mode,
      'sensitivity': sensitivity,
      'maxStorageMb': maxStorageMb,
    });
    return Map<String, dynamic>.from(res as Map);
  }

  Future<bool> cancelSchedule(String id) async {
    final res = await _channel.invokeMethod('cancelSchedule', {'id': id});
    final data = Map<String, dynamic>.from(res as Map);
    return data['cancelled'] == true;
  }

  Future<List<Map<String, dynamic>>> getSchedules() async {
    final res = await _channel.invokeMethod('getSchedules');
    return (res as List)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
  }
}
