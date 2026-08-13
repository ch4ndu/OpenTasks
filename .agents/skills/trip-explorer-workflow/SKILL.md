---
name: trip-explorer-workflow
description: Run an explicit Explorer-assisted TRIP workflow for repository-grounded engineering work, preserving direct discovery, independent plan and code review, user approval before edits, persistent read-only context, and a manager-owned completion gate. Use only when the user requests this workflow or invokes $trip-explorer-workflow.
---

# TRIP Explorer Workflow

Use this skill as one continuous planning-to-implementation workflow. Keep the
manager as the only user-facing orchestrator and completion owner. Apply the
repository rules in `AGENTS.md`, `docs/ARCHI.md`, the routed `docs/ai/*` files,
and `docs/ai/feature-implementation.md`'s **Delegated Workflow Authority and
Evidence** section; do not duplicate those implementation rules here.

Use direct collaboration `spawn_agent` and `followup_task` for role lifecycle
and handoffs. Do not invoke or depend on `.agents/trip/bin/launch_runtime.py`
or `.agents/trip/initialized.json`.

## Preflight and entry

1. Re-read the latest user request and all constraints. Read `AGENTS.md`, all
   of `docs/ARCHI.md`, the task-relevant `docs/ai/*` files, current worktree
   status, and any approved plan. Preserve user-owned and unrelated changes.
2. Choose exactly one entry path:
   - **New task:** perform planning before any implementation.
   - **Approved-plan resume:** verify the plan path, revision, explicit user
     approval, and current relevance before continuing. Missing approval means
     stop and request approval; never infer it from a plan file or agent report.
3. In a read-only planning mode, keep the repository read-only. Present the
   plan in chat and do not create a branch, plan file, state file, or edit until
   writes are allowed. If approval cannot be durably checkpointed, require
   renewed approval before a later implementation phase.
4. Before and after each inspection-capable role turn, capture the complete
   inspection snapshot defined below. Resolve any unexplained delta before
   trusting role output.

## Role matrix and launch contract

Launch every role with the exact model and reasoning setting below. Set
`fork_turns: "none"` for every role. If an exact model or setting is
unavailable, fail closed and report it; never substitute, merge roles, or
silently lower effort.

| Role | `agent_type` | Model / effort | Lifetime and authority |
| --- | --- | --- | --- |
| Explorer | `explorer` | `gpt-5.6-sol` / `high` | One persistent read-only evidence thread from planning through final packet; advisory only, no verdicts or decisions. |
| Plan reviewer | `default` | `gpt-5.6-sol` / `xhigh` | Independent retained thread for the plan loop; findings and one terminal verdict. |
| Implementer/fixer | `worker` | `gpt-5.6-luna` / `max` | One retained write thread after authorization; edits only manager-assigned paths and cannot declare completion. |
| Code reviewer | `default` | `gpt-5.6-sol` / `xhigh` | Independent retained thread for the code loop; never inherit Explorer conclusions. |
| Final verifier | `default` | `gpt-5.6-sol` / `xhigh` | New independent thread after code convergence; inspect current files and return one terminal verdict. |

Use these launch fields literally:

```text
Explorer:       agent_type: explorer, fork_turns: "none", model: gpt-5.6-sol, reasoning_effort: high
Plan reviewer:  agent_type: default,  fork_turns: "none", model: gpt-5.6-sol, reasoning_effort: xhigh
Luna:           agent_type: worker,   fork_turns: "none", model: gpt-5.6-luna, reasoning_effort: max
Code reviewer:  agent_type: default,  fork_turns: "none", model: gpt-5.6-sol, reasoning_effort: xhigh
Final verifier: agent_type: default,  fork_turns: "none", model: gpt-5.6-sol, reasoning_effort: xhigh
```

Derive each role's stable task name from the workflow task ID. Normalize the
raw task ID and each controlled role label independently to lowercase
`[a-z0-9_]+`: replace each disallowed run with `_` and trim leading/trailing
`_`. Fail closed if a controlled role label normalizes empty. Before the first
spawn, choose and reserve exactly one non-empty task base: use the normalized
raw task ID when it is non-empty and not owned by another recorded or live
workflow; otherwise use `task`, then `task_2`, `task_3`, and so on, selecting the
smallest unused valid base. Record the raw-to-chosen mapping and reuse that
chosen base for every role in this workflow. Define this same chosen base as
the workflow `context_id`; do not add another allocator. Record the raw
workflow ID → chosen task base/context ID mapping.

