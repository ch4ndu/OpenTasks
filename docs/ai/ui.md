# UI And Compose Rules

Load this for Compose UI, screens, bottom sheets, previews, theme work, or recomposition-sensitive changes.

## Theme And Resources

- Use Material3 with the custom theme in `ui/theme/`.
- App typography is controlled by `OpenTasksTheme`: window size selects the responsive base typography, then the persisted user text-size preference applies an additional scale.
- Use `OpenTasksTheme.dimens` for `dp` values, except 0-2dp inline spacing and preview containers.
- Use `stringResource()` for all user-visible strings.
- Use icons from `composeResources/drawable/`.
- Keep UI text, spacing, and behavior consistent with existing screens.

## Layout

- Bottom `NavigationBar` and top app bars overlay content with translucent alpha. Screens must pad for them.
- Prefer dense, functional task-management UI over decorative layouts.
- Text must fit its container on mobile and desktop.
- Do not add nested cards or unrelated decorative surfaces.

## Interaction Targets

- Ordinary actions use the shared 48dp minimum interactive container. Keep that container token separate from icon, checkbox, indicator, and other visual-size tokens so accessibility fixes do not enlarge glyphs.
- Preserve `CalendarTaskRows` geometry. The two dense inline completion controls used by Day and Three-Day timelines are approved compact measured/semantic exceptions; they rely only on framework-expanded touch hit bounds and must not grow the surrounding timeline rows.
- A seven-column `SelectableDayGrid` cannot guarantee 48dp measured/semantic cells when its content width is below 336dp. Compact date pickers retain their seven-column geometry and rely only on framework-expanded touch hit bounds; removing this narrow-layout residual requires a separately approved picker redesign.

## Composable Architecture

- One composable should represent one UI component.
- Screens and bottom sheets should extract inner content into a separate content composable that receives state and callbacks.
- Before creating a reusable component, check `SharedComposables.kt`, `calendar/CalendarComposables.kt`, and `calendar/CalendarTaskRows.kt`.
- If a pattern appears in two or more screens, extract or reuse a shared composable.

## State And Performance

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
- `QuickAddTaskViewModel` owns parsing, dismissed-token signatures, validation, save/error state, and stale/duplicate-save protection. `QuickAddTaskScreen` renders only the title field, active removable chips, Back/Add/IME Add, progress, and save error; do not move parsing or date math into composition or persist the draft in the navigation key.
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
