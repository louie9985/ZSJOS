# 移除考试收集局部刷新入口

## Registration
- Environment local (environment variable and /etc identity absent), owner /root, ID remove-exam-refresh-20261109.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Goal remove exam collection manual refresh controls and record durable UI direction.
- Scope frontend/workbench/src/pages/ExamCollectionPage.tsx, frontend/workbench/AGENTS.md, this record. Existing edits preserved.
- Dependencies None. Target branch/integration order None.
- Verification scoped diff, typecheck, existing inbox guards, browser availability.

## Delivery 2026-11-09 16:45 Beijing
- HEAD unchanged. Removed detail/list refresh buttons and disabled exam task table default reload. Automatic mutation updates and failure retry preserved. Added durable workbench UI rule. Other existing edits preserved.
- Changed files are the three registered scope files. Dependencies/integration impact None.
- Verification typecheck passed, scoped diff check passed, source inspection confirms no ReloadOutlined or refresh button in ExamCollectionPage. Existing inbox guards 5 passed / 3 failed on other pages and obsolete refresh requirement. Current explicit user direction takes precedence over that old refresh assertion.
- Browser visual verification unperformed. Authenticated exam collection flow was not established in this task, so actual rendered layout remains unverified. No deployment performed. Ownership released.
