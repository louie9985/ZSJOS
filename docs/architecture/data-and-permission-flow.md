# Data and Permission Flow

## Global maintenance mode

System owns the database-authoritative `zsjos.system.maintenance-enabled` configuration and its public read API. Only the stable `super_admin` role may toggle it. The request filter blocks ordinary writes with HTTP 503 without role or IP bypass; fixed authentication/callback recovery routes and the toggle itself are the only write exemptions. ZSJOS schedulers query the System public API before tenant enumeration, and business deadlines are not shifted by maintenance windows.

## Read-only impersonation

ZSJOS owns temporary impersonation sessions and their dedicated per-request audit. System remains authoritative for both administrator and target accounts. An active session replaces the request user ID before ZSJOS method and object permission checks, so permissions are resolved from System for the target user; the original administrator and session ID remain in request context and the dedicated audit. Non-read methods and cross-tenant visit contexts are rejected before identity replacement. Query strings, request bodies, filter values, and response content are never persisted in impersonation audit.

## Authentication and tenant flow

The employee workbench exposes the authenticated user's fixed `/user/profile` route from
the avatar menu. This route is not derived from permission menus. It reads and updates the
current System user through `/system/user/profile/*`, uploads avatar images through Infra
file storage, and uses `/system/social-user/*` plus the System social-auth redirect for the
current user's WeCom binding. The client accepts only the WeCom social type and clears OAuth
callback parameters after binding; it does not infer organization or permission data locally.

The employee workbench currently uses the administration API prefix and the system
authentication contract:

```text
login form
  -> POST /admin-api/system/auth/login
  -> access token + refresh token + expiry
  -> GET /admin-api/system/auth/get-permission-info
  -> current user + roles + permissions + menus
```

The workbench HTTP client centralizes:

- API base URL normalization.
- The configured `tenant-id` header.
- Bearer access-token attachment.
- One-time refresh and request replay after an HTTP `401` or a successful HTTP response whose business envelope has `code=401`.
- Authentication storage cleanup on logout or failed recovery; failed recovery also emits one global event that unmounts the workbench and returns directly to login.
- Unwrapping the backend's standard response envelope.

The Today Tasks page may show the ZSJOS business-task panel to users with
`zsjos:business-task:query`. It requests and renders the separate BPM task panel only when the
permission response also contains `bpm:task:query`; access to Today Tasks alone never implies the
broader BPM task permission.

ZSJOS employee login separates computer and mobile sessions with the OAuth2 clients
`zsjos-pc` and `zsjos-mobile`. The login request sends an explicit `platform` value; the
backend does not infer it from a URL, IP address, or user-agent string. Each client has an
independent per-user session limit. A new login beyond that limit revokes the oldest valid
refresh-token session for the same client, while the other client remains unaffected.

The administrator-owned Infra configuration keys `zsjos.auth.pc.max-devices`,
`zsjos.auth.mobile.max-devices`, and `zsjos.auth.remember-days` control the two limits and
the refresh-token lifetime. Defaults are one computer, one mobile device, and seven days.
Values are validated as positive integers with limits of 20 devices and 365 days. Access
tokens remain short lived; an unexpired refresh token allows automatic entry, while an
expired or revoked refresh token requires account-password authentication again. Clients
store opaque tokens and must not persist the account password for this behavior.

Tenant values come from environment configuration. A current local default is not a
license to hard-code tenant assumptions into business components.

### Independent partner frontend

The partner frontend uses `/part-api/zsjos/**` with the independent `PARTNER(3)` identity. The dedicated `partner-api` mapping targets `controller.app.partner` and the URL prefix itself enforces PARTNER token matching, while every other `/app-api/**` route retains MEMBER and `/admin-api/**` retains ADMIN. API prefixes are validated at startup and request filters include the dedicated partner prefix. OAuth token `userId` is the tenant-scoped `zsjos_partner_account.id`; each business request resolves it to `partnerId`, verifies both account and Partner state, and authorizes the target object by `partnerId`. PARTNER does not depend on System roles, departments, posts, menus, or `member_user`.

Partner account updates use the shared MyBatis-Plus optimistic-lock interceptor and the `zsjos_partner_account.version` column. Login audit, enable/disable, mobile, and password updates must affect exactly one row; a stale version returns the stable concurrent-modification error before token issuance, token revocation, or success reporting. This closes the race where a login could issue a token after the account was concurrently disabled.

Partner logout is type-safe and idempotent. System reads the persisted access-token record without requiring it to remain unexpired and deletes the access token, linked refresh token, and their caches only when `user_type=PARTNER`; a missing token or a token of another subject type is left untouched. Login-log enrichment is best effort and cannot roll back logout. The H5 likewise treats confirmed local logout as authoritative: its logout request never starts refresh, and any server, network, or audit failure still clears all local authentication state and navigates directly to login without a return target. Other protected requests retain one refresh-and-replay attempt and preserve their return target only when recovery fails.

The H5 System reference-data client is deliberately separate from the authenticated business client. It calls `GET /app-api/system/dict-data/type` and `GET /app-api/system/area/tree` with `tenant-id` only and removes `Authorization`; those public System endpoints therefore cannot misinterpret an ADMIN token as a MEMBER token. Reference-data failures are visible and retryable. The retained WeCom login entry is an unavailable product path only: it starts no OAuth flow and calls no backend login endpoint until a later approved integration.

Partner role grants are resolved by stable permission code. Numeric menu IDs are not authorization identities and must not be reused to infer or assign partner permissions. The partner role must not inherit work-plan permissions through menu-ID collisions, and finance review remains an explicitly assigned administrator capability rather than a default partner grant.

V071 makes the partner and selected review/finance roles declarative allowlists, gives `finance_manager` and `finance_specialist` the same finance permissions, preserves administrator withdrawal as read-only, and intentionally removes ZSJOS permissions from roles whose ZSJOS domains are not implemented. The complete 34-role target is recorded in `zsjos-role-permission-matrix.md`. App-only or orphaned partner permission buttons are non-routable metadata; V071 does not recreate the retired `partner-portal` administrator page.

Account-password login accepts either the tenant-scoped username or mobile number. New usernames
are 4-32 letters, digits, or underscores; newly set passwords are 8-20 characters and contain both
letters and digits. Login validation deliberately remains compatible with historical password hashes.
New or edited usernames and mobiles cannot collide with either login-identifier field. Historical
cross-field collisions are retained, but an ambiguous login is rejected for administrator correction.
Any self-service password change or administrator reset revokes every access and refresh token for
that user across OAuth clients.

