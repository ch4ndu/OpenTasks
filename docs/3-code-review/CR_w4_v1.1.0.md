# Code Review - Week 4, Version 1.1.0

**Review date:** 09-08-2026

**Scope:** Two-account PocketBase isolation, account/session lifecycle, owner-scoped sync, platform boundaries, migration/cutover, and release-candidate sync corrections

**Commit:** `feat: add multi-user PocketBase account isolation`

## Review provenance

- Consolidated release-candidate review: verified `gpt-5.6-sol/xhigh`, one fresh-context review round.
- Evidence response and accepted-fix wave: verified `gpt-5.6-sol/xhigh`.
- Exact-path ownership/adoption wave: verified `gpt-5.6-sol/xhigh`, no source changes required.
- Final adjudication: verified `gpt-5.6-sol/high`, round 2, `APPROVED`.

Persisted artifacts are under `.local/trip/runtime/invocations/code-review/multiple-user-v1.1.0-release-candidate/`.

## Findings and disposition

### Critical: account-owned ViewModels could survive an account epoch change

The authenticated composition was keyed by `CacheBinding.boundaryEpoch`, but top-level account-owned ViewModels could still resolve from a longer-lived host store. A queued Account A mutation could therefore survive a switch and act after Account B's cache became active.

**Resolved:** the authenticated subtree now supplies an epoch-owned `ViewModelStore` that is explicitly cleared on disposal. Note, notification, import, and pull-to-refresh entry points capture their originating account boundary before asynchronous dispatch and revalidate it after acquiring the shared mutation gate. Deterministic tests prove old-scope cancellation, distinct Account B instances, and zero Account B reads/writes for a queued Account A mutation using the same local ID.

### Major: transient PocketBase HTTP failures could destroy offline authority

Retryable `408`, `425`, `429`, and `5xx` responses could be classified as permanent authentication or capability rejection, causing a valid token and proven offline cache to be discarded.

**Resolved:** a shared account-HTTP classifier routes those statuses to `AccountConnectivityException` across authentication, capability, and owner-inventory requests. Explicit credential denial and permanent capability rejection remain distinct, and diagnostics include only the operation and status code.

### Scope observation: backend-free first launch

The review initially treated backend-free startup as a release requirement. The user had explicitly deferred it to a fast-follow. Version 1.1.0 therefore preserves offline continuation for a previously authenticated, durably bound cache; a fresh local-only session without PocketBase remains out of scope.

### Workflow provenance

The first final gate found that two valid production files were omitted from the accepted-fix ownership declaration. A fresh write-capable inspection explicitly reserved those exact files, verified unchanged hashes and non-overlapping ownership, reran lifecycle/DI tests, and formally adopted the implementation without rewriting historical metadata.

## Verified compliant areas

- PocketBase server rules and the client gateway enforce owner-only access for all seven synchronized collections.
- Account switching/logout use a final source sync, reject pending unsynced rows, replace the single Room cache transactionally, and fail closed on stale platform callbacks.
- Record updates use PocketBase `PATCH`; stale remote IDs recover through owner-scoped `localId` lookup without weakening last-write-wins or ownership checks.
- Manual sync waits for the actual pass and distinguishes record failure from connection failure.
- Account-screen text, landscape surfaces, restoration retry state, and Android system-bar icon appearance match the requested behavior.
- Passwords, tokens, record bodies, and user content are excluded from diagnostics.

## Verification evidence

- `:composeApp:jvmTest`: 287 tests, 0 failures/errors.
- `:androidApp:testDebugUnitTest`: 10 tests, 0 failures/errors.
- `:androidApp:lintDebug`: passed with 0 errors.
- `:androidApp:assembleDebug`: passed.
- `:composeApp:compileKotlinJvm`: passed.
- `:composeApp:compileKotlinIosArm64`: passed.
- `:composeApp:compileKotlinIosSimulatorArm64`: passed.
- Focused architecture, UI/Compose, and data/sync audit: no unresolved findings.
- `git diff --check`: passed.
- PocketBase 0.36.7 production cutover and Android physical-device account/isolation/sync acceptance: passed; the separate rehearsal was explicitly waived by the operator.

## Remaining accepted constraint

Backend-free first launch is deferred. Offline continuation requires a previously authenticated and durably bound account cache.

## Verdict

`APPROVED` — no unresolved product, security, data-isolation, verification, operator, or workflow findings remain for version 1.1.0.
