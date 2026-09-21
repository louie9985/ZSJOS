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

## Workstream Registration - 2026-09-20T10:36:55.417592+08:00

- Workstream ID: `test-aging-pool-filters`; Owner: Codex `/root`。
- Goal: 公海池常驻筛选栏复用客资管理样式，新增归属我的和我跟进的筛选。
- Non-goals: 不变更授权范围、数据库、共享服务或部署，不新增依赖。
- Environment: test；Branch: main；Worktree: /opt/zsjos；Base commit: 050f43dd0dcc996ce468efafa4715870264dafd6；Target branch / Integration order: None。
- Ownership scope: Workbench LeadAgingPoolPage.tsx、services/api.ts、公海池样式及相关测试；后端 LeadAgingPoolPageReqVO、LeadAgingPoolCycleMapper、LeadAgingPoolServiceImpl、LeadConstants 及公海池相关测试；docs/api/zsjos-lead-aging-pool.md；handoff/test_main.md。
- Dependencies: 现有公海池权限、服务端筛选配置和 A/B 业务关系；无新依赖。
- Verification plan: Workbench 定向测试与类型检查、后端筛选 SQL/服务测试及编译，核对 Admin 既有调用兼容性；检查浏览器可用性并验证受影响宽度，无法执行的检查单独记录。

## Delivery Entry - 2026-09-20T10:42:16.944824+08:00

- Workstream ID: `test-aging-pool-filters`; Owner / Branch / Worktree: 同本轮登记；HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`。
- User goal: 公海池常驻筛选栏与客资管理一致，新增“归属我的”“我跟进的”。
- Key decisions: 复用 lead-simple-status-shell；常驻选项单选并回到第一页；归属我的对应当前正式归属 A，我跟进的按当前协同 B 实现（已向用户提出语义澄清，暂无回复，采用已说明默认假设）；关系条件由服务端绑定登录用户，与现有范围取交集，manage-all 不绕过个人过滤。高级筛选不隐藏常驻栏，状态和高级条件取交集。
- Result: 完成前端样式、请求参数、后端分页过滤、API 文档与回归测试；Admin 未传 relationScope 的现有 GET/POST 调用和响应格式保持兼容。
- Changed files: Workbench src/pages/LeadAgingPoolPage.tsx、src/pages/lead-aging-pool.guard.test.ts、src/services/api.ts、src/styles/pages/aging-pool.css；后端 LeadConstants.java、LeadAgingPoolPageReqVO.java、LeadAgingPoolCycleMapper.java、LeadAgingPoolServiceImpl.java、LeadAgingPoolCycleMapperTest.java、LeadAgingPoolServiceImplTest.java；docs/api/zsjos-lead-aging-pool.md；handoff/test_main.md。
- Verification evidence: npm run typecheck 通过；Vitest 定向页面及样式检查 30 项通过；Maven reactor compile 在 zsjos target/classes/META-INF/spring-configuration-metadata.json 写权限处失败。使用现有依赖、显式 Lombok processor 在 /tmp/zsjos-aging-check-h536n8me 编译本次 4 个生产文件及 2 个测试文件成功，统一 JUnit 6.0.3 后执行 10 项测试全部通过，覆盖个人关系 SQL、授权范围交集、空高级结果、manage-all 登录用户传递、状态与高级筛选交集、Admin 不传新增字段兼容及既有公海业务。git diff --check 通过。
- Dependency or integration impact: 无新依赖、SQL、权限分配、服务变更、提交或部署；需前后端一同部署后新关系筛选才生效。Admin 消费者只读核对调用契约，并由缺省参数后端测试验证兼容。
- Remaining work: 完整 Maven 编译受已有构建输出目录权限限制；环境未提供可用浏览器及登录会话，桌面/移动视觉、实际点击切换与真实 API 联调尚未验证。当前运行服务未加载变更，不宣称页面已上线或完成浏览器验收。

## Workstream Registration - 2026-09-20T10:56:27.218220+08:00

- Workstream ID: `test-lead-contact-activation`; Owner: Codex `/root`.
- Goal: 第一页联系方式查重命中全部激活提醒，提交兜底复用，交叉命中视为强重复。
- Non-goals: 不改客资状态、归属或编辑联系方式规则，不部署、不写共享数据库、不新增依赖。
- Environment: test（/etc/zsjos/agent-environment）；Branch: main；Worktree: /opt/zsjos；Base commit: 050f43dd0dcc996ce468efafa4715870264dafd6；Target branch / Integration order: None。
- Ownership scope: 后端 Lead 提交 Controller/VO/Service、联系方式激活 Service、Lead/Activation Mapper、通知场景及相关测试；Workbench LeadSubmissionPage、services/api.ts 及相关测试；Admin/H5 提交结果文案；docs/api/zsjos-lead-submission-dispatch.md、docs/business/lead-order-state-machine.md；handoff/test_main.md。
- Dependencies: 复用现有激活表、通知框架、提交权限。公海筛选工作已交付，保留其 services/api.ts 等全部既有改动。
- Verification plan: 后端匹配/多客资/幂等/通知/接口权限测试与编译；Workbench 类型检查和定向测试；核对三端提交响应；浏览器可用时验证桌面及移动流程，否则记录未验证项。

## Workstream Registration - 2026-09-20 11:02:00 +08:00

- Workstream ID: `test-media-student-production-ticket-launch`; Owner: Codex `/root`。
- Goal: 在学员概览提供“发起剪辑设计工单”和“发起拍摄外勤工单”，从当前学员已授权账号中选择一个或多个账号，并将账号主页链接和封面快照带入各自的拍剪工单。
- Non-goals: 不改变账号、模板、角色或菜单授权数据；不执行测试数据库写入、迁移、部署或服务重启；不新增依赖。
- Environment: test；Branch: main；Worktree: /opt/zsjos；Base commit: `050f43dd0dcc996ce468efafa4715870264dafd6`；Target branch / Integration order: None。
- Ownership scope: `frontend/workbench/src/pages/MediaStudentsPage.tsx` 及其直接测试；`backend/yudao-module-zsjos` 的拍剪工单服务与直接测试；工单 API 文档；`handoff/test_main.md`（仅追加）。
- Dependencies: 现有学员详情授权账号投影、账号资料快照、账号资料读取 API、通用工单模板、拍剪单据状态机与 Workbench 资源链接组件；不引入新的数据源。
- Verification plan: Workbench 定向测试、类型检查；拍剪服务定向测试和模块编译；检查账号链接的服务端覆盖、封面快照、逐账号创建与失败反馈；`git diff --check`。无登录浏览器会话时记录真实交互未验证。

## Workstream Registration - 2026-09-20 11:30:00 +08:00

- Workstream ID: `test-partner-inbox-overview`; Owner: Codex `/root`.
- Goal: 兼职管理收件箱与学员管理对齐，补齐头像、收起/搜索、表格视图、概览与客资明细。
- Non-goals: 不变更后端、权限、数据库、共享服务，不新增依赖、不部署。
- Environment: test；Branch: main；Worktree: /opt/zsjos；Base commit: 050f43dd0dcc996ce468efafa4715870264dafd6；Target branch / Integration order: None。
- Ownership scope: frontend/workbench/src/pages/SubordinatePartnerPage.tsx、styles/pages/subordinate-partner.css、pages/subordinate-partner.guard.test.ts、test/partner-inbox.*；docs/api/zsjos-subordinate-partners.md；handoff/test_main.md（追加）。
- Dependencies: 现有 managementApi、SubjectAvatar、InboxAvatarRail；保留全部既有其他工作区改动。
- Verification plan: 定向测试、类型检查、scoped diff；探测现有浏览器工具并验证桌面/移动交互，可用性限制如实记录。

## Delivery Entry - 2026-09-20 11:18:00 +08:00

- Workstream ID: `test-lead-contact-activation`; Branch / Worktree: `main` / `/opt/zsjos`; HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`。
- User goal: 第一页手机号/微信号查重，命中全部客资激活提醒；交叉联系方式按强重复；多客资分别处理，无负责人统一展示。
- Key decisions: 新增普通、销售自拓、教务自拓三个联系方式查重入口；同字段和交叉字段均走 LeadActivation；逐 Lead 加锁、请求身份隔离和幂等；不改变历史 Lead 状态或归属；无负责人仍保存激活；最终提交再次执行联系方式激活，未命中才进入既有弱重复规则。
- Result: 后端、Workbench、Admin、H5 和相关 API/业务文档已完成；保留同一工作树其他工作流修改。
- Changed files: LeadContactCheckReqVO、LeadContactActivationService、LeadSubmissionController/Service/Impl、LeadMapper、LeadDuplicateMatcher、LeadNotifySceneProvider 及测试；Workbench LeadSubmissionPage、api、guard test；Admin/H5 提交结果文案；提交 API 与状态机文档；handoff/test_main.md。
- Verification evidence: 独立 javac 编译本次后端生产类和测试类通过；统一 JUnit 6.0.3 执行 72 项后端定向测试全部通过；Workbench 提交 guard 7 项通过，Workbench `npm run typecheck` 通过；H5 `vue-tsc --noEmit` 通过；Admin `pnpm ts:check` 仍被既有 ElMessageBox、AdvancedFilterTemplate 和 materialType 未使用符号错误阻断；`git diff --check` 通过。
- Dependency or integration impact: 无新依赖、数据库写入、部署、服务启停、提交或推送；复用现有 LeadActivation 表、通知框架和提交权限；主管/销售/教务收件人仍由通知规则解析。
- Remaining work: 未执行真实浏览器、数据库并发、真实 API 和通知送达验证；当前运行服务未加载变更。Admin 全量类型检查的既有错误未在本任务内修复。

## Delivery Entry - 2026-09-20T11:06:53+08:00

- Workstream ID: `test-media-student-production-ticket-launch`; Owner: Codex `/root`。
- Branch / Worktree / HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6（未变）。
- User goal: 学员概览新增两类工单入口，必选一个或多个名下账号，自动带入主页链接和封面，使用通用链接组件。
- Key decisions: 撤回本轮未经确认的逐账号拆单草稿；多账号是否合并一单需用户明确，现有单账号模型不能替代业务决定。共享工作树另有活动写入任务，暂停本任务修改以遵守串行规则。前述登记中的逐账号创建仅为已撤回的假设，不构成批准合同。
- Result: 已确认概览操作栏、服务端模板目录、账号资料 homepage_url/cover 和 ResourceLink 的复用位置；没有交付功能实现。
- Changed files: 仅 handoff/test_main.md 本轮追加；本轮五个功能/测试/文档文件已恢复至修改前 HEAD 内容，其他任务改动保留。
- Verification evidence: 草稿 typecheck 未通过；Maven 因既有 target/classes/META-INF/spring-configuration-metadata.json 写权限失败，测试未执行。撤回后核对五个文件无差异；无真实发单、浏览器或数据库写入。
- Dependency or integration impact: None；未提交、部署、重启或变更授权。
- Remaining work: 确认多账号工单语义，待共享工作树写入任务串行交接后实现并验证。

## Workstream Registration - 2026-09-20 11:40:00 +08:00

- Workstream ID: `test-media-student-production-ticket-multi-account`; Owner: Codex `/root`。
- Goal: 按用户确认，一张剪辑设计或拍摄外勤工单关联多个同一学员账号，并冻结账号主页链接和封面图快照。
- Non-goals: 不拆分为多张工单，不修改账号、角色或菜单授权数据，不执行测试数据库迁移、部署或服务重启，不新增依赖。
- Environment: test；Branch: main；Worktree: /opt/zsjos；Base commit: `050f43dd0dcc996ce468efafa4715870264dafd6`；Target branch / Integration order: None。
- Ownership scope: 拍剪工单请求/服务/持久化、统一工单信封、V265 迁移、学员概览页面及直接测试、工单 API 文档、`handoff/test_main.md`（仅追加）。
- Dependencies: 现有媒体账号对象权限、账号资料 `homepage_url`/`cover` 快照、通用工单中心、ResourceLink；无新依赖。
- Verification plan: 定向前端测试与 typecheck；拍剪服务测试与模块编译；检查同学员约束、链接服务端覆盖、封面/账号快照和单账号兼容；`git diff --check`。无登录浏览器会话时不宣称实际交互通过。

## Delivery Entry - 2026-09-20 11:20:00 +08:00

- Workstream ID: `test-media-student-production-ticket-multi-account`; Owner: Codex `/root`。
- Branch / Worktree / HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6（未变）。
- User goal: 一张剪辑设计或拍摄外勤工单包含多个该学员账号。
- Key decisions: `accountId` 保留首账号兼容用途；新增 `accountIds` 和 `studentPersonId`，服务端逐账号校验对象权限与学员归属，冻结账号 ID、昵称、平台、主页链接和封面文件 ID；`account_link` 用服务端账号主页链接列表覆盖。单个账号旧请求仍可继续使用。
- Result: 学员概览增加两类发起按钮与多选账号；选择项显示昵称-平台，主页链接使用 ResourceLink、封面图预览；一条请求创建一张多账号拍剪工单。V265 新增拍剪单账号集合和快照 JSON 列；查询按主账号或账号集合匹配。
- Changed files: ProductionTicket 请求/响应 VO、DO、Mapper、Service；V265 迁移；Workbench MediaStudentsPage、api 类型和守卫测试；generic-work-order-center API 文档；本 handoff 记录。
- Verification evidence: `npm run typecheck` 通过；`git diff --check` 通过。Vitest 默认配置加载因 `frontend/workbench/node_modules/.vite-temp` 写权限失败；Maven compile 因既有 `target/classes/META-INF/spring-configuration-metadata.json` 写权限失败，未执行服务测试。未执行浏览器、真实登录 API、数据库迁移或共享数据库写入。
- Dependency or integration impact: 无新增依赖、角色授权、部署或服务变更。发布时须先应用 V265 再部署后端和 Workbench；当前运行服务未加载本轮代码。
- Remaining work: 在可写构建输出环境运行前端守卫测试与拍剪服务测试；使用两账号同学员的登录会话验证创建、接收和详情快照；执行受控升级迁移验证后再发布。

## Delivery Entry - 2026-09-20 11:21:00 +08:00

- Workstream ID: `test-media-student-production-ticket-multi-account`; Owner: Codex `/root`。
- Branch / Worktree / HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6（未变）。
- User goal: 一张拍剪工单关联多个同学员账号。
- Key decisions: 保留上一条交付中的数据和兼容方案；前端恢复定位卡交接快照展示，满足既有拍剪创建页面合同。
- Result: 受影响 Workbench 页面可通过类型检查和守卫测试；无需改变已实现的账号集合、主页链接或封面快照路径。
- Changed files: `frontend/workbench/src/pages/MediaStudentsPage.tsx` 与 `handoff/test_main.md` 本条追加；其余本工作流文件见上一条。
- Verification evidence: `npm run typecheck` 通过；`npm test -- src/pages/media-students.guard.test.ts src/pages/production-ticket-positioning.guard.test.ts --run --configLoader runner` 通过，2 个文件共 5 项测试。`git diff --check` 通过。
- Dependency or integration impact: None；未执行数据库迁移、部署、重启、提交或推送。
- Remaining work: 后端 Maven 仍受既有 target 元数据写权限阻断；真实两账号登录流、受控 V265 升级验证和浏览器验收待后续环境完成。

## Delivery Entry - 2026-09-20T11:09:30.205737+08:00

- Workstream ID: `test-lead-contact-activation`; Owner / Branch / Worktree: 同本轮登记；HEAD unchanged: 050f43dd0dcc996ce468efafa4715870264dafd6。
- User goal: 第一页手机号/微信号查重，所有联系方式命中客资均激活并按归属提醒，无负责人统一展示。
- Key decisions: 新增受既有提交权限控制的 contact-check；全部同字段/交叉联系方式及已关闭客资参与提交激活，逐 Lead 加锁和幂等；复用 LeadActivation/ACTIVATED 通知；不改状态归属、不返回历史详情；编辑联系方式沿用原匹配规则。
- Result: 第一版后端和三端响应文案已实现；工作区检测到另两个新注册文件修改任务，已暂停业务文件写入并请求用户串行协调。本条仅追加交接元数据，当前任务未完成。
- Changed files: LeadContactCheckReqVO.java、LeadSubmissionController.java、LeadSubmissionService.java、LeadSubmissionServiceImpl.java、LeadContactActivationService.java、LeadDuplicateMatcher.java、LeadMapper.java、LeadNotifySceneProvider.java 及 4 个相关测试；Workbench LeadSubmissionPage.tsx/services/api.ts；Admin LeadCreateDialog.vue；H5 lead/submit.vue；handoff/test_main.md。保留其他任务全部改动。
- Verification evidence: /tmp/zsjos-contact-check-l6x5v003 中 javac 编译目标生产类和测试成功，统一已有 JUnit 6.0.3 后 72 项测试通过（含 Mapper SQL、提交、匹配、编辑、通知与权限注解）；Workbench 三步原有 guard 6 项通过；H5 vue-tsc 通过；Workbench 类型检查受并发 MediaStudentsPage ticketContext 错误阻断；Admin pnpm ts:check 受既有 ElMessageBox/AdvancedFilterTemplate 等无关错误阻断；git diff --check 通过。
- Dependency or integration impact: 无新依赖、数据库写入、部署、服务启停、Git 提交或推送；复用已有 owner 激活通知规则，主管接收由规则配置决定。
- Remaining work: 待并发工作流结束后同步 API/状态机文档、补充新查重 UI 与提交匹配范围测试、复查身份/通知/幂等边界；浏览器和真实 API/通知送达、事务并发数据库验证未执行；完整 Maven 未重跑（已有 target 写权限问题，使用独立编译目录）。当前运行服务未加载变更，不能宣称完成交付。

## Registration Update - 2026-09-20T11:11:29.494548+08:00

- Workstream ID: `test-partner-inbox-overview`; 原登记标题时间误填为 11:30，实际登记在本次实施开始前（约 11:07）。本更新按实际时间追加，保留原记录。
- Scope update: 增加 frontend/workbench/docs/ui-guidelines.md，仅同步主从页示例与其引用的现行样式 guard 对公共页面 padding/max-width 的约束；其余登记不变。

## Registration Update - 2026-09-20T11:52:00+08:00

- Workstream ID: `test-workbench-raised-surface-style`; Owner: Codex `/root`。
- Goal: 将 Workbench UI 规范和现有凹陷子块统一调整为凸起或无阴影的表面效果。
- Non-goals: 不改变业务逻辑、接口、权限、主题选择器、数据库、依赖或外部服务；不覆盖其他工作流未提交修改。
- Environment: test；Branch: main；Worktree: /opt/zsjos；Base commit: 050f43dd0dcc996ce468efafa4715870264dafd6；Target branch / Integration order: None。
- Ownership scope: `frontend/workbench/docs/ui-guidelines.md`、Workbench surface tokens/theme mapping、Workbench styles and Bootstrap preset affected by inset shadows；`handoff/test_main.md`（仅追加）。
- Dependencies: 复用现有 Ant Design shadow tokens 和 CSS 变量，不新增依赖；保留其他工作流已修改文件内容。
- Verification plan: Workbench styles guard、theme token tests、TypeScript typecheck/build（如环境允许）、`git diff --check`；无登录会话时记录浏览器视觉验证未执行。

## Delivery Entry - 2026-09-20T11:19:10+08:00

