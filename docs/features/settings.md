# Settings

## Overview

Settings centralizes local/PocketBase account controls, app preferences, sync status, permissions, and import/export entry points.

## User Flow

Users can:

- Start and continue in Local only mode without configuring PocketBase.
- In Local only mode, connect to PocketBase through an explicitly destructive local-authoritative replacement or clear all local data.
- In PocketBase mode, view the authenticated account and read-only endpoint, switch to the other pre-created account, log out, or trigger manual sync.
- Change theme mode.
- Change text size.
- Check notification and exact reminder permission status.
- Check calendar permission status.
- Start calendar, ICS, or CSV imports.
- Export tasks to CSV or ICS.

## Technical Design

`SettingsViewModel` observes installation preferences through settings use cases and writes through Actions such as `TriggerSyncAction`, `SaveThemePreferenceAction`, and `SaveTextSizePreferenceAction`. Account identity, switching, and logout are supplied by `AuthViewModel`, which uses account UseCases and Actions rather than repositories.

Installation settings are stored in the `app_settings` Room table as key/value rows. The active cache binding records either `LOCAL_ONLY` or `POCKETBASE` mode plus its boundary epoch. Typed transition markers make local clear, account change, and local-authoritative replacement recoverable before task UI is remounted. PocketBase tokens use platform secure storage and passwords are never persisted.

The endpoint is entered on the signed-out account screen and is read-only after normal PocketBase authentication. A detached client authenticates and validates capability plus owner-scoped inventory before activation.

Connecting a nonempty local-only cache is deliberately different from normal login. Settings first shows a sanitized count-only preview for the destination owner. Confirmation re-reads both the complete local snapshot and the complete destination inventory under the shared mutation gate. Any change refreshes the preview and requires another confirmation; no remote deletion begins until the preview still matches. Once confirmed, the operation cannot be cancelled and task UI stays hidden until delete/reset/reseed verification succeeds or the user retries.

Local-only mode has no manual Sync action, connection-error status, or network pull-to-refresh. Clear Local Data writes a durable transition before resetting Room and separately records pending attachment-file cleanup, so process death cannot remount a cleared cache as active.

Import dialogs are opened from Settings but handled by dedicated import ViewModels and actions. Export uses `GenerateCsvExportAction`, `GenerateIcsExportAction`, and platform `FileSaver` implementations.

## Shared Capabilities

- [Sync and Storage](common/sync-and-storage.md) for PocketBase setup, sync behavior, migrations, and local data reset.
- [Import and Export](common/import-export.md) for calendar, ICS, CSV, and shared task intake.
- [Reminders](common/reminders.md) for notification and exact reminder permissions.

## Current Limitations

Account creation, invitations, password reset, account administration, shared tasks, and a discard-pending-data switch path are not supported. PocketBase accounts must be created by the operator. A confirmed local-authoritative replacement is intentionally destructive for the authenticated destination owner; it is not an account merge.
