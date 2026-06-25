import 'dart:async';
import 'package:flutter/services.dart';

class RecorderPlugin {
  static const MethodChannel _channel = MethodChannel('ever_listen/recorder');
  static const EventChannel _eventChannel = EventChannel('ever_listen/events');

  Stream<String> get events => _eventChannel
      .receiveBroadcastStream()
      .map((event) => event?.toString() ?? '');

  Future<void> startRecording({String mode = 'detect'}) async {
    await _channel.invokeMethod('startRecording', {'mode': mode});
  }

  Future<void> stopRecording() async {
    await _channel.invokeMethod('stopRecording');
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
}
