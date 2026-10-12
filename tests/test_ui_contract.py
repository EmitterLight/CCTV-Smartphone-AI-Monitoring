import re
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DASHBOARD_HTML = ROOT / "app" / "web" / "templates" / "dashboard.html"
STYLE_CSS = ROOT / "app" / "web" / "static" / "style.css"
DASHBOARD_JS = ROOT / "app" / "web" / "static" / "dashboard.js"
ACCESS_JS = ROOT / "app" / "web" / "static" / "access.js"

HTML_IDS = {
    "msg",
    "status_readable",
    "log_tail",
    "ai_status_raw",
    "aiStateBadge",
    "aiEventId",
    "aiDwell",
    "aiHealth",
    "aiLastLine",
    "aiLastSummary",
    "aiTimes",
    "aiMetrics",
    "aiModelInfo",
    "aiHistoryList",
    "accessStatus",
    "loginForm",
    "logoutBtn",
    "devicePanel",
    "deviceList",
    "pairingBtn",
    "pairingCode",
    "adminToken",
    "accessMessage",
    "live",
    "liveLocked",
    "leftPane",
    "rightPane",
    "splitter",
    "toggleSettingsBtn",
    "shutdownBtn",
    "aiToggleBtn",
    "ingestToggleBtn",
    "recordToggleBtn",
    "snapshotBtn",
    "aiProviderHint",
    "stream_fps",
    "jpeg_quality",
    "record_fps",
    "segment_seconds",
    "out_root",
    "cam_name",
    "codec",
    "autosave",
    "viewer_auth_required",
    "record_cleanup_enabled",
    "record_retention_days",
    "record_max_storage_gb",
    "record_min_free_gb",
    "ai_enabled",
    "ai_provider",
    "ai_model",
    "ai_base_url",
    "ai_api_key",
    "ai_request_timeout_sec",
    "ark_model",
    "ark_api_key",
    "ai_interval_observe",
    "ai_dwell_threshold_sec",
    "ai_end_grace_sec",
    "ai_jpeg_quality",
    "motion_ratio_threshold",
    "motion_min_interval",
    "ai_prompt_template",
    "ai_scene_profile",
    "ai_session_focus",
    "ai_prompt_extra",
}

JS_CREATED_IDS = {"floatingExpandBtn"}

JS_MANAGED_CLASSES = {
    "visitor-mode",
    "collapsed",
    "primary",
    "danger",
    "ok",
    "warn",
    "err",
    "logs-box",
}

GENERATED_CLASSES = {
    "status-grid",
    "status-item",
    "status-k",
    "status-v",
    "pill",
    "model-info-card",
    "model-info-top",
    "model-info-grid",
    "model-info-row",
    "model-info-status",
    "ai-hitem",
    "ai-hrow",
    "ai-htime",
    "ai-htag",
    "ai-hmeta",
    "ai-hsummary",
    "ai-kv",
    "hint",
    "badge",
    "floating-toggle",
}

FIXED_LITERALS = {
    "Pair CamFlow in its Settings.",
    "Model Info",
    "v{{ sentinel_version }}",
    "{{ release_date }}",
}

IMPORTANT_ALLOWED_SELECTORS = ("[hidden]", ".visitor-mode")


def _read(path):
    return path.read_text(encoding="utf-8")


def _css_rules(css):
    return [(sel.strip(), body) for sel, body in re.findall(r"([^{}]+)\{([^{}]*)\}", css)]


def test_dashboard_has_all_contract_ids():
    html = _read(DASHBOARD_HTML)
    missing = [node_id for node_id in HTML_IDS if f'id="{node_id}"' not in html]
    assert not missing, f"contract ids missing from dashboard.html: {sorted(missing)}"


def test_js_created_id_is_created_by_js():
    js = _read(DASHBOARD_JS)
    for node_id in JS_CREATED_IDS:
        assert f'"{node_id}"' in js, f"{node_id} must be created by dashboard.js"


def test_contract_classes_are_styled_or_used_by_js():
    css = _read(STYLE_CSS)
    js = _read(DASHBOARD_JS) + _read(ACCESS_JS)
    for cls in JS_MANAGED_CLASSES | GENERATED_CLASSES:
        assert f".{cls}" in css or re.search(rf'["\s.]({cls})["\s]', js), f"class {cls} is not styled or used"


def test_fixed_literals_are_preserved():
    html = _read(DASHBOARD_HTML)
    for literal in FIXED_LITERALS:
        assert literal in html, f"literal changed: {literal}"


def test_dashboard_has_no_inline_styles():
    html = _read(DASHBOARD_HTML)
    found = re.findall(r'style="', html)
    assert not found, f"{len(found)} inline style attributes remain"


UTILITY_CLASSES = {"row-between", "gap-4", "mt-4", "mt-5", "divider", "strong", "empty-state"}