- Workstream ID: `test-workbench-raised-surface-style`; Owner / Branch / Worktree: Codex `/root` / main / `/opt/zsjos`; HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`。
- User goal: 将 Workbench UI 规范和检出的凹陷组件样式改为凸起或无阴影效果，拒绝凹陷显示。
- Key decisions: 用户本轮明确要求优先于既有“凹陷子块”规范；保留 `--crm-bg-sunken` 与 `--crm-shadow-inset` 历史变量名以避免大范围组件改名，但分别映射为容器底色和标准外阴影；Bootstrap 主题直接内阴影改为 Ant Design 外阴影；审批辅助卡文案同步为平面无阴影。
- Result: 24 个复用 `--crm-shadow-inset` 的业务样式位置通过主题映射统一变为微凸；默认 token、玻璃 token bridge、UI guidelines 和受影响主题样式已同步；选中态侧边标记/描边等结构性 inset 保留，不属于凹陷暗阴影。
- Changed files: `frontend/workbench/docs/ui-guidelines.md`; `frontend/workbench/src/styles/tokens.css`; `frontend/workbench/src/components/Theme/themeTokens.ts`; `frontend/workbench/src/components/Theme/themeTokens.presets.test.ts`; `frontend/workbench/src/components/Theme/presets/bootstrapTheme.ts`; `frontend/workbench/src/styles/pages/bpm-approval-center.css`; `handoff/test_main.md`。保留工作树其他任务改动。
- Verification evidence: Workbench `npm run typecheck` 通过；使用独立 `/tmp` Vitest 配置运行 styles guard 与 theme token 测试，3 个测试文件、46 项通过；`git diff --check` 通过。默认 Vitest 配置因 `node_modules/.vite-temp` 权限不足未直接使用，已用独立缓存目录完成等价测试。浏览器视觉验证未执行，环境无 Chromium/Firefox/Playwright。
- Dependency or integration impact: 无新增依赖、数据库或外部服务变更；主题变量兼容既有 CSS 调用，影响全部 Workbench preset 的子块阴影方向。
- Remaining work: 需要在有浏览器会话的环境复核浅色/暗色及玻璃背景下的真实视觉层次；直接写死的 `inset` 侧边标记、选中描边和玻璃高光仍按其非凹陷语义保留。

## Delivery Entry - 2026-09-20T11:12:38.456278+08:00

- Workstream ID: `test-partner-inbox-overview`; Owner / Branch / Worktree: 同登记；HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`。
- User goal: 补齐兼职管理收件箱、表格样式与头像/搜索/收起对齐；点击兼职进入概览，客资明细可查看详情。
- Key decisions: 复用 SubjectAvatar 与 InboxAvatarRail；共享现有服务端分页数据支持收件箱/表格；概览总量取 total，本页指标标注分页口径；客资编号和分类/归属快照沿用已有契约，现有管理权限不变。
- Result: 完成左栏卡片、收起头像栏、搜索和滚动条宽度对齐、桌面/移动响应布局、兼职表格、概览和客资明细表格；详情内保留兼职列表，返回保留分页。列表请求失败时清除失效选择。同步 API 展示说明及公共页面间距规范示例。
- Changed files: `frontend/workbench/src/pages/SubordinatePartnerPage.tsx`、`frontend/workbench/src/styles/pages/subordinate-partner.css`、`frontend/workbench/docs/ui-guidelines.md`、`docs/api/zsjos-subordinate-partners.md`、`handoff/test_main.md`。保留全部既有其他修改。
- Verification evidence: `npm run typecheck` 通过；`npm test -- src/pages/subordinate-partner.guard.test.ts --configLoader runner` 对应 3 项通过；`npm test -- src/styles/styles.guard.test.ts --configLoader runner` 28 项通过；scoped `git diff --check` 通过。样式初检揭示公共页面重复 padding/max-width 与动态滚动条间距校验问题，修正后全部通过。
- Dependency or integration impact: None；无新增依赖、后端/共享接口改动、数据库/共享服务操作、提交或部署。仅工作台展示调整，不影响 Admin 调用契约。
- Remaining work: 环境缺少浏览器可执行文件与 Playwright，真实桌面/移动视觉、搜索/折叠/分页/详情点击以及真实授权 API 联调未验证；现有测试为静态 guard，不替代浏览器验收。构建输入/依赖/路由未变，本次未执行发布构建。运行服务未加载本次改动。

## Registration Correction - 2026-09-20T11:19:59+08:00

- Workstream ID: `test-workbench-raised-surface-style`。前述登记标题 `11:52:00` 为误填，实际登记约在 `11:14`、本轮首次功能文件变更之前；保留原记录，仅追加更正。验证范围包含 `themeTokens.presets.test.ts` 的旧玻璃阴影差异断言更新。无 bundling、依赖、路由、资源或构建配置变更，生产构建不适用；桌面和移动视觉检查仍未完成。

## Delivery Entry - 2026-09-20T11:20:59.649445+08:00

- Workstream ID: `test-partner-inbox-overview`; Owner / Branch / Worktree: 沿用登记（Codex /root，main，/opt/zsjos）；HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`；Environment: test。
- User goal: 调整兼职概览分页统计，并核实表格是否使用通用组件。
- Key decisions: 移除本页客资数和本页已分配销售数；仅显示服务端 total 对应全部客资数，明细继续分页。表格使用 Ant Design Table，未使用 ProTable 或项目二次封装，本轮不扩展表格能力。
- Result: 统计卡改为单列，说明全部客资统计口径；同步接口展示文档；不添加后端不存在的全量指标。
- Changed files: frontend/workbench/src/pages/SubordinatePartnerPage.tsx；frontend/workbench/src/styles/pages/subordinate-partner.css；docs/api/zsjos-subordinate-partners.md；handoff/test_main.md（追加）。保留其他既有改动。
- Verification evidence: npm run typecheck 通过；兼职页面 guard 3 项和 styles guard 28 项全部通过；scoped git diff --check 通过；检查概览不再依赖 leads.length 或本页销售数量。
- Dependency or integration impact: None；无新依赖、接口改动、共享服务操作、提交或部署。
- Remaining work: 环境仍无可用浏览器/Playwright，桌面/移动视觉未验证；未部署，运行服务尚未加载修改。

## Delivery Entry - 2026-09-20T11:23:50.122967+08:00

- Workstream ID: `test-partner-inbox-overview`; Owner / Branch / Worktree: 沿用登记（Codex /root，main，/opt/zsjos）；HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`；Environment: test。
- User goal: 兼职管理使用 ProTable。
- Key decisions: 复用已安装 @ant-design/pro-components；兼职列表与客资明细启用刷新、密度、全屏和独立持久化列设置，保留原服务端搜索和每页 20 条分页；归属历史为无额外工具栏的紧凑 ProTable。
- Result: 三处 Table 已迁移；显式 ProColumns 类型；渲染从类型明确的记录读取状态、时间与空值字段，保留 leadNo 和历史快照语义；同步展示文档。
- Changed files: frontend/workbench/src/pages/SubordinatePartnerPage.tsx；docs/api/zsjos-subordinate-partners.md；handoff/test_main.md（追加）。
- Verification evidence: 最终 npm run typecheck 通过；兼职 guard 3 项通过；最终 Vite production build 通过，产物位于 /tmp/zsjos-partner-protable-build-final-20260920，已有大体积 chunk 警告；scoped git diff --check 通过。初始 Card bodyStyle 类型不兼容，改为当前版本 styles.body 后复验成功。
- Dependency or integration impact: None；无新依赖、接口/权限改动、共享服务操作、提交或部署；不覆盖运行环境产物。
- Remaining work: 无可用浏览器，实际列设置、全屏、密度切换及桌面/移动视觉未验证；运行环境尚未部署此修改。

## Registration Update - 2026-09-20T11:31:03.013494+08:00

- Workstream ID: `test-production-ticket-compile-fix`; Owner: Codex `/root`。
- Goal: 修复 ProductionTicketService 账号快照解析泛型编译错误；Non-goals: 不改变业务契约、不部署或启停服务。
- Environment: test（/etc/zsjos/agent-environment）；Branch: main；Worktree: /opt/zsjos；Base commit: 050f43dd0dcc996ce468efafa4715870264dafd6；Target branch / Integration order: None。
- Ownership scope: 接续已交付多账号工单工作，仅修改 ProductionTicketService.java 的 parseAccountSnapshots 及 import；handoff/test_main.md 仅追加。保留全部既有修改。
- Dependencies: 复用现有 JsonUtils 与 Jackson TypeReference，无新增依赖。
- Verification plan: Maven ZSJOS 模块及依赖 compile，scoped diff 检查。

## Delivery Entry - 2026-09-20T11:31:52.450234+08:00

- Workstream ID: `test-production-ticket-compile-fix`; Owner / Branch / Worktree: 同登记；HEAD unchanged: 050f43dd0dcc996ce468efafa4715870264dafd6。
- User goal: 修复部署时后端编译错误。
- Key decisions: 使用现有 tools.jackson.core.type.TypeReference 保留 List<Map<String, Object>> 完整泛型，移除该方法无效的 SuppressWarnings；空值处理不变。
- Result: 泛型编译错误已修复；保留其他未提交业务改动。
- Changed files: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/production/ProductionTicketService.java；handoff/test_main.md。
- Verification evidence: sudo -n mvn -f backend/pom.xml -pl yudao-module-zsjos -am -DskipTests compile 成功（BUILD SUCCESS，28.520 s）；日志 /tmp/zsjos-ticket-compile-fix.log；scoped git diff --check 通过。因已有 target 为 root 所有，编译使用 sudo。
- Dependency or integration impact: None；无新增依赖、业务契约或数据库变更，无部署或服务启停。
- Remaining work: 尚未重新部署及验证运行时启动；编译验证未运行测试。

## Registration - 2026-09-20 V265 migration recovery

- Workstream ID: test-v265-mysql-recovery; Owner: Codex /root.
- Environment: test (/etc/zsjos/agent-environment); Branch: main; Worktree: /opt/zsjos; Base commit: 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Correct failed V265 SQL and redeploy/restart the runtime explicitly authorized by the user.
- Non-goals: Other business changes, historical migration rewrites, dependency changes.
- Ownership scope: script/sql/mysql/migrations/V265__production_ticket_multi_account.sql; handoff/test_main.md (append only). Preserve existing work.
- Dependencies: Existing MySQL 8.4 runtime and deploy-production.sh with /opt/zsjos-runtime configuration. V265 confirmed unapplied.
- Verification plan: Isolated MySQL execution from prerequisite table structures, repeat execution and version/column inspection; deployment migration, read-only schema verification and backend health.
- Target branch / Integration order: None.

## Registration Update - V265 schema verification recovery

- Workstream: test-v265-mysql-recovery; unchanged owner/context.
- Evidence: corrected V265 applied, but post-migration verification rejected its columns because desired schema omitted them.
- Scope extended to script/sql/mysql/schema/core.sql and matching script/sql/mysql/00-bootstrap-schema.sql; only the two V265 column declarations. Fresh baseline is updated for future databases, not executed against the existing runtime.
- Verification extended to existing controlled test-fresh/test-upgrade commands and runtime schema comparison. Resume already-built release after successful verification.

## Delivery Entry - 2026-09-20T13:27:28.667396+08:00

- Workstream ID: test-v265-mysql-recovery; Owner / Branch / Worktree: Codex /root / main / /opt/zsjos; HEAD unchanged: 050f43dd0dcc996ce468efafa4715870264dafd6; Environment: test.
- User goal: Fix deployment failure and restart the same runtime using /opt/zsjos-runtime configuration.
- Key decisions: Correct previously unapplied V265 with information_schema checks and prepared ALTER statements; retain repeatability. Synchronize two nullable JSON columns in desired schema and fresh baseline after runtime verifier exposed omitted declarations. Existing runtime receives V265 only, never bootstrap. No business data backfill or permission edits. Reuse completed build for installation/start after schema verification.
- Result: Release 2026.09.20-132029-050f43dd installed; zsjos-backend.service active/running; application startup completed in 43.19 seconds; health HTTP 200 and UP. Runtime core V265 registered in both ledgers; both columns JSON nullable. Backup created before migration: migrator /backups/zsjos-20260920-132354.sql.
- Changed files: script/sql/mysql/migrations/V265__production_ticket_multi_account.sql; script/sql/mysql/schema/core.sql; script/sql/mysql/00-bootstrap-schema.sql; handoff/test_main.md. Preserved unrelated existing changes.
- Verification evidence: Isolated mysql:8 executed V265 twice against structures copied from actual V264 tables, confirming both JSON columns and one V265 record per ledger. Static check and git diff --check passed. Backend/Admin/Workbench/H5 release builds passed. Runtime db-verify passed structural drift checks; existing data-grade FAIL warnings remain warn-only. Read-only runtime column/ledger checks agree with isolated result. Logs: /tmp/zsjos-v265-redeploy.log, /tmp/zsjos-v265-verify.log, /tmp/zsjos-v265-start.log.
- Verification limitations: test-fresh and test-upgrade both blocked by pre-existing bootstrap syntax error at zsjos_student_delivery_config (missing semicolon before next CREATE TABLE; reproduced in HEAD). Full fresh/upgrade acceptance is therefore NOT verified; no unrelated repair undertaken. Start wrapper exited on its first failed health probe because existing health() calls exit; independently confirmed later successful startup and HTTP 200/UP.
- Dependency or integration impact: No new dependencies, commits or branches. Authorized existing runtime migration, release installation and backend start completed. Disposable isolated MySQL container removed.
- Remaining work: Independently repair existing fresh-bootstrap syntax error and rerun full initialization tests; investigate existing data-grade warnings separately. No remaining blocker for the requested runtime restart.

## Workstream Registration - 2026-09-20T13:30:48+08:00

- Workstream ID: `test-media-screen-imported-partner-attribution`; Owner: Codex `/root`.
- Goal: Make imported Partner Lead performance visible on the media-screen companion ranking by backfilling the current confirmed Partner-to-employee attribution into missing Lead contribution snapshots.
- Non-goals: Do not alter Lead status, counted time, provider identity, ownership records, orders, role permissions, or existing media-screen daily snapshots; do not deploy, restart, or write the shared test database in this source-change turn.
- Environment: test (`/etc/zsjos/agent-environment`); Branch: main; Worktree: `/opt/zsjos`; Base commit: `050f43dd0dcc996ce468efafa4715870264dafd6`; Target branch / Integration order: None.
- Ownership scope: `script/sql/mysql/migrations/V266__backfill_imported_partner_media_attribution.sql`; `script/sql/mysql/tools/test_imported_partner_media_attribution.py`; `docs/api/media-screen-public-api.md`; `handoff/test_main.md` (append only).
- Dependencies: Existing `zsjos_partner_ownership`, System user/department facts, Lead contribution snapshot contract, MySQL 8; no new dependency. The migration is after existing V265 and only applies rows carrying the `legacy-parttimecrm-%` import marker.
- Verification plan: Run a disposable MySQL replay twice with matching/nonmatching tenant, ownership, user status, department and import-marker fixtures; assert snapshot values, preserved Lead business fields, no broad update, version ledgers and UTF-8 descriptions. Then run migration discovery and scoped diff checks. Shared test execution needs a separately recorded write operation and post-write API/query verification.

## Delivery Entry - 2026-09-20T13:35:44+08:00

- Workstream ID: `test-media-screen-imported-partner-attribution`; Owner / Branch / Worktree: Codex `/root` / `main` / `/opt/zsjos`; HEAD unchanged: `050f43dd0dcc996ce468efafa4715870264dafd6`; Environment: test.
- User goal: Verify imported Partner data eligibility and backfill its historical performance to the current confirmed new-media operators for the media-screen companion ranking.
- Key decisions: V266 is a narrow import exception: it only matches `legacy-parttimecrm-%` Partner Leads with a non-null counted time and both contribution snapshots absent, then joins same-tenant current live Partner ownership, enabled employee and live department. It preserves all Lead business fields and daily screen snapshots. The 55 legacy rows whose current attribution cannot be established remain unmodified.
- Execution result: Shared test database V266 executed once and recorded in both Core version ledgers. It backfilled 461 Leads across 60 Partners to 6 operators in department 1013; 193 are in the current month. The public stats endpoint, after its 15-second natural cache expiry, returns a companion department with 6 members, month total 193, effective 149 and week 71.
- Changed files: `script/sql/mysql/migrations/V266__backfill_imported_partner_media_attribution.sql`; `script/sql/mysql/tools/test_imported_partner_media_attribution.py`; `docs/api/media-screen-public-api.md`; `handoff/test_main.md`.
- Verification evidence: Disposable MySQL replay passed, including tenant isolation, import-marker exclusion, missing ownership, disabled employee, existing snapshots, idempotent replay, UTF-8 version label and business-field preservation. Migration sequence discovery reports continuous V001-V266; scoped `git diff --check` passed. Post-write query reports 461 V266 rows, no dangling employee/department references and no null status/counted time/provider type. Public API verification returned the companion ranking above.
- Backup and recovery: The first pre-write `mysqldump` used a joined subquery and failed its lock-table check; the shell did not stop before running V266. All affected pre-write contribution user/department snapshots were deterministically NULL by the migration predicate. A post-apply full-row backup of exactly 461 V266 rows was then created at `/opt/zsjos-runtime/backups/v266-imported-partner-media-attribution-postapply-20260920-133400.sql` (336,976 bytes); recovery requires a reviewed scoped update that clears the six contribution snapshot fields for these V266 rows, then uses that backup for the post-apply row state. No data outside the 461 matching rows was written.
- Dependency or integration impact: No new dependencies, branches, commits, deployment or service restart. Existing historical daily snapshot pages and yesterday champion intentionally retain their frozen values; realtime, trend and 14-day series use the repaired Lead snapshots.
- Remaining work: Review the 55 unmatched imported rows before assigning an authoritative Partner ownership; do not infer an operator for them. If historical daily snapshot pages also must display the reassigned performance, a separately approved snapshot-reconstruction operation is required.

## Registration Update - 2026-09-20T13:42:12+08:00

- Workstream: test-media-screen-imported-partner-attribution; Owner / branch / worktree / HEAD unchanged; environment test.
- Goal: Show currently assigned enabled Partners with zero metrics under their active in-scope operator in realtime companion details, including operators with no contribution rows.
- Scope: MediaScreenQueryService.java, MediaScreenQueryServiceTest.java, docs/api/media-screen-public-api.md, this handoff. No database, deployment or service changes; preserve other work.
- Dependencies: existing PartnerOwnershipMapper and PartnerMapper, no new libraries. Target branch / integration: None.
- Verification: focused service tests and compilation; inspect existing frontend zero rendering; historical snapshots retain frozen membership.

## Delivery Entry - 2026-09-20T13:44:46+08:00

- Workstream: test-media-screen-imported-partner-attribution; Owner / branch / worktree: Codex /root / main / /opt/zsjos; HEAD unchanged: 050f43dd0dcc996ce468efafa4715870264dafd6; environment test.
- User goal: 数据全为 0 的兼职也显示在对应运营名下兼职明细。
- Decisions/result: Realtime companion details now supplement contribution rows with current ownership for enabled Partners under active in-scope operators, explicitly restricting ownership lookup to the requested tenant and roster. Operators without any contribution rows are included when they own an enabled Partner. Four primitive metric fields default to zero. Existing contribution names/attribution and totals are preserved; duplicate supplementation is prevented. Disabled Partners remain hidden; frozen history unchanged.
- Changed files: MediaScreenQueryService.java; MediaScreenQueryServiceTest.java; docs/api/media-screen-public-api.md; handoff/test_main.md. Preserved existing modifications.
- Verification: sudo -n mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=MediaScreenQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test: BUILD SUCCESS, 7 tests passed; log /tmp/zsjos-media-zero-test.log. Covers zero members/details, disabled filtering, no duplicate counts and reassignment retaining historical contributions. git diff --check passed. Existing adapter.ts and PartTimeDetailsPopover render zero values without filtering.
- Limitations: frontend npm test blocked by installed Node lacking --experimental-strip-types; no browser or deployed API verification of new code. No frontend source or layout changes. Current running backend still uses previous implementation.
- Dependency/integration impact: existing mappers only, no new dependency, database writes, deployment, service restart, commits or branch operations. No API field changes.
- Remaining work: Deploy backend through an authorized release and verify realtime details after cache expiry. Historical daily snapshots intentionally retain their frozen lists.

