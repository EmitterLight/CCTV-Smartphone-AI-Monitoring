# Spec: Автоматический инкремент versionCode + единая подпись APK

## Источник
- Запрос пользователя: «приложение не установлено, так как оно конфликтует с другим приложением» + «нужна автоматическая инкрементация версий»
- Файлы: `version.properties`, `PhoneCamSender/app/build.gradle.kts`, `tests/test_version_consistency.py`, `README.md`, `README_CN.md`, `PhoneCamSender/Keystore`

## Проблема

1. `camflow.versionCode = 9` задан вручную. Каждая новая сборка требует ручной правки файла, иначе Android не даёт обновить пакет поверх установленного (одинаковый versionCode).
2. Конфликт установки вызван не версией, а подписью:
   - `PhoneCamSender/CamFlow-v1.1.2.apk` подписан debug-ключом `8e77a6a0e7a3546c679d6b144116cd6869df4c96`
   - свежая локальная сборка — debug-ключом `7afd6c20ada86f80de8cbb9605f1d8592a4083e8`
   - разные `~/.android/debug.keystore` на разных машинах → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## ASSUMPTIONS
1. `versionName` (semver) меняет только человек; автоматически растёт только `versionCode`.
2. `version.properties` — единственный источник истины; Gradle и Python читают его, никто не хранит версию дублированно.
3. `PhoneCamSender/Keystore` (PKCS#12, 2500 байт, уже в git) — целевой signing-ключ; пароль и alias не коммитятся, читаются из окружения.
4. Имя канонического артефакта остаётся `PhoneCamSender/CamFlow-v{versionName}.apk` (тест проверяет `v{version}` в имени файла).
5. Первый установка APK с новой подписью требует удаления старого пакета с телефона — это документально фиксируется.

## Требования

- [ ] Gradle-таск `bumpVersionCode` увеличивает `camflow.versionCode` в `version.properties` на 1 при каждой сборке (debug и release), до упаковки APK.
- [ ] `versionName` не меняется автоматически.
- [ ] `signingConfig` на `PhoneCamSender/Keystore` применяется к обоим buildTypes (debug и release); пароль/alias берутся из `CAMFLOW_KEYSTORE_PASSWORD` / `CAMFLOW_KEYSTORE_ALIAS`, при отсутствии — сборка падает с понятной ошибкой.
- [ ] После упаковки APK обновляются `camflow.apkPath` и `camflow.apkSha256` в `version.properties`.
- [ ] `README.md` и `README_CN.md` синхронизируются с `version.properties` (путь, SHA-256, build number) — `tests/test_version_consistency.py` остаётся зелёным.
- [ ] Тесты: `python -m pytest -q` и `./gradlew testDebugUnitTest assembleDebug` проходят.

## Команды

```
Build (debug):  cd PhoneCamSender && ./gradlew.bat assembleDebug --console=plain
Build (release): cd PhoneCamSender && ./gradlew.bat assembleRelease --console=plain
Test (python):  python -m pytest -q
Test (android): cd PhoneCamSender && ./gradlew.bat testDebugUnitTest --no-daemon
Verify signature: apksigner verify --print-certs <apk>
```

## Структура

```
version.properties                     → единственный источник версий (пишется Gradle-таском)
PhoneCamSender/app/build.gradle.kts    → bumpVersionCode + signingConfig
tools/sync_version_docs.py             → синхронизация README с version.properties
tests/test_version_consistency.py      → проверка согласованности (расширяется)
specs/3-Версионирование/spec.md        → эта спецификация
```

## Code Style

```kotlin
val versionFile = rootProject.file("../version.properties")

tasks.register("bumpVersionCode") {
    val properties = Properties().apply { versionFile.inputStream().use { load(it) } }
    val next = (properties.getProperty("camflow.versionCode").toInt() + 1).toString()
    properties.setProperty("camflow.versionCode", next)
    versionFile.writer().use { properties.store(it, null) }
}
```

## Testing Strategy

- Python: `tests/test_version_consistency.py` — согласованность version.properties ↔ Gradle ↔ README (расширить: README SHA == version.properties SHA).
- Gradle: `testDebugUnitTest` (31 тестов) + сборка APK.
- Ручная проверка: две последовательные сборки → versionCode 9 → 10; `apksigner verify --print-certs` показывает один и тот же сертификат для debug и release.

## Границы

- **Always:** тесты перед коммитом; `PROGRESS.md` обновляется с системным временем.
- **Ask first:** изменение формата имени APK; добавление зависимостей; правка CI.
- **Never:** коммит пароля keystore; хардкод пароля в `build.gradle.kts`; удаление legacy APK без подтверждения.

## Критерии приёмки

- [ ] Две подряд сборки: `camflow.versionCode` 9 → 10 без ручных правок.
- [ ] Debug и release APK подписаны одним сертификатом (keystore), проверяется `apksigner verify --print-certs`.
- [ ] `python -m pytest -q` проходит.
- [ ] `README.md` / `README_CN.md` содержат актуальные путь, SHA-256 и build number.
- [ ] `PROGRESS.md` обновлён.

## Решения (согласованы 2026-10-10)

1. Новый keystore `PhoneCamSender/camflow.keystore` (PKCS#12, alias `camflow`); пароль — в `.env` (gitignored), переменные `CAMFLOW_KEYSTORE_PASSWORD` / `CAMFLOW_KEYSTORE_ALIAS`. Старый `PhoneCamSender/Keystore` остаётся в репозитории, но не используется (пароль неизвестен).
2. Имя канонического артефакта фиксированное: `PhoneCamSender/CamFlow-v{versionName}.apk`.
3. Если credentials отсутствуют — сборка падает с явной ошибкой (fallback на debug-ключ запрещён).

## Замечания реализации

- Бамп происходит на этапе конфигурации, когда в запрошенных тасках есть `assemble*`/`install*`; `testDebugUnitTest` версию не меняет.
- Неудачная сборка тоже потребляет номер версии (бамп до упаковки). Приемлемо: номер — счётчик сборок, а не гарантия успешности.
- Синхронизация документации точечная: заменяются только строки, содержащие старый SHA-256 или имя APK (`build N` → `build N+1`), исторические упоминания не затрагиваются.
- Смена подписи требует удаления старого пакета с телефона — Android не ставит APK с другой подписи поверх установленного.

## Проверка (выполнено 2026-10-10)

- `assembleDebug` → `assembleRelease`: versionCode 12 → 13 → 14 без ручных правок.
- `apksigner verify --print-certs`: один сертификат `CN=CamFlow` (SHA-1 `28844d02a52b5e3949818ec2c7b5180c391e539b`) для debug и release.
- `python -m pytest -q` — 69 passed; `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL.

