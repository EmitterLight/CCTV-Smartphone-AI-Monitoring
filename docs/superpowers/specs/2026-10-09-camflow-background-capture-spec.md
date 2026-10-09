# Spec: CamFlow Background Capture

**Plan:** `2026-10-09-camflow-background-capture.md` (this spec is the source of truth the plan implements).

## Objective

CamFlow (the Android phone-capture app in `PhoneCamSender`) must keep capturing frames and uploading them to the Sentinel dashboard while the app is in the background or the screen is off. Today the capture→upload pipeline lives inside `MainActivity`, so it stops when the activity is not in the foreground; background execution is only listed as future work (`README.md:1676`, `CamFlow_UserGuide.md:788`, `CamFlow_UserGuide_CN.md:705`).

Users: a single phone mounted as a fixed CCTV camera, uploading to a PC dashboard on the same LAN. Success = frames keep arriving for minutes with the screen off, and the loss of capture is *observable* when the vendor OS kills the process.

Why the pipeline must move out of the activity:
- A foreground service (not an activity) is the only construct that survives screen-off/Doze.
- Decision logic that can break in background (capture gate, backoff, interval) must stay testable on the JVM, so it is extracted into classes with no `android.*` types.

### User stories / acceptance criteria
1. With CamFlow started and the screen off, the dashboard keeps receiving frames.
2. If the user denies notification permission (API 33+), capture still runs; the app does not crash.
3. If the user turns on the `stopCamera` setting, capture stops even while the service is running.
4. If the server is unreachable during background capture, the app retries with capped backoff and recovers after a success.
5. If a vendor (MIUI/Samsung/Huawei/Oppo) kills the process, the app cannot prevent it — but the loss is observable: the last successful upload time is persisted and a stale value triggers re-verification/restart on the next launch.

## Assumptions (correct me before implementation)

1. "Background" means the process is alive but no activity is visible; the spec does **not** promise survival of a vendor process kill (Android cannot guarantee it at API 26–36).
2. Only `ImageAnalysis` is bound in the service — no `Preview` surface, because a service has no surface. Foreground preview stays in `MainActivity`.
3. No queue is introduced: dropped frames stay dropped (`UploadSlots(2)`, no queueing) — existing foreground behavior must not change.
4. The service is started by `MainActivity` and is not auto-started on boot; recovery after a process kill happens on the next app open only.
5. `stopCamera` remains the only user stop; `onDestroy` of the activity no longer stops capture.
6. The single new dependency allowed is `androidx.lifecycle:lifecycle-runtime-ktx` (for `LifecycleRegistry`); everything else stays as in `PhoneCamSender/app/build.gradle.kts:49-65`.
7. The existing pairing-token and `DeviceCredentials` behavior is unchanged; the service uses it as-is.

## Tech Stack

- Kotlin, Android app module `PhoneCamSender`
- `minSdk = 26`, `targetSdk = 36`, Java 11 (`PhoneCamSender/app/build.gradle.kts:23-24,40-43`)
- CameraX 1.3.4 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- OkHttp 4.12.0
- JUnit 4 for JVM unit tests; `androidx.test` + Espresso for instrumentation tests
- Gradle Kotlin DSL; CI: `.github/workflows/ci.yml:58`

## Commands

Run from `PhoneCamSender`:

```powershell
# Unit tests (all)
.\gradlew.bat testDebugUnitTest

# Single test class
.\gradlew.bat testDebugUnitTest --tests "com.example.phonecamsender.CaptureGateTest"

# Full CI-equivalent gate
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon

# Merged manifest check (foreground service + permissions)
.\gradlew.bat processDebugManifest

# Instrumentation test on a device
.\gradlew.bat installDebug connectedDebugAndroidTest --tests "com.example.phonecamsender.CaptureServiceTest"
```

## Project Structure

