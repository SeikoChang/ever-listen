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
  bool _detectMode = true;
  double _sensitivity = 0.6;
  int _maxStorageMb = 200;
  String _status = 'idle';

  void _toggleRecording() async {
    if (_monitoring) {
      await _recorder.stopRecording();
      setState(() {
        _monitoring = false;
        _status = 'stopped';
      });
    } else {
      await _recorder.setSensitivity(_sensitivity);
      await _recorder.setMaxStorageMb(_maxStorageMb);
      await _recorder.startRecording(mode: _detectMode ? 'detect' : 'monitor');
      setState(() {
        _monitoring = true;
        _status = 'recording';
      });
    }
  }

  @override
  void initState() {
    super.initState();
    _recorder.events.listen((e) {
      // basic UI events
      setState(() {
        _status = e;
      });
    });
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
            Row(
              children: [
                const Text('Mode:'),
                const SizedBox(width: 12),
                DropdownButton<bool>(
                  value: _detectMode,
                  items: const [
                    DropdownMenuItem(value: true, child: Text('Detect')), 
                    DropdownMenuItem(value: false, child: Text('Monitoring')),
                  ],
                  onChanged: (v) {
                    setState(() { _detectMode = v ?? true; });
                  },
                )
              ],
            ),
            const SizedBox(height: 12),
            Row(children: [const Text('Sensitivity'), Expanded(
              child: Slider(value: _sensitivity, min: 0.0, max: 1.0, onChanged: (v) { setState(() { _sensitivity = v; }); }),
            )]),
            const SizedBox(height: 12),
            Row(children: [
              const Text('Max storage (MB)'),
              const SizedBox(width: 12),
              Expanded(child: TextFormField(initialValue: '200', keyboardType: TextInputType.number, onChanged: (s) => _maxStorageMb = int.tryParse(s) ?? 200,)),
            ]),
            const SizedBox(height: 20),
            Text('Status: $_status'),
            const SizedBox(height: 20),
            ElevatedButton(onPressed: _toggleRecording, child: Text(_monitoring ? 'Stop' : 'Start'))
          ],
        ),
      ),
    );
  }
}