def test_utility_classes_are_used_and_defined():
    html = _read(DASHBOARD_HTML)
    css = _read(STYLE_CSS)
    used = {cls for attr in re.findall(r'class="([^"]+)"', html) for cls in attr.split()}
    missing_from_html = sorted(UTILITY_CLASSES - used)
    missing_from_css = sorted(cls for cls in UTILITY_CLASSES if f".{cls}" not in css)
    assert not missing_from_html, f"utility classes not used in dashboard.html: {missing_from_html}"
    assert not missing_from_css, f"utility classes not defined in style.css: {missing_from_css}"


def _rule(css, selector):
    rules = [
        body
        for sel, body in _css_rules(css)
        if re.sub(r"\s+", " ", re.sub(r"/\*.*?\*/", "", sel, flags=re.DOTALL)).strip() == selector
    ]
    assert rules, f"rule for {selector} not found"
    return rules[0]


def _tokens(rule):
    return {name: value.strip() for name, value in re.findall(r"(--[a-z0-9-]+):\s*([^;]+);", rule)}


def _contrast(color_a, color_b):
    def luminance(hex_value):
        hex_value = hex_value.lstrip("#")
        channels = [int(hex_value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
        linear = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]

    first, second = luminance(color_a), luminance(color_b)
    lighter, darker = max(first, second), min(first, second)
    return (lighter + 0.05) / (darker + 0.05)


def test_layout_height_uses_tokens_without_magic_numbers():
    css = _read(STYLE_CSS)
    root = _rule(css, ":root")
    assert "--topbar-height" in root, "missing --topbar-height token"
    assert "--footer-height" in root, "missing --footer-height token"

    assert "box-sizing: border-box" in css, "missing box-sizing: border-box"
    assert "100dvh" in css, "layout height must use 100dvh"

    body_rule = _rule(css, "body")
    assert "padding-bottom: var(--footer-height)" in body_rule, "body must reserve footer height via token"

    layout_rule = _rule(css, ".layout")
    assert "calc(100dvh - var(--topbar-height) - var(--footer-height))" in layout_rule, (
        ".layout height must be computed from --topbar-height and --footer-height"
    )

    footer_rule = _rule(css, ".app-footer")
    assert "position: fixed" in footer_rule, "footer must stay position: fixed (review decision)"

    for magic in (r"\b48px\b", r"\b60px\b"):
        assert not re.search(magic, css), f"magic number {magic} must be replaced by tokens"


def _media_block(css, condition):
    start = css.find(f"@media ({condition})")
    assert start != -1, f"no @media ({condition}) block"
    open_brace = css.index("{", start)
    depth = 0
    for i in range(open_brace, len(css)):
        if css[i] == "{":
            depth += 1
        elif css[i] == "}":
            depth -= 1
            if depth == 0:
                return css[open_brace + 1:i]
    raise AssertionError(f"unbalanced @media ({condition}) block")


def _media_rules(css, condition):
    return {
        re.sub(r"\s+", " ", sel).strip(): body
        for sel, body in _css_rules(_media_block(css, condition))
    }


def test_panels_stack_vertically_below_900px():
    css = _read(STYLE_CSS)
    rules = _media_rules(css, "max-width: 900px")

    layout = rules.get(".layout")
    assert layout and "display: block" in layout, (
        "panels must stack vertically below 900px without a flex container: dashboard.js writes inline "
        "width/flex from the splitter drag (restored from localStorage on load), and inline styles beat "
        "media rules without !important"
    )

    body = rules.get("body")
    assert body and "height: auto" in body, "body must allow page scrolling below 900px"

    left = rules.get("#leftPane")
    assert left and "width: 100%" in left, "fixed 620px left pane must not overflow narrow viewports"
    assert left and "max-width: 100%" in left, "#leftPane must be clamped to the viewport below 900px"
    assert left and "flex: 0 0 auto" in left, "stacked panes must size to content instead of shrinking"
    assert left and "overflow-y: visible" in left, "stacked panes must not clip content below 900px"

    right = rules.get("#rightPane")
    assert right and "width: 100%" in right, "#rightPane must span the viewport below 900px"
    assert right and "max-width: 100%" in right, "#rightPane must not exceed the viewport below 900px"
    assert right and "flex: 0 0 auto" in right, "stacked panes must size to content instead of shrinking"
    assert right and "overflow-y: visible" in right, "stacked panes must not clip content below 900px"
    assert "max-width: 60vw" in _rule(css, "#rightPane"), "#rightPane must keep max-width: 60vw above 900px"


def test_splitter_is_hidden_below_900px():
    rules = _media_rules(_read(STYLE_CSS), "max-width: 900px")
    splitter = rules.get("#splitter")
    assert splitter and "display: none" in splitter, "splitter must be hidden below 900px"


def test_stacked_panes_survive_splitter_inline_width():
    css = _read(STYLE_CSS)
    block = _media_block(css, "max-width: 900px")
    assert "!important" not in block, "stacked layout must not rely on !important against inline drag styles"

    rules = _media_rules(css, "max-width: 900px")
    layout = rules.get(".layout")
    assert layout and "display: block" in layout, (
        "inline flex from the splitter drag (dashboard.js:721-722) must be inert below 900px"
    )
    for selector in ("#leftPane", "#rightPane"):
        rule = rules.get(selector)
        assert rule and "max-width: 100%" in rule, f"{selector} must clamp inline drag width to the viewport"


def test_small_screen_uses_single_column_and_touch_targets():
    css = _read(STYLE_CSS)
    rules = _media_rules(css, "max-width: 640px")

    assert "--tap-target: 44px" in _rule(css, ":root"), "missing --tap-target token"

    for selector in (".grid", ".settings-grid, .ai-grid"):
        rule = rules.get(selector)
        assert rule and "grid-template-columns: 1fr" in rule, f"{selector} must be single column below 640px"

    button = rules.get("button")
    assert button and "min-height: var(--tap-target)" in button, "touch targets must be 44px below 640px"

    settings_body = rules.get("#settingsBody")
    assert settings_body and "width: 100%" in settings_body, "#settingsBody must span full width below 640px"

    fab = rules.get("#floatingExpandBtn")
    assert fab and "top: auto" in fab and "bottom: calc(var(--footer-height) + var(--space-5))" in fab, (
        "floating expand button must move below the topbar below 640px to avoid overlapping the brand"
    )

    footer = rules.get(".app-footer")
    assert footer and "white-space: nowrap" in footer and "text-overflow: ellipsis" in footer, (
        ".app-footer must stay one line below 640px so --footer-height stays accurate and the FAB does not overlap it"
    )
    footer_inner = rules.get(".footer-inner")
    assert footer_inner and "text-overflow: ellipsis" in footer_inner, (
        ".footer-inner must clip with ellipsis below 640px instead of extending past the viewport"
    )


def test_every_button_meets_the_touch_target():
    css = _read(STYLE_CSS)
    assert "min-height: var(--tap-target)" in _rule(css, "button"), "buttons must be at least --tap-target tall"
    assert "min-height: var(--tap-target)" in _rule(css, ".floating-toggle"), "the floating toggle must be a touch target"
    assert not re.search(r"\b36px\b", css), "fixed button heights must be replaced by --tap-target"


def test_destructive_and_active_states_are_separated():
    css = _read(STYLE_CSS)
    palettes = [
        _tokens(_rule(css, ":root")),
        _tokens(_media_rules(css, "prefers-color-scheme: dark")[":root"]),
    ]

    for tokens in palettes:
        active = tokens.get("--color-state-active")
        assert active, "--color-state-active token must be defined"
        ratio = _contrast(active, tokens["--color-surface-danger"])
        assert ratio >= 4.5, f"--color-state-active is not readable on --color-surface-danger ({ratio:.2f})"

    destructive = _rule(css, "#shutdownBtn.danger")
    assert "var(--color-state-danger)" in destructive, "#shutdownBtn.danger must render as destructive"
    assert "var(--color-state-active)" not in destructive, "destructive style must not reuse the active-state token"

    active_group = "#aiToggleBtn.danger, #ingestToggleBtn.danger, #recordToggleBtn.danger"
    rule = _rule(css, active_group)
    assert "var(--color-state-active)" in rule, f"{active_group} must render as active-state"
    assert "var(--color-state-danger)" not in rule, f"{active_group} must not render as destructive"


def test_success_and_primary_buttons_are_visually_distinct():
    css = _read(STYLE_CSS)
    primary = _rule(css, "button.primary")
    success = _rule(css, "button.success")
    assert primary != success, "button.success and button.primary must not be identical"
    assert "var(--color-surface-primary)" in primary and "var(--color-accent-primary)" in primary
    assert "var(--color-surface-success)" in success and "var(--color-accent-success)" in success


HOVER_TOKENS = {
    "button.primary:hover": "--color-state-primary-hover",
    "button.success:hover": "--color-state-success-hover",
    "button.danger:hover": "--color-state-danger-hover",
    "#shutdownBtn.danger:hover": "--color-state-danger-hover",
    "#aiToggleBtn.danger:hover, #ingestToggleBtn.danger:hover, #recordToggleBtn.danger:hover": "--color-state-active",
}

EXPECTED_LIGHT_HOVER = {
    "--color-state-primary-hover": "#3870be",
    "--color-state-danger-hover": "#b04848",
}


def test_hover_states_are_contrast_with_explicit_text_color():
    css = _read(STYLE_CSS)
    palettes = [
        _tokens(_rule(css, ":root")),
        _tokens(_media_rules(css, "prefers-color-scheme: dark")[":root"]),
    ]

    for selector, token in HOVER_TOKENS.items():
        rule = _rule(css, selector)
        assert f"var({token})" in rule, f"{selector} must use {token}"
        assert "color: var(--color-on-primary)" in rule, f"{selector} must set an explicit text color"

    for tokens in palettes:
        for token, expected in EXPECTED_LIGHT_HOVER.items():
            if tokens is palettes[0]:
                assert tokens[token] == expected, f"{token} must be {expected}, got {tokens.get(token)}"
            ratio = _contrast(tokens[token], tokens["--color-on-primary"])
            assert ratio >= 4.5, f"{token} is not contrasty against --color-on-primary ({ratio:.2f})"


def test_important_is_limited_to_hidden_and_visitor_mode():
    offenders = sorted({
        selector.strip()
        for selector, body in _css_rules(_read(STYLE_CSS))
        if "!important" in body
        and not any(allowed in selector for allowed in IMPORTANT_ALLOWED_SELECTORS)
    })
    assert not offenders, f"!important outside [hidden]/.visitor-mode: {offenders}"


VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}

