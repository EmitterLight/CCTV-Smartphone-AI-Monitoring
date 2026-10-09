# CamFlow Background Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CamFlow keeps capturing and uploading frames to the dashboard while the app is in the background or the screen is off.

**Architecture:** Move the capture→upload pipeline out of `MainActivity` into a foreground service that owns the CameraX lifecycle binding. `MainActivity` becomes a thin UI that starts/stops the service and displays its state. Decision logic that can break in background (capture gate, reconnect backoff, upload interval) is extracted into plain Kotlin classes with no Android framework types, so it stays testable by the existing JVM unit-test setup.

**Tech Stack:** Kotlin, CameraX 1.3.4, OkHttp 4.12.0, JUnit 4 (`app/src/test/java`), Android instrumentation tests (`app/src/androidTest/java`), Gradle Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-10-09-camflow-background-capture-spec.md` — if it does not exist, the code-review findings in `README.md:1676` and `CamFlow_UserGuide.md:753,788` (background operation not implemented, listed as future work) are the spec this plan implements.

## Global Constraints

- `minSdk = 26`, `targetSdk = 36`, Java 11 (`PhoneCamSender/app/build.gradle.kts:23-24,40-43`) — every API used must exist at API 26 or be guarded by a runtime version check.
- No new third-party dependencies except `androidx.lifecycle:lifecycle-runtime-ktx` (needed for `LifecycleRegistry` in the service).
- Unit tests are plain JVM tests in `PhoneCamSender/app/src/test/java/com/example/phonecamsender/`, JUnit 4, `org.junit.Assert.*`, one test class per production class — follow `CameraStartGuardTest.kt`.
- Production classes that carry decision logic must not reference `android.*` types, so they remain unit-testable.
- Test command: `.\gradlew.bat testDebugUnitTest` from `PhoneCamSender` (CI runs `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`, `.github/workflows/ci.yml:58`).
- Commit after every task; conventional commit messages (`feat:`, `test:`, `docs:`).
- Existing behavior must not change for foreground use: interval gating, `UploadSlots(2)` with no queue, dropped-frame counting, pairing-token handling.

## Review Focus

- **Screen off / Doze** — a foreground service must keep uploading with the screen off; a plain activity-bound pipeline must not be assumed to survive. Pinned by `CaptureGateTest.serviceRunningWithoutActivityAllowsCapture`.
- **Notification permission denied (API 33+)** — the service must still run and upload when the user denies notifications; it must not crash and must not call `startForeground` with a null notification. Pinned by `CaptureNotifierTest.buildReturnsNonNullWithoutNotificationPermission`.
- **User stop while backgrounded** — `stopCamera` preference must stop capture even when the service is running. Pinned by `CaptureGateTest.userStopWinsOverRunningService`.
- **Server unreachable during background** — reconnect must retry with capped backoff, not give up permanently, and must reset after a success. Pinned by `ReconnectPlannerTest.cappedBackoffAndResetAfterSuccess`.
- **Vendor process kill (MIUI/Samsung/Huawei)** — the app cannot prevent it; the plan must make the loss observable (last-upload timestamp persisted, service restart on next launch) rather than silently assumed. Pinned by `BackgroundRecoveryTest.staleLastUploadTriggersServiceRestart`.

---

### Task 1: CaptureGate — capture decision independent of UI

**Files:**
- Create: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureGate.kt`
- Test: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/CaptureGateTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `class CaptureGate { @Volatile var serviceRunning: Boolean; @Volatile var userStopped: Boolean; fun allowCapture(): Boolean }`
  - Consumed by `FramePipeline` (Task 3) and `CaptureService` (Task 4).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun serviceRunningWithoutActivityAllowsCapture() {
    val gate = CaptureGate()
    assertFalse(gate.allowCapture())          // no UI, no service
    gate.serviceRunning = true
    assertTrue(gate.allowCapture())           // background capture is allowed
}

