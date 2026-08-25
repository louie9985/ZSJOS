# ZSJOS 员工工作台

独立 React + Vite + Ant Design Pro 前端。所有数据、认证、权限和菜单均复用现有 `/admin-api` 接口；`yudao-module-zsjos` 不提供工作台数据接口。

## 开发

```bash
pnpm install
pnpm dev
```

默认前端端口为 `5174`，开发环境 `/admin-api` HTTP 请求和 `/infra/ws` WebSocket 连接代理到 `http://localhost:48080`。可用 `VITE_API_BASE_URL` 指定生产 API 地址。所有接口默认携带 `tenant-id: 1`，可用 `VITE_TENANT_ID` 覆盖。

如果菜单的 `workbenchRenderMode` 配置为 `admin_embed`，Workbench 会从同源
`/admin-embed/` 打开 Vue Admin 的内容页。为了本地保持一个浏览器 origin：

```bash
cd frontend/admin && pnpm dev-embed   # Admin Vite: 5175
cd frontend/workbench && npm run dev  # Workbench: 5174，对外只访问这个地址
```

Workbench Vite 会把 `/admin-embed` 代理到 5175，把 `/admin-api` 和 `/infra/ws` 代理到后端。
可用 `VITE_ADMIN_EMBED_PROXY_TARGET`、`VITE_BACKEND_PROXY_TARGET` 覆盖目标地址；生产环境
由反向代理按相同路径分流。两个应用共享同源 `ACCESS_TOKEN`、`REFRESH_TOKEN`、`CLIENT_ID`
存储，iframe 地址只包含业务路由和 `embed=workbench`，不包含 token。

## 约束

- 前端菜单从 `/admin-api/system/auth/get-permission-info` 的 `menus` 动态加载。
- 前端不自建数据来源，不调用 `/zsjos/**` 业务接口。
- 后端 `component` 字段只用于安全映射和占位展示，不动态执行。

详细设计见 [docs/architecture.md](docs/architecture.md)，开发规范见 [docs/code-conventions.md](docs/code-conventions.md)。
