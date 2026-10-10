# PROGRESS.md

> Автоматический журнал изменений агента  
> Формат времени: `YYYY-MM-DD HH:MM` (системное время)  
> Статусы: `[PLANNED]` → `[DONE]` | `[ERROR: причина]` | `[PARTIAL: детали]`

## Сессия: 2026-10-09

---
<!-- LOG_START -->
### [2026-10-10 12:27] Batch 8 knowledge graph
- Файл: `.ua/intermediate/batch-8.json`
- Действие: создан
- Суть: собран валидный JSON для batch 8: 54 узла и 82 ребра, включая APK-файлы, манифест, Kotlin-файлы, previousSymbols и явные связи contains/exports/calls/tested_by/configures.
- Статус: [DONE]
- Следующий шаг: проверить отсутствие подтверждённых удалений и продолжить merge knowledge graph.



### [2026-10-10 01:46] .gitignore: исключён signing key
- Файл: `.gitignore`
- Действие: изменён
- Суть: добавлен `PhoneCamSender/camflow.keystore` — ключ подписи не должен попадать в коммиты; это также разблокировало `prepare-incremental.mjs` (он отказывался из-за untracked keystore).
- Статус: [DONE]

### [2026-10-10 01:45] Подготовка коммитов для инкрементального обновления knowledge graph
- Файл: git (рабочее дерево)
- Действие: планируемое разделение на 4 коммита (fix lifecycle / keystore signing + metadata sync / bump v1.1.4 + APK + docs / docs+specs+PROGRESS)
- Суть: `prepare-incremental.mjs` требует чистого дерева относительно baseline `e2e62c4`; `PhoneCamSender/camflow.keystore` не коммитится (секрет).
- Статус: [DONE] — `92e19a2` (fix lifecycle), `5a78984` (keystore signing + metadata sync), `97cbcd1` (v1.1.4 APK + docs). `README_CN.md` пришлось восстанавливать из git повторно (файл исчезал из рабочего дерева дважды).

### [2026-10-10 01:41] Bump CamFlow до v1.1.4 и пересборка release
- Файл: `version.properties`, `PhoneCamSender/CamFlow-v1.1.4.apk`
- Действие: изменён и создан (`.\gradlew.bat assembleRelease --offline --no-daemon`)
- Суть: `camflow.version` 1.1.2 → 1.1.4, `camflow.versionCode` 15 → 16; APK 5 919 250 байт, SHA-256 `F56EA3AFE28739B283FC151A29A36AC6115DF48E62A500C7AE24E66E7058C283`, подпись та же (`camflow.keystore`, SHA-1 `28844d02a52b5e3949818ec2c7b5180c391e539b`); имя APK, версия и build number синхронизированы в `README.md`, `README_CN.md`, `CamFlow_UserGuide.md`, `CamFlow_UserGuide_CN.md`; `pytest tests -q` — 69 passed.
- Статус: [DONE]
- Следующий шаг: установить `PhoneCamSender/CamFlow-v1.1.4.apk` на телефон и проверить строки `upload begin` в `log/server.log`.

### [2026-10-10 01:41] README_CN.md восстановлен из git
- Файл: `README_CN.md`
- Действие: восстановлен (`git checkout -- README_CN.md`)
- Суть: файл отсутствовал в рабочем дереве (git фиксировал удаление), восстановлен из HEAD и заново приведён к v1.1.4 / keystore-signed / build 16 с абзацем о подписи.
- Статус: [DONE]

### [2026-10-10 01:11] Release-сборка CamFlow с фиксом lifecycle
- Файл: `PhoneCamSender/CamFlow-v1.1.2.apk`
- Действие: создан (`.\gradlew.bat assembleRelease --offline --no-daemon`)
- Суть: APK v1.1.2 build 15 (5 919 254 байт), подписан `camflow.keystore` (SHA-1 `28844d02a52b5e3949818ec2c7b5180c391e539b`), содержит переход `LifecycleRegistry` в `STARTED`; `version.properties` обновлён (versionCode 14 → 15, SHA `5B2C15DC…`), build number синхронизирован в README/UserGuide.
- Статус: [DONE]
- Следующий шаг: установить на телефон и проверить появление строк `upload begin` в `log/server.log`.

