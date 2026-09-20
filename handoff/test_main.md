# Test Main Handoff

## Delivery Entry - 2026-09-07 12:12:00 +08:00

- Workstream ID: `test-sync-origin-main-4a818fba`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `4a818fbab993b8096afb98e80a7fff1c161c3829` (local changes retained)
- User goal: 处理拉取最新代码时因 `handoff/main.md` 本地修改导致的合并中止。
- Key decisions: 将全部本地修改和未跟踪文件临时 stash，快进 `main` 到 `origin/main`，再三方恢复；`handoff/main.md` 冲突保留远端与本地追加记录。
- Execution or analysis result: 已从 `0f5734a7` 快进到 `4a818fba`；本地修改已恢复，无未合并路径。
- Changed files: 远端提交涉及 61 个文件；本地既有修改继续保留；本文件新增本次交付记录。
- Verification evidence: `HEAD` 与 `origin/main` 均为 `4a818fbab993b8096afb98e80a7fff1c161c3829`；`git diff --name-only --diff-filter=U` 为空；`git status --short --branch` 显示分支已同步；备份为 `stash@{0}`。
- Dependency or integration impact: 未提交、推送、部署、执行数据库操作或启停服务；未删除任何 stash。
- Remaining work: 确认不再需要恢复备份后，可手动删除本次及上一轮同步 stash。

## Workstream Registration - 2026-09-07 12:09:13 +08:00

- Workstream ID: `test-sync-origin-main-20260907`
- Goal: 拉取 `origin/main` 最新代码并合并到测试环境本地 `main`，完整保留当前暂存、未暂存和未跟踪内容。
- Non-goals: 不修改业务逻辑，不清理或覆盖本地改动，不提交、不推送、不部署、不重启服务。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `037496d1f2895e3f83319cb07185f9513b664f05`; Target branch: `main`
- Ownership scope: Git 同步操作及 `handoff/test_main.md` 本次登记与交付记录；远端提交涉及的文件仅按其提交内容快进，不作额外编辑。
- Owner: Codex `/root`
- Dependencies: `origin/main` 当前为 `0f5734a7`; 本地工作区通过临时 stash 保存并使用 `--index` 恢复。
- Integration order: 登记工作流 -> stash 全部本地状态 -> fast-forward 到 `origin/main` -> 恢复 stash -> 检查冲突和状态 -> 追加交付记录。
- Verification plan: 核对 `HEAD == origin/main`; 检查 `git status`; 确认不存在未合并路径；比较恢复后的本地改动状态与同步前快照。

## Workstream Registration - 2026-09-06 16:52:57 +08:00

- Workstream ID: `test-public-payment-surface`
- Goal: 将客户支付页从 Partner H5 身份表面解耦，在测试主域名 `/public/` 提供匿名支付页面，并闭环公开接口租户解析、历史链接兼容和发布验证。
- Non-goals: 不改变 Partner 登录或员工权限体系，不修改通联支付协议、金额和密钥，不批量改写历史支付数据，不重启或重配置当前共享服务。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `037496d1f2895e3f83319cb07185f9513b664f05`; Target branch: `main`
- Ownership scope: `frontend/public-h5/**`; `frontend/h5/src/pages/payment/**`; `frontend/h5/src/api/payment.ts`; `frontend/h5/src/router/index.ts`; payment public Controller/Service/Mapper and focused tests under `backend/yudao-module-zsjos`; `script/shell/deploy-production.sh`; relevant architecture, payment and deployment documentation; `handoff/test_main.md`. Runtime Nginx and environment files remain read-only until separate shared-service confirmation.
- Owner: Codex `/root`
- Dependencies: Reuse the repository's existing Vue 3, Vue Router, Vant, Axios, Vite and TypeScript versions in a separately built public frontend; no new library or cross-framework shared package.
- Integration order: Extract anonymous payment UI -> add tenant-derived public service boundary -> update link format and compatibility -> include public artifact in release -> document reverse-proxy/runtime changes -> run focused backend/frontend/build/browser checks.
- Verification plan: Public frontend production build and focused tests; ZSJOS Maven tests and server assembly where feasible; static link/routing checks; logged-out desktop/mobile browser checks against a local server; review Nginx configuration plan and legacy redirect; `git diff --check`. No shared-service restart or live payment mutation without separate confirmation.

## Delivery Entry - 2026-09-06 17:00:25 +08:00

- Workstream ID: `test-public-payment-surface`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `037496d1f2895e3f83319cb07185f9513b664f05` (uncommitted)
- User goal: 回退本轮独立公共支付前端及配套后端、部署和文档调整。
- Key decisions: 仅回退 `test-public-payment-surface` 本轮产生的文件变化；保留此前固定租户 Controller、自动发布版本及所有其他既有用户修改；按照 handoff 只追加不改写规则保留注册记录并追加本条回退结果。
- Execution or analysis result: 新建的 `frontend/public-h5` 已完整删除；Partner H5 支付 API、页面和路由已恢复；本轮支付 Service、架构、支付文档及部署脚本改动已撤销。共享 Nginx、运行环境、后端进程和数据库均未变更。
- Changed files: `handoff/test_main.md`（本条回退记录）；其余本轮文件修改均已恢复到执行前状态。
- Verification evidence: `git status --short` 与执行前状态核对一致，仅保留执行前既有修改及本 handoff 文件；目标文件 scoped diff 为空；`git diff --check` passed。
- Dependency or integration impact: 无新增依赖或发布产物；未安装、部署或重启服务。
- Remaining work: None for the rollback. 支付链接进入 Workbench 登录页的问题仍保持原状，后续需重新确认其他调整方向。

## Workstream Registration - 2026-09-06 16:40:00 +08:00

- Workstream ID: `test-automatic-release-version`
- Goal: 让每次生产发布自动生成唯一、可追溯的应用版本，并让数据库发布标识与同次应用发布保持一致。
- Non-goals: 不构建或安装 release，不执行数据库迁移，不重启或重配置 systemd 服务，不清理历史 release，不修改分支、提交或推送。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `037496d1f2895e3f83319cb07185f9513b664f05`; Target branch: `main`
- Ownership scope: `script/shell/deploy-production.sh`; `docs/operations/production-deployment.md`; `/opt/zsjos-runtime/.env.production`; this handoff record
- Owner: Codex `/root`
- Dependencies: Existing Bash, `date`, Git CLI, production deployment environment loader and release directory convention; no new dependency.
- Integration order: Register scope -> generate one version per script invocation -> bind database release version -> remove fixed runtime overrides -> document behavior -> run read-only syntax and environment-resolution checks.
- Verification plan: `bash -n script/shell/deploy-production.sh`; source only the function definitions in a subprocess and call `load_env` twice to confirm a stable timestamp-plus-commit version and matching database release version; inspect scoped diffs and run `git diff --check`.

## Delivery Entry - 2026-09-06 16:42:00 +08:00

- Workstream ID: `test-automatic-release-version`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `037496d1f2895e3f83319cb07185f9513b664f05` (uncommitted)
- User goal: 消除固定 `2026.09.01` 发布标识，让每次运行生产发布脚本时自动生成版本。
- Key decisions: 未显式配置时按 `YYYY.MM.DD-HHMMSS-<Git短提交号>` 生成一次应用版本，并在同一脚本进程内持续复用；数据库发布标识默认继承同一版本；保留显式版本覆盖能力。
- Execution or analysis result: 发布脚本已实现自动版本与数据库版本绑定；运行时环境文件已移除两个固定版本项；部署文档已说明默认行为和显式重放限制。未构建、部署、迁移或重启服务，当前运行 release 仍为 `2026.09.01`。
- Changed files: `script/shell/deploy-production.sh`; `docs/operations/production-deployment.md`; `/opt/zsjos-runtime/.env.production`; `handoff/test_main.md`.
- Verification evidence: `bash -n script/shell/deploy-production.sh` passed；隔离子进程连续两次 `load_env` 得到同一 `APP_VERSION=2026.09.06-164123-037496d1`，且 `ZSJOS_DB_RELEASE_VERSION` 相等；目标 release 目录不存在；scoped `git diff --check` passed。
- Dependency or integration impact: 无新增依赖；下一次完整 `deploy` 才会创建自动命名的新 release 并让新配置进入运行进程。
- Remaining work: 在获准的发布窗口执行完整部署并核对 `current`、服务启动日志、数据库迁移记录和健康接口；本次未触发这些外部状态变更。

## Workstream Registration - 2026-09-06 15:00:00 +08:00

