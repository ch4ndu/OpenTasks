# OpenTasks Roadmap

Feature ideas inspired by Todoist and Google Tasks, organized by implementation effort.

---

## Low Effort

- [x] **Starred/pinned tasks** — Star flag on tasks with "Starred" virtual filter in the category picker (cross-category)
- [x] **Sort options in task list** — Sort-by dropdown (date, priority, name, recently updated) with persisted preference
- [x] **Today view** — "Today" virtual filter in the category picker showing overdue + today's tasks across all categories

## Medium Effort

- [x] **Sections within categories** — Simple `section: String?` field on Task, grouped display with section headers in TaskList
- [x] **Rich text for task descriptions** — Reuses `richeditor-compose` (same as Notes) with shared formatting toolbar, HTML storage
- ~~**Drag-to-reorder tasks**~~ — Won't do
- [x] **Smart filters** — Overdue, No Date, High Priority, Due This Week + Filters/Lists section dividers in category picker
- [x] **Export to CSV/ICS** — TickTick-compatible CSV + RFC 5545 ICS export, file save on all platforms

## Higher Effort

- [x] **Kanban/board view** — List/Board toggle in TaskList. 3 columns (To Do / In Progress / Done) with drag-and-drop. `isCompleted` replaced with `TaskStatus` enum across entire codebase.
- [ ] **Natural language date parsing** — Parse dates from text input (e.g. "tomorrow at 3pm", "every Monday") during task creation
- [ ] **Activity log / completion history** — Track completed tasks with timestamps for review and reflection
- [ ] **Productivity stats** — Daily/weekly completion counts, streaks, simple graphs for motivation
- [ ] **Nested categories (sub-projects)** — Support category hierarchies / sub-categories

## Deferred / Low Priority

- [ ] **Collaboration/sharing** — Shared projects, task assignment, comments
- [ ] **Location-based reminders** — Trigger reminders based on GPS location
- [ ] **Email-to-task** — Create tasks from forwarded emails
- [ ] **AI features** — Task assist, filter assist, natural language filter generation
- [ ] **Rich text editor replacement** — `richeditor-compose` has rendering issues. Options: (1) `multiplatform-markdown-renderer` for display + plain markdown editing, (2) platform-native via expect/actual (MarkdownTwain on Android, RichTextKit on iOS), (3) wait for `richeditor-compose` 1.0 stable. No mature KMP WYSIWYG alternative exists yet
