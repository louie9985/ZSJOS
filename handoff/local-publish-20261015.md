# Local publication — 2026-10-15 Beijing

## Registration
- Environment local; owner Codex /root; branch main; worktree D:/ZSJ-OS; base 7ddd776265db39a8bdec7238867271baf17bbcc5.
- Goal: user-authorized commit of pending source/tests/migrations/documentation and normal push to origin/main, including four existing local commits.
- Ownership: this record and Git publication. Non-goals: feature changes, restoring rolled-back order-void functionality, migration renumbering, database/services/deployment or cleanup.
- Dependencies: feature handoffs including order-void rollback and delivery-manager checkpoint. Integration order: stage current snapshot, commit, normal push, verify remote identity.
- Verification: staged scope and conflict/credential patterns, static database consistency, remote equality. Exclude temporary artifacts, local reports, backend/.mvn, frontend/workbench/tmp, Python caches and TypeScript build info.

## Verification and delivery preparation
- Existing four commits include order-void feature and its explicit revert; preserve the resulting rollback state and history.
- Database static check fails due to missing Core V319 between V318 and V320; the delivery-manager handoff already records this blocker. No placeholder migration, database marker or automatic renumbering introduced. Full migration/release acceptance remains blocked.
- Feature-specific tests and acceptance limits remain in their handoffs; publication does not rerun full builds or runtime tests. Final commit and push result will be reported after Git verification.

## Delivery record — 2026-10-15 Beijing
- Context: registration above; pre-publication HEAD unchanged; owner /root. Snapshot includes 435 source/test/SQL/documentation/handoff files.
- Checks: no staged conflict markers or high-confidence private-key/token patterns; no staged binary files. Whitespace check reports five existing EOF blank lines in tests; preserved without unrelated edits. Database migration continuity remains blocked by missing V319 as recorded above.
- Decision: publish current source snapshot and existing revert history as explicitly requested; no deployment-readiness claim. Temporary/report/cache artifacts remain local.
- Integration: normal commit and push to origin/main; actual outcome and commit identity reported after remote verification. No database or service operation. Ownership released on publication completion.

## Registration update — main isolation publication / 2026-10-15 Beijing
- Context: local/main/D:/ZSJ-OS; owner /root; base 5b56fa071e. User explicitly authorizes publishing the inspected local main isolation state to origin/main.
- Scope: existing two isolation commits, pending exam-export V320 -> V319 rename, associated API documentation/test and renumber handoff, plus this record. Feature branch publication is outside this operation.
- Verification: upstream is ancestor; reviewed source diff and isolation handoff; preserve removed delivery feature. Core baseline copies match; missing Core V318 remains a known release blocker after isolation. No migration execution, ledger reconciliation or new source-number decision.

## Delivery preparation — main isolation publication / 2026-10-15 Beijing
- Result: publish main's delivery feature removal and existing exam renumber changes. Feature branch codex/delivery-manager-feature-20261015 remains local and untouched; historical code remains reachable.
- Files: docs/api/exam-calendar.md; exam-export migration renamed V320 to V319; tools/test_exam_export_permission.py; handoff/local-migration-renumber-20261015.md; this append.
- Verification/limits: scoped staged diff check and remote identity verification; no full runtime/database acceptance. Migration chain missing V318, and previously applied old-number databases require separate ledger/checksum rollout review.
- Integration: commit then normal push; final hash/result reported after command completion. Local caches/probes/reports retained unstaged; no database/service operation. Ownership released after publication.