SECTION_ORDER = ["General", "Recording", "AI"]

EXPECTED_SECTION_FIELDS = {
    "General": {
        "viewer_auth_required",
        "stream_fps",
        "jpeg_quality",
        "cam_name",
        "autosave",
    },
    "Recording": {
        "record_cleanup_enabled",
        "record_retention_days",
        "record_max_storage_gb",
        "record_min_free_gb",
        "record_fps",
        "segment_seconds",
        "out_root",
        "codec",
    },
    "AI": {
        "ai_provider",
        "ai_model",
        "aiProviderHint",
        "ai_base_url",
        "ai_api_key",
        "ai_request_timeout_sec",
        "ark_model",
        "ark_api_key",
        "ai_interval_observe",
        "ai_dwell_threshold_sec",
        "ai_end_grace_sec",
        "ai_jpeg_quality",
        "motion_ratio_threshold",
        "motion_min_interval",
        "ai_prompt_template",
        "ai_scene_profile",
        "ai_session_focus",
        "ai_prompt_extra",
    },
}


class SettingsWalker(HTMLParser):
    def __init__(self):
        super().__init__()
        self.stack = []
        self.sections = []
        self.unsectioned_fields = []
        self.span_2_fields = []
        self.in_h3 = False

    def _innermost(self, wanted):
        for entry in reversed(self.stack):
            if wanted in entry[1]:
                return entry
        return None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        classes = set((attrs.get("class") or "").split())
        parent = self._innermost("section")
        section = {"title": "", "fields": []} if "section" in classes else (parent[2] if parent else None)
        if "section" in classes:
            self.sections.append(section)
        if tag not in VOID_TAGS:
            self.stack.append((tag, classes, section))
        field = self._innermost("field")
        node_id = attrs.get("id")
        if node_id and field is not None:
            if field[2] is None:
                self.unsectioned_fields.append(node_id)
            else:
                field[2]["fields"].append(node_id)
            if "span-2" in field[1]:
                grid = self._innermost("settings-grid") or self._innermost("ai-grid")
                self.span_2_fields.append((node_id, tag, grid))
        if tag == "h3":
            section = self._innermost("section")
            if section is not None and not section[2]["title"]:
                self.in_h3 = True

    def handle_endtag(self, tag):
        if tag in VOID_TAGS:
            return
        for i in range(len(self.stack) - 1, -1, -1):
            if self.stack[i][0] == tag:
                del self.stack[i:]
                break
        if tag == "h3":
            self.in_h3 = False

    def handle_data(self, data):
        if self.in_h3 and self.stack:
            text = data.strip()
            if text and self.stack[-1][2] is not None:
                self.stack[-1][2]["title"] += text


