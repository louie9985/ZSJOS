# Workstream Handoff: main-wecom-login-push-closed-loop

## Workstream Registration

- Workstream ID: `main-wecom-login-push-closed-loop`
- Goal: implement the re-scoped WeCom closed loop so Workbench only supports WeCom binding and push preferences, while Partner H5 supports WeCom login, binding, and push preference control.
- Non-goals: do not add WeCom login to Workbench; do not change branches, commits, pushes, deployments, or external shared service state; do not remove unrelated existing social-login or notification behavior.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `88313524d2d575fa22b72811245643ba33f7ff17`
- Target branch: current local `main`
- Ownership scope: System admin user profile, System notification channel adapter/config, ZSJOS partner auth/profile, partner account schema, Workbench profile UI, Partner H5 login/profile UI, and directly affected notification/auth docs and tests.
- Owner: Codex `/root`
- Dependencies: existing System social client OAuth, existing notification scene/message infrastructure, existing Workbench and Partner H5 auth/session stacks, and existing MySQL migration chain; no new third-party dependency.
- Integration order: add schema fields -> add backend profile/bind/login endpoints -> implement WeCom recipient resolution and channel send -> wire Workbench and Partner H5 UI -> update docs/tests -> run focused verification.
- Verification plan: focused backend unit tests for auth/profile/notify paths; frontend typecheck/build for Workbench and H5; scoped static checks and targeted manual request validation when the runtime is available.

## Delivery Entry - 2026-08-30 01:30:39 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `88313524d2d575fa22b72811245643ba33f7ff17`
- User goal: implement the approved WeCom closed-loop plan where Workbench only binds WeCom and controls push preference, Partner H5 supports WeCom login/bind/preference, System sends WeCom self-built app notifications, and message clicks return to the preferred business detail.
- Key decisions: kept Workbench account-password login unchanged; reused System social binding for ADMIN and PARTNER; modeled `wecomEnabled` as a user-level global channel preference; generated short-lived Redis click tickets instead of putting tokens in WeCom URLs; routed click targets by `{userType,userId}` and business metadata with business detail before message fallback.
- Execution or analysis result: added WeCom recipient providers, WeCom channel send adapter, channel config lookup, Partner WeCom login/bind/preference APIs, public click-ticket resolve API, Workbench profile switch and click route, Partner H5 WeCom login/bind/preference/click pages, SQL migration/baseline updates, and directly affected architecture/API docs.
- Changed files: System notify/profile/social classes under `backend/yudao-module-system`; Partner auth/profile/account and WeCom click classes under `backend/yudao-module-zsjos`; `backend/yudao-server/src/main/resources/application.yaml`; Workbench files under `frontend/workbench/src`; H5 files under `frontend/h5/src`; `docs/architecture/data-and-permission-flow.md`; `frontend/h5/兼职端API接口.md`; SQL bootstrap/schema/migration files under `script/sql/mysql`; this handoff file. Pre-existing unrelated dirty entries `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/LeadMapper.java` and `handoff/main.md` were not intentionally modified in this delivery.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-system,yudao-module-zsjos -am -DskipTests compile` passed; `cd frontend/workbench && npm run typecheck` passed; `cd frontend/workbench && npm run build` passed; `cd frontend/h5 && npm run build` passed after restoring the existing pnpm-managed `gsap` dependency into local `node_modules`; `git diff --check` passed with line-ending warnings only.
- Dependency or integration impact: no package or Maven dependency was added; local H5 dependency installation used the existing `frontend/h5/pnpm-lock.yaml` and did not change dependency manifests. Real WeCom delivery requires tenant social client credentials, enabled WeCom notify channel config, and configured `zsjos.wecom.workbench-base-url` / `zsjos.wecom.partner-h5-base-url`.
- Remaining work: run real WeCom OAuth/message-send/click-back smoke tests against a configured tenant and Redis; apply V167 in a controlled database according to the repository SQL process; add focused automated tests for Partner WeCom login/bind/preference and WeCom notification recipient filtering if desired before release hardening.

## Delivery Entry - 2026-08-30 16:28:23 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `88313524d2d575fa22b72811245643ba33f7ff17`
- User goal: finish the re-scoped WeCom implementation so the repository matches the clarified boundary where Workbench does not expose WeCom login and H5 does.
- Key decisions: removed the stale H5 dev-env hint that suggested client-side WeCom OAuth configuration; kept the actual login/bind flow server-driven through System social-client configuration.
- Execution or analysis result: cleaned the H5 development env example so it no longer advertises unused `VITE_WECOM_CORP_ID` / `VITE_WECOM_AGENT_ID` knobs.
- Changed files: `frontend/h5/.env.development`; `handoff/main-wecom-login-push-closed-loop.md`
- Verification evidence: None run for this documentation/config-only cleanup.
- Dependency or integration impact: none.
- Remaining work: none for this cleanup.

## Delivery Entry - 2026-08-30 16:34:03 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `88313524d2d575fa22b72811245643ba33f7ff17`
- User goal: make the code reflect the clarified boundary where Workbench only binds WeCom and H5 supports WeCom login plus push.
- Key decisions: H5 login now auto-starts WeCom OAuth when opened inside enterprise WeChat, while still allowing manual retry; the front-end WeCom utility was reduced to environment detection only; stale H5 env hints for client-side corpId/agentId were removed.
- Execution or analysis result: updated the H5 login page to import `isInWecom`, auto-launch `wecomAuthorizeUrl` once per session in WeCom, preserve the existing code/state callback flow, and remove the dead client-side OAuth helper from `frontend/h5/src/utils/wecom.ts`.
- Changed files: `frontend/h5/src/pages/login/index.vue`; `frontend/h5/src/utils/wecom.ts`; `frontend/h5/.env.development`
- Verification evidence: `cd frontend/h5 && npm run build` passed; `git diff --check -- frontend/h5/.env.development frontend/h5/src/pages/login/index.vue frontend/h5/src/utils/wecom.ts` showed only expected LF/CRLF warnings.
- Dependency or integration impact: none.
- Remaining work: none for this login-boundary cleanup.
