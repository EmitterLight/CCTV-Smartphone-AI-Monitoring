# Онбординг: Sentinel (CCTV-Smartphone-AI-Monitoring)

Генерировано из knowledge graph `.ua/knowledge-graph.json`
(commit `e2e62c4228233d6c688ed89b07c3e6b0b4f6e7e7`, 302 узла, 511 рёбер).

## 1. Обзор проекта

**Sentinel** — распределённая система видеомониторинга в реальном времени для локальной сети, где неиспользуемые Android-устройства работают сетевыми камерами.

| | |
|---|---|
| **Языки** | Python, Kotlin, JavaScript, HTML/CSS, JSON, YAML, TOML, Properties, Batch, Markdown |
| **Фреймворки/инструменты** | Flask, Android (CameraX, OkHttp), Gradle, GitHub Actions, pytest, OpenCV, NumPy |
| **Три половины системы** | мобильный захват (CamFlow) → обработка на ПК (Flask-сервер) → управление из браузера (дашборд) |

---

## 2. Архитектурные слои

### Серверное ядро (`app/core`, `app/config`, `server.py`)

Точка входа, конфигурация, аутентификация, буфер кадров, сетевое обнаружение телефонов, запись видеопотоков на диск и служебная статистика.

Ключевые файлы: `server.py`, `app/config/config_store.py`, `app/core/auth.py`, `app/core/frame_buffer.py`, `app/core/runtime.py`, `app/core/upload_stats.py`, `app/core/logger.py`, `app/net/net_discovery.py`, `app/recorder/recorder.py`, `app/recorder/recorder_worker.py`, `app/recorder/storage.py`, `app/version.py`.

### AI-аналитика (`app/ai`)

Подсистема ИИ-анализа видео на PC-сервере: клиент Vision API, рабочий процесс AI-мониторинга, детектор движения, хранилище результатов и модель подписей кадров для обнаружения происшествий.

Ключевые файлы: `app/ai/ai_monitor_worker.py`, `app/ai/motion_trigger.py`, `app/ai/vision_client.py`, `app/ai/ai_ark.py`, `app/ai/ai_store.py`.

### Веб-API и дашборд (`app/web`)

HTTP-слой Sentinel: Flask-маршруты REST API для загрузки кадров с телефонов, диагностика загрузок, браузерный дашборд (HTML-шаблон, JavaScript и статические стили).

Ключевые файлы: `app/web/webapp.py`, `app/web/upload_diagnostics.py`, `app/web/templates/dashboard.html`, `app/web/static/dashboard.js`, `app/web/static/access.js`, `app/web/static/style.css`.

### Android-клиент CamFlow (`PhoneCamSender`)

Kotlin-приложение для Android: захват с камеры, обнаружение PC-сервера в LAN, отправка кадров, экраны настроек и диагностики, ресурсы и манифест, Gradle-сборка и подписанные релизные APK.

Ключевые файлы: `MainActivity.kt`, `SettingsActivity.kt`, `NetworkDiscover.kt`, `ImageUtil.kt`, `UploadSlots.kt`, `UploadDiagnostics.kt`, `CameraStartGuard.kt`, `DeviceCredentials.kt`, `ResolutionStatus.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`.

### Тестовый слой (`tests`, `PhoneCamSender/app/src/{test,androidTest}`)

Автотесты обеих подсистем: pytest-тесты PC-сервера (аутентификация, жизненный цикл, хранилище, веб-загрузка, сеть, запись, согласованность версий) и Kotlin-тесты CamFlow (unit- и instrumented-тесты).

### Документация

`README.md` (+ `README_CN.md`), `CamFlow_UserGuide.md` (+ `CamFlow_UserGuide_CN.md`), план `2026-10-09-camflow-background-capture.md`.

### Конфигурация и CI/CD

Единый файл версий Sentinel/CamFlow, зависимости Python, рабочий процесс GitHub Actions с тестами для Python, JavaScript и Android, служебные каталоги `log/` и `recordings/`.

---

## 3. Ключевые концепции

