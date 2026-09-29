# Tasks overview and Shortlist

Date: 2026-09-29
Status: Design and implementation plan; Shortlist decisions below remain open.
Source baseline inspected: `a71f332`; re-check the live tree before implementation.
Authorization for this turn: write this plan only. No implementation, commit, release, or server migration is authorized by this document.

## Objective and accepted direction

Make the Tasks tab's existing capabilities discoverable using Concept A, then add the proposed Shortlist extension as a separately scoped phase. The user selected Concept A and the name **Shortlist**. Do not use My Day in the product.

The earlier implementation handoff covers Concept A only and explicitly excludes Shortlist. This document collects both designs for the next handoff; it does not silently expand work already assigned to another agent.

Retained visual references, relative to the repository root:

- `.local/design/tasks-tab/a-task-overview.png`
- `.local/design/tasks-tab/a-shortlist-extension.png`
- `.local/design/tasks-tab/a-shortlist-extension.md` (notes and generation prompt)

These assets are intentionally Git-ignored by `.local/`. They must remain local; do not force-add them. An agent working in another checkout needs access to these files. The written requirements below take precedence over illustrative text, counts, dates, and optional elements in the images.

## Current implementation anchors

Paths below are under `composeApp/src/commonMain/kotlin/com/udnahc/opentasks/` unless stated otherwise.

| Area | Current anchor | Implication |
| --- | --- | --- |
| Navigation | `App.kt`, `Screen.TaskList` entry | Tasks currently opens the existing task-list screen. Preserve edit/create return paths and external entry points. |
| List UI | `ui/screens/TaskListScreen.kt` | Reuse existing list/board content and task actions. |
| Hidden navigation | `ui/screens/CategoryPickerBottomSheet.kt` | Existing smart views and categories are selected here. |
| Filters | `data/model/TaskListFilter.kt`, `viewmodel/TaskListViewModel.kt` | Six smart filters already exist; no Shortlist filter exists in the inspected tree. |
| Today | `domain/usecase/task/ObserveTodayTasksUseCase.kt` | Includes overdue, dated-today, and completed-today tasks. |
| Task persistence | `data/model/Task.kt`, `data/database/AppDatabase.kt` | No shortlist membership exists. Database version was 14 at inspection; never assume that remains current. |
| Sync | `data/sync/records/TaskRecord.kt`, `data/sync/adapters/TaskSyncAdapter.kt` | New durable state requires complete wire, canonical comparison, migration, and compatibility treatment. |
| Completion | `domain/action/task/TaskCompletionHandler.kt`, `ToggleTaskCompleteAction.kt`, `TaskWriteCoordinator` | Reuse existing guarded writes and occurrence/series decisions. |

Preserve current filter boundaries, including Due this week's current rolling seven-day window. Do not reinterpret labels as an unrelated behavior change.

## Phase A: Tasks overview

### Navigation and layout

- Entering Tasks from another main tab opens the overview.
- Show Tasks heading, Settings access, and a two-column phone layout for Today, Overdue, Starred, No date, High priority, and Due this week.
- Every shortcut has a readable label, icon, live unfinished-task count, and clear tap affordance.
- Show Your lists below the shortcuts: Inbox and custom categories, with icons, counts, and navigation affordances.
- Provide New list through the existing category creation flow.
- Preserve the existing main navigation and expose a labeled Add task action.
- Omit Recently opened. It appears in the original image but was excluded from the handoff.
- Adapt spacing and width to desktop/tablet without substituting the rejected B/C layouts.

### Selected view or category

- A tile/list opens the selected task content inside Tasks.
- Show its title and an All views back action. System/app Back also returns to the overview.
- Editing or creating a task and returning restores that selected destination and its relevant view state.
- Preserve widget/notification/deep-link intent: an explicitly targeted task or category must not be intercepted by the overview. Inspect current routing before changing it.
- Expose labeled List / Board and Sort controls.
- Preserve starring, editing, task previews, attachments/subtasks indicators, completion, recurrence choices, and existing grouping.
- Preserve date-driven Today behavior. Shortlist is a separate selection, not a replacement for Today.

### Counts and creation