ZSJOS personnel state is a business projection over the System account, not a replacement identity.
`enabled`, `disabled`, and `departed` are stored by ZSJOS; disabling or departing synchronously disables
the System account and revokes sessions. Re-enabling restores the original account, posts, roles, and
profile. Direct System account disable does not rewrite the ZSJOS personnel state. Lead and order
ownership is never reassigned by a personnel-state change.

Partner subjects are administrator-created and bind one existing System account identity. An enabled
partner account cannot simultaneously hold employee business posts. Partner conversion reuses the
same account and may assign only the stable new-media employee or manager posts; the partner profile
and history remain with `converted` status. Historical Lead submission-department snapshots remain by
default and are updated only when the administrator explicitly requests migration.

## Menu and route flow

```text
role-to-menu assignments
  -> backend permission calculation
  -> get-permission-info.menus
  -> client path normalization
  -> primary roots + visible leaf pages
  -> React navigation and local component registry
```

Rules:

- The permission response is the workbench menu source of truth.
- The client preserves backend names, order, visibility, icons, and hierarchy semantics.
- Relative child paths are resolved against their parent. Administrator menu paths are not replaced by demo routes.
- Deep leaves may be flattened for the approved two-column workbench presentation, without changing their permission or original route identity.
- Role names are not used to manufacture menus or grant access.
- Backend component names are metadata only. React renders an explicitly registered local page or a safe placeholder.
- The 36 active ZSJOS-facing page menus have matching React Workbench and Vue Admin renderers; the auditable mapping is maintained in `docs/frontend/zsjos-menu-coverage.md`. Both clients preserve the server path and permission identity instead of maintaining aliases or a duplicate menu tree.
- Authorized hidden pages remain in the React renderable menu tree for direct routing but are removed from visible navigation. Therefore `/zsjos/leads/manage` can be opened only when returned by the permission response, while it remains absent from the side or top menu.
- The canonical appeal and opportunity-public-sea routes are `/zsjos/appeals` and `/zsjos/lead-aging-pool`. The clients intentionally do not redirect the obsolete `/zsjos/leads/appeals` or `/zsjos/opportunity-public-sea` paths.
- A valid active read-only impersonation session is injected by the shared clients as `X-ZSJOS-Impersonation-Session` only on ZSJOS requests and excluded from impersonation lifecycle requests. The clients clear malformed, inactive, or server-rejected sessions and synchronize their visible state. A request rejected with `IMPERSONATION_SESSION_INVALID` remains failed and is not replayed under the administrator identity. Storage is also cleared with authentication state; server-side authorization and write rejection remain authoritative.
- Direct URL access must still resolve against the authorized menu set; hiding a menu item alone is not authorization.

The administration message center keeps personal station-message navigation server-owned. The
“全部消息” and “未读消息” routes are children of the existing message-center menu and inherit only
its established role grants. Both routes remain scoped to the authenticated user's messages through
the existing `/system/notify-message/my-page` contract; the unread route fixes `readStatus=false`.
Workbench lazy loading uses the additive `/system/notify-message/my-cursor` contract. It returns
`list`, `nextCursor`, and `hasMore`, with server ordering `create_time DESC, id DESC`; the legacy
page contract remains for compatibility.
WebSocket events are refresh hints, while the persisted message page remains authoritative.

The partner H5 exposes persisted personal messages through `/part-api/zsjos/messages/**`. The Controller validates the Partner context and supplies the Partner Account ID with `user_type=PARTNER` to every page, detail, read, and unread-count service call. Notification idempotency includes `user_type`, so equal ADMIN and PARTNER numeric IDs cannot collide. External notification channels consume the same typed identity: System dispatches ADMIN and MEMBER through their established APIs, while a module-owned mobile Provider resolves PARTNER only after both account and subject state checks. The SMS log retains the original PARTNER type and never interprets a Partner Account ID as an ADMIN ID.

Partner-created lead and appeal attachments use a typed storage directory containing the Partner Account ID. Reference validation checks both the Infra creator and this directory namespace; internal callers accept their historical `zsjos/lead/**` files but explicitly reject the Partner subtree. Tenant-global lead submission idempotency keys return a prior result only when its persisted `partner_id` matches the current Partner, otherwise the request fails without disclosing the existing record.

New external BPM initiators remain encoded as `<userType>:<userId>`. Approval and rejection notifications carry this typed start subject into the notification recipient, and process/model/task views use the snapshotted `externalStartUserName` rather than parsing the typed identifier as a System user ID. Business termination validates the same audit fields for ADMIN and external subjects; approval candidates and tasks remain ADMIN-only.

Business notification templates are global system configuration, while notification rules and
specified recipients are tenant-scoped. A tenant rule may select only a registered business scene,
one template belonging to that scene, recipient roles exposed by the scene provider, current-tenant
system users, and a controlled click action. It cannot grant lead visibility, infer recipients from
display names, or configure arbitrary URLs, APIs, SQL, expressions, departments, posts, or system
roles. Duplicate recipients from role resolution and explicit selection are merged.

`GET /system/notify-message/my-get?id=...` applies authenticated ownership before returning a
persisted message. For lead variables, the provider resolves contact values for each recipient:
submitter, current owner, `zsjos:lead:query-all`, and approved owner-department management access may
receive complete values; other recipients receive masked values. Receiving a notification does not
create object permission. Clients therefore verify the target API before navigation and fall back
to message details when access is absent.

## Dictionary flow

```text
system dictionary type and data
  -> existing dictionary API
  -> typed service normalization
  -> form label for display + stable value for submission
```

Dictionary type codes are protocol constants. Dictionary labels are administrator-owned
display data and must not become new protocol values. A dictionary-backed control must
represent loading, empty, failure, and retry behavior instead of silently substituting
hard-coded options.

Dictionary management lists and business selection controls both preserve the System
dictionary service order: dictionary entries are ordered by `sort DESC` within each type,
so larger sort values appear first.

## Administrative area flow

Administrative areas are global System-owned tree data and are not a flat system dictionary.
Production runtimes read `system_area`; the bundled `area.csv` is only the initial seed and the
standalone-framework fallback.

