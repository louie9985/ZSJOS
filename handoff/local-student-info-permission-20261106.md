# 报名资料收集权限修复

## Registration — 2026-11-06
- Environment local, ZSJOS_AGENT_ENV unset. Branch main, worktree D:/ZSJ-OS, owner /root coordinator.
- Goal resolve the reported denied page after granting registration-material collection permission. Preserve server menu permission and business data scope.
- Scope frontend StudentInfoTasksPage and focused permission regression, directly related documentation. frontend_acceptance is sole frontend writer. backend_acceptance reviews backend contracts read-only until a specific necessary change is coordinated.
- Existing generated tsbuildinfo/pyc changes and untracked files are preserved. No role grants, database changes, service restart, commit, push or deployment in this repair scope.
- Verification inspect menu/controller/service permission contract, allow/deny cases and relevant frontend type/browser checks. Integration target None. Current base main7a7cf5e14b.
