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
| `includePartTimers` | 否 | Integer | `0` | `0` 不返回分部计时榜内容，`1` 返回；其他值为 400 |

示例响应：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "tenantId": 1,
    "generatedAt": 1787630400000,
    "totalLeads": 128,
    "departmentRanking": [
      { "name": "示例部门", "leadCount": 52, "rank": 1 }
    ],
    "memberRanking": [
      { "name": "示例成员", "leadCount": 21, "rank": 1 }
    ],
    "todayStar": { "name": "示例成员", "leadCount": 21, "rank": 1 },
    "partTimer": { "enabled": false, "items": [] },
    "trend": [
      { "date": "2026-08-25", "leadCount": 18 }
    ],
    "historySnapshot": null,
    "available": true,
    "snapshotDate": null,
    "source": null,
    "snapshotCreatedAt": null
  }
}
```

当前统计口径：

- `totalLeads`：当前租户全部未逻辑删除 Lead 数量。
- `departmentRanking`：按 Lead 的来源部门分组，无法解析的部门显示“未分配”。
- `memberRanking`：按非空 Lead 负责人分组，无法解析的用户显示“未知成员”。
- `todayStar`：当前成员榜第一项；没有成员数据时为 `null`。
- `partTimer.items`：`includePartTimers=1` 时当前与成员榜一致，否则为空数组。
- `trend`：最近 7 个自然日按 `submitted_at` 聚合；没有数据的日期当前不会自动补零。

## 4. 历史统计

```http
GET /public-api/zsjos/media-screen/history?tenantId=1&date=2026-08-24&includePartTimers=0
```

| 参数 | 必填 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `tenantId` | 是 | Long | 无 | 服务端白名单中配置的租户 ID |
| `date` | 否 | `yyyy-MM-dd` | 当天 | 不得晚于当天，不得超过配置的最大历史天数 |
| `includePartTimers` | 否 | Integer | `0` | 仅允许 `0` 或 `1` |

当前版本尚未建立持久化历史快照源，因此接口会明确返回 `available=false`，不会用当前数据伪造历史数据：

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
    "source": "persisted_snapshot",
    "historySnapshot": {
      "available": false,
      "snapshotDate": "2026-08-24",
      "totalLeads": 0
    }
  }
}
```

前端应显示“该日期暂无历史快照”，不能把全零结果当成真实历史统计。

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
- 运行配置：`yudao-server/src/main/resources/application.yaml`

新增字段时必须同步 VO、本文档和前端类型。改变统计口径时必须说明旧、新口径及缓存影响；历史接口只有在存在权威持久化快照后才能返回 `available=true`。