```text
Admin area management
  -> System AreaService
  -> system_area + shared area caches
  -> GET /system/area/tree
  -> workbench, CRM, address controls, and AreaUtils consumers
```

- Stable administrative codes cannot be edited or physically deleted. Create and move operations
  validate the country -> province -> city -> district hierarchy, including the moved subtree.
- Disabling a parent removes its whole subtree from business selection without changing descendant
  status. Historical records still resolve names by their stored codes; re-enabling the parent
  restores descendants according to their own status.
- `GET /system/area/tree` returns the enabled China subtree. Management list/detail APIs include
  disabled records and require `system:area:query`; create and update/status operations require
  their corresponding `system:area:create` or `system:area:update` permissions.
- Business submission values are owned by each area's `selection_code`. Ordinary nodes use their
  stable administrative code, while administrator-managed special nodes use `OTHER`; the frontend
  must not synthesize an “other” option that is absent or disabled in `system_area`.
- Province nodes may be marked directly selectable when a city choice is not meaningful, such as
  Hong Kong and Macao. The lead contract remains `provinceCode + cityCode`: a direct province leaf
  is normalized to `cityCode=OTHER`, while its persisted city display name remains empty.
- V013 initializes ordinary siblings in Chinese pinyin order. The System service always keeps
  `selection_code=OTHER` at the end of each level, while subsequent administrator edits to ordinary
  nodes' `sort` values remain authoritative.
- `AreaUtils` uses the System provider in a complete application and retains CSV only for runtimes
  that do not assemble the System module. Administrator changes invalidate both service and
  framework area caches.

## User, department, post, role, and permission

These concepts are related but not interchangeable:

- A user is the authenticated system account and operator.
- A department places a user in the organization hierarchy and participates in data-scope decisions.
- A post describes an organizational job assignment. The same post definition may be used where the confirmed business model allows it; its name alone does not grant permissions.
- A role groups menu and operation permissions. Users receive effective permissions through backend role assignments and framework rules.
- Menus determine authorized navigation metadata; button or operation permissions protect actions.
- BPM model import is exposed only by the server-owned `bpm:model:import` button permission under the standard BPM model menu. The permission definition does not grant any role; BPM publisher roles receive it through normal System role administration.

Frontend code must not derive a role from a department or post name, or derive a post
from a role name. Initialization SQL must create and connect each confirmed entity using
the repository's actual relationship tables rather than assuming a one-to-one mapping.

### Employee avatar flow

`system_users.avatar` remains the personal employee-avatar source of truth. The global
`zsjos.user.default-avatar` Infra configuration is only a presentation fallback and is never
backfilled into user rows.

Employee-avatar uploads use the dedicated `/infra/file/avatar/upload` contract. Infra validates
the detected image content and approved `system/user/avatar` or `employee/avatar` directory,
stores the object under an immutable content-derived name, and returns a stable
`/infra/file/avatar/{fileId}` access URL. Personal and default-avatar records persist that stable
backend URL; they must not persist private-bucket presigned URLs because those expire. The public
avatar reader resolves the file through its original `infra_file.config_id`, rejects non-avatar
directories and non-image records, and does not expose the generic attachment catalog.

```text
personal System user avatar
  -> global default employee avatar
  -> nickname initial
```

- The authenticated permission response returns `user.avatar` and the top-level optional
  `defaultAvatar` together, so Workbench pages do not issue independent configuration requests.
- Administrators update another employee's personal avatar through the existing System user save
  contract. Employees may still update the same field through their profile; the latest update wins.
- The default-avatar read and update endpoints require `infra:config:query` and
  `infra:config:update`. Clearing the global value is supported and restores the nickname-initial
  fallback for users without a personal avatar.
- Workbench applies this fallback only to real System employee identities. Lead, student, order,
  notification and other business-object circles keep their domain-specific presentation.
- Avatar changes become visible when permission or employee data is fetched again; there is no
  avatar-specific realtime push contract.
- Historical URL-only avatars remain readable when their original URL is still valid. A new upload
  replaces them with the stable avatar URL; missing or expired legacy URLs follow the normal
  personal -> default -> nickname-initial presentation fallback without inventing a file mapping.

## ZSJOS authorization layers

ZSJOS authorization has three independent, cumulative layers:

```text
feature permission
  -> Controller @PreAuthorize
  -> list/statistics/export scope through Yudao DataPermission or reviewed SQL conditions
  -> single-object and object-bearing command permission through Service @ZsjosPermission
```

- `@PreAuthorize` answers whether the account may invoke an operation category.
- DataPermission or an explicit business SQL scope answers which rows may appear in list, aggregate, export, and batch-query results.
- `@ZsjosPermission` answers whether the current account may read, write, own, transfer, submit, or otherwise act on the identified ZSJOS object.
- All applicable layers must pass. Feature permission does not grant access to every object, row visibility does not grant mutation rights, and object ownership does not bypass feature permission.
- Object checks run at the Service boundary so alternate controllers, internal callers, and crafted requests cannot bypass them. Batch commands validate every target and make no mutation when any target is unauthorized.
- ZSJOS object-permission relationships are ZSJOS-owned data. CRM permission tables and CRM-specific public-pool or subordinate behavior are not a source of truth.
- Administrator bypass and hierarchy behavior must use confirmed system permission APIs and explicit ZSJOS relationships, never role, post, department, or user display names.
- `@ZsjosPermission` resolves a registered provider by business type. Unknown or duplicate provider types fail closed; existing lead behavior is retained through its provider adapter.

### Work-plan authorization

- Work-plan Controller methods require the corresponding `zsjos:work-plan:*` feature permission.
- The `工作计划` route node is permission-free. `zsjos:work-plan:query` belongs to the separate `查看工作计划` button node so role administrators can grant page access without cascading every write action.
- Work-plan clients treat the query-permitted plan list as the primary page resource. Creation-only templates and user or department options are requested only when the current operation needs them; an optional resource failure must not hide an already authorized plan list.
- Work-plan lists, statistics, and exports combine the current user's department data scope with explicit plan relationships such as creator, plan owner, task assignee, assigner, and confirmer. Cross-department collaboration is granted only by those persisted relationships; it does not make unrelated department-private plans visible.
- Single-plan and single-task commands first check the persisted object relationship and required action. After authorization, status, plan period, parent-child ownership, and deadline checks use the already loaded tenant-scoped objects as business facts; they do not reuse list visibility as an existence test.
- The assignee can read the assigned task as an object relation; completion still requires `zsjos:work-plan:complete`. The explicit confirmer can read and confirm the submitted task; confirmation still requires `zsjos:work-plan:review`.
- Team visibility uses the assignee department snapshot captured at assignment time and the current user's Yudao department data scope. A later employee transfer does not rewrite history.
- Creator, plan owner, assignee, assigner, confirmer, and in-scope users receive different server-calculated `availableActions`. Every write repeats object permission and optimistic-version checks at the Service boundary.
- Attachment submission accepts only Infra file IDs uploaded by the current user under the work-plan directory. A known file ID alone does not grant attachment authority.

