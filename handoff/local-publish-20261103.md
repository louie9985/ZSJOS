# Main publication — 2026-11-03 Beijing

## Registration
- Environment local (variable/marker absent); owner Codex /root; branch main; worktree D:/ZSJ-OS; base 5b25d5b1cc0c6475673b113e1a8ad1023b732cdc.
- Goal: user-authorized commit and push of current pending code to origin/main. Fetch confirms no remote divergence.
- Scope: four restored verifier/test/SQL/operations-document files, removal of test_runtime_verification.py, existing undo handoff and this publication record. Latest local-db-verification-20261102 undo delivery explicitly supersedes earlier verifier changes.
- Decisions: preserve confirmed rollback, including prior warn-only findings and route assertions; do not silently reapply the superseded fail-closed change. No business-route data/configuration changed by Git publication.
- Verification: static zsjos_db.py check PASS; focused schema regressions and staged diff checks, then normal commit/push and exact remote identity verification. No migration source numbering changes.
- Non-goals: further feature fixes, database/ledger/grant/service/deployment, branch/worktree changes or cleanup. Generated caches, tmp/editor files, reports/screenshots/raw workbook remain local.

## Delivery preparation
- Reviewed owning handoff's undo authorization, pre-task byte comparison and prior regression evidence. Publish existing undo source without additional implementation edits.
- Runtime/fresh data acceptance not rerun; static source checks are not business-data verification. Final commit/result reported after remote verification. Ownership released on completion.
- Publication evidence: 7 files staged; scoped whitespace check PASS; 23 schema-drift tests PASS. Only four pre-existing generated caches remain unstaged among tracked files; no database or runtime operation performed.