- Workstream ID: `test-payment-public-tenant-and-h5-domain`
- Goal: 固定公开支付接口使用租户 1，并将运行时 H5 域名从无效的 `testpartos` 统一为 `testos`。
- Non-goals: 不修改通联协议、支付金额、数据库数据、密钥内容或重启外部服务。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `1ad61abc6716f7abfc75ece7f7829aad518862fd`; Target branch: `main`
- Ownership scope: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/pub/payment/PublicPaymentController.java`; `/opt/zsjos-runtime/.env.production`; this handoff record
- Owner: Codex `/root`
- Dependencies: Existing Yudao tenant context utilities; current runtime systemd service.
- Integration order: Apply source/config changes, build focused backend module, then restart only if explicitly requested.
- Verification plan: Compile affected backend module; inspect runtime variables; request public payment detail and inspect service logs.

## Delivery Entry - 2026-09-06 16:15:00 +08:00

- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `1ad61abc6716f7abfc75ece7f7829aad518862fd` (uncommitted)
- User goal: 确认并修复固定租户公开支付异常，以及移除无效 `testpartos` 域名前缀。
- Key decisions: 公开支付四个操作使用 `TenantUtils.execute(1L, ...)`；运行时 `ZSJOS_PUBLIC_H5_BASE_URL`、`ZSJOS_WECOM_PARTNER_H5_BASE_URL` 和 `ZSJOS_H5_DOMAIN` 统一为 `testos.zhongshijian.top`；不改通联协议、密钥内容或重启服务。
- Execution or analysis result: 源码和运行时环境文件已修改。公网接口此前已复现 `code=500`，日志根因为缺失租户上下文；本次尚未部署新 JAR，因此线上接口验证待部署后进行。
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/pub/payment/PublicPaymentController.java`; `/opt/zsjos-runtime/.env.production`; `handoff/test_main.md`。
- Verification evidence: Maven reactor 编译到 `yudao-module-zsjos` 后失败，原因是既有 `root:root` 所有的 `target/classes/META-INF/spring-configuration-metadata.json` 无法覆盖；非源码编译错误。运行时变量已检查为 `testos` 域名，未重启服务。
- Dependency or integration impact: 后端需重新构建并发布 JAR 后公开支付接口修复才生效；H5/企微域名配置需由 systemd 重启后读取。
- Remaining work: 在维护窗口构建发布并重启 `zsjos-backend`；补齐 `ZSJOS_PAYMENT_LINK_HMAC_SECRET`；确认两枚 RSA 密钥实际存在且可读；部署后重新请求支付详情和支付回调链路。

## Delivery Entry - 2026-09-07 12:11:09 +08:00

- Workstream ID: `test-sync-origin-main-20260907`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `0f5734a73506259660e6286c11bae7c016c6a423` (local changes retained)
- User goal: 拉取最新代码并合并到本地。
- Key decisions: 先以包含未跟踪文件的 stash 保存本地状态，再将 `main` 快进到 `origin/main` 并三方恢复本地内容；保留原 stash 作为恢复备份；冲突的生成缓存与 V181 迁移采用远端版本，`handoff/main.md` 保留两侧追加记录。
- Execution or analysis result: 已从 `037496d1` 快进到 `0f5734a7`；本地暂存、未暂存和未跟踪内容已恢复，两个原有 `MM` 文件及仅未暂存的支付 Controller 状态已重建；无未合并路径。
- Changed files: 远端提交涉及 254 个版本控制文件；本地原有改动继续保留；`handoff/test_main.md` 新增本次登记与交付记录。
- Verification evidence: `git rev-parse HEAD` 与 `git rev-parse origin/main` 均为 `0f5734a73506259660e6286c11bae7c016c6a423`；`git diff --name-only --diff-filter=U` 为空；`git status --short --branch` 显示分支已同步且本地改动仍在；备份为 `stash@{0}: codex-sync-origin-main-20260907-120913`。
- Dependency or integration impact: 未新增依赖，未提交、推送、部署、执行数据库操作或启停服务；远端提交包含后端、三套前端、文档和 SQL 的广泛更新。
- Remaining work: 旧 `frontend/workbench/tsconfig.tsbuildinfo` 与旧 V181 本地版本仅保留在 stash 中，当前使用远端更新版本；确认无需逐字恢复后可另行删除该 stash。
## Workstream Registration - 2026-09-14 13:20:33 +08:00

- Workstream ID: `test-partner-wecom-domain-verification`
- Goal: 让兼职端测试域名 `testpartos.zhongshijian.top` 通过企业微信可信域名归属认证。
- Non-goals: 不修改 DNS、企业微信应用凭据、业务代码、数据库或其他域名；不执行完整前端部署。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `4a818fbab993b8096afb98e80a7fff1c161c3829`; Target branch: `main`
- Ownership scope: `frontend/h5/public/WW_verify_qI52MOnpdf21ycDu.txt`; `/opt/zsjos-runtime/releases/current/h5/WW_verify_qI52MOnpdf21ycDu.txt`; `handoff/test_main.md`.
- Owner: Codex `/root`
- Dependencies: 当前 Nginx `testpartos.zhongshijian.top` 静态站点和 H5 release 目录；无新增依赖。
- Integration order: 登记工作流 -> 添加可随构建发布的 public 文件 -> 写入当前测试 release -> 检查 Nginx 配置 -> 通过 HTTP/HTTPS 验证内容。
- Verification plan: `nginx -t`；分别请求 HTTP 和 HTTPS 地址，核对最终状态码、响应正文及内容类型；检查源文件和运行文件内容一致；`git diff --check`。

## Delivery Entry - 2026-09-14 13:22:00 +08:00

- Workstream ID: `test-partner-wecom-domain-verification`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `4a818fbab993b8096afb98e80a7fff1c161c3829` (uncommitted)
- User goal: 为兼职端测试域名配置企业微信可信域名归属认证文件，并确保公网可访问。
- Key decisions: 将认证文件纳入 H5 `public` 目录以便后续构建持续携带，同时写入当前 release 立即生效；沿用 HTTP 到 HTTPS 的现有重定向，不新增 Nginx location。
- Execution or analysis result: `testpartos.zhongshijian.top` 已可访问认证文件；Nginx 配置检查成功并完成 reload；未修改 Nginx 配置、DNS、数据库或业务逻辑。
- Changed files: `frontend/h5/public/WW_verify_qI52MOnpdf21ycDu.txt`; `/opt/zsjos-runtime/releases/current/h5/WW_verify_qI52MOnpdf21ycDu.txt`; `handoff/test_main.md`.
- Verification evidence: `sudo nginx -t` passed；HTTPS 请求返回 `200`、`Content-Type: text/plain`、正文 `qI52MOnpdf21ycDu\n`；HTTP 请求按既有策略返回 `301` 到同路径 HTTPS；源文件与运行文件内容一致；scoped `git diff --check` passed。
- Dependency or integration impact: 无新增依赖；后续 H5 构建会从 `public` 目录复制认证文件，当前测试 release 已立即生效。
- Remaining work: 在企业微信管理后台重新提交可信域名认证；如平台不跟随 HTTP 到 HTTPS 跳转，应填写 HTTPS 域名地址。

## Workstream Registration - 2026-09-15 17:42:01 +08:00