- Count unfinished, non-deleted tasks matching each destination. Count IN_PROGRESS as unfinished.
- Today count includes overdue plus dated-today unfinished tasks, excluding completed-today tasks.
- Starred/category counts exclude completed tasks even where the destination also displays a completed section.
- Reuse a shared domain projection/predicate where required to keep tile counts and destination filtering consistent; do not duplicate filtering in composables.
- Update counts after relevant local and remote changes and local-day rollover.
- Add task retains the existing Quick Add/full-editor chooser. Overview defaults to Inbox; category destinations retain their category.
- Inspect and preserve current smart-filter creation defaults. Do not infer new dates, priorities, or membership from a selected filter without a defined contract.

## Phase B: Shortlist proposal

This phase is planned, not implementation-approved by the earlier handoff. The name and retained visual direction are accepted; the behavioral recommendations below need to be settled before data-model work.

### Proposed experience

- Add a full-width Shortlist card above the six existing smart views, with a checklist icon, subtitle "Tasks you've chosen to tackle", and live remaining count.
- Shortlist is an explicit, persistent selection of existing tasks. It does not reset at midnight or change deadlines, reminders, category, star, or priority.
- Screen: All views, Shortlist title/subtitle, remaining count, Choose tasks, To tackle section, and collapsed Completed section.
- Completion checkboxes complete the real task through the existing flow. The row menu's Remove from Shortlist removes membership only; it never deletes the task.
- Choose tasks opens a sheet with Suggestions and All tasks. Suggestions are deterministic groups using existing data: Important / Not Urgent, due today, and undated tasks. No AI or network service.
- Suggestions do not auto-select tasks. Use labeled + Add controls, distinguish them from completion checkboxes, and prevent duplicate additions.
- All tasks lists eligible unfinished tasks across categories. Already selected tasks must be clearly marked or excluded; completed/deleted tasks cannot be newly selected.
- Important / Not Urgent must use the app's canonical quadrant mapping, not assume High priority means the same thing.
- Suggested tasks may match multiple groups; show each task once, with documented deterministic precedence.
- Display a quiet Due & overdue link showing the count of unfinished overdue/dated-today tasks outside Shortlist. Count each task once and navigate to existing Today.
- Empty Shortlist: "Choose a few tasks to tackle next." Keep Choose tasks prominent.
- Expose Add to Shortlist / Remove from Shortlist from existing task details or task menus in list and Matrix paths where those surfaces permit it. Avoid inventing an unrelated menu system.
- Keep the existing creation chooser separate from selection of existing tasks. Do not silently auto-shortlist every newly created task.

### Decisions to resolve before Phase B implementation

| Decision | Recommended bounded direction | Why it matters |
| --- | --- | --- |
| Lifetime | Persistent until completed or removed; no daily reset | This was recommended with the name change, not separately confirmed as a behavior contract. |
| Completed section and undo | Retain selection on ordinary completed tasks so they appear in collapsed Completed; reopening restores active selection; explicit removal clears it | Clearing every membership on completion cannot produce the mockup's completed section without additional history storage. Avoid a new general history system. |
| Recurring occurrence | Remove the completed occurrence from active Shortlist; do not automatically select the advanced occurrence | Existing completion can advance a task instead of leaving it DONE. Decide whether recurring completion is omitted from Shortlist history in the first version; do not silently build occurrence-history infrastructure. |
| Ordering | Reuse an existing sort with a stable tie-breaker; no drag ordering for the first version | The user described prioritized work, but manual ranking was not requested or agreed. Explicitly confirm if ranking is essential. |
| Persistence and sync | Account-owned durable state, available offline and synchronized with optional PocketBase | A device-only preference would behave differently across platforms. Decide before schema changes. |
| Data shape | Prefer the smallest representation that supports the approved semantics; evaluate a task membership field before a separate relation/history table | Recurrence/history and conflict semantics must determine the schema, not the screenshot. Do not treat a particular field or migration as approved here. |

### Resolved decisions (user, 2026-09-29)

| Decision | Resolution |
| --- | --- |
| Lifetime | Persistent, no daily reset. Membership lasts until the task is completed or explicitly removed. |
| Completion | Completing a task clears its membership. There is no Completed section in Shortlist; reopening a completed task does not restore membership. |
| Recurring occurrence | Advancing a recurring task clears membership; the next occurrence is not selected. |
| Ordering | Reuse the existing task sort option with a stable tie-breaker; no manual ranking. |
| Persistence and sync | A task field stored in Room and synced through TaskRecord/PocketBase; the server migration is prepared but deployed separately. |
| Scope | Implement Phase A, then Phase B, pausing for a commit between them. |

