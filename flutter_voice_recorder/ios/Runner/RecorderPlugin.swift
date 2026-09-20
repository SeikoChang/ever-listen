import AVFoundation
import Flutter
import Foundation
import UserNotifications

public final class RecorderPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
  private static let channelName = "ever_listen/recorder"
  private static let eventChannelName = "ever_listen/events"
  private static let schedulesKey = "ever_listen.schedules"
  private static let reminderIdentifierPrefix = "com.seikochang.everlisten.recording-reminder."

  private var eventSink: FlutterEventSink?
  private var recorder: AudioEngineRecorder?
  private var sensitivity = 0.6
  private var maxStorageMb = 200

  override init() {
    super.init()
  }

  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = RecorderPlugin()
    let channel = FlutterMethodChannel(
      name: channelName,
      binaryMessenger: registrar.messenger()
    )
    registrar.addMethodCallDelegate(instance, channel: channel)

    let eventChannel = FlutterEventChannel(
      name: eventChannelName,
      binaryMessenger: registrar.messenger()
    )
    eventChannel.setStreamHandler(instance)
  }

  public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    eventSink = events
    recorder?.eventSink = events
    return nil
  }

  public func onCancel(withArguments arguments: Any?) -> FlutterError? {
    eventSink = nil
    recorder?.eventSink = nil
    return nil
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    let arguments = call.arguments as? [String: Any] ?? [:]

    switch call.method {
    case "startRecording":
      startRecording(arguments: arguments, result: result)
    case "stopRecording":
      recorder?.stop()
      recorder = nil
      result(["status": "stopped"])
    case "requestPermissions":
      requestPermissions(result: result)
    case "setSensitivity":
      let value = number(arguments["sensitivity"]) ?? sensitivity
      sensitivity = min(max(value, 0), 1)
      recorder?.sensitivity = sensitivity
      result(["sensitivity": sensitivity])
    case "setMaxStorageMb":
      let value = Int(number(arguments["maxStorageMb"]) ?? Double(maxStorageMb))
      maxStorageMb = max(value, 1)
      recorder?.maxStorageMb = maxStorageMb
      result(["maxStorageMb": maxStorageMb])
    case "scheduleRecording":
      scheduleRecording(arguments: arguments, result: result)
    case "cancelSchedule":
      cancelSchedule(arguments: arguments, result: result)
    case "getSchedules":
      result(loadSchedules())
    case "getStatus":
      result(status())
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func startRecording(arguments: [String: Any], result: @escaping FlutterResult) {
    let mode = (arguments["mode"] as? String) ?? "detect"
    guard ["detect", "monitor", "schedule"].contains(mode) else {
      result(FlutterError(code: "INVALID_MODE", message: "Unsupported recording mode", details: nil))
      return
    }
    guard recorder == nil else {
      result(FlutterError(code: "ALREADY_RUNNING", message: "Recording is already running", details: nil))
      return
    }

    let requestedSensitivity = number(arguments["sensitivity"]) ?? sensitivity
    sensitivity = min(max(requestedSensitivity, 0), 1)
    let requestedStorage = Int(number(arguments["maxStorageMb"]) ?? Double(maxStorageMb))
    maxStorageMb = max(requestedStorage, 1)

    AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
      DispatchQueue.main.async {
        guard let self else { return }
        guard granted else {
          result(FlutterError(code: "PERMISSION_DENIED", message: "Microphone permission was denied", details: nil))
          return
        }
        do {
          let audioRecorder = try AudioEngineRecorder(
            mode: mode,
            sensitivity: self.sensitivity,
            maxStorageMb: self.maxStorageMb,
            eventSink: self.eventSink
          )
          try audioRecorder.start()
          self.recorder = audioRecorder
          result(["status": "started", "mode": mode])
        } catch {
          result(FlutterError(code: "START_FAILED", message: error.localizedDescription, details: nil))
        }
      }
    }
  }

  private func requestPermissions(result: @escaping FlutterResult) {
    requestMicrophonePermission { [weak self] microphoneGranted in
      self?.requestNotificationPermission { notificationsGranted in
        DispatchQueue.main.async {
          result([
            "microphone": microphoneGranted,
            "notifications": notificationsGranted
          ])
        }
      }
    }
  }

  private func requestMicrophonePermission(completion: @escaping (Bool) -> Void) {
    let session = AVAudioSession.sharedInstance()
    switch session.recordPermission {
    case .granted:
      completion(true)
    case .denied:
      completion(false)
    case .undetermined:
      session.requestRecordPermission { granted in
        completion(granted)
      }
    @unknown default:
      completion(false)
    }
  }

  private func requestNotificationPermission(completion: @escaping (Bool) -> Void) {
    let center = UNUserNotificationCenter.current()
    center.getNotificationSettings { settings in
      switch settings.authorizationStatus {
      case .authorized, .provisional, .ephemeral:
        completion(true)
      case .denied:
        completion(false)
      case .notDetermined:
        center.requestAuthorization(options: [.alert, .sound]) { granted, error in
          if let error {
            self.sendEvent("error", ["message": "Unable to request notification permission: \(error.localizedDescription)"])
          }
          completion(granted)
        }
      @unknown default:
        completion(false)
      }
    }
  }

  private func scheduleRecording(arguments: [String: Any], result: @escaping FlutterResult) {
    guard let start = number(arguments["startTimeMillis"]),
          let end = number(arguments["endTimeMillis"]),
          end > start,
          start > Date().timeIntervalSince1970 * 1000 else {
      result(FlutterError(code: "INVALID_SCHEDULE", message: "Schedule times are invalid", details: nil))
      return
    }
    let repeatValue = (arguments["repeat"] as? String) ?? "once"
    guard ["once", "daily", "weekly"].contains(repeatValue) else {
      result(FlutterError(code: "INVALID_SCHEDULE", message: "Unsupported repeat value", details: nil))
      return
    }

    let previousSchedules = loadSchedules()
    let schedule: [String: Any] = [
      "id": (arguments["id"] as? String) ?? UUID().uuidString,
      "startTimeMillis": Int64(start),
      "endTimeMillis": Int64(end),
      "repeat": repeatValue,
      "timezone": (arguments["timezone"] as? String) ?? TimeZone.current.identifier,
      "mode": (arguments["mode"] as? String) ?? "schedule",
      "sensitivity": min(max(number(arguments["sensitivity"]) ?? sensitivity, 0), 1),
      "maxStorageMb": max(Int(number(arguments["maxStorageMb"]) ?? Double(maxStorageMb)), 1)
    ]
    requestNotificationPermission { [weak self] notificationsGranted in
      DispatchQueue.main.async {
        guard let self else { return }
        guard notificationsGranted else {
          result(FlutterError(
            code: "NOTIFICATION_PERMISSION_DENIED",
            message: "Notification permission is required for iOS recording reminders",
            details: nil
          ))
          return
        }
        self.scheduleReminder(for: schedule) { error in
          DispatchQueue.main.async {
            if let error {
              result(FlutterError(code: "SCHEDULE_FAILED", message: error.localizedDescription, details: nil))
              return
            }
            var schedules = previousSchedules
            schedules.removeAll { ($0["id"] as? String) == (schedule["id"] as? String) }
            schedules.append(schedule)
            guard self.saveSchedules(schedules) else {
              self.removeReminder(for: schedule)
              result(FlutterError(code: "SCHEDULE_PERSIST_FAILED", message: "Unable to save schedule", details: nil))
              return
            }
            self.sendEvent("recordingReminderScheduled", [
              "id": schedule["id"] as? String ?? "",
              "startTimeMillis": schedule["startTimeMillis"] as? Int64 ?? 0
            ])
            result(schedule)
          }
        }
      }
    }
  }

  private func cancelSchedule(arguments: [String: Any], result: FlutterResult) {
    guard let id = arguments["id"] as? String else {
      result(FlutterError(code: "INVALID_ARGUMENT", message: "cancelSchedule requires an id", details: nil))
      return
    }
    var schedules = loadSchedules()
    let originalCount = schedules.count
    schedules.removeAll { ($0["id"] as? String) == id }
    guard saveSchedules(schedules) else {
      result(FlutterError(code: "SCHEDULE_PERSIST_FAILED", message: "Unable to cancel schedule", details: nil))
      return
    }
    removeReminder(forID: id)
    result(["cancelled": schedules.count != originalCount, "id": id])
  }

  private func status() -> [String: Any] {
    [
      "running": recorder != nil,
      "mode": recorder?.mode ?? "detect",
      "sensitivity": sensitivity,
      "maxStorageMb": maxStorageMb,
      "currentFilePath": recorder?.currentFilePath ?? "",
      "storageUsedMb": recorder?.storageUsedMb ?? storageUsedMb(),
      "schedules": loadSchedules()
    ]
  }

  func number(_ value: Any?) -> Double? {
    if let value = value as? NSNumber { return value.doubleValue }
    return nil
  }

  func loadSchedules() -> [[String: Any]] {
    guard let data = UserDefaults.standard.data(forKey: Self.schedulesKey),
          let decoded = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
      return []
    }
    return decoded
  }

  @discardableResult
  func saveSchedules(_ schedules: [[String: Any]]) -> Bool {
    do {
      let data = try JSONSerialization.data(withJSONObject: schedules)
      UserDefaults.standard.set(data, forKey: Self.schedulesKey)
      return true
    } catch {
      sendEvent("error", ["message": "Unable to persist schedules: \(error.localizedDescription)"])
      return false
    }
  }

  private func scheduleReminder(for schedule: [String: Any], completion: @escaping (Error?) -> Void) {
    guard let id = schedule["id"] as? String,
          let trigger = reminderTrigger(for: schedule) else {
      completion(NSError(
        domain: "EverListen.RecorderPlugin",
        code: 2,
        userInfo: [NSLocalizedDescriptionKey: "Schedule does not contain a valid reminder time"]
      ))
      return
    }

    let content = UNMutableNotificationContent()
    content.title = "Ever Listen recording reminder"
    content.body = "It is time to start your scheduled recording."
    content.sound = .default
    content.userInfo = ["scheduleId": id, "kind": "recordingReminder"]

    let request = UNNotificationRequest(
      identifier: reminderIdentifier(forID: id),
      content: content,
      trigger: trigger
    )
    UNUserNotificationCenter.current().add(request, withCompletionHandler: completion)
  }

  func reminderTrigger(for schedule: [String: Any]) -> UNCalendarNotificationTrigger? {
    guard let startMillis = number(schedule["startTimeMillis"]) else { return nil }
    let timezone = (schedule["timezone"] as? String).flatMap(TimeZone.init(identifier:)) ?? .current
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timezone
    let startDate = Date(timeIntervalSince1970: startMillis / 1000)
    let repeatValue = (schedule["repeat"] as? String) ?? "once"

    var components: DateComponents
    switch repeatValue {
    case "daily":
      components = calendar.dateComponents([.hour, .minute, .second], from: startDate)
    case "weekly":
      components = calendar.dateComponents([.weekday, .hour, .minute, .second], from: startDate)
    case "once":
      components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: startDate)
    default:
      return nil
    }
    components.calendar = calendar
    components.timeZone = timezone
    return UNCalendarNotificationTrigger(dateMatching: components, repeats: repeatValue != "once")
  }

  private func reminderIdentifier(forID id: String) -> String {
    Self.reminderIdentifierPrefix + id
  }

  private func removeReminder(for schedule: [String: Any]) {
    guard let id = schedule["id"] as? String else { return }
    removeReminder(forID: id)
  }

  private func removeReminder(forID id: String) {
    let identifier = reminderIdentifier(forID: id)
    let center = UNUserNotificationCenter.current()
    center.removePendingNotificationRequests(withIdentifiers: [identifier])
    center.removeDeliveredNotifications(withIdentifiers: [identifier])
  }

  private func sendEvent(_ type: String, _ data: [String: Any]) {
    eventSink?(["type": type, "data": data, "timestamp": Int(Date().timeIntervalSince1970 * 1000)])
  }

  func normalizedSchedules(now: Double) -> [[String: Any]] {
    var normalized: [[String: Any]] = []
    for original in loadSchedules() {
      var schedule = original
      guard var start = number(schedule["startTimeMillis"]),
            var end = number(schedule["endTimeMillis"]) else { continue }
      let repeatValue = (schedule["repeat"] as? String) ?? "once"
      let interval = repeatValue == "weekly"
        ? 7.0 * 24 * 60 * 60 * 1000
        : 24.0 * 60 * 60 * 1000

      while end <= now {
        if repeatValue == "once" {
          schedule = [:]
          break
        }
        start += interval
        end += interval
      }
      guard !schedule.isEmpty else { continue }
      schedule["startTimeMillis"] = Int64(start)
      schedule["endTimeMillis"] = Int64(end)
      normalized.append(schedule)
    }
    return normalized
  }

  private func storageDirectory() -> URL {
    let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    return base.appendingPathComponent("recordings", isDirectory: true)
  }

  private func storageUsedMb() -> Double {
    guard let files = try? FileManager.default.contentsOfDirectory(
      at: storageDirectory(), includingPropertiesForKeys: [.fileSizeKey]
    ) else { return 0 }
    let bytes = files.reduce(Int64(0)) { total, url in
      total + (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).flatMap(Int64.init) ?? 0
    }
    return Double(bytes) / (1024 * 1024)
  }
}

