# Workstream: local-account-cover-label-20261008

- Environment: local (environment variable and environment file absent; fallback).
- Goal: 修正主页图未上传时的责任标签，按服务端 cover.ownerType 展示运营填写，与维护弹窗一致。
- Non-goals: 数据库、上传权限、其他字段、布局、依赖、共享服务、部署及 Git 操作。
- Branch: main
- Worktree: D:/ZSJ-OS
- Base commit: d2f836006ff07b5517bcd6d485d72df788bf3cb0
- Owner: Codex /root, current chat
- Ownership: frontend/workbench/src/components/AccountProfilePanel.tsx; docs/api/media-account-profile.md; this handoff.
- Dependencies: existing profile.config.fields and ownerType label renderer; no new dependency.
- Target branch / integration order: None / None
- Verification: scoped diff and UTF-8 checks, existing profile responsibility tests, Workbench typecheck, browser inspection if a usable browser/runtime exists.
- Status: active

## Delivery — 2026-10-08 14:07:00 +08:00

- Environment / branch / worktree: local / main / D:/ZSJ-OS; registration above applies.
- HEAD: unchanged d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- User goal: 主页图由运营填写，修正未上传图片时错误的责任待配置提示。
- Key decisions: Reuse enabled cover field ownerType and existing tag renderer, as in the editor. OPERATOR displays 运营填写; UNASSIGNED alone displays 责任待配置; absent/disabled cover has no fabricated responsibility. Existing business contract already specifies operator maintenance.
- Result: Replaced the hardcoded empty-cover tag; synchronized the account-profile API documentation.
- Changed files: frontend/workbench/src/components/AccountProfilePanel.tsx; docs/api/media-account-profile.md; this handoff.
- Verification: npm test -- src/services/mediaAccountProfile.test.ts PASS (4/4); npm run typecheck PASS; scoped git diff --check PASS; strict UTF-8 decoding PASS; scoped diff reviewed. Existing tests cover responsibility/completeness/change handling, not rendered-browser acceptance.
- Unverified: Browser UI inspection unavailable (browser control returned unsupported call; no local listener found on inspected standard frontend/backend ports). Authenticated target response/configuration and live upload not exercised.
- Dependency / integration impact: None. No database, permission, dependency, shared-service, deployment or Git operations; unrelated changes preserved.
- Remaining work: Target browser visual acceptance after runtime availability.
- Status: completed; file ownership released.