def _walk_settings():
    walker = SettingsWalker()
    walker.feed(_read(DASHBOARD_HTML))
    return walker


def test_settings_fields_are_grouped_into_sections():
    walker = _walk_settings()
    assert not walker.unsectioned_fields, f"fields outside a .section card: {sorted(walker.unsectioned_fields)}"
    titles = [section["title"] for section in walker.sections]
    assert titles == SECTION_ORDER, f"section headings must be h3 in order {SECTION_ORDER}, got {titles}"
    by_title = {section["title"]: set(section["fields"]) for section in walker.sections}
    for title, expected in EXPECTED_SECTION_FIELDS.items():
        missing = sorted(expected - by_title.get(title, set()))
        assert not missing, f"{title} section does not contain these fields: {missing}"


def test_section_cards_are_styled_with_tokens():
    css = _read(STYLE_CSS)
    section = _rule(css, ".section")
    for token in ("var(--color-border", "var(--radius", "var(--space"):
        assert token in section, f".section must be styled via tokens ({token})"

    title = _rule(css, ".section-title")
    assert "font-size: var(--font-size-sm)" in title, ".section-title must use --font-size-sm"
    assert "font-weight: var(--font-weight-strong)" in title, ".section-title must use --font-weight-strong"
    assert "margin: 0" in title, ".section-title must not inherit h3 default margins"


def test_ai_panel_display_stays_js_controlled():
    walker = _walk_settings()
    ai_section = next((s for s in walker.sections if s["title"] == "AI"), None)
    assert ai_section is not None, "AI fields must be inside an AI section"
    assert 'id="aiPanel"' in _read(DASHBOARD_HTML)
    for selector, body in _css_rules(_read(STYLE_CSS)):
        if "#aiPanel" in selector:
            assert "display" not in body, f"#aiPanel rule must not set display (JS sets style.display): {selector}"
            assert "!important" not in body, f"#aiPanel rule must not use !important: {selector}"


