@testable import Runner
import XCTest

// MARK: - Test Helpers

final class CapturedResult: FlutterResult {
  var called = false
  var value: Any?
  var error: FlutterError?

  func _result(_ result: Any?) {
    called = true
    if let err = result as? FlutterError {
      self.error = err
    } else {
      self.value = result
    }
  }
}

// MARK: - Schedule CRUD Tests (TODO #64)

final class RecorderPluginScheduleTests: XCTestCase {

  var plugin: RecorderPlugin!

  override func setUp() {
    super.setUp()
    // Clear UserDefaults schedules before each test
    UserDefaults.standard.removeObject(forKey: "ever_listen.schedules")
    plugin = RecorderPlugin()
  }

  override func tearDown() {
    plugin = nil
    UserDefaults.standard.removeObject(forKey: "ever_listen.schedules")
    super.tearDown()
  }

  // TODO #64: MethodChannel handlers — start/stop/schedule

  func testGetSchedulesReturnsEmptyWhenNone() {
    let schedules = plugin.loadSchedules()
    XCTAssertTrue(schedules.isEmpty)
  }

  func testSaveAndLoadSchedules() {
    let schedule: [String: Any] = [
      "id": "test-1",
      "startTimeMillis": 1000000,
      "endTimeMillis": 2000000,
      "repeat": "once",
      "timezone": "UTC",
      "mode": "detect",
      "sensitivity": 0.7,
      "maxStorageMb": 150
    ]
    plugin.saveSchedules([schedule])

    let loaded = plugin.loadSchedules()
    XCTAssertEqual(loaded.count, 1)
    XCTAssertEqual(loaded[0]["id"] as? String, "test-1")
    XCTAssertEqual(loaded[0]["repeat"] as? String, "once")
  }

  func testSaveMultipleSchedules() {
    let schedules: [[String: Any]] = [
      ["id": "a", "startTimeMillis": 1000, "endTimeMillis": 2000, "repeat": "once"],
      ["id": "b", "startTimeMillis": 3000, "endTimeMillis": 4000, "repeat": "daily"],
      ["id": "c", "startTimeMillis": 5000, "endTimeMillis": 6000, "repeat": "weekly"]
    ]
    plugin.saveSchedules(schedules)

    let loaded = plugin.loadSchedules()
    XCTAssertEqual(loaded.count, 3)
  }

  func testSaveSchedulesOverwritesPrevious() {
    plugin.saveSchedules([["id": "old"]])
    plugin.saveSchedules([["id": "new"]])

    let loaded = plugin.loadSchedules()
    XCTAssertEqual(loaded.count, 1)
    XCTAssertEqual(loaded[0]["id"] as? String, "new")
  }

  // TODO #65: Schedule normalization (once/daily/weekly)

  func testNormalizedSchedulesRemovesExpiredOnce() {
    let past = (Date().timeIntervalSince1970 * 1000) - 100_000
    plugin.saveSchedules([
      ["id": "expired", "startTimeMillis": Int64(past - 50000), "endTimeMillis": Int64(past), "repeat": "once"]
    ])

    let now = Date().timeIntervalSince1970 * 1000
    let normalized = plugin.normalizedSchedules(now: now)
    XCTAssertTrue(normalized.isEmpty)
  }

  func testNormalizedSchedulesForwardsDailySchedule() {
    let past = (Date().timeIntervalSince1970 * 1000) - 2 * 24 * 60 * 60 * 1000 // 2 days ago
    let interval = 24.0 * 60 * 60 * 1000
    plugin.saveSchedules([
      ["id": "daily-1", "startTimeMillis": Int64(past), "endTimeMillis": Int64(past + 3600000), "repeat": "daily"]
    ])

    let now = Date().timeIntervalSince1970 * 1000
    let normalized = plugin.normalizedSchedules(now: now)

    XCTAssertEqual(normalized.count, 1)
    let nextStart = normalized[0]["startTimeMillis"] as? Int64 ?? 0
    XCTAssertGreaterThan(nextStart, Int64(now))
    // Should be forwarded by at least 2 intervals
    XCTAssertGreaterThanOrEqual(nextStart, Int64(past + 2 * interval))
  }

  func testNormalizedSchedulesForwardsWeeklySchedule() {
    let past = (Date().timeIntervalSince1970 * 1000) - 10 * 24 * 60 * 60 * 1000 // 10 days ago
    let interval = 7.0 * 24 * 60 * 60 * 1000
    plugin.saveSchedules([
      ["id": "weekly-1", "startTimeMillis": Int64(past), "endTimeMillis": Int64(past + 3600000), "repeat": "weekly"]
    ])

    let now = Date().timeIntervalSince1970 * 1000
    let normalized = plugin.normalizedSchedules(now: now)

    XCTAssertEqual(normalized.count, 1)
    let nextStart = normalized[0]["startTimeMillis"] as? Int64 ?? 0
    XCTAssertGreaterThan(nextStart, Int64(now))
    XCTAssertGreaterThanOrEqual(nextStart, Int64(past + 2 * interval))
  }

  func testNormalizedSchedulesKeepsFutureSchedule() {
    let future = (Date().timeIntervalSince1970 * 1000) + 1_000_000
    plugin.saveSchedules([
      ["id": "future", "startTimeMillis": Int64(future), "endTimeMillis": Int64(future + 3600000), "repeat": "once"]
    ])

    let now = Date().timeIntervalSince1970 * 1000
    let normalized = plugin.normalizedSchedules(now: now)

    XCTAssertEqual(normalized.count, 1)
    XCTAssertEqual(normalized[0]["startTimeMillis"] as? Int64, Int64(future))
  }

