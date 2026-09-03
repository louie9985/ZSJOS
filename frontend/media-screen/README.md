# ZSJOS 新媒体客资数量大屏

独立 React + Vite 全屏前端，只读取 ZSJOS 免登录公共聚合接口，不依赖 PartTimeCRM 运行时，也不接入 ZSJOS 工作台菜单。

## 本地启动

在 `.env.local` 中至少填写后端分配的租户 ID：

```dotenv
VITE_MEDIA_SCREEN_TENANT_ID=实际租户ID
VITE_MEDIA_SCREEN_BACKEND_TARGET=http://127.0.0.1:48080
```

然后执行：

```powershell
cd D:\ZSJ-OS\frontend\media-screen
npm ci
npm run dev
```

访问 `http://127.0.0.1:3009/?includePartTimers=1`。开发服务器把同源 `/public-api` 代理到 `VITE_MEDIA_SCREEN_BACKEND_TARGET`。缺少有效的 `VITE_MEDIA_SCREEN_TENANT_ID` 时，页面显示配置错误且不会发出请求。

## 接口与 Mock

页面只访问：

- `GET /public-api/zsjos/media-screen/stats`
- `GET /public-api/zsjos/media-screen/history`
- `GET /public-api/zsjos/media-screen/maintenance/status`

请求不携带 `Authorization`。生产环境保持 `VITE_MEDIA_SCREEN_API_BASE_URL` 为空，由部署层将同源 `/public-api` 转发到 ZSJOS 后端。

开发演示数据默认关闭。只有 `npm run dev` 且 `VITE_MEDIA_SCREEN_ENABLE_MOCK=true` 时，才会补充后端明确未提供的模块；真实值、真实空数组、错误、403、维护状态和历史无快照不会被 Mock 覆盖。生产构建始终禁用 Mock。

实时页面要求后端返回今日/周/月/有效汇总、新媒体部门及成员嵌套、兼职陪跑和当日累计走势。当前远程第一版的 `totalLeads`、租户级扁平排行和近几日趋势不会被当成这些数据展示。需要在远程后端升级前查看完整三部门布局时，可在 `.env.local` 设置：

```dotenv
VITE_MEDIA_SCREEN_ENABLE_MOCK=true
```

修改环境变量后需要重新启动 `npm run dev`。演示数据只用于验证页面结构，不会进入生产构建或历史页面。

## 验证与构建

```powershell
npm test
npm run build
```

字段契约见仓库根目录的 `docs/api/media-screen-public-api.md`，部署与白名单配置见
`docs/operations/media-screen-deployment.md`。
