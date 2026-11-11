# UI And Compose Rules

Load this for Compose UI, screens, bottom sheets, previews, theme work, or recomposition-sensitive changes.

## Theme And Resources

- Use Material3 with the custom theme in `ui/theme/`.
- App typography is controlled by `OpenTasksTheme`: window size selects the responsive base typography, then the persisted user text-size preference applies an additional scale. Reimagined app-bar titles are the exception: use the extended `appBarTitle` token based on Material TitleLarge (22sp/28sp regular), scaled by the user preference but not by viewport size. The Calendar date title uses a 14sp base size with proportionally scaled line height and the same user text-size preference, while retaining the same toolbar geometry.
- Use `OpenTasksTheme.dimens` for `dp` values, except 0-2dp inline spacing and preview containers.
- Use `stringResource()` for all user-visible strings.
- Use icons from `composeResources/drawable/`.
- Keep UI text, spacing, and behavior consistent with existing screens.

## Layout

- Classic bottom `NavigationBar` and top app bars overlay content with translucent alpha. Screens must pad for them. Reimagined uses bottom navigation on compact windows and a rail on wider eligible tab routes; its content must account for that rail and safe areas.
- Reimagined topbars use the shared `OpenTasksTopBar` Material small bar: 64dp content height from the library default, opaque `colorScheme.background`, and one status inset owner. Root tabs omit static tab titles; Calendar retains its single-line date dropdown, and detail/workflow screens retain contextual titles. Settings is the last action where present. Keep search and editor actions in the same bar shell; the editable note title remains body content.
- Place Reimagined root bars above content and refresh indicators. The Notes editor parent retains its status inset; its bar takes only horizontal safe insets. Other shared bars own their normal default insets. Do not change Classic overlay padding, colors, or responsive dimensions when standardizing Reimagined bars.
- Prefer dense, functional task-management UI over decorative layouts.
- Text must fit its container on mobile and desktop.
- Do not add nested cards or unrelated decorative surfaces.
- The signed-out Welcome screen uses a stacked brand/illustration/form at compact and medium widths, and a split welcome/form panel at expanded widths. Its artwork and controls use the active Classic or Reimagined color scheme in both light and dark mode. Keep credential state above responsive layout branches, passwords non-saveable, and sign-in/local-use actions on the existing account boundary flow. The entry surface owns safe drawing and keyboard insets and scrolls when content cannot fit.

## Interaction Targets

- Opaque Material `Surface` overlays already block hit testing to covered siblings. Do not add ancestor pointer handlers that consume every event: they cancel presses and gestures in interactive descendants.
- Ordinary actions use the shared 48dp minimum interactive container. Keep that container token separate from icon, checkbox, indicator, and other visual-size tokens so accessibility fixes do not enlarge glyphs.
- Preserve compact Eisenhower Matrix task rows. Their inline completion square uses `checkboxSize` as a measured/semantic size exception, with checkbox state and a localized action label; rely on framework-expanded touch hit bounds without adding a 48dp layout container that widens or raises the row.
- Reimagined Week overflow uses a compact button-role trigger in the existing overflow slot, with a localized full-day item-count description and the shared full-day dialog. This is a compact measured-height exception; preserve the dense grid. Month cell selection opens the full selected-day agenda at both widths.
- Preserve `CalendarTaskRows` geometry. The two dense inline completion controls used by Day and Three-Day timelines are approved compact measured/semantic exceptions; they rely only on framework-expanded touch hit bounds and must not grow the surrounding timeline rows.
- A seven-column `SelectableDayGrid` cannot guarantee 48dp measured/semantic cells when its content width is below 336dp. Compact date pickers retain their seven-column geometry and rely only on framework-expanded touch hit bounds; removing this narrow-layout residual requires a separately approved picker redesign.

## Composable Architecture

- One composable should represent one UI component.
- Screens and bottom sheets should extract inner content into a separate content composable that receives state and callbacks.
- Before creating a reusable component, check `SharedComposables.kt`, `calendar/CalendarComposables.kt`, and `calendar/CalendarTaskRows.kt`.
- If a pattern appears in two or more screens, extract or reuse a shared composable.

## State And Performance

- `UiExperience` is an installation preference with a Classic fallback. Wait asynchronously for its initial value before mounting the UI, keeping auth restoration and account-boundary effects active. Only initial observation failure may fall back; later failures retain the last experience. Keep Classic and Reimagined on the same account-keyed navigation subtree, ViewModels and domain actions. The rail overlays the existing parent and adjusts padding; changing experience or window size must not re-parent `NavDisplay` or bypass editor exit guards.
- Reimagined Notes owns its draft above width/focus layout branches, keyed by the existing note request token. Reuse App's create/edit flags for share-intake readiness. Save results must match that request, draft baselines must survive create-to-edit transitions, and the note overlay must not replace an underlying task editor's back-handler owner.
- Reimagined Notes applies each successful save revision to its draft baseline once, retaining the applied revision in request-owned saveable state. Restoring an earlier successful revision must not acknowledge a later failed or pending save. Collect workspace library and linked-task data only inside their visible panels; the obscured root Notes library retains its local UI state without collecting data until the workspace closes.
- Keep Classic Matrix's six-row projection separate from Reimagined's seven-row preview and due-label projection. Both include all lists and completed tasks. Full quadrant due labels must cover every task, not only preview rows. Collect each interface's projections only while that branch is active.
- Reimagined Calendar supplies bounded civil viewport windows to the existing projection owner. Expand multi-day intervals only inside those windows, exclude midnight ending days, preserve original task identities, and clip continuation labels/geometry outside composition. Year computes day keys without formatting rows. Notes navigation outside focus mode must go through the same dirty/busy guard as editor Back; countdown platform Back must respect in-flight mutations.

