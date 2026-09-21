# ZSJOS 生产发布与 systemd 启动

## 一键完整发布

生产发布统一由仓库脚本执行，发布用户需要能够无交互运行 `sudo systemctl`：

```bash
cd /opt/zsjos
ENV_FILE=/opt/zsjos-runtime/.env.production \
  bash script/shell/deploy-production.sh deploy
```

`deploy` 当前顺序是：停止旧后端、校验环境和工具、构建后端及五份前端产物
（Admin、admin-embed、Workbench、H5、media-screen）、用当前工作树的 SQL 重建
`db-migrator` 镜像、执行数据库 migrate、安装 release、启动后端，健康检查通过后清理历史 release。
当前脚本未自动调用 db-plan/db-verify，需要按发布要求单独执行。同一次完整发布只构建一次迁移镜像。

Admin 与 admin-embed 共用 Vue 源码和一次 pnpm 依赖安装，分别生成 `dist-prod` 与
`dist-embed`；嵌入包固定使用 `/admin-embed/` 资源路径。媒体大屏在
`frontend/media-screen` 使用 `npm ci` 和 `npm run build` 生成 `dist`。
安装阶段将本次产物复制到 release 的 `admin-embed/` 与 `media-screen/`，不再复制旧版本目录。
构建失败或缺少入口文件时不切换 current。构建日志分别为 `build-admin-embed.log` 与
`build-media-screen.log`，位于配置的日志目录。

媒体大屏构建必须由部署环境提供 `VITE_MEDIA_SCREEN_TENANT_ID`；可配置
`VITE_MEDIA_SCREEN_API_BASE_URL`（默认同源）和 `VITE_MEDIA_SCREEN_API_PREFIX`
（默认 `/public-api/zsjos/media-screen`），构建强制关闭 mock。
`ZSJOS_FRONTEND_MEDIA_SCREEN_DIR` 可覆盖源码目录。API 的租户/IP 授权仍由后端校验。
`start` / `restart` 只启停已有后端；要更新这些前端产物，使用 `build` / `deploy`。

脚本不会在生产流程中再用 `nohup` 启动后端。`build` 只构建并生成产物，不会安装 release、
执行迁移或重启服务；不要手工复制 JAR，release 必须由 `deploy` 的安装阶段生成。

独立执行 `db-plan`、`db-migrate` 或 `db-verify` 时，脚本同样会先重建迁移镜像。生产变更前可先
运行 `db-plan` 检查待执行版本；该命令会更新本机迁移镜像，但不会修改数据库。

## 运行时约定

生产环境文件至少应明确：

```dotenv
SERVER_PORT=48080
ZSJOS_SYSTEMD_SERVICE=zsjos-backend.service
ZSJOS_RELEASES_DIR=/opt/zsjos-runtime/releases
ZSJOS_PID_FILE=/opt/zsjos/zsjos-server.pid
```

`APP_VERSION` 和 `ZSJOS_DB_RELEASE_VERSION` 默认不要固化在环境文件中。脚本首次加载环境时会按
`YYYY.MM.DD-HHMMSS-<Git短提交号>` 生成本次命令唯一的 `APP_VERSION`，并让
`ZSJOS_DB_RELEASE_VERSION` 使用相同值；同一次 `deploy` 内后续构建、迁移、安装和校验都会复用
该值。确需重放或指定版本时，可以在专用环境文件中显式配置，但目标 release 目录必须确认不会
覆盖已有版本。

systemd 单元由服务器运维维护，不由仓库脚本创建。发布前应确认：

- `ExecStart` 使用 `/opt/zsjos-runtime/releases/current/yudao-server.jar`；
- `EnvironmentFile` 加载 `/opt/zsjos-runtime/.env.production`；
- 服务用户可以读取 release、配置和日志目录；
- `WorkingDirectory`、Java 参数和日志路径与生产环境一致；
- 停止服务后不会因 `Restart=always` 立即重新占用 48080。

脚本会检查 systemd 单元是否引用当前 release 路径。启动后还会核对 systemd `MainPID`、48080
监听进程和健康接口，避免 systemd 与遗留 `nohup` 实例并存。

## 失败处理与回滚

构建、迁移、安装或启动失败，以及健康检查重试耗尽时，发布命令失败，不执行历史版本清理，
也不会自动回切。启动后健康检查失败不代表后端进程已停止，应检查服务状态。

### 历史版本保留

`deploy` 成功启动后等待健康接口返回 HTTP 200（最多 120 次，每次请求最多 10 秒，
失败后间隔 1 秒），然后自动保留两份 release：`current` 指向的本次版本和
`previous-release` 文件记录的上一版。按这两个引用保留，不按目录修改时间排序。
`start`、`restart` 和 `rollback` 不触发清理。

删除范围仅限 `ZSJOS_RELEASES_DIR` 下其他直接子目录，且目录须包含 `yudao-server.jar`
和 `admin/`、`workbench/`、`h5/` 才识别为历史 release。删除整个目录，包括目录内附带的
产物与日志；独立配置的日志目录、数据库备份目录和源码目录受保护，候选目录若包含这些路径，
则整次清理报错退出。普通文件（包括 `.sha256`）、符号链接和不能识别为 release 的目录保留，
因此这里的“两份”指两份已识别的发布版本，不承诺清空发布根目录中的所有其他内容。

清理前校验两个保留版本存在、不同且都是发布根目录的直接子目录，当前版本须与本次
`APP_VERSION` 一致。引用缺失、越界、保留版本不完整或候选目录为挂载点时，跳过删除并报错；
首次发布尚无上一版时也采用该保护行为。删除不跟随符号链接、不跨文件系统。
清理失败会使发布命令返回非零，但不会停止已启动的服务。

清理可重复执行，两个受保护版本始终保留；被删除的更早版本没有自动备份或恢复机制，
需要更长回滚历史时应在发布前归档。当前保留的上一版仍可用下面的 `rollback` 命令恢复应用。

```bash
ENV_FILE=/opt/zsjos-runtime/.env.production \
  bash script/shell/deploy-production.sh rollback
```

`rollback` 只切换应用 release 并重新交给 systemd，数据库不会回滚。回滚前必须确认旧应用与
当前数据库结构兼容。

## 权限与安全

建议为发布用户配置仅限本服务的 sudoers 规则，使以下命令可无交互执行：

- `systemctl stop zsjos-backend.service`
- `systemctl start zsjos-backend.service`
- `systemctl is-active/show/status zsjos-backend.service`

不要把密码、token 或完整敏感配置写入发布日志、文档或提交记录。
