# OpenTasks

A cross-platform task management app built with **Kotlin Multiplatform** and **Compose Multiplatform**, featuring Eisenhower Matrix prioritization to help you focus on what truly matters. Inspired by [TickTick](https://ticktick.com).

## Note
This project is built collaboratively with AI assistance (Claude Code). The code is reviewed, iterated on, and guided by me at every step — not auto-generated and dumped. Architecture decisions, feature design, and quality standards are human-driven; AI accelerates the implementation.

## Screenshots

<details>
<summary>View screenshots</summary>

<br>

**Core Screens**

<p>
  <img src="screenshots/matrix_dark.png" alt="Eisenhower Matrix" width="250">
  <img src="screenshots/tasklist_light.png" alt="Task List (Light)" width="250">
  <img src="screenshots/tasklist_dark.png" alt="Task List (Dark)" width="250">
  <img src="screenshots/notes_dark.png" alt="Notes" width="250">
  <img src="screenshots/settings_dark.png" alt="Settings" width="250">
</p>

**Calendar Views**

<p>
  <img src="screenshots/calendar_month_dark.png" alt="Month View" width="250">
  <img src="screenshots/calendar_week_light.png" alt="Week View (Light)" width="250">
  <img src="screenshots/calendar_week_dark.png" alt="Week View (Dark)" width="250">
  <img src="screenshots/three_day_light.png" alt="3-Day View (Light)" width="250">
  <img src="screenshots/three_day_dark.png" alt="3-Day View (Dark)" width="250">
  <img src="screenshots/calendar_year_light.png" alt="Year View (Light)" width="250">
  <img src="screenshots/calendar_year_dark.png" alt="Year View (Dark)" width="250">
  <img src="screenshots/calendar_options.png" alt="Calendar Options" width="250">
</p>

**Countdowns & Import**

<p>
  <img src="screenshots/countdown_dark.png" alt="Countdown" width="250">
  <img src="screenshots/create_countdown.png" alt="Create Countdown" width="250">
  <img src="screenshots/import_calendar.png" alt="Import Calendar" width="250">
  <img src="screenshots/import_ics.png" alt="Import ICS" width="250">
</p>

</details>

## Platforms

| Android | iOS | Desktop (JVM) |
|---------|-----|----------------|
| API 26+ (Android 8.0) | arm64 + Simulator | Linux, macOS, Windows |

## Documentation

See [docs/README.md](docs/README.md) for feature behavior and technical design notes.

## Features

### Task Management
- **Eisenhower Matrix** — Visualize tasks across four priority quadrants (Urgent & Important, Not Urgent & Important, Urgent & Unimportant, Not Urgent & Unimportant)
- **Task Lists** — Organize tasks into custom categories (default "Inbox" included)
- **Recurring Tasks** — Daily, weekly, monthly, yearly, or every weekday with configurable intervals
- **Reminders** — Configurable notifications (days/weeks/months before deadline), plus duration and date-based reminders
- **Tags** — Color-coded tags for flexible cross-category organization
- **Soft Deletes** — Non-destructive deletion with sync-safe tracking

### Calendar
- **6 Calendar Views** — Year, month, week, 3-day, day, and list views
- **Calendar Import** — Import events directly from your device calendar with configurable date range
- **ICS Import** — Choose and import standard `.ics` calendar files on Android, iOS, and Desktop
- **CSV Import** — Choose and import TickTick-format CSV files on Android, iOS, and Desktop
- **All-Day Events** — Full support for all-day and multi-day events

### Notes
- **Rich Notes** — Create, edit, and delete notes with title and content

### Countdowns
- **Event Countdowns** — Track days until (or since) important dates
- **Countdown Types** — Holiday, Birthday, Anniversary, and general Countdown
- **Count Up Mode** — Switch between counting down to a date or counting up from a past date
- **Smart List Visibility** — Configure when countdowns appear in the Countdowns tab (on the day, 3/7 days early, always, or hidden)
- **Countdown Reminders** — Schedule notifications at 9:00 AM local time on the effective date or configurable days before it
- **Recurring Countdowns** — Project the effective occurrence and continue reminders on daily, weekly, monthly, yearly, or weekday schedules

### Sync & Data
- **PocketBase Sync** — Optional self-hosted sync via [PocketBase](https://pocketbase.io) for backup and multi-device access (tasks, attachments, categories, notes, countdowns, tags, task-tag assignments)
- **Automatic Sync** — Syncs on app resume and after every write; manual sync available in Settings
- **Clear Local Data** — Reset option available in Settings
- **CSV / ICS Export** — Export all tasks to a user-chosen file on Android, iOS, and Desktop

### Platform & UI
- **Cross-Platform** — Android, iOS, and Desktop (JVM) from a single codebase
- **Material Design 3** — Modern theming with custom color palette and typography
- **Dark / Light / System Theme** — Configurable in Settings
- **Responsive Layout** — Adapts to compact, medium, and expanded screen sizes
- **Android Widgets** — Home screen widgets for quick task access

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Compose Multiplatform + Material 3 |
| State | Per-screen ViewModels with StateFlow |
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
./gradlew :androidApp:assembleDebug

# Desktop
./gradlew :composeApp:run
```

### Bundle a macOS Release

Run the release packaging task on macOS with JDK 17 or newer:

```bash
./gradlew :composeApp:packageReleaseDmg
```

The DMG is written to `composeApp/build/compose/binaries/main-release/dmg/`. The bundle targets the
architecture of the Mac and JDK used to build it. Its package name, version, icon, and release
ProGuard configuration come from `composeApp/build.gradle.kts`.

The current project configuration does not sign or notarize the DMG. Configure Apple code signing
and notarization before distributing it to users without macOS Gatekeeper warnings.

For **iOS**, open `iosApp/iosApp.xcodeproj` in Xcode and build the `iosApp` scheme.

## Project Structure

```
androidApp/src/main/
├── AndroidManifest.xml     # Application identity and Android components
├── kotlin/.../             # Launcher, receivers, sync worker, widgets
└── res/                    # App icons, strings, styles, and widget resources

composeApp/src/
├── commonMain/          # Shared UI, data, domain, DI
│   ├── kotlin/.../
│   │   ├── App.kt              # Entry point & navigation
│   │   ├── viewmodel/          # Per-screen ViewModels + AppViewModel
│   │   ├── data/
│   │   │   ├── model/          # Task, Category, Note, Countdown, Tag, etc.
│   │   │   ├── database/       # Room database & converters
│   │   │   ├── dao/            # Data access objects
│   │   │   ├── repository/     # Repository interfaces & implementations
│   │   │   ├── sync/           # PocketBase sync adapters & records
│   │   │   └── extensions/     # Date/time utilities
│   │   ├── domain/
│   │   │   ├── usecase/        # Read operations (task/, note/, settings/, etc.)
│   │   │   └── action/         # Write operations (task/, note/, settings/, etc.)
│   │   ├── di/                 # Koin modules
│   │   └── ui/
│   │       ├── screens/        # One composable per screen
│   │       │   └── calendar/   # Calendar view variants
│   │       └── theme/          # Colors, typography, dimensions
│   └── composeResources/       # Drawables, strings
├── androidMain/         # Android actual implementations and DB builder
├── iosMain/             # iOS DB builder, MainViewController
└── jvmMain/             # Desktop DB builder, main entry point
```

Platform-specific code uses Kotlin's `expect`/`actual` pattern and is kept to a minimum.

## Architecture

- **Repository pattern** wraps Room DAOs for data access
- **UseCase / Action pattern** — reads via UseCase classes (return `Flow`), writes via Action classes
- **Per-screen ViewModels** — `MatrixViewModel`, `TaskListViewModel`, `CalendarViewModel`, `NoteViewModel`, `CountdownViewModel`, plus `AppViewModel` for shared operations
- **UTC storage** — dates stored as UTC epoch millis in the database, converted to local time in the repository layer
- **Derived StateFlows** — filtering, sorting, and grouping happen in UseCases/ViewModels, not in composables
- **Strong skipping** — Compose compiler handles recomposition skipping; no manual `@Immutable` annotations needed
- **Auto-sync** — repositories trigger PocketBase sync on every write; `SyncService` uses DAOs directly and syncs with pull-before-push last-write-wins passes

## PocketBase Sync (Optional)

The app supports syncing tasks, attachments, categories, notes, countdowns, tags, and task-tag assignments to a self-hosted [PocketBase](https://pocketbase.io) server for backup and multi-device access. No authentication is required — collections use public API rules.

### Quick Start (Ubuntu)

```bash
# 1. Download PocketBase (replace version as needed)
wget https://github.com/pocketbase/pocketbase/releases/latest/download/pocketbase_0.27.2_linux_amd64.zip
unzip pocketbase_*.zip -d /opt/pocketbase

# 2. Copy the migration script next to the binary
cp -r pocketbase/pb_migrations /opt/pocketbase/pb_migrations

# 3. Start PocketBase — the migration creates all collections automatically
/opt/pocketbase/pocketbase serve --http=0.0.0.0:8090
```

On first launch PocketBase runs the scripts in `pb_migrations/`, which create the app collections with all required fields and indexes.

### Running as a Background Service (systemd)

To keep PocketBase running after you close the terminal and auto-start on boot:

```bash
# Create a dedicated user
sudo useradd -r -s /bin/false pocketbase

# Set ownership
sudo chown -R pocketbase:pocketbase /opt/pocketbase

# Create the service file
sudo tee /etc/systemd/system/pocketbase.service > /dev/null <<'EOF'
[Unit]
Description=PocketBase
After=network.target

[Service]
Type=simple
User=pocketbase
Group=pocketbase
WorkingDirectory=/opt/pocketbase
ExecStart=/opt/pocketbase/pocketbase serve --http=0.0.0.0:8090
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl enable --now pocketbase

# Check status
sudo systemctl status pocketbase
```

### Connecting the App

1. Open the app and go to **Settings**
2. Enter your PocketBase URL (e.g. `http://192.168.1.100:8090`)
3. Tap **Save** — the app syncs automatically on launch and after every write

### Migration Details

The included migrations (`pocketbase/pb_migrations/`) create the following collections:

**`tasks`** — localId, title, content, subtasks, priority, deadline, endDeadline, notifyBeforeValue, notifyBeforeUnit, recurrenceType, recurrenceInterval, status, isStarred, section, isUrgent, isImportant, categoryId, isAllDay, sourceExternalId, location, url, organizer, eventStatus, attendees, durationReminders, dateReminders, isDeleted, localCreatedAt, localUpdatedAt

`tasks.subtasks` stores a JSON array of `{ "id": "uuid", "text": "Call vendor", "isChecked": false }` entries for subtask-mode editor state.

**`categories`** — localId, name, icon, sortOrder, isDeleted, localCreatedAt, localUpdatedAt

**`notes`** — localId, title, content, isDeleted, localCreatedAt, localUpdatedAt

**`countdowns`** — localId, title, targetDate, countdownType, countingMode, reminders, recurrenceType, recurrenceInterval, recurrenceDaysOfWeek, smartListVisibility, isCompleted, isDeleted, localCreatedAt, localUpdatedAt

**`tags`** — localId, name, color, isDeleted, localCreatedAt, localUpdatedAt

**`task_tags`** — localId, taskId, tagId, isDeleted, localCreatedAt, localUpdatedAt

**`attachments`** — localId, ownerType, ownerId, kind, file, mimeType, fileName, fileSizeBytes, width, height, sortOrder, isDeleted, localCreatedAt, localUpdatedAt. Task images use `ownerType = "task"` and `kind = "image"`. Files are public-by-randomized URL, matching the app's public sync model.

Each collection has a unique index on `localId` for fast sync lookups. All API rules are left empty (public access) since the app doesn't use authentication.

Sync runs one collection at a time in this order: categories, tags, tasks, attachments, task_tags, notes, countdowns. Each collection pulls before pushing. Conflicts use last-write-wins by the app-managed `localUpdatedAt` timestamp; if a remote row is newer it overwrites local state, and if an unsynced local row is newer or equal it is pushed. Deletes are durable tombstones (`isDeleted = true`) rather than PocketBase hard deletes. A physically missing server row is treated as server damage/manual deletion and the synced active local row is marked unsynced so the next push recreates it. Device clock skew can make the wrong edit win.

### Manual Setup (without migration)

If you prefer to create collections manually, open the admin UI at `http://<your-server>:8090/_/` and create each collection above with base type, setting all API rules to empty.

### Backups

PocketBase stores everything in a single SQLite file at `pb_data/data.db`. Back this up regularly:

```bash
# Simple cron backup (daily at 2am)
echo '0 2 * * * cp /opt/pocketbase/pb_data/data.db /backups/pocketbase-$(date +\%F).db' | sudo crontab -u pocketbase -
```

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
