# Settings

## Overview

Settings centralizes account controls, app preferences, sync status, permissions, and import/export entry points.

## User Flow

Users can:

- View the authenticated account and read-only PocketBase endpoint.
- Switch to the other pre-created account or log out.
- Trigger manual sync.
- Change theme mode.
- Change text size.
- Check notification and exact reminder permission status.
- Check calendar permission status.
- Start calendar, ICS, or CSV imports.
- Export tasks to CSV or ICS.

## Technical Design

`SettingsViewModel` observes installation preferences through settings use cases and writes through Actions such as `TriggerSyncAction`, `SaveThemePreferenceAction`, and `SaveTextSizePreferenceAction`. Account identity, switching, and logout are supplied by `AuthViewModel`, which uses account UseCases and Actions rather than repositories.

Installation settings are stored in the `app_settings` Room table as key/value rows. The authenticated cache binding and transition marker are durable account-state records; the PocketBase token is stored through the platform secure-token implementation, never in Room.

The endpoint is entered on the signed-out account screen and is read-only in Settings. A detached client authenticates and validates capability plus owner-scoped inventory before activation. Changing servers requires logout.

Import dialogs are opened from Settings but handled by dedicated import ViewModels and actions. Export uses `GenerateCsvExportAction`, `GenerateIcsExportAction`, and platform `FileSaver` implementations.

## Shared Capabilities

- [Sync and Storage](common/sync-and-storage.md) for PocketBase setup, sync behavior, migrations, and local data reset.
- [Import and Export](common/import-export.md) for calendar, ICS, CSV, and shared task intake.
- [Reminders](common/reminders.md) for notification and exact reminder permissions.

## Current Limitations

Account creation, invitations, password reset, account administration, shared tasks, and a discard-pending-data switch path are not part of the first multi-user release. Accounts must be created by the PocketBase operator.