- Workstream ID: `test-core-baseline-dictionary-sync`
- Goal: 修复 Core desired schema 与 fresh baseline 的严格差异，并为内容审核作品目的/作品形式空字典类型补齐安全、可重复的升级路径。
- Non-goals: 不预置业务字典项，不修改管理员已有字典配置，不执行测试或生产数据库迁移，不启停服务，不提交或推送。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `4419f295f37e0c4c02b244db762c26674935febd`; Target branch: `main`
- Ownership scope: `script/sql/mysql/schema/core.sql`; `script/sql/mysql/00-bootstrap-schema.sql`; `script/sql/mysql/migrations/V243__content_review_dictionary_types.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `handoff/test_main.md`。
- Owner: Codex `/root`
- Dependencies: 复用现有 MySQL 8、Core migrator、`system_dict_type` 和双版本登记机制；不新增依赖。`V242` 会清除退役 V243-V249 尝试的版本登记，Core 版本连续性要求本次从 `V243` 恢复编号。
- Integration order: 同步 baseline/desired schema -> 新增 V243 空字典类型迁移 -> 增加 UTF-8、空选项和版本验证 -> 更新迁移文档 -> 运行静态、fresh、upgrade、guardrail 检查。
- Verification plan: `cmp` 严格字节比较；`bash ./zsjos-db check`；`bash ./zsjos-db test-fresh`；`bash ./zsjos-db test-upgrade`；`bash ./zsjos-db test-guardrails`；代表性中文 `HEX()` 由验证 SQL检查；scoped `git diff --check`。

## Delivery Entry - 2026-09-15 17:48:47 +08:00

- Workstream ID: `test-core-baseline-dictionary-sync`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `4419f295f37e0c4c02b244db762c26674935febd` (uncommitted)
- User goal: 修复 `Desired schema differs from the fresh baseline for core`，并同步已审迁移。
- Key decisions: 将 baseline 的 `system_notice_recipient` 纯格式差异对齐 desired schema；保留两个管理员维护的空字典类型并同步到 desired schema；使用连续 `V243` 补齐升级路径，因为 `V242` 已清理退役 V243-V249 的旧登记；不预置作品目的/形式选项，不覆盖活动字典类型。
- Execution or analysis result: Core desired schema 与 fresh baseline 已字节一致；新增的 V243 只在活动类型缺失时创建 `zsjos_content_purpose`、`zsjos_content_format`，并增加空选项及 UTF-8 验证。未修改或迁移任何共享数据库。
- Changed files: `script/sql/mysql/schema/core.sql`; `script/sql/mysql/00-bootstrap-schema.sql`; `script/sql/mysql/migrations/V243__content_review_dictionary_types.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `handoff/test_main.md`。
- Verification evidence: `cmp` passed；一次性 `mysql:8` 容器中 V243 连续执行两次后活动类型计数 `2/2`、对应 `system_dict_data` 计数 `0`，中文名称 HEX 分别为 `E4BD9CE59381E79BAEE79A84`、`E4BD9CE59381E5BDA2E5BC8F`；容器已删除；scoped `git diff --check` passed。`bash ./zsjos-db check` 已越过原始 baseline drift 与版本连续性检查，随后被既有缺失 Core 映射表 `zsjos_partner_leaderboard_config`、`zsjos_payment_subject`、`zsjos_product_payment_subject` 阻断，因此 full fresh/upgrade/guardrails 未运行。
- Dependency or integration impact: 新增一个无第三方依赖的 Core 数据迁移；升级环境在 V242 后创建两个空字典类型，全新环境从 baseline 获得相同结果。未提交、推送、部署或启停服务。
- Remaining work: 另行修复三张已映射但未纳入 Core desired schema/fresh baseline 的表后，重跑 `check`、`test-fresh`、`test-upgrade`、`test-guardrails`；本次请求的原始 baseline drift 已修复。

## Workstream Registration - 2026-09-15 18:00:04 +08:00

- Workstream ID: `test-v244-pending-schema-gap`
- Goal: 新增连续 V244，为测试库从 V184 升级时补齐当前 desired schema 已声明但 V185-V243 未覆盖的 13 个 nullable 字段，使 pending migration 能完整解释现有结构差异。
- Non-goals: 不执行共享测试库迁移，不执行或授权 V211 数据重置，不回填或改写历史业务值，不补齐另行发现的三张 Core baseline 缺失表，不部署、提交或推送。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `4419f295f37e0c4c02b244db762c26674935febd`; Target branch: `main`
- Ownership scope: `script/sql/mysql/migrations/V244__pending_schema_gap.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `handoff/test_main.md`。
- Owner: Codex `/root`
- Dependencies: V243；既有 `zsjos_exam_schedule`、`zsjos_lead_intended_product`、`zsjos_production_ticket` 表；MySQL 8 information_schema 守卫；无新增依赖。
- Integration order: 新增 V244 幂等 DDL -> 增加字段元数据验证 -> 更新迁移文档 -> 一次性 MySQL V184 形态重复执行 -> 运行静态检查和测试库只读 plan。
- Verification plan: 13 个字段的类型、nullable、注释和顺序审查；V244 重复执行两次；information_schema 元数据比对；`bash ./zsjos-db check`；测试库 `plan production` 只读检查；scoped `git diff --check`。

## Delivery Entry - 2026-09-15 18:05:00 +08:00

- Workstream ID: `test-v244-pending-schema-gap`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `4419f295f37e0c4c02b244db762c26674935febd` (uncommitted)
- User goal: 解除测试库 `Migration is blocked by unexpected schema drift`，继续准备 V185-V243 的正式迁移。
- Key decisions: 新增 V244 作为 13 个字段的唯一升级路径；全部字段 nullable、无历史回填；不执行共享测试库 migrate，V211 数据重置仍需单独确认。
- Execution or analysis result: V244 已加入 Core 迁移序列，重建本地 migrator 镜像后测试库只读 plan 已从 BLOCKED 变为 `Status: MIGRATIONS PENDING`，待迁移包含 V185-V244；unexpected schema drift 为 None。
- Changed files: `script/sql/mysql/migrations/V244__pending_schema_gap.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `handoff/test_main.md`。
- Verification evidence: 一次性 MySQL 8 中 V244 连续执行两次后 13 个字段全部存在，类型/nullable/注释核对通过；migrator 镜像构建成功；测试库 `plan production` 只读通过且明确列出 V244；未执行 migrate、未改变测试库数据；scoped `git diff --check` 待最终复核。
- Dependency or integration impact: migrator 镜像已更新为当前工作树内容；正式迁移仍受 V211 删除范围审计与三张 Core baseline 缺失表的后续修复约束。
- Remaining work: 在确认 V211 删除范围、备份和执行窗口后，才可运行正式 migrate；先修复 `zsjos_partner_leaderboard_config`、`zsjos_payment_subject`、`zsjos_product_payment_subject` 的 baseline/mapping 一致性问题。

## Delivery Entry - 2026-09-15 18:18:00 +08:00

- Workstream ID: `test-schema-verification-reconciliation`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `4419f295f37e0c4c02b244db762c26674935febd` (uncommitted)
- User goal: 修复数据库 verification failed 的 Core drift，并恢复测试库迁移前的可验证状态。
- Key decisions: 新增 V245 补齐 `zsjos_content` 两个遗漏字段；同步已由 V231/V239/V230 创建的内容、支付、审计字段/索引；将已由正式迁移创建的 gift、student-delivery、partner leaderboard、payment subject 表加入显式 `allowedExtraTables`；保留 `zsjos_product_sku.price_unit` nullable 兼容实际数据（NULL 行数为 0），不执行约束变更或数据回填。
- Execution or analysis result: desired schema 与 fresh baseline 已同步；静态 `bash ./zsjos-db check` 通过；重建 migrator 镜像后测试库只读 plan 显示 `Unexpected schema drift: None`，状态为 `MIGRATIONS PENDING`，仅待 Core V245。
- Changed files: `script/sql/mysql/migrations/V245__content_snapshot_gap.sql`; `script/sql/mysql/schema/core.sql`; `script/sql/mysql/00-bootstrap-schema.sql`; `script/sql/mysql/modules/core.json`; `handoff/test_main.md`。
- Verification evidence: 测试库只读审计确认 `price_unit` NULL 行数为 0；V245 一次性 MySQL 重复执行和字段元数据验证已通过；`cmp`、`git diff --check`、静态 manifest/migration/mapping 检查通过；未执行共享数据库 migrate。
- Dependency or integration impact: 本地 migrator 镜像已刷新；测试库仍停在 Core V244，V245 尚未执行；V211 删除范围仍需单独确认。
- Remaining work: 在确认备份、删除范围和执行窗口后再运行正式 migrate；迁移后运行完整 `verify`，并关注 V245 字段与历史数据快照行为。

### 2026-09-16 15:40:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 抽离通用菜单权限给普通员工角色——业务角色只保留岗位专属业务功能，人人需要的（首页等）归 normal_user。
- Key decisions: 授权是并集语义，通用集移交给 `normal_user` 后账号可见范围不变，故不新建角色而是复用既有 normal_user（tenant 1 有角色账号中 41/42 已持有）；通用集运行时计算（"tenant 1 全部启用角色的共同持有菜单"=45 项）而非写死 ID；`super_admin` 不撤销（超管不受菜单授权限制）；撤销前预检"持有业务角色但无 normal_user"的账号，存在则整批中止；撤销后必须**回补祖先目录**（6735 工作台 / 73600 日历是纯容器，缺父节点会让子页面"有权限但界面不可达"，且并集比对看不出）。
- Result: `script/sql/mysql/migrations/V251__universal_menu_baseline.sql` 已应用开发库。通用集 45 项仅 `normal_user`/`super_admin` 持有（各 45），32 个业务角色通用授权归零，`normal_user` 共 103 项；逐账号并集比对 0 变化；悬空授权 0；有效关系 3817 → 2464（撤销 1395 + 回补 42）。`verify-role-menu-coverage.sql` 新增业务角色持有通用菜单 / 账号覆盖两组检查。
- Changed files: `script/sql/mysql/migrations/V251__universal_menu_baseline.sql`、`script/sql/mysql/verify-role-menu-coverage.sql`、`docs/architecture/zsjos-role-permission-matrix.md`、`script/sql/mysql/migrations/README.md`。
- Verification: 事务内 dry-run 先行（union_changed_users=0、universal=45）；应用后 C1/C2/C3/C4 全绿；`verify-role-menu-coverage.sql` 全 7 组检查 0 行（含 V246/V251 版本登记）；`reconcile production --apply` 校正 V251 台账校验和为文件字节哈希；`plan production` = READY。
- Dependency / integration impact: 需随 backend 同步部署。**部署前置**：任何持有业务角色但未持有 `normal_user` 的账号必须先补授该角色（当前 tenant 1 仅账号 1/super_admin 例外）。后端 `PermissionServiceImpl.getRoleMenuListByRoleId` 的并集语义是本次收敛成立的前提，若改为"取交集"或"按角色取第一个"会立即缩减可见范围。
- Remaining work: `system_administrator` 持有 6741/79980/79990 三个 `parent_id=1` 的页面但未持有菜单 1，前端会丢弃这三个节点（超管不受影响）；需补授菜单 1 或改挂父节点。tenant 1 有 49 个账号无任何角色（历史销售/运营/离职账号），是否补授 `normal_user` 未决——若补授，其可见范围将从 0 变为 45 项通用基线。

