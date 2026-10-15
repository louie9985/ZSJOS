# Order void integration rollback

## Registration — 2026-10-14 18:21:11 +0800 (Beijing)
- Environment: local. Owner: Codex /root. Branch: main. Worktree: D:/ZSJ-OS. Base: 3264cdeb4c0466a654c8c8cd0692d3a9f08fa871.
- Goal: undo only feature commit 0d53e1a513f43227c24fbcabce3adfa7a04be994 and integration-record commit 3264cdeb4c; retain feature branch and unrelated dirty work.
- Scope: exact 53 paths changed by those commits plus this rollback record; only order-void hunks removed from overlapping files. No push, service, permission or database operation. Target branch/integration order: main, one inverse commit.
- Verification: reverse-patch preflight, feature-path backup under %TEMP%/order-void-rollback-backup, unrelated diff-payload equivalence, untouched-file hashes, index-tree equality with pre-merge base except this record.

## Delivery — 2026-10-14 18:23:45 +0800 (Beijing)
- Result: rollback commit 907066a2ea on main removes feature 0d53e1a513 and integration record 3264cdeb4c. The committed tree matches pre-merge e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e except this rollback log. Feature branch codex/order-void remains at 0d53e1a513f43227c24fbcabce3adfa7a04be994.
- Changed files: exact inverse of the 53 integration paths plus this record. Contextual overlaps were resolved by removing only the added gift field and order-void documentation section, preserving other edits.
- Verification: all unrelated unstaged nonblank added/deleted line payloads match before rollback. All unrelated dirty/untracked file hashes match except concurrently updated handoff/local-pms-folder-import-20261014.md, which this task never wrote and leaves intact. Sales-performance documentation additionally matches the backed-up original minus exactly the committed order-void section; Git aligned blank lines differently, but no unrelated content was removed. Cached diff check passed. No runtime checks rerun because executable source returned to its pre-merge committed state with existing local work retained.
- No history rewrite, stash, unrelated cleanup, push, permissions/database changes or service restart. No deployment existed to roll back. Worktree D:/ZSJ-OS; environment local; owner Codex /root.
- Remaining work: None for rollback. This delivery append is committed separately. Ownership released.
