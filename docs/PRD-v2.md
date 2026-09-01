# YKS 2027 Koçluk & Deneme Takip — PRD v2 (Technical Hand-Off)

| | |
|---|---|
| **Version** | 2.0 — supersedes the Gemini-drafted v1 |
| **Date** | 2026-08-29 |
| **Status** | Ready to build |
| **Deliverable** | Sideloaded Android APK, tablet-first (Samsung Galaxy Tab S9 FE+) |
| **Stack decision** | Native Kotlin + Jetpack Compose (committed — see §11) |

This document is a corrected, research-backed rewrite of the v1 hand-off. Every YKS domain fact was verified against current sources (§16), the data model was redesigned around raw score storage, and three capabilities were added: **branş deneme tracking**, **study-time analytics**, and an optional **BYOK AI Koç** chat module. §14 is a full changelog against v1 with rationale.

---

## 1. Product Vision

An offline-first, single-user productivity app for a student preparing for the Turkish YKS 2027 exam (Sayısal track). Five capabilities in one tablet-optimized interface:

```
YKS 2027 Tracker
├── Module A  Deneme (mock exam) logging & trend analytics — TYT, AYT-SAY, branş
├── Module B  Configurable exam countdown (TYT + AYT)
├── Module C  Week-keyed kanban study planner with permanent history
├── Module D  Focus timer that survives process death and logs study sessions
└── Module E  AI Koç — optional, BYOK, data-aware chat coach (off by default)
```

**Privacy guarantee (product feature):** all tracker data lives on-device. No accounts, no telemetry, no sync. The `INTERNET` permission exists solely for the optional AI Koç endpoint the user explicitly configures; with no API key entered, the app performs zero network traffic.

## 2. Target Platform & Constraints

- **Primary:** Samsung Galaxy Tab S9 FE+ — 12.4", 2560×1600 (WQXGA, 16:10), landscape-first. In Compose window-size-class terms: **Expanded** width (~1280dp) in landscape, **Medium** (~800dp) in portrait.
- **Secondary:** Android phones (Compact width, portrait).
- **OS:** minSdk 29 (Android 10), targetSdk = latest stable.
- **Distribution:** signed APK, sideloaded (no Play Store requirements; R8/minify enabled; keep the signing keystore backed up).
- **Input:** touch (48dp minimum targets), S-Pen taps, hardware keyboard (full tab-order support in entry forms).
- **Locale/timezone:** Turkish-only UI (no i18n scaffolding). All date logic explicitly uses `Europe/Istanbul` (fixed UTC+3 — Turkey has no DST, but the explicit zone guards against device-timezone drift).

## 3. Verified YKS Domain Reference

These numbers are the domain contract for the whole app. Verified 2026-08-29 (§16).

### 3.1 Exam structure

| Exam | Section | Questions | Duration |
|---|---|---|---|
| **TYT** (120 soru) | Türkçe | 40 | 165 min total |
| | Sosyal Bilimler | 20 *(Tarih 5, Coğrafya 5, Felsefe 5, Din 5)* | |
| | Temel Matematik | 40 | |
| | Fen Bilimleri | 20 *(Fizik 7, Kimya 7, Biyoloji 6)* | |
| **AYT Sayısal** (80 soru) | AYT Matematik | 40 | 180 min total *(AYT booklet is 160q; SAY students answer Mat + Fen)* |
| | Fizik | 14 | |
| | Kimya | 13 | |
| | Biyoloji | 13 | |

Sub-splits of TYT Sosyal/Fen are reference-only — the app tracks the four TYT sections as ÖSYM reports them, and does not subdivide them.

### 3.2 Net calculation — **no clamping**

```
net = doğru − (yanlış / 4)          // 4 wrong cancel 1 correct; boş has no effect
```

- Nets have **quarter precision** (1Y = −0.25) and **can be negative**. ÖSYM convention; v1's `max(0, …)` clamp is removed everywhere.
- **Implementation rule:** compute in integer quarter-units end to end — `netQuarters = 4·doğru − yanlış` — and format as `netQuarters / 4` with two decimals (`.00/.25/.50/.75`). Never store or pipe nets as floats; convert to `Float` only at the final chart-rendering step. Exact, sortable, immune to float display bugs.
- Worked examples (these are unit-test cases, §13):
  - D=35, Y=4 → 34.00
  - D=30, Y=6 → 28.50
  - D=0, Y=3 → **−0.75**
  - D=2, Y=20 → **−3.00**
  - D=0, Y=4 → **−1.00**
- `boş = questionCount − doğru − yanlış` (derived, never entered).
- Total net = sum of section netQuarters; y-axes and KPIs must render negative values correctly.

### 3.3 Exam dates — **not yet announced**

ÖSYM has not published the 2027 calendar. Recent pattern (YKS 2026: TYT Sat 20 June 10:15, AYT Sun 21 June 10:15) implies:

