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
