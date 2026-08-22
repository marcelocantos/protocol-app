# Entropy audit — protocol-app

- **Date:** 2026-08-22
- **Mode:** full (entropy + hygiene)
- **Repository:** `/Users/marcelo/work/github.com/marcelocantos/protocol-app`
- **Branch:** `master` (tracks `origin/master`)
- **HEAD:** `c809d5d0775a54161112b8591cc44a6a152f3260`
  (`c809d5d push/remove targets md bullseye yaml is the source of t (#2)`)
- **Initial dirty state:** clean (`git status --porcelain=v1 -b` showed only
  `## master...origin/master`)
- **Baseline:** first entropy-audit under the 2026-08-22 contract. Prior
  snapshot: `docs/audit-2026-04-08.md` at `b63ff31` (pre-open-source). That
  checklist is historical; several High/Critical items there were later
  addressed in `1d3df33` / `89e52c2` (see `docs/audit-log.md`).

## Executive summary

Protocol is a single-module Kotlin/Compose Android app with one SQLite
database, three activity-scoped ViewModels, and four WorkManager reminder
channels. Declared architecture in `CLAUDE.md` matches the package layout.

The headline mechanism is **unshared in-memory observers over a shared
SQLite file**. `ProtocolDatabase` is the durable source of truth, but
schedule Flows, week-plan Flows, and completion reads are constructed
per ViewModel (and again in `PlanningReminderWorker`). Writes on one
screen do not invalidate the others. Independently, **week-plan day types
never reach Today or Week**, so the Plan screen can record a per-week
override that the checklist and weekly summary never honor. There is
**no test source set**; CI is `assembleDebug` + `lintDebug` only.

Highest-consequence findings:

- **ENT-001 (P1):** no unit, instrumented, or journey tests; `testDebugUnitTest`
  is `NO-SOURCE`.
- **ENT-002 (P1):** `schedule` and `week_plans.day_type` are competing
  authorities for “what kind of day is this date?”
- **ENT-003 (P1):** four `ScheduleRepository` instances, each with its own
  `MutableSharedFlow`; Week gym/summary does not observe completions.
- **ENT-004 (P1):** Today’s selected date is not refreshed on resume or
  midnight; `refreshDate()` is dead. Matches open 🎯T1.

Unverified residue: whether Plan day-type is *intended* to override Today
(owner intent); live notification timing on a device; restore-from-backup
across schema versions; inexact WorkManager delays under Doze.

Hygiene posture is **not declared** (`hygiene.yaml` absent).

## Scope and exclusions

**In scope:** all tracked source, Gradle/CI, `bullseye.yaml`, `CLAUDE.md`,
`README.md`, `docs/audit-*.md`, Android manifest/resources.

**Excluded (generated / local / vendored):**

- `app/build/`, `build/`, `.gradle/` — compiler, lint, and APK outputs
- `gradle/wrapper/gradle-wrapper.jar` — binary wrapper, not reviewed as
  application code
- `local.properties` — gitignored, not tracked
- `app/src/main/res/mipmap-xxxhdpi/` — binary launcher assets

**Languages detected:** Kotlin (25 `.kt` files), Gradle Kotlin DSL, XML
resources, SQLite DDL embedded in Kotlin. No Python, Go, C/C++, Rust, or
shell application code. Language companions read: `sql.md`, `journeys.md`.
No `kotlin.md` exists in `~/.claude/`.

**Unavailable analyzers (not installed; not added):** jscpd, detekt,
ktlint, ArchUnit, OWASP dependency-check. Clone and cycle claims below
are from full manual read of the 25 Kotlin files plus import grep.

## Commands run

