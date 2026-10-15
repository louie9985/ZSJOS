# Delivery feature isolation

## Registration
- Environment local (variable unset, environment file absent); owner Codex root; ID local-delivery-isolation-20261015.
- Goal: user-requested extraction of this chat delivery-manager feature from main into an independent branch/worktree.
- Base main 665fe9bf93c267788be7c8091ff14e93a36dedcc, worktree D:/ZSJ-OS.
- Target codex/delivery-manager-feature-20261015, D:/ZSJ-OS-delivery-manager. Integration order: main scoped removal then feature restoration; future merge only after review. Existing codex/delivery-manager-isolation remains untouched.
- Ownership: 69 feature files enumerated in D:/ZSJ-OS-backups/isolation-20261015/main-content.json; this record; delivery migration renumber changes transferred from completed local-migration-renumber-20261015. Shared hunks individually selected. Historical handoffs retained.
- Non-goals: no remote push/history rewrite, database or grant rollback, service restart, OCR fixes, or unrelated changes.
- Dependencies: mixed published commit; exam migration renumber remains unrelated uncommitted work. Feature migrations removed from main leave numbering gaps requiring separate integration planning before release.
- Verification: byte-preservation audit for unrelated workspace files, reversible Git tree reconstruction, backend focused tests, Workbench typecheck/build, Admin contract diff check. Runtime/database acceptance not implied.

## Delivery - 2026-10-15 10:24 Asia/Shanghai
- Main removal commit 38955a0e21; isolated feature restoration b4768812e3. Source scope 69 files; original mixed published commit preserved in history. No push.
- Worktrees: main D:/ZSJ-OS; codex/delivery-manager-feature-20261015 D:/ZSJ-OS-delivery-manager. Continue this feature only in the latter.
- PASS: isolated committed source tree equals 665fe9bf93 except this handoff. Unrelated main workspace files byte-identical to backup after checks. Shared hunks preserve exam export, student service, public-sea responsibility, last-week performance and complaint attachment repair.
- PASS on main: backend focused 84 tests, zero failures/errors/skips; Workbench typecheck and production build; both workspace diff checks. Admin shared API diff checked; Admin runtime/typecheck not rerun, previous unrelated withdrawal errors remain unverified here. Feature tests reused from prior delivery because committed source is identical; no new feature runtime acceptance claimed.
- Migration renumber overlay preserved uncommitted in feature worktree, including exam dependency copy. Original unrelated exam edits remain uncommitted on main. Main omits feature V318/V321; sequence gaps require separately scoped release planning. Do not deploy main against the feature-configured local database without reviewing schema/menu/grant compatibility.
- Backups and manifest: D:/ZSJ-OS-backups/isolation-20261015. No database, role or running service changes. Remote main still contains the earlier published feature until local removal is explicitly pushed.
- Remaining: OCR findings and normal supervisor write-flow acceptance continue on feature branch; migration ledger reconciliation/full-chain acceptance is separate. Ownership released.
