# Protocol

Android health and habit tracker for daily checklist management,
office day planning, and timed reminders.

Built with Kotlin, Jetpack Compose, and SQLite.

## Features

- **Daily checklist** with day-type-aware items (Office / WFH / Rest)
  and completion timestamps
- **Swipeable calendar** covering ±1 year of daily history
- **Office day planner** with parking status tracking and Wilson
  Parking integration
- **Weekly schedule** with configurable default day types and
  completion summary
- **Push notifications** for movement breaks, wind-down, bedtime, and
  weekend planning reminders

## Build

Requires JDK 17 and Android SDK (compileSdk 36, minSdk 29).

```bash
./gradlew assembleDebug
```

## Licence

[Apache 2.0](LICENSE)