- Strong skipping is enabled; do not add `@Immutable` or `@Stable`.
- Do not transform data in composables. Filtering, sorting, mapping, and grouping belong in UseCases or ViewModels.
- Do not compute per-row display values in composition (HTML stripping, day-count/date math, text truncation). Precompute them in a ViewModel row projection so scrolling and unrelated recomposition do not repeat the work.
- Task list, board, matrix, and quadrant rows consume ViewModel-provided due-text maps keyed by task ID; do not format due labels during composition.
- Pass `StateFlow` to children when useful and collect at the lowest practical scope.
- Collect mode-specific flows only inside the active UI branch. List-only projections should not stay subscribed while a board/calendar mode is visible, and board-only projections should not stay subscribed while list mode is visible.
- Screens should consume ViewModel-provided projections or keyed lookup maps instead of filtering full task lists in composables.
- Countdown list/detail/calendar UI consumes effective-occurrence projections; do not recompute recurrence, visibility windows, or day counts in composition.
- Task row HTML previews are precomputed into ViewModel lookup projections; row composables receive plain preview text and must not strip HTML during composition.
- The `Screen.EditTask` navigation entry owns recurring-DONE dialog presentation and consumes durable form-save results once. `TaskFormViewModel` retains the pending choice and proposed form data; `CreateTaskScreen` uses that data only to seed a new editor composition keyed by task ID, never to overwrite a live editor.
- The task form keeps its complete draft in entry-keyed saveable state. Ordinary saves preserve TODO or IN_PROGRESS; DONE follows the existing recurring-completion decision, and turning completion off restores the entry's original incomplete status. Title-only edits preserve valid start/end instants including seconds, milliseconds, overnight ranges, and imported all-day exclusive ends. Moving the start day shifts the end by the same civil-day delta, explicit time edits preserve the established span, clearing the date clears the end, and an end before start is rejected visibly before persistence.
- The active Navigation subtree is shared by valid local-only and authenticated sessions and remains keyed by `CacheBinding.boundaryEpoch`. Transitioning or invalid boundary state must unmount it. Network pull-to-refresh and sync progress/success presentation are installed only for authenticated PocketBase mode.
- Matrix, Task List, Calendar, and Quadrant Detail task FABs use the shared creation chooser. Quick Add and the full editor must receive the same category, priority, and optional Calendar civil date; Notes and Countdowns retain their direct creation behavior.
- `QuickAddTaskViewModel` owns parsing, dismissed-token signatures, validation, save/error state, and stale/duplicate-save protection. `QuickAddTaskScreen` renders the title field, active removable chips, Back/Add/IME Add, progress, and save error. Reimagined can transfer the current parsed values to the existing full-editor initializer without saving; that destination then owns its saveable draft. Keep this transfer in the navigation event callback; do not move parsing or date math into composition or persist the draft in the navigation key.
- Navigation3 task forms install the ViewModel-store entry decorator and resolve `TaskFormViewModel` with a create/edit entry key. Create and edit destinations, and edits for distinct task IDs, must never share draft or completion state.
- Date and Duration tabs both persist recurrence. Keep bottom-sheet state ownership in `DateReminderBottomSheet`; reusable sheet shells, date pickers, and reminder pickers receive state/callbacks rather than owning form state.
- Date editors receive their current civil day from the `LocalDaySignal` snapshot held by the app; do not read wall-clock date helpers directly during composition.
- Calendar composables collect one mode-aware render state whose selected mode, `today`, day keys, required day/selection projections, and hour labels come from the same source snapshot. UI branching must use that snapshot's mode so a preference or widget-navigation change cannot render new-mode content with old-mode projections. YEAR/key-only mode must not subscribe to or format row projections.
- Calendar row/day projections carry the original task plus only UI-consumed formatted fields, fixed preview prefixes, and overflow counts. Compare raw merged task/countdown inputs per day before sorting or formatting, reuse unchanged day and row identities, and invalidate removed days. Today-only rollover changes reuse rows/formatted fields while moving countdowns and both old/new `isToday` flags in the first emitted snapshot. Build one 24-hour label set per formatting-context key, including an empty selected day. A month/week layout may use one pure helper, memoized by projected-row identity and measured limit, to select a dynamic visible prefix. It must not format, sort, group, map models, or send pixel constraints back to the ViewModel.
- Native iOS share intake is ready only while the app is active, one active-cache navigation subtree is mounted, and editors, Quick Add, notes/task modals, import dialogs, and an existing share review are idle. Resigning active invalidates in-flight intake tickets while preserving an established review lease. Claiming a task or ICS item must acquire the review lease in the same atomic update that removes the queue head; release it only after task-editor exit or ICS dismissal/cancellation/terminal-result consumption.
- Keep `LaunchedEffect` keys narrow and intentional.
- Use `LazyColumn` keys for scrollable lists, usually `key = { it.id }`. Do not wrap large repeated rows inside one lazy item with `Column { items.forEach { ... } }`; emit keyed `items(...)` so virtualization is preserved.

