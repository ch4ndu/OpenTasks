---
name: delegate
description: Delegate an implementation task to Codex (the worker) while Codex acts as manager — plan, hand off, then verify and gate. Use when the user wants a task built by Codex under Codex's supervision, or asks to "delegate", "have Codex do X", or run the two agents together.
argument-hint: [task to delegate to Codex]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, Agent
---

# Delegate to Codex (Codex = manager, Codex = worker)

You are the **manager**. Codex does the typing; you own the plan and the gate.
Codex reads the same `AGENTS.md` + `docs/ai/*` rules you do, so the spec can stay
high-level on conventions and specific on intent.

Operating contract (chosen defaults — confirm only if the user contradicts them):
- **Isolation:** Codex works in a dedicated git worktree, never the user's tree.
- **Autonomy:** `-s workspace-write` — Codex may edit files but stays sandboxed.
- **Gate:** Nothing reaches the user's branch until you have reviewed the diff,
  run the build/tests, and the user has approved.

## 0. Orient

Read `AGENTS.md`. Load the focused docs relevant to the task:
- UI → `docs/ai/ui.md`
- Data / sync / timestamps / migrations → `docs/ai/data-sync-time.md`
- Widgets → `docs/ai/widgets.md`
- Architecture → `docs/ai/architecture.md`

## 1. Plan

Decompose the task. Write a precise spec for Codex: the intent, the files/areas
involved, the acceptance criteria, and a one-line pointer to read `AGENTS.md` +
the relevant `docs/ai/*` file. Keep it tight — Codex is capable; over-specifying
wastes tokens. If the task is ambiguous, resolve it with the user BEFORE delegating.

## 2. Isolate

Create a worktree off the current branch:

```bash
BR="codex/$(echo '<short-task-slug>')"
git worktree add -b "$BR" "../ot-$BR" HEAD
```

## 3. Delegate

Hand the spec to Codex, sandboxed to the worktree. Capture its final report:

```bash
codex exec -C "../ot-$BR" -s workspace-write \
  -o /tmp/codex-report.md \
  "Read AGENTS.md and the relevant docs/ai/* files first. <SPEC>"
```

Read `/tmp/codex-report.md` and `git -C ../ot-$BR diff` to see what it did.

## 4. Verify (your real job)

Do NOT trust the report. Gate the diff through, in order:
1. **Read every changed line.** Does it match intent and the `docs/ai` rules?
2. **Build / test.** Run the project's build, tests, and detekt against the worktree.
3. **Cross-review (optional, strong signal).** Run `codex exec review -C ../ot-$BR`
   and/or your own `/code-review` on the diff and reconcile findings.

## 5. Decide

- **Reject / iterate:** send specific corrections back to Codex via another
  `codex exec` in the same worktree. Repeat 3–4.
- **Accept:** summarize what changed and what you verified, then ask the user to
  approve merging the worktree back. On approval, merge/rebase and
  `git worktree remove ../ot-$BR`.

Report outcomes faithfully: if the build failed or you couldn't verify something,
say so plainly rather than rubber-stamping Codex's report.

## Task to delegate

$ARGUMENTS
