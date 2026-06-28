---
name: fresh-context-review
description: Re-review code, documentation, or plans from the current files on disk with source-backed findings. Use when the user asks for a fresh context review, review all changes again, audit for accuracy/correctness, grep verify everything, distinguish resolved versus remaining issues, or produce findings with file/line evidence after edits.
---

# Fresh Context Review

Use this skill to perform a current-state review, not a continuation of stale conclusions from earlier turns. Treat the live files as the review surface unless the user explicitly asks for a branch diff or PR-only review.

## Workflow

1. Re-read the latest user request and any task-specific constraints.
2. Inspect the current repository state before judging the change:
   - Use `git status --short` to understand dirty files.
   - Use `git diff --stat` and targeted `git diff -- <path>` when reviewing changes.
   - Use `rg` for source-backed verification of behavior, docs claims, configs, platform paths, tests, and invariants.
3. Load only the relevant repo instructions:
   - Follow `AGENTS.md`.
   - Load additional project docs only when their task category matches the review.
4. Review by behavior and risk, not by summarizing the diff:
   - Check each named screen, flow, platform path, sync transition, and data contract mentioned by the user.
   - Trace callers and observers far enough to confirm the implementation is wired, not merely present.
   - Verify deletion, failure, cancellation, permission-denied, offline, retry, and cross-device paths when the feature implies them.
5. Separate resolved issues from remaining issues when prior findings exist. Do not carry a finding forward unless it still reproduces in the current files.
6. Report findings first, ordered by severity. Each finding needs:
   - Severity such as `P1`, `P2`, or `P3`.
   - A concise title.
   - A file and line reference.
   - The concrete failure mode and user-visible or data-integrity impact.
7. If no issues are found, say that directly and list residual risk or tests not run.

## Review Standards

- Prefer concrete file/line evidence over broad assertions.
- Do not treat compile success as proof that behavior is correct.
- Do not propose unrelated refactors during a review.
- Call out noisy review surfaces, such as unrelated dirty files, and keep conclusions scoped to the files actually reviewed.
- For docs audits, verify claims against source with `rg`; for code audits, verify call chains, platform implementations, persistence, and tests.

## Output Shape

Use this order:

1. Findings.
2. Open questions or architecture decisions, only when needed.
3. Verification performed.
4. Brief change/context summary only if useful.

Keep summaries secondary; the review result is the findings and evidence.