| Command | Version / notes | Exit | Shipped vs auxiliary | Result / limitation |
|---|---|---|---|---|
| `git rev-parse --abbrev-ref HEAD && git rev-parse HEAD && git status --porcelain=v1 -b` | git 2.55.0 | 0 | provenance | `master`, `c809d5d0775a54161112b8591cc44a6a152f3260`, clean |
| `git ls-files`; `git log --oneline -30`; churn `uniq -c` | git 2.55.0 | 0 | auxiliary | 25 `.kt` tracked; no `src/test`; highest churn `TodayViewModel.kt` (7) |
| `git ls-files --error-unmatch local.properties` | git 2.55.0 | 1 | auxiliary | not tracked (prior High finding closed) |
| `rg` across `*.kt` / manifests | ripgrep 15.1.0 | 0 | auxiliary | callers, DataStore, tests, `refreshDate` |
| `java -version` | OpenJDK 21.0.11 (Homebrew) | 0 | auxiliary | daemon JVM 21; project `jvmTarget` 17 |
| `./gradlew --version` | Gradle 8.13, Kotlin 2.0.21 (wrapper) | 0 | auxiliary | AGP/Kotlin versions come from catalog, not wrapper Kotlin |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath --quiet` | Gradle 8.13 | 0 | auxiliary | no `datastore` in resolved runtime graph |
| `./gradlew tasks --group verification --quiet` | Gradle 8.13 | 0 | auxiliary | `test`, `lintDebug`, `connectedDebugAndroidTest` exist |
| `./gradlew lintDebug testDebugUnitTest --offline` | Gradle 8.13 | 1 | failed attempt | cache miss; discarded as evidence |
| `./gradlew lintDebug testDebugUnitTest` | Gradle 8.13, AGP 8.9.1 | 0 | **shipped-adjacent** | `lintDebug` 0 errors / 32 warnings; `:app:testDebugUnitTest NO-SOURCE`; `:app:compileDebugUnitTestKotlin NO-SOURCE` |
| parse `app/build/reports/lint-results-debug.xml` | Python 3.13.0 | 0 | auxiliary (lint output) | 24 GradleDependency, 3 AndroidGradlePluginVersion, 1 DataExtractionRules, 1 ObsoleteSdkInt, 1 UnusedResources, 1 MonochromeLauncherIcon, 1 UseKtx |
| `gh api repos/marcelocantos/protocol-app` | gh 2.97.0 | 0 | auxiliary | public, default `master`; Dependabot updates and secret scanning **disabled** |
| `/Users/marcelo/.claude/skills/hygiene/hygiene_check.py` | uv-run script | 1 | hygiene | `FileNotFoundError: hygiene.yaml` |

`assembleDebug` was not re-invoked this session. `lintDebug` compiled
debug Kotlin (`compileDebugKotlin UP-TO-DATE`) against the current tree.
A debug APK already exists under gitignored `app/build/outputs/apk/debug/`.

## Observed architecture

```
MainActivity ──► AppNavigation
                    │  viewModel() ×3 (activity scope)
                    ├─ TodayScreen  / TodayViewModel  ──► ScheduleRepository ──┐
                    ├─ PlanScreen   / PlanViewModel   ──► ScheduleRepository ──┤
                    │                         └──► PlanningRepository ─────────┤
                    └─ WeekScreen   / WeekViewModel   ──► ScheduleRepository ──┤
                                                                                │
ProtocolApp.db: ProtocolDatabase (SQLiteOpenHelper, protocol.db v2) ◄───────────┘
     tables: checklist_completions, schedule, week_plans, settings

BootReceiver / MainActivity.onResume ──► NotificationScheduler (WorkManager)
  MovementBreakWorker, WindDownWorker, BedtimeWorker, PlanningReminderWorker
  PlanningReminderWorker ──► new ScheduleRepository + PlanningRepository
