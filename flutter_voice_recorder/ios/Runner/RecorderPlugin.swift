// RecorderPlugin.swift
// Minimal Swift plugin stub. Implement AVAudioEngine tap, VAD bridge and background mode handling.

import Flutter
import UIKit

public class RecorderPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "ever_listen/recorder", binaryMessenger: registrar.messenger())
    let instance = RecorderPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)

    let eventChannel = FlutterEventChannel(name: "ever_listen/events", binaryMessenger: registrar.messenger())
    // TODO: set eventChannel.setStreamHandler(...) to emit events
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "startRecording":
      // TODO: start AVAudioEngine tap and VAD
      result(nil)
    case "stopRecording":
      // TODO: stop audio
      result(nil)
    case "setSensitivity":
      // TODO: set sensitivity
      result(nil)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
}