## Workstream Registration - 2026-09-20 (deployment frontend artifacts)

- Workstream ID: test-deploy-embed-media-build; Owner: Codex `/root`.
- Goal: Build and install fresh admin-embed and media-screen artifacts with the existing release script.
- Non-goals: No deployment, database operations, service restart, dependency additions or unrelated edits.
- Environment: test (`/etc/zsjos/agent-environment`); Branch: main; Worktree: `/opt/zsjos`; Base commit: `050f43dd0dcc996ce468efafa4715870264dafd6`; Target branch / Integration order: None.
- Ownership scope: `script/shell/deploy-production.sh`; `docs/operations/production-deployment.md`; `docs/operations/media-screen-deployment.md`; `handoff/test_main.md` (append only).
- Dependencies: Existing pnpm Admin and npm media-screen toolchains; existing release layout. Preserve all pre-existing changes.
- Verification plan: Bash syntax, isolated stub build/install/failure checks, actual frontend production builds in temporary output directories, asset path/PMS registration checks and scoped diff checks. No active release changes.

## Delivery Entry - 2026-09-20T13:59:22+08:00

- Workstream ID: test-deploy-embed-media-build; Owner: Codex `/root`; Environment: test.
- Branch / Worktree / HEAD: main / `/opt/zsjos` / `050f43dd0dcc996ce468efafa4715870264dafd6` (unchanged).
- User goal: Include admin-embed and media-screen in the startup/release script's build flow.
- Key decisions: Keep start/restart as backend lifecycle commands; build/deploy now produce five frontend artifacts. Admin dependency installation is shared; separate production builds use dist-prod and dist-embed with an explicit /admin-embed/ base. Media-screen uses existing npm lockfile and explicit deployment tenant/API environment with mock disabled. Install copies both fresh artifacts instead of carrying over old releases.
- Execution result: Updated script, help and directly affected deployment documentation. Reported and corrected the documented deployment order to match current script behavior; database and service lifecycle implementation unchanged.
- Changed files: `script/shell/deploy-production.sh`; `docs/operations/production-deployment.md`; `docs/operations/media-screen-deployment.md`; `handoff/test_main.md` (append only). Preserved unrelated existing modifications.
- Verification evidence: Bash syntax and scoped git diff --check passed. Temporary stub harness `/tmp/zsjos-embed-media-check/verify.py` passed five-artifact build/install, explicit embed base, single Admin install, build failure abort and missing-artifact no-switch cases. Actual Admin embed production build passed (65 seconds), media-screen npm production build including TypeScript checks passed. Outputs isolated under `/tmp/zsjos-embed-media-check`; HTML local asset references resolved for both artifacts; embedded registry contains 77 PMS Vue components including project list and workbench. Logs: admin.log and media.log in that directory.
- Dependency / integration impact: No dependency additions, commits, branches, database writes, deployments or service restarts. Media build requires deployment VITE_MEDIA_SCREEN_TENANT_ID (already configured in this environment).
- Remaining work / limitations: Active release remains unchanged. No live browser verification or complete backend/five-frontend deploy was performed; deployment and post-release PMS/media-screen validation remain separate operations. Existing Vite NODE_ENV configuration warning did not block build.

## Registration Update - 2026-09-20 (release retention)

- Workstream: test-deploy-embed-media-build; owner Codex `/root`; environment test; branch/main, worktree `/opt/zsjos`, HEAD unchanged from registration.
- Goal: After successful deploy health verification retain current and previous-release, remove other recognized release directories; fail closed on invalid protection paths. User confirmed this script change; no live cleanup/deployment this turn.
- Scope addition: `script/shell/tests/test_release_retention.py`; existing script, production deployment documentation and append-only handoff remain owned here.
- Verification: Isolated temporary release trees and stub deploy functions cover retention, path protection, failed/slow startup, failure before health, and non-deploy commands; Bash syntax and scoped diffs. No new dependencies.
- Non-goals/dependencies/integration: No database, service, shared release or branch changes; existing Bash/coreutils/Python only; integration None.

## Delivery Entry - 2026-09-20T14:09:49+08:00

- Workstream: test-deploy-embed-media-build; Owner: Codex `/root`; Environment: test.
- Branch / Worktree / HEAD: main / `/opt/zsjos` / `050f43dd0dcc996ce468efafa4715870264dafd6` (unchanged).
- User goal: Retain current and previous release after successful startup, automatically clean older versions; user confirmed script modification.
- Decisions/result: Deploy now runs cleanup only after HTTP 200 health verification. Fixed health probe failure to return rather than exit so existing retries work. Retention follows current symlink and previous-release path file, never mtime. Validate both references, expected current version, direct-child boundaries and retained artifact structure before deleting recognized historical release directories. Preserve unrecognized directories, symlinks, standalone checksums and configured source/log/backup paths; reject mount-point candidates and avoid crossing filesystems. Missing references (including first deployment) fail closed. start/restart/rollback do not clean. Cleanup failure returns nonzero without stopping the healthy process.
- Changed files: `script/shell/deploy-production.sh`; `script/shell/tests/test_release_retention.py`; `docs/operations/production-deployment.md`; `handoff/test_main.md` (append only). Earlier frontend-build changes and unrelated work preserved.
- Verification: `python3 -B -m unittest discover -s script/shell/tests -v` passed all 9 tests with temporary fixtures, including actual fixture deletion, idempotence, reference-based retention, symlink/path/mount protection, incomplete/missing retention targets, health retry then success, upstream/health failures, cleanup failure propagation and start/restart exclusion. Bash syntax and scoped diff checks passed. No live deployment, service operation, database change or shared-release deletion occurred.
- Dependency/integration impact: Existing Bash/coreutils/find/mountpoint runtime; added tool preflight checks, no package dependencies or branch/commit operations. Integration None.
- Remaining work/limitations: New cleanup will run on the next authorized successful deploy. Live service startup and cleanup intentionally unexecuted; isolated tests verify behavior. Deleted older releases have no automatic backup; retain required archives beforehand. Independent logs/backups and unrecognized release-root entries are intentionally outside deletion scope.

## Workstream Registration - 2026-09-20T14:16:24+08:00

- Workstream ID: test-account-kz-cleanup; Owner: Codex /root; environment: test from /etc/zsjos/agent-environment.
- Goal: Physically remove 13 confirmed test ADMIN accounts and their KZ Lead chains; user additionally confirmed two no-Lead test orders and two finance-test feedback records.
- Non-goals: No production access, schema/migration changes, unrelated account/business deletion, role-menu changes, attachment blob deletion, service restart, branch/commit/deployment operations.
- Branch / worktree / base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6. Target branch / integration order: None.
- Ownership scope: handoff/test_main.md append only; scoped tenant-1 runtime database cleanup and private recovery artifacts under /opt/zsjos-runtime/backups/test-account-cleanup-20260920.
- Dependencies: existing Docker MySQL/Redis tools and Python standard library; preserve all unrelated worktree changes.
- Verification plan: full pre-change backup, exact row manifests, isolated scoped replay/rollback/idempotence, transaction assertions, retained-row checksums, account/Lead/descendant zero-count checks and targeted cache/session inspection. Shared-test deletion is explicitly authorized by the current user request and follow-up confirmation.

## Delivery Entry - 2026-09-20T14:22:13+08:00

- Workstream: test-account-kz-cleanup; owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged: 050f43dd0dcc996ce468efafa4715870264dafd6.
- User goal: Physically remove the confirmed test accounts and associated KZ Lead records; follow-up explicitly authorized the two no-Lead test orders and finance feedback.
- Decisions/result: Removed 13 ADMIN test identities, 46 related KZ Leads, 15 orders, 2 Partner identities with 2 Partner login accounts, 2 feedbacks and their 2 work orders, plus scoped child data/settings: 1,276 rows in 59 tables, tenant 1 only. Explicit typed relationships determine scope; unrelated KZ Leads are preserved. No role-menu assignments, schema, audit logs or physical uploaded files changed.
- Changed repository files: handoff/test_main.md (append only). Runtime artifacts: private backup directory /opt/zsjos-runtime/backups/test-account-cleanup-20260920 containing full backup, scoped row restore SQL, exact manifest, executed SQL, rehearsal SQL and verification.
- Verification: Full pre-change mysqldump succeeded (536,260,094 bytes); scoped restore dump succeeded (622,051 bytes). Temporary full-table rehearsal passed exact deletion, second-run no-op, retained-row count/CRC sum/CRC XOR comparison and rollback restoration. Live transaction passed the same expected-count and retained-row guards; postcommit all 59 target table scopes are zero. Named accounts remaining: 0. Tenant Leads remaining: 4,537, including 6 unrelated KZ Leads and all 4,528 live Leads. Target process references have zero runtime/history instances; no target OAuth DB rows or cached access tokens/role entries were found.
- Dependency/integration impact: None; existing MySQL/Python/Redis tooling only, no service operation, branch/commit/push/deployment. Source bootstrap/migrations do not seed these runtime identities; no migration rewrite needed.
- Recovery/limitations: Restore only scoped rows after collision/dependency review; full backup is not authorization to overwrite the active database. Historical audit operator references and file metadata intentionally remain. Existing orphan registration 9 references already absent order 19 and has no traceable KZ chain; preserved instead of expanding deletion. No browser verification; database mutation and cached identity inspection are the applicable checks.
- Remaining work: None within confirmed exact deletion scope; any broader orphan/audit/file cleanup needs its own identified scope.

## Registration Update - 2026-09-20T14:39:57+08:00

- Workstream: test-account-kz-cleanup; owner Codex /root; environment test; main / /opt/zsjos / HEAD 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- Scope extension: user confirmed nine additional named test identities and all their associated dirty business records, including four completed content acceptance tasks. Preserve unrelated accounts/data and existing worktree changes.
- Ownership: handoff/test_main.md append only; exact tenant-1 database row cleanup and private artifacts /opt/zsjos-runtime/backups/test-account-cleanup2-20260920.
- Verification: refresh association audit, full/scoped backups, temporary-table deletion/repeat/rollback rehearsal, live transaction retained-row guards, postcommit zero target scopes and scoped session checks. No schema, role-menu, service, file-blob, dependency, branch or deployment operations. Integration None.

## Workstream Registration - 2026-09-20 (operation log names)

- ID: test-operation-log-names; Owner: Codex `/root`; Environment: test (environment file).
- Goal: Populate operation log userName through existing System user service for page/detail/export. Non-goals: deployment, database changes, authentication or permission changes.
- Branch: main; Worktree: `/opt/zsjos`; Base: `050f43dd0dcc996ce468efafa4715870264dafd6`; Target branch / integration order: None.
- Ownership: System OperateLogController.java, OperateLogRespVO.java, controller/admin/logger/OperateLogControllerTest.java; docs/api/system-operation-log.md; handoff/test_main.md append only. Preserve unrelated changes.
- Dependencies: existing System user service and test facilities; no new dependencies.
- Verification: focused controller tests for list/detail/export, missing users and typed identities; module compilation, frontend consumer contract inspection, scoped diff checks.

## Delivery Entry - 2026-09-20T14:43:42+08:00

- Workstream: test-operation-log-names; Owner: Codex `/root`; Environment: test.
- Branch / worktree / HEAD: main / `/opt/zsjos` / `050f43dd0dcc996ce468efafa4715870264dafd6` (unchanged).
- User goal: Show operation log operator names.
- Decisions/result: System page, detail and export explicitly populate userName from existing AdminUserService in one batch. Removed Easy-Trans dependency from this response path; only ADMIN identities resolve against System users, missing/deleted/unknown identities remain unset. Current nickname, not historical snapshot.
- Changed files: System controller/admin/logger/OperateLogController.java and vo/operatelog/OperateLogRespVO.java; controller/admin/logger/OperateLogControllerTest.java; docs/api/system-operation-log.md; handoff/test_main.md.
- Verification: Focused Maven reactor test passed in writable temporary backend copy `/tmp/zsjos-operation-log-d77o_opk/backend` (4 tests, zero failures/errors), including repeated/missing IDs, MEMBER/PARTNER/unknown type separation, null IDs, empty page, absent detail, detail name and exported names. Module and dependency compilation passed. Original attempt was blocked by root-owned target metadata; isolated copy avoided permission changes. Scoped diff check passed. Vue list/detail consume existing userName; no independent System operation log consumer found in Workbench.
- Dependencies/integration: None; no dependencies added, permissions or tenant interception changed, database writes, commits or deployment.
- Remaining work: Deployment and real HTTP/browser verification of the running application; tests verify controller contracts with mocked services, not live data.

## Delivery Entry - 2026-09-20T14:43:49+08:00

- Workstream: test-account-kz-cleanup; owner/environment: Codex /root / test; branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Remove the nine additionally named test identities and all their associated dirty business data; user explicitly confirmed the non-KZ content tasks and associated business scope.
- Decisions/result: Physically removed 109 exact rows across 20 tenant-1 tables: 9 ADMIN accounts, 2 Partner profiles with 2 Partner accounts, 6 related KZ Leads, 6 exclusive persons/contact claims, 2 feedbacks and 2 work orders, 4 content tasks, 37 business events and scoped invitation/ownership/history child rows. Four content source records were already absent. Shared dictionaries/templates/configuration and other users were not deleted merely because a test user created/updated them. Audit logs and uploaded file objects preserved.
- Changed repository files: handoff/test_main.md append only; private runtime artifacts at /opt/zsjos-runtime/backups/test-account-cleanup2-20260920.
- Verification: Full pre-change backup succeeded (536,983,575 bytes), scoped row restore backup succeeded (69,508 bytes). Rehearsal on temporary full-table copies passed exact deletion, repeat no-op, rollback restoration and retained-row checks. Live transaction passed expected counts and retained count/CRC sum/CRC XOR assertions; postcommit all 20 target scopes zero, named accounts zero. Tenant Leads now 4,531 total, 4,528 live and zero KZ; live Lead count unchanged. No target OAuth DB/cache entries, runtime assignees, department leaders, BPM group/manager/starter configuration references or referenced process instances. Five enabled super-admin memberships outside target scope existed at preflight.
- Dependency/integration impact: None; no schema/migration, role-menu, service, deployment, commit or branch operation. Existing unrelated repository edits preserved.
- Recovery: before.sql plus manifest.json, restore-deleted-rows.sql, executed deletion SQL, rehearsal SQL and verification.txt are private recovery evidence. Restore scoped rows only after collision and subsequent-write review; do not overwrite the shared database wholesale.
- Remaining work/limitations: None in confirmed scope. Historical audit and shared configuration creator/updater references intentionally remain; physical files are retained. No browser check applicable to the direct data-only operation.

## Workstream Registration - 2026-09-20T14:54:10+08:00

- Workstream ID: test-link-input-unification; Owner: Codex /root; environment: test.
- Goal: Scan frontend link-entry forms and use a reusable link input with existing ResourceLink presentation.
- Non-goals: No API/schema/permission changes, new dependencies, deployment or service changes.
- Branch/worktree/base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6. Target branch/integration order: None.
- Ownership scope: frontend/workbench/src/components/ResourceLinkInput.tsx and tests; AccountProfileTextInput.tsx; AccountProfilePanel.tsx; ViralAccountMaterialForm.tsx; ContentApprovalDraft.tsx; pages/ContentProductionPage.tsx; pages/ContentReviewBatchPage.tsx; docs/ui-guidelines.md; handoff/test_main.md append only. Other frontend scan is read-only pending scope clarification.
- Dependencies: Existing Ant Design/ResourceLink/Vitest only; preserve existing user changes.
- Verification plan: focused component and form tests, TypeScript check, browser interaction at desktop/mobile where tooling available, scoped diff and remaining-field audit.

### Registration scope update — test-link-input-unification

- Scope: Include frontend/admin/src/components/ResourceLinkInput/index.vue and README.md; existing AppLinkInput/index.vue; HTTP request setting components and Vue views with manually entered HTTP/link fields (IM, MP, MES, AI, Pay, Mall, SMS, ZSJOS material, IoT HTTP). Preserve specialized URI lists, connection strings and uploads. H5/media-screen scan only: no manual link fields found.
- Verification: Vue SFC compilation and scoped lint, available typecheck, same link safety/forwarded control behavior. No dependency or contract changes; separate framework components avoid a new shared package/build integration.

## Delivery Entry - 2026-09-20T15:35:02+08:00

- Workstream: test-link-input-unification; Owner: Codex /root; environment test; branch/worktree: main / /opt/zsjos; HEAD: 050f43dd0dcc996ce468efafa4715870264dafd6 (unchanged).
- User goal: Scan all link-entry forms and replace with common link components; follow-up explicitly excludes Maven verification, which was not executed.
- Decisions/result: React ResourceLinkInput preserves input/Form props, refs, raw values and business validation while reusing ResourceLink preview/copy. Replaced 10 fixed content/draft/review/publication input sites plus server-typed account URL and material https-link inputs including repeat groups. Vue ResourceLinkInput is used at 21 sites across 17 callers, including existing AppLinkInput, which retains its selector, plus material dynamic fields. Vue forwards model/attributes/events/slots and safely previews HTTP(S); IoT split protocol retains original model and passes composed previewValue. H5/media-screen scan found no manual webpage-link entry. Technical connection strings, OAuth multi-value URI selectors, upload controls and generated read-only links keep their specialized controls. No business rules, APIs or persisted data changed.
- Changed files: `frontend/admin/src/components/AppLinkInput/index.vue`, `frontend/admin/src/components/SimpleProcessDesignerV2/src/nodes-config/components/HttpRequestSetting.vue`, `frontend/admin/src/components/bpmnProcessDesigner/package/penal/task/task-components/ServiceTask.vue`, `frontend/admin/src/views/ai/model/apiKey/ApiKeyForm.vue`, `frontend/admin/src/views/im/manager/channel/material/ChannelMaterialForm.vue`, `frontend/admin/src/views/iot/rule/data/sink/config/HttpConfigForm.vue`, `frontend/admin/src/views/mall/promotion/banner/BannerForm.vue`, `frontend/admin/src/views/mes/dv/repair/RepairLineList.vue`, `frontend/admin/src/views/mes/pro/process/ProProcessContentList.vue`, `frontend/admin/src/views/mes/qc/template/TemplateIndicatorList.vue`, `frontend/admin/src/views/mp/components/wx-reply/components/TabMusic.vue`, `frontend/admin/src/views/mp/draft/components/NewsForm.vue`, `frontend/admin/src/views/mp/menu/components/MenuEditor.vue`, `frontend/admin/src/views/mp/messageTemplate/MessageTemplateSendForm.vue`, `frontend/admin/src/views/pay/app/components/AppForm.vue`, `frontend/admin/src/views/system/sms/channel/SmsChannelForm.vue`, `frontend/admin/src/views/zsjos/material/components/MaterialValueField.vue`, `frontend/admin/src/components/ResourceLinkInput/index.vue`, `frontend/workbench/src/components/ResourceLinkInput.tsx`, `frontend/workbench/src/components/ResourceLinkInput.test.tsx`, `frontend/workbench/src/components/AccountProfilePanel.tsx`, `frontend/workbench/src/components/AccountProfileTextInput.tsx`, `frontend/workbench/src/components/ContentApprovalDraft.tsx`, `frontend/workbench/src/components/ViralAccountMaterialForm.tsx`, `frontend/workbench/src/pages/ContentProductionPage.tsx`, `frontend/workbench/src/pages/ContentReviewBatchPage.tsx`, `frontend/workbench/docs/ui-guidelines.md`, `frontend/admin/src/components/ResourceLinkInput/README.md`, `handoff/test_main.md`. Existing unrelated changes, including UI guidelines edits, were preserved.
- Verification: Workbench npm run typecheck passed including final new tests; npm test -- ResourceLink ViralAccountMaterialForm mediaAccountProfile --configLoader runner --cache=false passed 4 files / 39 tests. Initial default test startup hit EACCES on existing node_modules/.vite-temp; runner/no-cache succeeded without changing permissions. All 18 affected Vue scripts/templates compile via existing vue/compiler-sfc; scoped pnpm exec eslint --no-cache passes after fixing the new slot closing tag. Vue SSR checks pass safe/unsafe previews, disabled/maxLength and protocol-prefix slot; an input-boundary stub verifies exact raw model, id and update-event forwarding. Element Plus SSR omits native value/id until client setup, so SSR alone does not verify live input behavior. Scoped/full diff whitespace checks passed.
- Dependency/integration impact: None; existing frontend libraries only, separate React/Vue adapters and no new package/build dependency; no service operation, database change, branch change, commit, push or deployment.
- Remaining verification/limitations: Real browser desktop/mobile form editing, clearing/reset, immediate save, focus and preview/copy interactions remain unverified because no browser executable/automation tooling was available. Full Admin pnpm ts:check started but its execution session was interrupted before a result; do not treat targeted compile/lint as full Admin typechecking. No production build required for this source-only form change (no asset/route/dependency/build wiring changed). Maven explicitly not run.

