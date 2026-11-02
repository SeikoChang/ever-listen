import 'dart:async';
import 'package:flutter/services.dart';

enum RecorderState { idle, starting, recording, stopping, interrupted, error }

/// Valid lifecycle transitions. Native interruption recovery is opt-in.
bool canTransitionRecorderState(
  RecorderState current,
  RecorderState next, {
  bool explicitRecovery = false,
}) {
  if (current == RecorderState.interrupted && next == RecorderState.recording) {
    return explicitRecovery;
  }
  return switch (current) {
    RecorderState.idle => next == RecorderState.starting,
    RecorderState.starting => next == RecorderState.recording ||
        next == RecorderState.stopping ||
        next == RecorderState.error,
    RecorderState.recording => next == RecorderState.stopping ||
        next == RecorderState.interrupted ||
        next == RecorderState.error,
    RecorderState.stopping =>
      next == RecorderState.idle || next == RecorderState.error,
    RecorderState.interrupted =>
      next == RecorderState.stopping || next == RecorderState.error,
    RecorderState.error =>
      next == RecorderState.idle || next == RecorderState.starting,
  };
}

/// Stable recorder snapshot shared by the Dart, Android, and iOS APIs.
class RecorderStatus {
  const RecorderStatus({
    required this.state,
    this.sessionId,
    this.mode,
    this.errorCode,
    this.errorMessage,
    this.recoverable = false,
  });

  final RecorderState state;
  final String? sessionId;
  final String? mode;
  final String? errorCode;
  final String? errorMessage;
  final bool recoverable;

  factory RecorderStatus.fromMap(Map<Object?, Object?> map) => RecorderStatus(
        state: RecorderState.values.firstWhere(
          (value) => value.name == map['state'],
          orElse: () => map['running'] == true
              ? RecorderState.recording
              : RecorderState.idle,
        ),
        sessionId: map['sessionId'] as String?,
        mode: map['mode'] as String?,
        errorCode: map['errorCode'] as String?,
        errorMessage: map['errorMessage'] as String?,
        recoverable: map['recoverable'] == true,
      );
}

class RecorderEvent {
  const RecorderEvent({
    required this.type,
    this.sessionId,
    this.state,
    this.data = const {},
  });
  final String type;
  final String? sessionId;
  final RecorderState? state;
  final Map<String, Object?> data;

  factory RecorderEvent.fromMap(Map<Object?, Object?> map) {
    final states = RecorderState.values.where((v) => v.name == map['state']);
    return RecorderEvent(
      type: map['type'] as String? ?? 'unknown',
      sessionId: map['sessionId'] as String?,
      state: states.isEmpty ? null : states.first,
      data: Map<String, Object?>.from(map['data'] as Map? ?? const {}),
    );
  }
}

class RecorderPlugin {
  static const MethodChannel _channel = MethodChannel('ever_listen/recorder');
  static const EventChannel _eventChannel = EventChannel('ever_listen/events');

  Stream<RecorderEvent> get events =>
      _eventChannel.receiveBroadcastStream().map(
            (event) =>
                RecorderEvent.fromMap(Map<Object?, Object?>.from(event as Map)),
          );

  Future<void> startRecording({
    String mode = 'detect',
    double sensitivity = 0.6,
    int maxStorageMb = 200,
    String? sessionId,
  }) async {
    await _channel.invokeMethod('startRecording', {
      'mode': mode,
      'sensitivity': sensitivity,
      'maxStorageMb': maxStorageMb,
      if (sessionId != null) 'sessionId': sessionId,
    });
  }

  Future<void> stopRecording() async {
    await _channel.invokeMethod('stopRecording');
  }

  Future<Map<String, dynamic>> requestPermissions() async {
    final res = await _channel.invokeMethod('requestPermissions');
    return Map<String, dynamic>.from(res as Map);
  }

  Future<bool> openAppSettings() async {
    return await _channel.invokeMethod<bool>('openAppSettings') ?? false;
  }

  Future<void> setSensitivity(double s) async {
    await _channel.invokeMethod('setSensitivity', {'sensitivity': s});
  }

  Future<void> setMaxStorageMb(int mb) async {
    await _channel.invokeMethod('setMaxStorageMb', {'maxStorageMb': mb});
  }

  Future<RecorderStatus?> getStatus() async {
    final res = await _channel.invokeMethod('getStatus');
    if (res == null) return null;
    return RecorderStatus.fromMap(Map<Object?, Object?>.from(res as Map));
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
