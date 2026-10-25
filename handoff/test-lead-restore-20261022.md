# Test Lead restoration 2026-10-22

## Registration — 2026-10-22 19:44 Beijing

- ID / owner: test-lead-restore-20261022 / Codex.
- Goal: execute the explicitly requested database restoration of `LD202609180108` to its confirmed former owner in test tenant 1, preserving education ownership and restarting qualification.
- Non-goals: permission changes, application fixes, notification delivery, migrations, historical rewrites, production changes.
- Branch / worktree / base: `main`, `/opt/zsjos`, `404387de566fcb9abf4b264f0e005bf0566ea209`.
- Exclusive ownership: this record, `script/sql/mysql/repairs/restore_recycled_lead_20261022.py`, `docs/operations/lead-restore-20261022.md`; one target Lead and its new assignment, qualification task, performance attribution and business events.
- Dependencies: current MySQL 8 schema; verified account, education assignment history, feature permissions and enabled default qualification rule. Existing unrelated working-tree changes remain owned elsewhere.
- Verification: isolated temporary tables copied from actual MySQL structures; successful application, repeat zero-write, injected failure rollback and changed-state rejection; protected preimage backup; transaction locks, postconditions and UTF-8 HEX checks; committed-state readback.
- Authorization: current user explicitly requested a direct database operation on this Lead and target owner, following the previous identification of the test environment.
- Target branch / integration order: None.

## Delivery — 2026-10-22 19:50 Beijing

- Branch / worktree / HEAD: unchanged from registration.
- User goal: directly restore `LD202609180108` to the explicitly designated former owner in test tenant 1.
- Key decisions: use existing recycled-to-owned transfer semantics with proven education identity; preserve all historical facts; restart qualification from the actual operation time; no permissions, notifications, deployed services or application behavior changes.
- Result: committed at 19:47:41 Beijing. One Lead updated; one transfer history, one qualification task, one performance attribution and two business events inserted atomically. Owner references agree; round 3, 72-hour deadline 2026-10-25 19:47:41. Repeat actual execution returned zero writes.
- Changed files: `script/sql/mysql/repairs/restore_recycled_lead_20261022.py`, `docs/operations/lead-restore-20261022.md`, this handoff record. Existing unrelated edits preserved.
- Verification: MySQL 8 actual-schema temporary clones passed six injected rollback points, stale-version refusal, successful execution, zero-write replay and unchanged prior history/other Lead fields; UTF-8 HEX checks passed. Actual post-commit independent SQL joined Lead, user, history and task and verified two unique events. Python syntax check passed. Protected pre/post images stored outside repository, directory 0700/files 0600. Initial backup path and temporary-table DDL issues occurred before live mutation and were corrected before successful verification.
- Dependency / integration impact: None; one scoped test-database operation, no schema/version-ledger changes, commits, publication or deployment. Existing database connection helper reused; no new dependency.
- Remaining work / limits: no browser or authenticated API validation. Existing stage projection returns `first_follow_pending` because actual first-follow time is empty, despite exceptional transfer's comment describing direct qualification; new qualification task is pending. User informed of this distinction; no invented follow-up facts or unsolicited application fix. Supervisor UI/request failure itself was not reproduced.
- Ownership: released on delivery.
