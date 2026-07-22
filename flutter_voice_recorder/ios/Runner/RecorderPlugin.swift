import AVFoundation
import BackgroundTasks
import Flutter
import Foundation

public final class RecorderPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
  private static let channelName = "ever_listen/recorder"
  private static let eventChannelName = "ever_listen/events"
  private static let schedulesKey = "ever_listen.schedules"
  private static let backgroundTaskIdentifier = "com.seikochang.everlisten.recording"
  private static var didRegisterBackgroundTask = false

  private var eventSink: FlutterEventSink?
  private var recorder: AudioEngineRecorder?
  private var sensitivity = 0.6
  private var maxStorageMb = 200
  private var scheduledStopWork: DispatchWorkItem?

  override init() {
    super.init()
    registerBackgroundTask()
    try? submitNextBackgroundTask()
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

  private func scheduleRecording(arguments: [String: Any], result: FlutterResult) {
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

    var schedule: [String: Any] = [
      "id": (arguments["id"] as? String) ?? UUID().uuidString,
      "startTimeMillis": Int64(start),
      "endTimeMillis": Int64(end),
      "repeat": repeatValue,
      "timezone": (arguments["timezone"] as? String) ?? TimeZone.current.identifier,
      "mode": (arguments["mode"] as? String) ?? "schedule",
      "sensitivity": min(max(number(arguments["sensitivity"]) ?? sensitivity, 0), 1),
      "maxStorageMb": max(Int(number(arguments["maxStorageMb"]) ?? Double(maxStorageMb)), 1)
    ]
    var schedules = loadSchedules()
    schedules.removeAll { ($0["id"] as? String) == (schedule["id"] as? String) }
    schedules.append(schedule)
    saveSchedules(schedules)
    do {
      try submitNextBackgroundTask()
      result(schedule)
    } catch {
      result(FlutterError(code: "SCHEDULE_FAILED", message: error.localizedDescription, details: nil))
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
    saveSchedules(schedules)
    try? submitNextBackgroundTask()
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

  func saveSchedules(_ schedules: [[String: Any]]) {
    if let data = try? JSONSerialization.data(withJSONObject: schedules) {
      UserDefaults.standard.set(data, forKey: Self.schedulesKey)
    }
  }

  private func registerBackgroundTask() {
    guard !Self.didRegisterBackgroundTask else { return }
    Self.didRegisterBackgroundTask = true
    BGTaskScheduler.shared.register(
      forTaskWithIdentifier: Self.backgroundTaskIdentifier,
      using: nil
    ) { [weak self] task in
      guard let task = task as? BGProcessingTask else {
        task.setTaskCompleted(success: false)
        return
      }
      self?.handleBackgroundTask(task)
    }
  }

  private func submitNextBackgroundTask() throws {
    let now = Date().timeIntervalSince1970 * 1000
    let schedules = normalizedSchedules(now: now)
    saveSchedules(schedules)
    let nextStart = schedules
      .compactMap { number($0["startTimeMillis"]) }
      .filter { $0 > now }
      .min()
    guard let nextStart else {
      BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.backgroundTaskIdentifier)
      return
    }

    BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.backgroundTaskIdentifier)
    let request = BGProcessingTaskRequest(identifier: Self.backgroundTaskIdentifier)
    request.earliestBeginDate = Date(timeIntervalSince1970: nextStart / 1000)
    request.requiresNetworkConnectivity = false
    request.requiresExternalPower = false
    try BGTaskScheduler.shared.submit(request)
  }

  private func handleBackgroundTask(_ task: BGProcessingTask) {
    task.expirationHandler = { [weak self] in
      self?.scheduledStopWork?.cancel()
      self?.recorder?.stop()
      self?.recorder = nil
      task.setTaskCompleted(success: false)
    }

    let now = Date().timeIntervalSince1970 * 1000
    var schedules = normalizedSchedules(now: now)
    var activeSchedule: [String: Any]?

    for schedule in schedules {
      guard let start = number(schedule["startTimeMillis"]),
            let end = number(schedule["endTimeMillis"]),
            let repeatValue = schedule["repeat"] as? String else { continue }

      if start <= now && end > now {
        activeSchedule = schedule
        break
      }
      if end <= now {
        if repeatValue == "once" {
          schedules.removeAll { ($0["id"] as? String) == (schedule["id"] as? String) }
        } else {
          var next = schedule
          let interval = repeatValue == "weekly" ? 7.0 * 24 * 60 * 60 * 1000 : 24.0 * 60 * 60 * 1000
          next["startTimeMillis"] = Int64(start + interval)
          next["endTimeMillis"] = Int64(end + interval)
          if let id = schedule["id"] as? String {
            schedules.removeAll { ($0["id"] as? String) == id }
          }
          schedules.append(next)
        }
      }
    }
    saveSchedules(schedules)

    guard let schedule = activeSchedule else {
      try? submitNextBackgroundTask()
      task.setTaskCompleted(success: true)
      return
    }

    let mode = (schedule["mode"] as? String) ?? "schedule"
    let scheduleSensitivity = number(schedule["sensitivity"]) ?? sensitivity
    let scheduleStorage = max(Int(number(schedule["maxStorageMb"]) ?? Double(maxStorageMb)), 1)
    do {
      let audioRecorder = try AudioEngineRecorder(
        mode: mode,
        sensitivity: scheduleSensitivity,
        maxStorageMb: scheduleStorage,
        eventSink: eventSink
      )
      try audioRecorder.start()
      recorder = audioRecorder
      sendEvent("scheduleStarted", ["mode": mode])

      let remaining = max((number(schedule["endTimeMillis"]) ?? now) - now, 0) / 1000
      let work = DispatchWorkItem { [weak self] in
        audioRecorder.stop()
        self?.recorder = nil
        self?.sendEvent("scheduleEnded", ["mode": mode])
        if let self {
          self.saveSchedules(self.normalizedSchedules(now: Date().timeIntervalSince1970 * 1000))
        }
        try? self?.submitNextBackgroundTask()
        task.setTaskCompleted(success: true)
      }
      scheduledStopWork = work
      DispatchQueue.global().asyncAfter(deadline: .now() + remaining, execute: work)
    } catch {
      sendEvent("error", ["message": error.localizedDescription])
      try? submitNextBackgroundTask()
      task.setTaskCompleted(success: false)
    }
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
  let mode: String
  var sensitivity: Double
  var maxStorageMb: Int
  var eventSink: FlutterEventSink?
  private let engine = AVAudioEngine()
  private var outputFile: AVAudioFile?
  private var speechActive = false
  private var silenceFrames = 0
  private var frameCount = 0
  private(set) var currentFilePath = ""
  private let directory: URL

  var storageUsedMb: Double { storageBytes() / (1024 * 1024) }

  init(mode: String, sensitivity: Double, maxStorageMb: Int, eventSink: FlutterEventSink?) throws {
    self.mode = mode
    self.sensitivity = sensitivity
    self.maxStorageMb = maxStorageMb
    self.eventSink = eventSink
    self.directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("recordings", isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
  }

  func start() throws {
    let session = AVAudioSession.sharedInstance()
    try session.setCategory(.record, mode: .default, options: [.allowBluetooth])
    try session.setActive(true)

    let input = engine.inputNode
    let format = input.outputFormat(forBus: 0)
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
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
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

    let speech = rms(buffer) > (0.02 + (1 - sensitivity) * 0.08)
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
      try? outputFile?.write(from: buffer)
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

  private func openFileIfNeeded(format: AVAudioFormat) throws {
    guard outputFile == nil else { return }
    let name = "\(mode)_\(Int(Date().timeIntervalSince1970 * 1000)).caf"
    let url = directory.appendingPathComponent(name)
    outputFile = try AVAudioFile(forWriting: url, settings: format.settings)
    currentFilePath = url.path
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
      try? FileManager.default.removeItem(at: url)
      total -= size
      emit("storagePruned", ["filePath": url.path])
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