Start each role at positive generation `1` and increase monotonically for each
replacement; never reuse a generation. Use the underscore-only name
`<chosen_base>_<role>_g<generation>`. A prefix already owned by this same
workflow-and-role is expected for a lost-thread replacement, so advance from
`g1` to the smallest never-used positive generation. Treat only another
workflow's ownership as a collision. Ensure every component, base, prefix, and
full candidate remains `[a-z0-9_]+`; fail closed if a valid unique candidate
cannot be established. Record the old-to-new task-name mapping. Reuse the same
role thread with follow-ups while available. If a thread is lost, create the
same exact role with `fork_turns: "none"` from authoritative artifacts and the
ledger, preserving the exact model, effort, authority, review counters,
findings, and ownership. Do not use a reviewer as Explorer, or Explorer as
reviewer. Perform normalization, reservation, and collision checks inline; do
not add a helper framework.

For each plan/code review, if the relevant counter is already `5`, make no tool
call. Never use a setup-only reviewer spawn: `spawn_agent` requires a mandatory
`message` and atomically creates the role and starts its turn. Every new or
replacement reviewer spawn must carry the complete current review prompt and
request one terminal verdict.

For a new or replacement reviewer, prepare and validate the exact launch tuple
(`agent_type`, `fork_turns`, model, effort, task name/generation), complete
current prompt, and preserved counter/findings before calling `spawn_agent` once
with that message. An error that explicitly proves creation failed before
delivery consumes no round and stops. A successful spawn made with that tuple
and its returned canonical target is authoritative creation provenance; record
the immutable requested tuple plus target. Require host-configuration readback
only when the host exposes it. A successful atomic spawn plus delivery consumes
exactly one round; record the increment and verify the returned canonical target
before trusting output. Ambiguous delivery, or transport/output failure after
the attempt, consumes one round and fails closed. A successful but mismatched
role has delivered and therefore consumes one round, then stops; never
substitute.

For a retained reviewer, verify target identity, availability, and status, and
match them to stored creation provenance; do not require unavailable
model/effort readback. If provenance is missing or contradictory, do not call
`followup_task`; launch a next-generation exact replacement with the preserved
counter/findings and complete review prompt under the new/replacement accounting
above. Otherwise increment once immediately before `followup_task`; its
delivery attempt consumes the round even if transport/output fails. Never retry
uncounted or reset a counter. Apply exact creation/verification to the final
verifier too, but its creation or delivery changes no `code_round`.

For the Explorer, plan reviewer, code reviewer, and final verifier, make every
prompt explicitly read-only: prohibit file, plan, or documentation edits,
Git-state mutations, write-producing commands, and delegated write work. If
Explorer mutates, invalidate and untrust its entire output, preserve the
incident, delta, response, and all findings/evidence in the chat or writable
ledger, consume no plan/code round, stop for reconciliation, and do not
automatically retry. A plan/code reviewer mutation invalidates and untrusts its
response and verdict, preserves the incident, delta, response, and all
findings/evidence in the ledger, consumes its attempted plan/code review round,
and stops for reconciliation. A final-verifier mutation fails that gate,
invalidates and preserves its output and all incident/finding evidence, but does
not consume, create, or reset `code_round`. Other unexplained deltas must be
reconciled before any inspection-role output is trusted. For every role,
preserve the invalidated response and complete incident, delta, finding, and
evidence record; do not discard an invalidated response.

Before and after every Explorer, reviewer, or final-verifier turn, capture a
complete inspection snapshot containing all of the following:

- `HEAD`;
- full `git status --short --untracked-files=all` output;
- a hash of the complete tracked HEAD-to-worktree binary diff;
- a separate hash of the staged binary diff;
- deterministic sorted path-plus-content hashes for every pre-existing
  untracked file;
- the active ignored `.local/trip-explorer/<context_id>/context.md` content hash,
  or an explicit absence marker.

Hash the complete byte contents with a deterministic algorithm and record the
path ordering. Any unreadable or unhashable component fails closed. Compare
every component before and after the turn. Treat role-attributable mutations
according to the invalidation rules above; reconcile all other deltas before
trusting output.

Sequence monitoring and ledger updates explicitly: finish any authorized
manager pre-turn ledger or context update first; capture the baseline snapshot;
invoke the inspection role; capture and compare the post-turn snapshot before
any manager post-turn ledger update; only after a clean comparison or explicit
reconciliation update the ledger. Do not write manager context between the
baseline and post-turn capture. In read-only planning, write no file ledger;
emit the required chat handoff only after the post-turn comparison. This order
keeps legitimate manager context writes outside the role-attribution window.

