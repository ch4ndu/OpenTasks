# OpenTasks

A cross-platform task management app built with **Kotlin Multiplatform** and **Compose Multiplatform**, featuring Eisenhower Matrix prioritization to help you focus on what truly matters. Inspired by [TickTick](https://ticktick.com).

## Platforms

| Android | iOS | Desktop (JVM) |
|---------|-----|----------------|
| API 24+ (Android 7.0) | arm64 + Simulator | Linux, macOS, Windows |

## Features

- **Eisenhower Matrix** — Visualize tasks across four priority quadrants:
  - Urgent & Important (red)
  - Not Urgent & Important (amber)
  - Urgent & Unimportant (blue)
  - Not Urgent & Unimportant (green)
- **Task Lists** — Organize tasks into custom lists (default "Inbox" included)
- **Calendar Views** — Year, month, week, 3-day, and list views
- **Recurring Tasks** — Daily, weekly, monthly, yearly, or every weekday
- **Reminders** — Configurable notifications before deadlines
- **Responsive Layout** — Adapts to compact, medium, and expanded screen sizes
- **Material Design 3** — Modern theming with custom color palette and typography

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Compose Multiplatform + Material 3 |
| State | Single ViewModel with StateFlow |
| Database | Room + BundledSQLiteDriver |
| DI | Koin |
| Navigation | AndroidX Navigation 3 |
| Date/Time | kotlinx-datetime |

## Building & Running

**Prerequisites:** JDK 17+, Android Studio (for Android), Xcode (for iOS)

```bash
# Quick compile check
./gradlew :composeApp:compileKotlinJvm

# Android
./gradlew :composeApp:assembleDebug

# Desktop
./gradlew :composeApp:run
```

For **iOS**, open `iosApp/iosApp.xcodeproj` in Xcode and build the `iosApp` scheme.

## Project Structure

```
composeApp/src/
├── commonMain/          # Shared UI, data, DI, and ViewModel
│   ├── kotlin/.../
│   │   ├── App.kt              # Entry point & navigation
│   │   ├── viewmodel/          # TaskViewModel (single VM)
│   │   ├── data/
│   │   │   ├── model/          # Task, TaskList, TaskPriority, RecurrenceType
│   │   │   ├── database/       # Room database & converters
│   │   │   ├── dao/            # Data access objects
│   │   │   ├── repository/     # Repository interfaces & implementations
│   │   │   └── extensions/     # Date/time utilities
│   │   ├── di/                 # Koin modules
│   │   └── ui/
│   │       ├── screens/        # One composable per screen
│   │       │   └── calendar/   # Calendar view variants
│   │       └── theme/          # Colors, typography, dimensions
│   └── composeResources/       # Drawables, strings
├── androidMain/         # Android DB builder, Application, Activity
├── iosMain/             # iOS DB builder, MainViewController
└── jvmMain/             # Desktop DB builder, main entry point
```

Platform-specific code uses Kotlin's `expect`/`actual` pattern and is kept to a minimum.

## Architecture

- **Repository pattern** wraps Room DAOs for data access
- **Single ViewModel** (`TaskViewModel`) manages all task and list operations
- **UTC storage** — dates stored as UTC epoch millis in the database, converted to local time on read
- **Derived StateFlows** — filtering and sorting happen in the ViewModel, not in composables
- **Immutable entities** — `Task` and `TaskList` are annotated `@Immutable` for efficient recomposition

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