```
PhoneCamSender/app/src/main/java/com/example/phonecamsender/
├── CaptureGate.kt          → capture decision, no UI dependency (no android.* imports)
├── ReconnectPlanner.kt     → capped exponential backoff (no android.* imports)
├── FramePipeline.kt        → interval + slot decision (no android.* imports)
├── BackgroundRecovery.kt   → stale-last-upload rule (no android.* imports)
├── GateSync.kt             → prefs → gate mapping
├── CaptureNotifier.kt      → notification/channel builder, never throws
├── CaptureService.kt       → foreground service, owns CameraX binding + pipeline
├── UploadSlots.kt          → existing, unchanged
├── UploadDiagnostics.kt    → existing, unchanged
├── CameraStartGuard.kt     → existing, unchanged
├── DeviceCredentials.kt    → existing, unchanged
├── ImageUtil.kt            → existing, unchanged (JPEG encoding stays in the caller)
├── MainActivity.kt         → UI only: starts/stops the service, shows status
└── SettingsActivity.kt     → unchanged

PhoneCamSender/app/src/test/java/com/example/phonecamsender/       → JVM unit tests, one class per production class
PhoneCamSender/app/src/androidTest/java/com/example/phonecamsender/ → service lifecycle instrumentation tests
PhoneCamSender/app/src/main/AndroidManifest.xml                    → permissions + <service> declaration
docs/superpowers/specs/2026-10-09-camflow-background-capture-spec.md → this spec
```

## Code Style

Decision classes are pure Kotlin, no `android.*` imports, cross-thread state via `@Volatile`, invalid constructor arguments rejected with `require()`.

```kotlin
package com.example.phonecamsender

/** Capture is allowed when the service owns the camera and the user did not stop it. */
class CaptureGate {
    @Volatile var serviceRunning: Boolean = false
    @Volatile var userStopped: Boolean = false

    fun allowCapture(): Boolean = serviceRunning && !userStopped
}
```

Conventions:
- One test class per production class, JUnit 4, `org.junit.Assert.*`, single-purpose test names that state the behavior (`serviceRunningWithoutActivityAllowsCapture`) — follow `CameraStartGuardTest.kt`.
- `handle()` order in `FramePipeline` must match the current analyzer: gate → interval → slot → sender (`MainActivity.kt:315-352`).
- Frame encoding is not the pipeline's job: JPEG encoding stays in the caller and reaches the pipeline through `FrameSender`.
- Version-gated APIs are guarded at runtime (`Build.VERSION.SDK_INT`), never assumed.
- Conventional commits (`feat:`, `test:`, `refactor:`, `docs:`), one commit per task; every change logged in `PROGRESS.md`.

## Testing Strategy

- **JVM unit tests** (`app/src/test/java`) pin the decision logic. No Robolectric, no `android.*` types in production classes under test.
- **Instrumentation tests** (`app/src/androidTest/java`) pin service lifecycle only (start → foreground → gate open with no activity → stop → gate closed).
- **Manifest test** via merged manifest (`processDebugManifest`): `FOREGROUND_SERVICE_CAMERA` and `<service android:name=".CaptureService">` must be present.
- **Pinned behaviors** (each has a named test):

| Concern | Test |
|---|---|
| Screen off / Doze: service running without an activity still allows capture | `CaptureGateTest.serviceRunningWithoutActivityAllowsCapture` |
| User stop wins over a running service | `CaptureGateTest.userStopWinsOverRunningService` |
| Notification permission denied (API 33+) → non-null notification, no throw | `CaptureNotifierTest.buildReturnsNonNullWithoutNotificationPermission` |
| Notification channel/id constants are stable | `CaptureNotifierTest.channelAndIdConstantsAreStable` |
| Interval gate + no queue + user stop | `FramePipelineTest.intervalGateAndNoQueue` |
| Slot exhaustion drops instead of queues | `FramePipelineTest.slotExhaustionDropsFrameInsteadOfQueueing` |
| Capped backoff + reset after success | `ReconnectPlannerTest.cappedBackoffAndResetAfterSuccess` |
| Stale last upload triggers restart | `BackgroundRecoveryTest.staleLastUploadTriggersServiceRestart` |
| Gate sync from settings without restarting the camera | `MainActivityGateSyncTest.settingsChangeUpdatesGateWithoutRestartingCamera` |
| Service lifecycle on device | `CaptureServiceTest.serviceStartsForegroundAndKeepsGateOpenWhenNoActivity` |

