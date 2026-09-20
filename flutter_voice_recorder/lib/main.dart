import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'src/recorder_plugin.dart';

void main() {
  runApp(const EverListenApp());
}

class EverListenApp extends StatelessWidget {
  const EverListenApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ever Listen',
      home: const RecorderHome(),
    );
  }
}

class RecorderHome extends StatefulWidget {
  const RecorderHome({super.key});

  @override
  State<RecorderHome> createState() => _RecorderHomeState();
}

class _RecorderHomeState extends State<RecorderHome> {
  final RecorderPlugin _recorder = RecorderPlugin();
  bool _monitoring = false;
  String _mode = 'detect';
  double _sensitivity = 0.6;
  int _maxStorageMb = 200;
  String _status = 'idle';
  DateTime _scheduleStart = DateTime.now().add(const Duration(minutes: 1));
  DateTime _scheduleEnd = DateTime.now().add(const Duration(minutes: 6));
  String _repeat = 'once';
  List<Map<String, dynamic>> _schedules = [];
  StreamSubscription<Map<String, dynamic>>? _eventSubscription;
  bool _busy = false;
  String? _pendingReminderId;

  Future<void> _toggleRecording() async {
    if (_busy) return;
    setState(() {
      _busy = true;
    });
    try {
      if (_monitoring) {
        await _recorder.stopRecording();
        if (!mounted) return;
        setState(() {
          _monitoring = false;
          _status = 'stopped';
        });
      } else {
        await _recorder.setSensitivity(_sensitivity);
        await _recorder.setMaxStorageMb(_maxStorageMb);
        await _recorder.startRecording(
          mode: _mode,
          sensitivity: _sensitivity,
          maxStorageMb: _maxStorageMb,
        );
        if (!mounted) return;
        setState(() {
          _monitoring = true;
          _status = 'recording';
        });
      }
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _status = 'Recording failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  @override
  void initState() {
    super.initState();
    _eventSubscription = _recorder.events.listen(
      _handleEvent,
      onError: (Object error, StackTrace stackTrace) {
        if (!mounted) return;
        setState(() {
          _status = 'Native event failed: $error';
        });
      },
    );
    unawaited(_refreshStatus());
    _refreshSchedules();
  }

  void _handleEvent(Map<String, dynamic> event) {
    if (!mounted) return;
    final type = event['type']?.toString() ?? 'event';
    final data = event['data'];
    setState(() {
      if (type == 'recordingStarted') _monitoring = true;
      if (type == 'recordingStopped' || type == 'error') _monitoring = false;
      if (type == 'recordingReminderDelivered' ||
          type == 'recordingReminderOpened') {
        _pendingReminderId =
            data is Map ? data['id']?.toString() : _pendingReminderId;
      }
      _status = data == null ? type : '$type $data';
    });
  }

  Future<void> _startFromReminder() async {
    setState(() {
      _mode = 'detect';
    });
    await _toggleRecording();
    if (mounted && _monitoring) {
      setState(() {
        _pendingReminderId = null;
      });
    }
  }

  Future<void> _refreshStatus() async {
    try {
      final status = await _recorder.getStatus();
      if (!mounted || status == null) return;
      setState(() {
        _monitoring = status['running'] == true;
        _status = _monitoring ? 'recording' : 'idle';
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _status = 'Unable to load recorder status: $error';
      });
    }
  }

  Future<void> _refreshSchedules() async {
    try {
      final schedules = await _recorder.getSchedules();
      if (!mounted) return;
      setState(() {
        _schedules = schedules;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _status = 'Unable to load schedules: $error';
      });
    }
  }

  Future<void> _pickScheduleTime({required bool start}) async {
    final initial = start ? _scheduleStart : _scheduleEnd;
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (date == null || !mounted) return;

    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial),
    );
    if (time == null) return;

    final selected = DateTime(
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    );
    setState(() {
      if (start) {
        _scheduleStart = selected;
        if (!_scheduleEnd.isAfter(_scheduleStart)) {
          _scheduleEnd = _scheduleStart.add(const Duration(minutes: 5));
        }
      } else {
        _scheduleEnd = selected;
      }
    });
  }

