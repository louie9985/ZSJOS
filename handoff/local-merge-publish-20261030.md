# Main merge and publication — 2026-10-30 Beijing

## Registration
- Environment local (variable/marker absent); owner Codex /root; branch main; worktree D:/ZSJ-OS; base ba3e687e678ce92b67f16615244e47007d0892d4; target origin/main 347e93142b.
- Goal: user-authorized handoff-guided merge preserving cloud migration identities/local features, then commit and normal push to origin/main.
- Scope: Git publication and this record. Remote operations/repair/docs have no production-source overlap; only Workbench cache overlaps. Local scope includes Person/Lead identity integration, homepage task split, Lead sorting/scope, order labels/navigation and refresh control removal, with related tests/docs.
- Decisions: cloud through V354; pending V355 unique/subsequent, no renumbering. Local identity delivery and subsequent read audit preserve its approved business changes; do not replay remote or local repair scripts. Keep raw imports, evidence, reports, screenshots, editor/temp files and caches local.
- Plan: capture all modified tracked SHA256; retain cache stash; fast-forward remote; restore local cache; compare source preservation, static DB, numbering and focused regressions; stage source/tests/SQL/docs/handoffs only, commit, push, verify exact remote hash.
- Non-goals: database/ledger/role changes, service/deployment, dependency or branch/worktree changes, cleanup. Per-feature runtime and release limitations remain in owning handoffs; publication adds no full runtime acceptance.

## Delivery — 2026-10-30 Beijing
- Integration: fast-forwarded remote 347e93142b (40 paths), no production-source overlap/conflicts. All 96 pre-existing modified tracked files retain exact SHA256 after restoring four caches from retained stash 512da24919fdaedc68952779ab4a5937f0498f25. New local feature source and remote operations history preserved.
- Migrations: cloud V001–V354 unchanged; local V355 unique/subsequent. Core V001–V355 continuous with no duplicates; no renumbering, historical checksum or ledger adjustment.
- Verification: static zsjos_db.py check PASS; schema-drift/migration-execution/numbering 32 tests PASS; 15 imported Python files parse successfully without execution. Staged conflict-marker/private-key-token pattern counts zero; no staged binary/temp artifacts. Per-feature implementation test evidence remains in owning handoffs; no new full server build, live API/browser, database fresh/upgrade acceptance claimed by this Git operation.
- Publication: commit scoped local source/tests/SQL/docs/handoffs, normal push origin/main, verify exact remote hash and report identity after execution. Reports, raw workbook, screenshots, editor/temp files and four caches remain local. No repair script run, actual data/role/service/deployment/feature-branch action or cleanup. Ownership released after remote verification.