- **Общее состояние вместо прямых зависимостей.** `config_store.py` — самый востребованный узел проекта (максимальный fan-in): значения по умолчанию, валидация частичных обновлений, атомарная запись JSON через `os.replace`. `frame_buffer.py` и `runtime.py` — общая «память» между потоками загрузки, стрима, рекордера и AI.
- **Потокобезопасность как норма.** Фоновые потоки записи и AI-мониторинга (`threading.Thread` + stop-event) работают через `threading.Lock`; на Android — `UploadSlots` (семафор параллельных отправок) и `CameraStartGuard` (защита от дублирующих стартов и устаревших колбэков).
- **Application factory.** Фабрика `create_app(settings)` в `webapp.py` — стандартный Flask-паттерн, который позволяет pytest-фикстурам собирать изолированное приложение с временной SQLite и конфигом.
- **Локальная безопасность.** SQLite-хранилище админ-сессий, устройств и одноразовых pairing-кодов; Flask-middleware `install_auth` с CSRF-защитой и digest-токенами; rate-limit; отсутствие хранения паролей в открытом виде; маскирование API-ключей в config API.
- **Обнаружение через UDP-broadcast.** Серверный responder слушает порт 37020 и отвечает Android-клиенту адресом HTTP-сервера; клиент дополнительно выполняет параллельное сканирование /24-подсети.
- **Абстракция провайдеров зрения.** `vision_client.py` разрешает настройки (ark / OpenAI-совместимые) и создаёт нужного клиента с единым интерфейсом анализа кадра и нормализацией ответа.
- **Движение как триггер, dwell как подтверждение.** `motion_trigger.py` (разность последовательных кадров OpenCV, порог по доле изменившихся пикселей, ограничение частоты) решает, когда вызывать vision-модель; события присутствия ведутся с dwell-подтверждением.
- **Единый источник версий.** `version.properties` связывает PC-сервер (`app/version.py`), Android-сборку и оба README; `test_version_consistency.py` проверяет согласованность, включая SHA-256 APK.
- **Тесты как контракт между слоями** (70 узлов с тегом `test`, 27 рёбер `tested_by`).

---

## 4. Обучающий тур

1. **Обзор проекта** — `README.md`: слоистая архитектура «мобильный захват + обработка на ПК + управление из браузера», возможности и порядок развёртывания.
2. **Точка входа сервера** — `server.py` и функция `main`: разбор аргументов, инициализация auth/config/buffer/stats, запуск фоновых потоков, `make_server`. Центральный оркестратор, от которого расходятся почти все подсистемы.
3. **Конфигурация и общее состояние** — `config_store.py`, `frame_buffer.py`, `runtime.py`. Поняв этот слой, вы понимаете, как компоненты обмениваются данными без прямых зависимостей.
4. **Аутентификация и доступ** — `app/core/auth.py`, класс `AuthStore`, `tests/test_auth.py`. Определяет, кто может управлять дашбордом и подключать камеры.
5. **Flask-приложение и REST API** — `create_app` в `webapp.py`, `upload_diagnostics.py`. Вершина fan-out структуры: связывает почти всё приложение.
6. **Браузерный дашборд** — `dashboard.html`, `dashboard.js`, `access.js`: опрос статуса, AI-событий и журнала, управление ingest/записью/снимками, переключение administrator/visitor, pairing-коды.
7. **Обнаружение сервера в LAN** — `app/net/net_discovery.py` ↔ `NetworkDiscover.kt`. Первый момент, где обе половины системы встречаются.
8. **Android-клиент CamFlow** — `MainActivity.kt`, `SettingsActivity.kt`, `AndroidManifest.xml`. Противоположный конец конвейера по отношению к `webapp.py`.
9. **Запись видео на диск** — `recorder_worker.py`, `SegmentRecorder` из `recorder.py`, `storage.py` (`maintain_storage`). Опирается на буфер кадров из шага 3.
10. **AI-аналитика видео** — `ai_monitor_worker.py`, `motion_trigger.py`, `vision_client.py`, `ai_ark.py`, `ai_store.py` (JSONL).
11. **Тестовый слой** — `conftest.py`, `test_server_lifecycle.py`, `test_version_consistency.py`, `test_web_upload.py`. Объясняют ожидаемое поведение шагов 2–10.
12. **CI/CD и версии** — `.github/workflows/ci.yml`, `version.properties`, `app/version.py`. Матрица Python 3.9/3.12 × Ubuntu/Windows, `node --check` для JS, Gradle-тесты Android.
13. **Документация и планы развития** — `CamFlow_UserGuide.md` (установка, подключение, настройки, API: `GET /ping`, `POST /upload`, `GET /stream`, troubleshooting) и план фонового захвата: девять задач (`CaptureGate`, `ReconnectPlanner`, `FramePipeline`, `CaptureService`) с переносом логики из `MainActivity`.

