# Remote integration — 2026-10-08 Beijing time

## Registration
- Environment: local (environment variable unset and marker file absent). Owner: Codex /root; workstream local-merge-remote-20261008.
- Branch/worktree/base: main; D:/ZSJ-OS; d43cc180c3a3e92740888b521fc2ea4e006a0560.
- Goal: merge origin/main d2f836006f according to local/test handoffs, preserving local repurchase, announcement pagination, upload-type and last-month performance changes and remote notice-attachment/drift fixes.
- Ownership: this record and Git integration/conflicting portions only. Non-goals: unrelated source edits, database/service operations, commit/push, branch/worktree changes and artifact cleanup.
- Dependencies/integration: capture SHA-256 of existing changed/untracked files, merge with autostash, verify restoration and remote changes. Remote and local migration chains both end at V287 with no unpublished additions; retain authoritative cloud numbering without unnecessary renumbering.
- Verification plan: compare every captured local file hash; HEAD/upstream and conflict checks; baseline hash equality; migration uniqueness/continuity; focused remote schema-drift regressions and static database check. No database execution.

## Delivery — 2026-10-08 Beijing time
- Context: local/main/D:/ZSJ-OS; HEAD d2f836006ff07b5517bcd6d485d72df788bf3cb0; owner /root; registration above applies.
- Result: fast-forward merged the one remote commit with autostash and restored local changes without conflicts. Remote notice attachment save correction, bounded backup allowlist/drift diagnostics and V287 baseline columns are integrated. Local repurchase, announcement pagination, upload configuration and last-month performance changes remain present.
- Preservation evidence: captured 89 changed/untracked local files before merge; 84 retained exact SHA-256. Five tracked files were normalized by Git line-ending conversion (useUploadFileRule.ts, ui-guidelines.md, HomeAnnouncementPanel.tsx, today-tasks.css and handoff/main.md); comparison against premerge autostash c14f59ed04 confirms no content differences. No manual stash deletion or artifact cleanup performed.
- Migration decision: neither side adds a numbered migration; cloud V285/V286/V287 identities preserved, V001–V287 continuous and unique. No renumbering required.
- Verification: eight schema-drift regression tests passed; zsjos_db.py check passed; both baseline hashes equal; git diff --check passed; no unmerged paths; HEAD/origin ahead-behind 0/0. Remote notice-attachment runtime/tests remain unverified as disclosed by its source handoff; no notice logic was changed during integration.
- Changed files: remote commit's 14 files plus this delivery record; local pending changes restored. Dependencies: None. No commit/push, database or service operation performed. Remaining: local pending work is uncommitted; deployment/runtime acceptance is outside this merge. Integration ownership released.

## Registration update — 2026-10-08 Beijing time / second remote integration
- Context: local/main/D:/ZSJ-OS; base d2f836006ff07b5517bcd6d485d72df788bf3cb0; target origin/main cf592507644bcce748b1458c8917689343e5373b; owner /root. User again authorized remote integration with cloud numbering preserved.
- Scope: autostash integration and two overlapping files LeadAppealServiceImpl.java/LeadManagementServiceImpl.java. Retain remote definition pinning/null handling/Partner cursor binding and local restoration helper/supervisor overturn/owner eligibility. Other local work remains owned by its delivered handoffs.
- Migration review: cloud numbered migrations end at V287; local unpublished V288–V294 are unique and subsequent, so no numbering collision exists and no renumbering is required.
- Verification: capture existing file SHA-256; restore and compare local content against autostash; inspect combined overlapping files; migration continuity, baseline equality, static DB consistency, scoped tests where practical. No publication, database/service operation or cleanup.

## Delivery — second remote integration / 2026-10-08 Beijing time
- Context: local/main/D:/ZSJ-OS; owner /root; HEAD/origin/main cf592507644bcce748b1458c8917689343e5373b. Registration update above applies.
- Integration: fast-forward with autostash succeeded without conflicts; no commit or push. Reviewed local feature records and remote test_main additions rather than choosing one entire side.
- Preservation: captured 599 changed/untracked file hashes before integration. All captured untracked files are byte-identical except this intentionally appended record. Comparison against premerge autostash c9b1becacd22edb9da2fa0bdebdd204846a2b943 shows zero content differences outside the 36 remote-commit paths; tracked raw-hash differences are Git newline normalization. Existing local modifications remain uncommitted, and no stash/object cleanup was performed.
- Overlap review: LeadAppealServiceImpl retains remote validated BPM definition pinning and null-safe name handling together with local LeadValidityRestoration. LeadManagementServiceImpl retains remote Partner submitter cursor binding together with local supervisor overturn and restoration owner eligibility. Remote upload uniqueness, typed filters, BPM recovery primitive and their documentation/tests are present.
- Migrations: cloud ends at V287 and introduces no new numbered scripts; unpublished local V288–V294 remain unchanged because no collision exists. V001–V294 are continuous and unique. Cloud V285–V287 identities are preserved; no deployed SQL/checksum/version ledger was rewritten.
- Verification: zsjos_db.py check PASS; both schema/core.sql and 00-bootstrap-schema.sql SHA256 equal 29B058660C8A1C90323D2565FDB93BE3E67BFE201BF3D7AF1136B786E8710F3F; git diff --check PASS; no unmerged paths or conflict markers in integrated files; HEAD/upstream ahead-behind 0/0; three appeal recovery preflight unit tests PASS.
- Limitations: merged Java behavior was source-reviewed but Java tests/build and live API/browser/database execution were not rerun in this integration. Earlier independent feature evidence does not establish runtime compatibility of the merged tree. No database/shared-service operation was performed, including remote repair execution. Runtime acceptance remains unverified.
- Changed scope: 36 remote files and this append-only record; restored local work otherwise untouched. Integration ownership released; no publication authorized or performed.
