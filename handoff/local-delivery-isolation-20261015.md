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