Resolve the one active context path as
`.local/trip-explorer/<context_id>/context.md` under the repository root. Before
reading it as active or writing it, validate that the resolved path is a unique
strict descendant of the repository's `.local/trip-explorer/` root; the root
itself is invalid. Fail closed on collision, path escape, symlink or alias
ambiguity, or unverifiable ownership. Use this one resolved path consistently
for monitoring, persistence, resume, ownership, and handoffs. Do not broaden
the approved ignored-file fingerprint beyond this active context path.

State the shared-workspace limitation honestly: direct collaboration agents
share one worktree. Treat Explorer read-only behavior as prompt prohibition plus
the pre/post monitoring above, not filesystem isolation. Explorer may run only
non-mutating inspection commands: no builds,
tests, formatters, generators, writes, or commands that create cache/state
output. Never inspect while Luna mutates the same tree. Permit no overlapping
write-capable waves; pause and reconcile concurrent user edits before writing.

## Planning phase

1. Conduct direct manager/user discovery. Ask only material questions and use
   at most three clarification rounds while blocking ambiguity remains.
2. Establish one persistent Explorer after the request has enough shape for a
   targeted investigation. If that thread is lost, rehydrate one exact
   replacement from authoritative artifacts without resetting workflow state.
   Supply the request, current phase, repository paths, and the read-only
   contract. Request factual production paths,
   contracts, invariants, cross-platform consumers, relevant tests,
   contradictions, uncertainties, and decision points with file/symbol
   evidence. Label each item `fact`, `inference`, `uncertainty`, or
   `decision required`.
3. Interpret Explorer evidence yourself. Keep the Explorer from choosing
   product behavior, expanding scope, editing the plan, or acting as a second
   planner. Draft a detailed file-level plan covering scope, architecture,
   edge cases, verification, and explicit exclusions. Do not implement before
   explicit user approval.
4. Spawn the independent plan reviewer with only the latest constraints and
   authoritative repository/plan paths. Do not pass Explorer conclusions.
   Retain the thread and initialize the separate maximum-five `plan_round`
   counter to `0`. In read-only planning with no plan file, include the
   complete current in-chat plan inline in the fork-none initial prompt. Include
   the complete revised plan inline in every follow-up or exact-role replacement,
   together with the prior reviewer's findings, manager dispositions, and
   verification. In all cases exclude Explorer conclusions and provide only the
   latest user constraints plus authoritative repository evidence. Follow the
   reviewer invocation order above; malformed-response follow-ups consume a
   round once prompt delivery is attempted. Invocations 1 through 5 are allowed
   and a sixth is impossible.
5. Require exactly one terminal verdict token: `APPROVED`,
   `REQUEST_CHANGES`, or `NEEDS_REWORK`. Treat a missing, duplicated,
   unknown, or otherwise malformed terminal verdict as failure closed. Ask the
   same reviewer for one corrected verdict only when the same pre-invocation
   check permits it; otherwise stop without another invocation. Never infer
   approval.
   `REQUEST_CHANGES` requires manager adjudication, plan revision,
   and the next same-thread review. `NEEDS_REWORK` stops implementation and
   surfaces the structural problem to the user.
6. Preserve every plan finding, including resolved findings, in the ledger with
   original meaning, evidence, manager disposition, and verification.
   Surface unresolved or decision-requiring findings to the user. A substantive
   user-requested plan revision returns to this reviewer and consumes the next
   round; never reset the five-round budget.
7. If `plan_round` reaches `5` with open findings or any malformed response,
   stop before implementation and surface every open and malformed finding. Ask
   the user to choose exactly one: cancel; request manager-only plan rework
   without any more reviewer calls; or explicitly accept named residual plan
   risks and waive reviewer convergence. Do not reset or add reviewer rounds.
   The latter two choices still require separate explicit approval of the exact
   resulting plan and explicit implementation authorization; manager-only
   rework must not be presented as reviewer convergence.
8. After plan convergence, obtain two separate user decisions: explicit plan
   approval and explicit implementation authorization. If implementation is
   deferred, checkpoint the approved plan and stop cleanly.

## Durable context and Explorer checkpoints

**Read-only planning handoff (chat only):** During read-only planning, carry
the phase and approval state in chat, write no context file or other state, and
if authoritative approval evidence is unavailable later, require renewed user
approval before implementation. Before every yield or phase boundary, provide
a complete authoritative in-chat handoff containing the complete current plan
text and revision, `plan_round`, every reviewer invocation with its verdict or
malformed result, every finding and disposition, exact role task names with
model/effort, context ID and resolved context path, raw workflow ID → chosen
task-base/context-ID and old-to-new generation mappings,
plan-approval and implementation-authorization state, and the next action. If
the consumed plan-review count cannot be recovered from an authoritative
handoff on resume, stop the entire current workflow before both further review
and implementation until it is recovered; never guess, reset, or waive the
counter. This handoff is chat-only and is not a writable context-file update.

