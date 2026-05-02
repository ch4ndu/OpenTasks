# OpenTasks Architecture

Load this when working on architecture, source layout, dependency flow, or cross-layer behavior.

## Stack

- Kotlin Multiplatform Compose targeting Android, iOS, and JVM Desktop.
- Room database with `BundledSQLiteDriver` and KSP code generation.
- Koin DI with `sharedModule` in common code and per-platform `platformModule` database builders.
- AndroidX Navigation 3 for app navigation.
- `kotlinx-datetime` for date/time logic.

## Layering

- Data layer owns models, DAOs, repositories, database setup, sync adapters, and date/time boundary conversion.
- Domain layer owns read UseCases and write Actions.
- ViewModels are per screen and inject UseCases/Actions, never repositories.
- UI renders state and sends events. Filtering, sorting, grouping, and mapping belong in UseCases or ViewModels, not composables.

## UseCases And Actions

- Reads use one UseCase class per read operation. UseCases return `Flow` via `operator fun invoke()`.
- Writes use one Action class per write operation. Actions expose `suspend operator fun invoke(...)`.
- Actions handle timestamps with `localNow()`; repositories convert local values to UTC for persistence.
- New features should create or reuse UseCases/Actions first, then wire them into the relevant ViewModel.

## Source Layout

```text
composeApp/src/commonMain/kotlin/.../
├── data/          # Models, DAOs, repositories, DB, sync
├── domain/        # UseCases and Actions
├── viewmodel/     # Per-screen ViewModels + AppViewModel
├── ui/            # Screens, theme, composables
└── di/            # Koin modules
```

Platform directories are `androidMain/`, `iosMain/`, and `jvmMain/`. Use `expect`/`actual` only when shared common code cannot reasonably handle the behavior.

## Key Files

| Purpose | Path |
| --- | --- |
| App entry and navigation | `App.kt` |
| ViewModels | `viewmodel/` |
| UseCases and Actions | `domain/usecase/`, `domain/action/` |
| Database | `data/database/AppDatabase.kt` |
| DI modules | `di/AppModule.kt` |
| Date utilities | `data/extensions/DateTimeUtils.kt` |
| Models | `data/model/` |
| Version catalog | `gradle/libs.versions.toml` |

## Priority System

- `HIGH` = Urgent and Important, quadrant I, red.
- `MEDIUM` = Not Urgent and Important, quadrant II, amber.
- `LOW` = Urgent and Unimportant, quadrant III, blue.
- `NONE` = Not Urgent and Unimportant, quadrant IV, green.