  Future<void> _createSchedule() async {
    if (!_scheduleEnd.isAfter(_scheduleStart)) {
      setState(() {
        _status = 'Schedule end must be after start';
      });
      return;
    }

    try {
      if (Platform.isIOS) {
        final permissions = await _recorder.requestPermissions();
        if (permissions['notifications'] != true) {
          setState(() {
            _status = 'Notifications are required for iOS recording reminders';
          });
          return;
        }
      }

      await _recorder.scheduleRecording(
        startTime: _scheduleStart,
        endTime: _scheduleEnd,
        repeat: _repeat,
        timezone: DateTime.now().timeZoneName,
        mode: 'schedule',
        sensitivity: _sensitivity,
        maxStorageMb: _maxStorageMb,
      );
      if (!mounted) return;
      setState(() {
        _status =
            Platform.isIOS ? 'recording reminder created' : 'schedule created';
      });
      await _refreshSchedules();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _status = 'Unable to create schedule: $error';
      });
    }
  }

  Future<void> _cancelSchedule(String id) async {
    try {
      await _recorder.cancelSchedule(id);
      await _refreshSchedules();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _status = 'Unable to cancel schedule: $error';
      });
    }
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ever Listen')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'detect', label: Text('Detect')),
                ButtonSegment(value: 'monitor', label: Text('Monitor')),
                ButtonSegment(value: 'schedule', label: Text('Schedule')),
              ],
              selected: {_mode},
              onSelectionChanged: (values) {
                setState(() {
                  _mode = values.first;
                });
              },
            ),
            const SizedBox(height: 12),
            Row(children: [
              const Text('Sensitivity'),
              Expanded(
                child: Slider(
                    value: _sensitivity,
                    min: 0.0,
                    max: 1.0,
                    onChanged: (v) {
                      setState(() {
                        _sensitivity = v;
                      });
                    }),
              )
            ]),
            const SizedBox(height: 12),
            Row(children: [
              const Text('Max storage (MB)'),
              const SizedBox(width: 12),
              Expanded(
                  child: TextFormField(
                initialValue: '200',
                keyboardType: TextInputType.number,
                onChanged: (s) => _maxStorageMb = int.tryParse(s) ?? 200,
              )),
            ]),
            const SizedBox(height: 20),
            Text('Status: $_status'),
            if (_pendingReminderId != null && !_monitoring) ...[
              const SizedBox(height: 8),
              ElevatedButton.icon(
                onPressed: _busy ? null : _startFromReminder,
                icon: const Icon(Icons.mic),
                label: const Text('Start now'),
              ),
            ],
            const SizedBox(height: 20),
            if (_mode == 'schedule') ...[
              if (Platform.isIOS) ...[
                const Text(
                  'On iPhone, a notification reminds you to start recording. '
                  'The reminder does not start recording automatically.',
                ),
                const SizedBox(height: 12),
              ],
              _ScheduleTimeRow(
                label: 'Start',
                value: _scheduleStart,
                onTap: () => _pickScheduleTime(start: true),
              ),
              _ScheduleTimeRow(
                label: 'End',
                value: _scheduleEnd,
                onTap: () => _pickScheduleTime(start: false),
              ),
              Row(
                children: [
                  const Text('Repeat'),
                  const SizedBox(width: 12),
                  DropdownButton<String>(
                    value: _repeat,
                    items: const [
                      DropdownMenuItem(value: 'once', child: Text('Once')),
                      DropdownMenuItem(value: 'daily', child: Text('Daily')),
                      DropdownMenuItem(value: 'weekly', child: Text('Weekly')),
                    ],
                    onChanged: (value) {
                      setState(() {
                        _repeat = value ?? 'once';
                      });
                    },
                  ),
                  const Spacer(),
                  ElevatedButton(
                    onPressed: _createSchedule,
                    child: Text(Platform.isIOS ? 'Add reminder' : 'Add'),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Expanded(
                child: ListView(
                  children: _schedules.map((schedule) {
                    final id = schedule['id']?.toString() ?? '';
                    final start = DateTime.fromMillisecondsSinceEpoch(
                      schedule['startTimeMillis'] as int,
                    );
                    final end = DateTime.fromMillisecondsSinceEpoch(
                      schedule['endTimeMillis'] as int,
                    );
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                          '${schedule['repeat']}  ${_formatDateTime(start)}'),
                      subtitle: Text(Platform.isIOS
                          ? 'Reminder only — recording starts when you choose it'
                          : 'Ends ${_formatDateTime(end)}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        tooltip: 'Delete schedule',
                        onPressed:
                            id.isEmpty ? null : () => _cancelSchedule(id),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ] else ...[
              ElevatedButton(
                onPressed: _busy ? null : _toggleRecording,
                child: Text(_monitoring ? 'Stop' : 'Start'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _formatDateTime(DateTime value) {
    final date =
        '${value.year}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';
    final time =
        '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
    return '$date $time';
  }
}

class _ScheduleTimeRow extends StatelessWidget {
  const _ScheduleTimeRow({
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final DateTime value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final formatted =
        '${value.year}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')} '
        '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(label),
      subtitle: Text(formatted),
      trailing: const Icon(Icons.edit_calendar),
      onTap: onTap,
    );
  }
}