@Test fun userStopWinsOverRunningService() {
    val gate = CaptureGate()
    gate.serviceRunning = true
    gate.userStopped = true
    assertFalse(gate.allowCapture())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.CaptureGateTest"`
Expected: FAIL — `CaptureGate` unresolved.

- [ ] **Step 3: Implement `CaptureGate.kt`**

Two `@Volatile Boolean` fields and `allowCapture() = serviceRunning && !userStopped`. No Android imports.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.CaptureGateTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureGate.kt PhoneCamSender/app/src/test/java/com/example/phonecamsender/CaptureGateTest.kt
git commit -m "feat: add capture gate independent of UI visibility"
```

---

### Task 2: ReconnectPlanner — capped backoff

**Files:**
- Create: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/ReconnectPlanner.kt`
- Test: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/ReconnectPlannerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `class ReconnectPlanner(val baseMs: Long, val maxMs: Long)` with `fun nextDelayMs(): Long`, `fun reset(): Unit`, `val attempts: Int`
  - Consumed by `CaptureService` (Task 4).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun cappedBackoffAndResetAfterSuccess() {
    val p = ReconnectPlanner(2000, 30000)
    assertEquals(2000, p.nextDelayMs())
    assertEquals(4000, p.nextDelayMs())
    assertEquals(8000, p.nextDelayMs())
    p.reset()
    assertEquals(2000, p.nextDelayMs())
    assertEquals(1, p.attempts)
    val big = ReconnectPlanner(2000, 30000)
    repeat(6) { big.nextDelayMs() }
    assertEquals(30000, big.nextDelayMs())   // never exceeds maxMs
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.ReconnectPlannerTest"`
Expected: FAIL — `ReconnectPlanner` unresolved.

- [ ] **Step 3: Implement `ReconnectPlanner.kt`**

`nextDelayMs()` returns `min(baseMs * 2^attempts, maxMs)` then increments `attempts`; `reset()` sets `attempts = 0`. `require(baseMs > 0 && maxMs >= baseMs)`. No Android imports.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.ReconnectPlannerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/ReconnectPlanner.kt PhoneCamSender/app/src/test/java/com/example/phonecamsender/ReconnectPlannerTest.kt
git commit -m "feat: add capped reconnect backoff planner"
```

---

### Task 3: FramePipeline — interval + slot decision extracted from MainActivity

**Files:**
- Create: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/FramePipeline.kt`
- Modify: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/MainActivity.kt:315-352`
- Test: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/FramePipelineTest.kt`

**Interfaces:**
- Consumes: `CaptureGate` (Task 1), `UploadSlots` (existing, unchanged).
- Produces:
  - `interface FrameSender { fun send(jpeg: ByteArray, slot: UploadSlots.Lease) }`
  - `class FramePipeline(val gate: CaptureGate, val slots: UploadSlots, val intervalMs: Long, val sender: FrameSender, val clock: () -> Long)` with `fun handle(width: Int, height: Int): Boolean` returning whether the frame was handed to the sender
  - `val dropped: Int`, `val sent: Int`
  - Consumed by `MainActivity` (Task 6) and `CaptureService` (Task 4).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun intervalGateAndNoQueue() {
    val sent = mutableListOf<Int>()
    val gate = CaptureGate(); gate.serviceRunning = true
    val slots = UploadSlots(2)
    var t = 0L
    val p = FramePipeline(gate, slots, 120, FrameSender { _, lease -> sent.add(1); lease.close() }, { t })

    t = 0;   assertTrue(p.handle(640, 480))
    t = 50;  assertFalse(p.handle(640, 480))   // inside interval
    t = 130; assertTrue(p.handle(640, 480))

    gate.userStopped = true
    assertFalse(p.handle(640, 480))            // user stop, even with service running
    assertEquals(2, sent.size)
    assertEquals(0, p.dropped)
}

@Test fun slotExhaustionDropsFrameInsteadOfQueueing() {
    val gate = CaptureGate(); gate.serviceRunning = true
    val slots = UploadSlots(1)
    val p = FramePipeline(gate, slots, 0, FrameSender { _, lease -> lease.close() }, { 0L })
    assertTrue(p.handle(640, 480))
    val busy = FramePipeline(gate, slots, 0, FrameSender { _, lease -> /* never released */ }, { 0L })
    assertTrue(busy.handle(640, 480))
    assertFalse(busy.handle(640, 480))
    assertEquals(1, busy.dropped)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.FramePipelineTest"`
Expected: FAIL — `FramePipeline` unresolved.

- [ ] **Step 3: Implement `FramePipeline.kt`**

`handle()` order must match the current analyzer: check `gate.allowCapture()` first, then interval against `clock()`, then `slots.tryAcquire()` (drop + `dropped++` when null), then `sender.send(jpeg, lease)`. The pipeline does not encode JPEG — encoding stays in the caller, which passes bytes through `FrameSender`. `sent`/`dropped` are `@Volatile Int`.

- [ ] **Step 4: Replace the inline decision block in `MainActivity.kt:315-352`**

The analyzer calls `pipeline.handle(width, height)`; when it returns true the activity encodes and sends through a `FrameSender` that delegates to the existing `uploadFrame()`. Keep `resolutionStatus.record(...)` in the analyzer.

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS, including existing `CameraStartGuardTest`, `UploadSlotsTest`, `UploadDiagnosticsTest`.

- [ ] **Step 6: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/FramePipeline.kt PhoneCamSender/app/src/test/java/com/example/phonecamsender/FramePipelineTest.kt PhoneCamSender/app/src/main/java/com/example/phonecamsender/MainActivity.kt
git commit -m "refactor: extract frame interval and slot decision into FramePipeline"
```

---

### Task 4: Manifest — permissions and service declaration

**Files:**
- Modify: `PhoneCamSender/app/src/main/AndroidManifest.xml:4-5,17-26`

**Interfaces:**
- Produces: `<service android:name=".CaptureService" android:exported="false" android:foregroundServiceType="camera">`, permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `WAKE_LOCK`, `POST_NOTIFICATIONS`.

- [ ] **Step 1: Add the permissions and the `<service>` block**

`foregroundServiceType="camera"` is API 29+; on API 26-28 the attribute is ignored, which is acceptable — the runtime branch in Task 5 handles it.

- [ ] **Step 2: Verify the merged manifest**

Run: `.\gradlew.bat processDebugManifest`
Expected: merged `AndroidManifest.xml` under `build/intermediates/` contains `FOREGROUND_SERVICE_CAMERA` and `<service android:name=".CaptureService">`.

- [ ] **Step 3: Commit**

```bash
git add PhoneCamSender/app/src/main/AndroidManifest.xml
git commit -m "feat: declare foreground camera service and wake lock permissions"
```

---

### Task 5: CaptureNotifier — notification built without crashing on denied permission

**Files:**
- Create: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureNotifier.kt`
- Test: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/CaptureNotifierTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object CaptureNotifier { const val CHANNEL_ID = "camflow_capture"; const val NOTIFICATION_ID = 4201; fun build(context: Context, statusText: String): Notification? }`
  - Consumed by `CaptureService` (Task 6).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun buildReturnsNonNullWithoutNotificationPermission() {
    // Context is injected through a fake; the object must not throw when
    // POST_NOTIFICATIONS is not granted, and must return null only when
    // the channel cannot be created.
    val n = CaptureNotifier.build(FakeContext(), "CamFlow running")
    assertNotNull(n)
}

@Test fun channelAndIdConstantsAreStable() {
    assertEquals("camflow_capture", CaptureNotifier.CHANNEL_ID)
    assertEquals(4201, CaptureNotifier.NOTIFICATION_ID)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.CaptureNotifierTest"`
Expected: FAIL — `CaptureNotifier` unresolved.

- [ ] **Step 3: Implement `CaptureNotifier.kt`**

Create the channel with `IMPORTANCE_LOW`, build a notification with `setOngoing(true)`, `setContentTitle("CamFlow")`, `setSmallIcon(R.mipmap.ic_launcher)`, `setShowWhen(false)`. Return `null` only if the channel creation throws; never throw. Use `NotificationChannel` guarded by `Build.VERSION.SDK_INT >= 26` (already satisfied by minSdk 26).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.CaptureNotifierTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureNotifier.kt PhoneCamSender/app/src/test/java/com/example/phonecamsender/CaptureNotifierTest.kt
git commit -m "feat: add capture notification builder tolerant of denied notification permission"
```

---

### Task 6: CaptureService — owns CameraX binding and the pipeline

**Files:**
- Create: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt`
- Modify: `PhoneCamSender/app/build.gradle.kts:50-57` (add `androidx.lifecycle:lifecycle-runtime-ktx`)
- Test: `PhoneCamSender/app/src/androidTest/java/com/example/phonecamsender/CaptureServiceTest.kt`

**Interfaces:**
- Consumes: `CaptureGate` (Task 1), `ReconnectPlanner` (Task 2), `FramePipeline` + `FrameSender` (Task 3), `CaptureNotifier` (Task 5), `UploadSlots`, `UploadHttp`, `DeviceCredentials`, `CameraStartGuard`, `ResolutionStatus`.
- Produces:
  - `class CaptureService : Service(), LifecycleOwner`
  - `companion object { fun start(context: Context, gate: CaptureGate); fun stop(context: Context) }`
  - `val gate: CaptureGate` (the same instance the UI passes in)
  - `val pipeline: FramePipeline`

- [ ] **Step 1: Write the failing instrumentation test**

```kotlin
@Test fun serviceStartsForegroundAndKeepsGateOpenWhenNoActivity() {
    val gate = CaptureGate()
    CaptureService.start(context, gate)
    assertTrue(gate.serviceRunning)
    // No activity is in the foreground here; capture must still be allowed.
    assertTrue(gate.allowCapture())
    CaptureService.stop(context)
    assertFalse(gate.serviceRunning)
    assertFalse(gate.allowCapture())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat assembleDebugAndroidTest`
Expected: FAIL — `CaptureService` unresolved.

- [ ] **Step 3: Implement `CaptureService.kt`**

`onCreate`: create `LifecycleRegistry(this)`, `UploadSlots(2)`, `CaptureGate`, `ReconnectPlanner(2000, 30000)`, acquire `PARTIAL_WAKE_LOCK`. `onStartCommand`: `ServiceCompat.startForeground(this, CaptureNotifier.NOTIFICATION_ID, CaptureNotifier.build(this, status), if (Build.VERSION.SDK_INT >= 34) ServiceCompat.FOREGROUND_SERVICE_TYPE_CAMERA else 0)`; return `START_STICKY`. CameraX is bound to the service's `LifecycleOwner`, not to an activity; `Preview` is omitted (no surface in a service), only `ImageAnalysis` is bound. `onDestroy`: `cameraProvider.unbindAll()`, `wakeLock.release()`, `pipeline` slots `stopAccepting()`, cancel active calls.

- [ ] **Step 4: Run the instrumentation test on a device**

Run: `.\gradlew.bat installDebug connectedDebugAndroidTest --tests "com.example.phonecamsender.CaptureServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt PhoneCamSender/app/src/androidTest/java/com/example/phonecamsender/CaptureServiceTest.kt PhoneCamSender/app/build.gradle.kts
git commit -m "feat: add foreground capture service owning CameraX lifecycle"
```

---

### Task 7: MainActivity becomes UI only

**Files:**
- Modify: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/MainActivity.kt:94-144,294-378,483-495`

**Interfaces:**
- Consumes: `CaptureService` (Task 6), `CaptureGate` (Task 1).
- Produces: no new public API; `MainActivity` no longer owns `cameraExecutor`, `uploadSlots`, or the analyzer.

- [ ] **Step 1: Write the failing test for the UI-side gate sync**

```kotlin
@Test fun settingsChangeUpdatesGateWithoutRestartingCamera() {
    val gate = CaptureGate(); gate.serviceRunning = true
    gate.userStopped = true          // Settings switch ON
    assertFalse(gate.allowCapture())
    gate.userStopped = false         // Settings switch OFF
    assertTrue(gate.allowCapture())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.MainActivityGateSyncTest"`
Expected: FAIL — test class does not exist yet.

- [ ] **Step 3: Implement `MainActivityGateSyncTest.kt` and the matching `GateSync.apply(gate, prefs)` helper in `MainActivity`'s package**

`GateSync.apply(gate: CaptureGate, userStopped: Boolean)` sets `gate.userStopped`. `onResume` calls it and starts the service if `!userStopped`.

- [ ] **Step 4: Remove the activity-owned pipeline**

Delete `cameraExecutor`, `uploadSlots`, `activeUploads`, `uploadFrame()`, `startCamera()` from `MainActivity`; keep `PreviewView` for foreground preview only, and read status from the service through a `LocalBinder`-style reference. `onDestroy` no longer stops capture — stopping capture must require the explicit `stopCamera` setting.

- [ ] **Step 5: Run tests and build**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/MainActivity.kt PhoneCamSender/app/src/main/java/com/example/phonecamsender/GateSync.kt PhoneCamSender/app/src/test/java/com/example/phonecamsender/MainActivityGateSyncTest.kt
git commit -m "refactor: MainActivity drives the capture service instead of owning the pipeline"
```

---

### Task 8: Reconnect and background-recovery behavior

**Files:**
- Modify: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt`
- Create: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/BackgroundRecovery.kt`
- Test: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/BackgroundRecoveryTest.kt`

**Interfaces:**
- Consumes: `ReconnectPlanner` (Task 2), `DeviceCredentials` (existing).
- Produces:
  - `class BackgroundRecovery(val staleAfterMs: Long) { fun shouldRestart(lastUploadSuccessMs: Long, nowMs: Long): Boolean }`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun staleLastUploadTriggersServiceRestart() {
    val r = BackgroundRecovery(120_000)
    assertTrue(r.shouldRestart(0, 120_000))            // never uploaded
    assertFalse(r.shouldRestart(100_000, 150_000))     // recent success
    assertTrue(r.shouldRestart(100_000, 220_000))      // stale
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.BackgroundRecoveryTest"`
Expected: FAIL — `BackgroundRecovery` unresolved.

- [ ] **Step 3: Implement `BackgroundRecovery.kt`**

`shouldRestart(nowMs - lastUploadSuccessMs >= staleAfterMs)`. No Android imports.

- [ ] **Step 4: Wire it in `CaptureService`**

On each upload failure, schedule the next ping with `ReconnectPlanner.nextDelayMs()`; on success call `ReconnectPlanner.reset()`. On service start, if `BackgroundRecovery(120_000).shouldRestart(prefs lastUploadSuccess, now)` then re-verify the saved `baseUrl` before binding CameraX.

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add PhoneCamSender/app/src/main/java/com/example/phonecamsender/BackgroundRecovery.kt PhoneCamSender/app/src/test/java/com/example/phonecamsender/BackgroundRecoveryTest.kt PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt
git commit -m "feat: add capped reconnect and stale-upload recovery in the service"
```

---

### Task 9: Documentation of what the app cannot control

**Files:**
- Modify: `CamFlow_UserGuide.md:753,788`
- Modify: `CamFlow_UserGuide_CN.md` (mirror section)

**Interfaces:**
- Produces: a "Background operation" section stating the foreground service behavior and the device-side settings the user must set.

- [ ] **Step 1: Move background execution from "future optimizations" to a documented section**

State: foreground service with `camera` type and partial wake lock; Doze and vendor killers (MIUI, Samsung, Huawei, Oppo) can still kill the process and the app cannot prevent it; the user must disable battery optimization for CamFlow, allow background activity and autostart, and grant notification permission.

- [ ] **Step 2: State the known limitation explicitly**

`stopCamera` remains a user-controlled stop that the service honors even in background; a process kill is not recoverable until the app is opened again.

- [ ] **Step 3: Verify no stale claim remains**

Run: `rg -n "background" CamFlow_UserGuide.md CamFlow_UserGuide_CN.md README.md`
Expected: no line claims background operation is unimplemented.

- [ ] **Step 4: Commit**

```bash
git add CamFlow_UserGuide.md CamFlow_UserGuide_CN.md
git commit -m "docs: document background operation and vendor battery policy limits"
```

---

## Verification Gate

Run before declaring the plan complete:

```bash
cd PhoneCamSender
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon
```

Then one real-device check, which no automated test can replace: start CamFlow, press home, turn the screen off, wait 5 minutes, and confirm `log/server.log` still shows `upload begin` lines. If it stops, the cause is vendor policy, not this plan — record the device model and Android version in the report.