### 2026-09-16 16:20:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 产品口径修正——「管理员拥有所有菜单权限」；并明确角色分配不由我调整，只整理角色的菜单权限。
- Key decisions: 把 `system_administrator` 从 V071 的按需 allowlist 改为持有**全部启用菜单**（2245 项，与 `super_admin` 可见范围一致）。V246 以来"新增业务页需人工判断补授"的做法作废；今后授权规则只有三条——通用菜单给 `normal_user`、全量菜单给 `system_administrator`/`super_admin`、岗位专属菜单给对应业务角色单独特评。禁用菜单（`status=1`：工作计划模块 + 框架自带支付/公众号/商城/CRM/ERP/AI/IoT/MES/WMS）不授予，与超管的框架行为一致（`getPermissionInfo` 会 `filterDisableMenus`）。**放弃**此前"禁止管理员持有财务复核与资金导出权限"的约束（全量与该约束不可兼得），并在文档中显式记录该代价。不改动任何用户-角色分配。
- Result: `script/sql/mysql/migrations/V252__system_administrator_full_menu.sql` 已应用开发库；`system_administrator` 232 → 2245 项，C1 缺失 0、C2 总数 2245。`verify-role-menu-coverage.sql` 移除管理员财务 violation 条款，4) 悬空检查改为只统计"父节点本身启用"的授权行（新增 4a) 单独列出 88 行"父停用/子启用"历史数据，属提示非缺陷），新增 V252 版本登记检查。授权关系总数 2464 → 4477。
- Changed files: `script/sql/mysql/migrations/V252__system_administrator_full_menu.sql`、`script/sql/mysql/verify-role-menu-coverage.sql`、`docs/architecture/zsjos-role-permission-matrix.md`、`script/sql/mysql/migrations/README.md`。
- Verification: 事务内 dry-run 先行（grants 2245 = 启用菜单数）；应用后 C1=0、C2=2245/2245；`verify-role-menu-coverage.sql` 全组通过（orphan-grant 0 行）；`reconcile production --apply` 已对齐 V252 台账校验和为文件字节哈希；`plan production` = READY。
- Dependency / integration impact: 需随 backend 同步部署。**职责分离影响**：`system_administrator` 现在可执行成交订单审批、返现查询、提现审核/打款与全部资金导出；若产品要求保留职责分离，需改为"全量菜单 − 财务黑名单"并恢复 verifier 的 violation 条款。管理员角色授权行数较大（2245），后续新增菜单需同时补 `system_administrator` 与 `super_admin`（或改为角色继承机制）。
- Remaining work: 停用模块下 88 行"父停用、子启用"的菜单数据为历史遗留，是否清理或补授父级未决。其余同上一节。

## Delivery Entry - 2026-09-17 11:37:00 +08:00

- Workstream ID: `advanced-filter-preset-surfacing`
- Branch: `main`; Worktree: `/opt/zsjos`; HEAD: `48ecc5c383d26bb1913f1bace4b400d64e6d199e` (uncommitted)
- User goal: 打通「高级筛选预置」到业务工作台的最后一环——让 7 个场景页能读取并一键套用管理员在「高级筛选预置」页维护的模板。此前 `visible-list` 接口、`zsjos_advanced_filter_template` 表、管理页均已存在，但没有任何业务页调用 `visible-list`，配置落库后无人消费。
- Key decisions: (1) 把 `visible-list` 的 `@PreAuthorize` 由静态权限列表改为与 `AdvancedFilterController.catalog` 一致的按 scene 判定，因为原列表遗漏 `zsjos:sales-order:query-management`（V195 统一后订单管理页所用）与 `zsjos:media-student:query-my`，订单管理页调用即 403；(2) 预置标签渲染在共享组件 `ZsjosAdvancedFilter.vue` 内，以可选 `pageKey` prop 开关，未传时行为完全不变，避免 7 个页面各自实现；(3) 点击标签把模板 `filter` 树灌入现有 draft 并复用 `deliver()` 链路；(4) 不预置种子模板（用户明确确认保持空表）。
- Registration: Goal 同上；Non-goals 不含个人模板「存为我的预置」UI、不改 `advancedSearchEndpoint` 链路、不预置种子数据；Ownership scope: `AdvancedFilterTemplateController`、`frontend/admin` 的 `ZsjosAdvancedFilter.vue`/`WorkbenchListPage.vue`/9 个场景页/`api/zsjos/advancedFilterTemplate`；Verification plan: ZSJOS Maven 聚焦测试（含 visible-list 允许/拒绝用例）、`pnpm ts:check`、受影响页面桌面/移动宽度浏览器检查；Dependencies: 无新增依赖；Integration order: None。
- Status: in-progress

### 2026-09-17 14:55:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 打通高级筛选预置到业务工作台，并明确快捷筛选可配置的边界。
- Key decisions: `visible-list` 守卫由静态权限并集改为按 `scene` 判定，与 `AdvancedFilterController.catalog` 逐条对齐——原并集缺 `zsjos:sales-order:query-management` 与 `zsjos:media-student:query-my`，订单管理页与媒体学员页调用必然 403（本轮修掉）。预置标签实现在共享组件 `ZsjosAdvancedFilter.vue`，以可选 `pageKey` prop 开关，未传时行为逐字不变，避免 10 个页面各写一份。默认预置仅在页面首次加载且条件为空时套用，不覆盖用户输入；点击标签是重复点击取消语义；用户改条件后高亮清除。不预置种子模板（用户确认保持空表）。
- Result: 后端守卫与 `/advanced-filter/catalog` 场景分支一致；前端新增 `getVisibleTemplateList`，`ZsjosAdvancedFilter.vue` 渲染「快捷」标签行并复用既有 `deliver()` 提交链路；`WorkbenchListPage.vue` 新增 `advancedPageKey` 透传；10 个业务页接入：`lead_management`、`lead_claim_pool`、`lead_aging_pool`、`sales_order_management`、`sales_order_supervisor_confirm`、`lead_appeal`、`lead_duplicate_review`、`registration_pool`、`student_my`、`subordinate_sales`。
- Changed files: `backend/.../advancedfilter/AdvancedFilterTemplateController.java`；新增 `backend/.../test/.../advancedfilter/AdvancedFilterTemplateControllerPermissionTest.java`；`frontend/admin/src/api/zsjos/advancedFilterTemplate/index.ts`；`frontend/admin/src/views/zsjos/components/{ZsjosAdvancedFilter,WorkbenchListPage}.vue`；9 个场景页；`docs/api/zsjos-lead-submission-dispatch.md`；本 handoff。
- Verification evidence: 新增权限测试 3 例通过（scene 分支数=7、`query-management` 与 `media-student:query-my` 在守卫内、7 个场景各一分支）；`SalesOrderServiceImplTest` 38 例通过；聚焦测试合计 41 例 0 失败（改动前已跑过，证据见上一段）。ESLint 在 `frontend/admin` 下对改动文件返回 0，并用故意语法错误的 `.vue` probe 确认 eslint 确实在解析 `.vue`（probe 返回 1）——注意 eslint 不做类型检查。定点 `tsc` 对 `advancedFilterTemplate/index.ts` 无相关错误（输出中 128 条均为既有配置噪音）。
- **未验证（需人工执行）**: 全量 `pnpm ts:check`（`vue-tsc`）未取得结果——本机 8G 内存下该进程触发 OOM，且机器于 14:10、14:29 两次重启，均在 `vue-tsc` 运行期间；用户要求改由人工执行编译验证，本轮不再运行 mvn/ts:check。`.vue` 的类型正确性、以及预置标签的浏览器实际渲染（桌面/移动宽度）均未验证。模块全量测试中另有 3 个既有失败（`ZsjosAuditCoverageTest` 端点计数 301→311 由并发会话新增 controller 引起；`ZsjosBpmBusinessTaskTargetServiceImplTest`、`ContentReviewBatchServiceTest`），均不在本次改动范围。
- Dependency or integration impact: 无新增依赖。`visible-list` 守卫收紧为按场景判定：原先仅凭任意一个业务查询权限即可跨场景读取模板的账号，现在只能读取自己有权限的场景。这是有意的对齐，但属于对外可见的授权变化，需随 backend 同步部署。`sales_order_approval:registration` / `:finance` 两个 pageKey 仍未接入（该页未使用 `ZsjosAdvancedFilter`）。
- Remaining work: 执行 `pnpm ts:check` 与受影响页面的浏览器检查；个人模板「存为我的预置」UI 未实现（后端 `personal` 作用域与 `visible-list` 合并逻辑已就绪）。

