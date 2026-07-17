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
- Navigation3 task forms install the ViewModel-store entry decorator and resolve `TaskFormViewModel` with a create/edit entry key. Create and edit destinations, and edits for distinct task IDs, must never share draft or completion state.
- Date and Duration tabs both persist recurrence. Keep bottom-sheet state ownership in `DateReminderBottomSheet`; reusable sheet shells, date pickers, and reminder pickers receive state/callbacks rather than owning form state.
- Date editors receive their current civil day from the `LocalDaySignal` snapshot held by the app; do not read wall-clock date helpers directly during composition.
- Calendar composables receive `today` from `LocalDaySignal` through their ViewModel. Resolve page-level task data before cells, preserve unchanged per-day list identities, and use precomputed timeline display values rather than observing whole maps or wall-clock values in cells.
- Keep `LaunchedEffect` keys narrow and intentional.
- Use `LazyColumn` keys for scrollable lists, usually `key = { it.id }`. Do not wrap large repeated rows inside one lazy item with `Column { items.forEach { ... } }`; emit keyed `items(...)` so virtualization is preserved.

## Previews

- Put `@Preview` composables in `androidMain`, not `commonMain`.
- Previews should not call `kotlinx-datetime`; use preview sample data.