## Workstream Registration - 2026-09-20T15:35:55+08:00

- ID: test-business-table-unification; Owner: Codex /root; Environment: test.
- Goal: Extract Lead Management table into BusinessTable and migrate all Workbench tabular views, including inbox table mode, selectors, detail and editable tables.
- Non-goals: Backend/API/database/permission changes, dependencies, service/deployment operations, branches/commits. Do not execute Maven; user handles Maven manually. Vue Admin expansion pending user scope response.
- Branch/worktree/base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6; target branch/integration order: None.
- Ownership: frontend/workbench/src/components/BusinessTable/**; affected table consumers in src/pages and src/components; src/styles/components/business-table.css, affected table styles and index.css; affected tests and browser fixture; frontend/workbench/AGENTS.md and docs/ui-guidelines.md, docs/business-table.md; handoff/test_main.md append only. Preserve pre-existing edits, especially SubordinatePartnerPage and ui-guidelines.
- Dependencies: existing React/Ant Design/Pro Components/Vitest only.
- Verification: focused logic and contract tests, table-entry guard, TypeScript, production build, real-browser desktop/mobile interaction and theme checks where available; no mvn commands.

### Scope confirmation — test-business-table-unification

- User explicitly limits this task to frontend/workbench; Vue Admin and embedded Admin pages are deferred. No Maven commands. Other registered scope and verification unchanged.

## Workstream Registration - 2026-09-20T15:42:22.694012+08:00

- ID: test-user-relation-multi-post; Owner: Codex /root; Environment: test.
- Goal: Support multiple configured source/target posts for user relation scenes, with union eligibility and legacy single-post compatibility.
- Non-goals: Work-order scenes, permission grants, relation reassignment, shared database writes or deployment. Permission-mode extension pending user clarification.
- Branch/worktree/base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6; target branch/integration: None.
- Ownership: userrelation scene VO/DO/service and tests; LeadAssignmentServiceImpl and focused tests; Admin userRelation API/form/list; Workbench managementApi and UserRelationPage section of ManagementPages (preserve existing BusinessTable changes); new V267 migration; docs/api/user-relation-multi-post.md; handoff/test_main.md append only. Current turn is serialized; no parallel file editing delegated.
- Dependencies: existing System Post API, persistence and frontend libraries; no new dependencies.
- Verification: focused backend tests/compile, both frontend static checks, browser if available, controlled SQL verification if available; shared test database application requires explicit authorization.

### Registration scope update — test-user-relation-multi-post

- Include Workbench services/managementApi.test.ts for shared array request/response contract checks; other scope unchanged.

## Delivery Entry - 2026-09-20T16:22:21+08:00

- Workstream: test-user-relation-multi-post; Owner: Codex /root; Environment: test.
- Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Allow multiple posts when configuring user relation scenes; continuation resumes the same scope.
- Decisions/result: Both Vue Admin and React Workbench select source/target post arrays from System Post API. Backend validates every code, deduplicates selected posts and candidate users, and keeps source department scope and enabled target requirements. Null arrays read legacy single-post values; explicit empty arrays never revive old eligibility. Existing permission-mode and Partner-scene rules retained; permission-mode extension was not confirmed. Existing unrelated BusinessTable changes preserved.
- Changed files: backend userrelation scene SaveReqVO/RespVO, UserRelationSceneDO, UserRelationSceneServiceImpl, LeadAssignmentServiceImpl, UserRelationSceneServiceImplTest and LeadAssignmentServiceImplTest; frontend/admin src/api/zsjos/userRelation/index.ts and views/zsjos/userRelation/SceneForm.vue/index.vue; frontend/workbench src/services/managementApi.ts and managementApi.test.ts, src/pages/ManagementPages.tsx (UserRelationPage only); script/sql/mysql/migrations/V267__user_relation_multi_post.sql; docs/api/user-relation-multi-post.md; handoff/test_main.md.
- Verification: Final isolated Maven reactor compile/test passed 19 tests (9 relation eligibility/scope, 10 scene validation/response), zero failures/errors; /tmp/zsjos-relation-final-maven.log and /tmp/zsjos-relation-final-k7ne7czi/backend. Original repository Maven attempt hit existing root-owned target permissions; temporary copy avoided permission changes. Initial 17-test run passed; interruption removed temporary artifacts, so final two added tests were rerun with all 19 passing. Workbench typecheck passed before interruption; new array API contract test and 4 management-access tests passed. Existing log paging test fails because it expects sceneCode while unchanged API sends scene; left unchanged. Scoped Admin ESLint and both changed Vue SFC script/template compilation passed. Scoped diff checks passed.
- SQL verification: Before interruption, copied only three table schemas read-only from test database zsjos (V265/V266 present, V267 absent, 8 scene rows). On a dedicated no-network MySQL 8.4 container, applied V267 twice to this prerequisite plus isolated synthetic fixture. Verified both nullable JSON columns, one version record in each table, unchanged legacy fields and Chinese HEX. Shared database never written; test container and prior /tmp evidence no longer present after environment interruption. Results were observed in this conversation; no full fresh-chain/release verification claimed.
- Dependencies/integration: No new libraries, grants, production changes, commits or deployment. Apply additive V267 before deploying backend and both frontend consumers together; old clients see only first compatibility post and must not edit multi-post configurations. Migration does not modify existing scene rows or relations.
- Remaining work: Obtain explicit authorization to apply V267 to shared test zsjos database (zsjos_user_relation_scene plus version metadata), then deployment authorization separately. Full Admin typecheck was interrupted without a result and remains unverified. Real browser interaction and live API/tenant tests remain unverified because no browser executable was available and new code/schema were not deployed. These are not covered by SFC compile or mocked service tests.

## Delivery Entry - 2026-09-20T16:32:17+08:00

- Workstream: test-business-table-unification; Owner/environment: Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Extract Lead Management table design, migrate all Workbench tables including existing table modes. Explicitly exclude Vue Admin/embedded Admin; do not execute Maven.
- Decisions/result: BusinessTable wraps existing ProTable with full/compact modes, filters/actions/batch slots, column resizing and preference compatibility, common pagination/state handling and theme-driven styles. Migrated 46 tables across 24 consumer files, including raw positioning interview editor. Preserved APIs, permissions and business renderers; native-column adapter retains raw-value semantics. Removed duplicated Lead/Student resize implementations; fixed-size Student pagination no longer offers an ineffective page-size control. Updated Workbench rules from absent HrmProTable to the approved universal entry.
- Changed files: frontend/workbench/src/components/BusinessTable/ (implementation and two tests); 18 table page files (AnnouncementCenterPage, BpmApprovalCenterPage, ConfigurationPages, EamAssetPage, ExportTaskPage, LeadAppealPage, LeadAssignmentPage, LeadComplaintPage, LeadDuplicateReviewPage, LeadManagementPage, ManagementPages, MaterialApprovalPage, MessageInboxPage, MySalesOrderPage, RegistrationPages, SalesOrderApprovalPage, SubordinatePartnerPage, SubordinateSalesPage); six consumers (MaterialSelectorModal, PositioningCardMaterialPicker, PositioningInterviewDialog, SalesOrderDetailCards, SalesOrderSupervisorInbox, StudentPartnerBindingDialog); five affected page guard tests; styles/components/business-table.css, business-inbox.css, styles/index.css, pages/lead-management.css and media-students.css; workbench AGENTS.md, docs/ui-guidelines.md and docs/business-table.md; test/business-table.html, business-table.tsx, business-table-browser.mjs; this handoff append. Existing edits in shared files preserved.
- Verification: TypeScript passed. Production Vite build passed on final source (17.90s; existing large-chunk warning); outputs only /tmp/zsjos-table-verified-build. Focused suite 56/56 passed; after final CSS changes component/entry/style suite 35/35 passed; final native-value correction component suite 6/6 passed. Full suite: 733 passed with unrelated managementApi sceneCode-versus-scene failure and entry-scan default-timeout under host load; entry timeout adjusted and focused rerun passed. git diff --check passed. No Maven executed.
- Browser: temporary Chromium 131 CDP script passed resize/persistence/sort isolation, cross-page selection/pagination, retry/denial, column hide, fullscreen, dark/large theme, 1280px desktop/390px mobile body/table bounds, and isolated real Lead/Student page components. Screenshots inspected; browser produced no page exceptions. Browser font environment lacks Chinese glyphs, so full Chinese typography remains unverified. Real authenticated business operations and exhaustive per-page browser walkthrough not performed; fixtures forbid business writes.
- Environment handling: existing node_modules/.vite-temp is not writable; used /tmp config/cache/build paths. Temporary browser binary downloaded into /tmp without package/dependency changes. User relation workstream's later changes were preserved and inspected, not overwritten.
- Dependencies/integration: None; no backend/database/service deployment, dependency, branch, commit or publication operations. Vue Admin deferred by user.
- Remaining work: Manual normal-font visual acceptance and deployed authenticated smoke test; existing management API test mismatch belongs to its own workstream. Maven left entirely to user, not applicable to this frontend-only change.

## Registration scope update — test-user-relation-multi-post — 2026-09-20T16:43:28+08:00

- Owner/environment/branch/worktree/HEAD: Codex /root / test / main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- Goal: Resolve reported V267 unexpected-column drift by synchronizing the desired and fresh schema with the existing additive migration.
- Ownership added: script/sql/mysql/schema/core.sql; script/sql/mysql/00-bootstrap-schema.sql; docs/api/user-relation-multi-post.md; handoff/test_main.md append only. Preserve existing production-ticket schema edits.
- Non-goals: Shared database writes, migration/checksum rewrites, permission changes, deployment, commits or unrelated changes. Dependencies/integration: None.
- Verification plan: static migration checks; controlled fresh initialization and scoped V266-to-V267 upgrade/repeatability; read-only test database schema comparison and migration plan. Existing registration metadata otherwise unchanged.


## Delivery Entry - 2026-09-20T16:45:20+08:00

- Workstream: test-user-relation-multi-post; owner/environment: Codex /root / test; branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Resolve migration BLOCKED status reporting unexpected `source_post_codes` and `target_post_codes` columns.
- Decisions/result: Synchronized both additive V267 JSON columns into desired `schema/core.sql` and fresh `00-bootstrap-schema.sql`; V267 migration and version ordering remain unchanged. Updated multi-post API documentation to describe baseline/drift alignment and preserve rollback constraints. Existing unrelated edits preserved.
- Changed files: `script/sql/mysql/schema/core.sql`, `script/sql/mysql/00-bootstrap-schema.sql`, `docs/api/user-relation-multi-post.md`, `handoff/test_main.md`.
- Verification: `zsjos_db.py check` passed static manifest/order/schema checks. Read-only live inspection confirmed core V267, zero pending migrations, both columns JSON nullable with expected UTF-8 comments and no scene-column drift. Controlled MySQL test created both fresh scene definitions, replayed V267 twice from a V266-shaped table, verified version rows, legacy data preservation and Chinese HEX; all passed. `git diff --check` passed. Full fresh test remains unverified because existing bootstrap SQL has a missing semicolon before `zsjos_student_delivery_defer`.
- Dependency/integration impact: None; no database writes, permission changes, service/deployment, dependency, branch, commit or publication operation. Live overall drift remains blocked by pre-existing unexpected backup table `zsjos_tmp_owner_backup_20260920`; this task did not delete it.
- Remaining work: Remove or formally allow the unrelated backup table through its own approved scope, and repair/verify the pre-existing bootstrap semicolon separately before claiming full migration readiness.

## Registration — test-positioning-template-preview — 2026-09-20T18:19:00+08:00

- Owner: Codex /root; environment: test; branch: main; worktree: /opt/zsjos; base/HEAD: 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Align Admin positioning template preview with Workbench four-column editing presentation; identify draft/published/history preview explicitly.
- Non-goals: Template data/version publication, route/API/permission changes, Workbench changes, deployment or shared service operations.
- Ownership: frontend/admin/src/views/zsjos/directorTemplate/**; docs/api/registration-fulfillment-api.md (preview documentation only); handoff/test_main.md (append only). Existing unrelated changes preserved.
- Dependencies: Existing Vue/Element Plus tooling only. Target branch/integration order: None.
- Verification plan: Scoped lint/SFC checks, production build into temporary output, desktop/mobile browser preview checks where environment supports them; scoped diff check.

## Delivery Entry — 2026-09-20T18:28:30+08:00

- Workstream: test-positioning-template-preview; owner/environment: Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Update the Admin positioning template page after diagnosis found its generic preview had not followed Workbench layout changes.
- Key decisions/result: Added a Vue-owned four-column read-only preview (item, guidance, planned delivery, reference materials), responsive stacked mobile mode, enabled/sorted fields and referenceFor grouping. Orphan references remain visible during configuration. Controls reflect field categories without inventing business/dictionary options. Preview spans the designer width. Explicit draft/published/history preview identity and published version guidance apply only to positioning; route/API/data/publication semantics unchanged.
- Changed files: frontend/admin/src/views/zsjos/directorTemplate/index.vue; new PositioningTemplatePreview.vue in the same directory; docs/api/registration-fulfillment-api.md; handoff/test_main.md append only.
- Verification: Scoped ESLint, Stylelint, Prettier and git diff checks passed. Final production Vite build passed (1m17s), output /tmp/zsjos-positioning-preview-final, log /tmp/zsjos-positioning-preview-final.log. Full vue-tsc with prescribed 8GB memory completed with 24 errors in unrelated files (missing ElMessageBox declarations and unused variables); neither changed Vue file reported errors. Initial default-memory typecheck exhausted heap, then reran with 8GB.
- Browser evidence: Chromium fixture mounts the actual Admin page and preview with isolated template API responses; verified four desktop columns, enabled/reference/orphan rendering, draft/history/current switching, mobile preview and 390px no-overflow, with no runtime exceptions. Desktop 1440px and mobile 390px screenshots inspected at /tmp/zsjos-positioning-desktop.png and /tmp/zsjos-positioning-mobile.png. Fixture-only Chinese font loaded for visual checks. Test harness /tmp/zsjos-positioning-fixture/browser.mjs; no real business writes. Temporary tooling did not change dependencies.
- Dependencies/integration impact: None; no backend, SQL, permissions, commits, deployment or shared service changes. Existing unrelated work preserved.
- Remaining work: Deployment to testos.zhongshijian.top Admin frontend requires explicit publication authorization under AGENTS.md section 4. Authenticated deployed smoke check remains pending that deployment; full Admin typecheck remains failing on unrelated files.

## Registration — test-student-list-scroll — 2026-09-20

- Owner: Codex /root; environment: test; branch: main; worktree: /opt/zsjos; base: 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: My Students continuous list loading and Lead Management card styling with identity/contact and authoritative coaching status.
- Non-goals: Detail layout, course display, database/service/deployment operations and unrelated changes.
- Ownership: frontend/workbench/src/pages/MediaStudentsPage.tsx; frontend/workbench/src/styles/pages/media-students.css; frontend/workbench/src/pages/media-students*.test.*; frontend/workbench/docs/ui-guidelines.md; handoff/test_main.md append only. Existing changes preserved.
- Dependencies: Existing frontend APIs; account-level status aggregation decision pending. Target branch/integration order: None.
- Verification: Focused tests, TypeScript and desktop/mobile browser fixture; status wiring scope to be registered after authoritative source review.

## Registration — test-approved-single-lead-cleanup — 2026-09-20T18:35:37+08:00

- Owner/environment: Codex /root / test; branch/worktree/base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Execute explicitly confirmed permanent cleanup of Lead KZ202609201739000001 and its exclusive related records and object attachment in test database zsjos, tenant 1.
- Non-goals: Other leads/accounts, schema/source changes, service lifecycle, production, commits/publication.
- Ownership: handoff/test_main.md append only; controlled database rows and one exclusive S3 object; restricted recovery artifacts outside repository at /var/backups/zsjos/lead-cleanup/KZ202609201739000001/.
- Dependencies: Existing Docker MySQL client and Python boto3; no added dependencies. Target branch/integration order: None.
- Verification: Revalidate target/counts and exclusive references; backup selected rows and object with checksums; guarded transactional database deletion; verify zero remaining scoped rows and S3 absence, compare unrelated row counts. Preserve all existing repository changes.

## Delivery Entry — 2026-09-20T18:39:27+08:00

- Workstream: test-approved-single-lead-cleanup; owner/environment: Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Permanently delete confirmed Lead KZ202609201739000001 and exclusive related records from test zsjos, tenant 1. Explicit user confirmation covered listed database rows and S3 attachment.
- Decisions/result: Removed 18 rows across 9 tables: zsjos_lead, zsjos_person, zsjos_person_contact_claim, zsjos_lead_assignment_history, zsjos_lead_intended_product, zsjos_lead_attachment and infra_file (one each), system_notify_message (8), system_notify_business_outbox (3); deleted one exclusive S3 object. Tenant predicates retained; tenantless infra_file used exact exclusive identity. No schema/config/account/permission changes.
- Changed repository files: handoff/test_main.md append only; all existing unrelated edits preserved. Execution/recovery artifacts are outside repository.
- Verification: Rechecked latest submitter record and direct Lead/person/file references, generic task/form/work-order/payment/log relations and notification deliveries. Restricted SQL dumps verified by checksums and row counts, then loaded into temporary tables; compared every column to locked live rows within the guarded serializable deletion transaction. First attempt failed before deletion on path collation comparison; corrected guard to byte comparison without schema changes, rerun committed. Post-check: every scoped row and checked relationship absent, all 9 affected-table totals reduced exactly by approved counts, S3 HEAD returned 404. Backup object size and SHA-256 verified.
- Recovery/evidence: /var/backups/zsjos/lead-cleanup/KZ202609201739000001/ (directory 0700, files 0600) contains per-table SQL, attachment binary and restore metadata, checksum manifest, guarded deletion SQL, commit marker, verification.json and RECOVERY.txt. Backups contain sensitive data and remain outside version control. Restore order and explicit recovery authorization requirement documented there.
- Dependencies/integration impact: None; used existing MySQL tools and boto3. No added dependencies, service restart, deployment, branch or commit operation. No schema migration was applicable to this one-record operational cleanup.
- Remaining work: None for approved cleanup. Recovery backups intentionally retained; external messages already delivered to third-party channels cannot be recalled by this operation. S3 verification establishes current object absence, not historical version erasure.

## Registration update — test-student-list-scroll — 2026-09-20T18:40:00+08:00

- Existing registration metadata unchanged. User withdrew status display after account-level ownership review; final scope is name, student business number, mobile, WeChat and continuous loading. No status/backend/API work required. Dependencies: None beyond existing frontend APIs.

## Delivery Entry — 2026-09-20T18:40:00+08:00

- Workstream/owner/environment: test-student-list-scroll / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Reference Lead Management left cards, show name/student number/mobile/WeChat, omit course/service count/status, replace pagination with lazy loading. Detail layout unchanged.
- Decisions/result: Existing paged API remains authoritative; IntersectionObserver appends the next batch, deduplicates identities, rejects stale list responses, preserves selection/detail on append, and provides retry/end states. Search resets paging; collapsed avatar rail shares continuous loading. Own list navigation no longer resets the list. External deep links retain detail navigation. Cards reuse Lead Management base styling and place name/number across the full header width.
- Changed files: frontend/workbench/src/pages/MediaStudentsPage.tsx; frontend/workbench/src/styles/pages/media-students.css; frontend/workbench/docs/ui-guidelines.md; handoff/test_main.md. Pre-existing unrelated edits preserved.
- Verification: TypeScript check passed; focused media-students guard/account-tab tests 8/8 passed; scoped diff check passed. Actual MediaStudentsPage production fixture build passed. Chromium fixture verified 20→40→45 append, append failure/retry, retained selected student, no pagination/course count, WeChat display, collapse/expand, real keyboard search, denied/empty states, desktop 1440px and mobile 390px bounds with no browser exceptions. Temporary evidence: /tmp/zsjos-student-list/{entry.tsx,browser.mjs,build.log}; screenshots /tmp/zsjos-table-student-list-{desktop,mobile}.png.
- Verification limitation: Browser system fonts lack full Chinese coverage; fixture subset font confirms some Chinese labels, full Chinese typography remains unverified (full font download timed out). Real authenticated deployed API smoke and publication not performed. No bundling/dependency/route changes; full application release build not required for this local UI change.
- Dependencies/integration: None. No backend/database, permissions, shared services, dependencies, branches, commits or publication operations.
- Remaining work: Normal-font visual acceptance and deployed authenticated smoke following separately authorized publication.

## Registration — test-director-operator-notifications — 2026-09-20T23:11:09+08:00

- Owner/environment: Codex /root / test; branch/worktree/base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Complete director/operator notification triggers, recipient resolution, configurable defaults, reminder scheduling and authorized message navigation identified in the read-only audit.
- Non-goals: Business workflow redesign, permission assignments, historical message replay, new dependencies, deployment/service operations or shared database writes without separate authorization.
- Ownership: backend/yudao-module-zsjos notification providers/publishers and related media, positioning, positioninginterview, studentcontact, account, delivery, contentreview, task services/controllers/VOs/tests; existing BPM public task read boundary if needed; frontend/workbench notification actions and related routing tests; frontend/admin notification consumers only if affected; script/sql/mysql notification migration and scoped verification; directly affected API/notification documentation; handoff/test_main.md append only. Preserve existing changes, no concurrent file-changing agents.
- Dependencies: Existing System notification/outbox, BPM public APIs and current business relationships. Target branch/integration order: None.
- Verification: Focused recipient/trigger/idempotency/reminder/authorization tests, backend compilation, frontend tests/typecheck/build and browser fixture, isolated SQL replay/repeatability/UTF-8 and scoped read-only comparison. Shared test database synchronization and runtime delivery remain separately controlled.

## Registration update — test-director-operator-notifications — 2026-09-20T23:36:00+08:00

- Existing registration metadata unchanged. Explicit scope additions: BPM BpmProcessTaskApi/Impl, additive pending-task DTO and focused API test; Workbench ContentReviewBatchPage, WorkOrderCenterPage, WorkPlanPage and api.ts for notification deep links; ZSJOS MediaStudentService target VO and permission tests; WorkPlanNotifySceneProvider for correct plan-vs-task variable namespace. All are directly required notification consumers. No dependency additions. Defaults assumed after optional preference question: in-app; interview advance 60 minutes and live BPM review overdue 1440 minutes, configurable; student link sharing stays manual.

## Delivery Entry — 2026-09-20T23:49:40+08:00

- Workstream/owner/environment: test-director-operator-notifications / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Completely fill director/operator business notifications following the read-only coverage audit.
- Decisions/result: Added collaborator handover, interview scheduling/completion, positioning approval/link-ready/effective/revision/application, account creation, diagnosis completion, delivery completion and content publication events. Manual student link sharing remains manual; no tokens in notifications. Fixed assignee/supervisor role filtering and diagnosis payload; media timing sends all eligible rules without suppressing distinct recipients. Added configured interview/review reminders and tenant initializer; BPM exposes additive tenant-scoped live task assignments. V269 covers 59 in-app scene defaults, preserves custom/disabled rules and historical messages, adds assignee due only beside untouched V216 supervisor default. Corrected stageCode contract and work-plan summary namespace. Workbench checks real APIs, opens exact batches/orders/plans and resolves service/unbound positioning/diagnosis/delivery targets; unsupported/denied actions fall back to message detail.
- Changed files: BPM api/task/BpmProcessTaskApi.java, BpmProcessTaskApiImpl.java, dto/BpmPendingTaskRespDTO.java and API test; ZSJOS enums/MediaNotificationScenes.java, media/MediaNotifySceneProvider.java and new MediaCollaborationNotifyPublisher, MediaNotificationReminderScheduler, MediaNotificationTenantInitializer; related account/MediaAccountService and MediaAccountProfileService, positioning/PositioningCardService/PositioningConfirmationService/PositioningEvidenceService/PositioningAssignmentService, positioninginterview/PositioningInterviewService, studentcontact/StudentContactServiceImpl, content/ContentService, contentreview/ContentReviewNotifyPublisher/ContentReviewNotifySceneProvider, delivery/StudentDeliverySubmissionServiceImpl, task/BusinessTaskReminderService, registration/MediaStudentService and target VO, workplan/WorkPlanNotifySceneProvider; focused tests in these scopes. Workbench services/api.ts, notifyMessageAction.ts/test, pages/ContentReviewBatchPage.tsx, WorkOrderCenterPage.tsx, WorkPlanPage.tsx. SQL migrations/V269__director_operator_notifications.sql and tools/test_director_operator_notifications.py. docs/api/director-operator-notifications.md, system-business-notifications.md, historical director matrix and two module content-review notification guides; this handoff append only. Existing user edits retained.
- Verification: Isolated source copy /tmp/zsjos-notify-check/backend Maven reactor with Java 25: 193 focused tests, zero failures/errors; repository Java source bytes match tested copy. Workbench typecheck and production build passed, output /tmp/zsjos-notify-workbench-dist; 32 notification tests passed. Chromium mounted real React batch/order pages with synthetic transport: exact record outside first list page, message clicks, permission-denied fallback and desktop/mobile execution, no exceptions. Evidence /tmp/zsjos-notify-browser/result.log. Isolated MySQL replay passed V269 defaults, existing custom/disabled tenants, relational template matching, Chinese HEX, version marker and repeatability; no shared DB writes. Read-only test zsjos tenant 1 comparison: 41 missing in-app scene rules plus expected diagnosis-assignee rule addition. Scoped diff and documentation links passed.
- Verification caveats: Shared target directories caused permissions/incremental compilation interference; final successful backend verification used isolated source copy, frontend build used temporary output. Corrected stale confirmation test assertion to student_evidence_pending per existing business contract. Browser fixture initially had incomplete API snapshots/overlay interactions; corrected fixture and reran successfully. Existing chunk-size warnings remain. No authenticated shared runtime/real notification delivery or backend restart was performed; additive BPM API does not change existing frontend contracts, Admin keeps existing message-detail fallback.
- Dependencies/integration: No new npm/Maven dependencies, role grants, branch/worktree operations, commits, pushes, deployment or shared services changed. V269 is a forward upgrade after V268; do not rewrite historical deployed migrations.
- Remaining work: Separate authorization requested for test zsjos configuration synchronization, with backup under /var/backups/zsjos/director-operator-notifications/; no authorization received at delivery time. Shared DB remains unchanged. Deploy matching backend/Workbench and run authenticated notification smoke after separate deployment approval. Historical 15 failed diagnosis outbox events deliberately not replayed.

## Registration update — test-director-operator-notifications — 2026-09-21T00:00:34.789646+08:00

- Existing owner/branch/worktree/base unchanged. User explicitly authorized V269 configuration synchronization to test zsjos tenant 1 and configuration backup under /var/backups/zsjos/director-operator-notifications/. Scope excludes other migrations, deployment/restart, permissions and historical replay. Additional read-only scope: WeCom/in-app rule parity and H5 partner notification consumers. Verification: preflight version/runtime prerequisites, restricted backup integrity, scoped configuration and UTF-8 checks if execution becomes eligible.

## Delivery Entry — 2026-09-21T00:01:28.363730+08:00

- Workstream/owner/environment: test-director-operator-notifications / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Execute authorized V269 test configuration synchronization and audit WeCom/in-app parity plus H5 business notifications.
- Decisions/result: V269 authorization retained. Preflight found both core version tables at V267, missing V268, and deployed runtime JAR lacks MediaNotificationReminderScheduler. V269 explicitly requires V268 and matching backend; database execution paused without expanding authorization to unrelated schema changes or deployment. No SQL writes, notification sends, replay, service changes or permission changes performed.
- Backup: /var/backups/zsjos/director-operator-notifications/20260921-000034/; tenant-1 rules, global templates, two version tables; restricted directory/files 0700/0600, four nonempty dumps with verified SHA-256 manifest. Refresh before eventual execution; restoration needs scoped review.
- Read-only verification: tenant 1 has 96 enabled in_app rules, 92 enabled wecom rules, one disabled each. All 92 enabled wecom rules have an in_app peer with identical recipients, specified users, action and timing; linked templates enabled. Four enabled in_app scenes lack enabled wecom rules: zsjos.lead.submitter_feedback_created, zsjos.lead.submitter_supplemented, media.account.diagnosis, student.delivery.confirmation. V269 covers 59 scenes, only 16 currently have enabled WeCom rules (43 missing); V269 does not add WeCom. Tenant WeCom channel enabled. Partner accounts 121, push enabled 0; persisted tenant messages only ADMIN (633), no PARTNER messages. Aggregate evidence only, no personal data emitted.
- Code audit: channels are independently configured; WeCom send does not persist an in_app message automatically and is not protected by the in_app durable outbox guarantee. H5 PARTNER-scoped message list/detail/read/unread APIs and profile WeCom binding/preferences exist. H5 details support lead, sales_order list fallback, cashback, withdrawal, feedback; WeCom partner target resolver supports lead/cashback/withdrawal/feedback, other targets fall back to inbox. Employee media workflows are not H5 partner workflows. H5 messages refresh on navigation/pull refresh; no WebSocket live notification consumer found. Actual WeCom sends/H5 authenticated runtime delivery not tested.
- Changed repository files: handoff/test_main.md append only. Verification: source inspection, deployed JAR class inspection, current database aggregate/configuration comparisons, backup integrity and scoped diff check. No application edits; prior implementation tests unchanged and not rerun.
- Dependencies/integration impact: Requires separately scoped V268 schema change and matching application deployment before V269; existing V269 approval remains valid. No new dependency or Git operation.
- Remaining work: Resolve migration/runtime prerequisites, refresh backup, execute authorized V269 and verify additions, preservation, UTF-8 HEX and versions. WeCom expansion/partner preference changes and live delivery were not authorized by this audit request.

## Registration update — test-director-operator-notifications — 2026-09-21T00:16:19.563262+08:00

- User confirmed V269 (not applied V267) as the script to extend with WeCom defaults. Scope: V269, its isolated SQL tests, directly affected notification docs and this log. Add missing business WeCom templates/rules from authoritative in_app sources after existing V269 defaults; preserve any existing per-scene WeCom rules including disabled/custom rules, tenant channel/user preferences and historical messages. No deployment/shared DB execution until prior prerequisites are met. Verification: isolated execution, rule parity, administrator/template preservation, multiple-source rules, UTF-8 HEX, idempotency and read-only impact comparison. Existing metadata unchanged.

## Delivery Entry — 2026-09-21T00:18:41.339971+08:00

- Workstream/owner/environment: test-director-operator-notifications / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Confirmed unapplied V269 as the migration in which to complete WeCom alongside in_app notifications.
- Decisions/result: Extended V269 after its 59 in_app defaults with business template mirroring using existing V177 code naming and source-preserving fields, then one set-based insert copying all source rules for tenant/scenes without any existing WeCom rule. Scope also covers post-V177 business scenarios such as lead feedback. Existing WeCom scenes, disabled rules and same-code templates are preserved; all recipients, specified users, timing, action and status are copied. No channel/account preferences, permissions, historical messages or delivery machinery changed. New tenant initializer can reuse matching templates through existing System initialization; channels remain independently maintained.
- Changed files: script/sql/mysql/migrations/V269__director_operator_notifications.sql; script/sql/mysql/tools/test_director_operator_notifications.py; docs/api/director-operator-notifications.md; docs/api/system-business-notifications.md; handoff/test_main.md append only.
- Verification: python3 script/sql/mysql/tools/test_director_operator_notifications.py passed in isolated MySQL (14.905s), log /tmp/zsjos-notify-wecom-sql.log. Verified multi-tenant preservation, existing custom template bytes, disabled WeCom rule preservation, multiple timing/recipient rules copied together, source status including disabled copied, rule/template channel and scene match, default template availability for new tenant initialization, Chinese title HEX, repeat execution exact configuration equality and V269 record. Initial test fixture omitted mandatory summary; fixture corrected without schema changes before final passing run. Scoped UTF-8/whitespace and diff checks passed. No Java/frontend behavior changed, so earlier runtime tests were not rerun.
- Dependencies/integration impact: None added; unchanged V268/matching-backend prerequisites. Shared test database remains untouched and existing authorized synchronization awaits these prerequisites; no deployment, restart, branch, commit or actual message send.
- Remaining work: Refresh actual configuration/backup and execute V269 when prerequisites are met; verify live additions/UTF-8/version state and authenticated delivery. Real WeCom delivery and H5 end-to-end remain unverified; this script does not enable partner push preferences or add H5 employee workflows.

## Registration — test-partner-wecom-foundation — 2026-09-21T01:06:17.123258+08:00

- Owner/environment: Codex /root / test. Branch/worktree/base: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: First-priority Partner notification identity and WeCom authorization repair following user approval.
- Non-goals: New notification policy, SQL/configuration writes, preference changes, actual sends, deployment/restart, commits.
- Ownership: LeadSubmissionServiceImpl, LeadContactActivationService, LeadNotifySceneProvider and focused tests; System SocialClientServiceImpl, error constants, focused social tests; frontend/h5/docs/login.md, docs/api/system-business-notifications.md; this handoff append only. Existing edits preserved; one file-changing owner.
- Dependencies: Existing tenant SocialClient configuration and typed Partner identities; no added dependency. Target branch/integration order: None.
- Verification: Typed operator collision tests and publisher assertions; Partner fallback/override/disabled/missing plus ADMIN/MEMBER social regression tests; focused Maven checks, diff checks. Runtime activation requires separately authorized deployment; no real-account tests or external sends.

## Delivery Entry — 2026-09-21T01:14:34.779474+08:00

- Workstream/owner/environment: test-partner-wecom-foundation / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Repair first-priority Partner WeCom entry and notification identity issues after full-flow audit.
- Decisions/result: Creation and contact-activation events freeze operator user type; notification recipient resolution preserves PARTNER versus ADMIN even for identical numeric IDs, rejects explicit unknown types, and retains legacy employee event compatibility. Partner operator/submitter labels resolve through Partner data with existing submitter masking. Partner WeCom authorization uses dedicated tenant application when configured, otherwise enabled complete tenant ADMIN application, retaining PARTNER binding/token identities. Disabled/incomplete dedicated configuration is not bypassed; unavailable application returns stable actionable error. No account preferences changed.
- Changed files: ZSJOS service/lead/LeadSubmissionServiceImpl.java, LeadContactActivationService.java, LeadNotifySceneProvider.java and their tests; personnel PartnerAuthServiceImplTest.java, PartnerProfileServiceImplTest.java; System SocialClientServiceImpl.java, ErrorCodeConstants.java, SocialClientServiceImplTest.java; frontend/h5/docs/login.md; docs/api/system-business-notifications.md; handoff/test_main.md. Prior unrelated edits preserved.
- Verification: Focused Maven reactor compilation and six test classes passed 94 tests, zero failures/errors/skips; final log /tmp/zsjos-partner-wecom-tests.log. Covers application fallback/override/missing/disabled/incomplete and ADMIN/MEMBER regression, typed token/binding, unbound push rejection, operator ID collision, Partner display-name source, creation/activation event type. Initial build blocked by root-owned existing target metadata; reused sudo Maven with existing dependency repository, no chmod/cleanup. Corrected missing import and old reflective test signature before final pass. Scoped UTF-8 and git diff --check passed.
- Dependencies/integration impact: No new dependencies, SQL, shared database writes, actual sends, service lifecycle, branch, commit or publication operations. H5/admin/workbench runtime consumers unchanged; shared social service regression verifies ADMIN and MEMBER retain prior credential selection.
- Remaining work: Runtime deployment and dedicated-account WeCom bind/login/delivery/click acceptance unverified because no deployment or real-account mutation/send authorization. This first batch does not complete missing withdrawal/feedback rules, click-ticket recovery/order navigation or durable external delivery; no whole-chain completion claimed.

## Registration update — test-partner-wecom-foundation — 2026-09-21T01:18:59.737112+08:00

- Existing owner/environment/branch/worktree/base unchanged. User requested continuation. Scope added: unapplied V269 withdrawal defaults and isolated SQL tests; WithdrawalNotifySceneProvider, WithdrawalServiceImpl, new withdrawal tenant initializer and focused tests; WecomClickTicketService and focused tests; directly affected notification docs.
- Goal: Complete five existing withdrawal scenes through default rules and new-tenant initialization; verify feedback/supplement WeCom mirroring; resolve Partner sales-order notifications to their authorized lead instead of generic inbox.
- Contract boundary: Architecture explicitly requires one-time short-lived click tickets; retain that behavior in this batch. No new scene policy, arbitrary links, sends, SQL writes to shared test or deployment. V268/V269 remain absent in test database; earlier approved synchronization remains blocked by matching runtime prerequisites.
- Verification: Isolated MySQL execution/preservation/UTF-8 HEX/repeatability and scoped comparison; Java recipient/default/payload tests and click ticket target/one-time/invalid/missing ownership tests. Both existing ADMIN and Partner ticket audiences covered; existing H5 lead route reused without frontend changes.

## Delivery Entry — 2026-09-21T01:25:20.211059+08:00

- Workstream/owner/environment: test-partner-wecom-foundation / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Continue Partner WeCom fixes with missing notification defaults and usable sales-order destinations.
- Decisions/result: Added five existing withdrawal scene defaults before V269 WeCom mirroring, using business withdrawal number, amount and rejection reason. Finance receives submission/weekly summary; applicants and finance receive approval/rejection/payout results. New tenants use existing System default-rule API and matching WeCom templates. Existing disabled/custom configuration, account preferences and historical messages preserved. Partner sales-order tickets snapshot only an owned associated Lead destination; missing/mismatched relations fall back to inbox and authenticated detail rechecks ownership. ADMIN routing unchanged. One-time short-lived tickets retained per architecture contract.
- Changed files: V269__director_operator_notifications.sql; test_director_operator_notifications.py; WithdrawalNotificationTenantInitializer.java (new), WithdrawalNotifySceneProvider.java, WithdrawalServiceImpl.java; WecomClickTicketService.java; WithdrawalNotificationDefaultsTest.java (new), WithdrawalServiceImplTest.java, WecomClickTicketServiceTest.java (new); docs/api/system-business-notifications.md, docs/api/director-operator-notifications.md; handoff/test_main.md. Existing unrelated edits preserved.
- Verification: 19 focused Java tests passed across four classes; includes typed applicant/finance collision, absent Partner account, new-tenant context restoration, workflow payload business number/reason, order ownership/missing fallback, ADMIN/Partner routing, and one-time TTL regression. Reactor compilation passed. Latest withdrawal payload assertions separately recompiled and rerun after initial test run; all seven passed. Logs: /tmp/zsjos-partner-notification-tests.log and /tmp/zsjos-withdrawal-payload-tests.log. Isolated MySQL V269 replay passed (17.055s): all five scenes dual-channel, exact recipients, custom disabled rule preservation, Lead feedback/supplement mirroring, Chinese HEX, template-rule relationships, version record and unchanged rerun. Log: /tmp/zsjos-partner-notification-sql.log. Scoped diff check passed.
- Dependencies/integration impact: No new dependency, grants, shared database writes, actual sends, deployment, service restart, branch, commit or publication. SQL uses existing V269/V268 prerequisite chain; no new numbered migration. Read-only test database comparison still shows no withdrawal templates/rules, feedback/supplement in_app only, and no V268/V269 version records. Therefore source change is ready but shared synchronization and actual delivery remain incomplete. Existing V269 authorization does not bypass runtime/V268 prerequisites or automatically authorize newly expanded synchronization scope.
- Remaining work: Deploy reviewed matching runtime and resolve migration prerequisites before separately scoped shared synchronization/real-account acceptance. Durable WeCom retries and in_app sales-order precise navigation remain outside this batch. Reusable/longer-lived ticket behavior requires explicit resolution of the existing architecture contract before changing it. Existing live configuration is not represented as fixed.

## Registration update — test-partner-wecom-foundation — 2026-09-21T01:31:49.219261+08:00

- Existing owner/environment/branch/worktree/base unchanged. User authorized continuation with reliable WeCom delivery and H5 in-app sales-order navigation.
- Scope: System notify event API, processor, outbox service/mapper, WeCom adapter, additive durable payload DTO/service and query VO/controller endpoint/tests; ZSJOS Partner message controller/VO/order target service/tests; H5 message API/detail and focused browser tests; directly affected notification/API docs and this record. Existing edits preserved.
- Design: Reuse outbox JSON payload in a versioned WeCom envelope, frozen rendered per-recipient state and fenced checkpoints, no schema/dependency additions. Confirmed successes are not resent; interrupted/ambiguous sends become uncertain terminal failures for inspection. Rule/tenant configuration remains authoritative. Add authorized tenant-scoped delivery query without exposing content/tokens. Partner message detail projects an owned order's Lead destination.
- Non-goals: New notification business policy, changes to one-time ticket contract, real sends/preferences/shared configuration, deployment/restart, commits.
- Verification: Processor/outbox/adapter recipient isolation, retries, uncertainty, fencing, statuses, query permission/tenant and no-payload tests; Partner target owner/deleted/missing/action tests; H5 type/build and actual browser success/failure/denied navigation. SQL schema checks are inapplicable; existing MySQL outbox checkpoint/query contract tested in isolation if available.

## Delivery Entry — 2026-09-21T01:59:31.442790+08:00

- Workstream/owner/environment: test-partner-wecom-foundation / Codex /root / test. Branch/worktree/HEAD: main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged; existing registration applies.
- User goal: Continue reliable WeCom delivery and precise Partner H5 in-app sales-order navigation.
- Decisions/result: WeCom now joins the existing transactional System outbox using a versioned JSON envelope, frozen typed/rendered recipient snapshots and click URLs. Fenced tenant/token/unexpired-lease checkpoints persist send intent/results; replay skips successful/permanent outcomes, preserves uncertain interrupted sends without blind resend, and processes remaining recipients after failure. Explicit transient provider rejection retries; invalid-token rejection can refresh once. Pre-POST lookup failure remains safely retryable; timeout/malformed POST response becomes uncertain. Dedicated disabled application no longer falls back, aligning the already-approved auth contract. Workers re-read after claim and calculate a fresh lease per row. Jackson 3 builder compatibility and legacy null payload support verified; malformed payload parsing does not log original content. publishConfirmed explicitly documents WeCom durable queue acceptance, not provider delivery.
- Query/navigation: Added tenant-scoped permission-protected delivery-page API returning safe status/error projections, with no bodies/tickets/payload. Existing Admin/Workbench rule and message contracts unchanged; no new UI or grants. Partner detail authorizes the typed stored message before resolving the order's owned Lead; H5 uses the exact destination or shows unavailable explanation. Existing route/object checks and one-time 30-minute WeCom tickets remain in effect.
- Changed files: System api/notify/NotifyBusinessEventApi.java, NotifyBusinessEventApiImpl.java, dto/NotifyDeliveryContext.java; service/notify/NotifyBusinessEventProcessor.java, NotifyBusinessOutboxService.java, WecomNotifyChannelAdapter.java, new WecomOutboxPayload.java, WecomOutboxDeliveryService.java, NotifyDeliveryQueryService.java; dal/mysql/notify/NotifyBusinessOutboxMapper.java; controller/admin/notify/NotifyRuleController.java and new delivery request/response VOs; six corresponding System test classes. ZSJOS PartnerAppMessageController.java, new PartnerNotifyMessageRespVO.java, PartnerNotificationTargetService.java and two focused tests. H5 src/api/message.ts, src/pages/messages/detail.vue, tests/message-navigation-browser.mjs, 兼职端API接口.md; docs/api/system-business-notifications.md, docs/api/director-operator-notifications.md; this record append only. Unrelated edits preserved.
- Verification: 40 focused Java tests across eight classes passed without failures/errors/skips. Full ZSJOS reactor test/compilation log /tmp/zsjos-reliable-tests.log; final System regression after silent JSON parsing changes /tmp/zsjos-reliable-system-final.log. Covers per-recipient retry/deduplication/typed collision, interrupted send, lost lease, disabled rule/application, invalid recipient, token refresh/body stability, frozen template, fresh claim snapshot, null payload, safe projection, tenant predicates and actual method-security allowed/denied invocation, Partner message ownership/action/target cases. Initial failures exposed Jackson 3 incompatibility; corrected it plus test fixture wiring/stubbing. Final null-payload rerun also refreshed a stale class compiled before its source edit.
- Additional evidence: Isolated MySQL ran SQL extracted from the actual mapper, verifying due-time claim, tenant/token/expiry fencing, takeover and payload preservation; /tmp/zsjos-wecom-outbox-sql.log (script /tmp/zsjos-wecom-outbox-sql.py). No schema migration changed. H5 vue-tsc and production bundle passed (/tmp/zsjos-h5-type.log, /tmp/zsjos-h5-build.log); real Chromium exercised synthetic-only APIs at desktop 1440 and mobile 390: exact order navigation, unavailable relation, message-only action, error/retry, unauthorized route, no runtime exceptions (/tmp/zsjos-h5-browser.log). Screenshots inspected at both widths; temporary test-only CJK font addressed missing host fonts without product/dependency changes. Scoped diff/UTF-8/whitespace checks passed.
- Dependencies/integration impact: None added. No shared DB writes, real notification sends, preference changes, deployment/restart, branch/commit/push or publication. Runtime wiring/live endpoint smoke remains unverified because current shared service was not replaced or restarted. Existing V268/matching-runtime prerequisites still block earlier V269 synchronization; no live configuration fix is claimed.
- Remaining work: Reviewed deployment, prerequisite-compatible shared SQL synchronization and dedicated-account real WeCom bind/login/send/click acceptance require their separately scoped authorization. Uncertain sends deliberately require investigation and are not automatically replayed. This delivery provides backend status query, not an operations UI; whole-chain live delivery is not claimed complete.

## Registration update — test-partner-wecom-config — 2026-09-21

- Owner: Codex /root; environment: test; branch: main; worktree: /opt/zsjos; base/HEAD: 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Complete user_type=3 dictionary and move tenant 1 social client record 2 (AgentId 1000051) from MEMBER to PARTNER, as explicitly authorized by user.
- Non-goals: ADMIN application, credentials, account bindings, grants, deployment, restart, commits, unrelated existing work.
- Ownership: script/sql/mysql/03-bootstrap-dictionary-types.sql; frontend/h5/docs/login.md; handoff/test_main.md; scoped test database configuration above.
- Dependencies: Existing System dictionary and PARTNER social client selection; no new dependency. Target branch/integration order: None.
- Verification: Execute dictionary SQL in isolated temporary tables and repeat; scoped backup and transaction; UTF-8 HEX; unchanged ADMIN config; real Partner authorization login/bind URLs. No baseline chain/order/schema change.

## Delivery Entry — 2026-09-21 03:21 +08:00 — test-partner-wecom-config

- Branch/worktree/owner/environment: registration unchanged; HEAD 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Complete Partner dictionary option and directly adjust AgentId 1000051 application settings.
- Decisions/result: Added missing active System user_type=3 (兼职合作方); tenant 1 social client record 2 changed MEMBER=1 to PARTNER=3 and removed leading AgentId space. ADMIN record 1/1000062 unchanged. Credentials, account bindings and permissions untouched. User explicitly authorized these shared test configuration writes.
- Changed files: script/sql/mysql/03-bootstrap-dictionary-types.sql (missing enum seed only); frontend/h5/docs/login.md; handoff/test_main.md. No migration version/order/schema/checksum records changed.
- Verification: Changed SQL block and complete dictionary seed executed twice successfully in isolated codex_partner_dict_20260921_0319 schema; exactly one active value 3. UTF-8 client and HEX(label)=E585BCE8818CE59088E4BD9CE696B9 match test database. Transaction updated exactly one social client. Real HTTPS login and bind authorization endpoints both return AgentId 1000051 and expected testpartos callbacks. Scoped git diff --check passed. Initial temporary-table harness failed due to MySQL temporary self-reference restriction, before live writes; switched to isolated ordinary tables successfully.
- Recovery: Restricted field-only backup /opt/zsjos-runtime/backups/partner-wecom-config-20260921-0319.tsv (0600); restore record 2 previous type/AgentId if separately authorized, disable newly inserted dictionary entry only after consumer review. No credentials in backup. Isolated validation schema retained; no destructive cleanup.
- Dependencies/integration impact: No dependency, frontend static option, backend code, restart, deployment, commit or push. Existing deployments must review baseline checksum handling; no automatic reconciliation. Full bootstrap/upgrade chain not run for this scoped dictionary correction with unchanged chain/schema. Existing unrelated dictionary duplicates preserved.
- Remaining work: Actual user login/binding and WeCom-side callback configuration require interactive acceptance; no completed binding claimed. Admin cached dictionary may require refresh after cache expiry or re-login. No real account binding or messages performed.

## Registration — test-partner-wecom-web-oauth — 2026-09-21
- Owner/environment: Codex /root / test; branch/worktree/HEAD main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- Goal: Partner H5 WeCom in-client webpage authorization after consent, external QR retained.
- Scope: System SocialClient API/service and focused tests; Partner auth controller/service and tests; H5 auth API/login page/tests; H5 login documentation; this log.
- Non-goals: dependencies, database, account binding changes, ADMIN/Workbench behavior, deployment/restart, commits.
- Dependencies: Existing JustAuth shared enterprise code exchange/state validation and tenant application selection. Target branch/integration order None.
- Verification: focused Java tests/compile; H5 build and browser consent/UA/login/bind checks. Real WeCom acceptance requires deployed code and actual client.

## Delivery Entry — 2026-09-21 03:34 +08:00 — test-partner-wecom-web-oauth
- Owner/environment/branch/worktree/HEAD: registration unchanged; HEAD 050f43dd0dcc996ce468efafa4715870264dafd6.
- User goal: Use in-client webpage authorization for Partner H5 login/binding after consent, retaining external QR login.
- Decisions/result: Add optional inWecom (default false) at Partner authorize endpoint. H5 shared auth API detects wxwork for both login/bind. System web URL generation preserves existing enterprise application's registered state, encoded callback and identity namespace, adds snsapi_base OAuth endpoint. Existing JustAuth enterprise code exchange, tenant config, typed Partner binding and token validation reused. Removed automatic agreement bypass; explicit user action starts login.
- Changed files: System api/social/SocialClientApi.java and Impl; service/social/SocialClientService.java and Impl; SocialClientServiceImplTest.java; ZSJOS PartnerAppAuthController.java, PartnerAuthService.java and Impl, PartnerAuthServiceImplTest.java; frontend/h5/src/api/auth.ts, src/pages/login/index.vue, tests/wecom-authorization-browser.mjs, docs/login.md; handoff/test_main.md. Existing unrelated edits preserved.
- Verification: Maven reactor compile and 48 focused tests passed (34 social client, 8 Partner auth, 6 Partner profile), including existing ADMIN/MEMBER selection regressions and new URL/state/encoding/mode checks; /tmp/zsjos-web-oauth-tests.log. H5 vue-tsc and production build passed to /tmp/zsjos-web-oauth-dist; /tmp/zsjos-web-oauth-h5.log. Original dist output permission blocked first build; isolated output resolved without changing permissions or deploying.
- Browser evidence: Real headless Chromium with synthetic APIs ran mobile wxwork and desktop browser login/bind; no automatic redirect, consent cancellation, authorization failure/retry, correct callback/mode, no runtime exceptions. /tmp/zsjos-web-oauth-browser-pass.log. Harness was corrected for dialog/toast animation timing and document replacement; initial stale bundle run discarded. No visual layout changed.
- Dependency/integration impact: No new dependencies, SQL, shared config, credential, actual account binding, service restart, deployment, commit/push. Existing ADMIN and Workbench endpoints still call unchanged getAuthorizeUrl behavior; optional Partner parameter is backward compatible. Scoped diff check passed.
- Remaining work: Deploy matching backend/H5 with separate authorization; mobile WeCom end-to-end code exchange/login/binding and app webpage trusted domain/visibility acceptance cannot be proven with synthetic tests. Live service remains unchanged by this turn.

## Registration — test-mobile-route-prefix — 2026-09-21
- Owner/environment: Codex /root / test; branch main; worktree /opt/zsjos; base/HEAD 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Persist Mobile URL namespace through navigation, deep links, login and refresh; open authorized native Workbench pages on Mobile.
- Non-goals: backend/session lifetime changes, real permissions, deployments, database writes, dependencies, commits. Admin embed scope awaits user clarification.
- Ownership: frontend/workbench/src/main.tsx, constants.ts, services/authSession.ts and tests, services/mobileRoutes.ts and tests, services/menu.test.ts, layouts/RouteHost.tsx and route guard tests, pages/WorkOrderCenterPage.tsx and UserProfilePage.tsx/tests, components/ViralContentMaterialForm.tsx; scoped browser harness under frontend/workbench/test; docs/architecture/data-and-permission-flow.md; handoff/test_main.md.
- Dependencies: existing React Router basename and server-owned menu contracts. No package addition. Target branch/integration order: None.
- Verification: focused route/session/menu tests, typecheck, production build into /tmp, isolated real Chromium desktop/mobile navigation and authorization checks. No real account login or shared deployment.

### Registration update — test-mobile-route-prefix — 2026-09-21 10:34 +08:00
- User explicitly includes authorized Vue admin_embed pages on Mobile. Extend ownership to Workbench layouts/AdminEmbedPage.tsx/tests; Admin src/utils/workbenchAuth.ts, auth.ts, hooks/web/useCache.ts, config/axios/service.ts, permission.ts, utils/impersonation.ts and focused tests; browser fixtures for both consumers. Other registration metadata unchanged.
- Verify isolated Mobile token/client/user-menu cache, same-origin route bridge, refresh/logout and denied states in both runtimes; build both frontends. Do not copy Mobile tokens into PC keys or change backend grants. Admin-only menus remain outside the server-authorized Workbench projection.

## Registration update — test-partner-wecom-login-guidance — 2026-09-21
- Owner/environment: Codex /root / test; main / /opt/zsjos / HEAD unchanged from previous registration.
- Goal: Explicit unbound WeCom popup and password-login guidance; seven-day remembered session verified against existing backend configuration.
- Scope: H5 auth API/request/composable/login and focused browser tests/docs; System Partner-only enterprise identity request/factory/service and tests; Partner auth token tests; this handoff.
- Prerequisite: Remove Partner login/bind dependency on contact-detail API so unbound detection works. Non-goals: other account bindings, ADMIN behavior, dependencies, SQL, deployment/restart, commits.
- Verification: focused Java identity/token/regression tests, H5 type/build/browser unbound vs other errors, refresh success/expiry. Target branch/integration order None.
- Scope refinement: System OAuth2TokenServiceImpl and its existing tests: cap PARTNER access-token expiry at the original refresh deadline so a refresh near day seven cannot extend the remembered session; ADMIN/MEMBER issuance unchanged.
- Scope refinement: Admin src/store/modules/user.ts and utils/workbenchEmbedBridge.ts are included to reject stale Mobile permission-cache fallback and preserve business query/hash through iframe navigation; Workbench embed URL and bridge tests cover the same contract.

## Delivery Entry — 2026-09-21 10:39 +08:00 — test-partner-wecom-login-guidance
- Owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged 050f43dd0dcc996ce468efafa4715870264dafd6.
- User goal: Unbound/new Partner WeCom users receive popup guiding password login; remember login seven days.
- Decisions/result: Dedicated 1900000018 popup explains password login then profile binding and first-time activation. Only this code suppresses generic toast; other authorization errors retain their distinction. Callback query cleared on failure, destination retained. No auto-account creation or phone-number matching. Existing remember-days=7 and mobile 7200/604800 settings verified read-only; no DB write. PARTNER access expiry capped by original refresh expiry, avoiding an extra access-token lifetime after the seventh day.
- Prerequisite fix: Partner-only JustAuth identity adapter obtains UserId without member contact-detail API; shared state validation/cache, effective tenant application and original identity namespace preserved. Non-members/upstream failures rejected. ADMIN/MEMBER use original flow. Auth log no longer serializes code/state/token/user payloads. Binding conflict semantics not changed in this task.
- Changed files: System framework/justauth/core/AuthWecomIdentityRequest.java (new), AuthRequestFactory.java, service/social/SocialClientServiceImpl.java, service/oauth2/OAuth2TokenServiceImpl.java; tests AuthWecomIdentityRequestTest.java (new), SocialClientServiceImplTest.java, OAuth2TokenServiceImplTest.java; ZSJOS PartnerAuthServiceImplTest.java; frontend/h5/src/api/auth.ts, api/request.ts, composables/useAuth.ts, pages/login/index.vue, tests/wecom-unbound-browser.mjs (new), docs/login.md; handoff/test_main.md. Prior unrelated changes preserved.
- Verification: Final Maven reactor passed 71 tests (35 social, 19 OAuth, 3 identity adapter, 8 Partner auth, 6 profile), no failures/errors/skips; /tmp/zsjos-login-guidance-java-final.log. Covers identity-only success, nonmember/error rejection, invalid state, configured 604800 issuance, refresh deadline and existing ADMIN/MEMBER regressions. Fixed test generic type; stale incremental class from editing during initial compilation was detected by deadline test, source touched and full affected compilation rerun successfully.
- Frontend verification: vue-tsc + production build passed to /tmp/zsjos-login-guidance-dist; /tmp/zsjos-login-guidance-build.log. Real Chromium synthetic API tests passed unbound popup/acknowledgement, callback replay prevention, other-error distinction, automatic refresh and expired-session clearing; /tmp/zsjos-login-guidance-browser.log. Existing in-client/external login/bind consent/retry browser suite passed against same bundle; /tmp/zsjos-login-guidance-auth-regression.log. Mobile 390 and desktop 1440 screenshots inspected; temporary test-only CJK font used because host has no Chinese font. git diff --check passed.
- Dependencies/integration impact: No new dependency, shared DB/config mutation, account operation, runtime restart/deployment, commit or push. New adapter selected only for PARTNER, expiry cap only for PARTNER; Admin and Workbench shared ADMIN contract unchanged, regression-tested at service boundary. No seven-day plaintext password persistence.
- Remaining work: Deploy matching backend and H5 under separately authorized service/deployment scope, then real mobile WeCom acceptance. Synthetic tests cannot validate live corporate credentials/member visibility; identity endpoint may still return genuine 60011 for application permission problems. Existing issued tokens are not retroactively capped; new issuance/refresh applies cap. Live service not changed in this turn.

## Delivery Entry — 2026-09-21 10:43 +08:00 — test-mobile-route-prefix
- Owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged 050f43dd0dcc996ce468efafa4715870264dafd6.
- User goal: Keep Mobile in employee URLs through all navigation and allow authorized native and Vue admin_embed pages on Mobile; user explicitly confirmed inclusion of embedded management pages.
- Decisions/result: `/zsjos/mobile` is a React Router basename, followed by the complete canonical server route (e.g. `/zsjos/mobile/zsjos/tasks/today`, `/zsjos/mobile/system/notice`). Menu/permission metadata stays server-owned. Old marked Mobile tabs normalize before router mount; copied links identify Mobile without sessionStorage. Login preserves deep-link query/hash. Native content production/review restrictions removed. Ordinary links and WeCom binding callback retain the platform prefix. Admin embeds now support both platforms and preserve business query/hash through the same-origin bridge; no tokens are sent through URLs or messages.
- Authentication boundaries: Mobile Admin uses only MOBILE token keys/client and separate user/menu/visit-tenant caches; no PC/legacy-token fallback or PC impersonation context. Mobile permission loading rejects cached-data fallback on request failure. Expired/missing Mobile embed authentication returns to the outer Mobile login. Backend authorization/grants and standalone PC behavior remain unchanged. Reused existing framework adapters/test runner; no shared package or dependency added.
- Changed Workbench files: src/main.tsx, constants.ts, services/authSession.ts, new services/mobileRoutes.ts and mobileRoutes.test.ts, services/menu.test.ts, layouts/RouteHost.tsx, layouts/pc-only-routes.guard.test.ts, layouts/AdminEmbedPage.tsx and test, pages/WorkOrderCenterPage.tsx, pages/UserProfilePage.tsx and test, components/ViralContentMaterialForm.tsx; new test/mobile-routes-browser.mjs.
- Changed Admin files: new src/utils/workbenchAuth.ts; src/utils/auth.ts, impersonation.ts, workbenchEmbedBridge.ts; src/hooks/web/useCache.ts; src/config/axios/service.ts; src/permission.ts; src/store/modules/user.ts; new tests/workbenchAuth.test.ts and workbenchAuth.vitest.config.mjs. Documentation: docs/architecture/data-and-permission-flow.md and this append-only record. Existing unrelated changes preserved.
- Verification: 61 Workbench focused tests plus 4 Admin token/cache tests passed (65 total). Workbench npm typecheck and final Admin vue-tsc with 8GB heap passed; Admin final incremental recheck exited 0 after the last source changes. Both production bundles passed to /tmp/zsjos-mobile-dist and /tmp/zsjos-mobile-admin-dist. Logs: /tmp/zsjos-mobile-unit.log, /tmp/zsjos-mobile-admin-unit.log, /tmp/zsjos-mobile-type.log, /tmp/zsjos-mobile-admin-type.log, /tmp/zsjos-mobile-build.log, /tmp/zsjos-mobile-admin-build.log. Existing Vite temp-directory permissions were handled with a /tmp config; first Admin typecheck exceeded default heap and was rerun using the project's 8GB setting. No shared directory permissions changed.
- Browser evidence: Real Chromium against both built frontends with isolated synthetic APIs passed Mobile entry/login, menu/back/forward, reload, fresh-tab copied link, query/hash/login return, native production/review, error/retry, denied menus/actions, refresh/logout isolation, real Vue Mobile/PC embeds, iframe route bridge/query preservation, Mobile iframe refresh and expiry return. /tmp/zsjos-mobile-browser.log; screenshots /tmp/zsjos-mobile-{production-390,review-390,review-1440,embed-390,embed-1440}.png inspected. Test-only CJK font used for host font limitations. Harness timing was corrected to await actual iframe refresh rather than its old DOM. No live account/business requests. Read-only GETs to the test site's Mobile root/native/BPM deep paths returned HTTP 200 HTML, confirming current SPA fallback. Scoped diff and new-file UTF-8/whitespace checks passed.
- Reproduction: Admin focused tests reuse the installed Workbench runner via `pnpm exec node ../workbench/node_modules/vitest/vitest.mjs run --config tests/workbenchAuth.vitest.config.mjs` from frontend/admin. Browser harness requires MOBILE_TEST_CHROME and built bundle locations; optional MOBILE_TEST_FONT supplies CJK glyphs without changing product assets.
- Dependencies/integration impact: None added. No database, real permissions, external state, service restart, deployment, Git branch/commit/push change. Deploy matching Admin embed first (or both atomically), then Workbench; old Admin bundles still use PC credentials and must not serve new Mobile embeds. Bundles reflect the existing worktree, including earlier unrelated frontend changes, so publication requires review of that full delivery scope.
- Remaining work: Separately authorized paired frontend deployment and real-account acceptance on testos.zhongshijian.top. Complex management tables retain their existing horizontal scrolling on small screens; not every business form received a mobile layout redesign. This fixes platform/routing continuity, not a claim that all previously reported short-session failures are diagnosed or fixed. Admin-only menus remain excluded from Workbench under the existing server contract.

## Registration scope update — test-positioning-template-preview — 2026-09-21 11:34 +08:00

- Owner/environment/branch/worktree/HEAD: Codex /root / test / main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User confirmed the 47 supplied fields as the desired template after explicit discussion of synchronizing the shared test database. Goal: publish the existing canonical 47-field definition for tenant 1 default template 2 and retire its obsolete 15-field draft so Admin and new business cards agree.
- Scope: read-only backups and controlled data correction in zsjos_director_form_template / zsjos_director_form_template_version (tenant 1, template 2); handoff/test_main.md append only. SQL definition source remains script/sql/mysql/positioning-single-source.sql; no source baseline/checksum rewrite.
- Non-goals: Deleting linked delivery plans/stages or historical business cards; touching the separately created template 52; changing dictionaries, account profiles, permissions, migration records, or deploying frontends/backend. Prior proposed delivery-plan expansion has not been confirmed.
- Dependencies/integration: None. Target branch/integration order: None.
- Verification: fresh scoped backup, guarded transactional SQL generated from the existing canonical source, isolated MySQL execution and repeatability, old version bytes and unrelated template preservation, live UTF-8/HEX and exact 47-field comparison, both consumers' published/draft selection contract review.

## Registration execution update — test-positioning-template-preview — 2026-09-21 11:40 +08:00

- User explicitly overrides backup and isolated verification for this operation: no backup, no isolation environment, no new tables. Proceed directly against the authorized existing tenant 1 default template 2 using a guarded transaction and read-after-write verification. No new backup or verification database will be created. The previous turn's pre-existing backup is not changed or deleted.
- Scope and owner remain as registered; preserve business cards, linked delivery data and separate template 52. Canonical 47-field source is unchanged. Verification uses row locks, exact old-field signature, exact new-field equality, repeatability and scoped before/after fingerprints of unrelated rows; no schema or checksum changes.

## Delivery Entry — 2026-09-21 11:40:38 +08:00 — test-positioning-template-preview

- Owner/environment/branch/worktree/HEAD: Codex /root / test / main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged. Timestamp correction: preceding execution-update entry labeled 11:40 was actually appended at 11:38:30; its content and authorization remain unchanged.
- User goal: Activate exactly the confirmed 47 positioning fields. User explicitly directed no new backup, no isolated environment and no new tables.
- Decisions/result: Direct guarded transaction on existing test database zsjos, tenant 1 default template 2. Published V3 (version row 57) with the exact 47 definitions from script/sql/mysql/positioning-single-source.sql (37 main fields and 10 references). Archived old published V1/id2 and old draft V2/id54 without rewriting their field JSON; default published_version_id now 57. No draft remains under template 2, so Admin's draft-first logic now resolves the same published template as Workbench new-card creation. No frontend/backend deployment or restart required for this database-owned configuration.
- Scope preserved: Separate template 52 and all other templates unchanged. Existing 3 business cards, submissions, service-card mappings, applications, delivery plan/stage unchanged. Earlier proposed delivery-plan deletion expansion remains unconfirmed. No account profile, dictionary, permission, migration/checksum, schema or runtime change; no new backup/isolation/table created.
- Changed repository files: handoff/test_main.md append only. Database rows: template 2 metadata; version 2 and 54 status/audit/version; inserted version 57. Canonical SQL source already contains the agreed fields and was not modified. Execution script /tmp/zsjos-sync-positioning-47.py; executed statements /tmp/zsjos-sync-positioning-47-executed.sql (technical selectors and template definitions only).
- Verification: Exact canonical JSON equality; count 47/37/10; obsolete recommendedMatchRate absent; published V3 pointer verified; draft count zero; representative Chinese label HEX equals expected UTF-8; old JSON SHA256 unchanged; unrelated template fingerprints and relevant business table checksums unchanged. Repeated execution returned already synchronized with no writes. Both consumer source paths reviewed: Admin chooses draft || published, Workbench published-template API selects enabled default and its published version. First transaction rolled back when marker SELECT reset ROW_COUNT; checking ROW_COUNT in the same statement batch fixed the verifier, then transaction succeeded. Rolled-back insertion left only an unused auto-increment gap, no retained row.
- Dependency/integration impact: None; no package, branch, commit or publication action. Read-only live response/browser verification under a logged-in user was not performed; evidence is database plus existing consumer contract inspection, not authenticated browser acceptance.
- Remaining work: Refresh/reopen the default template/new-card form. Existing cards still preserve their old immutable snapshots; removing their linked delivery records remains outside confirmed scope. Separate nondefault draft template 52 still retains its owner's original 15 fields and was not overwritten.

## Registration — test-content-review-attachments — 2026-09-21 11:47:15 +08:00

- Owner: Codex /root; environment test; branch main; worktree /opt/zsjos; base/HEAD 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Implement per-work review attachments, file/drop/clipboard input, deferred upload, draft/revision persistence and responsive UI.
- Non-goals: database writes, deployment/restart, dependencies, permissions assignments, branch/commit/publication operations.
- Ownership: Workbench ContentApprovalDraft, ContentReviewBatchPage, MediaStudentsPage, new ContentReviewAttachments component/service/styles/tests and scoped browser fixtures; content review service request types as needed; ZSJOS ContentVersionService, ContentReviewBatchService, BusinessFileDirectUploadService and focused attachment type helper/tests; docs/api/content-review-batch.md; handoff/test_main.md. Preserve existing unrelated changes.
- Dependencies: existing Infra direct upload, content-version file bindings, BPM attachment projection, deferredUpload and clipboardImage utilities. Target branch/integration order: None.
- Verification: focused frontend/backend tests, typecheck and compile, isolated browser desktop/mobile and upload/retry/paste flows; verify existing approval attachment consumer contract. Shared authenticated endpoint checks reported separately.

## Registration scope update — test-positioning-template-preview — 2026-09-21 11:48 +08:00

- User explicitly authorized removal of the old drafts and published/confirmed positioning records created over September 20–21 so all cards can be filled again; prior explicit no-backup/no-isolation/no-new-table direction remains applicable.
- Scope: test zsjos tenant 1 cards 2,3,4 (old template version 2, created 2026-09-20/21), submission 2 and linked confirmation/service-card/application/log rows; derived delivery plan 1 and waiting stage 1 with zero submissions/defers; account 6 _diagnosisContext source metadata only (source submission 2). Preserve all account fields and snapshots, profile audit entries, students/accounts, canonical 47-field V3 and all template definitions.
- Execution: guarded existing-table transaction, children before parent, exact IDs plus tenant and date/template guards, repeated no-op verification. No backup; pre-commit rollback only, no post-commit recovery artifact. No schema, permissions, service or deployment changes. Ownership adds only this live scoped cleanup and append-only handoff; owner/branch/worktree/HEAD unchanged.
- Verification: row locks and expected counts; abort on BPM/production-ticket/stage-submission/defer/new references; retained business/template fingerprints; no dangling target references and second-run no-op. No personal values written to reports.

## Delivery Entry — 2026-09-21 11:47:43 +08:00 — test-positioning-template-preview

- Owner/environment/branch/worktree/HEAD: Codex /root / test / main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged. Previous scope-update label 11:48 was approximate and preceded this delivery; execution clock is this entry's timestamp.
- User goal: Delete the old recent draft and published/confirmed cards so all positioning cards can be filled afresh. Existing explicit no-backup/no-isolation/no-new-table direction followed.
- Result: Committed one guarded tenant-1 transaction deleting old-template cards 2,3,4 (created September 20–21), submission 2, one confirmation link, three service-card bindings, one application and one application log; removed derived delivery plan 1 and its single WAITING stage 1 (no submissions/defers/completed work). Account 6 retained; only _diagnosisContext source metadata pointing to removed submission 2 cleared and optimistic version advanced. Existing account field values and snapshot bytes preserved, as were profile audit history, students, accounts and all template definitions. Canonical published V3 with 47 fields remains effective. Template configuration drafts are distinct from business cards and were not deleted.
- Verification: Locked exact card/date/template IDs and related rows; required no BPM process, execution/version rows, production references, stage submissions/defers or account card-pointer references. Expected deletion counts all matched. Post-commit all scoped rows absent; tenant active cards/submissions/service bindings/applications all zero. Retained table checksums and non-context account field/snapshot fingerprints unchanged. Repeat execution returned already cleared/no writes. Live published template still has 47 fields. No logged-in browser acceptance performed; verified database and source contracts.
- Changed repository files: handoff/test_main.md append only. Operational SQL and guarded executor retained under /tmp/zsjos-cleanup-positioning-executed.sql and /tmp/zsjos-cleanup-positioning.py without business payloads. No schema, dependency, permissions, commits, deployment or external notifications performed.
- Recovery: No new backup by user instruction; transaction rollback protected pre-commit errors only. Deleted records have no recovery artifact from this operation. Unrelated earlier backup files left untouched.
- Remaining work: Users should refresh/reopen the media student workspace and create cards using the new 47-field template. Historical profile/audit entries remain as audit history; template 52's separate configuration draft remains untouched.

## Delivery Entry — 2026-09-21 12:03:02 +08:00 — test-content-review-attachments

- Owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged 050f43dd0dcc996ce468efafa4715870264dafd6; fixed metadata refers to registration above.
- User goal: Implement review attachment uploads and clipboard image paste with workbench UI styling.
- Decisions/result: Per-work optional 20 attachments plus one image cover; file multi-select/drop, scoped paste event and Clipboard API button; deferred uploads on save, failure blocks business command, successful uploads reused on retry; covers/attachments restored for drafts; explicit empty array clears only the new version; rejected revisions retain source IDs and historical files. Added RESUBMIT action using server-returned action and existing endpoint. Responsive themed file cards, document downloads, image preview; document MIME types accepted at both upload and binding boundaries. No schema, shared state, permissions grants, dependency, deployment or branch operation.
- Changed files: frontend/workbench/src/components/ContentApprovalDraft.tsx, ContentReviewAttachments.tsx (new); src/pages/MediaStudentsPage.tsx, ContentReviewBatchPage.tsx; src/services/materialApi.ts, contentReviewAttachments.ts/test.ts (new); src/styles/components/content-review-attachments.css (new); test/content-review-attachments.html, .tsx, -browser.mjs (new). ZSJOS service/content/ContentVersionService.java, service/contentreview/ContentReviewBatchService.java, service/file/BusinessFileDirectUploadService.java, ContentAttachmentTypes.java (new), tests for ContentVersionService and BusinessFileDirectUploadService. docs/api/content-review-batch.md; handoff/test_main.md. Existing unrelated worktree edits preserved.
- Verification: Workbench typecheck passed; 14 focused frontend tests passed (attachments, deferred upload, clipboard). Maven reactor focused test run passed 22 tests (version documents/ownership/history/clear, direct upload, controller permission); /tmp/content-attachments-java.log. Final production build passed to /tmp/zsjos-content-attachments-dist, /tmp/content-attachments-build-final.log; existing large-chunk advisory remains. Scoped git diff --check passed.
- Browser evidence: Real isolated Chromium at 1280x1000 and 390x844; synthetic transport only. Passed scoped paste/multi-file selection, no pre-save upload, failure prevention/retry/success reuse, empty arrays, actual draft editor removal and actual rejected editor source retention. /tmp/content-attachments-browser-final.log; desktop/mobile PNGs at /tmp/zsjos-content-attachments-desktop.png and /tmp/zsjos-content-attachments-mobile.png inspected with CJK font loaded from existing temporary font. Browser tests use generated paste events, not OS clipboard; clipboard permission/error behavior additionally covered by existing utility tests.
- Verification adjustments: Existing root-owned Maven/Vite output directories blocked ordinary execution, used existing sudo for local build/test processes. Browser editor check initially used an inaccurate mobile pointer position; switching to semantic button activation passed. Final revision source retrieval fixed before final browser/build.
- Dependencies/integration: None; no new npm/Maven packages, no commits or publication. Existing BPM attachment payload unchanged; React attachment projection supports non-image download. No new Vue submission consumer.
- Remaining risks: No authenticated shared test/COS end-to-end upload, deployed API or cross-tenant live requests; no deployment/restart authorized or performed. Existing production-material auto-collection maps deliverables to a video-only field and may reject mixed image/document review attachments when collection is selected; preserved that separate contract, documented limitation instead of changing administrator templates or dropping files. Normal review without collection is unaffected.

## Registration — test-order-reviewer-dept-scope — 2026-09-21

- Owner: Codex /root; environment: test; branch/worktree: main / /opt/zsjos; HEAD/base: 050f43dd0dcc996ce468efafa4715870264dafd6.
- Goal: Fix order approval reviewers resolving empty for SELF-scoped sales when reviewers live in child departments. Preserve the existing uncommitted collection-overload annotation.
- Ownership: backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/dept/DeptApiImpl.java; corresponding api/dept/DeptApiImplTest.java; docs/architecture/data-and-permission-flow.md (order approval section); handoff/test_main.md append only.
- Non-goals: Permission grants, tenant bypass, database changes, frontend changes, other workstreams, deployment/restart without explicit authorization. Target integration/order/dependencies: None; existing Spring/JUnit/Mockito facilities only.
- Verification: Actual Spring advisor regression on both overloads, context restoration on success/failure and tenant-context preservation; compile focused source/tests against current runtime dependencies; related order reviewer tests. Root-owned Maven target must not be overwritten.

## Delivery Entry — 2026-09-21 12:07 +08:00 — test-order-reviewer-dept-scope

- Owner/environment/branch/worktree/HEAD: Codex /root / test / main / /opt/zsjos / 050f43dd0dcc996ce468efafa4715870264dafd6 unchanged.
- User goal: Fix sales order submission incorrectly reporting missing registration/finance approvers.
- Result: DeptApiImpl explicitly overrides and annotates getChildDeptList(Long) with DataPermission(enable=false). Interface default-method self-invocation previously bypassed the annotated collection overload, leaving child-department lookup under sales SELF scope; both configured centers have their users in child departments. Existing uncommitted collection-overload annotation preserved. Tenant/logical-delete behavior and sales business permission scope unchanged.
- Files: DeptApiImpl.java; DeptApiImplTest.java (actual Spring ProxyFactory/advisor regression); docs/architecture/data-and-permission-flow.md; handoff/test_main.md append only.
- Verification: Focused javac compilation against current deployed dependency jars and installed JUnit/Mockito succeeded. JUnit Platform ran 7 tests (2 department API + 5 existing order permission tests), all passed. New regression executed against unmodified deployed DeptApiImpl fails expected child [1031] vs empty, and passes with patched class. Covers both overloads, caller context restoration after success/exception and unchanged tenant context; related tests cover disabled reviewer exclusion and order scope denial. Logs/executor: /tmp/zsjos-order-scope-tests.log, /tmp/zsjos-order-scope-diagnosis/run-tests.py, regression-before.log. Scoped diff check passed. During test refinement explicit Collection<Long> matcher type resolved Java overload ambiguity; final source compiled and passed.
- Build scope: No full Maven reactor/package/startup performed; existing target is root-owned. Focused source and test compilation used /tmp outputs without modifying dependencies or permissions. No frontend changes or public API contract changes; shared internal department API behavior tested at its actual proxy boundary.
- Integration/remaining: Not deployed; current test service still uses old class. Backend publication and restart of testos test service require explicit authorization under root AGENTS.md section 4. Deployment must use the repository's normal packaging/release flow and verify a sales submission afterward; actual order creation was not performed as a diagnostic test. No database, grants, branch, commit, push, service or deployment operations performed.

## Registration — test-user-validation-scope — 2026-09-21 15:13:21 +08:00

- Owner: Codex /root; environment test; branch main; worktree /opt/zsjos; base/HEAD e2584ce2879dd11ebd18c6f5450ec9353c80d060.
- Goal: Fix assigned operator account creation by exempting explicit System user validity checks from caller department/self data scope.
- Non-goals: Role grants, database writes, deployment/restart, dependencies, Git branch/commit/publication operations.
- Ownership: System AdminUserApiImpl.java, AdminUserApi.java and AdminUserApiImplTest.java; docs/architecture/data-and-permission-flow.md; handoff/test_main.md.
- Dependencies: Existing DataPermission advisor and tenant isolation; single-user default delegation requires explicit intercepted entry. Target branch/integration order: None.
- Verification: Spring proxy tests for single/batch scope exemption, unchanged tenant context and exception/context restoration; existing user validity tests; affected media account authorization tests when available; frontend consumer contract inspection; scoped diff checks. Live deployment/browser acceptance is outside authorized scope.

## Delivery Entry — 2026-09-21 15:17:04 +08:00 — test-user-validation-scope

- Owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged e2584ce2879dd11ebd18c6f5450ec9353c80d060; registration above applies.
- User goal: Implement confirmed fix for assigned operator account creation failing with user-not-found under self-only data scope.
- Decisions/result: Added DataPermission(enable=false) to batch validity API and explicit single-user override; interface default self-invocation must not bypass the advisor. Tenant and logical deletion remain independent filters; service still rejects missing/disabled users. Existing feature/object/assignment authorization remains unchanged.
- Changed files: System api/user/AdminUserApi.java, AdminUserApiImpl.java, api/user/AdminUserApiImplTest.java; docs/architecture/data-and-permission-flow.md; handoff/test_main.md.
- Verification: sudo -n mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=AdminUserApiImplTest,AdminUserServiceImplTest,MediaAccountServiceTest,MediaAccountControllerPermissionTest,StudentServiceObjectPermissionProviderTest -Dsurefire.failIfNoSpecifiedTests=false test: BUILD SUCCESS, 68 tests passed, zero failures/errors/skips. Log /tmp/zsjos-user-validation-test.log. Real Spring class proxy verifies both entries disable data scope, retain tenant context, restore caller scope and propagate invalid-user errors. Existing tests cover actual service missing/disabled cases and assigned/unassigned account creation. git diff --check passed. Initial non-sudo build was blocked by root-owned target output; rerun succeeded using existing repository practice.
- Consumer/dependency impact: Reviewed shared callers in ZSJOS, BPM, PMS, HRM, FMS, IM and Mall; explicit validity API no longer implicitly checks caller visibility. Workbench creation request unchanged; Admin has account configuration/calendar consumers but no direct media-account/create source consumer. No HTTP payload/menu/authentication contract changes or frontend edits. No new dependencies, database/role changes, deployment/restart or Git operations. Integration: None.
- Remaining work/limits: Running service still uses previously deployed build. Authenticated browser/live request and live cross-tenant/database deletion checks not performed; tenant context retention and independent tenant interceptor inspected, not claimed as live isolation acceptance. Deployment and runtime acceptance require separately authorized service operation.

## Registration — test-withdrawal-batch-payout — 2026-09-21T16:28:56+08:00

- Owner/environment: Codex /root / test; branch main; worktree /opt/zsjos; base/HEAD e2584ce2879dd11ebd18c6f5450ec9353c80d060.
- Goal: Multi-select batch payout registration; remove bank transaction/proof inputs; optional shared paidAt and remark in both clients.
- Non-goals: Database writes/schema changes, history cleanup, permission grants, deployment/restart, dependency or Git operations.
- Ownership: ZSJOS withdrawal controller/VO, service and focused withdrawal tests; frontend/admin withdrawal view/API and focused browser harness if needed; frontend/workbench ManagementPages.tsx, managementApi.ts and focused withdrawal browser/API tests; docs/api/withdrawal-and-offline-payout.md and directly affected withdrawal design contract; handoff/test_main.md append only. Existing unrelated dirty System files and architecture document preserved.
- Decisions/dependencies: Existing payout permission and per-object service checks; atomic batch with stable locking order; optional paidAt stays null when omitted. Read-only test DB confirms 45 paid/11 approved/3 rejected rows, no bank/proof values; all required columns nullable, so no SQL adjustment needed. Existing histories retained. Existing Maven, Vue/pnpm, React/npm and browser facilities only. Target branch/integration order: None.
- Verification: Focused Java validation/service/authorization/transaction tests, both frontend static checks and real browser interactions with isolated synthetic transport, scoped diffs and API documentation. Shared test business writes and deployment are excluded.

## Delivery Entry — 2026-09-21T16:48:26+08:00 — test-withdrawal-batch-payout

- Owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged e2584ce2879dd11ebd18c6f5450ec9353c80d060; registration above applies.
- User goal/result: Added current-page multi-select batch payout to Vue Admin and React Workbench; only approved records selectable. Removed bank transaction/proof fields from single and batch payout forms and write VO. Both accept optional paidAt and remark; blank time persists null, shared values apply to the whole batch. Finance detail displays time/remark. Selection clears on list reload; frozen dialog selection, busy submission/close protection, preserved form on failures, responsive Admin payout dialog.
- Backend decisions: PUT /zsjos/withdrawal/batch-payout reuses configured zsjos:withdrawal:payout; validated 1–100 positive IDs, deduplicated and sorted. All objects checked before writes; calls the existing proxied single-record @ZsjosPermission boundary inside an outer transaction. Withdrawal/cashback/explicit audit/outbox rollback together. Generic request attempt audit remains independently recorded. Existing paid rows, legacy bank/proof read/upload paths, schema, grants and histories unchanged. No new dependencies.
- Database evidence: Read-only UTF-8 MySQL inspection via configured test connection and existing container: 45 paid, 11 approved, 3 rejected; bank transaction/proof populated counts all zero; 45 existing paidAt values. paid_at, payout_remark, transaction/proof columns all nullable in database and V052. No SQL changes, database writes, cleanup or migration needed; compatibility data is preserved.
- Changed files: ZSJOS controller/admin/withdrawal/WithdrawalController.java; vo/WithdrawalPayoutReqVO.java and new WithdrawalBatchPayoutReqVO.java; service/withdrawal/WithdrawalServiceImpl.java and new WithdrawalBatchPayoutService.java; corresponding WithdrawalServiceImplTest.java and new WithdrawalBatchPayoutServiceTest.java. Admin src/views/zsjos/withdrawal/index.vue, src/api/zsjos/withdrawal/index.ts, test/withdrawal-payout.html/ts. Workbench src/pages/ManagementPages.tsx, src/services/managementApi.ts, test/withdrawal-payout.html/tsx and withdrawal-payout-browser.mjs. docs/api/withdrawal-and-offline-payout.md, docs/superpowers/specs/2026-09-11-cashback-withdrawal-admin-design.md; handoff/test_main.md append only. Prior unrelated System/architecture edits preserved.
- Verification: sudo -n mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=WithdrawalServiceImplTest,WithdrawalBatchPayoutServiceTest -Dsurefire.failIfNoSpecifiedTests=false test passed 15 tests, /tmp/zsjos-withdrawal-java.log. Real Spring transaction and permission proxies plus isolated H2 verify shared fields, no-field single/batch, denied/mixed objects, missing/cross-tenant targets, later state/event failure rollback, duplicate IDs, validation and JSON contract; DAO SQL represented by scoped test adapters, not a live MySQL mutation. Existing ordinary/partner finance-field redaction tests pass.
- Frontend verification: Workbench npm run typecheck and 4 managementAccess tests passed; Admin pnpm ts:check and scoped ESLint/Prettier passed. /tmp/zsjos-withdrawal-{workbench-types,workbench-tests,admin-types,admin-eslint}.log. Real isolated Chromium on actual pages and typed HTTP clients with synthetic adapters passed selection/state disabling, common time/remark payload, actionable conflict and retry, busy controls, empty single request, selection clearing, load error/retry/empty and no-payout permission. Desktop 1440x1000/mobile 390x844 screenshots inspected; final Admin responsive layout and viewport bounds verified. Logs /tmp/zsjos-withdrawal-browser.log and /tmp/zsjos-withdrawal-admin-browser-final.log; screenshots /tmp/zsjos-withdrawal-{workbench,admin}-{desktop,mobile}.png. Scoped diff check passed.
- Verification adjustments: Test login fixture required MockHttpServletRequest; corrected before final passing Java run. Browser harness corrected actual Ant 6 modal selector, real mouse clicks/react flush ordering and text whitespace handling; added Admin UnoCSS fixture import. Existing root-owned Vite outputs required sudo for Vitest; no dependency install. Temporary loopback Vite processes stopped after checks.
- Dependency/integration impact: Both ADMIN consumers updated; no H5 payout writer. No build/bundling/asset/route changes; production frontend build not applicable to this scoped UI/API edit. No deployment, shared service restart, account permission change, commit or publication.
- Remaining work/limits: Source is verified but not deployed; authenticated live endpoint/browser and live MySQL concurrent/tenant acceptance not performed because shared business writes/service rollout are outside authorization. Date-bounded payout reports exclude new undated payouts; unbounded paid totals include them, documented. Browser fixture also exposed existing Workbench withdrawal amount/date render callbacks receiving formatted ProTable nodes (amount renders NaN); this unrelated pre-existing list rendering issue was not changed. Deployment/runtime acceptance requires separately authorized test service release.

## Registration — test-positioning-revision-confirm — 2026-09-21T16:57:13+08:00

- Owner/environment: Codex /root / test; branch main; worktree /opt/zsjos; base/HEAD e2584ce2879dd11ebd18c6f5450ec9353c80d060.
- Goal: Prevent accidental positioning-card revision by confirming before the state-changing request at both Workbench revision entry points.
- Non-goals: Backend/state-machine changes, database writes, permissions, dependencies, deployment/restart, Git operations; preserve existing unrelated changes.
- Ownership: frontend/workbench/src/components/ServicePositioningCard.tsx; src/pages/MediaFeaturePage.tsx; test/positioning-revision-confirm.html, .tsx and -browser.mjs; docs/api/positioning-service-application.md; handoff/test_main.md append only.
- Dependencies: Existing Ant Design confirmation and positioning revision API. Target branch/integration order: None.
- Verification: Workbench typecheck; isolated real Chromium checks of cancel/no writes, confirm/single request, failure and draft continuation at affected entry points; desktop/mobile dialog inspection; scoped diff check.

## Delivery Entry — 2026-09-21T17:01:06+08:00 — test-positioning-revision-confirm

- Owner/environment/branch/worktree: Codex /root / test / main / /opt/zsjos; HEAD unchanged e2584ce2879dd11ebd18c6f5450ec9353c80d060; registration above applies.
- User goal: Add secondary confirmation before a director changes a completed positioning card, preventing accidental transition back to draft.
- Decisions/result: Both ServicePositioningCard revision and MediaFeaturePage positioning-list revision now show Ant Design confirmation before start-revision. Explains draft transition, renewed review/student confirmation/evidence and retained historical/applied versions; cancel receives initial focus. Cancel sends no write or editor callback; explicit confirmation uses existing API/state handling. Draft continuation remains direct. Backend, permissions and shared data unchanged.
- Changed files: frontend/workbench/src/components/ServicePositioningCard.tsx; frontend/workbench/src/pages/MediaFeaturePage.tsx; frontend/workbench/test/positioning-revision-confirm.html, positioning-revision-confirm.tsx, positioning-revision-confirm-browser.mjs (new); docs/api/positioning-service-application.md; handoff/test_main.md append only. Prior unrelated edits preserved.
- Verification: Workbench npm run typecheck passed; existing positioningCardApi.test.ts passed all 5 tests. Real isolated Chromium with actual React components and synthetic HTTP adapter passed both entry points at 1440x1000 and 390x844: no writes/editor on open or cancel, one write on confirm, failure message/no editor, server-action absence, unchanged draft continuation, no runtime exceptions. Screenshots for both entry points/widths inspected; modal stays within viewport and Chinese text renders correctly. Evidence: /tmp/zsjos-positioning-revision-browser.log and /tmp/zsjos-positioning-revision-{overview,list}-{desktop,mobile}.png. git diff --check passed.
- Verification adjustments: Corrected initial command working directory; browser draft-navigation assertion gained a null-body guard for document load timing. Final browser rerun passed. Existing root-owned Vite/Vitest output required sudo for local verification processes; temporary loopback Vite process stopped after checks.
- Dependency/integration impact: None; no new dependencies, branches, commits, deployment, shared service restart or database writes. UI-only interaction does not change a shared backend contract or require an Admin/H5 change. No build/asset/route changes; production build not applicable.
- Remaining work/limits: Source verified, not deployed. Authenticated live test-site acceptance remains unverified; isolated browser checks do not claim live business mutation evidence. Runtime rollout requires separately authorized deployment.

## Registration — test-area-other-first — 2026-09-21T17:48:31+08:00

- Owner/environment: Codex /root / test; branch main; worktree /opt/zsjos; base/HEAD e2584ce2879dd11ebd18c6f5450ec9353c80d060.
- Goal: Put configured OTHER area nodes first among siblings in management lists and enabled trees. Non-goals: database changes, deployment/restart, dependencies, permissions and unrelated existing edits.
- Ownership: backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/ip/AreaServiceImpl.java; corresponding src/test AreaServiceImplTest.java; Administrative area flow section of docs/architecture/data-and-permission-flow.md; handoff/test_main.md append only. Prior completed work and existing edits preserved.
- Dependencies: Existing System area APIs and test facilities. Target branch/integration order: None.
- Verification: Focused System area database tests and compilation, Admin tree conversion and Workbench option order contract checks; scoped diff. Live runtime acceptance requires separately authorized deployment.

## Delivery Entry — 2026-09-21T17:50:48+08:00 — test-area-other-first

- Owner/context: Codex /root; environment test; branch main; worktree /opt/zsjos; HEAD unchanged e2584ce2879dd11ebd18c6f5450ec9353c80d060; registration above applies.
- User goal/result: Confirmed previous OTHER-last policy; current explicit request supersedes it. System management lists (including filtered lists) and enabled trees now put configured selection_code=OTHER first among siblings at every level. Ordinary sort and ID ordering, enabled filtering and historical reads retained. Versioned list/tree cache keys avoid reusing old ordering after deployment.
- Changed files: AreaServiceImpl.java; AreaServiceImplTest.java; Administrative area flow in docs/architecture/data-and-permission-flow.md; handoff/test_main.md append only. Existing unrelated changes preserved. No database/SQL changes.
- Verification: sudo -n mvn -f backend/pom.xml -pl yudao-module-system -am -Dtest=AreaServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test passed all 10 tests and reactor compilation; log /tmp/zsjos-area-other-first-java.log. Covers province/city/district OTHER-first despite maximum stored sort, management full/filtered ordering, normal sort/ID ties and existing disabled-subtree/historical rules. Workbench npm test -- src/services/area.test.ts passed 6 tests. Existing TypeScript compiler executed actual Admin listToTree and Workbench buildLeadAreaOptions with synthetic server-order data; both preserve province/city OTHER-first order. Admin tree controls and H5 AreaPicker inspected: consume server arrays without reordering. Scoped git diff --check passed.
- Dependency/integration impact: Shared System list/tree ordering changes all consumers together; no dependency, authorization, payload or frontend implementation change. No commit, publication, deployment or shared service restart. Target branch/integration order: None.
- Remaining work/limits: Source verified, not deployed. Live HTTP and browser acceptance of the changed server response unverified because the shared test service has not been redeployed; frontend conversion checks do not claim live runtime evidence. No rendered layout/assets/routes changed.

## Registration — test-delivery-class-direct-skus — 2026-09-21T17:51:38+08:00

- Owner/environment: Codex /root / test; branch main; worktree /opt/zsjos; base/HEAD e2584ce2879dd11ebd18c6f5450ec9353c80d060.
- Goal: Confirmed direct multi-SKU class creation, selected-SKU exam matching, explicit nonempty selection, snapshot-safe editing and existing documented product-scope lock.
- Non-goals: Schema/data/permission changes, new dependencies, deployment/shared service restart, Git operations, unrelated dirty files.
- Ownership: ZSJOS deliveryclass controller/VO/service and focused tests, ZsjosErrorCodeConstants.java; frontend/workbench DeliveryClassPage.tsx, services/api.ts, delivery-class tests and browser fixtures; frontend/admin class-management.vue, api/zsjos/deliveryClass/index.ts and browser fixtures; docs/api/delivery-class-management.md; handoff/test_main.md append only.
- Dependencies: Existing product catalog, permission boundaries, Maven, React/npm, Vue/pnpm and Chromium facilities. Target branch/integration order: None.
- Decisions: Existing API documentation controls the product/SKU lock after any service relation; unchanged selections retain snapshots. No database migration needed.
- Verification: Focused Java tests for scope/query/save/snapshot/lock, both frontend type checks and isolated real-browser creation/edit/error/race flows at desktop/mobile widths, scoped diff checks. No shared business writes or rollout.