### 2026-09-17 15:20:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 补齐个人高级筛选模板（下称「我的快捷筛选」）的自助维护 UI，并确定默认优先级为个人默认高于系统默认。
- Key decisions: (1) 优先级实现放在前端 `loadTemplates`——取 `defaultTemplate && scope==='personal'` 为优先候选，否则回退任意默认；数据库按 `scope + owner` 清默认，个人默认与系统默认互不覆盖，因此两者可并存，选择权在页面。(2) 「重命名」不写回当前条件：编辑态复用被编辑模板自己的 `filter` 与 `sort`，否则用户点重命名会把模板内容悄悄改成当前草稿。(3) 不新增后端接口或字段，三个 personal 接口与 `visible-list` 均已存在且可用。(4) 管理入口收敛为一个弹窗（先写重了下拉菜单 + 弹窗两套，已删除下拉）。
- Result: `api/zsjos/advancedFilterTemplate/index.ts` 新增 `createPersonalTemplate`/`updatePersonalTemplate`/`deletePersonalTemplate`；`ZsjosAdvancedFilter.vue` 新增「存为快捷筛选」按钮（筛选栏下方与抽屉底部各一处）、保存/重命名弹窗、管理弹窗（重命名、设为默认、删除）；默认模板在标签上带「默认」角标。10 个已接入 pageKey 的页面自动获得该能力，未传 `pageKey` 的场景行为不变。
- Changed files: `frontend/admin/src/api/zsjos/advancedFilterTemplate/index.ts`；`frontend/admin/src/views/zsjos/components/ZsjosAdvancedFilter.vue`；`docs/api/zsjos-lead-submission-dispatch.md`；本 handoff。
- Verification evidence: 本地 `./node_modules/.bin/eslint` 对两个改动文件返回 0，并用故意语法错误的 `.vue` 探针确认 eslint 确实在解析 `.vue`（探针返回 1）。逐项静态复核：`Dialog` 非自动导入故已显式 `import { Dialog } from '@/components/Dialog'`（与 `advancedFilterTemplate/index.vue` 一致）；`AdvancedFilterTemplateSaveReq` 字段名与后端 `AdvancedFilterTemplateSaveReqVO` 逐字段核对一致；`enabled`/`defaultTemplate` 均为 `Boolean`。
- **未验证（需人工执行）**: `pnpm ts:check` 本轮仍未运行——`vue-tsc` 在 8G 内存下 OOM，且 14:10、14:29 两次重启均发生在其运行期间，用户要求改由人工验证编译，本轮不再执行 mvn/ts:check。因此 `.vue` 类型正确性、以及个人模板增删改与「个人默认优先」的实际交互均未经运行时验证。
- Dependency or integration impact: 无新增依赖，无后端改动。个人默认优先级是纯前端选择逻辑，后端排序（`scope DESC`）未改动；若未来有其他前端消费 `visible-list`，需各自实现同一优先级，或改为后端返回单一 `effectiveDefault` 标记。
- Remaining work: 执行 `pnpm ts:check`；浏览器验证存/重命名/设为默认/删除四条路径与个人默认优先于系统默认的表现；`sales_order_approval:registration` / `:finance` 两个 pageKey 仍未接入。

### 2026-09-17 15:45:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 补全「个人默认优先于系统默认」的实现，避免该优先级只存在于某一个前端组件里。
- Key decisions: 优先级规则上移到服务端唯一实现——`AdvancedFilterTemplateRespVO` 新增 `effectiveDefault`，`visibleList` 计算并标记至多一条；选取规则为「个人默认优先，同范围内取 id 最小」。`systemList`（管理页）不参与页面自动套用，恒为 `false`。前端 `loadTemplates` 改为直接消费该标记，删除原先自行按 `scope` 推断的逻辑。未改动 `visible-list` 的返回顺序（仍为 `scope DESC`），因为顺序是既有既有行为，改排序会让标签重新排列。
- Result: 后端：`AdvancedFilterTemplateRespVO` 增字段；`AdvancedFilterTemplateServiceImpl` 新增 `resolveEffectiveDefaultId`，`toResp` 改为接收 `effectiveDefaultId`；两处调用点同步更新。前端：`api/zsjos/advancedFilter/index.ts` 的 `AdvancedFilterTemplate` 增可选 `effectiveDefault`；`ZsjosAdvancedFilter.vue` 改用 `item.effectiveDefault` 挑选自动套用项。
- Changed files: `backend/.../advancedfilter/vo/AdvancedFilterTemplateRespVO.java`；`backend/.../advancedfilter/AdvancedFilterTemplateServiceImpl.java`；`backend/.../test/.../advancedfilter/AdvancedFilterTemplateServiceImplTest.java`；`frontend/admin/src/api/zsjos/advancedFilter/index.ts`；`frontend/admin/src/views/zsjos/components/ZsjosAdvancedFilter.vue`；`docs/api/zsjos-lead-submission-dispatch.md`；本 handoff。
- Verification evidence: 新增 4 个用例覆盖优先级（个人默认压过系统默认、无个人默认时回退系统默认、两者都无默认时不标记、`systemList` 恒不标记），并抽取 `template(id, scope, owner)` 重载以便构造不同 id；`toResp` 的 `id` 比较加空值保护。本地 `./node_modules/.bin/eslint` 对三个前端改动文件返回 0。逐项静态复核：`Comparator.comparing(...).thenComparing(getId)` 的 `min` 语义确认为「personal 映射 0 先于 system 映射 1」；`SCOPE_PERSONAL`/`SCOPE_SYSTEM` 常量已存在；`import java.util.Comparator` 已补。
- **未验证（需人工执行）**: 按用户要求本轮不执行 mvn 与 `pnpm ts:check`，故新增的 4 个 Java 用例尚未实际运行，`effectiveDefault` 的端到端表现（保存个人默认后刷新页面是否自动套用）也未经运行时验证。`vue-tsc` 在本机 8G 内存下 OOM 的历史问题同上一条。
- Dependency or integration impact: `effectiveDefault` 为新增响应字段，对既有消费者是纯增量，但 `frontend/workbench` 若将来消费 `visible-list` 应直接使用该字段而非自行推断。无新增依赖，无数据库改动。
- Remaining work: 运行 `mvn -pl yudao-module-zsjos -am -Dtest=AdvancedFilterTemplateServiceImplTest test` 与 `pnpm ts:check`；浏览器验证个人默认优先的实际表现。

