// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:flutter_voice_recorder/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Ever Listen app renders its recorder screen', (WidgetTester tester) async {
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final recorderChannel = const MethodChannel('ever_listen/recorder');
    final eventsChannel = const MethodChannel('ever_listen/events');

    messenger.setMockMethodCallHandler(recorderChannel, (call) async {
      if (call.method == 'getSchedules') return <Map<String, dynamic>>[];
      return null;
    });
    messenger.setMockMethodCallHandler(eventsChannel, (call) async => null);

    addTearDown(() {
      messenger.setMockMethodCallHandler(recorderChannel, null);
      messenger.setMockMethodCallHandler(eventsChannel, null);
    });

    await tester.pumpWidget(const EverListenApp());
    await tester.pump();

    expect(find.text('Ever Listen'), findsOneWidget);
    expect(find.text('Sensitivity'), findsOneWidget);
    expect(find.text('Start'), findsOneWidget);
  });
}
