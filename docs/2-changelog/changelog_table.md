# Changelog Table

| Version | Week | Commit Message |
| --- | --- | --- |
| `1.2.0` | 5 | fix: remediate project audit findings |
| `1.1.0` | 4 | feat: add multi-user PocketBase account isolation |
| `1.0.1` | 1 | chore: initialize TRIP workflow |

# Changelog Summary

- **v1.2.0 (project-audit-remediation - Week 5, 12-08-2026)**:
  - **Local operation**: Released backend-free Local-only startup, authoritative PocketBase connection, and bounded Quick Add from the checkpointed feature work
  - **Platform boundaries**: Bounded Android, iOS, and JVM share/import inputs, strict UTF-8 decoding, iOS provider aggregation, and desktop process lifetime
  - **Lifecycle and security**: Closed PocketBase clients exactly once, preserved coroutine cancellation, removed sensitive diagnostics, and removed the unreachable Settings endpoint editor
  - **Calendar and recurrence**: Added bounded countdown occurrence projection and moved calendar display projection out of composition
  - **Toolchain and Android**: Updated compatible dependencies, isolated Compose tooling to debug builds, fixed widget/resource lint, and retained documented API-36-compatible pins
  - **Verification**: Passed 354 JVM tests, 11 Android tests, lint, debug/release APK assembly, JVM and both iOS Kotlin compiles, compile-only Xcode app/extension build, and the Sol-xhigh final gate
- **v1.1.0 (multi-user-pocketbase-isolation - Week 4, 09-08-2026)**:
  - **Accounts**: Added two pre-created PocketBase account sessions, durable restoration, safe switching/logout, and per-account task UI boundaries
  - **Isolation**: Enforced owner-scoped PocketBase rules, client validation, cache epochs, background-entry boundaries, and account-owned ViewModel disposal
  - **Migration**: Added the PocketBase 0.36.7 ownership migration and production cutover runbook, assigning the legacy dataset to Account A
  - **Sync**: Corrected record updates to `PATCH`, recovered stale remote IDs safely, classified transient account-service failures for offline continuation, and improved manual-sync status
  - **UI**: Improved account-field readability, landscape consistency, system-bar icon visibility, and session-restoration retry behavior
  - **Verification**: Passed 287 JVM tests, 10 Android tests, Android lint/assembly, JVM and both iOS ARM64 compiles, production PocketBase cutover, physical-device acceptance, and the Sol final gate
- **v1.0.1 (trip-initialization - Week 1, 17-07-2026)**:
  - **Setup**: Initialized the project-local TRIP workflow and documentation structure
  - **Documentation**: Generated and approved OpenTasks cross-platform mobile and desktop architecture guidance
  - **Workflow**: Adapted planning, implementation, review, testing, release, and hotfix gates to the Kotlin Multiplatform repository
  - **Files Added**: `docs/ARCHI.md`, `docs/ARCHI-rules.md`, `docs/2-changelog/changelog_table.md`, `docs/4-unit-tests/TESTING.md`