## Business API flow

The workbench calls focused existing business APIs through typed services. For example,
sales-assignment options are retrieved from a ZSJOS business endpoint; they are not a
static list of names. Server-side services remain responsible for eligibility, tenant,
permission, and data-scope decisions. The client displays the returned identity using
the approved user-facing label while submitting the stable identifier.

### Lead specified-assignment relationships

Specified lead assignment is an explicit user-to-user relationship keyed by business
scene, source user ID, and target user ID. Posts only determine who is currently
eligible to appear as a source or target; replacing an employee in the same post does
not copy the previous employee's relationships.

```text
new-media operator post eligibility
  + explicit lead_specified_assignment relations
  + enabled sales-specialist post eligibility
  -> current user's assignable sales API
  -> display name (mobile) while submitting the sales user ID
```

- The lead form derives the source user from the authenticated account. It does not
  accept a client-selected source identity.
- A relationship remains attached to the exact account until an authorized manager
  appends, replaces, or removes it.
- `zsjos:lead-assignment:manage-all` allows global relationship management.
- Without that permission, a manager may configure and view audit records only for
  source users in departments they currently lead, including child departments.
- Audit records containing sources outside the manager's current scope are not exposed.

### Lead management visibility

The Vue administration page and React employee workbench consume the same ZSJOS
lead-management query contract. Feature access requires `zsjos:lead:query`; row
visibility is then applied by the ZSJOS service:

```text
zsjos:lead:query-all
  -> all non-deleted leads in the current tenant
otherwise
  -> zsjos:lead:query-submitted
       AND lead.source_user_id is the current user or a currently managed employee
     OR zsjos:lead:query-owned
       AND lead.owner_user_id is the current user or a currently managed employee
```

- `source_user_id` is the original Lead submitter. A later `LeadActivation` submitter
  does not inherit visibility to the existing Lead from the activation alone.
- A user who is both submitter and owner receives the Lead once, with both relation
  types in the response.
