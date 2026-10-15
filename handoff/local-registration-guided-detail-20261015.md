# 公共池流程引导详情

## Registration — 2026-10-15 Asia/Shanghai
- Environment: local; ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent, fallback local.
- Workstream/owner: local-registration-guided-detail-20261015 / current Codex agent.
- Branch/worktree/base: main / D:\ZSJ-OS / 315efd729f2e049fc2639194567d86077ae91184.
- Goal: implement approved 45:55 information/three-step operation detail, responsive mobile access, persistent action footer, draft guard and stale response isolation.
- Non-goals: backend/API/SQL/permissions changes, other student/config pages, deployment, Git publication.
- Ownership: RegistrationPoolPage section in frontend/workbench/src/pages/RegistrationPages.tsx; src/styles/pages/registration.css scoped additions; test/registration-guided-detail* fixtures/browser tests; docs/frontend/registration-guided-detail.md; this handoff.
- Dependencies: delivered local-registration-early-delivery-20261015 changes retained; its ownership released. Existing pending changes in RegistrationPages.tsx preserved. No active shared-file dependency.
- Verification: npm typecheck, focused browser synthetic API cases and 1920/1440/1280/390 widths, CSS guard and scoped diff. No authenticated business writes.
- Target branch/integration order: None.

## Delivery — 2026-10-15 15:58 Asia/Shanghai
- Branch/worktree/HEAD: unchanged from registration.
- Result: implemented 45:55 detail with flat information fields, current delivery and collapsed first assignment, numbered three-step operations, persistent footer and container-based single column. Mobile task selection now opens usable detail with return to list.
- Preserved early-delivery changes, per-product scope, historical routes, attachments and terminal results. Checklist waits for server response; no client promotion to completable. Update permission gates all editing controls. Pending writes disable completion. Generation/identity guards prevent stale mutations overwriting another detail; retry uses requested task ID.
- Draft protection covers task/filter/group/page/refresh/mobile return and browser unload. Discard restores persisted class choices without saving. External application route navigation is not globally intercepted.
- Files: pool section of RegistrationPages.tsx; scoped registration.css; registration-guided-detail.html/.tsx/-browser.py fixtures; docs/frontend/registration-guided-detail.md; this handoff. Pre-existing student-page changes in the same TSX retained.
- Verification: final npm run typecheck passed. Browser fixture passed 1920/1440/1280/390 and 1000px container fallback, long content/footer geometry, independent scroll, mobile return, draft cancel/discard/refresh, assignment/finance/complete, denial, three terminal states, checklist failure/retry, delayed item/class/detail responses, attachment upload/delete, close required reason, legacy assignment, detail retry and class failure. Browser page-error list empty. Screenshots under frontend/workbench/tmp/registration-guided-<width>.png are local evidence, not production artifacts.
- CSS guard: 28 passed / 2 pre-existing failures; font literals in calendar-time-grid.css and content-review-attachments.css, missing --crm-color-fill-tertiary in calendar-colors.css. No modified registration styles reported. Scoped git diff --check passed.
- Limitations: browser uses synthetic API responses with live transport blocked. Authenticated business flow, real storage upload/clipboard integration, backend concurrency and deployed acceptance remain unverified. No API/SQL/dependency changes, deployment, restart, commit or push. Production build not applicable to unchanged routes/assets/bundling.
- Dependencies/integration: None; no shared service started or reconfigured. Ownership released.
