# ZSJOS 员工工作台

独立 React + Vite + Ant Design Pro 前端。所有数据、认证、权限和菜单均复用现有 `/admin-api` 接口；`yudao-module-zsjos` 不提供工作台数据接口。

## 开发

```bash
pnpm install
pnpm dev
```

默认前端端口为 `5174`，开发环境 `/admin-api` HTTP 请求和 `/infra/ws` WebSocket 连接代理到 `http://localhost:48080`。可用 `VITE_API_BASE_URL` 指定生产 API 地址。所有接口默认携带 `tenant-id: 1`，可用 `VITE_TENANT_ID` 覆盖。

## 约束

- 前端菜单从 `/admin-api/system/auth/get-permission-info` 的 `menus` 动态加载。
- 前端不自建数据来源，不调用 `/zsjos/**` 业务接口。
- 后端 `component` 字段只用于安全映射和占位展示，不动态执行。

详细设计见 [docs/architecture.md](docs/architecture.md)，开发规范见 [docs/code-conventions.md](docs/code-conventions.md)。