### 2026-09-17 17:40:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 修复需求反馈审批的可视化路径——审批人（部门负责人、董事长）在审批中心应能看到自己正在审什么，包括用户填写的表单内容和附件图片预览。
- Key decisions: (1) 可见性口径由「持有 `zsjos:feedback:requirement:manage`」改为「管理权限 ∨ 提交人 ∨ 该轮次 `approval_context_json` 记录的指定审批人」。原先审批人只看到「无权查看」，而审批按钮仍在，等于逼人盲签。(2) 审批卡不再调用 `FeedbackService.getAdmin()`——它带 `@ZsjosPermission(action="read-admin")`，切面抛异常被上层 `catch` 静默吞掉，导致申请附件与处理结果附件整块消失且不留日志。改为直接读轮次快照并自行解析。(3) 申请内容按 `round.formSnapshotJson` + `round.valueSnapshotJson` 渲染，字典标签用提交时冻结的那份，不查当前字典。(4) `brief()` 的深链只对提交人下发，审批人不给——反馈页按 `read-own` 放行，审批人点进去只会看到无权页。(5) 驳回重提仍走完整两轮审批，不做简化。
- Result: `FeedbackContentProvider` 重写可见性、轮次解析与内容渲染；`ZsjosApprovalAttachmentSupport` 新增 `resolveAttachment(Long)`，按 infra 文件编号补齐名称/MIME/大小并签名；附件 MIME 由快照 `type` 透传到 `BpmApprovalFieldVO.Attachment.contentType`，前端 `AttachmentGrid.isImage` 据此恢复图片预览（此前恒为 false，所有图片降级成文字链接）。
- Changed files: `backend/.../service/bpm/content/provider/FeedbackContentProvider.java`；`backend/.../service/bpm/content/ZsjosApprovalAttachmentSupport.java`；`backend/.../test/.../provider/FeedbackContentProviderTest.java`（新增）；`backend/.../test/.../provider/ApprovalContentProviderBusinessKeyTest.java`；`docs/api/feedback-management.md`；本 handoff。
- Verification evidence: 本次改动共 19 个用例全绿（`FeedbackContentProviderTest` 14 + `ApprovalContentProviderBusinessKeyTest` 5）；连同既有 `ZsjosApprovalAttachmentSupportTest`、`ZsjosApprovalProviderWiringTest` 共 31 例 0 失败。运行方式：因约定不使用 mvn，改以 `javac` 编译到临时目录后用 JUnit Platform Launcher 直接执行，classpath 为 `~/.m2` 中已下载的依赖 jar + 各模块 `target/classes`（须把新编译产物置于 `target/classes` 之前，否则会命中陈旧 class，这一点在排查中真实踩到）。覆盖点：部门负责人/董事长/提交人/管理者四条可见路径、无关账号仍被拒、跨轮审批人不得越权、历史轮次渲染自己的快照与驳回原因、缺轮次段退回最新一轮、附件 MIME 透传、结果附件签名失败只丢单个、无附件不留空分组、反馈不存在时返回 notFound。
- **未验证（需人工执行）**: 按用户要求未执行 mvn，故模块全量测试与打包未跑；未执行 `pnpm ts:check`。浏览器侧的审批中心实际渲染（表单字段、附件缩略图与点击放大）未验证。
- Dependency or integration impact: `ZsjosApprovalAttachmentSupport` 新增 public 方法，属增量。`businessId` 由「仅 workOrderId」改为「workOrderId:roundNo」，但该句柄只在 Provider 内部消费（`BpmApprovalContentServiceImpl` 不透明传递），对注册表与其他 Provider 无影响。可见性放宽仅限「该轮次的指定审批人」，上游 `getTodoTask`/`getDoneTask` 仍按 userId 校验任务归属，未放宽到人人可见。
- Remaining work: 运行 `mvn -f backend/pom.xml -pl yudao-module-zsjos -Dtest='FeedbackContentProviderTest,ApprovalContentProviderBusinessKeyTest,ZsjosApprovalAttachmentSupportTest,ZsjosApprovalProviderWiringTest' test`（注意：**不要带 `-am`**，且 `-pl` 只能用裸模块名，见下）；工作台审批中心浏览器验证。处理人（应用开发工程师）目前仍无反馈管理菜单——候选池按 `zsjos:feedback:requirement:manage` 计算，而该菜单只授予 `system_administrator`，因此岗位角色无法被指派，此项用户确认后再开。

### 2026-09-17 17:55:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 修掉上一条交付里给的验证命令跑不通的问题（`Could not find the selected project in the reactor: yudao-module-zsjos`）。
- Key decisions: (1) 命令跑不通与 pom 无关。reactor 根是 `backend/pom.xml`（仓库根 `/opt/zsjos` 下没有 pom.xml），而 `-pl` **只接受相对于 reactor 根的裸模块名**——任何路径前缀都会失败：`backend/yudao-module-zsjos`、在 `backend/` 下写 `./yudao-module-zsjos` 都报同一个错。(2) `-am` 必须去掉。它会把上游模块的测试一起编译，而 `yudao-module-bpm` 有一个**并行会话留下的未跟踪半成品** `api/approvalcontent/BpmApprovalFormatTest.java`，调用了主源码里不存在的 `BpmApprovalFormat.maskCard(...)`，直接卡死整条链，连 zsjos 都到不了。该文件非本次改动，**未修改**。(3) 上游依赖改为一次性 `-Dmaven.test.skip=true -DskipTests install` 装进本地仓库，之后不带 `-am` 单跑目标模块。
- Result: 上一条交付的 31 个用例在 mvn 下真实跑通，无需再依赖 javac + JUnit Launcher 的手工 classpath。
- Changed files: 无源码改动；仅本 handoff 与记忆文件。
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -Dtest='FeedbackContentProviderTest,ApprovalContentProviderBusinessKeyTest,ZsjosApprovalAttachmentSupportTest,ZsjosApprovalProviderWiringTest' test` → `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。分项：`FeedbackContentProviderTest` 14、`ZsjosApprovalAttachmentSupportTest` 8、`ApprovalContentProviderBusinessKeyTest` 5、`ZsjosApprovalProviderWiringTest` 4。
- **未验证（需人工执行）**: 工作台审批中心浏览器渲染仍未验证。`yudao-module-bpm` 的 `BpmApprovalFormatTest` 仍编译失败（缺 `maskCard`），因此**整仓 `mvn test` 目前是红的**——需要该半成品会话补上方法或先剔除该文件。
- Dependency or integration impact: 上游模块已 `install` 到 `~/.m2`（`2026.07-jdk25-SNAPSHOT`），后续单模块构建可直接复用，不必每次 `-am`。副作用：本地仓库里的上游 jar 现在是当前工作树的快照，含未提交改动。
- Remaining work: 同上一条（处理人入口待用户确认）。另：`-DfailIfNoTests=false` 是无效属性名，正确为 `-Dsurefire.failIfNoSpecifiedTests=false`，给其他会话参考。

### 2026-09-17 18:40:00 +08:00

- Branch: main
- Worktree: /opt/zsjos
- User goal: 让整仓 `mvn test` 变绿。上一条把编译/路径问题解决后，暴露出一批**长期存在的**红测试（不是本次改动引入）。
- Key decisions: 逐个定位后按类型分别处理，不做「改断言让它过」的通用操作：
  1. **`BpmApprovalFormat.maskCard(String)` 缺失**（`yudao-module-bpm` 编译失败，卡死整条 `-am` 链）。按测试注释指名的权威实现 `WithdrawalServiceImpl.mask` 逐字补齐语义：`null` 或长度 < 8 → `"****"`，否则首 4 + `" **** **** "` + 末 4。**注意该方法目前只被测试调用，生产代码尚未接入**——属并行会话的「统一卡号脱敏」半成品，本次只补编译。
  2. **`system_menu.workbench_render_mode` 列在 H2 测试建表脚本里缺失**。生产库由 V137 迁移添加，`create_tables.sql` 漏了，导致 `MenuServiceImplTest` 17 个用例（14 错 + 3 败）全红。补列，位置对齐生产（`component_name` 之后）。
  3. **H2 `datetime` 默认精度只有秒**，毫秒/纳秒往返必然丢失，`OAuth2ApproveServiceImplTest`、`PmsIterationServiceImplTest` 因此断言失败。前者把全表 datetime 提到 `datetime(6)` 并把 `CURRENT_TIMESTAMP` 同步为 `CURRENT_TIMESTAMP(6)`（差异经 diff 校验：只动精度）；后者按本仓库既有惯例在测试里 `truncatedTo(ChronoUnit.MICROS)`。
  4. **`ZsjosAuditCoverageTest` 端点清单基线过时**（301→310 GET 等）。该测试的分类不变量全部通过，只有计数基线失败，说明新增端点审计分类正确。实测四个真值后更新基线。过程踩坑：`javap -v` 会把注解在常量池和 Code 属性里各计一次（翻倍），静态统计得 303 而运行时是 310；最终靠临时替换断言为打印语句取真值。
  5. **两处测试断言与当前设计冲突，判定为测试过时（非实现错误）**，各附证据后修正：
     - `ZsjosBpmBusinessTaskTargetServiceImplTest`：期望 `/zsjos/material-library/approvals`，但该路由**在整个前端不存在**（`constants.ts` 只有 `browse`/`manage`），而 `MaterialLibraryPage` 在 `manage` 路由下确实消费 `taskId`/`versionId` 并调用审批接口。改为 `manage`。
     - `ContentReviewBatchServiceTest`：期望编导「混合通过+退回」也放行。但 `validateTaskAction` 的守卫与 `docs/content-review-optimization.md`（第 95-115 行**逐字包含该守卫代码**）以及 `completeDirector`（有退回即 `rejectTask`）三者自洽；守卫是 ea5dccd2 后加的，测试是 a9f7e2a7 先写的，当时未同步。改名为 `directorApprovalRejectsMixedApprovedAndReturnedItems` 并断言拒绝，另补 `directorApprovalAcceptsBatchWhereEveryItemPassed` 守住全票通过的正向路径。
