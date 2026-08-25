# ZSJ-OS System Overview

## Purpose

ZSJ-OS uses the existing multi-module administration platform as its backend and
administrator-facing system, with a separate employee workbench for employee-facing
flows. The system has four active development surfaces with different ownership and
toolchains.

## Runtime surfaces

| Surface | Location | Stack | Responsibility |
| --- | --- | --- | --- |
| Backend and assembly | `backend/`, `backend/yudao-server/`, `backend/yudao-module-*/` | Java 25, Spring Boot 4.1, Maven | Authentication, authorization, tenant isolation, system capabilities, business APIs, persistence, runtime assembly |
| Administration frontend | `frontend/admin/` | Vue 3, TypeScript, Vite, Pinia, Element Plus | Administrator configuration and management workflows |
| Employee workbench | `frontend/workbench/` | React, TypeScript, Vite, Ant Design 6, Pro Components | Employee navigation and business workflows backed by existing APIs |
| Partner H5 | `frontend/h5/` | Vue 3, TypeScript, Vite, Vant | Independent PARTNER login and partner-owned mobile workflows |

Administration and employee APIs use `/admin-api/**` with ADMIN identities. Member APIs use
`/app-api/**` with MEMBER identities. The partner H5 uses `/part-api/zsjos/**` with PARTNER
identities; its public dictionary and area lookups remain on `/app-api/system/**` without a
partner bearer token. Development and production proxies must forward both H5 prefixes.

The read-only media screen uses `/public-api/zsjos/media-screen/**` without a login token. It is
not generally anonymous: the backend binds each configured client IP/CIDR to one tenant, rejects
unlisted combinations before entering the Controller, and installs the validated tenant context
for downstream queries. The feature is fail-closed and disabled by default. Its API and deployment
contracts are documented in `docs/api/media-screen-public-api.md` and
`docs/operations/media-screen-deployment.md`.

`../CRM-demo-Ant-design` is a design reference when available. It is not a runtime
dependency or a source of production data, routes, permissions, or API contracts.

## Backend module roles

- `yudao-server` assembles enabled modules and provides the runnable Spring Boot application.
- `yudao-module-system` owns users, departments, posts, roles, permissions, menus, dictionaries, tenants, and authentication endpoints.
- Existing business modules own their established domains and APIs.
- `yudao-module-zsjos` owns new Zhongshijian-specific business behavior that is not already owned by another module.
- `yudao-framework` owns shared technical infrastructure. Business behavior should not be placed there.

The backend is configured as a Maven multi-module build. Enabled modules
are defined by `backend/pom.xml`; runtime exposure also depends on `backend/yudao-server`
dependencies and Spring component scanning.

## Realtime message contract

- Authenticated browser clients connect to `/infra/ws`; this endpoint is not under the
  `/admin-api` HTTP prefix. Development and production proxies must enable WebSocket Upgrade
  for this path. The `token` query parameter carries the current OAuth access token, never the
  refresh token.
- Durable user notifications are written to `system_notify_message` first. After the database
  transaction commits, the backend sends a `notify-message-new` event containing only the
  message identifier. Clients then query the system notification APIs for authoritative data.
- Business notification events are published by registered backend code after the owning
  business transaction commits. System resolves enabled tenant rules and recipients, renders a
  recipient-specific snapshot, persists it in an isolated transaction, and only then emits the
  WebSocket hint. Notification failure is logged without payload data and never rolls back the
  originating business operation.
- `(tenant, rule, recipient, source event key)` is the durable idempotency boundary. WebSocket
  reconnect or duplicate delivery may trigger another fetch, but cannot create another message.
- Business realtime events such as `zsjos_lead_assignment` are invalidation hints. Clients
  refresh the corresponding business API instead of treating the WebSocket payload as stored truth.
- `yudao.websocket.sender-type=local` supports a single backend instance. Multi-instance
  deployments must use one of the configured shared senders, normally Redis, and verify
  cross-node delivery before release.

## User feedback contract

- `Message` is for immediate feedback from the user's current request: success, failure,
  validation, upload checks, and lightweight explanations. It remains a top-level transient hint.
- `Notification` is for asynchronous WebSocket events, background completion, and cross-page
  business events. It appears at the bottom right and shows only the persisted title and summary.
- Full notification content is shown only in message details. Loading, empty, unauthorized, and
  retry states remain in the owning page rather than being replaced by a transient message.
- Confirmation and irreversible actions continue to use `MessageBox` or `Modal`.

## Time Contract

- ZSJOS HTTP JSON date-time fields use Unix epoch milliseconds in both directions. ISO strings are not a supported JSON request value.
- Date range query parameters remain formatted strings governed by the receiving request VO and are not JSON timestamps.
- Business-local `LocalDateTime`, JVM time, MySQL connection sessions, and user-facing rendering use `Asia/Shanghai`. Database connection configuration must force the MySQL session timezone rather than only describing how Connector/J interprets values.
- ZSJOS clients display full timestamps as `YYYY-MM-DD HH:mm:ss` unless a feature explicitly requires date-only or minute-only precision.
- Existing historical anomalies are audited with `script/sql/mysql/verify-zsjos-time-contract.sql`; repair requires separate review because a naive `DATETIME` value does not retain its original timezone.

## Local development commands

Backend, from the repository root:

```powershell
mvn -f backend/pom.xml -pl yudao-module-zsjos -am test
mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package
```

Administration frontend:

```powershell
cd frontend/admin
pnpm dev
pnpm ts:check
pnpm lint
pnpm build:local
```

Employee workbench:

```powershell
cd frontend/workbench
npm run dev
npm test
npm run typecheck
npm run build
```

For local same-origin Workbench/Admin development, run Vue Admin with
`pnpm dev-embed` (it listens on `5175` with `VITE_BASE_PATH=/admin-embed/`) and open the
Workbench on `5174`. The Workbench Vite proxy forwards `/admin-embed` to the Admin dev
server, `/admin-api` and `/infra/ws` to the backend, so the browser sees one origin and the
two clients share token storage. Set `VITE_ADMIN_EMBED_PROXY_TARGET` and
`VITE_BACKEND_PROXY_TARGET` when those local ports or hosts differ. Production reverse
proxying should expose `/` for Workbench, `/admin-embed/` for Admin, `/admin-api/` for the
backend, and `/infra/ws` with WebSocket upgrade enabled.

Ports, credentials, tenant values, and service addresses are environment configuration,
not durable architecture facts. Check the active `.env*`, application YAML, proxy, and
running processes before diagnosing a local startup issue.

## Change routing

- Employee-only presentation and interaction: React workbench.
- Administrator configuration and management: Vue administration frontend.
- Existing system or domain behavior: its owning backend module.
- New Zhongshijian-specific business capability: `yudao-module-zsjos`.
- Cross-surface behavior: update each affected contract consumer, but do not duplicate business truth between clients.