def test_span_two_fields_span_the_full_grid():
    css = _read(STYLE_CSS)
    assert "grid-column: 1 / -1" in _rule(css, ".span-2"), ".span-2 must span the full grid"
    walker = _walk_settings()
    assert walker.span_2_fields, "no .span-2 fields found"
    for node_id, tag, grid in walker.span_2_fields:
        assert grid is not None and grid[2] is not None, (
            f".span-2 field {node_id} must be a direct child of a grid inside a .section card"
        )
    assert any(tag == "textarea" for _, tag, _ in walker.span_2_fields), "no textarea .span-2 fields found"


EMPTY_STATE_TEXT = "loading…"
PLACEHOLDER_TEXTS = {EMPTY_STATE_TEXT, "loading.", "loading...", "waiting…", "—"}


class PlaceholderWalker(HTMLParser):
    def __init__(self):
        super().__init__()
        self.stack = []
        self.placeholders = []

    def handle_starttag(self, tag, attrs):
        if tag in VOID_TAGS:
            return
        attrs = dict(attrs)
        self.stack.append({
            "tag": tag,
            "classes": set((attrs.get("class") or "").split()),
            "id": attrs.get("id"),
            "text": "",
        })

    def handle_endtag(self, tag):
        if tag in VOID_TAGS:
            return
        for i in range(len(self.stack) - 1, -1, -1):
            if self.stack[i]["tag"] == tag:
                entry = self.stack[i]
                text = entry["text"].strip()
                if entry["id"] and text in PLACEHOLDER_TEXTS:
                    self.placeholders.append((entry["id"], entry["classes"], text))
                del self.stack[i:]
                break

    def handle_data(self, data):
        if self.stack:
            self.stack[-1]["text"] += data


def _walk_placeholders():
    walker = PlaceholderWalker()
    walker.feed(_read(DASHBOARD_HTML))
    return walker


def test_placeholders_use_the_single_empty_state_pattern():
    placeholders = _walk_placeholders().placeholders
    assert placeholders, "no empty-state placeholders found in dashboard.html"
    unclassed = sorted(node_id for node_id, classes, _ in placeholders if "empty-state" not in classes)
    assert not unclassed, f"placeholders not using the .empty-state pattern: {unclassed}"
    variants = sorted({text for _, _, text in placeholders})
    assert variants == [EMPTY_STATE_TEXT], f"placeholder text variants remain: {variants}"


def test_empty_state_is_styled_with_tokens():
    rule = _rule(_read(STYLE_CSS), ".empty-state")
    for token in ("var(--color-text-muted)", "var(--line-height-"):
        assert token in rule, f".empty-state must be styled via tokens ({token})"
    assert "min-height: var(--empty-state-min-height)" in rule, ".empty-state must reserve its own height"
    assert not re.search(r"#[0-9a-fA-F]{3,6}\b", rule), ".empty-state must not hardcode colors"


def test_placeholders_are_the_elements_js_updates():
    js = _read(DASHBOARD_JS)
    placeholders = _walk_placeholders().placeholders
    for node_id, _, _ in placeholders:
        assert f'getElementById("{node_id}")' in js, (
            f"{node_id} holds the placeholder text but dashboard.js does not write into it"
        )


def test_live_frame_reserves_space_before_the_first_frame():
    css = _read(STYLE_CSS)
    root = _rule(css, ":root")
    assert "--skeleton-min-height" in root, "missing --skeleton-min-height token"

    frame = _rule(css, ".live-frame")
    assert "aspect-ratio: 16 / 9" in frame, "the live frame must keep a 16/9 box before the first frame"
    assert "min-height: var(--skeleton-min-height)" in frame, "the live frame must not collapse before the first frame"
    assert "var(--color-surface-muted)" in frame, "the live frame must show a skeleton surface"

    live = _rule(css, "#live")
    assert "height: 100%" in live, "#live must fill the reserved frame instead of shrinking to its alt text"

    skeleton = _rule(css, ".live-frame:has(#live:not([src]))")
    assert "animation" in skeleton, "the live frame must show a skeleton until the first frame"
    assert "var(--motion-skeleton)" in skeleton, "the skeleton must use the motion token"

    hidden = _rule(css, ".live-frame:has(#live[hidden])")
    assert "display: none" in hidden, "the live frame must not show an empty box in privacy mode"

    html = _read(DASHBOARD_HTML)
    assert re.search(r'class="live-frame">\s*<img id="live"', html), "#live must sit inside the reserved frame"


def test_log_and_data_blocks_wrap_without_horizontal_scroll():
    css = _read(STYLE_CSS)
    pre = _rule(css, "pre")
    assert "white-space: pre-wrap" in pre, "log lines must wrap instead of scrolling horizontally"
    assert "overflow-wrap: anywhere" in pre, "long unbroken tokens must break inside log blocks"
    assert "max-height" in pre, "pre blocks must keep their max-height (JS rewrites textContent)"

    for selector in (".ai-kv", ".ai-hmeta", ".ai-hsummary"):
        rule = _rule(css, selector)
        assert "white-space: pre-wrap" in rule, f"{selector} must wrap its data lines"
        assert "word-break: break-word" in rule or "overflow-wrap: anywhere" in rule, (
            f"{selector} must break long data values"
        )

    mono = _rule(css, ".model-info-card")
    assert "font-family: var(--font-sans)" in mono, "model info prose must not be monospace"