- Default TYT target: **2027-06-19 10:15** Europe/Istanbul (Saturday)
- Default AYT target: **2027-06-20 10:15** Europe/Istanbul (Sunday)

Both are **user-editable settings** with an `exam_dates_confirmed` flag. Until the user confirms the official date, the countdown shows a persistent caption: *"ÖSYM takvimi henüz açıklanmadı — tahmini tarih"*. (v1 hardcoded a single 2027-06-19 10:00 target; the 10:00 was wrong and hardcoding asserts false precision for months.)

## 4. Module A — Deneme Logging & Analytics

### 4.1 Exam kinds

```kotlin
enum class ExamKind { TYT_FULL, AYT_SAY_FULL, BRANS_TYT, BRANS_AYT }

enum class Subject(val defaultQuestionCount: Int) {
    TYT_TURKCE(40), TYT_SOSYAL(20), TYT_MATEMATIK(40), TYT_FEN(20),
    AYT_MATEMATIK(40), AYT_FIZIK(14), AYT_KIMYA(13), AYT_BIYOLOJI(13)
}
```

- `TYT_FULL` = 4 fixed sections; `AYT_SAY_FULL` = 4 fixed sections (counts from §3.1).
- **`BRANS_TYT` / `BRANS_AYT` (new):** a single-subject mock test — exactly **one** section row, subject picked from the shared taxonomy, `question_count` editable (branş booklet sizes vary by publisher; defaults to the subject's official count). Closes v1's inconsistency where the planner had branş categories but scores couldn't be recorded.

### 4.2 Entry form (create **and** edit — same screen, nullable `examId`)

- Metadata: name (optional), publisher (optional), date (defaults to today, editable), optional actual duration + notes.
- Per section: **doğru** and **yanlış** numeric fields. Live derived display per section: **boş** and **net** (negative allowed, shown in error color). Running total net at the bottom.
- Validation at the input boundary: `0 ≤ D`, `0 ≤ Y`, `D + Y ≤ questionCount` — violating input is rejected inline, not on submit.
- **Ergonomics target: 8 numbers in under 60 seconds.** Numeric keyboard (`KeyboardType.Number`), IME-next focus auto-advance across fields in reading order, hardware-keyboard tab order matching, steppers as secondary affordance. On Expanded width the four section cards form a 2×2 grid; Compact stacks them.
- Duplicate guard: warn (not block) when an exam of the same kind + date already exists.
- Delete: confirmation-free with **undo snackbar** (soft window), from list or detail.

### 4.3 History (Denemeler screen)

- Expanded: `ListDetailPaneScaffold` — left pane filterable list (chips: Tümü / TYT / AYT / Branş), right pane detail: per-section table (doğru / yanlış / boş / net / accuracy %), edit + delete actions.
- Compact: list → detail navigation.
- Sort: newest first by `taken_at_day`.

### 4.4 Analytics (Analiz screen)

- **Total-net trend line** (Vico 2.x): segmented toggle *TYT Toplam Net* / *AYT Toplam Net*.
  - **Series rule:** only `TYT_FULL` / `AYT_SAY_FULL` feed the total-net lines. Branş results are on a different scale and are excluded — they feed the per-subject charts.
  - **X-axis is exam index, not date** (denemes cluster — three in a weekend, then a two-week gap; a time axis renders ugly voids). Axis labels render the date ("12 Eyl"); tapping a point shows a marker card: name, date, total net, section breakdown.
  - Y-axis: 0–120 (TYT) / 0–80 (AYT) by default, **extending below 0** when negative nets exist. Never floor at 0.
  - Fewer than 2 points → friendly empty state, not a degenerate chart.
- **Per-subject charts (M2):** subject filter → net trend (full + branş results together), accuracy %, and a **boş-vs-yanlış** breakdown (10 blanks and 10 wrongs demand opposite study strategies — this split is the actionable insight).
- **KPI cards:** Son TYT Neti · En Yüksek TYT · Son AYT Neti · En Yüksek AYT · Toplam Deneme · Δ vs previous exam · last-5 moving average (M2).

## 5. Module B — Exam Countdown

- **Two countdowns** (TYT + AYT), dashboard hero component + settings entry — not a standalone nav destination (a countdown has one number and zero interactions).
- Targets from settings (§3.3); computed against `ZoneId("Europe/Istanbul")` regardless of device timezone.
- Implementation is deliberately dumb: while visible, a coroutine ticker (`delay(1000)`) recomputes `Duration` to target → days/hours/minutes/seconds badges with leading zeros. No service, no alarm, no persistence beyond the settings.
- States: normal countdown · exam day (*"Sınav günü!"*) · past (clamps to zeros, shows a completed state — no negative offsets).
- "Tahmini tarih" caption until `exam_dates_confirmed = true`.

## 6. Module C — Weekly Kanban Planner

### 6.1 Week keying — archiving is implicit, not an action

A week is identified by the **epochDay of its Monday** (computed in Istanbul). Tasks belong to a week row. The "current week" is whatever key `today` resolves to; past weeks are automatically history because their key is in the past. **No data is ever moved or destroyed to "archive" it** — v1's destructive weekly reset ritual is gone.

**Rollover flow** (runs on every planner open):
1. Compute current Monday key (Istanbul zone — Sunday 23:59 vs Monday 00:01 must land in different weeks; unit-test target §13).
2. `INSERT OR IGNORE` the `plan_weeks` row.
3. If the row was just created and the previous week has tasks → one-tap prompt: *"Geçen haftanın planını kopyala?"* — copies tasks with completion cleared. This replaces the Monday-morning "untick everything" ritual that motivated v1's design.

### 6.2 Board layout

- 7 columns Pazartesi → Pazar. Expanded width: all 7 visible side-by-side (7 × ~165dp fits 1280dp beside a nav rail), per-column vertical scroll, today's column visually highlighted. Compact: `HorizontalPager`, one day per page, opens on today.
- Task card: category badge (color token), topic text, target question count, checkbox, overflow menu (edit / move to another day / delete-with-undo). Cards are orderable within a column (`order_index`); drag between days on Expanded.
- Completed cards: strikethrough + 65% opacity (kept from v1).

### 6.3 Categories (fixed taxonomy — no custom-category editor)

| Key | UI Label | Accent (light) | Container (light) |
|---|---|---|---|
| `GEOMETRI` | Geometri | `#0284c7` | `#e0f2fe` |
| `AYT_MAT` | AYT Matematik | `#ea580c` | `#ffedd5` |
| `TYT_MAT` | TYT Matematik | `#d97706` | `#fef3c7` |
| `TYT_DENEME` | TYT Deneme | `#7c3aed` | `#ede9fe` |
| `AYT_FIZIK` | AYT Fizik | `#2563eb` | `#dbeafe` |
| `AYT_KIMYA` | AYT Kimya | `#e11d48` | `#ffe4e6` |
| `AYT_BIYOLOJI` | AYT Biyoloji | `#16a34a` | `#dcfce7` |
| `TYT_FEN` | TYT Fen | `#0d9488` | `#ccfbf1` |
| `AYT_DENEME` | AYT Deneme | `#c026d3` | `#fae8ff` |
| `TYT_TURKCE` | TYT Türkçe | `#9333ea` | `#f3e8ff` |

Changes from v1: `TYT_BRANS`/`AYT_BRANS` renamed to `TYT_DENEME`/`AYT_DENEME` so full mocks are schedulable too. Colors are **theme tokens in code** (dark variant: same accent, container = accent at ~16% alpha over the dark surface) — never stored in the DB.

### 6.4 Weekly metrics — honest numbers

- **Completion %** = completed tasks / total tasks (current week).
- **Hedeflenen Soru** = Σ `target_questions` over the week's tasks.
- **Çözülen Soru** = Σ `solved_questions` — the **actual** count, captured at check-off: tapping the checkbox opens a number pad **pre-filled with the target** (one tap confirms, so honesty costs zero friction). v1 summed the *targets* of ticked tasks and labeled it "çözülen" — fabricated data, removed.
- Weekly study minutes per category (from Module D sessions) shown alongside.

### 6.5 Actions

- **Tikleri Sıfırla** — clears completion flags **for the current week only** (kept as a convenience; now history-safe).
- **Tümünü Temizle** — deletes the current week's tasks only; confirm dialog + undo snackbar.
- **Geçmiş Haftalar** — read-only screen listing past weeks (completion %, question totals, study minutes) → tap into a read-only board view. This is the coaching-review surface.

## 7. Module D — Focus Timer

### 7.1 Product behavior

- Custom duration 1–300 min; presets 25 / 45 / 60 / 90.
- Optional **category tag** (same taxonomy as planner) and optional link to a specific task.
- Monospace `MM:SS` display (`HH:MM:SS` at ≥60 min). START locks the duration input; PAUSE freezes; RESET restores.
- Completion: full-screen in-app alert when foregrounded, high-importance notification (sound + vibration) otherwise.
- **Every session ≥ 60s writes a `focus_sessions` row** (finished or abandoned, flagged which) — the timer is a data source, not a dead end. Sub-minute sessions are discarded as noise.

### 7.2 Reliability spec (state machine — the hard requirement)

Source of truth: `timer_state` DataStore, written **only on state transitions**, never per tick.

| State | Persisted | Notes |
|---|---|---|
| `IDLE` | — | |
| `RUNNING` | absolute `end_at` (wall-clock epoch ms) | wall clock survives reboot; `elapsedRealtime` does not |
| `PAUSED` | `remaining_ms` | an end-timestamp cannot represent a paused timer |

Mechanism stack (each layer independent):
1. **UI tick** — ViewModel flow (~4 Hz) computing `end_at − now()`. Display only.
2. **Foreground service while RUNNING** — ongoing silent notification via `setUsesChronometer(true) + setChronometerCountDown(true) + setWhen(endAt)` (OS renders the live countdown; zero notification updates). API 34+: `foregroundServiceType="specialUse"`.
3. **Exact alarm at `end_at` — the completion guarantee.** Fires the completion notification, writes the session row, clears state — even if the process was killed or the device dozed.
4. **`BOOT_COMPLETED` receiver** — alarms are wiped on reboot: if RUNNING with `end_at` in the future, reschedule the alarm; if already past, immediately post *"Süre doldu (cihaz kapalıyken)"* and finalize the session.

**Permissions** (v1 ignored these — without them the completion alert silently doesn't exist on the target device):
- `POST_NOTIFICATIONS` (runtime, API 33+) — request contextually on first timer start; on denial the timer still works foregrounded, with a visible warning that background alerts are off.
- `USE_EXACT_ALARM` — auto-granted, intended for timer/alarm apps; sidesteps the API 34+ trap where `SCHEDULE_EXACT_ALARM` is denied by default. (Play-policy restrictions are irrelevant for a sideloaded APK.) Keep a `setWindow(±1 min)` fallback for defense in depth.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`.

## 8. Module E — AI Koç (optional, BYOK, off by default)

**Positioning:** a *data-aware coach*, not a generic chatbot. Generic chat loses to the free ChatGPT/Gemini apps the student already has; the reason to build AI into *this* app is that it can read the local stats and answer *"son 5 denemede en zayıf dersim hangisi?"* or draft next week's plan from real numbers.

- **Fully optional:** the AI Koç destination is hidden until an API key is entered in Ayarlar. Without a key the app performs no network traffic at all.
- **Providers — two client implementations behind one `AiProvider` interface:**
  1. `AnthropicProvider` — official **Anthropic Java SDK** (Kotlin uses the Java SDK; OkHttp-based, Android-compatible). Streaming responses; adaptive thinking left at default. Never route Claude through an OpenAI-compat shim.
  2. `OpenAiCompatProvider` — one configurable chat-completions client (base URL + key + model string) covering **OpenAI**, **Gemini** (OpenAI-compat endpoint), **xAI/Grok**, and **Ollama** (local LAN server — the only option keeping traffic off the internet entirely; expect markedly weaker Turkish math/science tutoring from small local models).
- **Model picker** with provider presets. Anthropic reference pricing (per million tokens, for the family's cost decision):

  | Model | ID | Input | Output |
  |---|---|---|---|
  | Claude Opus 5 *(default)* | `claude-opus-5` | $5 | $25 |
  | Claude Sonnet 5 | `claude-sonnet-5` | $2 | $10 |
  | Claude Haiku 4.5 | `claude-haiku-4-5` | $1 | $5 |

  Light daily tutoring chat is cheap at any tier; set a **spend cap in the provider dashboard** regardless.
- **Data-aware context:** a coach system prompt plus a compact stats summary (recent nets per subject, plan completion %, weekly study minutes) injected into the conversation. Controlled by an `ai_share_stats` toggle — off means pure chat, nothing about the student is sent.
- **Storage & security:** API key in `EncryptedSharedPreferences`, **excluded from JSON backups** (re-enter after import). Chat threads/messages stored locally in Room, included in backups.
- **Product cautions (state in-app):** LLMs can err on hard math — this is a tutor, not an answer key; responses stream in; requests fail gracefully offline with a clear "AI Koç çevrimdışı" state.
- **High-value follow-up (flagged, not MVP):** photo-of-a-question multimodal input (camera → image block) for "bu soruyu açıkla".

## 9. Data Architecture

### 9.1 Principles

- **Store only non-recomputable inputs.** Raw `correct`/`wrong`/`question_count` are stored; boş, nets, totals, accuracy, KPIs are **derived at read time** (a stored net can silently disagree with its inputs — exactly v1's bug class; dataset ≈ 150 exams/year, recomputation cost is nil). One deliberate exception: `focus_sessions.active_ms` (pause history isn't otherwise reconstructable).
- Room, **schema version 1** (v1 never shipped — no migration path exists). Set `exportSchema = true` from day one and commit `app/schemas/` so every future change is a testable migration.
- Enums persisted as `TEXT` via TypeConverters. No lookup tables. Colors never in the DB.
- Room has no CHECK constraints — input invariants (`D+Y ≤ max`, branş = exactly one section) are enforced in the entry use case.

### 9.2 Tables

```sql
CREATE TABLE exams (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    exam_kind     TEXT    NOT NULL,              -- ExamKind
    taken_at_day  INTEGER NOT NULL,              -- LocalDate.toEpochDay(), Istanbul
    name          TEXT,
    publisher     TEXT,
    duration_min  INTEGER,
    notes         TEXT,
    created_at    INTEGER NOT NULL,              -- epoch ms
    updated_at    INTEGER NOT NULL
);
CREATE INDEX idx_exams_kind_taken ON exams(exam_kind, taken_at_day);

CREATE TABLE exam_sections (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    exam_id        INTEGER NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    subject        TEXT    NOT NULL,             -- Subject
    question_count INTEGER NOT NULL,             -- stored: branş sizes vary by publisher
    correct_count  INTEGER NOT NULL,
    wrong_count    INTEGER NOT NULL,
    order_index    INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_sections_exam_subject ON exam_sections(exam_id, subject);
CREATE INDEX idx_sections_subject ON exam_sections(subject);

CREATE TABLE plan_weeks (
    week_start_day INTEGER PRIMARY KEY,          -- epochDay of Monday (Istanbul)
    note           TEXT,
    created_at     INTEGER NOT NULL
);

CREATE TABLE plan_tasks (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start_day   INTEGER NOT NULL REFERENCES plan_weeks(week_start_day) ON DELETE CASCADE,
    day_of_week      INTEGER NOT NULL,           -- ISO: 1=Pazartesi .. 7=Pazar
    category         TEXT    NOT NULL,           -- PlannerCategory
    topic            TEXT    NOT NULL,
    target_questions INTEGER,                    -- nullable: "konu tekrarı" tasks have no count
    solved_questions INTEGER,                    -- nullable: actual count captured at check-off
    is_done          INTEGER NOT NULL DEFAULT 0,
    completed_at     INTEGER,
    order_index      INTEGER NOT NULL,
    created_at       INTEGER NOT NULL
);
CREATE INDEX idx_tasks_week_day ON plan_tasks(week_start_day, day_of_week, order_index);

CREATE TABLE focus_sessions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at  INTEGER NOT NULL,
    ended_at    INTEGER NOT NULL,
    active_ms   INTEGER NOT NULL,                -- net of pauses (stored exception, see §9.1)
    planned_min INTEGER NOT NULL,
    completed   INTEGER NOT NULL,                -- 1 = ran to zero, 0 = abandoned
    category    TEXT,                            -- nullable PlannerCategory
    task_id     INTEGER REFERENCES plan_tasks(id) ON DELETE SET NULL,
    note        TEXT
);
CREATE INDEX idx_focus_started ON focus_sessions(started_at);

CREATE TABLE chat_threads (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    title      TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE chat_messages (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    thread_id  INTEGER NOT NULL REFERENCES chat_threads(id) ON DELETE CASCADE,
    role       TEXT    NOT NULL,                 -- user | assistant
    content    TEXT    NOT NULL,
    model      TEXT,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_chat_thread ON chat_messages(thread_id, created_at);
```

Chart feed query (illustrative):

```sql
SELECT e.id, e.taken_at_day, e.name,
       SUM(s.correct_count * 4 - s.wrong_count) AS total_net_quarters
FROM exams e JOIN exam_sections s ON s.exam_id = e.id
WHERE e.exam_kind = :kind
GROUP BY e.id
ORDER BY e.taken_at_day, e.id;
```

### 9.3 Settings — DataStore Preferences (not Room)

Singleton key-values with reactive Flow reads; two files so frequent timer writes don't wake settings collectors:

- **`settings`**: `tyt_exam_at`, `ayt_exam_at` (epoch ms; defaults §3.3), `exam_dates_confirmed`, `theme_mode` (SYSTEM/LIGHT/DARK), `backup_dir_uri`, `last_backup_at`, `ai_provider`, `ai_base_url`, `ai_model`, `ai_share_stats`.
- **`timer_state`**: `state`, `end_at_epoch_ms` (RUNNING), `remaining_ms` (PAUSED), `planned_min`, `category`, `linked_task_id`, `started_at`.
- **AI API key**: `EncryptedSharedPreferences` — separate from both, excluded from backup.

### 9.4 Backup / export — the data-loss and device-transfer answer

- **Format:** one JSON document via kotlinx.serialization:

```json
{
  "format": "yks-backup",
  "schema_version": 1,
  "exported_at": "2026-08-29T21:04:00+03:00",
  "app_version": "1.0.0",
  "settings": { "...": "..." },
  "exams": [ { "...": "...", "sections": [] } ],
  "plan_weeks": [ { "...": "...", "tasks": [] } ],
  "focus_sessions": [],
  "chat_threads": [ { "...": "...", "messages": [] } ]
}
```

- **Manual export/import** via SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) — fully offline, works to Downloads/SD/USB. JSON over a raw `.db` copy: includes settings, human-inspectable, version-checkable, WAL-safe.
- **Auto-backup:** user picks a folder once (`ACTION_OPEN_DOCUMENT_TREE`, persistable permission); on app open with `last_backup_at` > 7 days, write `yks_backup_YYYY-MM-DD.json` in the background; keep last 8. Dashboard nudge: *"Son yedek X gün önce"*.
- **Import = full replace only**, behind a confirm dialog, with an automatic pre-import safety snapshot. No merge — cross-device is "export on tablet, import on phone"; merge semantics are a project-killing rabbit hole for a one-user app.
- Do **not** rely on Android Auto Backup (cloud transport, 25MB cap, frequently disabled on sideloaded devices). Explicit files are the guarantee.

## 10. UI/UX Specification

### 10.1 Information architecture

Top-level destinations via `NavigationSuiteScaffold` (rail on Expanded, bottom bar on Compact):

**Ana Sayfa · Denemeler · Planlayıcı · Sayaç · Ayarlar** (+ **AI Koç**, visible only once configured)

**Ana Sayfa (new in v2 — the daily loop):** dual countdown hero (TYT + AYT, "tahmini" caption until confirmed), bugünün görevleri (today's planner column, checkable in place), son deneme KPI strip, backup nudge, quick actions (Deneme Ekle, Sayaç Başlat). v1 had four silos and no answer to "open app → what now?"; this screen is that answer.

### 10.2 Per-screen adaptive behavior

| Screen | Expanded (tablet landscape — primary) | Compact (phone) / Medium (tablet portrait) |
|---|---|---|
| Ana Sayfa | Hero + 2-column card grid | Stacked |
| Denemeler | ListDetailPaneScaffold (list ⇄ breakdown) | List → detail |
| Deneme Girişi | 2×2 section-card grid, side summary | Vertical cards |
| Analiz | Chart ~70% width + KPI column | KPI row scrolls horizontally, chart below |
| Planlayıcı | All 7 day columns + metrics header | One day per page (pager), today first |
| Geçmiş Haftalar | Week-card grid | List |
| Sayaç | Big dial + presets + recent sessions panel | Stacked |
| AI Koç | Chat + optional stats side panel | Chat |
| Ayarlar | Two-pane settings | Single list |

### 10.3 Visual system

- **Typography:** Inter (or Plus Jakarta Sans); headers 800, body 500/600; timer display monospace.
- **Light theme (kept from v1):** brand gradient `#312e81 → #4338ca → #6366f1`; canvas `#f1f5f9`; cards `#ffffff`; outlines `#cbd5e1`.
- **Dark theme (new — night studying is the norm):** Material 3 dark scheme; canvas ≈ `#0f172a`, cards ≈ `#1e293b`, outlines ≈ `#334155`; same brand hues at dark-appropriate tones. Category accents unchanged; category containers = accent at ~16% alpha over the surface. Theme setting: SYSTEM / LIGHT / DARK.
- **Ergonomics:** 48dp minimum touch targets; S-Pen hover-safe; all destructive actions confirm or offer undo.

## 11. Implementation Stack (committed)

| Concern | Choice |
|---|---|
| Language / UI | Kotlin 2.x + Jetpack Compose (Material 3) |
| Adaptive layout | `androidx.compose.material3.adaptive` — WindowSizeClass, NavigationSuiteScaffold, ListDetailPaneScaffold |
| Architecture | MVVM, single Gradle module, unidirectional data flow, Hilt DI, navigation-compose typed routes |
| Persistence | Room + KSP (`exportSchema = true`); DataStore Preferences for settings/timer state; EncryptedSharedPreferences for the AI key |
| Charts | **Vico 2.x** (Compose-native, actively maintained). MPAndroidChart is legacy/unmaintained — dropped. |
| Serialization | kotlinx.serialization (backup format, AI payloads) |
| AI (Module E) | Official Anthropic Java SDK + one OpenAI-compatible OkHttp/SSE client |
| Time | java.time with injectable `IstanbulClock` (`ZoneId("Europe/Istanbul")`) — makes rollover/countdown unit-testable |
| Testing | JUnit + Robolectric where needed; Room MigrationTestHelper from schema v2 onward |

Package sketch:

```
com.yks2027.tracker
├── core/ database · datastore · model (enums, NetCalculator) · time · backup · ai · ui (theme, tokens)
└── feature/ dashboard · exams (list|entry|analytics) · planner · timer · aikoc · settings
```

*(Alternatives considered and dropped: Flutter — viable, but native wins for this Android-only, tablet-first scope; WebView/Capacitor wrap — weakest timer-reliability and offline-data story.)*

## 12. Milestones (each independently shippable)

**M1 — MVP "Kayıt + Plan + Sayaç"**
Theme (light **and** dark) + adaptive nav shell · TYT/AYT exam create/edit/delete with raw counts, live boş/net preview, negative nets · exam list + detail · Analiz v0 (total-net line, TYT/AYT toggle, KPI cards) · Ana Sayfa with dual configurable countdown · week-keyed planner with copy-last-week, scoped Sıfırla/Temizle, undo · full timer stack (state machine + FGS + exact alarm + boot receiver + session rows) · manual JSON export/import.
**Ships the complete schema** (incl. branş kinds, `solved_questions`, chat tables) even where UI lags — future migrations stay additive.

**M2 — "Analiz + Arşiv"**
Branş deneme entry UI · per-subject trend / accuracy / boş-vs-yanlış charts · Geçmiş Haftalar review · solved-count capture at check-off · weekly study-time-per-category chart · auto-backup + nudge · moving-average KPI, bugünün görevleri polish.

**M3 — "AI Koç + Koçluk"**
AI Koç module (BYOK provider setup, streaming chat, stats-context injection) — self-contained; can be pulled earlier if wanted · weekly combined coaching report · streaks · eksik-konu quick tags (additive schema v2) · Glance home-screen countdown widget · timer auto-break cycles.

## 13. Acceptance Criteria & Test Plan

**Unit-test targets (these three carry most of the correctness risk):**
1. `NetCalculator` — all §3.2 worked examples, including negative and fractional cases; quarter-unit integer math; formatting.
2. `WeekRolloverUseCase` — Sunday 23:59 vs Monday 00:01 Istanbul boundary; idempotent re-open; copy-last-week clears completion.
3. **Timer state machine** — transition table; RUNNING→(process kill)→relaunch resumes from `end_at`; PAUSED survives relaunch with `remaining_ms`.

**Device acceptance:**
- Kill the process mid-timer → completion notification still fires at `end_at`. Reboot mid-timer → alarm rescheduled (or immediately finalized if past).
- Deny `POST_NOTIFICATIONS` → timer still completes in-app with visible degradation warning.
- Enter D+Y > max → rejected at input; negative net renders correctly in preview, list, chart.
- Log full TYT, full AYT, branş; edit each; delete with undo.
- Cross Sunday→Monday → new week appears, previous week intact in Geçmiş Haftalar.
- Export → wipe app data → import → identical state (minus AI key, by design).
- All screens usable at Expanded and Compact; both themes; no chart flooring at 0.
- With no AI key configured: zero network requests (verifiable — sole `INTERNET` usage is the AI client).

## 14. Changes from v1 (changelog with rationale)

| # | v1 said | v2 says | Why |
|---|---|---|---|
| 1 | Countdown hardcoded 2027-06-19 **10:00** | Two editable targets, default 06-19/06-20 **10:15**, "tahmini" banner | ÖSYM hasn't announced 2027; exams start 10:15; there are two exam days |
| 2 | `Net = max(0, D − Y/4)` | No clamp; negatives flow end-to-end; integer quarter-units | ÖSYM convention allows negative nets; floats invite display bugs |
| 3 | Store 4 net columns (`sub_1..4_net`), meaning flips by exam type | Store raw D/Y per section in a child table; derive everything | Single source of truth; enables accuracy/boş analytics; kills positional fragility |
| 4 | Create-only exam logging | Create/edit/delete + undo + duplicate warning | Real data has typos |
| 5 | Planner has "Branş Deneme" categories but no way to log branş scores | `BRANS_TYT`/`BRANS_AYT` exam kinds (one section, variable count) | Closes the module inconsistency |
| 6 | "Tikleri Sıfırla" wipes the week; no history | Week-keyed rows; archiving implicit; copy-last-week prompt; Geçmiş Haftalar | Coaching review needs history; destructive resets lose it |
| 7 | "Çözülen Soru" = Σ targets of ticked tasks | Actual solved count captured at check-off | The v1 metric reported plan compliance as performance |
| 8 | Timer is a dead end; lifecycle hand-waved | Session logging + full state machine (FGS + exact alarm + boot receiver + permissions) | Study-time analytics; on API 33+/34+ the v1 design silently fails to alert |
| 9 | No backup story | SAF JSON export/import + auto-backup + safety snapshot | Offline app = one dropped tablet from total loss; also the phone-transfer answer |
| 10 | Light palette only | Full dark theme + theme setting | Night studying is the norm |
| 11 | Two tabs, countdown as a "module screen" | Ana Sayfa dashboard + 5/6 destinations, adaptive nav | The daily "what now?" loop was unserved |
| 12 | Chart library "Vico or MPAndroidChart" | Vico 2.x only | MPAndroidChart is unmaintained |
| 13 | Flutter kept as a parallel option | Kotlin + Compose committed | Android-only, tablet-first scope; adaptive APIs; one codebase to maintain |
| 14 | — | AI Koç module (optional, BYOK, multi-provider, data-aware) | Requested addition; differentiator is reading local stats, not generic chat |
| 15 | "100% offline" | "Offline-first; network only for the optional AI endpoint you configure" | Honest restatement after adding Module E |

**Kept from v1 verbatim (it got these right):** exam structures/counts/durations · net formula + quarter precision (minus the clamp) · the 10-category color taxonomy (two labels renamed) · offline-first, no accounts, no telemetry, sideloaded APK · landscape-first 7-column week · Turkish-only UI · 1–300 min timer range · single-line total-net chart concept + tap tooltips · 48dp touch targets.

## 15. Out of Scope (deliberate)

Hedef-net goals · **tahmini puan / sıralama estimator** (yearly ÖSYM coefficients + OBP make offline puan math false precision — nets are the honest metric; also the top scope-creep trap) · topic-level (konu) wrong-answer analysis · PDF report export · flashcards / question bank / social features · any form of sync or accounts. Revisit only after M2 ships and gets used.

## 16. Sources (domain facts verified 2026-08-29)

- YKS 2027 not yet announced; June 19–20 expected pattern — manisamansetgazetesi.com; blog.sorumatik.co
- YKS 2026 dates & times (TYT Sat 20 June 10:15, AYT Sun 21 June 10:15; 165/180 min) — cnnturk.com; hurriyet.com.tr; fokusplus.com
- TYT/AYT question distribution — xyzakademi.com.tr; unirehberi.com
- Net formula & negative nets — rehberpanda.com; yksnethesapla.com
- Vico (maintained, Compose-native) vs MPAndroidChart (legacy) — github.com/patrykandpatrick/vico
- Compose Material 3 adaptive APIs stable — android-developers.googleblog.com; developer.android.com
- Competitor feature landscape — denemetakip.com; Google Play (YKS Deneme Takip, Testy)

## 17. Uygulama sürüm notları (PRD-sonrası eklemeler)

Bu bölüm, PRD v2 yazıldıktan sonra gönderilen sürümlerin spec'e göre farklarını izler.

- **v1.0.0 (2026-08-30):** M1+M2+M3 — PRD'nin tamamı. İmzalı ilk release.
- **v1.1.0 (2026-08-30, "M4 Konu Takibi"):** §15'te "kapsam dışı" denen konu-analizi, ailenin talebiyle kapsama alındı: ~140 konuluk gömülü katalog, deneme başına yapılandırılmış konu işaretleri (Y/B + hata türü Bilgi/İşlem/Dikkat/Süre), Zayıf Konular sıralaması, Konular çizelgesi, AI Koç bağlamına zayıf konular. Şema v3.
- **v1.2.0 (2026-08-30, "Profiller + İçe/Dışa Aktar + Notlar + Görsel Paso"):**
  - **AI sağlayıcı profilleri** (şema v4 `ai_profiles`): tek-slot yapı kaldırıldı; her profil ad + protokol (Anthropic / OpenAI-uyumlu) + taban URL + model + kendi şifreli anahtarını taşır. Şablonlar: Claude, OpenAI, Gemini, xAI, OpenRouter, OpenCode Zen, Ollama-LAN, Özel. "Bağlantıyı Sına" ve canlı "Modelleri Getir" (models uçları). Eski tek-slot yapı ve anahtarı ilk açılışta otomatik profile taşınır.
  - **Yeniden adlandırma:** görünen ad "YKS Takip"; yıl etiketi TYT tarihinden türetilir (applicationId değişmedi — güncellemeler kurulmaya devam eder).
  - **Kronometre** (Sayaç'ta ileri sayım modu; oturumlar plannedMin=0 ile kaydedilir).
  - **CSV dışa/içe aktarma** (belgelenmiş `;` ayraçlı format; içe aktarma önizleme + mükerrer uyarısıyla EK yapar) ve **AI ile karne okuma** (görsel/PDF/metin → onay için önceden doldurulmuş form; yalnız Anthropic profilleri; hiçbir şey otomatik kaydedilmez).
  - **Notlar** (markdown; AI Koç yanıtları "Nota kaydet" ile düşer) — şema v4 `notes`, yedek v4.
  - **Paylaşım:** Geçmiş Haftalar'dan haftalık rapor metni paylaşımı (yalnız özet veriler); uzaktan takip için sıfır-kod yol: haftalık otomatik yedeği Drive-eşitlenen paylaşımlı klasöre yönlendirmek.
  - **Görsel paso:** KPI sparkline'ları, plan/ konu ilerleme halkaları, Sayaç kadranı, Zayıf Konular hata-türü şerit çubukları, Planlayıcı kategori şeridi, 8 haftalık aktivite ısı şeridi, Geçmiş Haftalar çift eksenli trend grafiği.
  - Firebase/hesap tabanlı canlı takip bilinçli olarak YAPILMADI (şeffaflık ilkesi).
- **v1.3.0 (2026-08-30, "AI Koç — Oturum Paneli + Klasörler + Arama"):** Sohbetler açılır menüden çıkıp birinci sınıf oldu: geniş ekranda kalıcı sol panel, dar ekranda alt sayfa; sabitli-önce + son-mesaj-etkinliği sıralaması (sorgu, şema değil); satır başına başlık/göreli zaman/mesaj sayısı/özet; yeniden adlandır (elle ad otomatik başlıkla ezilmez), sabitle, klasöre taşı, sil+geri al. Şema v5: `chat_folders` + `chat_threads.pinned/folder_id` (FK SET NULL — klasör silmek sohbeti asla silmez); yedek v5 klasörleri sabit id'lerle taşır (v1–v4 okunur). Arama başlık+içerikte (LIKE; bu ölçekte FTS bilinçli olarak yok), sonuçlar klasörler-arası ve klasör rozetli. Mesaj-başı token kolonları bu sürümde YOK; satırda token toplamı bu yüzden gösterilmiyor (kolonlar gelirse eklenecek).