---

## 5. Карта файлов

### Серверное ядро

| Файл | Назначение |
|---|---|
| `server.py` | Точка входа: аргументы, auth, config, буфер кадров, статистика, фоновые потоки, запуск Flask через `make_server` |
| `app/config/config_store.py` | Потокобезопасное хранилище конфигурации: дефолты, валидация частичных обновлений, атомарный JSON |
| `app/core/auth.py` | SQLite-хранилище админ-сессий, устройств и pairing-кодов + middleware `install_auth` (CSRF, digest-токены) |
| `app/core/frame_buffer.py` | Потокобезопасный буфер последнего BGR-кадра (общий для upload/stream/recorder/AI) |
| `app/core/runtime.py` | Dataclass общего состояния рекордера между потоками |
| `app/core/upload_stats.py` | Счётчики и оценка FPS по скользящему окну для `/upload` |
| `app/net/net_discovery.py` | UDP discovery-responder на порту 37020 |
| `app/core/logger.py` | Ротирующий файловый логгер (5 МБ, 3 резервные копии) |
| `app/version.py` | `version_info()`: версия, код, путь к APK и его SHA-256 |

### Запись

| Файл | Назначение |
|---|---|
| `app/recorder/recorder.py` | `SegmentRecorder`: перебор кодеков, VideoWriter, запись сегментов, регистрация в хранилище |
| `app/recorder/recorder_worker.py` | Фоновый поток: следит за конфигурацией и кадрами, запускает/останавливает рекордер, проверяет хранилище |
| `app/recorder/storage.py` | Безопасное разрешение корневого пути, SQLite-индекс, квоты, ретенция, минимум свободного места |

### AI-аналитика

| Файл | Назначение |
|---|---|
| `app/ai/ai_monitor_worker.py` | Фоновый воркер: кадры → детектор движения → vision-модель → события присутствия с dwell-подтверждением |
| `app/ai/motion_trigger.py` | Детектор движения на разности кадров OpenCV с порогом и ограничением частоты |
| `app/ai/vision_client.py` | Абстракция провайдеров (ark / OpenAI-совместимые), единый интерфейс, нормализация ответа |
| `app/ai/ai_ark.py` | Клиент Ark Vision (Volcengine), кодирование кадра в data URL, извлечение JSON |
| `app/ai/ai_store.py` | Потокобезопасный `AiRuntime` и `EventStore` (JSONL) |

### Веб-API и дашборд

| Файл | Назначение |
|---|---|
| `app/web/webapp.py` | Фабрика Flask-приложения и все маршруты: `/upload`, MJPEG-стрим, config, запись, снимки, статус, AI-события, логи |
| `app/web/upload_diagnostics.py` | Логирование начала и завершения приёма изображений с замерами времени |
| `app/web/templates/dashboard.html` | Jinja2-шаблон: топбар, панель доступа и pairing, трансляция, статус, логи, форма настроек |
| `app/web/static/dashboard.js` | Опрос API статуса/AI-событий/журнала, управление ingest/записью/снимками, применение конфигурации |
| `app/web/static/access.js` | Проверка статуса аутентификации, режимы administrator/visitor, вход/выход, pairing-коды |
| `app/web/static/style.css` | Стили дашборда: топбар, карточки, панели, AI-статус, история, visitor-режим |

### Android-клиент CamFlow