- Phase-four submitter commands continue to use immutable `source_user_id` after a post or department transfer, while requiring the system account and the original business subject to remain enabled. Ordinary creation identity is resolved from stable post, department-leader, and partner records rather than role or department display names. Sales self-sourced creation is a separate permission and direct-ownership path. Complaint handling is an independent shared business queue; it does not duplicate BPM tasks or modify sales assignment and performance state. Both founded and unfounded decisions notify the complainant persisted on the complaint record: employee complaints resolve `complainant_user_id`, partner complaints resolve `partner_id` to the partner account. Founded decisions additionally retain the snapshotted owner and current direct department leader recipients. Neither current Lead ownership nor a role/display name may substitute for the actual complainant.
- Managed employees come from current System department-leader relationships, including child departments. Historical Leads follow the employee's current department after transfer; `source_dept_id` is not an authorization source.
- Page and status-count queries retain the submitted/owned list boundary. A single-record detail request uses the unified Lead object reader instead: submitter, current owner, owner-department leader, `query-all`, authorized aging/manual-public-sea participant or manager, readable participant/approver of an order whose `lead_id` is the requested Lead, and owner of an active service relation on such an order. Sharing only the same Person, including a repurchase order with no `lead_id`, grants no Lead access. Aging-pool detail visibility delegates to the same `LeadAgingPoolService.canRead` rule as its list/detail API. A menu or peer-department relationship alone grants no object access.
- 员工工作台统一从 `/zsjos/leads/manage` 进入，并通过 `relationScope=all|submitted|owned` 切换范围。`submitted` 要求 `zsjos:lead:query-submitted` 并消费提交人方案，`owned` 要求 `zsjos:lead:query-owned` 并消费负责人方案；旧 inbox API 与路由仅作兼容。成交审批继续使用独立的 `reviewer` 方案。
- 提交人和负责人客资收件箱使用服务端游标每批读取 `20` 条。工作台使用左侧滚动容器内的底部哨兵提前加载下一批；切换搜索、分组或环节时废弃旧请求结果、回到列表顶部并重新读取首批。游标排序固定为 `last_activity_at DESC, id DESC`，下一批失败必须保留已加载客资并提供局部重试。旧分页接口继续保留给兼容调用方。
- 通用 `GET /zsjos/lead/page` 继续服务管理端；一旦请求携带 `audience`，Service 仍校验对应视角权限，前端隐藏控件不能代替授权。
- 统一客资页固定使用 `relationScope=all`，对提交人与负责人两类已授权关系取去重并集，不再让前端通过“我提交的/我负责的”切换关系范围；旧接口传入 `submitted` 或 `owned` 仍必须具备对应关系权限。页面保留单选的简单状态标签，`simpleStatus` 只在上述关系并集内追加生命周期条件。跨订单、学员、商机或审批入口只可用内部 `leadId` 深链读取指定详情，绝不把该关系人的客资加入管理列表。
- 详情响应由服务端投影 `overviewVisible`、`visibleTabs`、`sourceLabel`、`sourceUserName`、`ownerUserName` 和 `identityMaskMode`。`visibleTabs` 由独立 System 功能权限与统一对象关系共同决定；申诉页签对拥有申诉读取/审核能力的用户可见，也对当前 Lead 原提交人可见，但申诉记录接口仍校验 Lead 对象可读和同一申诉读取规则。`flow-history` 仅在当前用户持有 `zsjos:lead-detail:flow-read` 时投影，流转接口还必须通过同一个 Lead 对象读取检查。前端不得根据角色名、详情 mode 或关系字符串推断。跟进、申诉、投诉、订单和流转记录接口仍分别执行对象校验，隐藏页签不构成授权。
- 客资流转记录是现有事实的只读合并投影：`zsjos_business_event` 提供判定、挂起、恢复、申诉和跟进等业务事件，`zsjos_lead_assignment_history` 提供派单、接单、抢单、转派、回收和释放等归属事件，`zsjos_lead_aging_pool_event` 提供商机公海进入、协作人分配/变更和退出等事件；提交节点来自 Lead 已持久化的提交时间。业务事件通过 `related_object_refs.assignmentHistoryId` 排除对应的重复分配记录，结果按实际发生时间及原始数值 ID 倒序。该投影不补造仓库未记录的历史，不修改三类来源记录，也不建立第二套流转事实表。
- 流转记录的事件码、状态码和来源关联由服务端映射为中文显示值。自动与指定派单以分配历史中是否存在持久化规则引用区分，不能从人员或节点显示名称推断。历史 `lead_appeal_overturned` 即使保存的是申诉审核状态，也按稳定事件语义投影为客资“无效 → 有效”；提交申诉和维持原判不产生客资状态变化，历史 `converted` 状态码兼容显示为“有效”，均不改写原事件。原因与备注必须分字段投影：业务原因使用来源记录的原因或原因标签快照，提交说明、判定说明和跟进内容使用已有备注事实，不得复制同一文本填充两个字段。三类来源表只保存人员 ID 而没有统一的姓名快照，因此员工事件通过 System 用户解析当前昵称，兼职提交通过当前 Partner 记录解析名称；主体已删除且无来源快照时显示“未知账号”，不得虚构历史姓名。事件证据引用解析为短时 Infra 文件预览地址，仍受租户、功能权限和 Lead 对象权限保护；Workbench 仅为图片和 PDF 提供预览入口，不提供下载按钮。
- Lead 业务通知统一深链到 `/zsjos/leads/manage?leadId={内部客资ID}&tab={目标页签}`。申诉结果进入 `appeals`，投诉结果进入 `complaints`，跟进和提醒进入 `follow-ups`，其余场景进入 `overview`；申诉提交给审核人的待办入口仍优先进入独立申诉处理页。实时弹窗、消息铃铛和消息中心复用同一动作解析。`tab` 只是导航意图，Workbench 必须用详情响应的 `visibleTabs` 再校验，不可见时回退概览，不能据此扩大对象或接口权限。
- `zsjos_lead_inbox_filter_scheme` 保存租户级草稿和当前已发布配置，`zsjos_lead_inbox_filter_version` 保存不可变发布快照。列表查询只消费已发布版本；筛选标签不返回数量且不执行额外统计查询。保存草稿不影响工作台，回滚通过复制历史快照并发布新版本完成。
- 管理端只能从后端按视角返回的条件能力白名单选择字段和值，不得提交 SQL、列名或任意表达式。`submitter` 与 `owner` 只允许客资主状态和分配状态；`reviewer` 只允许处理状态和 BPM 任务节点。不同视角的字段不得混用。
- 收件箱归类是对客资主状态和分配状态的只读投影，不是新的持久化状态。前端只能展示服务端返回的筛选项，不得自行补齐尚未实现的跟进、申诉、机会或订单状态。
- 客资状态由后端拆分投影：`qualificationStatus` 表示待判定大类/已判有效/已判无效，`followUpStatus` 表示待首跟/跟进中/成交待审核/已成交，`handlingStage` 进一步区分待分配、待接单、待首跟和有效性判定计时中，`assignmentStatus` 表示分配生命周期，`operationalStatus` 表示挂起等控制状态。有效性判定计时从当前归属周期首次跟进成功开始；前端不得根据 `status`、分配字段或机会状态自行拼装按钮和用户状态，写操作只能消费 `availableActions`，任务提醒按 `handlingStage` 和对应非空截止时间展示。
- Full submitted mobile and WeChat values are returned to an authorized submitter,
  owner, or `query-all` administrator. After automatic assignment, ordinary submitter
  and owner views blind the counterpart employee identity (name and user ID). A new-media
  specified assignment is an explicit mutual identity disclosure, so both counterpart
  views receive the complete submitter and owner employee identity; the
  other direct business readers, owner-department managers, and `query-all` administrators retain
  the complete employee identity. Frontends must not broaden either authorization.
- `query-all` is an explicit permission-based bypass for the current tenant; it is
  never inferred from a role, post, department, or display name.
- Team visibility is resolved from current System department leader relationships, not
  from the `sales_manager` role name or its generic role data scope. The lead-management
  user filters use `GET /zsjos/lead/visible-users`: `query-all` users receive all enabled
  users in the tenant; other users receive only themselves and enabled users in departments
  they currently lead, including child departments.
- V078 将 V007 的两个固定入口收拢为单一“客资管理”页面，原权限节点保留为隐藏范围能力；不根据角色名、岗位名或前端标签推断数据范围。
- V121 退役独立“异常客资”页面菜单；挂起与回收待处理客资仍通过统一“客资管理”读取，恢复、转派、回收、释放动作由详情 `availableActions` 返回并在 `lead-action-toolbar` 中展示。后端异常处置 API 与权限标识保留。
- V025 通过现有 `system_role_menu` 关系将“我的订单”复制给已经拥有“录入成交”的角色。订单列表固定使用 `submitter_user_id = 当前用户`，详情继续执行本人提交对象校验；客资转派不会改变历史订单提交人，也不会扩大成交审批池。

### Qualification exception authorization

- 销售有效性判定要求 `zsjos:lead:qualify`，且对象必须仍为当前用户负责的 `submitted + owned` 待判定客资。
- 异常队列查询、异常处置和全租户处置分别使用 `zsjos:lead:qualification:query`、`zsjos:lead:qualification:manage`、`zsjos:lead:qualification:manage-all`，不得从角色、岗位或显示名称推断。
- 无效判定附件上传使用 `zsjos:lead:qualify` 专用接口；上传只产生当前用户的临时文件引用，最终仍由判无效命令校验文件归属并在客资行锁事务中固化证据快照。申诉附件接口继续使用申诉权限，不与判定权限互相替代。
- 普通主管的对象范围来自系统部门负责人关系：必须负责原销售所在部门或其上级部门；转派候选仅包含本人管理部门及子部门内的启用销售专员。
- 回收清除 `owner_user_id` 后，以 `recycle_source_owner_user_id` 继续执行主管对象范围校验。全租户处置权限只放宽当前租户内部门范围，不绕过租户隔离。
- 判定、超时扫描和主管处置都在租户条件下锁定客资。超时扫描实际提交前允许人工判定；任一事务先提交后，后续事务按新的持久化状态拒绝冲突操作。

