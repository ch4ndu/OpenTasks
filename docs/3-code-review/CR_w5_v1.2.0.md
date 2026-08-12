# Code Review - Week 5, Version 1.2.0

**Review date:** 12-08-2026

**Scope:** Repository audit remediation across external inputs, PocketBase lifecycle, cancellation, diagnostics, authentication coverage, recurrence, calendar projections, Settings cleanup, Android resources, dependencies, and verification debt

**Commit:** `fix: remediate project audit findings`

## Review provenance

- Initial implementation review: verified `gpt-5.6-sol/xhigh`, one fresh-context review round, `REQUEST_CHANGES` with seven findings.
- Consolidated evidence response: verified `gpt-5.6-sol/xhigh` on the same read-only review thread.
- Accepted-fix wave: verified fresh `gpt-5.6-luna/max`, one bounded correction pass.
- Final adjudication: verified fresh `gpt-5.6-sol/xhigh`, round 1, `APPROVED` with no actionable findings.

Persisted review artifacts are under `.local/trip/runtime/invocations/code-review/docs_1-plans_F_1.2.0_project-audit-remediation.plan.md/`.

## Findings and disposition

### Major: child process could escape cancellation cleanup during startup

The JVM process was started inside a non-cancellable dispatcher hop, but ownership was assigned only after the hop returned. Cancellation in that interval could leave the outer cleanup path without the started process.

**Resolved:** process startup and ownership assignment now happen in the same non-cancellable block. One focused regression test starts a long-lived child, captures its PID, cancels the runner, and boundedly verifies that the child dies.

### Major: supported iOS provider-load failures could close silently

The Share Extension discarded `NSItemProvider.loadItem` errors and could classify a failed supported provider as ignored content.

**Resolved:** provider errors map to the typed unreadable failure and flow through the existing error-URL and local app-open feedback paths.

### Minor: PocketBase endpoint details remained in session diagnostics

Session restoration still logged protocol, host, and port during credential refresh.

**Resolved:** the diagnostic is now structural and contains no endpoint value.

### Minor: malformed CSV reminder digits could expose imported content

Throwing integer conversion could include untrusted reminder digits in exception logs and user-visible error detail.

**Resolved:** reminder components use non-throwing `Long` parsing and range checks. Generic import failure logs and UI now contain no exception message or imported value while cancellation remains cancellation.

### Minor: completed debounce job retained a stale reference

Cleanup compared `debounceJob` with the `NonCancellable` context job instead of the launched owner.

**Resolved:** cleanup captures the launch job before entering the non-cancellable section and clears the stored reference only when that job still owns it.

### Minor: week mini-calendar mapped projections back to domain tasks

The mini-calendar only needed populated day presence but received a task map reconstructed during composition.

**Resolved:** the shared mini-calendar boundary now accepts `Set<Long>` day keys. Week and year paths pass existing populated keys without domain-model mapping.

### Minor: compatible Compose and RichEditor releases were still pinned back

Compose Multiplatform 1.11.1 and RichEditor 1.0.0 were compatible with the approved Kotlin line, while the prior comments described the older versions as current.

**Resolved:** the pair was upgraded together. AGP 9.2.1 and Gradle 9.4.1 remain the empirically verified project pair because the published Kotlin Multiplatform compatibility table does not yet establish support for the proposed newer AGP line. API-37-dependent AndroidX artifacts retain declaration-specific pins and removal triggers.

## Verified compliant areas

- Shared Android, iOS, and JVM import/share limits reject oversized, over-count, unreadable, and malformed-UTF-8 inputs before parsing or navigation.
- PocketBase clients and account sessions close exactly once across activation, replacement, disconnect, failure, and cancellation.
- A focused Ktor `MockEngine` suite covers authentication request construction, token promotion, capability/inventory pagination, owner rejection, representative error mapping, and cleanup.
- Countdown recurrence uses bounded anchor/index projection while preserving interval normalization and monthly/yearly clamping.
- The unreachable Settings endpoint mutation path is removed while live authenticated endpoint persistence and replacement remain.
- Calendar composables consume projections and populated-day keys; formatting, grouping, fixed previews, and model mapping remain outside composition.
- Android runtime Compose tooling is debug-only, widget metadata retains API-26 compatibility with API-31 variants, and lint contains no unexplained issues.
- No CI workflow was added; missing CI remains explicitly deferred by the user rather than represented as fixed.

## Verification evidence

- `:composeApp:jvmTest`: 354 tests, 0 failures/errors.
- `:androidApp:testDebugUnitTest`: 11 tests, 0 failures/errors.
- Focused `JvmProcessRunnerTest`: 5 tests, including cancellation/child-death cleanup.
- `:androidApp:lintDebug`: passed with an empty issue report.
- `:androidApp:assembleDebug` and `:androidApp:assembleRelease`: passed; both APKs exist.
- `:composeApp:compileKotlinJvm`, `:composeApp:compileKotlinIosArm64`, and `:composeApp:compileKotlinIosSimulatorArm64`: passed.
- Code-signing-disabled Xcode app and Share Extension build: passed without booting a simulator.
- Release dependency inspection: exact `androidx.compose.ui:ui-tooling` absent; preview tooling retained.
- `git diff --check`: passed.
- Focused architecture, UI/Compose, data/sync/time, and affected-call-path audit: no unresolved findings.

## Remaining manual boundaries

Real Android/iOS share and file-provider behavior, calendar and widget interaction, macOS Calendar permission/AppleScript behavior, packaged-JVM logging, live PocketBase authentication/replacement, and post-upgrade runtime smoke tests remain documented owner-operated checks. No permanent native/service harness was added.

## Verdict

`APPROVED` — all eleven actionable audit findings and all seven initial review findings are resolved. Missing CI remains deferred by explicit user decision.
