# ZSJOS 生产发布与 systemd 启动

## 一键完整发布

生产发布统一由仓库脚本执行，发布用户需要能够无交互运行 `sudo systemctl`：

```bash
cd /opt/zsjos
ENV_FILE=/opt/zsjos-runtime/.env.production \
  bash script/shell/deploy-production.sh deploy
```

`deploy` 的顺序是：校验环境和工具、构建后端及三个前端、用当前工作树的 SQL
重建 `db-migrator` 镜像、执行数据库 plan/migrate/verify、安装版本 release、停止并启动
`zsjos-backend.service`，最后检查健康接口。同一次完整发布只构建一次迁移镜像，因此新加入的
迁移脚本不会因复用旧镜像而被遗漏。

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

构建、迁移、安装、遗留 PID 清理或健康检查失败时，发布命令会失败并保持服务停止；旧 release
和 `previous-release` 不会被删除，也不会自动回切。

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
