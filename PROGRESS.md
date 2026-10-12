# PROGRESS.md

> Автоматический журнал изменений агента  
> Формат времени: `YYYY-MM-DD HH:MM` (системное время)  
> Статусы: `[PLANNED]` → `[DONE]` | `[ERROR: причина]` | `[PARTIAL: детали]`

## Сессия: 2026-10-10

---
<!-- LOG_START -->

### [2026-10-12 15:12] Подготовка ветки к переносу в GitLab: ignore-правила и очистка индекса
- Файл: `.gitignore`, `.ua/.understandignore`
- Действие: изменён
- Суть: В `.gitignore` добавлены tool-caches (`.pytest_cache/`, `.mypy_cache/`, `.ruff_cache/`), scratch (`.tmp/`, `*.sqlite3`), Android build (`.gradle/`, `build/`), `*.apk`, секреты (`*.keystore`, `*.jks`, `PhoneCamSender/Keystore`, `keystore.properties`, `secrets.properties`) и `/DESIGN_PLAN.md`; `.ua/` игнорируется целиком, кроме `knowledge-graph.json` и `.understandignore`. В `.understandignore` активированы паттерны из `.gitignore` и добавлены новые (`.tmp/`, caches, `*.sqlite3`, `*.apk`, keystore).
- Статус: [DONE]
- Дополнительно: из индекса удалены `PhoneCamSender/Keystore` (PKCS#12 с реальным ключом — секрет уже в истории GitHub), 4 APK (~24 МБ → публиковать через GitLab Releases) и корневой `2026-10-09-camflow-background-capture.md` (дубликат spec в `docs/superpowers`). Физически удалены `.tmp/` и `.ua/.trash-*`.
- Следующий шаг: перед push на GitLab решить вопрос с историей Keystore (`git filter-repo` / новый репозиторий / смена пароля).

### [2026-10-12 14:03] Финальный smoke: login-шаг (visitor → administrator)
- Файл: `app/web/templates/dashboard.html`, `app/web/static/access.js` (только проверка, не изменён)
- Действие: проверка
- Суть: В изолированном browser-контексте (чистая visitor-сессия) введён admin-токен, `#loginForm` отправлен, состояние перешло в administrator.
- Статус: [DONE]
- Проверка: `#accessStatus` → «Administrator · Default mode», `#accessMessage` → «Signed in. Log out when finished; session expires after 8 hours.», `body.visitor-mode` снят, `#rightPane` = `flex`, все кнопки активны, `#logoutBtn` и `#devicePanel` видны. Mobile 390px (admin): горизонтального скролла нет, единственный скролл-контейнер — документ (вложенный только `#log_tail`), `#live` = `16 / 9` на media-bg, footer `fixed` (37px) с `body padding-bottom = 37px` → последняя карточка заканчивается ровно у границы footer, наложений нет. Скриншот: `.tmp/final_smoke_admin_mobile_light.png`.
- Важно: план и guard-тест требуют 44px touch-target для `button`; фактические `input`/`select` на mobile = 33–35px (выполняет WCAG AA 2.5.2 ≥ 24px, но не 44px из описания Task 5). Изменение не внесено — вне guard-теста, требует решения пользователя.
- Не выполнено (исключено пользователем — UI-only steps): pairing, Ingest/Recording/Snapshot, Apply/Save/Load, озвучка `#msg` через Apply.
- Открытый вопрос: `.stale` индикатор.

### [2026-10-12 13:56] Финальный smoke-прогон финального чеклиста `DESIGN_PLAN.md`
- Файл: `DESIGN_PLAN.md`, `app/web/templates/dashboard.html`, `app/web/static/style.css`
- Действие: проверка
- Суть: Прогон финального чеклиста на живом сервере `http://127.0.0.1:8010/` — visitor-сессия в изолированном browser-контексте плюс существующая admin-сессия.
- Статус: [PARTIAL: login и mutate-шаги не выполнены по решению пользователя]
- Проверка: `venv\Scripts\python.exe -m pytest -q` → 119 passed; inline styles в `dashboard.html` = 0; HEX только в `:root`; DOM-контракт — 72 id идентичны baseline, классы только добавлены (12 новых), ни одного удалённого; `!important` = 4; мёртвые селекторы (`.grid2`, `.container`, `.ai-summary`, `#splitter.hidden`, `.right.collapsed`, `.splitter.collapsed`) отсутствуют. Контраст light/dark посчитан, все ключевые пары ≥ 4.5. Visitor mode: `#rightPane` и `#splitter` скрыты, все admin-кнопки disabled, статус «Visitor · Default mode». Mobile 390px: нет горизонтального скролла, `.topbar` переносится, единственный скролл-контейнер — документ (`#leftPane`/`#rightPane` = `overflow-y: visible`), `#splitter` = `display: none`. Focus-visible: `outline 2px`, `outline-offset 4px`, `:focus-visible` = true. Tab order: `summary` → `#adminToken` → login → `#splitter`. Splitter: `role="separator"`, `aria-orientation="vertical"`, `aria-valuenow/min/max`, `title="Drag to resize"`, `tabindex="0"`, `z-index 50`; drag меняет ширину с клампом 280px..65%. Collapse/Expand: классы `collapsed` на `#rightPane`/`#splitter`/`.layout`, текст кнопки меняется, FAB создаётся (44px) и удаляется. AI panel toggle проверен (UI-only, состояние восстановлено). Footer не перекрывает контент (`footer top = 863` при viewport 900). Скриншоты: `.tmp/final_smoke_desktop_dark.png`, `.tmp/final_smoke_visitor_mobile.png`, `.tmp/final_smoke_visitor_light.png`.
- Не выполнено: login (нужен admin-токен — пользователь обещал предоставить), pairing, Ingest/Recording/Snapshot, Apply/Save/Load — эти шаги пишут в БД/файлы и были исключены пользователем («UI-only steps only»). Announcement `#msg` без Apply не проверялся.
- Важно: `#accessStatus` находится внутри `<summary>`, поэтому его текст попадает в accessibility tree только как часть имени disclosure-triangle, а не как содержимое live-region — структурное изменение потребовало бы переноса элемента, что выходит за scope HTML-only.
- Открытый вопрос: `.stale` индикатор не реализован — требуется решение пользователя.
- Следующий шаг: получить admin-токен и выполнить login-шаг; отдельное решение по `.stale`.

### [2026-10-12 13:56] Финальный smoke: исправление некорректного `label` на `#aiProviderHint` — выполнено
- Файл: `app/web/templates/dashboard.html`, `app/web/static/style.css`, `tests/test_ui_contract.py`
- Действие: изменён
- Суть: `label for="aiProviderHint"` заменён на `span.field-label` (id сохранён), правило отступов расширено до `.field label, .field .field-label`, добавлены guard-тесты `test_labels_point_only_to_labelable_elements` и `test_field_label_span_shares_label_spacing`.
- Статус: [DONE]
- Проверка: `venv\Scripts\python.exe -m pytest -q` → 119 passed. На живом сервере (`python server.py --port 8010`) console-issue «Incorrect use of `<label for=FORM_ELEMENT>`» исчез после перезапуска сервера (Jinja-кэш шаблона, старый PID 10900 остановлен). Остались только информационные password-field warnings и `401` на автозапросе `/favicon.ico`.

### [2026-10-12 13:35] Финальный smoke: исправление некорректного `label` на `#aiProviderHint`
- Файл: `app/web/templates/dashboard.html`, `app/web/static/style.css`, `tests/test_ui_contract.py`
- Действие: запланировано изменение
- Суть: DevTools console на живом сервере даёт issue «Incorrect use of `<label for=FORM_ELEMENT>`» — `label for="aiProviderHint"` указывает на `div`. Заменить label на `span.field-label` (id `#aiProviderHint` не трогать, JS `dashboard.js:133` и тесты `test_web_model_info.py:72` зависят от id), добавить `.field .field-label` к правилу отступов и guard-тест «label указывает только на labelable-элементы».
- Статус: [PLANNED]

### [2026-10-12 13:23] Task 14: RED + GREEN + smoke — motion и z-index
- Файл: `tests/test_ui_contract.py`, `app/web/static/style.css`
- Действие: изменён
- Суть: Добавлены 4 guard-теста Task 14: `z-index` только через токены (`--z-splitter/--z-topbar/--z-footer/--z-overlay`, `--z-topbar > --z-splitter`), floating button не перекрывает `topbar-actions`, `@media (prefers-reduced-motion: reduce)` отключает motion, `#floatingExpandBtn` остаётся кликабельным. В CSS: `.topbar` получил `position: relative` + `z-index: var(--z-topbar)`; `.floating-toggle` перенесён ниже topbar (`top: calc(var(--topbar-height) + var(--space-6) + var(--space-3))` вместо `top: var(--space-5)`); в блоке `max-width: 900px` FAB перенесён к footer, потому что там `.topbar` переносится строкой (`flex-wrap: wrap`) и `--topbar-height` больше не описывает его высоту; добавлен блок `prefers-reduced-motion: reduce` с `transition: none; animation: none; scroll-behavior: auto` и явным переопределением skeleton-pulse.
- Статус: [DONE]
- Проверка: RED — `pytest tests/test_ui_contract.py -q` → 3 failed (нет `prefers-reduced-motion`-блока, topbar без stacking-контекста, FAB перекрывает topbar), 45 passed. GREEN — `venv\Scripts\python.exe -m pytest -q` → 117 passed (113 baseline + 4 новых guard-теста). Browser smoke (file:// + реальный `style.css`): desktop — FAB `top = 81px` (`57 + 16 + 8`), `#shutdownBtn` y=22..66, пересечения нет, `elementFromPoint` по центру FAB → `floatingExpandBtn`, `z-index = 9999`, `pointer-events = auto`, `.topbar` = `relative` / `100`; viewport 800x600 — FAB у footer (`bottom = 49px`), пересечений с topbar и Shutdown нет, кликабельность подтверждена. Скриншот: `.tmp/task14_desktop.png`.
- Важно: плановая цифра «70 passed» устарела (baseline вырос до 113). `prefers-reduced-motion` в smoke нельзя проверить функционально — DevTools MCP не эмулирует media-features, `matchMedia` = false; блок проверен guard-тестом, а необходимость явного переопределения доказана в браузере: `* { animation: none }` НЕ гасит `skeleton-pulse` (проигрывает по специфичности), явное правило внутри блока → `animation-name: none`. JS (`dashboard.js:780-786`) не тронут.
- Следующий шаг: Task 14 — последний в `DESIGN_PLAN.md`; план выполнен, следующий шаг — финальная сверка чеклиста AGENTS.md / review.

### [2026-10-12 13:03] Task 13: RED + GREEN + smoke — #splitter как separator
- Файл: `tests/test_ui_contract.py`, `app/web/templates/dashboard.html`
- Действие: изменён
- Суть: `#splitter` (строка 97) получил declarative-ARIA: `role="separator"`, `aria-orientation="vertical"`, `aria-valuenow="360"`, `aria-valuemin="280"`, `aria-valuemax="780"`, `tabindex="0"`, `title="Drag to resize"` сохранён. Значения выведены из контракта: min = `const minW = 280` (JS-кламп), now = дефолтный `--sidebarW: 360px`, max = 65% от типового layout-широкого (780). JS-логика `mousedown` (731) не тронута, клавиатурный drag не добавлялся (запрещён планом).
- Статус: [DONE]
- Проверка: RED — `pytest tests/test_ui_contract.py -q` → 2 failed (нет ARIA-атрибутов), 42 passed. GREEN — `venv\Scripts\python.exe -m pytest -q` → 113 passed (110 baseline + 3 новых guard-теста). Browser smoke (file:// + реальный `style.css`): accessibility tree → `separator "Drag to resize" orientation="vertical" value="360" valuemax="780" valuemin="280"`, `tabIndex = 0`; drag splitter → Settings изменил ширину `#rightPane` с 360px до 280px (упор в min-кламп) — drag работает как раньше. Скриншот: `.tmp/task13_splitter.png`.
- Важно: плановая цифра «70 passed» устарела (baseline вырос до 110). Console-ошибки в smoke — только `file://` fetch CORS без сервера. ARIA-значения статичны: JS не обновляет `aria-valuenow` при drag (JS заморожен планом), значения отражают дефолт и границы, а не текущее состояние. Тест `test_splitter_drag_handler_is_untouched` — регрессионный guard, проходил и на baseline.
- Следующий шаг: Task 14 (Motion и z-index) по `DESIGN_PLAN.md`.

### [2026-10-12 12:48] Task 12: GREEN + browser smoke — labels и заголовки
- Файл: `app/web/templates/dashboard.html`, `app/web/static\style.css`, `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Все 32 `label` получили `for` на существующий id (26 новых; `for` уже был у 5 + `#ai_enabled`). `<b>` секционные заголовки заменены: Live View / Monitor Status / Settings → `h2.card-title`, Logs и AI Status → `h3.section-title`. Добавлено правило `.card-title` (margin 0, `var(--font-size-sm)`, `var(--font-weight-strong)`, `var(--color-text)`) — без hardcoded HEX и магических отступов. Литерал `Model Info` сохранён, `#adminToken` сохранил `aria-label`, JS и селекторы не менялись. В тесте Task 7 title секции = первый заголовок (иначе `AI Status` смешивался с `AI`).
- Статус: [DONE]
- Проверка: `venv\Scripts\python.exe -m pytest -q` → 110 passed (104 baseline + 6 новых guard-тестов). Browser smoke (file:// + реальный `style.css`): 32 label, все с `for`, все цели существуют; клик по label фокусирует поле (`#cam_name`, `#ai_model`, `#ai_prompt_extra`); outline `h2 Live View → h2 Monitor Status → h3 Logs → h2 Settings → h3 General → h3 Recording → h3 AI → h3 AI Status` (без пропусков); computed `.card-title` = margin 0, 14px, 700, token color. Lighthouse accessibility: `label` — passed (было 26 warning «No label associated with a form field»), `heading-order` — passed, `color-contrast` — passed. Скриншот: `.tmp/task12_headings.png`.
- Важно: `for="aiProviderHint"` указывает на `div` (не labelable) — критерий плана выполнен буквально, но HTML-валидатор считает такую ассоциацию некорректной; корректная альтернатива (`aria-labelledby`) потребовала бы нового id на label, что меняет набор id. Оставшиеся Lighthouse-failures вне Task 12: `landmark-one-main`, `meta-description`, `robots-txt`, `llms-txt`.
- Следующий шаг: Task 13 по `DESIGN_PLAN.md`.

### [2026-10-12 12:37] Task 12: RED — guard-тесты labels и заголовков
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Добавлены guard-тесты Task 12: каждый `label` имеет `for`, указывающий на существующий id; каждое поле имеет доступное имя (ассоциированный label или `aria-label`); секционные заголовки — `h2`/`h3` вместо `<b>` (Live View/Monitor Status/Settings → h2, Logs/AI Status → h3) с сохранением текста и литерала `Model Info`; структура заголовков не пропускает уровни и открывается с h2; `#adminToken` сохраняет `aria-label`; `.card-title` стилизуется токенами без hardcoded HEX.
- Статус: [DONE]
- Проверка: RED подтверждён — `pytest tests/test_ui_contract.py -q` → 5 failed, 36 passed (labels без `for`, поля без ассоциированного label, `<b>` вместо заголовков, outline начинается с h3, `.card-title` отсутствует). Тест `aria-label` у `#adminToken` проходит и сейчас — инвариант против регрессии.
- Следующий шаг: GREEN — `dashboard.html` + `style.css`.

### [2026-10-12 12:22] Task 11: GREEN — focus-visible и aria-live
- Файл: `app/web/static/style.css`, `app/web/templates/dashboard.html`
- Действие: изменён
- Суть: Добавлено глобальное `:focus-visible { outline: 2px solid var(--color-focus); outline-offset: var(--space-1); }` — кольцо теперь не только у `.access-panel summary` (дублирующее правило удалено). `#msg` получил `role="status" aria-live="polite" aria-atomic="true"`; шумные регионы `#status_readable`, `#log_tail`, `#aiHistoryList` получили `aria-live="off"`. Классы и JS не менялись.
- Статус: [DONE]
- Проверка: `venv\Scripts\python.exe -m pytest -q` → 104 passed (99 baseline + 5 новых guard-тестов). Browser smoke (file:// + реальный `style.css`): Tab по форме входа — на `#snapshotBtn` computed `outline: solid 2px rgb(127,178,245)`, `outline-offset: 4px`, `:focus-visible` = true; на `#cam_name` — то же кольцо; `summary`, `select`, `input` получают кольцо от глобального правила. Accessibility tree: `#msg` — `status atomic live="polite"`, `#accessStatus`/`#accessMessage` сохраняют `status live="polite"`, `#status_readable` больше не live-регион (обычный текст). Скриншот: `.tmp/task11_focus_ring.png`.
- Важно: console-ошибки в smoke — только `file://` fetch CORS без сервера; warning `No label associated with a form field` (26) — существующая проблема, относится к Task 12.
- Следующий шаг: Task 12 (Labels и заголовки) по `DESIGN_PLAN.md`.

### [2026-10-12 12:16] Task 11: RED — guard-тесты focus-visible и aria-live
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Добавлены guard-тесты: глобальное `:focus-visible` с `var(--color-focus)` и `outline-offset: var(--space-1)`, запрет подавления кольца (`outline: none` на `:focus`), контраст кольца ≥ 3.0 к `--color-bg`/`--color-surface`/`--color-surface-muted` в light и dark, live-регион `#msg` (`role="status"`, `aria-live="polite"`, `aria-atomic="true"`, JS продолжает писать в него), `aria-live="off"` для `#status_readable`/`#log_tail`/`#aiHistoryList`, сохранение `role="status"` у `#accessStatus`/`#accessMessage`/`#pairingCode`.
- Статус: [DONE]
- Проверка: RED подтверждён — `pytest tests/test_ui_contract.py -q` → 3 failed, 32 passed (кольцо не глобальное, `#msg` без live-атрибутов, шумные регионы без `aria-live="off"`). Тесты контраста кольца и сохранения `role="status"` проходят и сейчас — это инварианты против регрессии.
- Следующий шаг: GREEN — `style.css` + `dashboard.html`.

### [2026-10-12 12:08] Task 10: GREEN — читаемость логов и данных
- Файл: `app/web/static/style.css`, `tests/test_ui_contract.py`
- Действие: изменён
- Суть: В правило `pre` добавлены `font-family: var(--font-mono)` и `font-variant-numeric: tabular-nums`; guard-тесты фиксируют, что лог-блоки используют моноширинный data-шрифт и `#log_tail` остаётся `pre`-блоком, который JS перезаписывает через `textContent`.
- Статус: [DONE]
- Проверка: `venv\Scripts\python.exe -m pytest -q` → 99 passed. RED был подтверждён для `test_log_blocks_use_the_monospace_data_font`; `test_log_tail_stays_a_pre_block_that_js_rewrites` проходит как инвариант. Browser smoke в Chrome при viewport `360x640`: страница без горизонтального скролла, `#log_tail` переносит длинную строку (`scrollWidth == clientWidth`), использует `var(--font-mono)`, `tabular-nums`, `white-space: pre-wrap`, `overflow-wrap: anywhere`, `max-height: 260px`; `#settingsBody` сохраняет `scrollbar-gutter: stable`; при видимом AI-панеле `#aiTimes` и `#aiMetrics` тоже переносят длинные значения без горизонтального скролла. Скриншот: `.tmp/task10_360_logblock.png`.
- Важно: console-ошибки в smoke — только `file://` fetch CORS из-за отсутствия сервера; новых CSS/JS ошибок нет. Warning `No label associated with a form field` относится к Task 12.
- Следующий шаг: Task 11 (`Focus-visible и aria-live`) по `DESIGN_PLAN.md`.

### [2026-10-12 12:00] Task 10: читаемость логов и данных
- Файл: `app/web/static/style.css`
- Действие: планируемое изменение
- Суть: В правило `pre` добавить `font-family: var(--font-mono)` (логи — данные) и `font-variant-numeric: tabular-nums` для таймстемпов/счётчиков. Остальная часть Task 10 (`white-space: pre-wrap`, `overflow-wrap: anywhere`, scrollbar через токены, `scrollbar-gutter: stable` на `#settingsBody`, tabular-nums на `.ai-kv`/`.ai-hmeta`/`.status-v`) уже закрыта ранее — проверяется guard-тестами.
- Статус: [PLANNED]

### [2026-10-12 09:33] Task 9: GREEN — `.empty-state` и skeleton для `#live`
- Файл: `app/web/static/style.css`, `app/web/templates/dashboard.html`
- Действие: изменён
- Суть: Добавлены токены `--empty-state-min-height`, `--skeleton-min-height`, `--motion-skeleton` и блок `/* empty & loading states */`: `.empty-state` (muted-текст, line-height, собственная min-height — без font-size/padding, чтобы не перекрывать типографику `.ai-kv`/`pre`) и skeleton-рамка `.live-frame` (`aspect-ratio: 16 / 9`, `min-height`, `var(--color-surface-muted)`, `animation: skeleton-pulse`), `#live` заполняет рамку (`height: 100%`), рамка скрывается при `#live[hidden]` (privacy mode). В `dashboard.html` 8 placeholder-элементов сведены к одному паттерну `empty-state` с единым текстом `loading…`, `#live` обёрнут в `.live-frame`.
- Статус: [DONE]
- Проверка: `pytest -q` → 94 passed; ручной smoke в Chrome (file:// + реальный `style.css`): рамка 334px до первого кадра и 334px после установки `src` (CLS = 0), `skeleton-pulse` активен только пока нет `src`, при `#live[hidden]` рамка не показывает пустой box; `#liveLocked` остаётся `hidden`-управляемым.
- Важно: на `<img>` без `src` Chrome игнорирует `aspect-ratio` (broken image с alt = content box ~23px), поэтому reserved box сделан на обёртке `.live-frame`, а не на самом `#live` — иначе первый кадр даёт сдвиг ~154px.
- Следующий шаг: Task 10 (читаемость логов).

### [2026-10-12 09:20] Task 9: guard-тесты пустых состояний
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: `PlaceholderWalker` (html.parser) находит элементы, чей текст целиком является placeholder, и требует единый класс `.empty-state` и единственный вариант текста; `.empty-state` добавлен в `UTILITY_CLASSES`; тесты на стилизацию через токены, на то, что placeholder-элементы — именно те, куда JS пишет (`getElementById`), на reserved box `#live` (`.live-frame` с `aspect-ratio: 16 / 9`, `min-height`, skeleton-анимация только без `src`, скрытие в privacy mode) и на то, что `#liveLocked` остаётся `hidden`-управляемым.
- Статус: [DONE]
- Проверка: RED подтверждён — `pytest tests/test_ui_contract.py -q` → 4 failed, 21 passed (`.empty-state` не определён, токены отсутствуют, placeholder-тексты различны). Два теста (`test_placeholders_are_the_elements_js_updates`, `test_live_locked_stays_hidden_controlled`) проходят и сейчас — это инварианты, которые ловят регрессию, если placeholder перенесён в обёртку или `#liveLocked` начнёт управляться через `display`.
- Следующий шаг: GREEN — `style.css` + `dashboard.html`.

### [2026-10-12 01:16] Task 8: кнопки, touch-таргеты и семантика состояний
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Базовый `button` и `.floating-toggle` получили `min-height: var(--tap-target)`, фиксированная высота 36px у тумблеров заменена на тот же токен. `#shutdownBtn.danger` — сплошной destructive (`--color-state-danger` + `--color-on-primary`), тумблеры `#aiToggleBtn/#ingestToggleBtn/#recordToggleBtn.danger` — active-state (`--color-state-active` на tinted-поверхности), semantics разведены без изменения классов и JS. `button.success` переведён на success-токены и больше не идентичен `button.primary`. Hover-фоны вынесены в токены `--color-state-primary-hover` (`#3870be`), `--color-state-danger-hover` (`#b04848`), `--color-state-success-hover` с явным `color: var(--color-on-primary)` и dark-переопределениями.
- Статус: [DONE]
- Проверка: `python -m pytest -q` → 89 passed (85 baseline + 4 новых guard-теста Task 8). Фактический контраст hover к `--color-on-primary`: light 4.97 / 8.04 / 5.43, dark 10.44 / 10.05 / 10.05; active-state к `--color-surface-danger`: 5.69 light, 8.53 dark. HEX только внутри `:root`, `!important` не добавлен.
- Следующий шаг: Task 9 по `DESIGN_PLAN.md`.

### [2026-10-12 01:14] Task 8: кнопки, touch-таргеты и семантика состояний
- Файл: `app/web/static/style.css`
- Действие: планируемое изменение
- Суть: `min-height: var(--tap-target)` для базового `button` и `.floating-toggle`, убрать фиксированную высоту 36px; `#shutdownBtn.danger` — сплошной destructive, тумблеры `.danger` — active-state через новый `--color-state-active`; `button.success` получает success-токены вместо primary; hover-фоны вынесены в токены `--color-state-primary-hover` (`#3870be`) и `--color-state-danger-hover` (`#b04848`) с явным `color: var(--color-on-primary)` и dark-переопределениями. Классы и JS не меняются.
- Статус: [PLANNED]

### [2026-10-12 01:13] Task 8: guard-тесты кнопок и состояний
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Добавлены хелперы `_tokens`/`_contrast` (WCAG relative luminance) и четыре теста: touch-target для всех кнопок через `--tap-target` (без фиксированной высоты 36px), разделение destructive (`#shutdownBtn.danger` → `--color-state-danger`) и active-state (`#aiToggleBtn/#ingestToggleBtn/#recordToggleBtn.danger` → новый `--color-state-active`), различие `button.success`/`button.primary`, контраст hover-фонов (`#3870be`, `#b04848`) с явным `color: var(--color-on-primary)` в light и dark. `_rule` теперь нормализует пробелы, чтобы матчить групповые селекторы.
- Статус: [DONE]
- Проверка: RED подтверждён — `pytest tests/test_ui_contract.py -q` → 4 failed, 16 passed (отсутствуют токены/правила). При GREEN исправлен хелпер `_contrast` (срез `#` у HEX) и групповые селекторы матчатся через нормализацию пробелов в `_rule`.
- Следующий шаг: GREEN — правка `app/web/static/style.css`.

### [2026-10-11 21:02] Ошибка при обновлении журнала и восстановление
- Файл: `PROGRESS.md`
- Действие: изменён
- Суть: При вставке записей Task 7 скрипт ошибочно пропустил содержимое между `<!-- LOG_START -->` и `<!-- LOG_END -->` (записи Tasks 1–6 были потеряны). Файл восстановлен из snapshot-блоба opencode (`801946d608ec50f3701a0c7558b1f4a72b05edf4`, 390 строк) и заново дополнен записями Task 7.
- Статус: [DONE] (потери нет)

### [2026-10-11 21:02] Task 7: секции General / Recording / AI
- Файл: `app/web/templates/dashboard.html`
- Действие: изменён
- Суть: `#settingsBody` разбит на три `.section`-карточки с `h3.section-title` (General, Recording, AI). Поля перенесены без изменения id/классов; `#aiPanel` остался внутри AI-секции, `#aiHeaderRow` и `#aiSettingsSection` сохранены; удалён лишний закрывающий `</div>` и висячий комментарий `<!-- AI Events -->`, `hr.divider` между секциями убран (карточки имеют собственную рамку). JS не изменялся.
- Статус: [DONE]

### [2026-10-11 21:02] Task 7: CSS `.section` / `.section-title`
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Добавлен блок `/* settings sections */` перед `/* fields */`: `.section` (рамка, радиус, padding, фон — только токены) и `.section-title` (`margin: 0 ...`, `--font-size-sm`, `--font-weight-strong`, `--line-height-normal`, `--color-text`). Правила `#aiPanel` не трогались, `!important` не добавлен.
- Статус: [DONE]

### [2026-10-11 21:02] Task 7: guard-тесты секционирования Settings
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Добавлен `SettingsWalker` (html.parser) — стек открытых тегов, поле привязывается к ближайшему `.field`, секция — к ближайшей `.section`. Четыре теста: порядок `h3`-заголовков General/Recording/AI и состав секций; `.section`/`.section-title` через токены; `#aiPanel` внутри AI-секции и ни одно CSS-правило с `#aiPanel` не задаёт `display`/`!important`; `.span-2` = `grid-column: 1 / -1` и прямые дети сетки внутри секции.
- Статус: [DONE]
- Проверка: RED подтверждён (4 failed: поля вне секций, `.section` не определён, `#aiPanel` вне AI-секции, `.span-2` не внутри сетки внутри секции), затем `pytest -q` → 85 passed. Баланс тегов dashboard.html: 0 unclosed, 0 mismatch. Render-smoke (Flask test client): titles `['General', 'Recording', 'AI']`, `#aiPanel` внутри AI-секции, 5 `.span-2` полей.
- Следующий шаг: Task 8 по `DESIGN_PLAN.md`.


### [2026-10-11 20:44] Task 6: utility-классы вместо inline-стилей
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Добавлены токены `--font-weight-strong: 700` и `--space-divider: 14px`, а также utility-блок `.row-between`, `.gap-4`, `.mt-4`, `.mt-5`, `.divider`, `.strong` — все значения через токены, блок размещён после базовых правил и перед media-queries. `.row` уже даёт `display:flex; gap: var(--space-3); flex-wrap: wrap`, поэтому `gap:8px`/`flex-wrap:wrap` из HTML удалены как дубли.
- Статус: [DONE]

### [2026-10-11 20:44] Task 6: dashboard.html — 20 inline-стилей заменены utility-классами
- Файл: `app/web/templates/dashboard.html`
- Действие: изменён
- Суть: Все 20 `style="..."` заменены классами (`row row-between`, `gap-4`, `mt-4`, `mt-5`, `divider`, `strong`); `<label style="display:none;">` → `<label hidden>`. `#aiPanel` намеренно не получает CSS `display:none`: `syncAiUi()` (`dashboard.js:113`) пишет `panel.style.display = on ? "" : "none"`, и CSS-правило перебило бы пустое inline-значение — начальное скрытие обеспечивает вызов `syncAiUi()` на `DOMContentLoaded` (`dashboard.js:818`).
- Статус: [DONE]
- Проверка: computed-значения совпадают с прежними inline (row-between = space-between/center, divider = 14px 0, mt-4 = 10px, mt-5 = 12px, strong = 700, `#aiHeaderRow` gap = 10px, hidden label = display none); `#aiPanel` display none → block после Enable; 375px — `.layout` display block, панели 343px, horizontal scroll false; 1280px — row layout, splitter visible; консоль — только `favicon.ico` 404. Сервер перезапущен (PID 7312 отдавал кэш Jinja).

### [2026-10-11 20:44] Task 6: guard-тесты
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: снят `xfail` с `test_dashboard_has_no_inline_styles` (RED подтверждён: 20 inline-стилей), добавлен `test_utility_classes_are_used_and_defined` (RED подтверждён: классы не использованы и не определены). Убран неиспользуемый `import pytest`.
- Статус: [DONE]
- Проверка: `.\venv\Scripts\python.exe -m pytest -q` → 81 passed, 0 xfailed.

### [2026-10-11 12:18] Task 5: фикс каскада — inline-стили сплиттера побеждали media-правила
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: В `@media (max-width: 900px)` `.layout` переведён с `flex-direction: column` на `display: block`, `#leftPane` получил `max-width: 100%`. Это делает inline `flex` (пишется в `dashboard.js:721-722`, восстанавливается из `localStorage("rightPaneWidth")` при загрузке — `dashboard.js:726-729`) недействительным, а inline `width` зажимается через `max-width: 100%` — без `!important`.
- Статус: [DONE]
- Проверка: RED подтверждён (2 failed на прежней версии CSS), затем `.\venv\Scripts\python.exe -m pytest -q` → 79 passed, 1 xfailed. Browser-smoke (DevTools, http://127.0.0.1:8010, inline width 811px из localStorage): 375px — `.layout` display block, `#rightPane` 343×1088 (по контенту), `#settingsBody` не обрезан (scrollH 934 = clientH 934), horizontal scroll false; 768px — stacked, splitter hidden, no clipping; 1280px — flex row, splitter visible, `#rightPane` 768px (= 60vw), драг работает (811 → 354). Collapsed на 375px: `#rightPane.collapsed` display none, FAB top 607 / bottom 651 — не перекрывает topbar (125) и footer (663). Состояние браузера восстановлено (inline-стили сняты, ключ `rightPaneWidth` удалён).

### [2026-10-11 12:18] Task 5: guard-тест приведён к фактическому каскаду
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: `test_panels_stack_vertically_below_900px` теперь требует `.layout { display: block }` (а не `flex-direction: column`) и `max-width: 100%` для `#leftPane`; `max-width: 60vw` трактуется как ограничение выше 900px (ниже 900px оно противоречит `min-width: 280px` и full-width критерию). Добавлен регрессионный `test_stacked_panes_survive_splitter_inline_width`: запрет `!important` в media-блоке + `display: block` + `max-width: 100%` для обеих панелей.
- Статус: [DONE]
### [2026-10-11 04:12] Task 5: responsive breakpoints
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Добавлены media-queries `@media (max-width: 900px)` (`.topbar` wrap, `.layout` → `flex-direction: column; height: auto`, `.pane` → `min-width: 0`, `.pane.left` и `#rightPane` → `width: 100%`, `#rightPane` → `max-width: 100%`, `#splitter` → `display: none`) и `@media (max-width: 640px)` (`.grid`, `.settings-grid, .ai-grid` → `grid-template-columns: 1fr`, `button` → `min-height: var(--tap-target)`, `#settingsBody` → `width: 100%`). В `:root` добавлен токен `--tap-target: 44px`. Селекторы, id и классы не переименованы, `!important` не добавлен.
- Статус: [PARTIAL: collapsed на mobile не проверен — visitor-mode скрывает `#rightPane`, нужен авторизованный session]
- Проверка: `.\venv\Scripts\python.exe -m pytest -q` → 78 passed, 1 xfailed. Browser-smoke (DevTools, http://127.0.0.1:8010, visitor-mode): 375px — column layout, splitter hidden, settings-grid 1fr, button min-height 44px, `#settingsBody` 100%, topbar wrap без перекрытия, horizontal scroll = false; 768px — column layout, splitter hidden, horizontal scroll = false; 1280px — row layout, `#rightPane` max-width 768px (= 60vw), topbar nowrap. Консоль: только ожидаемый 401 (visitor-polling).
- Следующий шаг: проверить collapsed на mobile в авторизованной сессии.

### [2026-10-11 04:05] Task 5: responsive breakpoints (план)
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Guard-тесты Task 5 — `_media_block()`/`_media_rules()`, вертикальная раскладка панелей и `width/max-width: 100%` ниже 900px, скрытие `#splitter` ниже 900px, single-column grid + touch-target 44px + `#settingsBody` full width ниже 640px, сохранение `#rightPane { max-width: 60vw }`.
- Статус: [DONE] (RED подтверждён: 3 failed — отсутствие media-блоков)
### [2026-10-11 03:50] Task 4: высота, footer и box-sizing (план)
- Файл: `tests/test_ui_contract.py`
- Действие: изменён
- Суть: Guard-тест Task 4 — токены `--topbar-height`/`--footer-height`, `box-sizing: border-box`, `100dvh`, `body { padding-bottom: var(--footer-height) }`, `.layout` считается из токенов, footer остаётся `fixed`, запрет magic-чисел 48px/60px.
- Статус: [DONE] (RED подтверждён: 1 failed)

### [2026-10-11 03:57] Task 4: высота, footer и box-sizing
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Добавлены layout-токены `--topbar-height: 57px` и `--footer-height: 37px` (фактические измеренные высоты), глобальный `box-sizing: border-box` (`*`, `::before`, `::after`), `100dvh` вместо `100vh`, `body` → flex-колонка с `padding-bottom: var(--footer-height)`, `.layout` → `calc(100dvh - var(--topbar-height) - var(--footer-height))` + `min-height: 0` + `flex: 1 1 auto`, `.topbar`/`.access-panel` → `flex: 0 0 auto`, `#leftPane` → `min-height: 0; overflow-y: auto` (внутренний скролл вместо обрезки нижних карточек), `.app-footer` → `min-height: var(--footer-height)`. Magic-числа 48px/60px удалены.
- Статус: [DONE]
- Проверка: `pytest -q` → 75 passed, 1 xfailed (новый guard-тест `test_layout_height_uses_tokens_without_magic_numbers`). Browser-smoke при 700/850/1000px: page scroll = false, footer 37px фиксирован и виден, `body padding-bottom` = 37px = фактическая высота footer, нет перекрытия `#settingsBody`/`#log_tail`, `#leftPane` скроллится внутри.
- Следующий шаг: Task 5 (по DESIGN_PLAN.md)

### [2026-10-11 03:41] Task 3: чистка каскада (дубли и мёртвые селекторы)
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Файл переписан единым структурированным блоком (tokens → dark → base → topbar → layout → fields → ai → status → footer). Слеяны дубли: `html`+`html, body`, `body`, `img`, `.layout`, `.field`/`.field label`/`.field input,select,textarea`, `.settings-grid`+`.ai-grid` (в т.ч. `#aiSettingsSection.ai-grid`), `#settingsBody`, `#leftPane`, `#rightPane`, `#rightPane.collapsed`, `#splitter` (в т.ч. `#splitter.splitter`), `.brand-title`, `.brand-subtitle`, тумблеры (`#snapshotBtn` → группа `#ingestToggleBtn, #recordToggleBtn`). Удалено мёртвое: `.grid2`, `.container`, `.ai-summary`, `#splitter.hidden`, `.ai-grid .field*`, `.right.collapsed`/`.splitter.collapsed` (слиты в `#rightPane.collapsed, #splitter.collapsed`). `#rightPane` получил `min-width: 280px` (совпадает с clamp `dashboard.js:748`).
- `!important`: сокращён с 22 до 4 — только `[hidden]` и `.visitor-mode`. Collapse переведён на `display: none` (`#rightPane.collapsed, #splitter.collapsed`), что перебивает inline `width/flex` из drag без `!important`.
- Статус: [DONE]
- Проверка: `.\venv\Scripts\python.exe -m pytest -q` — 73 passed, 1 xfailed, 1 xpassed → после снятия xfail-маркера с guard-теста (`tests/test_ui_contract.py`) — `tests/test_ui_contract.py`: 5 passed, 1 xfailed (остался только Task 6 xfail). Скрипт-проверка: дубликаты селекторов — только `:root` (light + dark, обязательно); `!important` — 4, только в разрешённых селекторах; мёртвые селекторы отсутствуют. Browser-smoke (DevTools, http://127.0.0.1:8010, dark): expanded `#rightPane` = flex/360px/min-width 280px; при симулированном drag (inline width+flex) collapse даёт `display: none` для `#rightPane` и `#splitter`, `#leftPane` занимает всю ширину (1373px); re-expand восстанавливает 360px; visitor-mode скрывает `#rightPane`/`#splitter`; splitter-line = rgb(74,80,88) = #4a5058; консоль чистая (401 — ожидаемый visitor-polling).
- Сервер, запущенный для smoke (port 8010), остановлен.
- Следующий шаг: Task 4 (footer/spacing tokens по `DESIGN_PLAN.md`).

### [2026-10-10 19:47] Task 3: чистка каскада (дубли и мёртвые селекторы) (план)
- Файл: `app/web/static/style.css`
- Действие: планируемое изменение
- Суть: Слить дубли: `.layout`, `img`, `.field` (+ `.field label`, `.field input/select/textarea`), `#settingsBody`, `#rightPane`, `#rightPane.collapsed`, `#leftPane`, `#splitter` (в т.ч. `#splitter.splitter`), `.brand-title`, `.brand-subtitle`, `body`, `html`, `.ai-grid` (в т.ч. `#aiSettingsSection.ai-grid`), кнопка-тумблеры (`#snapshotBtn` к `#ingestToggleBtn, #recordToggleBtn`). Удалить мёртвое: `.grid2`, `.container`, `.ai-summary`, `#splitter.hidden`, `.ai-grid .field*` (полные копии `.field*`). `#rightPane` min-width = 280px (совпадает с clamp `dashboard.js:748`).
- Важное уточнение по `!important`: проверено в браузере — `.right.collapsed { display: none }` НЕ работает для `#rightPane`, потому что `#rightPane` (специфичность id 1-0-0) перебивает `.right.collapsed` (0-2-0); фактический механизм collapse — `!important`-правило, которое перебивает inline `right.style.width/flex` из drag (`dashboard.js:721-722`). Чтобы `!important` остался только в `[hidden]` и `.visitor-mode`, collapse переводится на `display: none` (`#rightPane.collapsed`, `#splitter.collapsed`) — inline-стили width/flex при `display: none` не влияют, визуальный результат идентичен.
- Статус: [PLANNED]

### [2026-10-10 19:42] Task 2: dark mode и color-scheme
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: В `:root` добавлен `color-scheme: light dark`; после токенов добавлен блок `@media (prefers-color-scheme: dark)`, переопределяющий color- и shadow-токены (HEX остаётся только внутри `:root`-блоков). Добавлен `html { scrollbar-color: var(--color-border) var(--color-surface-muted) }`. Нативные controls темизируются: `input, select` и `textarea` получили `background: var(--color-surface)` и `color: var(--color-text)` (+ `accent-color` для checkbox, для checkbox фон прозрачный); `body` получил `color: var(--color-text)`. Пользовательского переключателя темы нет, JS не трогался.
- Dark-пары (проверено скриптом WCAG на фоне `--color-bg` #16181c): text #e0e0e0 = 13.46, muted #a5a5a5 = 7.22, success #24d35e = 8.93, warning #dbb252 = 8.89, danger #e09d99 = 8.00; дополнительно: state-цвета на своих tinted-поверхностях 6.62–7.58, on-primary #0f1216 на state-фонах 8.45–9.44, text на surface 12.52.
- Статус: [DONE]
- Проверка: `python -m pytest -q` (venv) — 73 passed, 2 xfailed; `rg "#[0-9a-fA-F]{3,6}"` — HEX только в `:root` (light + dark); `!important` — 22 (не вырос). Browser-smoke (DevTools, http://127.0.0.1:8010, `prefers-color-scheme: dark`): токены резолвятся (bg #16181c, surface #1c1f24, text #e0e0e0, border #4a5058, media-bg #101215), `color-scheme` = light dark, `scrollbar-color` = rgb(74,80,88)/rgb(31,34,39), input/select/textarea = тёмный фон с светлым текстом, `#live` читается (тёмный фон + контрастная рамка), splitter-line = #4a5058, visitor-mode скрывает `#rightPane`/`#splitter`/`.logs-box`, `#rightPane.collapsed` работает, консоль чистая (401 — ожидаемый visitor-polling). Скриншот: topbar, cards, badges (warn-pill), buttons, footer — темные.
- Сервер, запущенный для smoke (port 8010), остановлен.
- Следующий шаг: Task 3 (чистка каскада — дубли и мёртвые селекторы).

### [2026-10-10 19:38] Task 2: dark mode и color-scheme (план)
- Файл: `app/web/static/style.css`
- Действие: планируемое изменение
- Суть: Добавить `color-scheme: light dark` в `:root` и блок `@media (prefers-color-scheme: dark)`, переопределяющий цветовые токены (shadow — только внутри `:root`-блоков, чтобы проверка «HEX только в :root» осталась GREEN). Плюс `scrollbar-color` на `var(--color-border)` и темизация нативных controls (input/select/textarea получают background/color из токенов), `body` получает `color: var(--color-text)`. Пользовательского переключателя темы нет, JS не трогается.
- Dark-пары (пересчитаны WCAG на фоне `--color-bg` #16181c): text #e0e0e0 = 13.46, muted #a5a5a5 = 7.22, success #24d35e = 8.93, warning #dbb252 = 8.89, danger #e09d99 = 8.00; on-primary #0f1216 на state-фонах 8.45–9.44; state-цвета на своих tinted-поверхностях ≥ 6.6.
- Статус: [PLANNED]

### [2026-10-10 19:34] Task 1: токен-слой в style.css
- Файл: `app/web/static/style.css`
- Действие: изменён
- Суть: Строка 1 — блок `:root` с слоями color / typography / spacing / radius / shadow / motion / z-index; `--sidebarW` и `--splitW` перенесены в него и используются. Все правила переписаны на `var(--token)` вместо HEX, `font-family` — из `--font-sans` (`system-ui, Arial, sans-serif`) и `--font-mono`. Дубли селекторов не сливались (Task 3), dark mode не добавлялся (Task 2), magic-числа высоты/footer не трогались (Task 4).
- Исправленные контрастные пары (WCAG, пересчитаны скриптом): `--color-text` #1e1e1e = 16.67 (было #222 = 15.91), `--color-text-muted` #636363 = 6.01 (было #666 = 5.74), on-primary #ffffff на `--color-state-primary` #366cc0 = 5.17 (было #3870be = 4.97), success #1a6537 = 7.09, warning #826325 = 5.58, danger #b24642 = 5.46 (было #b04848 = 5.43/3.87 на hover-фоне), footer #888 (3.54) → `--color-text-subtle` #707070 = 4.95. Состояния ok/warn/err получили текстовый цвет токена (бейджи/pills/htags).
- Статус: [DONE]
- Проверка: `python -m pytest -q` — 73 passed, 2 xfailed (baseline + guard-тест); `rg "#[0-9a-fA-F]{3,6}"` — HEX только в `:root`; `!important` — 22 (не вырос); `var(--sidebarW)`/`var(--splitW)` используются. Browser-smoke (DevTools, http://127.0.0.1:8010): токены резолвятся, `font-family` = system-ui, visitor-mode скрывает `#rightPane`/`#splitter`, `#rightPane.collapsed` → width 0, footer z-index 999, splitter z-index 50, консоль чистая (401 — ожидаемый visitor-polling).
- Замечено, но не тронуто: у `body` нет `color` (наследует `#000`) — для dark mode (Task 2) базовый текст нужно привязать к `--color-text`; shadow/motion-токены объявлены, но пока не используются (Task 8/14).
- Следующий шаг: Task 2 (dark mode + `color-scheme`) или Task 3 (чистка каскада).

### [2026-10-10 19:24] Task 1: токен-слой в style.css (план)
- Файл: `app/web/static/style.css`
- Действие: планируемое изменение
- Суть: Строка 1 заменяется блоком `:root` с слоями color / typography / spacing / radius / shadow / motion / z-index (плюс сохранённые `--sidebarW`, `--splitW`). Существующие правила переписываются на `var(--token)` вместо HEX; `font-family` — из токенов `--font-sans` (`system-ui, Arial, sans-serif`) и `--font-mono`. Дубли селекторов не сливаются (это Task 3), dark mode не добавляется (Task 2), magic-числа heights не меняются (Task 4).
- Контрастные пары (пересчитаны WCAG, фон `--color-bg`): text #1e1e1e = 16.67, muted #636363 = 6.01, on-primary 5.17 на --color-state-primary #366cc0, success #1a6537 = 7.09, warning #826325 = 5.58, danger #b24642 = 5.46; footer #888 (3.54) переводится на --color-text-subtle #707070 (4.95).
- Статус: [PLANNED]

### [2026-10-10 19:16] Task 0: guard-тест DOM-контракта
- Файл: `tests/test_ui_contract.py`
- Действие: создан
- Суть: Guard-тест фиксирует неизменяемый DOM-контракт redesign до начала правок: 68 id из `dashboard.html`, id `floatingExpandBtn` (создаётся `dashboard.js`, а не шаблоном), классы, которыми управляет JS (`visitor-mode`, `collapsed`, `primary`, `danger`, `ok`, `warn`, `err`, `logs-box`), классы генерируемой JS-разметки (`status-*`, `pill`, `model-info-*`, `ai-h*`, `ai-kv`, `hint`, `badge`, `floating-toggle`) и зафиксированные литералы. Проверки «нет inline-стилей» и «`!important` только в `[hidden]`/`.visitor-mode`» помечены `xfail` — они станут GREEN после Task 6 и Task 3.
- Статус: [DONE]
- Проверка: `python -m pytest -q` — 73 passed, 2 xfailed (69 baseline + 4 новых зелёных проверок + 2 ожидаемо-падающих). RED-проверка: временное переименование `id="aiModelInfo"` → тест падает с «contract ids missing», откат выполнен, файл восстановлен.
- Уточнение к плану: фактические счётчики на baseline — 20 inline-атрибутов `style=` (не 26) и 23 `!important`; итог suite — 73 passed + 2 xfailed, а не 70 passed, потому что guard-тест разбит на 6 проверок.
- Следующий шаг: Task 1 (токен-слой в `style.css`).


### [2026-10-10 19:11] Решения по review плана redesign (web)
- Файл: `DESIGN_PLAN.md`
- Действие: изменён
- Суть: Применены ответы пользователя: dark mode только через `prefers-color-scheme` (без пользовательского переключателя), шрифт `system-ui` с fallback `Arial`, splitter только declarative ARIA + `tabindex` без JS, `!important` остаётся только для `[hidden]` и `.visitor-mode`, footer остаётся `fixed` с padding по фактической высоте через токен, добавлен Task 0 — guard-тест `tests/test_ui_contract.py` для DOM-контракта перед любыми правками. Обновлены Open Questions (закрытые/открытые), Parallelization и счёт тестов до 70 passed.
- Статус: [DONE]
- Следующий шаг: Task 0 — создать `tests/test_ui_contract.py` (GREEN на baseline), затем Phase 1.

### [2026-10-10 19:00] План redesign: только серверная (web) часть
- Файл: `DESIGN_PLAN.md`
- Действие: создан
- Суть: План из 14 задач по визуальному слою web-дашборда (`app/web/templates/dashboard.html`, `app/web/static/style.css`) на основе аудита UI Designer: токены, dark mode, чистка каскада (дубли/мёртвые селекторы/`!important`), layout и footer, responsive, состояния и пустые состояния, touch-таргеты, WCAG AA (focus-visible, aria-live, labels, заголовки). Android-клиент (`PhoneCamSender/`) в план не включён по требованию пользователя.
- Зафиксировано: неизменяемый DOM/классовый контракт JS (`visitor-mode`, `collapsed`, `primary`/`danger`, `ok`/`warn`/`err`, `status-*`, `ai-h*`, `model-info-*`, `hint`, `badge`, `.logs-box`), литералы тестов (`Model Info`, `Pair CamFlow in its Settings.`, `v{{ sentinel_version }}`, `{{ release_date }}`, id `aiModelInfo`/`ai_provider`/`ai_model`/`ai_base_url`/`ai_api_key`/`ai_request_timeout_sec`/`aiProviderHint`) и clamp ширины правой панели 280px из `dashboard.js:748`.
- Статус: [DONE]
- Проверка: `python -m pytest -q` — 69 passed (baseline, код не изменялся).
- Следующий шаг: review плана человеком перед Phase 1.

### [2026-10-10 18:50] План визуальных улучшений дизайна (без изменения бизнес-логики)
- Файл: `DESIGN_PLAN.md`
- Действие: планируемое создание (переопределено записью 19:00 — scope сужен до серверной части)
- Суть: зафиксировать план работ по дизайну web-дашборда и Android-клиента на основе аудита UI Designer.
- Статус: [PARTIAL: Android-часть исключена из плана по запросу пользователя]

### [2026-10-10 17:37] Перевод документации на русский и чистка внешних ссылок
- Файлы: `README.md`, `CamFlow_UserGuide.md`, `README_CN.md`, `CamFlow_UserGuide_CN.md`, `LICENSE`, `app/web/templates/dashboard.html`, `tests/test_version_consistency.py`, `PhoneCamSender/app/build.gradle.kts`, `docs/UA_ONBOARDING.md`, `specs/3-Версионирование/spec.md`
- Действие: изменён (перевод на русский), удалён (`README_CN.md`, `CamFlow_UserGuide_CN.md` через `git rm`)
- Суть: `README.md` и `CamFlow_UserGuide.md` полностью переведены на русский; удалены бейджи img.shields.io, блоки «Community Recognition» и «Star History», переключатели языка и внешние URL/картинки; автор Suzuran0y заменён на EmitterLight (документация, LICENSE, футер Dashboard). Исправлены ссылки на удалённые CN-файлы: тест согласованности версий, Gradle-синхронизация, onboarding-документ и spec по версионированию.
- Сохранено: якоря (`#sec1…#sec55`, `#privacy-mode`, `#camera-pairing`, `#upload-diagnostics`, `#resolution-preference`, `#background-operation`), технические строки для теста (`v1.1.4`, путь APK, SHA-256, `build 16`, `2026-09-27`), функциональные Base URL провайдеров и LAN-плейсхолдеры.
- Статус: [DONE]
- Проверка: `python -m pytest -q` — 69 passed; `rg` не находит CN-ссылок, бейджей и внешних URL в документации.
- Не тронуто (архив): `2026-10-09-camflow-background-capture.md`, `docs/superpowers/specs/2026-10-09-camflow-background-capture-spec.md`, исторические записи этого журнала.

### [2026-10-10 15:21] Fix stop-dashboard.ps1
- Файл: `C:\Users\Dmitr\Desktop\stop-dashboard.ps1`
- Действие: изменён
- Суть: Переименована переменная `$pid` в `$procId`, так как `$PID` — встроенная readonly-автопеременная PowerShell и присваивание ей вызывает ошибку; скрипт завершения дашборда теперь работает.
- Статус: [DONE]
- Следующий шаг: запускать скрипт через `powershell -ExecutionPolicy Bypass -File`, а не двойным кликом (`.ps1` ассоциирован с Блокнотом).

### [2026-10-10 14:42] Full rebuild knowledge graph
- Файл: `.ua/knowledge-graph.json`, `.ua/meta.json`, `.ua/fingerprints.json`
- Действие: обновлён
- Суть: Полный пересчёт knowledge graph на русском: 126 файлов, 337 узлов, 719 рёбер, 7 слоёв, 14 шагов тура; baseline commit `2aa8c5bbda54a60c05f36b6bc4a262ea5d8ae64b`.
- Статус: [DONE]
- Предупреждения: 14 orphan-узлов (APK, `.gitkeep`, `__init__.py`, `proguard-rules.pro`, `settings.gradle.kts`, `ExampleInstrumentedTest.kt`); Phase 3 выполнен детерминированно из-за ограничения контекста subagent.
- Следующий шаг: при необходимости запустить dashboard или `--review` для LLM-проверки.

### [2026-10-10 13:12] Batch 1 knowledge graph
- Файл: .ua/intermediate/batch-1-part-1.json и .ua/intermediate/batch-1-part-2.json
- Действие: создан (2 части, split по протоколу)
- Суть: Анализ batch 1 (11 python-файлов: config_store, auth, frame_buffer, runtime, upload_stats + тесты). 51 узел (17+34), 168 рёбер (58+110); imports 31 = сумма batchImportData; tested_by production→test 19; split по протоколу (edges>120).
- Статус: [DONE]
- Следующий шаг: merge batch-graphs

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
- Доказательства: `log/server.log` — ни одной строки `upload begin` с рестарта сервера (00:53:33); `app/config/auth.sqlite3` — устройство `MY` спарено (не 401); `GET /api/status` — `last_frame_age_age_sec: null`, `upload_fps: 0.0`; серверный путь upload→frame_buffer проверен тестами (69 passed).
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
