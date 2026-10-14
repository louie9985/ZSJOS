# Order void integration rollback

## Registration — 2026-10-14 18:21:11 +0800 (Beijing)
- Environment: local. Owner: Codex /root. Branch: main. Worktree: D:/ZSJ-OS. Base: 3264cdeb4c0466a654c8c8cd0692d3a9f08fa871.
- Goal: undo only feature commit 0d53e1a513f43227c24fbcabce3adfa7a04be994 and integration-record commit 3264cdeb4c; retain feature branch and unrelated dirty work.
- Scope: exact 53 paths changed by those commits plus this rollback record; only order-void hunks removed from overlapping files. No push, service, permission or database operation. Target branch/integration order: main, one inverse commit.
- Verification: reverse-patch preflight, feature-path backup under %TEMP%/order-void-rollback-backup, unrelated diff-payload equivalence, untouched-file hashes, index-tree equality with pre-merge base except this record.