**Writable context state:** Only when repository writes are authorized, and
when a workflow crosses a user turn or session, maintain exactly one ignored
context state and handoff file at
`.local/trip-explorer/<context_id>/context.md`. Validate the resolved active
path as specified above before reading or writing. Create or update this one
context file
after every plan-review invocation/round and every code-review invocation/round,
including malformed responses, before any resulting fix wave; also update it
after each material implementation/fix wave, every explicit user decision, and
before yielding at a phase boundary. Keep it an index, not proof, and never
store hidden reasoning, raw full logs, secrets, credentials, or private user
data. Record:

- latest request, binding constraints, exclusions, and current phase;
- plan path/revision/hash, explicit approval and implementation authorization;
- references to authoritative user messages or transcripts for those approvals;
- the user choice and named residual risks when reviewer convergence is waived;
- task names and requested model/effort for Explorer, reviewers, verifier, and Luna;
- context ID and resolved active context path;
- raw workflow ID → chosen task-base/context-ID and old-to-new generation mappings;
- immutable requested launch tuples and returned canonical targets, plus any
  host-configuration readback when exposed;
- independent plan/code round counts, verdicts, open findings, and dispositions;
- branch, base/`HEAD`, diff fingerprint, Luna-owned paths, and protected paths;
- verification results, deliberately unrun checks, evidence freshness, and next action.

Any prior ledger whose resolved path differs from the one active safe context
path is protected historical input. Never automatically move, copy, delete, or
overwrite it. Rehydrate the one active safe ledger from authoritative evidence
under the normal snapshot ordering.

On every writable resume, require authoritative exact recovery of both
`plan_round` and `code_round`, plus the complete invocation/verdict/finding
ledger, before any budget-dependent plan/code review continuation. If
`plan_round` or its plan invocation/verdict/finding ledger is missing, corrupt,
conflicting, or unrecoverable, block plan review and implementation. If
`code_round` or its code invocation/verdict/finding ledger is missing, corrupt,
conflicting, or unrecoverable, block code-review prompts, review-driven Luna
repair waves, final-verifier calls, and verified-complete claims. While blocked,
permit read-only evidence recovery and necessary safety/rollback actions only;
neither may guess, reset, waive, reconstruct, or substitute for authoritative
counter recovery. For unknown `code_round`, permit only cancel or an explicitly
incomplete handoff naming the unknown budget. Later authoritative recovery may
resume with the recovered remaining budget. Refresh plan revision, `HEAD`,
status, diff, affected files, and verification evidence. Mark stale claims
stale before use. Rehydrate lost Explorer/reviewer/Luna threads from current
authoritative sources without resetting either five-round counter. Context alone
is not proof of user approval: if the authoritative user-message or transcript
reference is absent, require renewed user approval before edits.

At material checkpoints, follow up with the Explorer only after a coherent
wave has yielded and writers are idle. Request a delta-only packet:

```text
Snapshot: plan revision | branch@HEAD | diff state | evidence freshness
Outcome: one sentence for the material change
Acceptance delta: implemented | missing | divergent criteria with evidence
Knowledge delta: verified facts | inferences | invalidated assumptions | uncertainties
Review ledger delta: finding | status | evidence | recommended disposition | verification
Decision required: none or one concrete manager/user decision
Next handoff: owner | bounded outcome | owned/protected paths | invariant | focused check
```

Do not request repeated plans, full diffs, raw logs, or directory dumps.
Use Explorer for source-checking disputed facts, cross-boundary risks, plan
assumption failures, security/persistence/platform contract issues, failures
surviving two focused attempts, or user decisions. Route routine compiler and
test defects directly to Luna.

## Authorized implementation

1. Start only after both plan approval and separate implementation
   authorization. Create or continue a task branch only when safe. Do not
   overwrite, revert, stage, or absorb unrelated worktree changes. Ensure all
   writers are idle before the next handoff.
2. Have the Explorer produce an implementation capsule at approval with the
   binding outcome, constraints/exclusions, approved plan and repository
   snapshot, verified paths/contracts/invariants, criteria-to-check mapping,
   protected paths, material uncertainties, and the bounded first Luna task.
   Treat this capsule as advisory context, not proof.
3. Reconcile the capsule against current files, the approved plan, the latest
   request, and authoritative constraints. Resolve stale or divergent claims
   before handing off; do not infer implementation authorization from Explorer
   output.