- Existing tests must keep passing: `CameraStartGuardTest`, `UploadSlotsTest`, `UploadDiagnosticsTest`, `ResolutionStatusTest`, `NetworkDiscoverTest`, `DeviceCredentialsTest`.
- **Not automatable:** one real-device check — start CamFlow, press home, turn the screen off, wait 5 minutes, confirm `log/server.log` still shows `upload begin`. If it stops, the cause is vendor policy; record device model and Android version.

## Boundaries

**Always:**
- Write the failing test first, then implement (TDD order per task).
- Keep decision classes free of `android.*` imports so they stay JVM-testable.
- Guard every API above minSdk 26 with a runtime version check.
- Run `testDebugUnitTest` before each commit; run the full CI gate before declaring the plan complete.
- Log every change in `PROGRESS.md` (reverse-chronological).

**Ask first:**
- Adding any dependency beyond `androidx.lifecycle:lifecycle-runtime-ktx`.
- Changing CI config, `minSdk`/`targetSdk`, or the pairing/token flow.
- Auto-start on boot, `START_NOT_INVALID` vs `START_STICKY` semantics, or any change to `UploadSlots(2)`.

**Never:**
- Claim background survival that Android cannot guarantee (vendor killers, Doze) in code, docs, or reports.
- Call `startForeground` with a null notification.
- Remove or weaken an existing failing-test-first step, or delete a test to make the build pass.
- Queue frames (the no-queue rule is existing behavior).

## Success Criteria

1. `.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon` passes with the new test classes listed above.
2. Merged manifest contains `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `WAKE_LOCK`, `POST_NOTIFICATIONS` and `<service android:name=".CaptureService" android:exported="false" android:foregroundServiceType="camera">`.
3. `CaptureService` owns the CameraX `LifecycleOwner`; `MainActivity` no longer owns `cameraExecutor`, `uploadSlots`, `activeUploads`, `uploadFrame()` or `startCamera()`.
4. Foreground behavior is unchanged: interval gating, `UploadSlots(2)` with no queue, dropped-frame counting, pairing-token handling.
5. Denied notification permission does not crash the service and does not stop uploads.
6. `stopCamera` stops capture even when the service is running; `onDestroy` alone does not.
7. Reconnect retries with backoff capped at 30 s and resets after a success.
8. Last successful upload time is persisted; a stale value (>120 s) triggers re-verification of the saved `baseUrl` on service start.
9. `CamFlow_UserGuide.md` / `CamFlow_UserGuide_CN.md` contain a "Background operation" section, and no line still claims background operation is unimplemented (`rg -n "background" CamFlow_UserGuide.md CamFlow_UserGuide_CN.md README.md`).
10. The 5-minute screen-off real-device check is recorded with device model and Android version.

## Open Questions

1. Should the service auto-start on boot / after a vendor kill without reopening the app? (Currently out of scope — Android cannot reliably do this.)
2. Should `MainActivity` show the service state as a persistent status line, or keep the current status text only?
3. Is `PARTIAL_WAKE_LOCK` acceptable for the target devices (battery cost), or should it be opt-in in Settings?
4. Notification tap behavior: open `MainActivity` (existing behavior) or nothing?
5. Do we need a `FOREGROUND_SERVICE_IMMEDIATE` / `START_STICKY` re-attempt path for API 34+ foreground-launch restrictions when the app is opened from a non-foreground state?