### Claim-pool visibility and actions

The React workbench and Vue administration table consume the same paged claim-pool
contract. Every response keeps the submitted name, mobile, and WeChat identifier
masked while returning complete region, intended-course snapshots, source-channel and
lead-category labels, remark, and attachment images.

- An eligible sales specialist with `zsjos:lead:claim` may list and atomically claim
  public-pool leads from the React workbench.
- A `zsjos:lead:query-all` administrator may list the current tenant's public-pool
  leads in the Vue administration table without becoming eligible to claim them.
- Claiming still requires both the feature permission and current sales eligibility;
  list access never grants the action.
- Results are ordered by `public_pool_at ASC, id ASC`, so the oldest waiting lead is
  presented first. Tenant and logical-delete interceptors remain applicable.

### Opportunity public-sea visibility and actions

The Opportunity public sea is independent from the pre-qualification claim pool. Only a qualified
Lead with an open/following Opportunity can enter. The formal owner and owner department remain
authoritative; configuring collaborator B does not transfer Lead or Opportunity ownership.

- `zsjos:lead-aging-pool:query` grants feature access only. Rows are limited to enabled eligible
  sales in formal owner A's current department, that department's direct leader, A, B, or a user with
  `zsjos:lead-aging-pool:manage-all`.
- The direct current-department leader also needs `zsjos:lead-aging-pool:manage` to assign, reassign,
  or exit a cycle. `manage-all` is the tenant-wide operational fallback.
- B must be an enabled eligible sales user in A's current department and must differ from A. The entry-time
  department snapshot is retained for audit only.
- A and configured collaborator B may both add follow-ups and submit or revise the deal. Commands lock
  the active cycle, so the first conflicting mutation to commit wins.
- Full contact data follows the same server-side pool visibility. Frontends consume
  `availableActions` and do not infer mutation rights from owner or department labels.
- The published `agingPool` inbox audience owns the configurable status grouping for the dedicated
  pool page. It filters cycle status only and does not reinterpret ordinary Lead assignment state.
- When an order becomes effective, the cycle exits as converted while Lead and Opportunity formal
  ownership remain unchanged. The immutable order submitter records the actual operator.
- A rejected A-owned order is not reassigned in place. B creates a linked continuation order; the A
  order remains as `superseded` history and only the B order may proceed through the new approval round.
- Advance reminders use a durable pending/failed/sent stage. The stage becomes sent only after System
  confirms message persistence; failed attempts retain a stable error code and a bounded retry time.

### Sales assignment acceptance

- Sales acceptance requires `zsjos:lead:accept`; fresh initialization and V006 grant it to roles that already hold the claim action instead of inferring access from a role or post display name.
- The same permission only exposes the workbench intake control. The backend separately confirms the current account remains an enabled `sales_specialist` post holder before admitting it to the tenant-scoped Redis pool; role labels never establish eligibility.
- Page presence and intake preference are independent. A 30-second workbench heartbeat maintains a 90-second Redis presence key, while the accepting/paused preference is persisted by ZSJOS and defaults to paused. Effective automatic-assignment eligibility requires both online presence and accepting mode.
- Redis owns only pool rotation, transient presence, and one-pending reservation. Lead assignment status, candidate history, expiry, ownership and business tasks remain database-owned; automatic timeout recovery scans `pending_expires_at` rather than expired Redis keys.
- The workbench starts pending queries and WebSocket invalidation only when the permission response contains that permission. Authorized load failures remain visible and retryable.
- A pending assignment is represented by Lead assignment fields plus a `lead_assignment_accept` business task. Accept, reject, timeout and administrative cancellation complete or cancel that task in the same business transaction.
- Acquiring ownership creates a `lead_first_follow_up` task from the currently enabled tenant follow-up rule. The task payload freezes the rule ID, version, timeout and ownership start time.
- Before qualification, append-only follow-up records belong to Lead. Only an authorized sales operator can create them; readers require `zsjos:lead-detail:follow-up-read` plus the unified Lead detail object relation. After qualification creates an Opportunity, subsequent sales follow-up belongs to Opportunity instead.
- `lead_first_follow_up` and `lead_follow_up_reminder` are completed or replaced only by the lead follow-up transaction. The employee today-task APIs are assignee-scoped and expose stable action codes rather than a generic completion endpoint.
- 跟进备注和下次跟进时间均为必填，下次时间必须晚于当前时间。无效客资不再允许新增跟进；判无效及成交订单最终生效会取消未完成的首次跟进、下次跟进和适用的判定任务，并清空 Lead/Opportunity 当前下次跟进投影，历史跟进记录保持不变。
- 首次跟进、下次跟进和有效性判定提醒使用 System 租户通知规则中的 `advance/due/overdue` 内部阶段值。ZSJOS 扫描仍为 pending 的业务任务，按当前规则发送最紧急的适用阶段，并在 `zsjos_business_task_notify_stage` 中按内部阶段值做任务/阶段幂等；配置变化立即影响未发送阶段，已经处理的阶段不补发或重写。消息展示边界将三个阶段转换为“即将到期/已到期/已逾期”，系统默认规则分别使用阶段化中文标题、摘要和正文，不向用户暴露内部英文值；管理员自定义模板仍由 System 配置管理。直属主管只取销售当前部门负责人，不向上级部门递归。
- “客资新建”是默认站内信场景。新租户初始化使用两条独立规则：`operator` 接收通用提交成功消息，`new_media_provider` 只在销售自拓明确选择了不同于操作人的新媒体提供方时解析。提供方消息固定为“`{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。`”；未选择提供方和普通新媒体提交不解析该角色。V080 仅拆分未经编辑且启用的 V075 系统默认，管理员已有的启用、停用或已编辑规则保持权威，迁移不覆盖，也不补发历史消息。
- Business editing overlays have presentation priority over assignment prompts. An assignment may continue to expire on the server while the workbench defers its modal, so reconnect, focus refresh and polling always reload server truth.

### Subordinate-sales management

The server-owned `下属销售` menu is available only with `zsjos:subordinate-sales:query`. Runtime scope is resolved from System department-leader relationships, including every child department, and then limited to users holding the stable `sales_specialist` post. Disabled accounts remain visible; no role name or department label creates access.