private final class AudioEngineRecorder {
  fileprivate static let vadSampleRate = 16_000.0
  fileprivate static let vadFrameSampleCount = 320 // 20 ms at 16 kHz

  let mode: String
  var sensitivity: Double {
    didSet { vadProcessor?.setSensitivity(sensitivity) }
  }
  var maxStorageMb: Int
  var eventSink: FlutterEventSink?
  private let engine = AVAudioEngine()
  private var outputFile: AVAudioFile?
  private var speechActive = false
  private var silenceFrames = 0
  private var frameCount = 0
  private(set) var currentFilePath = ""
  private let directory: URL
  private var vadProcessor: WebRTCVADProcessor?
  private let vadFormat = AVAudioFormat(
    commonFormat: .pcmFormatInt16,
    sampleRate: vadSampleRate,
    channels: 1,
    interleaved: false
  )!
  private var vadSourceFormat: AVAudioFormat?
  private var vadConverter: AVAudioConverter?
  private var pendingVADSamples: [Int16] = []

  var storageUsedMb: Double { storageBytes() / (1024 * 1024) }

  init(mode: String, sensitivity: Double, maxStorageMb: Int, eventSink: FlutterEventSink?) throws {
    self.mode = mode
    self.sensitivity = sensitivity
    self.maxStorageMb = maxStorageMb
    self.eventSink = eventSink
    self.directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("recordings", isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    self.vadProcessor = WebRTCVADProcessor()
  }

  func start() throws {
    let session = AVAudioSession.sharedInstance()
    try session.setCategory(.record, mode: .default, options: [.allowBluetooth])
    try session.setActive(true)

    let input = engine.inputNode
    let format = input.outputFormat(forBus: 0)
    vadSourceFormat = nil
    vadConverter = nil
    pendingVADSamples.removeAll(keepingCapacity: true)
    input.installTap(onBus: 0, bufferSize: 480, format: format) { [weak self] buffer, _ in
      self?.process(buffer)
    }
    engine.prepare()
    do {
      try engine.start()
      emit("recordingStarted", ["mode": mode])
    } catch {
      input.removeTap(onBus: 0)
      try? session.setActive(false, options: .notifyOthersOnDeactivation)
      throw error
    }
  }

  func stop() {
    let input = engine.inputNode
    input.removeTap(onBus: 0)
    engine.stop()
    closeFile(emitReady: true)
    deactivateAudioSession()
    emit("recordingStopped", [:])
  }

  private func process(_ buffer: AVAudioPCMBuffer) {
    frameCount += 1
    if mode == "monitor" || mode == "schedule" {
      do { try openFileIfNeeded(format: buffer.format); try outputFile?.write(from: buffer) }
      catch { emit("error", ["message": error.localizedDescription]) }
      if frameCount % 100 == 0 { pruneStorage() }
      return
    }

    let speech = processVAD(buffer) ?? (rms(buffer) > (0.02 + (1 - sensitivity) * 0.08))
    if speech {
      silenceFrames = 0
      if !speechActive {
        speechActive = true
        emit("speechStarted", ["frame": frameCount])
        do { try openFileIfNeeded(format: buffer.format) }
        catch { emit("error", ["message": error.localizedDescription]) }
      }
      do { try outputFile?.write(from: buffer) }
      catch { emit("error", ["message": error.localizedDescription]) }
    } else if speechActive {
      silenceFrames += 1
      do { try outputFile?.write(from: buffer) }
      catch { emit("error", ["message": error.localizedDescription]) }
      if silenceFrames >= 17 {
        speechActive = false
        emit("speechEnded", ["frame": frameCount])
        closeFile(emitReady: true)
        pruneStorage()
      }
    }
  }

  private func rms(_ buffer: AVAudioPCMBuffer) -> Float {
    guard let data = buffer.floatChannelData?[0] else { return 0 }
    let count = Int(buffer.frameLength)
    guard count > 0 else { return 0 }
    var sum: Float = 0
    for index in 0..<count { sum += data[index] * data[index] }
    return sqrt(sum / Float(count))
  }

  /// WebRTC VAD accepts only mono PCM16 frames at supported sample rates and
  /// durations. Hardware input is commonly Float32 at 44.1/48 kHz, so convert
  /// it separately from the file-writing path and feed fixed 20 ms frames.
  private func processVAD(_ buffer: AVAudioPCMBuffer) -> Bool? {
    guard let samples = convertedVADSamples(from: buffer) else { return nil }
    pendingVADSamples.append(contentsOf: samples)

    var processedFrame = false
    var speechDetected = false
    while pendingVADSamples.count >= Self.vadFrameSampleCount {
      let frame = Array(pendingVADSamples.prefix(Self.vadFrameSampleCount))
      pendingVADSamples.removeFirst(Self.vadFrameSampleCount)
      guard let isSpeech = vadProcessor?.process(frame) else { return nil }
      processedFrame = true
      speechDetected = speechDetected || isSpeech
    }
    return processedFrame ? speechDetected : nil
  }

  private func convertedVADSamples(from buffer: AVAudioPCMBuffer) -> [Int16]? {
    if vadConverter == nil ||
      vadSourceFormat?.sampleRate != buffer.format.sampleRate ||
      vadSourceFormat?.channelCount != buffer.format.channelCount {
      vadSourceFormat = buffer.format
      vadConverter = AVAudioConverter(from: buffer.format, to: vadFormat)
    }
    guard let converter = vadConverter else { return nil }

    let convertedCapacity = AVAudioFrameCount(
      ceil(Double(buffer.frameLength) * Self.vadSampleRate / buffer.format.sampleRate)
    )
    guard convertedCapacity > 0,
      let converted = AVAudioPCMBuffer(pcmFormat: vadFormat, frameCapacity: convertedCapacity) else {
      return nil
    }

    var hasSuppliedInput = false
    var conversionError: NSError?
    let status = converter.convert(to: converted, error: &conversionError) { _, inputStatus in
      if hasSuppliedInput {
        inputStatus.pointee = .noDataNow
        return nil
      }
      hasSuppliedInput = true
      inputStatus.pointee = .haveData
      return buffer
    }
    if let conversionError {
      emit("error", ["message": "VAD audio conversion failed: \(conversionError.localizedDescription)"])
      return nil
    }
    guard status == .haveData || status == .inputRanDry,
      let data = converted.int16ChannelData?[0] else {
      emit("error", ["message": "VAD audio conversion did not produce PCM16 data"])
      return nil
    }
    return Array(UnsafeBufferPointer(start: data, count: Int(converted.frameLength)))
  }

  private func openFileIfNeeded(format: AVAudioFormat) throws {
    guard outputFile == nil else { return }
    let name = "\(mode)_\(Int(Date().timeIntervalSince1970 * 1000)).caf"
    let url = directory.appendingPathComponent(name)
    outputFile = try AVAudioFile(forWriting: url, settings: format.settings)
    currentFilePath = url.path
  }

  private func deactivateAudioSession() {
    do {
      try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    } catch {
      emit("error", ["message": "Unable to deactivate audio session: \(error.localizedDescription)"])
    }
  }

  private func closeFile(emitReady: Bool) {
    guard outputFile != nil else { return }
    outputFile = nil
    let path = currentFilePath
    currentFilePath = ""
    if emitReady { emit("fileReady", ["filePath": path]) }
  }

  private func storageBytes() -> Double {
    guard let files = try? FileManager.default.contentsOfDirectory(
      at: directory, includingPropertiesForKeys: [.fileSizeKey]
    ) else { return 0 }
    return files.reduce(0) { total, url in
      total + Double((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
    }
  }

  private func pruneStorage() {
    guard let files = try? FileManager.default.contentsOfDirectory(
      at: directory, includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey]
    ) else { return }
    var total = storageBytes()
    let limit = Double(maxStorageMb) * 1024 * 1024
    let sorted = files.sorted {
      modificationDate($0) < modificationDate($1)
    }
    for url in sorted where total > limit && url.path != currentFilePath {
      let size = Double((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
      do {
        try FileManager.default.removeItem(at: url)
        total -= size
        emit("storagePruned", ["filePath": url.path])
      } catch {
        emit("error", ["message": "Unable to delete recording \(url.lastPathComponent): \(error.localizedDescription)"])
      }
    }
  }

  private func modificationDate(_ url: URL) -> Date {
    (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)
      ?? .distantPast
  }

  private func emit(_ type: String, _ data: [String: Any]) {
    DispatchQueue.main.async { [weak self] in
      self?.eventSink?(["type": type, "data": data, "timestamp": Int(Date().timeIntervalSince1970 * 1000)])
    }
  }
}

// MARK: - WebRTC VAD Processor (TODO #20)

/// Wraps the native WebRTC VAD C library via vad_wrapper.h.
/// Falls back gracefully to nil if the native library fails to initialize.
private final class WebRTCVADProcessor {
  private var handle: UnsafeMutableRawPointer?

  init() {
    // Default aggressiveness 2 = "Aggressive" (good balance)
    handle = vad_init(2)
    if handle == nil {
      os_log(.debug, "WebRTC VAD init failed — will use RMS fallback")
    }
  }

  deinit {
    vad_destroy(handle)
  }

  /// Map 0.0–1.0 sensitivity to WebRTC aggressiveness 0–3.
  func setSensitivity(_ sensitivity: Double) {
    let s = sensitivity.clamped(to: 0.0...1.0)
    let aggressiveness = Int((1.0 - s) * 3).clamped(to: 0...3)
    vad_destroy(handle)
    handle = vad_init(aggressiveness)
  }

  /// Process one mono PCM16 frame at 16 kHz. Returns nil if the native VAD is
  /// unavailable, which lets the caller use the RMS fallback.
  func process(_ samples: [Int16]) -> Bool? {
    guard let handle = handle else { return nil }
    guard samples.count == AudioEngineRecorder.vadFrameSampleCount else { return nil }
    return samples.withUnsafeBufferPointer { pointer in
      let result = vad_process(handle, pointer.baseAddress!, samples.count)
      return result == 1
    }
  }
}

private extension FloatingPoint {
  func clamped(to range: ClosedRange<Self>) -> Self {
    if self < range.lowerBound { return range.lowerBound }
    if self > range.upperBound { return range.upperBound }
    return self
  }
}

private extension BinaryInteger {
  func clamped(to range: ClosedRange<Self>) -> Self {
    if self < range.lowerBound { return range.lowerBound }
    if self > range.upperBound { return range.upperBound }
    return self
  }
}
