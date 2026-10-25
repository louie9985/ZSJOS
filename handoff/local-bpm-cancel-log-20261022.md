# BPM cancellation log adjustment

- Environment: local; workstream: local-bpm-cancel-log-20261022; owner: Codex current chat.
- Goal: change the missing-task and terminal-task cancellation skip logs to DEBUG and use the current method name.
- Non-goals: workflow behavior, business data, other log branches, deployment or service restart.
- Branch: main; worktree: D:\ZSJ-OS; base: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Owned files: this record and backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java (only the two processTaskCanceled log statements).
- Dependencies: none; target branch/integration order: None.
- Verification: scoped diff and whitespace checks; confirm both branches still return and cancellation updates remain unchanged. No runtime tests required for log-only edits.

## Delivery

- Changed exactly two log statements from ERROR to DEBUG and corrected their method prefix to processTaskCanceled. Control flow and task updates are unchanged.
- Verified source diff (2 insertions/2 deletions) and scoped git diff --check passed. No runtime tests, database writes, deployment, restart, commit or push performed.
- Ownership released.
