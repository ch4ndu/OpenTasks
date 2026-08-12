# Testing Guidelines

## Test Framework

OpenTasks uses `kotlin.test` with JUnit 4.13.2, `kotlinx-coroutines-test`, Turbine, Ktor MockEngine, and Kover 0.9.9. Room integration tests use the bundled SQLite driver.

## Running Tests

```bash
# Shared commonTest and jvmTest suites
./gradlew :composeApp:jvmTest

# Android application-shell unit tests
./gradlew :androidApp:testDebugUnitTest

# A specific shared/JVM test
./gradlew :composeApp:jvmTest --tests 'fully.qualified.TestClass.testName'

# A specific Android-shell test
./gradlew :androidApp:testDebugUnitTest --tests 'fully.qualified.TestClass.testName'

# HTML coverage report
./gradlew :composeApp:koverHtmlReport
```

Use the affected suites during implementation. Run the consolidated multiplatform compile, Android lint/assembly, and proportional native/manual checks described in `docs/ARCHI.md` when the change crosses those boundaries. Device, simulator, and live-service execution remain separately requested gates.

## Test Organization

- `composeApp/src/commonTest` contains portable parser, date/time, reminder, sync-record, domain, and contract tests.
- `composeApp/src/jvmTest` contains Room persistence/migration, sync-adapter, server-seed, DI, Action, ViewModel, and pure UI/navigation-helper tests.
- `androidApp/src/test` contains Android shell, WorkManager, and widget data-provider tests.
- Test source files use the `*Test.kt` suffix and test functions use `@Test`.

## Writing Tests

- Put tests in the lowest portable source set.
- Cover valid, invalid, empty, boundary, cancellation, retry, stale-state, and partial-failure paths relevant to the change.
- Use fake repositories for domain/ViewModel tests, real temporary Room databases for persistence and migration behavior, and Ktor MockEngine through `AccountClientSession` for authenticator HTTP seams. A permanent disposable PocketBase/server harness requires separate scope approval.
- Preserve cancellation semantics and deterministic time/dispatcher control.
- Add platform or packaged-runtime verification when behavior cannot be proven by a host unit test.
- Record intentionally uncovered risky paths in `docs/4-unit-tests/COVERAGE-DEBT.md` and remove entries when coverage is added.

See [COVERAGE-DEBT.md](COVERAGE-DEBT.md) for the small ledger of remaining native and live-service boundaries. The authenticator's focused MockEngine/session tests are the required automated gate; they do not imply live PocketBase availability.

## Coverage Requirements

No numeric minimum coverage threshold is configured. Kover reports exclude generated code, DI wiring, platform shells, previews, resources, and UI packages. Treat meaningful behavioral coverage and risk-based platform verification as the acceptance gate.
