# Feature Implementation

Load this for new features, bug fixes, UI work, and significant refactors.

## Before Editing

- Read `AGENTS.md`, then load only the task-relevant focused docs.
- Explore existing code for reusable UseCases, Actions, repositories, composables, utilities, strings, and icons.
- Identify whether the change belongs in `commonMain` or requires platform-specific code.
- Keep the change minimal and aligned with existing architecture.

## Data Layer

For a new persisted entity or schema change:

- Add model in `data/model/`.
- Add DAO in `data/dao/`; reads return `Flow`, writes are `suspend`.
- User-visible DAO queries filter out soft-deleted rows with `isDeleted = 0`.
- Add repository interface and implementation in `data/repository/`.
- Repository reads convert UTC to local with `withLocalTimestamps()`.
- Repository writes convert local to UTC with `withUtcTimestamps()`.
- Repository inserts fill zero timestamps with `localNow()` via default timestamp handling.
- Repository deletes are soft deletes, setting `isDeleted = true` and `isSynced = false`.
- Add sync adapter and record in `data/sync/` if the entity syncs to PocketBase.
- Update `AppDatabase.kt` entities, DAO accessors, and version with explicit migrations.
- Register repositories and sync adapters in `di/AppModule.kt`.

## Domain And ViewModels

- Add or reuse one UseCase per read in `domain/usecase/{entity}/`.
- Add or reuse one Action per write in `domain/action/{entity}/`.
- Put derived and filtered flows in UseCases where possible.
- Register UseCases and Actions in `di/AppModule.kt`.
- Use a screen-specific ViewModel for screen state.
- Use `AppViewModel` only for shared sheet operations such as create/edit task or note.
- Register ViewModels in `di/AppModule.kt` with `viewModel { ... }`.

## UI

- Add screens and composables under `ui/screens/`.
- Use `koinViewModel()` at the call site in `App.kt`.
- Wire new tabs or destinations in `App.kt`.
- Follow `docs/ai/ui.md` for theme, strings, dimensions, previews, and recomposition rules.

## Platform Document Flows

- CSV/ICS import and export use the shared Compose launcher contracts in `ui/util`: platform pickers own selection/presentation, while parsing and export generation remain in common ViewModels/Actions.
- Cancellation is a neutral result. Report success only after the selected source was read or the destination write/export callback completed.
- Keep blocking file reads and writes off Android, UIKit, and AWT UI threads. iOS security-scoped access and temporary export files must be released or removed on every terminal path.
- Allow only one export preparation/picker flow at a time so result metadata and platform delegates cannot be overwritten.

## Verification

- Before calling a coding change complete, run a focused audit pass:
  - Load `docs/ai/audit.md` and apply it to the changed files and affected call paths.
  - If UI or Compose changed, also load `docs/ai/ui.md`.
  - If Room, repositories, sync, timestamps, date/time, or migrations changed, also load `docs/ai/data-sync-time.md`.
  - Treat unresolved audit findings as incomplete work unless the user explicitly accepts the gap.
- Run `./gradlew :composeApp:compileKotlinJvm` for normal changes.
- For shared domain, ViewModel, parser/import/export, sync, or settings changes, run `./gradlew :composeApp:jvmTest`.
- For broad shared-core changes, prefer `./gradlew :composeApp:allTests` when the local Kotlin/Native/iOS toolchain is available.
- For platform-sensitive changes, also run the relevant Android or iOS compile task.
- For broad shared changes, prefer `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinIosSimulatorArm64`.
- For coverage-sensitive changes, run the Kover report task in report-only mode and inspect the generated report before proposing threshold changes.
- Search changed code for `!!`, raw day millis such as `86400000`, hardcoded user-visible strings, and calendar filtering that removes completed tasks.
- After running Gradle, stop daemons with `./gradlew --stop`.
