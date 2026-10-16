# Local delivery feature integration
## Registration
- Environment local (variable unset, environment file absent); owner Codex root.
- Feature codex/delivery-manager-feature-20261015 at 4e6bcaa498, D:/ZSJ-OS-delivery-manager; target main ca1bfdf85d, D:/ZSJ-OS.
- Goal: user-authorized local commit and merge of feature plus reviewed repairs, preserving main changes and resolving migration collisions. Integration order: feature snapshot, main merge, conflict resolution and checks. No push.
- Ownership: feature changes from prior review handoff; overlapping merge files; migration source/verifiers/current docs and this record. No unrelated caches/temp files.
- Migration plan: main V318 exam/V319 rest-days unchanged; employee menu V320, delivery domain V321, review V322. Historical handoffs unchanged; no live ledger rewrite, organization restoration, role grants or service restart.
- Verification: dirty-file backup/hash audit, focused Java tests, frontend typecheck/build and API contract, static numbering check, controlled migration tests as applicable.
- Backup: D:/ZSJ-OS-backups/delivery-merge-20261016.
