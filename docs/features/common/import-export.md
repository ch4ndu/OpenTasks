# Import and Export

## Overview

OpenTasks can bring external data into tasks and export tasks for use elsewhere. Import/export is task-centered; notes and countdowns do not currently have dedicated export formats.

## Calendar Import

Calendar import reads events from the platform calendar after permission is granted. Imported events become tasks in the stable `Calendar Imports` category and receive the stable `Imported` tag.

On macOS, passive permission refreshes must not execute AppleScript because addressing the Calendar application activates it. OpenTasks queries Calendar only after the user explicitly starts a calendar import from Settings; macOS resolves any Automation consent at that point.

`ImportCalendarEventsAction` skips duplicates using the event external id. Event metadata such as location, URL, organizer, status, attendees, all-day state, and time range is preserved on the task where supported by the task model.

## ICS Import and Export

ICS import parses calendar data and creates tasks through the same calendar import action path. ICS export generates calendar text from tasks and saves it with the platform file saver.

## CSV Import and Export

CSV import supports TickTick-style task CSV files. Parsed rows become tasks through `ImportCsvTasksAction`.

CSV export writes task data using `GenerateCsvExportAction` and the platform file saver.

## System Share Intake

System share intake supports text, URLs, and calendar payloads. Text and URLs prefill Create Task fields. ICS payloads route to the ICS import flow.

On Android, the main activity handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE`. Shared text becomes a task description, the first URL found in shared text fills the task URL field, and calendar MIME streams or raw ICS text route to ICS import.

On iOS, the Share Extension accepts text, URLs, and calendar event payloads. It opens the containing app with the custom `opentasks://share` URL and these query names: `description`, `url`, `ics`, `icsFileName`, or typed `error` values. The receiver validates the encoded URL and decoded payload again before publishing a `SharedTaskPayload`; it either opens Create Task or imports ICS content. The extension keeps its UI visible and shows local failure feedback when opening the containing app is rejected. This transport deliberately uses the custom URL only; there is no App Group handoff or shared-container dependency.

## Boundary limits and rejection

External data is bounded before parsing or navigation:

- File imports accept at most 5 MiB. Android `ContentResolver` metadata is only an early rejection; the bounded stream read remains authoritative. JVM and iOS file readers use the same limit.
- Android accepts at most eight share providers/URIs. The ninth item is rejected as `too_many_items`.
- A shared payload is at most 32 KiB in cumulative UTF-8 bytes across description, URL, ICS text, and filename; the ICS filename is at most 255 UTF-8 bytes. The iOS custom URL, including percent encoding, is at most 64 KiB.
- Malformed or unmappable UTF-8 is rejected as `invalid_utf8`, never replaced. Other typed failures are `too_large`, `too_many_items`, `invalid_file_type`, and `unreadable` where the boundary supports them. Rejections are carried through the one-shot event boundary and localized by the app; platform exception text is not shown.

Cancellation remains cancellation: bounded Android/iOS reads and shared ICS import do not publish an ordinary error or continue later work after the caller is cancelled. A newer Android intent cancels the previous bounded read and rejects any late result by generation/current-job checks.

The current deferred roadmap includes share-to-task images/files. Core task image attachment storage exists, but system share intake for images/documents is not wired yet.

## Related Docs

- [Tasks](../tasks.md)
- [Calendar](../calendar.md)
- [Settings](../settings.md)
- [Attachments](attachments.md)
- [Sync and Storage](sync-and-storage.md)
