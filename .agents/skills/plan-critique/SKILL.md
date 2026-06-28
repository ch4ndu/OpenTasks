---
name: plan-critique
description: Pressure-test and revise engineering plans against the actual repository. Use when the user asks to critique a plan, evaluate findings and make a plan, revise a plan, ask for architecture decisions, or turn review findings into an implementation plan without assuming unresolved design choices.
---

# Plan Critique

Use this skill to turn a proposed plan or set of findings into a decision-complete, repo-grounded plan. The goal is to expose hidden assumptions before implementation.

## Workflow

1. Re-read the user request and identify the plan's intended outcome.
2. Inspect the relevant repository reality before judging the plan:
   - Current models, repositories, actions/use cases, ViewModels, UI wiring, platform code, migrations, tests, and build files.
   - Existing project instructions in `AGENTS.md` and task-specific docs when applicable.
3. Evaluate the plan against concrete criteria:
   - Does it cover every named behavior, platform, and state transition?
   - Does it preserve existing layering and local patterns?
   - Does it define data invariants and sync contracts precisely?
   - Does it handle creation, edit, delete, retry, offline, cancellation, permission denial, and partial failure paths when relevant?
   - Does it include focused tests and compile/build verification?
   - Does it avoid speculative abstractions and unrelated refactors?
4. Identify hidden assumptions and unresolved decisions. Ask the user only when the answer would materially change architecture or user-visible behavior.
5. Revise the plan into implementable steps. Each step should include:
   - Files or layers likely involved.
   - The behavior or invariant being changed.
   - Verification for that step.
6. If the user says to implement, proceed from the revised plan without reopening already-settled decisions unless the code contradicts them.

## Architecture Decision Gate

Ask before proceeding when choices involve:

- Data model shape, sync ownership, or migration compatibility.
- Public versus private storage/security behavior.
- Platform-specific UX or permission semantics.
- Whether to preserve tombstones, historical data, or local files.
- User-visible error handling for partial failure.
- Any change that broadens scope beyond the user's requested feature or fix.

Do not ask for decisions when the repo already has a clear established pattern or when a conservative local fix is sufficient.

## Output Shape

For critique:

1. Plan risks or gaps, ordered by severity.
2. Missing decisions.
3. Revised plan.
4. Verification plan.

For a decision prompt:

1. Ask only the blocking architecture questions.
2. Give the impact of each decision in one sentence.
3. Avoid broad restatement of the whole plan.
