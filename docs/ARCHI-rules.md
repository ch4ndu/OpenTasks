# Architecture Documentation Rules

[ARCHI.md](ARCHI.md) documents the OpenTasks architecture. After each task, determine whether the implementation changed an architectural fact recorded there.

## When to Update

Update ARCHI.md after any change that alters:

- Technology Stack, Project Structure, or Core Architecture Principles
- Build System and Toolchain, Configuration and Versioning, or Packaging and Distribution
- UI, State, and Navigation or Domain and Dependency Injection
- Persistence and Offline Data or PocketBase Synchronization
- Date, Recurrence, and Reminder Architecture
- Native Platform Integration or Import, Export, and Attachments
- Data Flow Diagrams, Error Handling and Observability, Testing Strategy, performance, security, or known constraints

## How to Update by Change Type

### Major Feature or Refactor

Review every affected architecture section, data-flow diagram, platform boundary, verification requirement, and known constraint.

### Minor Feature or Enhancement

Update the specific subsystem section when ownership, data flow, configuration, platform behavior, or verification requirements changed.

### Bug Fix

Usually no update is needed. Update ARCHI.md when the fix corrects a documented invariant, exposes a previously undocumented boundary, or changes architectural behavior.

### Dependency or Toolchain Change

Update Technology Stack and Build System and Toolchain, then any affected platform, packaging, testing, or compatibility sections.

## Guidelines

- Describe the current repository, not a proposed design.
- Keep ownership and dependency direction explicit.
- Reference real paths and stable identifiers.
- Update Mermaid diagrams when data flow changes.
- Record meaningful platform differences and verification requirements.
- Keep detailed coding rules in `docs/ai/*` and feature behavior in `docs/features/*`; do not duplicate them here.
