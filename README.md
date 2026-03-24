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

## PocketBase Sync (Optional)

The app supports syncing tasks, categories, and notes to a self-hosted [PocketBase](https://pocketbase.io) server for backup and multi-device access. No authentication is required — collections use public API rules.

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

On first launch PocketBase runs `pb_migrations/001_create_collections.js`, which creates the `tasks`, `categories`, and `notes` collections with all required fields and indexes.

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

The included migration (`pocketbase/pb_migrations/001_create_collections.js`) creates three collections:

**`tasks`** — localId, title, content, priority, deadline, notifyBeforeValue, notifyBeforeUnit, recurrenceType, recurrenceInterval, isCompleted, isUrgent, isImportant, categoryId, isAllDay, sourceExternalId, location, url, organizer, eventStatus, attendees, durationReminders, dateReminders, isDeleted, localCreatedAt, localUpdatedAt

**`categories`** — localId, name, icon, sortOrder, isDeleted, localCreatedAt

**`notes`** — localId, title, content, isDeleted, localCreatedAt, localUpdatedAt

Each collection has a unique index on `localId` for fast sync lookups. All API rules are left empty (public access) since the app doesn't use authentication.

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