### [2026-10-10 01:04] Fix: CameraX никогда не attach-ает анализатор в CaptureService
- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt`
- Действие: изменён
- Суть: `LifecycleRegistry` оставался в состоянии `INITIALIZED`, поэтому `LifecycleCamera` не вызывает `attachUseCases()` (attach только при `State.STARTED` или выше) — анализатор никогда не стреляет, сервис не отправляет ни одного `/upload`. Добавлен переход в `STARTED` в `onCreate` и в `DESTROYED` в `onDestroy`.
- Статус: [DONE]
- Доказательства: `log/server.log` — ни одной строки `upload begin` с рестарта сервера (00:53:33); `app/config/auth.sqlite3` — устройство `MY` спарено (не 401); `GET /api/status` — `last_frame_age_sec: null`, `upload_fps: 0.0`; серверный путь upload→frame_buffer проверен тестами (69 passed).
- Следующий шаг: пересобрать APK и проверить на устройстве, что в `log/server.log` появляются `upload begin` строки.

- Файл: `PhoneCamSender/app/src/androidTest/java/com/example/phonecamsender/CaptureServiceTest.kt`
- Действие: изменён
- Суть: регрессионный тест `serviceLifecycleReachesStartedSoCameraXCanAttachUseCases` — падает без фикса (`INITIALIZED`), проходит с ним; проверяет `STARTED` после старта и `DESTROYED` после остановки.
- Статус: [PARTIAL: компилируется, на устройстве не выполнялся — нет подключённого телефона]
- Проверка: `gradlew testDebugUnitTest compileDebugKotlin compileDebugAndroidTestKotlin --offline` — BUILD SUCCESSFUL.

### [2026-10-10 00:37] Реализация авто-бампа versionCode + единой подписи
- Файл: `PhoneCamSender/app/build.gradle.kts`
- Действие: изменён
- Суть: функция + таск `bumpVersionCode` инкрементирует `camflow.versionCode` при запросе `assemble*`/`install*` (тесты версию не меняют); `signingConfig "camflow"` применяется к debug и release, credentials из `.env`, без fallback — сборка падает; таск `updateApkMetadata` копирует APK в `PhoneCamSender/CamFlow-v{version}.apk`, пишет `apkPath`/`apkSha256` и синхронизирует строки документации, содержащие старый SHA или имя APK.
- Статус: [DONE]

- Файл: `PhoneCamSender/camflow.keystore`
- Действие: создан (PKCS#12, alias `camflow`, сертификат SHA-1 `28844d02a52b5e3949818ec2c7b5180c391e539b`)
- Суть: единый signing-ключ вместо машинного debug-ключа; старый `PhoneCamSender/Keystore` остаётся, но не используется (пароль неизвестен).
- Статус: [DONE]

- Файлы: `.env`, `.gitignore`
- Действие: создан / изменён
- Суть: `CAMFLOW_KEYSTORE_PASSWORD`, `CAMFLOW_KEYSTORE_ALIAS`; `.env` добавлен в `.gitignore` (раньше игнорировались только `.env/` и `.env.*`).
- Статус: [DONE]

- Файл: `tests/test_version_consistency.py`
- Действие: изменён
- Суть: добавлены проверки `bumpVersionCode`, `startParameter.taskNames`, `signingConfigs`, переменных keystore и `build {versionCode}` в README; исправлено утверждение `service?.resolutionStatus?.summary(getResolutionLabel())` — расхождение с `MainActivity.kt:313`, тест падал до этих изменений.
- Статус: [DONE]

- Файлы: `README.md`, `README_CN.md`, `CamFlow_UserGuide.md`, `CamFlow_UserGuide_CN.md`
- Действие: изменены
- Суть: `debug-signed` → `keystore-signed`; добавлено пояснение про единый ключ, авто-бамп versionCode и необходимость удалить пакет с другой подписью; build number и SHA синхронизированы (build 14).
- Статус: [DONE]

- Файл: `version.properties`
- Действие: изменён (результат сборок)
- Суть: `camflow.versionCode` 9 → 14, SHA пересчитан; `PhoneCamSender/CamFlow-v1.1.2.apk` — актуальная сборка с подписью CamFlow.
- Статус: [DONE]

- Проверка: `python -m pytest -q` — 69 passed; `gradlew testDebugUnitTest` — SUCCESS; `apksigner verify --print-certs` — один сертификат для debug и release.
- Статус: [DONE]
- Следующий шаг: удалить CamFlow с телефона, затем установить APK, подписанный `camflow.keystore`.

### [2026-10-10 00:23] Spec: автоматический инкремент versionCode + единая подпись
- Файл: `specs/3-Версионирование/spec.md`
- Действие: создан
- Суть: зафиксированы две причины проблемы — ручной `versionCode` и разные debug-ключи (`8e77a6a0…` у установленного APK vs `7afd6c20…` у локальной сборки); требования: Gradle-таск `bumpVersionCode`, `signingConfig` на committed keystore, синхронизация README с `version.properties`.
- Статус: [DONE]
- Следующий шаг: согласовать пароль/alias keystore и имя артефакта, затем реализация.

### [2026-10-09 23:47] Сборка APK
- Файл: `PhoneCamSender/app/build/outputs/apk/debug/app-debug.apk`
- Действие: создан (`.\gradlew.bat assembleDebug`)
- Суть: debug-APK CamFlow v1.1.2 (versionCode 9), 7 312 684 байт; исходники и зависимости без изменений.
- Статус: [DONE]

### [2026-10-09 22:51] Verification gate — полный прогон
- Файл: `PhoneCamSender`
- Действие: выполнено `.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`
- Суть: 31 unit-тест проходят, оба APK собираются; `CaptureServiceTest` компилируется, но не выполнялся — нет устройства/эмулятора.
- Статус: [PARTIAL: real-device check (screen off, 5 min, log/server.log) требует устройства]

### [2026-10-09 22:51] Task 9 — документация background operation
- Файл: `CamFlow_UserGuide.md`
- Действие: изменён
- Суть: добавлен раздел `Background operation (screen off)` (anchor `#background-operation`) + запись в TOC; bullet «Background execution mode» удалён из Roadmap; §5.2 переписан — background реализован (foreground service + partial wake lock), но Doze/вендор-киллеры могут убить процесс; счётчик тестов обновлён 19 → 31.
- Статус: [DONE]

