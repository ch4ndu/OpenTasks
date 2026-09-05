# Import and Export

## Overview

OpenTasks can bring external data into tasks and export tasks for use elsewhere. Import/export is task-centered; notes and countdowns do not currently have dedicated export formats.

## Calendar Import

Calendar import reads events from the platform calendar after permission is granted. Imported events become tasks in the stable `Calendar Imports` category and receive the stable `Imported` tag.

On macOS, passive permission refreshes must not execute AppleScript because addressing the Calendar application activates it. OpenTasks queries Calendar only after the user explicitly starts a calendar import from Settings; macOS resolves any Automation consent at that point.

`ImportCalendarEventsAction` assigns a deterministic identity from the first 16 bytes of a platform SHA-256 digest to each occurrence. A source UID is paired with its occurrence token; UID-less rows use the complete normalized record plus a stable identical-row ordinal. Existing legacy external ids remain one-use compatibility aliases within each import batch, so a canonical match never consumes an alias and formerly colliding rows can import canonically. Event metadata such as location, URL, organizer, status, attendees, all-day state, and time range is preserved on the task where supported by the task model.

Platform calendar providers return at most 10,000 eligible events in deterministic order. A 10,001st event is a typed, localized overflow rather than a truncated import. Android converts all-day UTC civil dates to local civil starts and makes the exclusive end date inclusive. macOS keeps passive permission checks side-effect-free and uses a bounded, structurally validated JXA JSON response only after the explicit Import action.

## ICS Import and Export

ICS import parses calendar data and creates tasks through the same calendar import action path. ICS export generates calendar text from tasks and saves it with the platform file saver.

## CSV Import and Export

CSV import supports TickTick-style task CSV files. Header matching is locale-independent, and parsed rows become tasks through `ImportCsvTasksAction` with canonical SHA-256 identities and the same one-use legacy-alias compatibility rule.

CSV export writes task data using `GenerateCsvExportAction` and the platform file saver.

## System Share Intake

System share intake supports text, URLs, and calendar payloads. Text and URLs prefill Create Task fields. ICS payloads require explicit confirmation before the shared import path parses or persists them.

On Android, the main activity handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE`. Shared text becomes a task description, the first URL found in shared text fills the task URL field, and calendar MIME streams or raw ICS text route to ICS import.

On iOS, the Share Extension accepts text, URLs, and calendar event payloads. Save durably publishes one bounded, owner-only envelope to the `group.com.udnahc.opentasks` App Group, then reports that the item is saved for review; Cancel before Save publishes nothing. The flow does not depend on `extensionContext.open`, so the pending record remains when the containing app is closed or cannot be opened. Publish I/O or full-queue failures stay visible in the extension with Retry and Cancel.

The containing app scans on launch, foreground activation, a strict legacy `opentasks://share?nonce=<64-lowercase-hex>` signal, and every transition from blocked to ready. These signals coalesce through one serialized scanner. It discovers the oldest valid pending record by creation time then nonce, reserves a process ticket for the exact active, mounted account/epoch and readiness generation, and only then atomically claims/deletes the disk record. Backgrounding or another readiness change invalidates publication from an in-flight ticket, and stale publication is rejected rather than retargeted to a later account. The app admits one item at a time: text and URLs hold intake while Create Task is open, and ICS holds it through confirmation and import completion. An established review remains leased across ordinary background/resume. Signed-out, restoring, inactive, busy, or full states leave disk records pending. Legacy content-bearing share URLs and forged, duplicate, or additional query parameters remain ignored.

## Boundary limits and rejection

External data is bounded before parsing or navigation:

- File imports accept at most 5 MiB. Android `ContentResolver` metadata is only an early rejection; the bounded stream read remains authoritative. JVM and iOS file readers use the same limit.
- Android accepts at most eight share providers/URIs. The ninth item is rejected as `too_many_items`.
- A shared payload is at most 32 KiB in cumulative UTF-8 bytes across description, URL, ICS text, and filename; the ICS filename is at most 255 UTF-8 bytes.
- An iOS App Group envelope is at most 64 KiB. The pending queue is limited to 64 envelopes and 4 MiB, envelopes remain eligible for 24 hours, and timestamps more than 60 seconds in the future are rejected. A full queue rejects new publication and never evicts a fresh pending record. Temporary, pending, and claimed files are serialized under one native file lock and fail closed on unknown or unsafe entries; interrupted claims are retired rather than replayed.
- Malformed or unmappable UTF-8 is rejected as `invalid_utf8`, never replaced. Other typed failures are `too_large`, `too_many_items`, `invalid_file_type`, and `unreadable` where the boundary supports them. Rejections are carried through the one-shot event boundary and localized by the app; platform exception text is not shown.

Cancellation remains cancellation: bounded Android/iOS reads and shared ICS import do not publish an ordinary error or continue later work after the caller is cancelled. A newer Android intent cancels the previous bounded read and rejects any late result by generation/current-job checks.

The current deferred roadmap includes share-to-task images/files. Core task image attachment storage exists, but system share intake for images/documents is not wired yet.

## Related Docs

- [Tasks](../tasks.md)
- [Calendar](../calendar.md)
- [Settings](../settings.md)
- [Attachments](attachments.md)
- [Sync and Storage](sync-and-storage.md)
