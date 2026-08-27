# 媒体大屏公共 API

## 1. 适用范围

本文档面向媒体大屏前端、联调人员和后端开发人员，描述只读公共接口、数据口径、访问控制和前端接入方式。部署与白名单配置见 `docs/operations/media-screen-deployment.md`。

媒体大屏接口不使用登录令牌，但并非匿名开放：后端同时校验功能开关、HTTP 方法、`tenantId` 查询参数以及客户端 IP 与租户的服务端白名单。前端不得直连数据库，也不得在页面中保存数据库凭据。

## 2. 基础契约

- 基础路径：`/public-api/zsjos/media-screen`
- HTTP 方法：仅 `GET`
- 认证：不携带 `Authorization`
- 租户：每次请求必须携带正整数查询参数 `tenantId`
- 成功响应：HTTP 200，响应体使用 `CommonResult<T>`，其中 `code=0`
- 时间：`LocalDateTime` 字段按服务器全局契约返回 epoch milliseconds；`LocalDate` 返回 `yyyy-MM-dd`
- 缓存：实时统计默认缓存 15 秒，维护状态默认缓存 5 秒；具体值由后端运行配置决定

公共接口路径由 `controller.pub` 包映射到 `/public-api`，Controller 中只声明 `/zsjos/media-screen`，前端不能重复或省略公共前缀。

## 3. 实时统计

```http
GET /public-api/zsjos/media-screen/stats?tenantId=1&includePartTimers=0
```

查询参数：

| 参数 | 必填 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `tenantId` | 是 | Long | 无 | 服务端白名单中配置的租户 ID |
| `includePartTimers` | 否 | Integer | `0` | 仅允许 `0` 或 `1`；控制汇总、趋势、14 日序列、今日之星、昨日冠军和兼职陪跑卡片是否计入兼职贡献 |

示例响应：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "tenantId": 1,
    "updatedAt": 1787630400000,
    "refreshIntervalSeconds": 5,
    "partTimeIncluded": false,
    "summary": { "today": 18, "week": 52, "monthTotal": 128, "monthEffective": 46 },
    "departments": [{
      "departmentId": 1011,
      "name": "新媒体一部",
      "subtitle": "主管 示例主管",
      "metrics": { "today": 18, "week": 52, "monthTotal": 128, "monthEffective": 46 },
      "members": [{ "userId": 31, "name": "示例成员", "today": 18, "week": 52, "monthTotal": 128, "monthEffective": 46 }]
    }],
    "partTimeCompanionDepartment": null,
    "todayStar": { "name": "示例成员", "deptName": "新媒体一部", "today": 18, "yesterday": 12, "rankToday": 1, "rankYesterday": 2, "includesPartTime": false },
    "yesterdayChampion": { "name": "昨日成员", "deptName": "新媒体二部", "count": 16, "includesPartTime": false },
    "trend": { "today": [0, 2, 5], "yesterday": [1, 3, 4], "stepMinutes": 10 },
    "series": { "submitted": [8, 11, 18], "valid": [3, 4, 6] }
  }
}
```

当前主统计口径：

- 只返回 `yudao.media-screen.new-media.department-ids` 明确配置的部门，顺序与配置一致。
- 所有统计只读取 Lead 首次计数时冻结的 `countedAt`、规范提供方和贡献员工/部门/主管快照，不按当前组织或 Partner 归属反推历史。
- 新媒体专员、主管和销售明确选择的 System 提供方计为 `direct`；Partner 在提交时已有员工归属快照时计为 `part_time`，两类贡献互斥。
- 销售自拓未选择提供方时不计入新媒体大屏；重复客资重新激活不再次计数，也不改写首次归属和 `countedAt`。
- 汇总保留停用员工的历史贡献，实时成员榜过滤当前停用员工；实时 Partner 明细过滤当前停用 Partner，但员工兼职汇总保留其历史贡献。
- 今日之星和昨日冠军按员工 ID 合并直属与兼职贡献；新媒体主管与普通成员使用相同上榜规则。
- `monthEffective` 使用 Lead 状态 `valid`、`converted`、`won`。
- `summary` 是当前开关口径下的直属部门与可选兼职合计；配置部门和在职成员无数据时仍返回零值对象。
- `trend.today/yesterday` 是北京时间零点起每 10 分钟累计序列；`series` 是近 14 个自然日提交量和有效量。
- `includePartTimers=0` 时不返回兼职陪跑卡片且所有全局指标排除兼职；为 `1` 时所有相关指标统一计入兼职。

新客户端使用嵌套结构。旧平铺字段仅为兼容投影，不再定义主业务口径。

## 4. 历史统计

```http
GET /public-api/zsjos/media-screen/history?tenantId=1&date=2026-08-24&includePartTimers=0
```

| 参数 | 必填 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `tenantId` | 是 | Long | 无 | 服务端白名单中配置的租户 ID |
| `date` | 否 | `yyyy-MM-dd` | 当天 | 不得晚于当天，不得超过配置的最大历史天数 |
| `includePartTimers` | 否 | Integer | `0` | 仅允许 `0` 或 `1` |

V141 建立持久化历史快照源。指定日期无快照时返回 `available=false`，不会用当前数据伪造历史数据：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "tenantId": 1,
    "generatedAt": 1787630400000,
    "totalLeads": 0,
    "departmentRanking": [],
    "memberRanking": [],
    "trend": [],
    "available": false,
    "snapshotDate": "2026-08-24",
    "source": "persisted_snapshot_v2",
    "historySnapshot": {
      "available": false,
      "snapshotDate": "2026-08-24",
      "totalLeads": 0
    }
  }
}
```