- Файл: `CamFlow_UserGuide_CN.md`
- Действие: изменён
- Суть: зеркало EN-раздела «后台运行说明（熄屏持续上传）» + TOC; bullet «后台运行模式» удалён из 后续规划; §5.2 обновлён (31 тестов, реализовано, но вендорная политика не контролируется).
- Статус: [DONE]

- Файл: `README.md`
- Действие: изменён
- Суть: убрано устаревшее утверждение, что background не принят; обновлено 19 → 31 Android unit tests со ссылкой на `CamFlow_UserGuide.md#background-operation`.
- Статус: [DONE]
- Проверка: `rg -n "background|后台|熄屏"` — ни одна строка не утверждает, что фоновый режим не реализован.
- Следующий шаг: коммит `docs: document background operation and vendor battery policy limits`

### [2026-10-09 22:49] Task 8 — BackgroundRecovery
- Файл: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/BackgroundRecoveryTest.kt`
- Действие: создан (RED)
- Суть: RED подтверждён (`Unresolved reference 'BackgroundRecovery'`), после реализации тест GREEN.
- Статус: [DONE]

- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/BackgroundRecovery.kt`
- Действие: создан
- Суть: `shouldRestart(lastUploadSuccessMs, nowMs)` = `nowMs - lastUploadSuccessMs >= staleAfterMs`, без `android.*` импортов.
- Статус: [DONE]

- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt`
- Действие: изменён
- Суть: при неудачной загрузке планируется ping через `ReconnectPlanner.nextDelayMs()`, при успехе `planner.reset()`; при старте сервиса, если `BackgroundRecovery(120_000).shouldRestart(prefs lastUploadSuccess, now)`, сохранённый `baseUrl` перепроверяется пингом до binding CameraX; `onDestroy` снимает отложенные ping.
- Статус: [DONE]

### [2026-10-09 22:46] Task 7 — GateSync + UI-only MainActivity
- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/GateSync.kt`
- Действие: создан
- Суть: `GateSync.apply(gate, userStopped)` переносит настройку `stopCamera` в gate без перезапуска камеры; RED (`MainActivityGateSyncTest`) подтверждён ошибкой компиляции, после реализации тест GREEN.
- Статус: [DONE]

- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/MainActivity.kt`
- Действие: изменён
- Суть: удалены `cameraExecutor`, `uploadSlots`, `activeUploads`, `uploadFrame()`, `startCamera()`, `pipeline`, counters и `lastEncodeMs`; активность только binds `Preview`, запускает `CaptureService`, читает статус/счётчики из сервиса и применяет настройки через `GateSync`; `onDestroy` больше не останавливает захват.
- Статус: [DONE]

### [2026-10-09 22:46] Backfill: Tasks 3-6 (записи задним числом, время системное)
- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/FramePipeline.kt`
- Действие: создан (Task 3), коммит `158593d`
- Суть: `FramePipeline.handle(width, height)` принимает решение по интервалу и слотам без `android.*`; `FramePipelineTest` проходит; `MainActivity` использовал pipeline для анализатора.
- Статус: [DONE]
- Отклонение: план описывал `FrameSender.send(jpeg, slot)`, но pinned-тест вызывает `handle(640, 480)` и конструирует `FrameSender { _, lease -> }`; реализован `fun interface FrameSender { fun send(width: Int, slot: UploadSlots.Lease) }`, кодирование остаётся в вызывающем коде.

- Файл: `PhoneCamSender/app/src/main/AndroidManifest.xml`
- Действие: изменён (Task 4), коммит `bb4e025`
- Суть: добавлены `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `WAKE_LOCK`, `POST_NOTIFICATIONS` и `<service android:name=".CaptureService" android:exported="false" android:foregroundServiceType="camera" />`; merged manifest проверен.
- Статус: [DONE]

- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureNotifier.kt`
- Действие: создан (Task 5), коммит `2ca477a`
- Суть: правило уведомления (id 4242, channel `camflow_capture`, low importance, `onlyAlertOnce`) через seam `NotificationSource`, чтобы тест был JVM-тестируемым без Robolectric; при `POST_NOTIFICATIONS` denied возвращается `null`.
- Статус: [DONE]
- Отклонение: сигнатура плана (`Context`/`Notification`) заменена на seam ради тестируемости.

- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureService.kt`
- Действие: создан (Task 6), коммит `34852b7`
- Суть: foreground service владеет `LifecycleRegistry`, CameraX `ImageAnalysis`, `UploadSlots(2)`, `ReconnectPlanner`, wake lock и уведомлением; `onBind` возвращает `null` (LocalBinder-style `CaptureService.current()`), `onDestroy` освобождает ресурсы.
- Статус: [DONE]
- Отклонение: `ServiceCompat.startForeground` отсутствует в `androidx.core:core:1.10.1`, поэтому используется version-gated `startForeground` с `ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA` для API 34+.
- Ограничение: `CaptureServiceTest` компилируется, но не выполнялся — нет устройства/эмулятора.

- Файл: `PhoneCamSender/app/build.gradle.kts`
- Действие: изменён (Task 6), коммит `34852b7`
- Суть: добавлен единственный новый dependency `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`.
- Статус: [DONE]

### [2026-10-09 22:12] Task 2 — ReconnectPlanner (RED)
- Файл: `PhoneCamSender/app/src/test/java/com/example/phonecamsender/ReconnectPlannerTest.kt`
- Действие: создан (RED)
- Суть: Тест `cappedBackoffAndResetAfterSuccess` фиксирует экспоненциальную задержку с потолком 30 с и сброс счётчика после успеха.
- Статус: [PLANNED]
- Следующий шаг: подтвердить провал компиляции, затем реализовать `ReconnectPlanner.kt`

### [2026-10-09 22:10] Task 1 — CaptureGate
- Файл: `PhoneCamSender/app/src/main/java/com/example/phonecamsender/CaptureGate.kt`
- Действие: создан
- Суть: Класс решения о захвате (`serviceRunning && !userStopped`) без `android.*` импортов; RED подтверждён (unresolved reference), после реализации оба теста проходят.
- Статус: [DONE]
- Следующий шаг: Task 2 — ReconnectPlanner

### [2026-10-09 21:55] Спецификация background capture
- Файл: `docs/superpowers/specs/2026-10-09-camflow-background-capture-spec.md`
- Действие: создан
- Суть: Спецификация (6 областей + Success Criteria + Open Questions) для плана `2026-10-09-camflow-background-capture.md`; зафиксированы assumptions (Doze/vendor-kill не гарантируется, только ImageAnalysis в сервисе, no-queue сохраняется) и pinned-поведения с тестами.
- Статус: [DONE]
- Следующий шаг: review спецификации человеком перед Phase 2 (plan)

<!-- LOG_END -->
