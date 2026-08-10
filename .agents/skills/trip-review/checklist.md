# Code Review Checklist

This file is the **single source of truth** for code-review criteria. Both human-driven reviews via `.agents/skills/trip-review` and Terra-driven private runtime reviews via `.agents/trip/runtime/code-review` apply the criteria below — referenced, not copied — so the two review surfaces cannot drift.

## Systematic Review Checklist

### 1. Functional Requirements

- [ ] Implementation logic matches requirements correctly
- [ ] Interface/API matches documented specifications
- [ ] Error scenarios handled with proper feedback
- [ ] Edge cases and boundary conditions validated

### 2. Code Quality

- [ ] Proper typing (no unjustified dynamic types)
- [ ] DRY principle - no code duplication
- [ ] KISS principle - not unnecessarily complex
- [ ] Consistent, descriptive naming conventions
- [ ] Complex logic has explanatory comments
- [ ] Files/modules not excessively large
- [ ] Imports/includes organized, unused ones removed

### 3. Architectural Compliance

- [ ] Code follows established patterns from ARCHI.md
- [ ] Proper separation of concerns
- [ ] Appropriate abstractions used
- [ ] Consistent with existing codebase style

### 4. Compose UI and State

- [ ] Composables render state and send events; filtering, sorting, grouping, and mapping stay in UseCases or ViewModels
- [ ] State collection is scoped to the screen, mode, or component that needs it
- [ ] ViewModel flows use appropriate dispatchers and `SharingStarted.WhileSubscribed(5000)`
- [ ] Lazy layouts retain stable keys, virtualization, and efficient row-level recomposition
- [ ] Shared resources, theme dimensions, insets, accessibility, and adaptive layouts are handled
- [ ] Android previews remain in `androidMain`

### 5. Data, Sync, Time, and Reminders

- [ ] Room changes include explicit migrations, exported schemas, and persistence tests
- [ ] Repository and DAO boundaries preserve UTC/local conversion and tombstone rules
- [ ] Multi-record and attachment mutations preserve transaction and file-lifecycle invariants
- [ ] Sync preserves dependency order, pull-before-push, conditional bookkeeping, and failure isolation
- [ ] Recurrence and reminder identities remain stable across scheduling, delivery, actions, and cancellation
- [ ] Offline, retry, partial-failure, clock-skew, reset, and server-replacement paths are considered

### 6. Platform Integration

- [ ] Shared behavior is implemented in `commonMain`; platform code is limited to necessary native integration
- [ ] Every affected Android, iOS, and JVM `actual` path is wired and verified
- [ ] Stable component, database, widget, notification, WorkManager, app-group, and package identities are preserved
- [ ] External intents, URLs, files, permissions, background work, and lifecycle transitions are handled
- [ ] Native behavior has the required emulator, device, simulator, packaged-app, or real-service check

### 7. Error Handling

- [ ] Errors are properly caught and handled
- [ ] Error messages are clear and actionable
- [ ] Failure modes are graceful
- [ ] Logging is appropriate (not too verbose, not silent)

### 8. Security (if applicable)

- [ ] Input validation implemented
- [ ] No sensitive data exposed in logs, errors, or responses
- [ ] Authentication/authorization handled correctly
- [ ] No obvious vulnerabilities

### 9. Performance

- [ ] No obvious performance issues
- [ ] Resource cleanup implemented (no leaks)
- [ ] Appropriate data structures used
- [ ] No unnecessary operations in hot paths

---

## Issue Severity Classification

**Critical (Block Deployment)**:

- Security vulnerabilities
- Data corruption risks
- Breaking API/interface changes
- Authentication bypasses

**Major (Require Immediate Fix)**:

- Incorrect business logic
- Significant performance degradation
- Missing error handling
- Compilation/build errors

**Minor (Should Fix)**:

- Code style inconsistencies
- Missing documentation
- Code duplication
- Missing edge case handling

**Suggestions (Nice to Have)**:

- Performance optimizations
- Readability improvements
- Additional test coverage

---

## Review Completion Criteria (Approval Gate)

Minimum for approval:

- [ ] All functional requirements implemented
- [ ] No critical or major issues remaining
- [ ] Android lint successful (`./gradlew :androidApp:lintDebug`) when Android code or resources are affected
- [ ] Build/compilation successful (`./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`)
- [ ] Affected unit tests pass (per the trip-2 testing gate: `./gradlew :composeApp:jvmTest :androidApp:testDebugUnitTest`)
- [ ] New logic has test coverage (or a coverage-debt ledger entry per the hard-to-cover policy)
- [ ] Documentation updated per project standards
