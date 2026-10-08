# WorkBuddy sales skills

## Registration — 2026-10-08 (Asia/Shanghai)
- Environment: local (ZSJOS_AGENT_ENV unset; environment file absent).
- ID: local-workbuddy-sales-20261008. Owner: Codex /root.
- Branch: main. Worktree: D:/ZSJ-OS. Base: d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- Goal: 中世健销售每日计划和确认后跟进回填的 WorkBuddy 技能包、受控接口脚本、测试及接入说明。
- Non-goals: frontend/backend business changes, database, grants, dependencies, actual sales records, shared services, Git operations; no changes to other workstreams.
- Ownership: tools/workbuddy-sales/**; docs/operations/workbuddy-sales.md; this record. Separate record because the worktree contains concurrent disjoint work.
- Dependencies: existing ADMIN session and tenant context, lead calendar/detail/history/dictionary/catalog/follow-up APIs, WorkBuddy skill execution/knowledge/scheduling capabilities (integration verification pending).
- Verification: Python standard-library unit and HTTP contract tests, skill validation, UTF-8 and scoped diff/link checks. Live WorkBuddy and authenticated API checks only with an available authorized session; distinguish unverified integrations.
- Target branch / integration order: None.
- Product naming: user's explicit correction establishes 中世健; use it throughout new artifacts despite historical 中视间 spellings. Do not bulk rename historical source.