The independent `zsjos:subordinate-sales:pause-all` command resolves that same live scope on the server and never accepts frontend target IDs, filters, or loaded rows. It persists only the sales dispatch preference as paused, including for disabled accounts, and records per-user changes in the existing subordinate-sales audit log. The first V092 installation grants this capability to enabled `sales_manager` roles, but the permission does not expand the manager hierarchy or subordinate visibility.

The Workbench owns one shared sales-dispatch status lifecycle for the header control and global route-shell warning. The warning is mounted outside individual pages, so it remains visible while navigating between routes. Only the backend-projected eligible sales identity is warned; an eligible user sees status-load failure first, then page/realtime offline, then a paused preference. Managers and other non-sales users do not receive a recoverable intake warning merely because they hold management permissions. The header uses red tags for paused and offline states.

The subordinate Lead detail reuses the same presentation component in read-only mode. `zsjos:subordinate-sales:query` may permit the detail entry, but each history tab additionally requires its dedicated `zsjos:lead-detail:*` feature permission and the unified Lead object check. The selected Lead owner must remain inside a department currently led by the caller; frontend tab visibility and absent write controls are defense in depth only.

- Account and dispatch mutations use separate permissions. Account disable delegates to the System public user API so current login tokens are revoked without moving Lead ownership.
- Every account, dispatch, transfer, and manual public-sea mutation requires a trimmed reason of at most 500 characters and writes operator, target, before/after values, reason, and occurrence time.
- Effective new-Lead intake requires an enabled account, current sales eligibility, online page presence, and accepting mode. Presence and accepting preference remain independent sources.
- Metrics use current owned Lead inventory and Beijing-day pending follow-up tasks. Historical effective-order metrics remain attributed to immutable order submitters after later Lead transfers.
- Batch transfer and manual public-sea release accept at most 200 Lead IDs and commit each Lead independently. Every result returns the Lead ID and a stable success or failure code; one failure does not roll back successful siblings.
- Manual public sea is an owner-preserving collaboration marker with an optional enabled in-scope sales collaborator. It does not change Lead primary status, `owner_user_id`, or `assignment_status`, does not enter the claim pool, and cannot be claimed.

### Lead appeal routing (V015)

- `zsjos:lead:appeal:create` is restricted to the current lead submitter and is checked again at the service boundary against the invalid lead and the next round.
- Round 1 resolves the assigned salesperson's direct department leader and requires `zsjos:lead:appeal:review-sales-manager`; missing, disabled or unauthorized leaders reject submission.
- Round 2 resolves all enabled users in the `quality_manager` or `quality_specialist` roles. BPM owns the parallel any-sign task; the first completed task closes the others and stale clicks return a stable handled error.
- Round 3 requires exactly one enabled `boss` role user and `zsjos:lead:appeal:review-chairman`; missing or multiple users reject submission.
- The dedicated React “申诉处理” menu is an extracted BPM business inbox. It does not move or mark messages in the ordinary message center. A reviewer must satisfy feature permission, BPM task ownership, stage permission and lead object permission together.
- The server-owned menu path remains authoritative. The workbench resolves the approved `zsjos/leadAppeal/index` component metadata through a local component registry, and replaces an inaccessible route retained from another account with that account's first authorized internal page.
- Empty BPM todo and done pages return an empty business inbox without querying process instances with an empty identifier set.
- There is no deadline, automatic escalation or fourth appeal. Notifications are published through the system business-notification API while the appeal inbox reads ZSJOS records plus BPM task APIs.

### Sales-order dual approval (V023)

