---
name: trip-test
description: Author and run focused tests following project standards
---

# Testing Mode

## Initialization Gate

Before any action, require `.agents/trip/initialized.json`. If it is absent, stop and invoke `trip-init`; do not author or run project tests through this workflow.

You are now in **testing mode** for **OpenTasks**.

This skill is the **deep test-authoring reference**: the `trip-2-implement` testing gate points here for heavy authoring work and full guidance. Invoke it standalone for test backfill or coverage work outside an implementation session.

## Prerequisites - Read First

Before testing, you MUST read:

1. @docs/ARCHI.md - Understand system architecture
2. @docs/4-unit-tests/TESTING.md - Testing guidelines

## Your Task

Test: $ARGUMENTS

---

## Testing Guidelines

### Scope

- Only run tests for relevant files that changed (not the whole project)
- Focus on the new feature/fix/refactor

### Commands

```bash
# Run all tests
./gradlew :composeApp:jvmTest :androidApp:testDebugUnitTest

# Run a specific test
./gradlew :composeApp:jvmTest --tests 'fully.qualified.TestClass.testName'

# With coverage (if configured)
./gradlew :composeApp:koverHtmlReport
```

### Test Structure

- `composeApp/src/commonTest`: portable parser, date/time, reminder, sync-record, domain, and contract tests.
- `composeApp/src/jvmTest`: Room persistence/migrations, sync adapters, DI resolution, Actions, ViewModels, and pure UI/navigation helpers.
- `androidApp/src/test`: Android application-shell, worker, and widget data-provider unit tests.
- Test files use the `*Test.kt` suffix and `kotlin.test.Test`; coroutine tests use `kotlinx-coroutines-test`, Flow assertions may use Turbine, and HTTP seams may use Ktor MockEngine.
- Put tests in the lowest portable source set. Use `jvmTest` for Room/JVM integration and `androidApp/src/test` only for Android-shell behavior.

### Testing Priorities

**Unit and portable contract tests**:

- UseCase and Action behavior, ViewModel projections, parsing/import/export, date/recurrence calculations, reminder identities, and sync record mapping
- Normal, empty, boundary, invalid, cancellation, retry, and stale-response paths

**Persistence and integration tests**:

- Room migrations, transactions, tombstones, attachment file/metadata arbitration, sync ordering, pull/push failure isolation, server seeding, and DI resolution
- Equal/newer/older timestamp conflicts and partial external failures

**Manual or platform verification**:

- Compose interaction and adaptive layout behavior
- Android widgets, notifications, alarms, receivers, intents, and permissions
- iOS notifications, background refresh, share extension, and native file/calendar flows
- Desktop packaged startup, ProGuard/JNI, file dialogs, and native distributions
- PocketBase migrations and wire contracts against a disposable real server

---

## Hard-to-Test Code

Seam ladder, cheapest first: **exported pure helper → injectable client/adapter → module mock → integration/emulator test**. Take the first rung that works; refactor for a seam only if the refactor is smaller than the feature you're shipping — otherwise it's coverage debt. Before refactoring legacy code, pin it with characterization tests (assert current behavior as-is, then refactor safely).

Uncovered risky paths: one line each in `docs/4-unit-tests/COVERAGE-DEBT.md` (`path | why hard | escape plan`). Delete a ledger line in the same change that gives its path meaningful coverage.

---

## Post-Testing Summary

After completing tests, create a summary file:

**File**: `docs/4-unit-tests/wa_vx.y.z_test.md`
(a = project week, x.y.z = version)

**Content**:

```markdown
# Test Summary - Week a, V. x.y.z

## What Was Tested

[List of tested components/functions]

## Test Results

- Total tests: X
- Passed: X
- Failed: X
- Coverage: X%

## Key Findings

[Any issues discovered, edge cases found, etc.]

## Notes

[Additional context or recommendations]
```
