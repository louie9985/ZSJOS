# 媒体大屏部署与配置

## 1. 部署边界

媒体大屏是无登录令牌的只读公共接口，因此默认关闭并采用租户与客户端 IP 绑定的白名单。仓库配置不得包含真实租户、真实客户端地址、账号、令牌或数据库凭据；实际值通过部署环境的外部 YAML、环境变量或配置中心提供。

本功能需要按顺序应用改造后的 V141 和 V143：V141 增加 Lead 规范提供方/贡献快照字段并建立 v2 每日快照表，
V143 只用原有 Partner 提交时员工快照补充可证明的历史归属。实时查询不修改 Lead；定时任务会在快照表写入前一天的冻结统计。
部署会更换后端制品并需要重启服务；对共享或生产环境执行迁移、启停或配置变更前必须另行确认目标实例、时间窗口和回滚责任人。

## 2. 配置契约

配置前缀固定为 `yudao.media-screen`：

```yaml
yudao:
  security:
    permit-all_urls:
      - /public-api/zsjos/media-screen/**
  media-screen:
    enabled: true
    trusted-proxies:
      - <REVERSE_PROXY_IP_OR_CIDR>
    clients:
      - tenant-id: <TENANT_ID>
        cidrs:
          - <CLIENT_IP_OR_CIDR>
    cache:
      stats-ttl-seconds: 15
      history-ttl-seconds: 60
      maintenance-ttl-seconds: 5
      refresh-interval-seconds: 5
      stale-if-error-seconds: 60
    limits:
      max-history-days: 366
    new-media:
      department-ids:
        - <NEW_MEDIA_DEPARTMENT_ID_1>
        - <NEW_MEDIA_DEPARTMENT_ID_2>
        - <NEW_MEDIA_DEPARTMENT_ID_3>
    snapshot:
      hour: 4
      minute: 0
      scan-delay-ms: 300000
```

约束：

- `enabled=false` 时所有媒体大屏请求返回 503。
- `enabled=true` 时必须至少配置一个 `clients` 条目，每个条目必须有正整数 `tenant-id` 和至少一个 CIDR；否则应用启动失败并输出明确配置错误。
- `trusted-proxies` 只填写由团队控制、会正确覆盖或追加转发头的反向代理地址。直连后端时使用空数组。
- 生产和共享环境不得使用 `0.0.0.0/0` 或 `::/0` 作为便捷白名单；应配置经过确认的最小客户端网段。
- 多个租户必须分别配置客户端地址，不能依赖前端传入的租户值获得访问权。
- `new-media.department-ids` 必须使用 System 中确认的新媒体部门 ID，列表顺序就是大屏部门顺序；禁止按名称、角色或岗位推断。
- 每日到北京时间 `snapshot.hour:snapshot.minute` 后，扫描任务幂等冻结前一天。首次上线不制造历史快照，从首次成功冻结日期开始可用。
- 快照冻结直属/兼职类型、部门、主管、成员、快照日账号状态、四项累计指标和 Partner 明细；历史查询不使用当前组织关系重算。

### 当前 local 开发例外

仓库的 `application-local.yaml` 当前按明确授权为租户 `1` 配置了 `0.0.0.0/0`，并固定本地新媒体部门
`1011/1012/1013`。该配置只随默认 `local` profile 生效，会使任何能够连接本机 `48080` 端口的 IPv4 客户端读取租户 `1` 的媒体大屏数据，不是部署模板，也不得直接复制到共享、测试或生产环境。部署到其他环境时必须通过外部配置替换租户、部门和 CIDR，并收敛到实际客户端或代理出口地址。

等价环境变量示例：

```text
YUDAO_MEDIA_SCREEN_ENABLED=true
YUDAO_MEDIA_SCREEN_TRUSTED_PROXIES_0=<REVERSE_PROXY_IP_OR_CIDR>
YUDAO_MEDIA_SCREEN_CLIENTS_0_TENANT_ID=<TENANT_ID>
YUDAO_MEDIA_SCREEN_CLIENTS_0_CIDRS_0=<CLIENT_IP_OR_CIDR>
```

多个代理、租户或 CIDR 继续递增数组下标。环境变量名称和值应由部署平台管理，不写入仓库。

## 3. 后端如何判断客户端 IP

请求没有经过受信任代理时，后端只使用 TCP 连接的 `remoteAddr`，忽略客户端自行提供的 `X-Forwarded-For` 和 `X-Real-IP`。

