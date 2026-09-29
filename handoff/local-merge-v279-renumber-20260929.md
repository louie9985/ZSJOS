# Local merge: preserve cloud V279 and renumber local migrations

## Registration — 2026-09-29

- Environment: `local` (fallback; no explicit environment configured)
- Workstream ID: `local-merge-v279-renumber-20260929`
- Goal: merge `origin/main` into local `main` without losing either side's delivered functionality; keep the cloud-owned attribution migration as V279 and move the local unpublished migration chain from V279–V283 to V280–V284 with all active references synchronized.
- Non-goals: no database execution or shared database changes; no service operations; no commit, push, branch switch, worktree operation, stash deletion, or unrelated cleanup.
- Branch: `main`
- Worktree: `D:/ZSJ-OS`
- Base commit: `532286468ee0e0658b2330cedceca4be7913f00a`
- Merge target and integration order: `origin/main` at `3d630e1f201312ae27e968b8641e61aed84e2195`; renumber unpublished local migration files first to remove the V279 path collision, then merge and reconcile overlapping tracked files from both sides.
- Owner: primary Codex workstream for this chat.
- Ownership scope: the five local migrations being renumbered; their active documentation, verification scripts and test references; this workstream record; merge-conflicted portions of the nine files changed by the remote commit. Existing unrelated local modifications remain owned by their originating workstreams and must be preserved.
- Dependencies: cloud V279 remains authoritative; local migrations retain their existing sequence and gain one version number; `handoff/main.md` is append-only evidence for reconciling overlapping functionality.
- Verification plan: confirm the final V279–V284 sequence and prerequisites, search for stale active filenames/versions, run migration-numbering/static validation, confirm no unmerged paths or conflict markers, inspect the merged remote diff and handoff evidence, and run scoped `git diff --check`. Database migrations will not be executed in this workstream.

## Delivery — 2026-09-29

- Merge result: local `main` fast-forwarded from `532286468ee0e0658b2330cedceca4be7913f00a` to `3d630e1f201312ae27e968b8641e61aed84e2195`; `HEAD` and `origin/main` are equal with ahead/behind `0/0`. No commit or push was created.
- Cloud preservation: retained tracked `V279__attribution_org_provenance_and_identity_split.sql`, `org_source` in both baselines, `PerformanceAttributionDO.orgSource`, `PerformanceSnapshotService` frozen writes, backfill current writes, sales-performance documentation and the remote append-only handoff delivery.
- Local renumbering: unpublished local migrations moved in order: media-student service period V279->V280, cashback control V280->V281, calendar notifications V281->V282, new-media analysis V282->V283 and notice statistics V283->V284. Procedure names, prerequisites, messages, menu audit markers, both ledgers, filename checksums, active docs and Python verification references use the new numbers. V280 explicitly requires cloud V279 in both ledgers.
- Conflict resolution: autostash produced conflicts only in `handoff/main.md`, `00-bootstrap-schema.sql` and `schema/core.sql`. The handoff keeps the shared prefix, remote V279 delivery and all local unique append-only entries. Both schema files retain remote `org_source`/repair-marker objects and local calendar-notification baseline objects. No unmerged path or conflict marker remains.
- Verification: static migration scan passed with one file for every version V001–V284 and the expected V279–V284 tail; five affected Python verifier files compile; baseline and bootstrap schema are byte-identical; stale old filenames are absent outside historical handoff; scoped whitespace and diff checks pass; cloud V279 is tracked. No database or service command was run.
- Existing-history limitation: `handoff/main.md` contains pre-existing whitespace/control-character defects in historical local entries, so whole-file `git diff --check` remains unsuitable. The merge reduced the file to remote history plus the 1,251-line local unique tail instead of concatenating two complete histories; this workstream's independent handoff and all changed source/doc scopes pass their checks.
- Safety: the merge-created autostash remains at the top of the stash list as recovery evidence and was not dropped. Existing unrelated staged, modified and untracked work was preserved.
- Database rollout note: some handoff evidence says old-numbered development copies were previously applied or verified. No ledger/checksum rewrite was performed. Any affected database requires a separately authorized inspection and scoped reconciliation before running the renumbered chain.

## Publication authorization — 2026-09-29

- User explicitly authorized committing the current local code and pushing it to remote `main`. Target is `origin/main` on Gitee; fetch confirmed local and remote both started at `3d630e1f201312ae27e968b8641e61aed84e2195`.
- Publication scope: all current tracked changes plus untracked business source, tests, migration SQL, API/frontend documentation and handoff records that belong to the completed local workstreams.
- Excluded local-only artifacts: `.failing_blocks/`, `.playwright-mcp/`, `.tmp_drift/`, `.probe_*.py`, `.verify_*`, `backend/.mvn/jvm.config`, `content-review-tab.png`, Python caches and `frontend/workbench/tmp/`. They are not deleted and remain local.
- Pre-push checks: staged conflict-marker and credential scan, staged whitespace review with historical handoff exceptions called out, migration-chain/baseline verification, commit creation, push to `origin main`, then remote commit equality verification.