def test_log_blocks_use_the_monospace_data_font():
    css = _read(STYLE_CSS)
    pre = _rule(css, "pre")
    assert "font-family: var(--font-mono)" in pre, "log blocks must use the monospace data token"
    assert "font-variant-numeric: tabular-nums" in pre, "log timestamps must use tabular figures"


def test_log_tail_stays_a_pre_block_that_js_rewrites():
    html = _read(DASHBOARD_HTML)
    assert re.search(r'<pre id="log_tail"', html), "#log_tail must stay a pre block (JS rewrites textContent)"
    assert 'document.getElementById("log_tail")' in _read(DASHBOARD_JS), "dashboard.js must keep writing #log_tail"


def test_numeric_data_uses_tabular_figures():
    css = _read(STYLE_CSS)
    for selector in (".ai-kv", ".ai-hmeta", ".ai-htime", ".status-v"):
        assert "font-variant-numeric: tabular-nums" in _rule(css, selector), (
            f"{selector} must use tabular figures so counters do not jitter"
        )
    assert "font-family: var(--font-mono)" in _rule(css, ".ai-kv"), ".ai-kv must stay monospace (data)"
    assert "font-family: var(--font-mono)" in _rule(css, ".ai-hmeta"), ".ai-hmeta must stay monospace (data)"
    assert "font-family: var(--font-mono)" in _rule(css, ".model-info-row strong"), (
        "model info values must stay monospace (data)"
    )


def test_scrollbars_use_tokens_and_stable_gutter():
    css = _read(STYLE_CSS)
    for selector in ("html", "pre", "#settingsBody", "#leftPane", "#rightPane"):
        rule = _rule(css, selector)
        assert "scrollbar-color: var(--color-border)" in rule, f"{selector} must style its scrollbar via tokens"

    settings_body = _rule(css, "#settingsBody")
    assert "scrollbar-gutter: stable" in settings_body, "#settingsBody must reserve its scrollbar gutter"
    assert not re.search(r"#[0-9a-fA-F]{3,6}\b", css.split("/* base */", 1)[1]), (
        "no hardcoded colors outside the :root token block"
    )


ANNOUNCED_LIVE_REGIONS = {"msg": "polite"}
NOISY_LIVE_REGIONS = {"status_readable", "log_tail", "aiHistoryList"}
PRESERVED_STATUS_ROLES = {"accessStatus", "accessMessage", "pairingCode"}


def _element_tag(html, node_id):
    match = re.search(rf'<[^>]*\bid="{node_id}"[^>]*>', html)
    assert match, f"element #{node_id} not found in dashboard.html"
    return match.group(0)


def test_focus_ring_is_global_not_only_summary():
    css = _read(STYLE_CSS)
    rule = _rule(css, ":focus-visible")
    assert "outline: 2px solid var(--color-focus)" in rule, (
        "a global :focus-visible ring must exist, not only on .access-panel summary"
    )
    assert "outline-offset: var(--space-1)" in rule, "the focus ring must be offset so it stays visible inside cards"
    for selector, body in _css_rules(css):
        if ":focus" in selector and "outline: none" in body:
            assert False, f"the focus ring must not be suppressed for focusable elements: {selector}"


def test_focus_ring_is_visible_against_surfaces():
    css = _read(STYLE_CSS)
    palettes = [
        _tokens(_rule(css, ":root")),
        _tokens(_media_rules(css, "prefers-color-scheme: dark")[":root"]),
    ]
    for tokens in palettes:
        focus = tokens.get("--color-focus")
        assert focus, "--color-focus token must be defined"
        for surface in ("--color-bg", "--color-surface", "--color-surface-muted"):
            ratio = _contrast(focus, tokens[surface])
            assert ratio >= 3.0, f"the focus ring is not visible on {surface} ({ratio:.2f})"


def test_msg_is_an_announced_live_region():
    tag = _element_tag(_read(DASHBOARD_HTML), "msg")
    assert 'role="status"' in tag, "#msg must expose Apply/Snapshot results to screen readers"
    assert f'aria-live="{ANNOUNCED_LIVE_REGIONS["msg"]}"' in tag, "#msg must announce its updates"
    assert 'aria-atomic="true"' in tag, "#msg must announce the whole message, not the diff"
    assert 'document.getElementById("msg")' in _read(DASHBOARD_JS), "dashboard.js must keep writing #msg"


def test_noisy_regions_are_not_announced():
    html = _read(DASHBOARD_HTML)
    for node_id in sorted(NOISY_LIVE_REGIONS):
        tag = _element_tag(html, node_id)
        assert 'aria-live="off"' in tag, f"#{node_id} is rewritten every 1-2 s and must not be announced"