| Файл | Назначение |
|---|---|
| `MainActivity.kt` | Обнаружение сервера, разрешения, запуск CameraX, кодирование и асинхронная загрузка кадров |
| `SettingsActivity.kt` | Адрес сервера, сопряжение устройства, качество/частота/разрешение, отладочные переключатели |
| `NetworkDiscover.kt` | UDP-broadcast, параллельное сканирование /24-подсети, разбор адреса сервера |
| `ImageUtil.kt` | YUV_420_888 → NV21 → JPEG |
| `UploadSlots.kt` | Потокобезопасный семафор параллельных загрузок |
| `UploadDiagnostics.kt` | Трассировка фаз OkHttp-запроса (`UploadTrace`, `UploadHttp`) |
| `CameraStartGuard.kt` | Защита от дублирующих стартов и устаревших колбэков при смене конфигурации камеры |
| `ResolutionStatus.kt` | Фактически применённое разрешение кадра (игнорирует устаревшие кадры) |
| `DeviceCredentials.kt` | Шифрование pairing-токена через Android Keystore (AES/GCM), привязка к адресу сервера |
| `AndroidManifest.xml` | Разрешения CAMERA/INTERNET, activity, cleartext-трафик для LAN |
| `app/build.gradle.kts` | applicationId, min/target SDK, версии из `version.properties`, CameraX и OkHttp |

### Тесты и CI

| Файл | Назначение |
|---|---|
| `tests/conftest.py` | Общие pytest-фикстуры и helper `authenticate` (временные SQLite, конфиг, runtime) |
| `tests/test_server_lifecycle.py` | Полный жизненный цикл сервера через subprocess: запуск, логин, корректное завершение |
| `tests/test_version_consistency.py` | Согласованность версий между `version.properties`, Android-сборкой, README и дашбордом |
| `tests/test_auth.py` | Сессии, CSRF, pairing, rate-limit, приватность, отсутствие plaintext-паролей |
| `tests/test_web_upload.py` | Обязательность ingest, приём JPEG, учёт ошибок, диагностика без утечки секретов |
| `tests/test_storage.py` | Ретеншн, квоты, минимум свободного места, защита от выхода за пределы корневой папки |
| `tests/test_recorder.py` | Подбор кодека, расширения по кодекам, отчёты об ошибках, проверка хранилища |
| `tests/test_ai_monitor.py` | Dwell-подтверждение, таймаут и восстановление, отключение ИИ, ротация API-ключей |
| `tests/test_vision_client.py` | Провайдеры и пресеты, построение chat-URL, нормализация ответа, отсутствие утечки ключа |
| `tests/test_net_discovery.py` | UDP-ответчик отвечает `PHONECAM_SERVER` с корректным адресом и завершается по stop_event |
| `tests/test_stream_upload_concurrency.py` | Два одновременных HTTP-трансляции не сериализуют загрузку кадров |
| `.github/workflows/ci.yml` | CI при push и pull_request: pytest (Python 3.9/3.12 × Ubuntu/Windows), `node --check`, Gradle-тесты Android |
| `version.properties` | Единый источник версий Sentinel и CamFlow, путь к APK и SHA-256 |
| `requirements.txt` | Flask, NumPy, OpenCV, SDK Volcengine Ark |

---

## 6. Зоны повышенной сложности — читать осторожно

### Python

- **`app/config/config_store.py`** — максимальный fan-in, самое востребованный узел проекта; любое изменение затрагивает все подсистемы.
- **`app/core/auth.py`** — безопасность, CSRF, digest-токены, rate-limit.
- **`app/web/webapp.py`** — вершина fan-out, связывает почти всё приложение.
- **`app/ai/ai_monitor_worker.py`** — фоновый цикл с dwell-логикой и восстановлением.
- **`app/ai/vision_client.py`** — абстракция провайдеров и нормализация ответов.
- **`app/recorder/recorder.py`** — перебор кодеков, VideoWriter, регистрация записей.

### Kotlin

- **`MainActivity.kt`** — CameraX и сетевая отправка в одном месте; планируется вынос в `CaptureGate`, `ReconnectPlanner`, `FramePipeline`, `CaptureService`.
- **`NetworkDiscover.kt`** — UDP-broadcast плюс параллельное сканирование подсети.
- **`SettingsActivity.kt`** — состояние настроек и сопряжение устройства.

### Frontend

- **`dashboard.js`** — периодический опрос API и управление ingest/записью/снимками.
- **`dashboard.html`**, **`style.css`** — разметка и visitor-режим.

### Документация

- `README.md`, `CamFlow_UserGuide.md`, `2026-10-09-camflow-background-capture.md` — самые объёмные узлы графа.
