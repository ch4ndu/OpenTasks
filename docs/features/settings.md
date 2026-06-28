# Settings

## Overview

Settings centralizes app preferences, sync configuration, permissions, import/export entry points, and local data reset.

## User Flow

Users can:

- Configure the PocketBase URL or clear the saved URL.
- Trigger manual sync.
- Change theme mode.
- Change text size.
- Check notification and exact reminder permission status.
- Check calendar permission status.
- Start calendar, ICS, or CSV imports.
- Export tasks to CSV or ICS.
- Clear local app data.

## Technical Design

`SettingsViewModel` observes settings through settings use cases and writes through actions such as `SavePocketBaseUrlAction`, `ClearPocketBaseUrlAction`, `TriggerSyncAction`, `SaveThemePreferenceAction`, `SaveTextSizePreferenceAction`, and `ClearLocalDataAction`.

Settings values are stored in the `app_settings` Room table as key/value rows. Sync connection state is derived from the stored PocketBase URL and the `PocketBaseClientProvider`.

Saving a PocketBase URL is transactional from the user's perspective: the app creates a client, verifies server health, verifies all required collections, runs an initial sync, then saves the URL and swaps the active client. If verification or initial sync fails, the new URL is not persisted.

Import dialogs are opened from Settings but handled by dedicated import ViewModels and actions. Export uses `GenerateCsvExportAction`, `GenerateIcsExportAction`, and platform `FileSaver` implementations.

## Shared Capabilities

- [Sync and Storage](common/sync-and-storage.md) for PocketBase setup, sync behavior, migrations, and local data reset.
- [Import and Export](common/import-export.md) for calendar, ICS, CSV, and shared task intake.
- [Reminders](common/reminders.md) for notification and exact reminder permissions.

## Current Limitations

PocketBase sync uses public collection rules and is intended for trusted app instances against a self-hosted server. The app does not currently provide account-based multi-user auth.
