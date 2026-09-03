# Workstream: main-notify-channel-config

- Beijing time: 2026-09-03
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 16e026a672dfd4ab087d9a84179ccd544df30a99
- User goal: 完整修复企业微信通知渠道配置能力和当前租户配置
- Key decisions: System owns channel configuration; WeCom credentials continue to come from system_social_client; Vue Admin owns the administrator page; no new dependency.
- Non-goals: No replacement of the existing social-client credential model; no unrelated notification-channel redesign.
- Ownership scope: backend/yudao-module-system notification channel API/service/DAL, frontend/admin notification channel page/API, MySQL migration/bootstrap documentation.
- Owner: main workstream
- Dependencies: Existing Yudao permission, tenant, MyBatis-Plus, Vue Admin request and menu conventions.
- Integration order: Backend contract, frontend client/page, SQL/documentation, verification.
- Verification plan: Backend compile/tests, frontend typecheck/build, read-only database checks, focused WeCom configuration path review.

## Delivery Entry

- Beijing time: 2026-09-03 19:00
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 16e026a672dfd4ab087d9a84179ccd544df30a99
- User goal: 完整修复企业微信通知渠道配置能力和当前租户配置
- Key decisions: Added System-owned channel read/update API with credential validation, Vue Admin channel page, V179 menu migration, and tenant-1 `wecom` upsert. Credentials remain in Social Client and no secret was added to source.
- Execution result: Backend module compiled and packaged successfully. Admin production build succeeded. The database now contains tenant 1 `wecom` enabled config and the V179 menu/version row.
- Changed files: backend notify channel DTO/service/mapper/controller/error code; frontend admin channel API/page; V179 migration/bootstrap; API documentation.
- Verification evidence: `mvn -pl yudao-module-system -am -DskipTests compile` SUCCESS; `mvn -pl yudao-module-system -DskipTests package` SUCCESS; `pnpm build:local` SUCCESS; `git diff --check` clean; scoped MySQL queries verified channel/menu/version. Full `vue-tsc` remains failing on pre-existing unrelated errors.
- Dependency/integration impact: No new dependency. Existing admin menu permissions and System social-client contract are used. Current social client still has placeholder-looking Client ID `1000062`; real CorpID/Secret/AgentID and recipient opt-in remain operational prerequisites.
- Remaining work: Replace the current social-client Client ID/Secret with the real WeCom application credentials and enable push for intended recipients; restart/redeploy backend to load the new API code.

## Verification Correction

- Beijing time: 2026-09-03 19:02
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 16e026a672dfd4ab087d9a84179ccd544df30a99
- User goal: 完整修复企业微信通知渠道配置能力和当前租户配置
- Key decisions: Added strict `ww` CorpID validation before channel enablement.
- Execution result: Final backend compile succeeded; menu query shows parent and both permissions granted to `system_administrator`; tenant 1 channel row is enabled.
- Changed files: NotifyChannelConfigServiceImpl.java; handoff record.
- Verification evidence: `mvn -pl yudao-module-system -DskipTests compile -q` exit 0; scoped MySQL permission/channel query passed; `git diff --check` clean.
- Dependency/integration impact: No dependency changes. Runtime deployment is required for the new endpoint/page to be served.
- Remaining work: Configure real WeCom credentials and recipient opt-ins, then trigger a real notification.

## Encoding Repair Entry

- Beijing time: 2026-09-03 19:15
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 16e026a672dfd4ab087d9a84179ccd544df30a99
- User goal: 修复通知渠道配置说明乱码
- Key decisions: Added V180 with `SET NAMES utf8mb4`, scoped to tenant 1 and `channel_code=wecom`; no credentials changed.
- Execution result: Current `masked_config` repaired to 企业微信自建应用配置.
- Changed files: `script/sql/mysql/migrations/V180__repair_wecom_channel_config_encoding.sql`; `script/sql/mysql/bootstrap.sql`; current tenant-1 config row.
- Verification evidence: `HEX(masked_config)` now equals UTF-8 bytes `E4BC81E4B89AE5BEAEE4BFA1E887AAE5BBBAE5BA94E794A8E9858DE7BDAE` (verified by read-only query); migration version recorded as V180.
- Dependency/integration impact: Requires no new dependency; existing Admin page reads the corrected value.
- Remaining work: Redeploy/restart backend and refresh Admin session if the old value is cached.

## Repository Rule Entry

- Beijing time: 2026-09-03 19:25
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 16e026a672dfd4ab087d9a84179ccd544df30a99
- User goal: 总结乱码成因并写入 AGENTS.md
- Key decisions: Added a repository-wide UTF-8/utf8mb4 contract covering source, SQL, PowerShell/Docker pipes, MySQL clients, JDBC/HTTP, and HEX-based verification. The rule distinguishes display-only code-page issues from persisted double-encoded values such as `ä¼...`.
- Execution result: Root `AGENTS.md` now contains durable encoding guidance for future AI changes and database repairs.
- Changed files: `AGENTS.md`; `handoff/main-notify-channel-config.md`.
- Verification evidence: Reviewed the actual malformed `masked_config` bytes and the corrected UTF-8 `HEX()` value from the development database; `git diff --check` remains the required final check.
- Dependency/integration impact: No runtime dependency or product behavior change; applies to future migrations, database sync scripts, and user-visible text changes.
- Remaining work: None for this documentation request.

## Follow-up Delivery Entry

- Beijing time: 2026-09-03 19:10
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 16e026a672dfd4ab087d9a84179ccd544df30a99
- User goal: 修复通知渠道接口路径疑问及后台菜单乱码
- Key decisions: Kept the existing Admin axios contract using relative `/system/notify-channel/*` paths; added `SET NAMES utf8mb4` to V179 and repaired the three affected tenant-0 menu labels by exact IDs.
- Execution result: Database menu names now contain valid UTF-8 bytes and render as 通知渠道、通知渠道查询、通知渠道更新.
- Changed files: `script/sql/mysql/migrations/V179__notify_channel_config_admin.sql`; current database menu rows.
- Verification evidence: `HEX(name)` matches UTF-8 encodings for all three labels; API source remains consistent with all existing Admin System APIs.
- Dependency/integration impact: A frontend rebuild/redeploy is required for runtime env changes; no dependency changes.
- Remaining work: If the browser still shows a malformed `:admin-api` URL, inspect the deployed `VITE_BASE_URL`/reverse-proxy runtime configuration; the source path is correct and resolves to `/admin-api/system/notify-channel/get` under current `.env.local`, `.env.test`, and `.env.prod` settings.
