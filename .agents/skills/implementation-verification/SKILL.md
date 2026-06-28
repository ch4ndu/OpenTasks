---
name: implementation-verification
description: Verify delivered implementation work against the user's actual request before calling it complete. Use after code edits, feature implementation, bug fixes, or review-finding fixes when Codex must check behavior, platform paths, tests, builds, and known constraints rather than relying only on compilation.
---

# Implementation Verification

Use this skill as the final engineering gate after implementation. The purpose is to confirm that the delivered work matches the user's request and repository constraints.

## Workflow

1. Re-read the latest user request and any accepted plan or review findings.
2. Build a short checklist of requested behaviors and constraints.
3. Inspect the changed files and relevant call paths:
   - Confirm each screen, action, ViewModel, use case, repository, platform implementation, and migration path is wired where the user expects.
   - Check negative paths such as cancellation, permission denial, partial failure, deletion, retry, and offline behavior when relevant.
   - Verify platform paths separately for Android, iOS, JVM, common code, widgets, or server migrations when touched.
4. Run the required audit pass:
   - Load `docs/ai/audit.md` and apply it to the changed files and affected call paths.
   - If UI or Compose changed, also load `docs/ai/ui.md`.
   - If Room, repositories, sync, timestamps, date/time, or migrations changed, also load `docs/ai/data-sync-time.md`.
   - Check the Karpathy-style discipline from `AGENTS.md`: smallest sufficient change, no unrelated refactor, no speculative abstraction, and every changed line traces to the request.
   - Treat unresolved audit findings as incomplete work unless the user explicitly accepts the gap.
5. Run focused verification:
   - Prefer existing tests that cover the changed behavior.
   - Add or update focused tests when the risk warrants it and the codebase has a nearby pattern.
   - Run relevant compile tasks for touched platforms.
   - After Gradle commands in OpenTasks, run `./gradlew --stop`.
6. Reconcile the checklist:
   - Continue fixing any incomplete requested behavior unless the user explicitly accepts the gap.
   - If verification cannot be run, state why and what risk remains.

## Completion Criteria

Do not call a task complete until:

- The latest request has been checked against the implementation.
- A focused `docs/ai/audit.md` pass has been completed, with `docs/ai/ui.md` and `docs/ai/data-sync-time.md` loaded when applicable.
- Known review findings or accepted plan items are resolved or explicitly called out as remaining.
- The relevant build/test commands have passed, or unrun verification is reported.
- Unrelated dirty worktree changes have not been reverted or mixed into the work.

## Final Response Shape

Separate request verification from build verification:

1. What changed.
2. Request verification: name the behaviors checked.
3. Build/test verification: name the commands run and their result.
4. Remaining gaps, if any.

Keep the final response concise, but do not hide incomplete work or unrun checks.