- `zsjos:sales-order:create` exposes direct order entry only when the backend `availableActions` projection enables `ENTER_DEAL`; the Service rechecks current ownership, valid qualification, suspension, opportunity state, active-order uniqueness and enabled SKU state under a tenant-scoped row lock.
- Sales-order field options come from System dictionaries and the enabled ZSJOS product/SKU catalog. The workbench does not keep static business options or infer product hierarchy from labels.
- `zsjos_order_approval_config` stores the tenant's registration-fulfillment and finance-settlement root department IDs. New approval rounds snapshot all enabled users in each root department and its children, including department leaders; department names, role names and frontend menus are not reviewer sources. Submission fails as invalid approval configuration only when either center has no enabled user.
- 成交订单提交/补正只通知本轮两个配置部门解析出的实际审批人；最终通过、拒绝或取消只通知订单提交销售。通知显示配置根部门名称，内部任务键仍保持 `registrationReview` / `financeReview`。
- 主管加签申请使用 `zsjos.sales_order.supervisor_requested` 只通知指定销售主管；主管决定使用 `zsjos.sales_order.supervisor_decided` 只通知加签申请人。消息携带订单号、申请中心、申请人、主管、原因、决定和受控任务定位字段，不广播给双中心其他审批人。V093 仅为缺失租户规则补建默认模板和站内信规则，不覆盖管理员配置。
- BPM owns the two parallel user-task groups and their history. Each center is an any-sign pool with no claim step; the first valid decision closes sibling tasks in that center. Both centers must approve, while any rejection ends the round.
- 订单详情通过 BPM 公共 API 汇总当前轮次两个节点的 `pending/approved/rejected/cancelled` 状态并展示给已有订单读取权限的用户；汇总优先保留实际通过或驳回决定，不能让同组后续取消的会签任务覆盖结果。ZSJOS 不新增审批任务或节点状态表。
- 上线后轮次允许普通审批人每轮唯一一次申请销售主管审批，因此整轮最多是报名履约、财务和主管三方。主管只取订单正式销售当前直属部门的 `leaderUserId`，必须启用、不能是正式销售本人且必须持有 `zsjos:sales-order:supervisor-confirm`；BPM 通过并行加签拥有主管任务、评论和历史，ZSJOS 的 `zsjos_order_supervisor_confirmation` 只保存业务申请、决定、状态和 BPM 引用，不复制任务。`pending` 不锁定普通审批任务，报名履约、财务与主管任务可同时可见和处理；一旦申请加签，该中心普通审批与对应主管审批都成为通过条件，先通过的一方等待另一方，二者通过后该中心节点才完成，另一中心状态不受影响。任一中心或主管驳回都由 BPM 驳回整轮；申请中心通过不取消主管记录，中心驳回、销售终止或流程取消才取消未完成申请。普通审批与主管审批共用服务端“成交订单审批”页面，功能权限和本人 BPM 任务仍分别校验。
- 今日任务和业务消息通过 `/zsjos/sales-order/approval/task-target` 校验 BPM 任务关系后深链到统一审批页；从客户档案返回时仅允许 `/zsjos/sales-order-approvals` 白名单相对路径，禁止站外跳转。
- 成交普通审批累计要求 Controller 功能权限 `zsjos:sales-order:review`、配置部门范围及本人普通 BPM 任务；主管确认累计要求 `zsjos:sales-order:supervisor-confirm`、本人主管 BPM 子任务、申请记录指定主管和订单对象关系。菜单可见、部门成员、对象可读和 BPM 任务所有权互不替代。
- 首购订单强制关联同一客户的主客资和商机；复购订单只关联客户，客资仅作为系统客户复购的对象权限上下文。正式销售归属与实际提交人分别固化。超期协同公海与主管人工公海对同一 Lead 互斥：两条创建路径均在 Lead 行锁后检查另一活动记录；历史上若两者同时存在，协同操作以稳定冲突错误失败，运维只读脚本负责列出冲突但不自动清理。任一公海的协同销售提交首购订单时，事务锁定 Lead、Opportunity 和协同记录，先将 Lead/Opportunity 正式负责人永久转给该销售并换绑未完成跟进任务，再将订单 `formalSalesUserId` 固化为新负责人；订单驳回、取消或终止不恢复原负责人。
- 报名履约和财务任务处理、驳回、创建人或正式负责人终止均按“订单→当前审批轮次”顺序加锁，并校验当前 BPM 任务、轮次、订单/轮次版本与节点幂等键。终止由 BPM 业务授权公共 API 执行并记录真实操作人、授权类型和原因；ZSJOS 保存订单业务状态、轮次和原因快照。
- ZSJOS owns order, item, immutable round snapshot and business status. A process result listener maps BPM approval to `order.status.effective` and Opportunity `won`, or rejection/cancellation to `order.status.revision_required` and Opportunity `following`. Resubmission never mutates the rejected order into a new round: it creates an independent successor, marks the old order `superseded`, preserves old audit facts, and starts a new BPM round in one transaction.
- 审批人视角的筛选方案沿用客资筛选方案的草稿/发布版本机制，audience 固定为 `reviewer`，能力值仅允许 `handled=todo|done` 和 `task_definition_key=registrationReview|financeReview`。筛选项稳定编码使用小写下划线格式 `registration_review` / `finance_review`，与保持 BPM 契约的驼峰条件值相互独立；读取历史配置时兼容旧筛选项编码，并在下一次保存或发布时规范化。列表查询先在订单域按订单号、学员姓名或手机号解析流程实例集合，再将租户、流程定义、任务节点和流程实例条件传给 BPM，确保统计、分页和对象授权一致。
- 工作台业务附件选择后先保留本地文件和预览地址，确认提交时才通过 Infra 文件 API 上传 COS；任一上传失败都不会发送业务命令，成功引用和失败项会保留以便重试。删除只移除当前表单引用，不物理删除已上传文件。

### Registration fulfillment and students (V073)

- `registrationReview` first approval creates one tenant/order-unique public-pool case. Finance pending or `revision_required` cases remain editable. Completion requires an `effective` order, every manual item checked, every required attachment present, at least one selected route, and a currently eligible assignee for each route. Planner eligibility is role code `study_planner`; director eligibility is post code `content_director`; both are restricted to the selected System department subtree. Completed route rows retain department and assignee snapshots. Planner/director My Students visibility is derived from these completed route relations, while public-pool permission remains independent.
- Feature permissions (`query-pool`, `update`, `complete`) and registration object checks are cumulative. Public-pool access is never inferred from role or department. The planner candidate query uses System public APIs and never reads System tables.
- Completion atomically records checklist facts, one service relation for each order item, and Person identity `student`. My Students is scoped by active service relations plus selected tenant-matched route relations and grouped by Person. `leadNo` remains the only user-visible Lead identifier; for a user who directly owns an active service relation, internal `leadId` is derived from that relation's actual order and may be returned only as the technical link used to load the existing Lead detail. Another Lead that merely shares the Person is never used as a fallback.
- An active student service owner has a read-only object relationship to the student's Lead. The relationship is checked from `zsjos_service_relation`, never from a role/post/display name, and grants no mutation. Which history tabs appear is independently controlled by the four `zsjos:lead-detail:*` role permissions; the workbench consumes only the server-projected `visibleTabs`.
- The My Students detail reuses the same Lead detail shell as Lead management while applying a planner-specific projection. When a Person has multiple service relations, each relation resolves its own order-linked Lead and `leadNo`; the client must never use another relation's Lead as a fallback. The planner projection displays成交产品 from the selected order-item snapshot and uses only service-relation contact records for最近联系, contact history, next-contact time and task aging; it does not expose Lead sales follow-up data. The progress surface maps the existing first-contact, study-plan and recurring-contact tasks to首联、制定学习计划、督学, with考试 retained as a future stage until an authoritative exam task contract exists. Study-plan success advances directly to督学; the planner has no group/handoff stage. Before acceptance, the server may project only `ACCEPT`; after acceptance it projects at most one of `FIRST_CONTACT`, `STUDY_PLAN`, or `FOLLOW_UP`, followed by separately authorized identity and collaborator actions. Planner identity updates write only the Person master and a PII-minimized business event, never Lead or order snapshots. Every command remains scoped to the selected `serviceRelationId`; Controller feature permission and object authorization remain cumulative.
- Registration task creation publishes `zsjos.registration.task_created` through the System notification API. Recipients are the intersection of enabled users with `zsjos:registration:query-pool` and users in the configured registration approval department subtree; administrators outside that center are excluded. Registration completion publishes one `zsjos.registration.planner_assigned` event directly to the selected planner after service relations exist, renders the student name and `personNo`, and identifies the click target as the internal Person ID with `bizType=student`. Selecting or changing a planner before completion sends no assignment notification. Persisted in-app messages and post-commit WebSocket hints are idempotent by their registration-case/planner event key. User interfaces consume Chinese label fields while retaining protocol codes internally. Newly created Persons receive `XYyyyyMMddHHmmss` plus a four-digit sequence scoped by tenant and Beijing-local date; legacy `P + UUID` values remain unchanged.