- Result: **整仓 `mvn -f backend/pom.xml test` → BUILD SUCCESS**。各模块：system 570、zsjos 1209、bpm 115(skip 6)、pms 236、hrm 85(skip 3)、eam 43、infra 237(skip 11)、其余 14/13/10/20/9/1。全部 0 失败 0 错误。
- Changed files: `backend/.../bpm/api/approvalcontent/BpmApprovalFormat.java`（补 `maskCard`）；`backend/yudao-module-system/src/test/resources/sql/create_tables.sql`（补列 + datetime 精度）；`backend/.../zsjos/framework/audit/ZsjosAuditCoverageTest.java`（更新基线）；`backend/.../zsjos/service/contentreview/ContentReviewBatchServiceTest.java`；`backend/.../zsjos/service/bpm/ZsjosBpmBusinessTaskTargetServiceImplTest.java`；`backend/yudao-module-pms/src/test/.../PmsIterationServiceImplTest.java`；本 handoff。
- Verification evidence: 全仓 `exit=0` / `BUILD SUCCESS`。zsjos 单模块 1209 例 0 失败。`grep -rn 'DBG' --include=*.java` = 0（插桩已全部清除）。
- **未验证（需人工执行）**: 工作台审批中心浏览器渲染（表单字段、附件缩略图与点击放大）仍未验证。
- Dependency or integration impact: `create_tables.sql` 的 datetime 精度提升是纯测试基础设施变更，不影响生产 schema。`maskCard` 为新增 public 静态方法，无生产调用方，接线上线前需确认由哪个 Provider 使用（`WithdrawalContentProvider` 目前直接取 `item.getMaskedCardNumber()`，脱敏在 `WithdrawalServiceImpl` 完成）。
- Remaining work: 处理人（应用开发工程师）工作台入口待用户确认。`maskCard` 的统一接入未完成——属并行会话范围。

## Workstream Registration - 2026-09-18 09:43:00 +08:00

- Workstream ID: `test-viral-template-recovery`; Owner: Codex `/root`
- Goal: 修复两类爆款拆解页面模板引用失效导致只显示封面的故障。
- Non-goals: 不更改权限、字典、审批流程或历史素材；不覆盖有效/自定义模板；未经单独确认不写共享测试库、不部署或重启服务。
- Environment: test（/etc/zsjos/agent-environment）；Branch: main；Worktree: /opt/zsjos；Base commit: 1639c6659ddc9f047934eadea32a728b87c987a7；Target branch / Integration order: None。
- Ownership scope: MaterialTypeServiceImpl.java、对应 MaterialTypeServiceImplTest.java；ViralAccountMaterialForm.tsx 及现有测试；两个 Viral*DecomposePage.tsx；script/sql/mysql/repair-viral-template-references.sql；docs/operations/viral-material-review-deployment.md；本工作记录。
- Dependencies: 现有默认模板、素材 Mapper、Ant Design、Vitest/JUnit；无新增依赖。保留所有已有未提交修改。
- Verification plan: 聚焦后端/前端测试、类型检查、临时表隔离 SQL 重复执行与作用域验证；浏览器验证如环境可用；共享测试库修改需另行确认，确认后核对模板字段及中文 HEX。

## Delivery Entry - 2026-09-18 09:48:21 +0800

