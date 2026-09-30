# Local remote merge — 2026-09-30

## Registration

- Environment: `local` (no `ZSJOS_AGENT_ENV`; `/etc/zsjos/agent-environment` absent).
- Workstream ID/owner: `local-merge-remote-20260930`; Codex `/root`.
- Branch/worktree/base: `main`; `D:/ZSJ-OS`; `421712db09f6593e546ba0b6214979ebf304c3b4`.
- Goal: merge remote `origin/main` at `a10411fff48cdf7cf68eccae072689460d9b22f6` according to local/test handoff evidence while preserving completed functionality and all existing local changes. Remote migration identities remain authoritative; unpublished local migrations move only when their number collides with a remote numbered migration.
- Migration decision: remote adds no numbered migration and remains at Core V286. Local `V287__exam_revoke_reedit.sql` is already the next free number, so retain V287 and do not create a needless V288. Preserve remote V285/V286 definitions and the local V287 prerequisite chain.
- Ownership: this handoff; Git merge/autostash conflict resolution; any directly conflicting portions of remote baseline, migration metadata, documentation or generated cache files. Existing exam-reedit and calendar-navigation workstreams retain ownership of their delivered local source changes.
- Non-goals: no commit/push, branch/worktree switch, rebase, force update, stash deletion, database/ledger/checksum mutation, service operation, permission change, temporary-artifact cleanup or unrelated refactor.
- Dependencies/integration order: first preserve current dirty tree through Git autostash, fast-forward/merge remote commits, restore local changes, then reconcile overlaps using `handoff/test_main.md`, `local-exam-reedit-20260930.md` and `local-calendar-side-navigation-20260930.md`.
- Verification: no unmerged paths/conflict markers; `HEAD` equals fetched `origin/main`; migration numbers V001 through V287 remain continuous/unique with exact V285/V286/V287 identities; remote V283/V284 baseline inventory and repair sources remain present; local exam-reedit/calendar functions remain present; schema baselines byte-identical; focused compile/tests only for conflict-touched logic; scoped diff checks. No database execution.

## Delivery — 2026-09-30 Beijing time

- Result: fast-forwarded local `main` from `421712db09f6593e546ba0b6214979ebf304c3b4` to remote `a10411fff48cdf7cf68eccae072689460d9b22f6` using Git autostash, then restored the complete pre-existing local dirty tree. No content conflicts or unmerged paths occurred.
- Remote preservation: retained the remote V283/V284 desired-schema inventory corrections, byte-identical baseline updates, optional `zsjos_data_repair_backup` manifest allowance, notice-statistics fixture correction, test handoff evidence, legacy-chain repair assets and scoped order-attribution repair scripts.
- Local preservation: exam revoke/reedit backend, Workbench and verification files remain present; all five calendar side-navigation changes and unified exam-day details remain present. Existing unrelated diagnostics, temporary output and tracked Python bytecode remain untouched.
- Migration identity: remote remains authoritative for V285 `order_imported_actor_snapshot` and V286 `multi_day_exam_schedule`. Remote introduced no numbered migration, so local `V287__exam_revoke_reedit.sql` remains the next free version and continues to require Core V286 in both ledgers. V001–V287 are continuous and unique; no V288 was created.
- Verification: schema baselines have identical SHA-256; no conflict markers or scoped whitespace errors; `HEAD` equals `origin/main` with ahead/behind `0/0`; Python syntax compilation passed for remote repair tools and local V287 tools; `zsjos_db.py check` passed manifests, migration order, desired schema, Java mappings, baseline versions and verification consistency. Existing focused backend/frontend/browser/MySQL evidence in the source handoffs remains applicable because the merge did not modify those local implementation files.
- Operations not performed: no database execution or ledger/checksum mutation, no service or permission operation, no commit/push, no branch/worktree switch, no stash deletion and no cleanup. Merge-workstream ownership is released.

## Publication authorization — 2026-09-30 Beijing time

- User explicitly authorized committing the current local code and pushing it to remote `origin/main`. Exact target is `https://gitee.com/louie9985/ZSJOS.git`, branch `main`; expected impact is one normal source commit containing the completed exam reedit, calendar navigation/day/full-name/remark-link work, announcement action placement, Lead submitter-assist deadline change, V287, tests, directly affected documentation and handoff records, followed by a non-force push.
- Exclude obvious local diagnostics and generated/runtime artifacts: `.failing_blocks`, `.playwright-mcp`, `.probe_*`, `.tmp_drift`, `.verify_*`, `backend/.mvn`, `content-review-tab.png`, `frontend/workbench/tmp` and tracked Python bytecode. Preserve them locally without deletion. No database execution, service/permission operation, branch/worktree change, rebase, force push or stash deletion is included.

## Publication delivery — 2026-09-30 Beijing time

- Committed the authorized local source as `943f26c677` (`feat: 完善考期重编与日历交互`): 68 files, 2,361 insertions and 199 deletions. The commit contains V287, exam revoke/reedit, calendar navigation/day/full-name/remark-link behavior, announcement action placement, Lead submitter-assist deadline behavior, tests, directly affected documents and handoff records.
- Pre-commit checks passed: no unmerged paths, conflict markers, whitespace errors, staged binary/runtime artifacts or high-risk secret patterns; `zsjos_db.py check` passed manifests, migration order, desired schema, Java mappings, baseline versions and verification consistency.
- Pushed by normal non-force update from remote `a10411fff4` to `943f26c677` on `origin/main`. Command-scoped direct HTTPS bypassed the unavailable configured localhost proxy without changing global Git configuration.
- Excluded diagnostics, temporary output, screenshot, local Maven configuration and tracked Python bytecode remain local and were not deleted. No database migration, ledger/checksum, service, permission, branch/worktree, force-push or stash operation was performed.