## Previews

- Put `@Preview` composables in `androidMain`, not `commonMain`.
- Previews should not call `kotlinx-datetime`; use preview sample data.

## Review, logbook, bulk actions, and note links

- Weekly review, logbook, bulk selection, and note-task destinations own separate entry ViewModels. Projections and date filtering run off the UI thread; task/history lists remain lazy with stable IDs.
- Workflow date labels observe formatting-context changes independently of civil-day changes. Bulk selection combines selected IDs with already-projected rows, so a checkbox change does not repeat sorting or date formatting.
- Weekly review's Done requests DONE explicitly; it never toggles a completed task open. Pending status requests disable the row actions, and recurring choice admission must preserve an existing choice during confirmation.
- Bulk batches guard toolbar, platform, and navigation Back while busy. Keep the entry alive for the result; cancellation from other lifecycle changes still preserves already committed rows.
- The note-to-linked-tasks action saves the open draft and navigates only on the matching successful note operation. A plain Save or Delete clears a pending linked-task navigation request. Source-note navigation from a task retains that task editor entry and its draft.
- All workflow destinations count as busy for shared intake, preventing a system share from replacing an active review or batch-selection flow.

## Audit repair contracts

- Quick Add token dismissal must respect saving/saved state in the ViewModel and both UI experiences. Countdown form fields, pickers, and Back use the same busy guard in Classic and Reimagined.
- A remote note disappearance must retain an open draft until an explicit decision. Distinguish initial loading from a completed absent lookup so restored drafts recover even when deletion precedes the first observation. While that lookup is unresolved, both experiences own Back/navigation and require explicit discard before retiring the request; do not initialize empty editor savers during loading. Structurally key the editor and Classic dialog's saveable composition by the persisted request token: `rememberSaveable` inputs alone do not validate restored ownership. Keep that token through successful create/recovery-to-edit transitions, and change it when retiring or replacing a request so unconsumed restored values cannot seed another editor. Saving a missing note creates a fresh note identity; a stale update cannot resurrect a deleted row. Both editors show durable inline mutation errors, and consuming an operation result must not cancel its feedback.
- Completing a recurring occurrence after editing its schedule advances from the accepted edited deadline. The original expected occurrence remains the stale-write guard and completion-history identity.
- Display text projections compare content/deadline inputs before expensive formatting and independently invalidate on formatter-context changes. This includes both Matrix overview experiences and selected-quadrant/list/board labels. Notes cache previews by content and date labels by timestamp/context per note ID, evict removed notes, and retain the current Note for callbacks on metadata-only updates. Do not suppress sync bookkeeping globally in repositories; current entities remain available to mutation callbacks.
- Calendar overlap lanes use the same minimum display duration as timeline bars, while retaining actual event endpoints for labels and editing. Reimagined timelines and synchronized hour columns include enough measured trailing space to reach a minimum-height event starting at 23:59. Metadata-only updates may reuse formatted projections but must refresh their Task references.
- Classic and Reimagined Calendar, Matrix, and Countdown surfaces expose local read failures and invoke the ViewModel's local retry; a network refresh is not a substitute for that retry. Matrix quadrant detail also exposes task read failures in both experiences; only its Classic list branch depends on category readiness. Calendar category readiness is required only in the modes/experience that collect category names, using the same predicate for collection and readiness.
- Workflow and Task List local-read readiness includes every source required by the visible content. Loading or failure must not imply an empty list or missing record. Task List attachment readiness and collection belong only to list mode; keep the mode control usable during a list-only failure.
- Countdown detail/edit retries retain the last successful same-ID lookup. Keep an already-loaded editor mounted through error and retry so its draft is not reseeded. Read status and mutation availability are separate from mutation busy state; local read failure exposes Retry and permits Back. Only a successful absent lookup means Missing.

## Notes organization controls

- Both Notes experiences use the same full-content search/filter projections. HTML decoding, excerpts, tag names, note relationships, sorting, and civil-day filters belong outside composition. The card preview must never be the search corpus.
- Note organization belongs to the request-owned saveable draft and its successful-save baseline. Save, Save and open linked tasks, and Save as new carry the same organization. Following a note link uses the dirty/busy guard. Relationship data is collected only while its dialog is visible; focus mode and covered libraries do not observe hidden panels.