```

**Declared rules that agree with code**

- Three screens, one ViewModel each, one `ProtocolDatabase` (`CLAUDE.md`).
- Completions timestamped RFC 3339 with millis (`ProtocolDatabase` /
  `RFC3339_MS`).
- Day types drive `checklistFor()`; gym counted via item id `gym`.
- Four OneTimeWork channels with manual re-enqueue, not `PeriodicWork`.
- No DI framework; `application as ProtocolApp`.
- CI: `assembleDebug` + `lintDebug` on push/PR to `master`.

**Observed, inferred from code (not in docs)**

- ViewModels are created in `AppNavigation` and survive tab switches.
- Each ViewModel constructs its own `ScheduleRepository` (and Plan its own
  `PlanningRepository`). Refresh `MutableSharedFlow`s are therefore
  per-instance, not process-wide.
- Today and Week never read `week_plans`.
- Selected tab is a `settings` row (`selected_tab`), not DataStore.
- `TodayViewModel.refreshDate()` has no callers.

**Contradictions**

- `CLAUDE.md` “See `docs/targets.md`” — file removed in `c809d5d`; live
  target is `bullseye.yaml` 🎯T1.
- `gradle/libs.versions.toml` still catalogs `datastore-preferences`;
  audit-log claims DataStore was purged. Runtime classpath has no
  DataStore; catalog entry is residue.
- `docs/audit-2026-04-08.md` actionable checklist remains unchecked;
  `docs/audit-log.md` records those High/Critical items as addressed.
  Historical snapshot vs log — not runtime, but a competing record.

**Unknown intent**

- Should a Plan-screen day-type override the default `schedule` for that
  calendar date on Today/Week, or is Plan parking-only with day-type as a
  planning worksheet?

**Enforcement**

- Compile + Android lint in CI. Lint does not fail the build on warnings
  (0 errors / 32 warnings).
- No architecture test, no package-cycle check, no schema test, no
  WorkManager test, no Compose UI test, no journey.

## Dimension vector

No scalar. Change-from-baseline is versus the 2026-04-08 audit + open-source
follow-through, not versus a prior entropy report.

| Dimension | State | Evidence summary | Change from baseline |
|---|---|---|---|
| Architecture topology | concern | Clean UI → repository → SQLite direction; broken by per-VM repository instances and Today bypassing `week_plans`. | Improved packaging after DataStore removal; observer identity still missing. |
| Redundancy / sources of truth | concern | `schedule` vs `week_plans.day_type`; four schedule Flows; leftover DataStore catalog. | DataStore/runtime dual-write closed; catalog residue remains; plan/schedule split unchanged. |
| Change amplification | concern | Checklist item ids are stringly typed; gym special-cased in SQL; adding a date’s effective day type requires Today + Week + Plan. | `GYM_WEEKLY_TARGET` extracted; item id `gym` still a literal. |
| Local code quality | healthy | 25 Kotlin files, linear ViewModels, parameterized SQL, SPDX on all `.kt`. Dead `refreshDate`, leftover catalog, one TOCTOU. | Dead `ChecklistRepository` / migration removed. |
| Correctness / verification | critical | No tests; known Today blank-page (🎯T1); Week summary stale after completions; 09:00 movement notify skipped. | Still zero tests (prior High). T1 documented 2026-04-12, still `identified`. |
| Security / dependencies | concern | No secrets in source. Public repo; Dependabot and secret scanning disabled. Lint dependency-age warnings. Backup XML ignored on API 31+. | `local.properties` untracked. No scanner added. |
| Build / release / operations | concern | CI compile+lint green path exists. No test job. No release pipeline (accepted in audit-log for a personal APK). WorkManager delays inexact. | CI added (`1d3df33`). Still no `./gradlew test` in workflow. |
| Documentation / governance | concern | README + `CLAUDE.md` + LICENSE accurate on structure; stale targets pointer; no `AGENTS.md`; no `hygiene.yaml`. | Docs added; `docs/targets.md` deleted without updating `CLAUDE.md`. |

## Findings

### ENT-001: No executable tests on the shipped or auxiliary path

- **Priority:** P1
- **Dimensions:** Correctness / verification; Build / release / operations
- **Status:** observed fact
- **Evidence:**
  - `app/src/` contains only `main/` (no `test/`, no `androidTest/`).
  - `git log --all -- '**/src/test/**' '**/src/androidTest/**'` — empty.
  - `./gradlew lintDebug testDebugUnitTest` → `:app:testDebugUnitTest NO-SOURCE`,
    `:app:compileDebugUnitTestKotlin NO-SOURCE`, exit 0.
  - `.github/workflows/ci.yml:23-27` runs `assembleDebug` and `lintDebug`
    only.
  - `app/build.gradle.kts` declares no `testImplementation` libraries.
  - `docs/audit-2026-04-08.md` Finding 12 (High) and `docs/audit-log.md`
    deferred “Zero test coverage”.
- **Mechanism:** Load-bearing properties (schema v1→v2 rewrite, `checklistFor`
  per day type, `unplannedDays`, `delayUntil` wrap-past-midnight, gym weekly
  count, schedule default-merge) have no oracle. CI stays green while those
  functions regress. A `NO-SOURCE` test task is success-shaped failure.
- **Blast radius:** every future behavior change, including 🎯T1 and ENT-002.
  Personal-use does not shrink the set of untested invariants.
- **Counterevidence checked:** Gradle still generates `testDebugUnitTest` /
  `connectedDebugAndroidTest` tasks; they have nothing to run. Lint is not a
  substitute (no Kotlin logic rules fired).
- **Smallest coherent remediation:** add `src/test` with JVM tests for
  `checklistFor`, `ScheduleRepository` default-merge, `PlanningRepository.unplannedDays`,
  `ProtocolDatabase.onUpgrade` against `SQLiteDatabase.create(null)` or
  Robolectric, and `NotificationScheduler.delayUntil` with a clock seam.
  Then add `./gradlew testDebugUnitTest` to CI. Journeys (device) later;
  do not call lint a journey (`journeys.md`).
- **Verification:** CI job that fails when the test source set is empty or
  when `checklistFor(DayType.OFFICE)` drops `gym`.
- **Ratchet candidate:** CI step `testDebugUnitTest` plus a hygiene
  `correctness.unit-tests` item once `hygiene.yaml` exists.

### ENT-002: `schedule` and `week_plans` both claim “day type for this date”

- **Priority:** P1
- **Dimensions:** Redundancy / sources of truth; Change amplification
- **Status:** observed fact (split); inference (user-visible wrong checklist)
- **Evidence:**
  - Default weekly type: `ScheduleRepository.dayTypeFor` maps
    `date.dayOfWeek` through `schedule` only
    (`app/src/main/java/com/marcelo/protocol/data/ScheduleRepository.kt:37-39`).
  - Today binds that flow: `app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:60-62`.
  - Week summary uses `sched[date.dayOfWeek]` only
    (`app/src/main/java/com/marcelo/protocol/ui/week/WeekViewModel.kt:56`).
  - Plan writes per-week overrides to `week_plans.day_type`
    (`app/src/main/java/com/marcelo/protocol/data/PlanningRepository.kt:30-32`, `app/src/main/java/com/marcelo/protocol/ui/plan/PlanViewModel.kt:68-78`).
  - Effective type on Plan: `plan.dayType ?: defaultType`
    (`app/src/main/java/com/marcelo/protocol/ui/plan/PlanViewModel.kt:32`).
  - Planning reminders use the same merge
    (`app/src/main/java/com/marcelo/protocol/data/PlanningRepository.kt:40`).
  - No `getWeekPlan` / `PlanningRepository` reference under
    `ui/today` or `ui/week`.
- **Mechanism:** Two tables store a `DayType` for a calendar day. Plan can
  mark next Tuesday OFFICE (and book parking) while `schedule` still says
  WFH. When that Tuesday arrives, Today still shows the WFH checklist.
  A bugfix to “what items appear on a planned office day” must be repeated
  in Today, Week, and Plan or it will drift again.
- **Blast radius:** Today checklist composition, Week 7-day percentages,
  gym-day presence, parking reminders vs actual day type.
- **Counterevidence checked:** `CLAUDE.md` describes Plan as “day type
  (Office/WFH/Rest) and parking status” and Today as “day-type-aware”
  without saying they share an effective-type function. Could be a
  worksheet-only Plan (owner intent — residue). The UI still persists
  `day_type` and uses it for parking visibility, so it is not a throwaway
  label.
- **Smallest coherent remediation:** one function
  `effectiveDayType(date, schedule, weekPlan)` used by Today, Week, Plan,
  and `PlanningReminderWorker`. If Plan is parking-only, stop storing
  `day_type` overrides and drive Plan chips from `schedule`.
- **Verification:** given default Tue=WFH and a week-plan Tue=OFFICE,
  Today’s `dayType` for that date is OFFICE; a unit test fails if Today
  consults `schedule` alone.
- **Ratchet candidate:** test that `effectiveDayType` is the only
  `DayType` derivation (arch unit / grep ratchet on `schedule[date.dayOfWeek]`
  outside the helper).

### ENT-003: Per-instance repositories and Week not observing completions

- **Priority:** P1
- **Dimensions:** Architecture topology; Change amplification; Correctness
- **Status:** observed fact
- **Evidence:**
  - `ScheduleRepository` holds `private val _refresh = MutableSharedFlow`
    (`app/src/main/java/com/marcelo/protocol/data/ScheduleRepository.kt:29-35`). Emitting after `setDayType` only
    notifies collectors of *that* instance.
  - Constructors:
    `app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:42`, `app/src/main/java/com/marcelo/protocol/ui/plan/PlanViewModel.kt:40-41`,
    `app/src/main/java/com/marcelo/protocol/ui/week/WeekViewModel.kt:32`, `app/src/main/java/com/marcelo/protocol/notification/PlanningReminderWorker.kt:24-25`.
  - `app/src/main/java/com/marcelo/protocol/ui/navigation/AppNavigation.kt:63-65` creates all three ViewModels at activity
    scope, so they stay alive across tabs.
  - `WeekViewModel.init` (`app/src/main/java/com/marcelo/protocol/ui/week/WeekViewModel.kt:47-63`) loads
    `gymCountForWeek` **once**, then rebuilds `weekSummary` only when
    *its* `scheduleRepo.schedule` emits. Completing gym on Today does not
    write the schedule table, so Week stays stale until process death or
    a schedule chip change.
  - Today completions refresh via a private `_refresh`
    (`app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:64-66`, `app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:95-105`) that Week cannot see.
- **Mechanism:** SQLite is shared; observers are not. The repository
  pattern is instantiated as a cache without a shared owner. Tab switches
  look like they should show fresh data because the VM still exists.
- **Blast radius:** Week gym count and last-7-day percentages after Today
  toggles; Today day type after Week schedule edits, until a date change
  retriggers `flatMapLatest`.
- **Counterevidence checked:** Worker-created repositories are one-shot
  (`first()`), so they are not a stale UI cache. `ProtocolApp.db` is a
  single `lazy` instance (`app/src/main/java/com/marcelo/protocol/ProtocolApp.kt:12`) — the DB object is shared;
  the Flows are not. Batch query `completedItemsBatch` (`app/src/main/java/com/marcelo/protocol/data/ProtocolDatabase.kt:103-119`)
  fixed the 7-query performance issue from the 2026-04-08 audit; it did
  not add invalidation.
- **Smallest coherent remediation:** construct `ScheduleRepository` and
  `PlanningRepository` once on `ProtocolApp` (or a tiny holder) and add a
  completions refresh signal (or `callbackFlow` on DB writes) that Week
  and Today both collect. Do not introduce Hilt solely for this.
- **Verification:** instrumented or Robolectric test: toggle gym on Today,
  collect `WeekViewModel.gymCount`, assert increment without recreating
  the VM. Second test: change Monday type on Week, assert Today’s
  `dayType` for a Monday updates.
- **Ratchet candidate:** test that `ScheduleRepository(` has a single
  production construction site (`ProtocolApp`).

### ENT-004: Today date is frozen across midnight and resume (🎯T1)

- **Priority:** P1
- **Dimensions:** Correctness / verification; Local code quality
- **Status:** observed fact (code); needs verification (device repro this
  session)
- **Evidence:**
  - `MainActivity.onResume` (`app/src/main/java/com/marcelo/protocol/MainActivity.kt:44-47`) only calls
    `NotificationScheduler.ensureScheduled`. It does not touch
    `TodayViewModel`.
  - `TodayViewModel.refreshDate()` (`app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:90-93`) exists and
    has **zero** callers (`rg fun refreshDate` — definition only).
  - `_selectedDate` is initialized to `LocalDate.now()` once
    (`app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:44`). `isToday` remaps that value against a new
    `LocalDate.now()` only when `_selectedDate` emits (`app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:52-54`).
  - Pager mapping `pageToDate` uses `LocalDate.now()` at call time
    (`app/src/main/java/com/marcelo/protocol/ui/today/TodayScreen.kt:62-63`) but the pager-to-VM sync is
    `snapshotFlow { pagerState.settledPage }` (`app/src/main/java/com/marcelo/protocol/ui/today/TodayScreen.kt:79-83`), which does not
    emit when the civil date changes under a stable page index.
  - Inactive pages hide the checklist (`app/src/main/java/com/marcelo/protocol/ui/today/TodayScreen.kt:165`:
    `if (isActivePage)`). If `selectedDate` is yesterday and page
    `PAGE_OFFSET` now means today, the visible page is not active → blank
    body. Matches `bullseye.yaml` 🎯T1 context causes (2) and (4).
  - `dayType` `stateIn` initial value is `DayType.REST`
    (`app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:60-62`) — T1 cause (3) default-value flash.
- **Mechanism:** activity is `singleTask` (`app/src/main/AndroidManifest.xml:23`);
  ViewModel survives overnight. Date is not a clock subscription. The
  one method that could reset it is unused.
- **Blast radius:** primary owner path (open app the next morning, swipe
  days). 🎯T1 acceptance: “Opening the app on a new day shows today’s
  checklist immediately — never a blank or hung page.”
- **Counterevidence checked:** `goToDate` resets date on swipe
  (`app/src/main/java/com/marcelo/protocol/ui/today/TodayViewModel.kt:81-84`), which is why “swipe away and back” recovers
  (T1). `ensureScheduled` on resume is unrelated to UI date. Process death
  would reinit `_selectedDate` to today — the bug is the *surviving*
  process.
- **Smallest coherent remediation:** on `ON_RESUME`, call `refreshDate()`
  (or compare `LocalDate.now()` and jump the pager to `PAGE_OFFSET`).
  Then cache/prefetch adjacent completions (T1 cause 1) only if swipe
  still hitching.
- **Verification:** journey or instrumented test: set VM date to
  yesterday, fire resume, assert `selectedDate == LocalDate.now()` and
  checklist non-empty. Must fail before the resume hook exists.
- **Ratchet candidate:** test that `refreshDate` is invoked from a
  lifecycle observer; 🎯T1 remains the product assertion.

### ENT-005: Movement window excludes 09:00; WorkManager delays are inexact

- **Priority:** P2
- **Dimensions:** Correctness / verification; Build / release / operations
- **Status:** observed fact (09:00 skip); inference (Doze lateness)
- **Evidence:**
  - First daily movement work is delayed until `LocalTime.of(9, 0)`
    (`app/src/main/java/com/marcelo/protocol/notification/NotificationScheduler.kt:31`, `app/src/main/java/com/marcelo/protocol/notification/NotificationScheduler.kt:59`).
  - Worker notifies only if `now.isAfter(workStart) && now.isBefore(workEnd)`
    with `workStart = 09:00` (`app/src/main/java/com/marcelo/protocol/notification/MovementBreakWorker.kt:17-21`).
    `LocalTime.09:00.isAfter(09:00)` is false, so the 09:00 run is silent
    and only chains `scheduleMovementFollowUp`.
  - Follow-up uses two clocks: `val now = LocalTime.now()` then
    `Duration.between(LocalTime.now(), nextTime)`
    (`app/src/main/java/com/marcelo/protocol/notification/NotificationScheduler.kt:116-122`) — leftover TOCTOU from
    `docs/audit-2026-04-08.md` Finding 5.
  - All work is `OneTimeWorkRequestBuilder.setInitialDelay` with no
    `setExpedited`, exact alarm permission, or `AlarmManager`. Manifest
    has `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` only
    (`app/src/main/AndroidManifest.xml:8-9`).
  - `CLAUDE.md` table: “Every 90 min, 9am–5pm”.
- **Mechanism:** documented 9am reminder can never fire on time even if
  WorkManager runs at 09:00:00. Inexact work on API 31+ can also slip
  bedtime/wind-down past the intended minute. Follow-up delay shortens
  by the time between the two `now()` calls (usually negligible).
- **Blast radius:** four reminder channels; owner-visible missed 9am
  stand-up.
- **Counterevidence checked:** `BootReceiver` re-enqueues on reboot
  (`app/src/main/java/com/marcelo/protocol/notification/BootReceiver.kt:12-14`). `ensureScheduled` uses `ExistingWorkPolicy.KEEP`
  so a live 90-minute chain is not replaced on every resume
  (`app/src/main/java/com/marcelo/protocol/notification/NotificationScheduler.kt:27-32`). Wind-down at 22:00 calling
  `reschedule()` REPLACE-queues movement for next 09:00, which is after
  work hours — not a cancelled mid-day chain.
- **Smallest coherent remediation:** notify on `!now.isBefore(workStart)`
  (inclusive 09:00). Pass a single `now` into delay math. Decide whether
  exact alarms are worth `SCHEDULE_EXACT_ALARM` for a personal app.
- **Verification:** unit test with injected clock at 09:00:00 expects a
  notification (or a `shouldNotify` helper returning true).
- **Ratchet candidate:** clock-injected tests for `delayUntil` and the
  movement window predicate.

### ENT-006: Android 12+ backup ignores `backup_rules.xml`

- **Priority:** P2
- **Dimensions:** Security / dependencies; Build / release / operations
- **Status:** observed fact
- **Evidence:**
  - `app/src/main/AndroidManifest.xml:13-14`: `allowBackup=true`,
    `fullBackupContent="@xml/backup_rules"`. No
    `android:dataExtractionRules`.
  - `app/src/main/res/xml/backup_rules.xml` includes `protocol.db` only.
  - Lint `DataExtractionRules` at `app/src/main/AndroidManifest.xml:14`: fullBackupContent
    deprecated from Android 12; without `dataExtractionRules`, API 31+
    uses default backup rules.
  - `minSdk` 29, `targetSdk` 36 (`app/build.gradle.kts:14-15`).
- **Mechanism:** on the devices this app actually runs on (API 31+), the
  explicit include file does not govern device-to-device transfer or the
  new backup schema. Defaults still back up databases, so data is not
  silently dropped, but include/exclude intent is unenforced. A future
  schema bump plus cloud restore can land vN data on vN+1 without a
  tested path (`onUpgrade` only handles `oldVersion < 2`,
  `app/src/main/java/com/marcelo/protocol/data/ProtocolDatabase.kt:57-65`).
- **Blast radius:** sole copy of habit history (`protocol.db`). Personal
  app, one user — still the only dataset.
- **Counterevidence checked:** `allowBackup=true` plus defaults likely
  still backs up the DB on API 31+ (so this is not “backup off”). No
  in-app export exists (prior audit Finding 12/25). `onUpgrade` is
  transactional via `SQLiteOpenHelper`.
- **Smallest coherent remediation:** add `@xml/data_extraction_rules`
  mirroring the include of `protocol.db`, set
  `android:dataExtractionRules`. Keep `fullBackupContent` for API 29–30.
- **Verification:** lint `DataExtractionRules` warning gone; optional
  restore test with a v1 fixture DB.
- **Ratchet candidate:** lint check already exists — fail CI on that id
  (`lint.xml` / `abortOnError` for selected issues).

### ENT-007: `CLAUDE.md` still points at deleted `docs/targets.md`

- **Priority:** P2
- **Dimensions:** Documentation / governance
- **Status:** observed fact
- **Evidence:**
  - `CLAUDE.md:93`: “See `docs/targets.md` for convergence targets.”
  - `docs/targets.md` is absent; `git ls-files` has `bullseye.yaml` instead.
  - HEAD commit `c809d5d` message: “push/remove targets md bullseye yaml
    is the source of t (#2)”.
  - `docs/audit-log.md:14` still cites `docs/targets.md` for 🎯T1.
- **Mechanism:** agents following project instructions look for a file
  that does not exist and miss the live target (Today render, 🎯T1).
- **Blast radius:** agent/owner routing to the wrong work record; 🎯T1
  already encodes ENT-004.
- **Counterevidence checked:** `bullseye.yaml` T1 is `identified` with
  acceptance criteria that still match the code. README does not mention
  targets.
- **Smallest coherent remediation:** point `CLAUDE.md` (and the audit-log
  line, or leave the log as historical) at `bullseye.yaml`.
- **Verification:** `test -f docs/targets.md` is false; a doc grep that
  `CLAUDE.md` contains `bullseye.yaml` and not `docs/targets.md`.
- **Ratchet candidate:** hygiene `file:` evidence for `bullseye.yaml` and
  an `absent:` or content check that `CLAUDE.md` does not mention
  `docs/targets.md`.

### ENT-008: DataStore catalog residue after runtime purge

- **Priority:** P3
- **Dimensions:** Redundancy / sources of truth; Security / dependencies
- **Status:** observed fact
- **Evidence:**
  - `gradle/libs.versions.toml:8` and `gradle/libs.versions.toml:25` declare
    `datastore-preferences`.
  - `app/build.gradle.kts` `dependencies { }` does not reference it.
  - `./gradlew :app:dependencies --configuration debugRuntimeClasspath`
    prints no `datastore`.
  - Lint still warns `GradleDependency` on that unused version key.
  - `docs/audit-log.md` says DataStore dependency was purged.
- **Mechanism:** a future editor can `implementation(libs.datastore.preferences)`
  and reintroduce a second preference store next to `settings`. Catalog
  is not a runtime dual-write today.
- **Blast radius:** low until someone wires it; lint noise.
- **Counterevidence checked:** no `DataStore` / `PrefsKeys` / `ChecklistRepository`
  Kotlin remains (`git ls-files`).
- **Smallest coherent remediation:** delete the version and library entries.
- **Verification:** `rg datastore gradle/` empty; lint count drops.
- **Ratchet candidate:** `rg -n datastore gradle/libs.versions.toml` in CI
  (optional).

### ENT-009: Gym item id is a magic string in SQL

- **Priority:** P3
- **Dimensions:** Change amplification; Local code quality
- **Status:** observed fact
- **Evidence:**
  - `ChecklistItem` ids `"gym"` in
    `app/src/main/java/com/marcelo/protocol/model/DayType.kt:23`,
    `app/src/main/java/com/marcelo/protocol/model/DayType.kt:36`,
    `app/src/main/java/com/marcelo/protocol/model/DayType.kt:45`.
  - `ProtocolDatabase.gymCountForWeek` filters `item_id = 'gym'`
    (`app/src/main/java/com/marcelo/protocol/data/ProtocolDatabase.kt:125`).
  - `GYM_WEEKLY_TARGET` is a named constant (`app/src/main/java/com/marcelo/protocol/model/ChecklistItem.kt:6`) used
    by Today and Week UI — target was extracted, identity was not.
- **Mechanism:** renaming or splitting the gym item requires a SQL
  string, three checklist rows, and any future analytics to move together.
  Completions already stored as `'gym'` would desync from the checklist.
- **Blast radius:** weekly gym progress on Today and Week.
- **Counterevidence checked:** no other item id is queried specially.
  Domain data duplication across OFFICE/WFH/REST lists is intentional
  (different labels/times), not a competing truth.
- **Smallest coherent remediation:** `const val GYM_ITEM_ID = "gym"` used
  in `checklistFor` and `gymCountForWeek`.
- **Verification:** test that `gymCountForWeek` counts rows whose id
  equals `GYM_ITEM_ID`; grep for `'gym'` outside that constant fails.
- **Ratchet candidate:** unit test + optional grep.

## Redundancy and competing-source-of-truth inventory

| Concept | Authorities | Drift already? | Action |
|---|---|---|---|
| Day type for a calendar date | `schedule` table + `ScheduleRepository.DEFAULT_SCHEDULE`; `week_plans.day_type`; `stateIn` initial `DayType.REST` | Yes if Plan override ≠ default (ENT-002). REST flash on subscribe (T1 cause 3). | Merge or delete override. |
| Schedule Flow | Four `ScheduleRepository` instances | Yes across tabs (ENT-003). | Single instance on `ProtocolApp`. |
| Completions | `ProtocolDatabase` (writes); Today `_refresh`; Week one-shot + schedule collect | Week stale after Today toggle (ENT-003). | Shared invalidation. |
| Gym weekly target | `GYM_WEEKLY_TARGET` (healthy) vs item id `'gym'` (ENT-009) | No numeric drift; identity still split. | Named item id. |
| Preferences | SQLite `settings` (live); DataStore catalog (dead) | Catalog only (ENT-008). | Drop catalog. |
| Convergence targets | `bullseye.yaml` (live); `CLAUDE.md` → `docs/targets.md` (dead) | Pointer drift (ENT-007). | Fix docs. |
| Prior audit status | `docs/audit-2026-04-08.md` unchecked boxes vs `docs/audit-log.md` | Documentary only. | Leave snapshot; do not rewrite. |

Deliberate duplication: OFFICE/WFH/REST checklist literals (different
times/labels). Worker classes (~25 lines each) — not worth a framework.

## Healthy structure worth retaining

- **Single SQLite owner.** `ProtocolApp.db` is the only `SQLiteOpenHelper`.
  All queries seen are parameterized (`?` bind args). Column lists are
  explicit (no `SELECT *`). Matches `sql.md` safety on injection.
- **Package direction.** `ui/*` → `data` + `model`; `notification` →
  `data` + `NotificationHelper`; `model` has no Android imports besides
  serialization annotations. No cycles among the 25 files.
- **Screen/VM/table correspondence** in `CLAUDE.md` is accurate enough to
  onboard: Today/Plan/Week, four tables, four notification channels.
- **Open-source hygiene that held:** Apache-2.0 `LICENSE`; SPDX on every
  tracked `.kt`; `local.properties` untracked; ProGuard rules for
  kotlinx.serialization (`app/proguard-rules.pro`); CI workflow present.
- **`completedItemsBatch`** and `GYM_WEEKLY_TARGET` from the 2026-04-08
  follow-through — keep.
- **Boot + resume scheduling.** `BootReceiver` + `ensureScheduled(KEEP)`
  is the right shape for OneTime chains; do not replace with uncoordinated
  `PeriodicWork` without revisiting the 90-minute window.

## Hygiene posture

`hygiene.yaml` is **absent**. Invoking
`/Users/marcelo/.claude/skills/hygiene/hygiene_check.py` from the repo
root raised `FileNotFoundError` for
`/Users/marcelo/work/github.com/marcelocantos/protocol-app/hygiene.yaml`
(exit 1). Per skill/brief: **hygiene posture not declared**. Not
initialized.

Informal reality (not a hygiene vector — would be the seed if onboarded):

| Dimension | What exists | Gap vs a typical floor 2 |
|---|---|---|
| correctness | none | no tests (ENT-001) |
| quality | `lintDebug` in CI | warnings non-blocking; 32 warnings |
| build | `assembleDebug` in CI | no release variant in CI |
| docs | README, LICENSE, `CLAUDE.md` | stale targets pointer (ENT-007) |
| security | no secrets in tree | Dependabot/secret scanning disabled |
| governance | squash-only claimed in old audit | no CODEOWNERS, no `hygiene.yaml` |
| release | versionName `0.1.0` | no `/release` artifacts; audit-log skipped on purpose |

Entropy findings ENT-001, ENT-006, ENT-007 are the ones that become
hygiene items without new product design.

## Oracle coverage and residue

| Property | Decision path |
|---|---|
| Kotlin compiles for debug | shipped: `compileDebugKotlin` via `lintDebug` this session; CI `assembleDebug` |
| Android lint errors = 0 | shipped: `lintDebug` 0 errors / 32 warnings |
| Unit-test logic | **nothing** (`NO-SOURCE`) |
| Schema v1→v2 rewrite | **nothing** (code in `onUpgrade` only) |
| Effective day type | **nothing** (ENT-002) |
| Cross-tab invalidation | **nothing** (ENT-003) |
| Today date on resume / new day | bullseye 🎯T1, **manual / unreproduced this session** |
| Notification wall-clock | **nothing**; WorkManager inexact on device |
| Owner journey (open app, tick item, see Week gym) | **nothing** — not a journey, not a hermetic |
| Dependency CVEs | **nothing** (Dependabot off) |
| Backup/restore | lint warning only (ENT-006) |
| Hygiene floors | **undeclared** |

Failed/skipped checks: `./gradlew … --offline` (cache); jscpd/detekt not
present; `assembleDebug` not re-run; no device journey.

**Owner residue (intent only):**

1. Should Plan `day_type` override Today/Week for that week (ENT-002)?
2. Accept inexact WorkManager for bedtime/wind-down, or take exact alarms?
3. Onboard `hygiene.yaml` at honest floors (likely correctness=0 until
   tests exist)?
4. Keep “no binary release” as the delivery definition (`CLAUDE.md`
   Delivery: merged to master)?

Do not hand back “please run lint” — it ran.

## Remediation sequence

1. **Oracle seam:** JVM tests for `checklistFor`, schedule default-merge,
   `unplannedDays`, movement window predicate, `delayUntil`. Put
   `testDebugUnitTest` on CI. Empty-suite must not be green
   (ENT-001).
2. **One effective day type:** implement or explicitly reject Plan→Today
   merge (ENT-002). Owner call first.
3. **One repository instance + completion invalidation** on `ProtocolApp`
   (ENT-003). Tests from step 1 should fail on stale Week gym until this
   lands.
4. **Resume/midnight date:** wire `refreshDate()` (ENT-004 / 🎯T1). Then
   prefetch if swipe hitch remains.
5. **Notifications:** inclusive 09:00; single `now` (ENT-005).
6. **Backup XML** for API 31+ (ENT-006); drop DataStore catalog
   (ENT-008); `GYM_ITEM_ID` (ENT-009); retarget `CLAUDE.md` (ENT-007).
7. **Ratchet** in CI / future `hygiene.yaml`. Re-run this audit on the
   same finding ids.

No architectural rewrite. Hilt/Koin is not required at this size
(prior Low finding still stands as non-actionable).