def test_existing_status_roles_are_preserved():
    html = _read(DASHBOARD_HTML)
    for node_id in sorted(PRESERVED_STATUS_ROLES):
        assert 'role="status"' in _element_tag(html, node_id), f'#{node_id} must keep role="status"'


LABELABLE_TAGS = {"input", "select", "textarea", "button", "output", "meter", "progress"}

HEADING_LEVELS = {
    "Live View": 2,
    "Monitor Status": 2,
    "Logs": 3,
    "Settings": 2,
    "AI Status": 3,
}


class LabelWalker(HTMLParser):
    def __init__(self):
        super().__init__()
        self.labels = []
        self.fields = []
        self.label_stack = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "label":
            label = {"for": attrs.get("for"), "text": ""}
            self.labels.append(label)
            self.label_stack.append(label)
            return
        if tag in LABELABLE_TAGS:
            self.fields.append({
                "tag": tag,
                "id": attrs.get("id"),
                "aria_label": attrs.get("aria-label"),
                "label": self.label_stack[-1] if self.label_stack else None,
            })

    def handle_endtag(self, tag):
        if tag == "label" and self.label_stack:
            self.label_stack.pop()

    def handle_data(self, data):
        if self.label_stack:
            self.label_stack[-1]["text"] += data


def _walk_labels():
    walker = LabelWalker()
    walker.feed(_read(DASHBOARD_HTML))
    return walker


def test_every_label_points_to_an_existing_id():
    walker = _walk_labels()
    assert walker.labels, "no labels found in dashboard.html"
    without_for = sorted(label["text"].strip() for label in walker.labels if not label["for"])
    assert not without_for, f"labels without for: {without_for}"
    html = _read(DASHBOARD_HTML)
    broken = sorted({label["for"] for label in walker.labels if f'id="{label["for"]}"' not in html})
    assert not broken, f"label for points to a missing id: {broken}"


def test_labels_point_only_to_labelable_elements():
    walker = _walk_labels()
    labelable = {field["id"] for field in walker.fields if field["id"]}
    bad = sorted(label["for"] for label in walker.labels if label["for"] not in labelable)
    assert not bad, f"label for points to a non-labelable element: {bad}"


def test_field_label_span_shares_label_spacing():
    rule = _rule(_read(STYLE_CSS), ".field label, .field .field-label")
    assert "margin: 0 0 var(--space-2) 0" in rule, ".field-label must share label spacing"


FORM_FIELDS = {"input", "select", "textarea"}


def test_every_field_has_an_associated_label():
    walker = _walk_labels()
    by_for = {label["for"]: label for label in walker.labels}
    fields = [field for field in walker.fields if field["id"] and field["tag"] in FORM_FIELDS]
    assert fields, "no form fields found in dashboard.html"
    unnamed = sorted(
        field["id"] for field in fields
        if not field["aria_label"]
        and field["label"] is None
        and (by_for.get(field["id"]) is None or not by_for[field["id"]]["text"].strip())
    )
    assert not unnamed, f"fields with no associated label: {unnamed}"


def test_section_headings_are_heading_elements():
    html = _read(DASHBOARD_HTML)
    assert "<b>" not in html, "section headings must be h2/h3, not <b>"
    for text, level in HEADING_LEVELS.items():
        assert re.search(rf'<h{level}[^>]*>\s*{re.escape(text)}\s*</h{level}>', html), (
            f'"{text}" must be an h{level}'
        )
    assert "Model Info" in html, "the fixed literal Model Info must be preserved"


def test_heading_outline_does_not_skip_levels():
    levels = [int(level) for level in re.findall(r"<h([1-6])", _read(DASHBOARD_HTML))]
    assert levels, "no headings found in dashboard.html"
    assert levels[0] == 2, "card titles must open the heading outline at h2"
    for previous, current in zip(levels, levels[1:]):
        assert current <= previous + 1, f"heading outline skips a level: h{previous} then h{current}"


def test_admin_token_keeps_aria_label():
    tag = _element_tag(_read(DASHBOARD_HTML), "adminToken")
    assert 'aria-label="Administrator token"' in tag, "#adminToken must keep its aria-label"


def test_card_titles_are_styled_with_tokens():
    rule = _rule(_read(STYLE_CSS), ".card-title")
    assert "margin: 0" in rule, "card titles must not inherit default heading margins"
    assert "font-weight: var(--font-weight-strong)" in rule, ".card-title must use --font-weight-strong"
    assert "color: var(--color-text)" in rule, ".card-title must use --color-text"
    assert not re.search(r"#[0-9a-fA-F]{3,6}\b", rule), ".card-title must not hardcode colors"


def test_live_locked_stays_hidden_controlled():
    html = _read(DASHBOARD_HTML)
    assert re.search(r'id="liveLocked"[^>]*\bhidden\b', html), "#liveLocked must be hidden by default"
    for selector, body in _css_rules(_read(STYLE_CSS)):
        if "liveLocked" in selector:
            assert "display" not in body, f"#liveLocked must stay hidden-attribute controlled: {selector}"
    assert 'document.getElementById("liveLocked").hidden' in _read(ACCESS_JS), "access.js must keep controlling #liveLocked"