只有当 `remoteAddr` 命中 `trusted-proxies` 时，后端才按以下顺序解析：

1. `X-Forwarded-For` 的第一个地址；
2. `X-Real-IP`；
3. 代理连接地址。

Vite 开发代理属于一层代理。浏览器访问 3009、Vite 再请求 48080 时，如果 Vite 与后端不在同一进程，`clients[].cidrs` 至少要覆盖后端实际看到的 Vite 主机地址。不要仅根据浏览器电脑地址猜测白名单；应从受控访问日志或网络连接确认。

Nginx 示例：

```nginx
location /public-api/ {
    proxy_pass http://<BACKEND_HOST>:48080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

同时将 Nginx 的连接地址加入 `trusted-proxies`。如果代理链上任一节点不受控制，不应信任该节点传入的转发头。

## 4. 部署顺序

1. 确认目标后端制品包含 `MediaScreenController`、`MediaScreenAccessFilter` 和快照调度器，并确认配置前缀为 `yudao.media-screen`。
2. 在受控数据库按顺序应用 V141、V143。执行 V141 前确认旧 v1 快照表为空并备份旧 DDL；迁移只回填 Lead 中已有字段能证明的 ID 与计数时间，不补当前名称、组织或 Partner 关系，也不生成历史快照。
3. 确认目标租户 ID、三个新媒体部门 ID、前端或代理的实际出口 IP、代理链和所需 CIDR，执行双人复核。
4. 在部署平台写入外部配置，先保持 `enabled=false`。
5. 部署新后端制品。共享服务的停止、启动或重启必须在单独授权后执行。
6. 使用关闭状态请求验证接口返回 HTTP 503 且消息为“媒体大屏服务未开启”。
7. 配置完整白名单后设置 `enabled=true`，再次重启或刷新配置。当前实现不承诺动态刷新，默认按需要重启处理。
8. 分别执行正确租户/IP、错误租户、错误 IP、缺少参数以及维护状态检查。
9. 最后通过生产反向代理或 3009 开发代理验证，不能只在后端本机直连验证。

## 5. 验证命令

将占位符替换为经确认的值：

```powershell
$backendBase = 'http://<BACKEND_HOST>:48080'
$tenantId = '<TENANT_ID>'

Invoke-WebRequest -SkipHttpErrorCheck `
  -Uri "$backendBase/public-api/zsjos/media-screen/stats?tenantId=$tenantId&includePartTimers=0"

Invoke-WebRequest -SkipHttpErrorCheck `
  -Uri "$backendBase/public-api/zsjos/media-screen/maintenance/status?tenantId=$tenantId"
```

验收矩阵：

| 场景 | 预期 |
| --- | --- |
| 关闭开关 | HTTP 503，`code=503` |
| 缺少或非法 `tenantId` | HTTP 400，`code=400` |
| 错误租户或 IP | HTTP 403，`code=403` |
| POST 请求 | HTTP 405，`Allow: GET` |
| 正确租户和 IP | HTTP 200，`code=0`，`data.tenantId` 与请求一致 |
| 维护状态 | HTTP 200，返回明确的 `maintenanceEnabled` |

服务端构建检查：

```powershell
mvn -f backend/pom.xml -pl yudao-module-zsjos -am test
mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package
```

## 6. 故障定位

- 503：运行实例未加载正确配置，或 `enabled=false`。先核对最终生效配置，不修改前端路由。
- 403：开关已生效，但租户/IP 组合未命中。核对 URL 中的 `tenantId`、代理连接地址和受信任转发头。
- 400：前端没有把 `tenantId` 放在查询参数中，或参数格式错误。
- 404：运行制品不含公共 Controller、公共前缀未装配，或代理路径被改写。
- 502/连接失败：代理目标、端口、网络或后端进程不可用。
- HTTP 200 但 `code!=0`：按响应 `msg` 处理业务或依赖服务错误。
- 实时统计长时间不变化：先等待统计 TTL，再检查 Redis 和数据库；不要通过禁用缓存掩盖数据口径问题。

## 7. 回滚

优先将 `yudao.media-screen.enabled` 设为 `false` 并重启或刷新目标实例，接口会稳定返回 503。若必须回退制品，恢复上一份已验证 JAR 和对应外部配置。V141 为 forward-only：尚未产生快照时可通过后续迁移移除空表；已经产生快照后不得直接删除，必须先导出冻结历史并评审前向修复方案。Redis 中的 `zsjos:media-screen:*` 缓存可自然过期，不需要批量删除。
