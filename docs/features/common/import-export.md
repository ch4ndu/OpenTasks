# Import and Export

## Overview

OpenTasks can bring external data into tasks and export tasks for use elsewhere. Import/export is task-centered; notes and countdowns do not currently have dedicated export formats.

## Calendar Import

Calendar import reads events from the platform calendar after permission is granted. Imported events become tasks in the stable `Calendar Imports` category and receive the stable `Imported` tag.

`ImportCalendarEventsAction` skips duplicates using the event external id. Event metadata such as location, URL, organizer, status, attendees, all-day state, and time range is preserved on the task where supported by the task model.

## ICS Import and Export

ICS import parses calendar data and creates tasks through the same calendar import action path. ICS export generates calendar text from tasks and saves it with the platform file saver.

## CSV Import and Export

CSV import supports TickTick-style task CSV files. Parsed rows become tasks through `ImportCsvTasksAction`.

CSV export writes task data using `GenerateCsvExportAction` and the platform file saver.

## System Share Intake

System share intake supports text, URLs, and calendar payloads. Text and URLs prefill Create Task fields. ICS payloads route to the ICS import flow.

On Android, the main activity handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE`. Shared text becomes a task description, the first URL found in shared text fills the task URL field, and calendar MIME streams or raw ICS text route to ICS import.

On iOS, the Share Extension accepts text, URLs, and calendar event payloads. It opens the containing app with a custom `opentasks://share` URL, publishes a `SharedTaskPayload`, and the shared app flow either opens Create Task or imports ICS content.

The current deferred roadmap includes share-to-task images/files. Core task image attachment storage exists, but system share intake for images/documents is not wired yet.

## Related Docs

- [Tasks](../tasks.md)
- [Calendar](../calendar.md)
- [Settings](../settings.md)
- [Attachments](attachments.md)
- [Sync and Storage](sync-and-storage.md)