有快照时返回 `available=true`、`source=persisted_snapshot_v2`，结构与实时接口的 `summary` 和 `departments` 对齐。
每个快照行直接冻结目标日截止时的 `today/week/monthTotal/monthEffective` 累计值、成员启用状态和 Partner 明细，
历史读取不再查询当前 Lead、用户、部门或 Partner 状态。`includePartTimers` 对历史汇总和榜单使用与实时接口相同的口径。
前端应在 `available=false` 时显示“该日期暂无历史快照”。

## 5. 维护状态

```http
GET /public-api/zsjos/media-screen/maintenance/status?tenantId=1
```

示例响应：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "tenantId": 1,
    "maintenanceEnabled": false,
    "checkedAt": "2026-08-25T16:00:00+08:00"
  }
}
```

`maintenanceEnabled=true` 表示系统处于维护模式。大屏可停止自动刷新并展示维护状态，但不得自行推断恢复时间。

## 6. 错误响应

访问过滤器错误使用 JSON `CommonResult`，HTTP 状态和响应体 `code` 保持一致：

| HTTP / `code` | 场景 | `msg` |
| --- | --- | --- |
| 400 / 400 | `tenantId` 缺失、非数字或非正整数 | `tenantId 必须是正整数查询参数` |
| 403 | 客户端 IP 与租户白名单不匹配 | `当前客户端无权访问该租户的媒体大屏` |
| 405 | 使用了 GET 以外的方法 | `媒体大屏仅支持 GET 请求` |
| 503 | 媒体大屏功能开关关闭 | `媒体大屏服务未开启` |

请求进入 Controller 后遵循项目通用响应契约：参数校验、`includePartTimers` 非 0/1 或历史日期越界通常返回 HTTP 200、响应体 `code=400`；数据库、System API 或其他未恢复异常通常返回 HTTP 200、响应体 `code=500`。调用方必须同时检查 HTTP 状态和 `CommonResult.code`。

示例：

```json
{
  "code": 503,
  "msg": "媒体大屏服务未开启",
  "data": null
}
```

前端必须分别处理 400、403、503 和可重试的 5xx，不能将它们统一显示成“网络错误”或“账号未登录”。

## 7. 前端接入

开发环境建议由 Vite 转发 `/public-api`，避免浏览器跨域：

```ts
// vite.config.ts
export default defineConfig({
  server: {
    port: 3009,
    proxy: {
      '/public-api': {
        target: 'http://<BACKEND_HOST>:48080',
        changeOrigin: true,
      },
    },
  },
})
```

调用示例：

```ts
type CommonResult<T> = { code: number; msg: string; data: T | null }

export async function getMediaScreenStats(tenantId: number) {
  const query = new URLSearchParams({
    tenantId: String(tenantId),
    includePartTimers: '0',
  })
  const response = await fetch(`/public-api/zsjos/media-screen/stats?${query}`)
  const result = (await response.json()) as CommonResult<MediaScreenStats>
  if (!response.ok || result.code !== 0 || !result.data) {
    throw new Error(result.msg || `媒体大屏请求失败 (${response.status})`)
  }
  return result.data
}
```

`tenantId` 必须位于 URL 查询参数中；只发送常规 `tenant-id` 请求头不能满足该公共接口的过滤器契约。页面应实现 loading、success、empty、error、retry、403 和 503 状态，并在组件卸载或切换租户时取消过期请求。

## 8. 后端开发位置

- Controller：`yudao-module-zsjos/.../controller/pub/mediascreen/MediaScreenController.java`
- 响应 VO：`yudao-module-zsjos/.../controller/pub/mediascreen/vo/MediaScreenRespVO.java`
- 聚合服务：`yudao-module-zsjos/.../service/mediascreen/MediaScreenQueryService.java`
- 访问过滤器和配置：`yudao-module-zsjos/.../framework/mediascreen/`
- Lead 统计 SQL：`yudao-module-zsjos/.../dal/mysql/lead/LeadMapper.java`
- 历史快照：`yudao-module-zsjos/.../dal/mysql/mediascreen/` 和 `service/mediascreen/MediaScreenSnapshotScheduler.java`
- 运行配置：`yudao-server/src/main/resources/application.yaml`

新增字段时必须同步 VO、本文档和前端类型。改变统计口径时必须说明旧、新口径及缓存影响；历史接口只有目标日期存在权威冻结快照时才能返回 `available=true`。
