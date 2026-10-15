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

## Registration update — subsequent main publication / 2026-10-15 Beijing
- Context: local/main/D:/ZSJ-OS; owner /root; base 8df3f65eb379b142c0ce0cb84b21e621e59b79b9. User requests committing local main and pushing origin/main again.
- Scope: pending Lead flow snapshots/history, current-owner conversion population, transfer/feedback notification entries, exam migration V319 -> V318, associated tests/docs/handoffs and this record. Preserve delivery-feature isolation.
- Verification: reviewed feature handoffs; static DB check PASS, Core source continuity restored through V318. Existing old-number environments still need separately reviewed ledger/checksum mapping. Feature runtime evidence and known failures remain in their handoffs; publication does not add runtime/database acceptance.
- Non-goals unchanged: no business edits, feature-branch publication, database/ledger/service/deployment operations or artifact cleanup.

## Delivery preparation — subsequent main publication / 2026-10-15 Beijing
- Snapshot: 74 files including the existing exam-export V318 rename, Lead flow/history, conversion ownership and notification fixes. No additional business source changes during publication.
- Verification: staged diff check passed; no conflict markers or high-confidence private-key/token matches in prior staged scan. Rechecked staging and upstream after user continuation: main and origin/main remain at 8df3f65eb3, with only caches and temporary artifacts outside the staged snapshot. Static database consistency passed during preparation.
- Integration: user reiterated commit and push authorization; commit staged snapshot then normal push to origin/main and verify remote identity. Final hash/result reported in conversation.
- Limits: no runtime/deployment or database operation; feature handoff acceptance limits and old-number database rollout review still apply. Temporary files and caches retained; publication ownership released after completion.