- Workstream ID: `test-viral-template-recovery`; Owner / Branch / Worktree: 同本工作流登记；HEAD unchanged: `1639c6659ddc9f047934eadea32a728b87c987a7`。
- User goal: 修复爆款账号、爆款内容拆解页只显示封面的故障。
- Key decisions: 默认模板引用必须检查目标存在；失效引用优先关联已发布版本，不能把管理员草稿自动发布；没有模板版本时沿用后端默认字段；前端缺字段阻断空表单并提供重新请求入口。无需新增依赖或更改接口字段。共享测试库修复单独请求明确授权。
- Execution result: 源码、测试、恢复脚本及运维说明已完成。只读确认 tenant=1 的类型 1/2 分别指向不存在的模板 2/3，两类均无模板/素材记录（包括删除记录）；读取当前部署 jar 字节码确认现有初始化可在引用清空后重建默认模板。未执行共享库写入、部署或重启。
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/material/MaterialTypeServiceImpl.java`；对应 `src/test/java/.../material/MaterialTypeServiceImplTest.java`；`frontend/workbench/src/components/ViralAccountMaterialForm.tsx` / `.test.ts`；`frontend/workbench/src/pages/ViralAccountDecomposePage.tsx`、`ViralContentDecomposePage.tsx`；`script/sql/mysql/repair-viral-template-references.sql`；`docs/operations/viral-material-review-deployment.md`；本工作记录。
- Verification evidence: 聚焦 JUnit 6/6；Vitest 6/6（两种表单三栏正常渲染、空字段/缺模板提示与保存按钮阻断）；`npm run typecheck` passed；scoped `git diff --check` passed。首次构建因已有 root 所有缓存权限失败，随后使用 sudo 和 `/home/ubuntu/.m2/repository` 完成 Maven 检查；无权限调整。
- SQL verification: 独立 `mysql:8` 容器，使用仓库真实建表定义和合成夹具执行完整恢复脚本；首次更新 2 行，第二次 0 行；断言其他类型/租户、已有草稿、有效引用及含已删历史素材的类型不变；UTF-8 客户端 HEX 对照通过。隔离容器已关闭回收。测试脚本 `/tmp/verify-viral-template-sql.py`，测试日志 `/tmp/viral-template-maven.log`、`/tmp/viral-template-vitest.log`。
- Dependency or integration impact: 无新依赖、分支、提交或迁移版本；React 与 Vue 继续消费相同 currentSchema/fields 结构，已检查 Vue 素材/模板/导入入口的数据消费。修复源代码尚未发布。
- Remaining work: 等待对测试库两条失效引用修复的明确授权；批准后备份目标行、执行恢复 SQL、通过页面触发既有默认模板初始化，检查已发布字段及中文 HEX。当前无可用浏览器或已授权登录会话，实际 API 响应、重试交互和桌面/移动页面仍未验证，不能宣称测试站已恢复。生产构建不适用本轮局部逻辑交付；若后续发布需执行对应构建和验收。

## Registration Update - 2026-09-18 09:53:00 +08:00

- Workstream ID: `test-viral-template-recovery`; metadata/ownership unchanged.
- Authorization: 用户已明确确认备份并修复测试库 tenant=1 的 viral_account / viral_content 两条失效引用，使现有初始化重建默认模板；无需重启、部署、权限或字典变更。
- Execution context: 共享测试库限定范围写入已获授权；备份存于仓库外 `/var/tmp/zsjos-viral-template-recovery-20260918-0953/`，不记录完整数据库载荷。
- Verification plan update: 写前重新核对两行状态和模板/素材计数，执行已验证脚本，核对实际更新数及重复执行结果，再查询重建模板三栏字段和 UTF-8 HEX；真实登录请求仍取决于可用会话。

## Delivery Entry - 2026-09-18 09:56:04 +0800

- Workstream ID: `test-viral-template-recovery`; Branch/Worktree/Owner: 同登记；HEAD unchanged: `1639c6659ddc9f047934eadea32a728b87c987a7`。
- User goal: 确认执行两类爆款模板引用修复。
- Key decisions: 按已批准范围执行现有恢复 SQL；不修改其他类型、权限、字典或服务；使用现有部署后端的页面加载初始化能力重建默认模板。
- Execution result: 写前核对 tenant=1 的类型 1/2，失效引用 2/3，模板和素材计数均为 0；备份目标两行后执行脚本，第一次更新 2 行，重复执行更新 0 行。两行引用均已置空，版本各加 1。未重启或发布代码。
- Backup: `/var/tmp/zsjos-viral-template-recovery-20260918-0953/type-rows.sql`，目录 0700/文件 0600；确认只含两个目标类型。SHA-256 `65b529933629065b2bc6fda258c8750a4dc059add5cfd37310387fd99299b6cb`。回退限制沿用脚本说明，初始化后采用向前修复。
- Changed files: 仅 `handoff/test_main.md`（注册授权更新及本条交付）；仓库外新增上述定向备份。共享测试数据库仅更新两条素材类型记录。
- Verification evidence: 使用 UTF-8 MySQL 客户端执行，更新行数 2/0；两类型名称 HEX 分别为 `E78886E6ACBEE8B4A6E58FB7`、`E78886E6ACBEE58685E5AEB9`，与爆款账号/爆款内容一致。脚本未变，沿用上一轮隔离 MySQL 与代码测试证据。初次备份命令因 where 引号转义失败且未产生备份或写库，随后改为位置参数后成功；只在备份验证通过后执行修复。
- Dependency or integration impact: 无新依赖、提交、部署或重启；上一轮防回归源码仍未部署。
- Remaining work: 查询时尚未收到页面触发的初始化请求，两类 current_schema_version_id 当前为 NULL；已告知用户刷新页面以触发现有默认模板初始化。没有可用浏览器/授权登录会话，不能代替用户发起真实登录请求；模板生成后的三栏字段/标签 HEX、两个页面真实显示尚待刷新后核验，不宣称模板已生成或页面已实测恢复。

## Workstream Registration - 2026-09-18T10:05:50+08:00

- Workstream ID: `test-db-backup-cleanup`; Owner: Codex `/root`。
- Goal: 按用户明确要求删除 /opt/zsjos 和 /opt/zsjos-runtime 内全部已识别数据库备份，释放磁盘空间。
- Non-goals: 不操作现用数据库、SQL 源码、服务、其他目录或已有用户修改。
- Environment: test（/etc/zsjos/agent-environment）；Branch: main；Worktree: /opt/zsjos；Base commit: 1639c6659ddc9f047934eadea32a728b87c987a7；Target branch / Integration order: None。
- Ownership scope: backups/**、deploy/production/backups/**、/opt/zsjos-runtime/backups/** 中已核对的 40 个 SQL/TSV 备份和 MD5 校验文件；handoff/test_main.md（仅追加）。
- Dependencies: None；批量备份删除由当前用户请求明确授权，不保留额外副本；删除后无法从这些文件恢复。
- Verification plan: 精确文件清单删除，核对文件数量、目录残留、磁盘可用空间和受影响 Git 路径；不运行数据库写入或应用测试。

## Delivery Entry - 2026-09-18T10:06:28+08:00

- Workstream ID: `test-db-backup-cleanup`; Owner / Branch / Worktree: 同本轮登记；HEAD unchanged: `1639c6659ddc9f047934eadea32a728b87c987a7`。
- User goal: 清除 zsjos 和 zsjosruntime 内的数据库备份以释放空间；实际运行目录为 /opt/zsjos-runtime。
- Key decisions: 按已核对清单删除数据库 SQL/TSV 备份及其 MD5 文件；保留目录、现用数据库、初始化/迁移源码和非数据库备份。
- Execution result: 删除 40 个文件，逻辑大小 7,222,908,111 字节，占用磁盘块 7,223,050,240 字节（约 6.73 GiB）。首次删除遇到 root 所有目录权限，随后用 sudo 按同一清单核对 inode/大小后完成，未更改权限。
- Changed files: backups/ 下 4 个文件、deploy/production/backups/ 下 3 个文件（Git 跟踪）、/opt/zsjos-runtime/backups/ 下 33 个文件；handoff/test_main.md 追加登记及交付。
- Verification evidence: 三个备份目录递归检查剩余文件数为 0；完整目录扫描未发现额外压缩 SQL/数据库 dump 候选；Git scoped diff 确认 3 个跟踪备份删除；handoff scoped diff --check 通过；df 显示磁盘可用约 37 GiB、使用率 68%。
- Dependency or integration impact: None；未执行数据库 SQL、服务变更、提交或推送；源码测试不适用于备份清理。
- Remaining work: None；已删除备份不可用于历史恢复，自动备份策略未变更。

## Workstream Registration - 2026-09-19T00:35:58+08:00

- Workstream ID: `test-origin-sync-20260919`; Owner: Codex `/root`。
- Goal: 按用户要求拉取远程最新代码并合并到当前本地 main。
- Non-goals: 不部署、重启、执行数据库迁移、推送或修改业务实现；保留已有 yudao-server.jar 修改。
- Environment: test（/etc/zsjos/agent-environment）；Branch: main；Worktree: /opt/zsjos；Base commit: d9850a6a15a77b2ddd7a1f508df1d7a8eab2d3e1。
- Ownership scope: origin/main 两个提交涉及的跟踪文件（仅 Git 快进同步）及 handoff/test_main.md（追加记录）。
- Dependencies: origin/main；用户已明确授权拉取合并。Target branch: main；Integration order: origin/main 快进至 main。
- Verification plan: 检查提交分歧、快进结果、HEAD 与 origin/main 一致性、无冲突及原有 jar SHA-256 保持不变；本次仅同步，不作运行时验收。

## Delivery Entry - 2026-09-19T00:36:26+08:00

- Workstream ID: `test-origin-sync-20260919`; Owner / Branch / Worktree: 同本轮登记；HEAD: `7785d317c829650980192d06383739958f642e43`。
- User goal: 拉取远程最新代码合并到本地。
- Key decisions: 使用 git fetch origin 与 git merge --ff-only origin/main；无需暂存或覆盖本地改动。
- Execution result: 从 d9850a6a 快进至 7785d317，同步 2 个提交、281 个文件，无冲突。
- Changed files: 上述远程提交包含的 281 个文件（git diff --name-only d9850a6a..7785d317 可复现清单）；本地追加 handoff/test_main.md。
- Verification evidence: HEAD 与 origin/main 完全一致，ahead/behind 为 0/0，git ls-files -u 为空；原有 yudao-server.jar SHA-256 前后一致（54a2794d0993d47ccf1a4613cd8be97e86904a50e480a1343999936bdb32a81d）；记录 scoped diff --check 通过。
- Dependency or integration impact: 无新合并提交、推送、部署、服务或数据库操作；工作区保留 jar 原有修改及本次交付记录。
- Remaining work: None（代码同步范围）；未执行构建、应用测试或 SQL，远程变更运行效果未验证。

## Workstream Registration - 2026-09-19 13:26:55 +08:00

- Workstream ID: `test-s3-upload-endpoint`
- Goal: 修复自定义访问域名导致的上传预签名地址错误，保留读取链接行为。
- Non-goals: 不改数据库/domain、前端、权限、GET 签名行为；不部署、重启、提交或推送。
- Branch: `main`; Worktree: `/opt/zsjos`; Base commit: `7785d317c829650980192d06383739958f642e43`; Target branch / Integration order: None
- Ownership scope: Infra `S3FileClient.java`、`S3FileClientTest.java`、`docs/api/material-library.md`、`handoff/test_main.md`。
- Owner: Codex `/root`; Dependencies: 现有 AWS SDK / JUnit，无新依赖。
- Verification plan: PUT 两个入口、自定义公开读取域名、私有 GET、path-style 回归测试及编译；核对 Admin/Workbench 调用契约与 scoped diff。真实共享环境上传和服务启停须另行授权。

## Delivery Entry - 2026-09-19 13:29:23 +08:00

- Workstream ID: `test-s3-upload-endpoint`; Branch / Worktree: 同本次登记；HEAD: `7785d317c829650980192d06383739958f642e43`（未变）。
- User goal: 修复爆款账号拆解图片上传失败，保留自定义文件访问域名。
- Key decisions: 新增独立 PUT 签名器，两个上传入口均使用存储 endpoint；原 GET 签名器及 domain 配置不变，无数据库写入。
- Result: 修复自定义域名前被 SDK 重复拼接桶名的问题；Admin 旧 PUT 入口与 Workbench 业务直传入口同步修复，响应字段不变。读取行为按原逻辑保留。
- Changed files: `backend/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/s3/S3FileClient.java`; `backend/yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/framework/file/core/s3/S3FileClientTest.java`; `docs/api/material-library.md`; `handoff/test_main.md`。其余既有修改保留。
- Verification evidence: Maven reactor 测试在 yudao-common testCompile 因无法创建测试输出目录中止；改用现有依赖与 target/classes，在 `/tmp/zsjos-s3-check-bcszpco8` javac 编译目标生产类和测试，JUnit Launcher 执行 7 项通过、6 项外部存储集成测试按原注解跳过。覆盖 COS 自定义域名、两个 PUT 入口、签名期限/请求头、公开读取域名、path-style 私有 GET、默认桶域名。Workbench `npm test -- src/services/directUpload.test.ts --configLoader runner` 2 项通过（默认配置打包因 .vite-temp 无写权限失败，runner 成功绕过）。Admin `useUpload.ts` 已核对直接 PUT 后端返回 uploadUrl，与旧入口测试匹配；浏览器实测未执行。`git diff --check` 通过。
- Dependency or integration impact: 无新增依赖，无前端/认证/权限接口格式变更；未提交、部署、重启或覆盖现有 jar。存储 endpoint 必须可被浏览器访问且允许 CORS。
- Remaining work: 经用户另行明确授权后构建并部署测试服务、重启并验证 Admin/Workbench 实际上传、确认和预览；当前运行服务尚未加载修复。完整 Maven reactor 验证仍受既有输出目录问题阻断。