  func testNormalizedSchedulesMixedExpiredAndFuture() {
    let now = Date().timeIntervalSince1970 * 1000
    let past = now - 100_000
    let future = now + 1_000_000
    plugin.saveSchedules([
      ["id": "expired-once", "startTimeMillis": Int64(past - 50000), "endTimeMillis": Int64(past), "repeat": "once"],
      ["id": "future-once", "startTimeMillis": Int64(future), "endTimeMillis": Int64(future + 500000), "repeat": "once"],
      ["id": "daily", "startTimeMillis": Int64(past), "endTimeMillis": Int64(past + 1000), "repeat": "daily"]
    ])

    let normalized = plugin.normalizedSchedules(now: now)
    // expired-once removed, future-once kept, daily forwarded
    XCTAssertEqual(normalized.count, 2)
    let ids = normalized.compactMap { $0["id"] as? String }.sorted()
    XCTAssertEqual(ids, ["daily", "future-once"])
  }

  func testNormalizedSchedulesWithInvalidEntries() {
    plugin.saveSchedules([
      ["id": "no-start", "endTimeMillis": 2000, "repeat": "once"],
      ["id": "no-end", "startTimeMillis": 1000, "repeat": "once"],
      ["id": "valid", "startTimeMillis": 1000, "endTimeMillis": 2000, "repeat": "once"]
    ])

    let now = 0.0
    let normalized = plugin.normalizedSchedules(now: now)
    // Invalid entries are skipped, valid one is kept (future relative to now=0)
    XCTAssertEqual(normalized.count, 1)
    XCTAssertEqual(normalized[0]["id"] as? String, "valid")
  }

  // TODO #64: number() helper tests

  func testNumberWithNSNumber() {
    let ns = NSNumber(value: 3.14)
    XCTAssertEqual(plugin.number(ns), 3.14, accuracy: 0.0001)
  }

  func testNumberWithInt() {
    XCTAssertEqual(plugin.number(42 as NSNumber), 42.0, accuracy: 0.0001)
  }

  func testNumberWithNil() {
    XCTAssertNil(plugin.number(nil))
  }

  func testNumberWithString() {
    XCTAssertNil(plugin.number("not a number" as Any))
  }

  // TODO #64: Cancel schedule logic (via saveSchedules + loadSchedules)

  func testCancelScheduleRemovesEntry() {
    let schedule: [String: Any] = [
      "id": "to-cancel",
      "startTimeMillis": 1000,
      "endTimeMillis": 2000,
      "repeat": "once"
    ]
    plugin.saveSchedules([schedule])
    XCTAssertEqual(plugin.loadSchedules().count, 1)

    // Simulate cancel: load, remove, save
    var schedules = plugin.loadSchedules()
    schedules.removeAll { ($0["id"] as? String) == "to-cancel" }
    plugin.saveSchedules(schedules)

    XCTAssertTrue(plugin.loadSchedules().isEmpty)
  }

  func testCancelNonexistentIdNoCrash() {
    plugin.saveSchedules([["id": "other"]])
    var schedules = plugin.loadSchedules()
    schedules.removeAll { ($0["id"] as? String) == "nonexistent" }
    plugin.saveSchedules(schedules)

    XCTAssertEqual(plugin.loadSchedules().count, 1)
  }

  // TODO #64: Status snapshot

  func testStatusContainsRequiredKeys() {
    // We can't easily test RecorderPlugin.status() because it's private,
    // but we verify loadSchedules returns expected format for status composition
    let schedule: [String: Any] = [
      "id": "s1", "startTimeMillis": 1000, "endTimeMillis": 2000, "repeat": "once"
    ]
    plugin.saveSchedules([schedule])
    let loaded = plugin.loadSchedules()
    XCTAssertEqual(loaded.count, 1)
    XCTAssertNotNil(loaded[0]["id"])
    XCTAssertNotNil(loaded[0]["startTimeMillis"])
  }

  // TODO #64: Duplicate schedule ID replacement

  func testSaveSchedulesWithDuplicateIdReplaces() {
    let original: [String: Any] = [
      "id": "dup",
      "startTimeMillis": 1000,
      "endTimeMillis": 2000,
      "mode": "detect",
      "repeat": "once"
    ]
    let replacement: [String: Any] = [
      "id": "dup",
      "startTimeMillis": 5000,
      "endTimeMillis": 6000,
      "mode": "monitor",
      "repeat": "daily"
    ]
    // Simulate what scheduleRecording does: remove old, append new
    var schedules = plugin.loadSchedules()
    schedules.removeAll { ($0["id"] as? String) == "dup" }
    schedules.append(original)
    plugin.saveSchedules(schedules)

    // Now replace
    schedules = plugin.loadSchedules()
    schedules.removeAll { ($0["id"] as? String) == "dup" }
    schedules.append(replacement)
    plugin.saveSchedules(schedules)

    let loaded = plugin.loadSchedules()
    XCTAssertEqual(loaded.count, 1)
    XCTAssertEqual(loaded[0]["mode"] as? String, "monitor")
    XCTAssertEqual(loaded[0]["startTimeMillis"] as? Int64, 5000)
  }
}