4. Only after that reconciliation, atomically spawn one Luna `worker` with the
   complete capsule, exact owned/protected paths, repository rules, acceptance
   criteria, proportionate verification, and the warning that user and other
   agent changes may coexist and must not be reverted. Reuse the Luna thread
   thereafter for bounded continuations and accepted fixes.
5. After each coherent Luna wave, inspect current files and evidence yourself,
   then request the Explorer's material delta. Verify the effective change
   against the latest request and plan; Explorer output never replaces current
   file inspection, focused audit, tests, builds, or behavior tracing.
6. Consolidate routine failures and bounded dependencies into the current Luna
   wave. Escalate to Explorer/manager after two focused attempts or sooner for
   a contract, scope, or user-decision issue. Pause affected edits and obtain
   renewed approval when the user materially changes scope.

## Independent code review and repair

1. After initial manager verification passes, spawn a fresh independent code
   reviewer with the exact `default`/`gpt-5.6-sol`/`xhigh` contract. Seed it
   with the latest request, approved plan path, diff basis, repository review
   rules, exact verification evidence, and the explicit read-only prompt
   contract; never seed Explorer conclusions.
2. Initialize the separate maximum-five `code_round` counter to `0`. Follow
   the reviewer invocation order above for the initial prompt and every
   follow-up; malformed-response follow-ups consume a round once their prompt
   delivery is attempted. Invocations 1 through 5 are allowed and a sixth is
   impossible. Require exactly one terminal token: `APPROVED`,
   `REQUEST_CHANGES`, or `NEEDS_REWORK`. For a malformed response, request one
   corrected verdict only when the pre-invocation check permits it; never infer
   approval.
   `NEEDS_REWORK` stops and surfaces structural rework.
   `REQUEST_CHANGES` enters disposition, one repair wave, re-verification, and
   reviewer recheck. Do not reset the budget when replacing a thread.
3. Preserve every code finding verbatim in the ledger. Ask Explorer to map
   each finding to current code, plan language, user decisions, evidence,
   repair owner, and required verification. Explorer may recommend
   accept/rebut/escalate but may not filter findings or decide disposition.
4. Have the manager adjudicate all findings. Consolidate all accepted findings
   for each review round into exactly one Luna repair wave. If ownership cannot
   safely combine them, stop and ask; never split the round into per-finding
   waves or overlapping writers. Run one risk-based verification matrix for
   that wave and return fresh evidence to the same reviewer when available.
5. Stop at reviewer `APPROVED`. If `code_round` reaches `5` with unresolved
   findings, a malformed response, or a reopened final-gate issue, report the
   incomplete state and do not claim completion. Keep the code-review cap
   blocking verified completion: the user may accept only an explicitly
   incomplete handoff, never an `APPROVED` or verified-complete claim. Treat
   `NEEDS_REWORK` as a user-visible stop.

## Fresh final gate and completion

1. After code-review convergence, have Explorer assemble a traceability packet
   mapping request and plan criteria, implementation evidence, ledger findings
   and fixes, verification results, deliberately unrun manual/platform checks,
   and residual uncertainty. Do not include an approval recommendation.
2. Spawn a new final verifier with the exact independent
   `default`/`gpt-5.6-sol`/`xhigh` contract and a new task name. Seed only
   authoritative paths, current evidence, and the explicit read-only prompt
   contract. Require it to inspect the current repository and return exactly
   `APPROVED`, `REQUEST_CHANGES`, or
   `NEEDS_REWORK`. Treat malformed output as a failed gate: stop and report it
   or obtain a corrected exact verdict without ever inferring approval.
3. On final-verifier `REQUEST_CHANGES` or `NEEDS_REWORK`, reopen the bounded
   code-review/repair loop only if `code_round < 5`, then run another fresh
   final verifier after convergence. Repair plus reviewer recheck consumes the
   next code round; a final-verifier call does not create or reset a budget.
   With no rounds left, report the issue and do not complete.
4. Before completion, reread the latest request and constraints, verify every
   named behavior, affected call path and platform, apply the focused audit
   rules, and confirm every changed line traces to the request. Report request
   and behavior verification separately from build/test verification, listing
   unrun checks and residual risks.
5. Declare completion only as manager. Treat worker, Explorer, reviewer, and
   verifier reports as evidence, not authority. Stop at the verified
   implementation handoff by default. Never automatically commit, push,
   merge, tag, bump versions, publish, or release; require a later explicit
   authorization for each Git or release action.

## Stop conditions

Stop and ask the user for a decision when exact role creation fails, scope or
product behavior is unresolved, safe ownership cannot be isolated, the plan or
code budget is exhausted, final verification remains unresolved, or a
destructive/external/device/broad-fixture check needs separate authorization.
