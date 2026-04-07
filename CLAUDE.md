# Protocol

Android health/habit tracker built with Kotlin and Jetpack Compose.
Personal-use app for daily checklist tracking, office day planning,
and timed notification reminders.

## Build

Requires JDK 17 and Android SDK (compileSdk 36, minSdk 29).

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew lintDebug              # Run lint checks
./gradlew installDebug           # Install to connected device
```

CI runs `assembleDebug` and `lintDebug` on push/PR to master.

## Architecture

Three screens, one ViewModel each, all backed by a single SQLite
database (`ProtocolDatabase`):

- **Today** (`TodayScreen` / `TodayViewModel`) — Swipeable
  `HorizontalPager` showing the day's checklist. Pager covers ±365
  days. Completions are timestamped. Past days are locked by default
  (unlock via lock icon).
- **Plan** (`PlanScreen` / `PlanViewModel`) — Week-ahead office day
  planner. Each weekday gets a day type (Office/WFH/Rest) and parking
  status (Unplanned/Planned/Booked). Links to Wilson Parking app.
- **Week** (`WeekScreen` / `WeekViewModel`) — Default schedule
  configuration (day type per weekday) and 7-day completion summary.

### Data layer

- `ProtocolDatabase` — `SQLiteOpenHelper` subclass, single source of
  truth. All queries dispatch to `Dispatchers.IO`.
- `ScheduleRepository` — Wraps schedule reads/writes with a
  `MutableSharedFlow` refresh signal.
- `PlanningRepository` — Same pattern for week plans.
- No DI framework. ViewModels cast `application as ProtocolApp` to
  access the shared `db` instance.

### Notifications

Four `WorkManager` channels, each using `OneTimeWorkRequest` with
manual re-enqueue (not `PeriodicWork`):

| Channel | Schedule | Purpose |
|---------|----------|---------|
| `movement_break` | Every 90 min, 9am–5pm | Stand up and move |
| `wind_down` | 10pm daily | Screens off |
| `bedtime` | 11pm daily | Get to bed |
| `planning` | Sat 10am, Sat 5pm, Sun 10am | Book office parking |

`BootReceiver` re-schedules on device reboot.
`MovementBreakWorker` chains follow-up work requests for the 90-min
interval.

## Database

SQLite v2 (`protocol.db`). Four tables:

| Table | Purpose | PK |
|-------|---------|-----|
| `checklist_completions` | Item completions with RFC 3339 timestamps | `(date, item_id)` |
| `schedule` | Default day type per weekday | `day_of_week` |
| `week_plans` | Per-week office day plans with parking status | `(week_start, day_of_week)` |
| `settings` | Key-value store for app preferences | `key` |

v1→v2 migration converts `completed_at` from `HH:mm` to full
RFC 3339 (`yyyy-MM-ddTHH:mm:ss.SSS`).

## Checklist model

Day types define which checklist items appear. Items are hardcoded in
`checklistFor()` (`DayType.kt`). The `gym` item ID is special — it's
counted across the week against `GYM_WEEKLY_TARGET` (3).

## Conventions

- Apache 2.0 licence. All `.kt` files carry SPDX headers.
- No test suite yet (tracked in audit findings).
- ProGuard rules in `app/proguard-rules.pro` for
  kotlinx.serialization.

## Delivery

Merged to master.

## Targets

See `docs/targets.md` for convergence targets.