SPLITTER_ARIA = {
    "role": "separator",
    "aria-orientation": "vertical",
    "aria-valuenow": "360",
    "aria-valuemin": "280",
    "aria-valuemax": "780",
    "tabindex": "0",
}


def _attrs(html, node_id):
    return dict(re.findall(r'([\w-]+)="([^"]*)"', _element_tag(html, node_id)))


def test_splitter_is_an_accessible_separator():
    html = _read(DASHBOARD_HTML)
    tag = _element_tag(html, "splitter")
    for attr, value in SPLITTER_ARIA.items():
        assert f'{attr}="{value}"' in tag, f"#splitter must expose {attr}=\"{value}\""
    assert 'title="Drag to resize"' in tag, "#splitter must keep its title"


def test_splitter_aria_values_match_the_drag_clamp():
    attrs = _attrs(_read(DASHBOARD_HTML), "splitter")
    js_min = int(re.search(r"const minW = (\d+);", _read(DASHBOARD_JS)).group(1))
    css_default = int(re.search(r"--sidebarW:\s*(\d+)px", _read(STYLE_CSS)).group(1))
    assert int(attrs["aria-valuemin"]) == js_min, (
        f"aria-valuemin must match the JS drag clamp minimum ({js_min})"
    )
    assert int(attrs["aria-valuenow"]) == css_default, (
        f"aria-valuenow must match the default --sidebarW width ({css_default})"
    )
    assert int(attrs["aria-valuemax"]) > int(attrs["aria-valuenow"]), (
        "aria-valuemax must be above the default width (65% clamp of a typical layout)"
    )


def test_splitter_drag_handler_is_untouched():
    js = _read(DASHBOARD_JS)
    assert 'splitter.addEventListener("mousedown"' in js, "the drag must stay a mousedown handler"
    assert "const minW = 280;" in js and "0.65" in js, "the drag clamp must stay as documented in the plan"


Z_INDEX_TOKENS = ("--z-splitter", "--z-topbar", "--z-footer", "--z-overlay")


def test_z_index_is_token_only_and_ordered():
    css = _read(STYLE_CSS)
    root = _tokens(_rule(css, ":root"))
    for token in Z_INDEX_TOKENS:
        assert token in root, f"missing {token} token"

    for selector, body in _css_rules(css):
        for declaration in body.split(";"):
            if re.match(r"\s*z-index\s*:", declaration):
                assert "var(--z-" in declaration, f"z-index must use a token, found in {selector.strip()}"

    topbar = _rule(css, ".topbar")
    assert "position: relative" in topbar, ".topbar must establish its own stacking context"
    assert "z-index: var(--z-topbar)" in topbar, ".topbar must use --z-topbar"
    assert int(root["--z-topbar"]) > int(root["--z-splitter"]), "--z-topbar must stay above the splitter"


def test_floating_toggle_does_not_cover_topbar_actions():
    css = _read(STYLE_CSS)
    fab = _rule(css, ".floating-toggle")
    assert "position: fixed" in fab, "the floating toggle must stay fixed"
    assert "z-index: var(--z-overlay)" in fab, "the floating toggle must use --z-overlay"
    assert re.search(r"top:\s*calc\(var\(--topbar-height\)", fab), (
        "the floating toggle must start below --topbar-height so it cannot cover #shutdownBtn"
    )

    stacked = _media_rules(css, "max-width: 900px").get("#floatingExpandBtn")
    assert stacked and "top: auto" in stacked and "bottom: calc(var(--footer-height) + var(--space-5))" in stacked, (
        "the topbar wraps below 900px, so the floating toggle must move to the footer edge instead of the topbar"
    )


def test_reduced_motion_disables_transitions_and_animations():
    css = _read(STYLE_CSS)
    rules = _media_rules(css, "prefers-reduced-motion: reduce")
    universal = {sel: body for sel, body in rules.items() if sel.startswith("*")}
    assert universal, "reduced-motion block must apply to all elements"
    body = next(iter(universal.values()))
    assert "transition: none" in body, "transitions must be disabled under prefers-reduced-motion"
    assert "animation: none" in body, "animations must be disabled under prefers-reduced-motion"

    skeleton = [b for sel, b in rules.items() if "live-frame" in sel]
    assert skeleton and "animation: none" in skeleton[0], (
        "the skeleton pulse must be re-declared inside the block: `*` loses to the more specific animation rule"
    )


def test_floating_expand_button_stays_clickable():
    css = _read(STYLE_CSS)
    js = _read(DASHBOARD_JS)
    for selector, body in _css_rules(css):
        sel = selector.strip()
        if "floatingExpandBtn" not in sel and "floating-toggle" not in sel:
            continue
        if ".visitor-mode" in sel:
            continue
        assert "pointer-events: none" not in body, f"{sel} must not swallow clicks"
        assert "display: none" not in body, f"{sel} must not be hidden outside visitor mode"

    assert 'fb.className = "floating-toggle"' in js, "dashboard.js must keep creating the floating toggle"
    assert "fb.onclick" in js, "the floating toggle must keep its click handler"