Record answers in this file. Phase A can be prepared independently; do not implement the unresolved Phase B choices by treating elapsed time or this plan as approval.

## Implementation sequence

1. **Reconcile live state and finalize contracts.** Read AGENTS.md and relevant `docs/ai/*`; inspect any ongoing Concept A implementation before touching files. Resolve only material Phase B questions. Verify every referenced source path against the live tree.
2. **Overview navigation and projections.** Modify shared navigation, domain reads, ViewModel state and DI as needed. Add overview and selected-view transitions while reusing task rendering. Verify all tiles/categories, counts, back navigation and edit/create returns.
3. **Overview visual pass.** Apply Concept A with theme dimensions, strings and icons. Verify phone/wide layouts, dark/light themes, font scaling, empty states and many categories. Use lazy content and accessible controls.
4. **Shortlist persistence, after decisions.** Implement the approved schema, explicit non-destructive Room migration, repository writes and Actions. Route through current account-bound mutation and task-write coordination. Preserve unrelated task edits on stale callbacks; deletion hides membership. Define recurrence and undo atomically.
5. **Shortlist sync integration.** Cover TaskRecord/adapter or approved relation, full payload serialization including false/cleared values, canonical comparisons, acknowledgements, seed/connect inventories, account clearing and schema migrations. Evaluate older-client/server compatibility explicitly; do not deploy a server migration as part of UI work. Verify local-only operation requires no backend.
6. **Shortlist UI and entry actions.** Implement card, remaining/completed projections, selection sheet, suggestions, add/remove actions and Today link in shared UI. Show truthful write failures and prevent repeated in-flight actions; preserve cancellation and boundary rejection behavior. Verify offline/restart and account-switch paths.
7. **Focused audit and handoff.** Audit changed code and affected paths against architecture/UI/data rules. Update the relevant feature docs and durable AI guidance only where contracts actually change. Report unresolved findings as incomplete work.

## Verification and acceptance

The user verifies behavior manually (2026-09-29). Do not add new tests or verification harnesses; only update existing tests that no longer compile or no longer describe the changed contract. The table below is the manual checklist.

| Area | Required verification |
| --- | --- |
| Overview | All six views and custom lists reachable; New list works; live counts follow completion, star, category/date edits, deletion, remote updates and day rollover. |
| Navigation | All views/Back returns to overview; edit/create returns to selected view; explicit external entry intent survives; no accidental back-stack duplication. |
| Existing behavior | List/Board, Sort, Quick Add/full editor, starring and recurring occurrence/series decisions remain wired. |
| Selection | Add/remove is idempotent; selection preserves task fields; removal never deletes; suggestions deduplicate; remaining/outside counts are exact. |
| Lifecycle | Restart retains selection; no midnight reset under the proposed contract; offline use works; account switch/clear/replacement exposes no stale tasks or counts. |
| Completion | Ordinary completion/undo and recurring advancement match the resolved policy, including completion from other screens or remote sync. |
| Data compatibility | Upgrade migration preserves existing tasks; legacy/default membership, clearing false values, full sync round-trip and conflict handling are checked against approved rollout policy. |
| UI | Compare runtime screenshots to retained mockups; check narrow/wide and dark/light, large fonts, accessible labels/touch targets, empty lists and long titles. |

Expected build/test surfaces, refined to the delivered change: focused common/JVM tests via `:composeApp:jvmTest`, JVM compile, Android debug assemble, and iOS simulator Kotlin compile. Run relevant Android tests/lint when Android behavior is touched; use existing migration/sync tests for persistence changes. Do not claim native runtime validation from shared compilation. Consult current build tasks before execution. Stop Gradle daemons with `./gradlew --stop` after running Gradle.

Final implementation handoff must separate request/behavior verification from builds/tests, list actual runtime checks and screenshot paths, and state any remaining gaps. No commit, push, release, live account replacement, or server deployment is implied.

## Explicit exclusions

- Concepts B/C, the My Day name, daily-reset scheduling and Recently opened.
- AI suggestions, timers, habits, manual ranking, broad project management, new history infrastructure, or redesign of other tabs.
- New widget surfaces, import/export format extensions, or notification actions for Shortlist without a separately justified requirement. Existing paths must continue to work and must not erase membership on ordinary edits.
- Force-adding ignored mockups or modifying unrelated working-tree changes.
