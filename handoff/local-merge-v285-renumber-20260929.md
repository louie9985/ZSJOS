# Local merge: preserve cloud V285 and renumber local migration

## Registration — 2026-09-29

- Environment: `local` (fallback; no explicit environment marker configured).
- Workstream ID: `local-merge-v285-renumber-20260929`.
- Goal: merge `origin/main` into local `main` according to both local and test handoff evidence without losing either side's completed functionality; retain cloud `V285__order_imported_actor_snapshot.sql` and move the unpublished local multi-day exam migration from V285 to V286 with every active reference synchronized.
- Non-goals: no database execution or ledger/checksum mutation; no service lifecycle operation; no commit, push, branch/worktree operation, stash deletion, temporary-artifact cleanup or unrelated refactor.
- Branch: `main`; worktree: `D:/ZSJ-OS`; base commit: `5fadfb3e9be563ac4cb3e5c142308549944aa465`.
- Merge target and integration order: `origin/main` at `3f3741454433ede5eb196e8b6357c5ceb1d176b6`; first free the V285 pathname by renumbering the local unpublished migration to V286, then merge with autostash and reconcile overlapping tracked files from both handoffs.
- Ownership scope: local multi-day exam migration, its active verification/document references, this independent handoff, and merge-conflicted portions of the remote commit's 29 changed files. Existing unrelated local modifications and local-only artifacts remain preserved under their originating workstreams.
- Owner: primary Codex workstream for this chat.
- Dependencies: cloud V285 is authoritative and requires Core V284; local V286 must require cloud Core V285 in both ledgers. Remote order-actor snapshot and local multi-day exam schema definitions must both remain in `schema/core.sql` and `00-bootstrap-schema.sql`.
- Verification plan: confirm final V001–V286 continuity and exact V285/V286 identities; inspect active filename/version references; verify cloud order-actor behavior plus local multi-day exam behavior from handoff evidence; confirm both baselines are byte-identical; ensure no unmerged paths or conflict markers; compile affected Python verifiers and run scoped whitespace checks. Database migrations will not be executed in this workstream.

## Delivery — 2026-09-29 Beijing time

- Result: fetched and fast-forwarded local `main` to remote `3f3741454433ede5eb196e8b6357c5ceb1d176b6`, then restored the pre-existing local work through Git autostash. Remote order-actor history, cashback deduplication and performance-attribution changes remain present; local exam-calendar, pagination, cashback-search and performance-detail work remains in the worktree.
- Conflict resolution: `PerformanceReportTest.java` keeps the remote summary label `其他` and the local `PerformanceDetailReference.details` oracle. Generated `frontend/workbench/tsconfig.tsbuildinfo` uses the remote side. No unmerged paths or conflict markers remain.
- Migration identity: remote/deployed `V285__order_imported_actor_snapshot.sql` remains authoritative. The unpublished local exam migration is now `V286__multi_day_exam_schedule.sql`, requires Core V285 in both ledgers, and all active SQL/tool/API/operations references use V286. Both fresh schema baselines retain the order evidence column and multi-day exam schema and are byte-identical.
- Verification evidence: migration filenames are continuous and unique from V001 through V286; Python syntax compilation passed for the multi-day and order-actor recovery tools; the ZSJOS Maven reactor build and `PerformanceReportTest` passed (9 tests, zero failures/errors); scoped conflict and reference scans passed. No database migration, ledger/checksum mutation, service operation, commit or push was performed.
- Preservation: historical handoff records were not rewritten; a numbering correction was appended to `local-exam-calendar-ranges-20260929.md`. The merge-created autostash remains available and was not dropped. Existing unrelated local changes and artifacts remain untouched. Ownership for this merge/renumber workstream is released.

## Publication authorization — 2026-09-30 Beijing time

- User explicitly authorized committing the current local code and pushing it to remote `origin/main`. Target is branch `main` at `https://gitee.com/louie9985/ZSJOS.git`; expected impact is one normal commit containing the accumulated business source, tests, SQL, documentation and handoff records from the registered local workstreams, followed by a non-force push.
- Scope excludes obvious local runtime artifacts and diagnostics (`.failing_blocks`, `.playwright-mcp`, `.probe_*`, `.tmp_drift`, `.verify_*`, `content-review-tab.png`, `frontend/workbench/tmp`, local Maven wrapper/runtime files and tracked Python bytecode). Those files remain local and are not deleted. No database, service, branch, worktree, rebase, force-push or stash operation is authorized or performed.

## Publication delivery — 2026-09-30 Beijing time

- Committed the accumulated authorized source, tests, SQL, documentation and handoff records as `aded970b57` (`feat: 完善业务查询与多日考期支持`): 174 files, 5,193 insertions and 451 deletions. Pre-commit checks found no whitespace errors, unmerged paths, conflict markers, staged binary/runtime artifacts or high-risk secret patterns.
- Pushed the commit by normal non-force update from remote `3f37414544` to `aded970b57` on `origin/main`. The first configured localhost proxy was unavailable, so the operation used a command-scoped direct HTTPS connection without modifying global Git configuration.
- The explicitly excluded local diagnostics, temporary output and tracked Python bytecode remain outside the commit and were not deleted. Existing stashes remain untouched. No database migration, external service or permission change was performed.
