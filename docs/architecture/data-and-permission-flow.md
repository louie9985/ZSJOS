# Data and Permission Flow

## Global maintenance mode

System owns the database-authoritative `zsjos.system.maintenance-enabled` configuration and its public read API. Only the stable `super_admin` role may toggle it. The request filter blocks ordinary writes with HTTP 503 without role or IP bypass; fixed authentication/callback recovery routes and the toggle itself are the only write exemptions. ZSJOS schedulers query the System public API before tenant enumeration, and business deadlines are not shifted by maintenance windows.

## Authentication and tenant flow

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
- Authentication storage cleanup on logout or failed recovery.
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
- Direct URL access must still resolve against the authorized menu set; hiding a menu item alone is not authorization.

The administration message center keeps personal station-message navigation server-owned. The
“全部消息” and “未读消息” routes are children of the existing message-center menu and inherit only
its established role grants. Both routes remain scoped to the authenticated user's messages through
the existing `/system/notify-message/my-page` contract; the unread route fixes `readStatus=false`.
WebSocket events are refresh hints, while the persisted message page remains authoritative.

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

Frontend code must not derive a role from a department or post name, or derive a post
from a role name. Initialization SQL must create and connect each confirmed entity using
the repository's actual relationship tables rather than assuming a one-to-one mapping.

### Employee avatar flow

`system_users.avatar` remains the personal employee-avatar source of truth. The global
`zsjos.user.default-avatar` Infra configuration is only a presentation fallback and is never
backfilled into user rows.

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
  -> lead.source_user_id = current user
     OR lead.owner_user_id = current user
     OR lead.owner_user_id belongs to a department currently led by the user,
        including child departments
```

- `source_user_id` is the original Lead submitter. A later `LeadActivation` submitter
  does not inherit visibility to the existing Lead from the activation alone.
- A user who is both submitter and owner receives the Lead once, with both relation
  types in the response.
- Phase-four submitter commands continue to use immutable `source_user_id` after a post or department transfer, while requiring the system account and the original business subject to remain enabled. Ordinary creation identity is resolved from stable post, department-leader, and partner records rather than role or department display names. Sales self-sourced creation is a separate permission and direct-ownership path. Complaint handling is an independent shared business queue; it does not duplicate BPM tasks or modify sales assignment and performance state.
- Page, status-count, and single-record queries apply the same team boundary. A direct
  detail request cannot bypass row visibility. A leader of a peer department receives
  no access, while a leader of a parent department may access owners in child departments.
- 员工工作台使用固定接口：`GET /zsjos/lead/inbox/submitted/page` 与 `/filter-profile` 只消费提交人方案，要求 `zsjos:lead:query-submitted`；`GET /zsjos/lead/inbox/owned/page` 与 `/filter-profile` 只消费负责人方案，要求 `zsjos:lead:query-owned`。成交审批使用独立的 `reviewer` 方案：后端根据审批配置根部门及子部门解析用户可用中心，报名履约用户固定查询 `registrationReview`，财务用户固定查询 `financeReview`，同时属于两个范围的用户才可切换中心。`inbox-page` 必须将请求中心、已发布筛选条件和用户可用 BPM 节点取交集，页面隐藏另一中心不能代替授权。
- 提交人和负责人客资收件箱固定按服务端分页每批读取 `20` 条。工作台使用左侧滚动容器内的底部哨兵提前加载下一页；切换搜索、分组或环节时废弃旧请求结果、回到列表顶部并重新读取第一页。下一页失败必须保留已加载客资并提供局部重试，不得把增量失败渲染成空列表或扩大服务端筛选范围。
- 通用 `GET /zsjos/lead/page` 继续服务管理端；一旦请求携带 `audience`，Service 仍校验对应视角权限，前端隐藏控件不能代替授权。
- 一旦指定视角，`submitter` 必须限定 `lead.source_user_id = currentUserId`，`owner` 必须限定 `lead.owner_user_id = currentUserId`；`query-all` 不得把“我的”视角扩大成全租户数据。未指定视角的通用管理查询继续遵循原有 `query-all` 或提交人/负责人关系范围。
- `zsjos_lead_inbox_filter_scheme` 保存租户级草稿和当前已发布配置，`zsjos_lead_inbox_filter_version` 保存不可变发布快照。列表查询和数量统计只消费已发布版本；保存草稿不影响工作台，回滚通过复制历史快照并发布新版本完成。
- 管理端只能从后端按视角返回的条件能力白名单选择字段和值，不得提交 SQL、列名或任意表达式。`submitter` 与 `owner` 只允许客资主状态和分配状态；`reviewer` 只允许处理状态和 BPM 任务节点。不同视角的字段不得混用。
- 收件箱归类是对客资主状态和分配状态的只读投影，不是新的持久化状态。前端只能展示服务端返回的筛选项，不得自行补齐尚未实现的跟进、申诉、机会或订单状态。
- 客资状态由后端拆分投影：`qualificationStatus` 表示待判定大类/已判有效/已判无效，`followUpStatus` 表示待首跟/跟进中/成交待审核/已成交，`handlingStage` 进一步区分待分配、待接单、待首跟和有效性判定计时中，`assignmentStatus` 表示分配生命周期，`operationalStatus` 表示挂起等控制状态。有效性判定计时从当前归属周期首次跟进成功开始；前端不得根据 `status`、分配字段或机会状态自行拼装按钮和用户状态，写操作只能消费 `availableActions`，任务提醒按 `handlingStage` 和对应非空截止时间展示。
- Full submitted mobile and WeChat values are returned to an authorized submitter,
  owner, or `query-all` administrator. Frontends must not broaden that authorization.
- `query-all` is an explicit permission-based bypass for the current tenant; it is
  never inferred from a role, post, department, or display name.
- Team visibility is resolved from current System department leader relationships, not
  from the `sales_manager` role name or its generic role data scope. The lead-management
  user filters use `GET /zsjos/lead/visible-users`: `query-all` users receive all enabled
  users in the tenant; other users receive only themselves and enabled users in departments
  they currently lead, including child departments.
- V007 通过既有菜单权限关系分配固定入口：提交权限映射到“我提交的”，抢单或接单权限映射到“我负责的”，`query-all` 映射到两者；不根据角色名或岗位名推断。
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
- Before qualification, append-only follow-up records belong to Lead. Only the current owner can create them; the submitter, current owner, leaders of the owner's department hierarchy, and `zsjos:lead:query-all` may read them. After qualification creates an Opportunity, subsequent sales follow-up belongs to Opportunity instead.
- `lead_first_follow_up` and `lead_follow_up_reminder` are completed or replaced only by the lead follow-up transaction. The employee today-task APIs are assignee-scoped and expose stable action codes rather than a generic completion endpoint.
- 跟进备注和下次跟进时间均为必填，下次时间必须晚于当前时间。无效客资不再允许新增跟进；判无效及成交订单最终生效会取消未完成的首次跟进、下次跟进和适用的判定任务，并清空 Lead/Opportunity 当前下次跟进投影，历史跟进记录保持不变。
- 首次跟进、下次跟进和有效性判定提醒使用 System 租户通知规则中的 `advance/due/overdue` 阶段配置。ZSJOS 扫描仍为 pending 的业务任务，按当前规则发送最紧急的适用阶段，并在 `zsjos_business_task_notify_stage` 中做任务/阶段幂等；配置变化立即影响未发送阶段，已经处理的阶段不补发或重写。直属主管只取销售当前部门负责人，不向上级部门递归。
- Business editing overlays have presentation priority over assignment prompts. An assignment may continue to expire on the server while the workbench defers its modal, so reconnect, focus refresh and polling always reload server truth.

### Subordinate-sales management

The server-owned `下属销售` menu is available only with `zsjos:subordinate-sales:query`. Runtime scope is resolved from System department-leader relationships, including every child department, and then limited to users holding the stable `sales_specialist` post. Disabled accounts remain visible; no role name or department label creates access.

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
- `zsjos_order_approval_config` stores the tenant's registration-fulfillment and finance-settlement root department IDs. Each approval round snapshots all enabled users in each root department and its children; department names, role names and frontend menus are not reviewer sources.
- 成交订单提交/补正只通知本轮两个配置部门解析出的实际审批人；最终通过、拒绝或取消只通知订单提交销售。通知显示配置根部门名称，内部任务键仍保持 `registrationReview` / `financeReview`。
- BPM owns the two parallel user-task groups and their history. Each center is an any-sign pool with no claim step; the first valid decision closes sibling tasks in that center. Both centers must approve, while any rejection ends the round.
- 订单详情通过 BPM 公共 API 汇总当前轮次两个节点的 `pending/approved/rejected/cancelled` 状态并展示给已有订单读取权限的用户；汇总优先保留实际通过或驳回决定，不能让同组后续取消的会签任务覆盖结果。ZSJOS 不新增审批任务或节点状态表。
- 首购订单强制关联同一客户的主客资和商机；复购订单只关联客户，客资仅作为系统客户复购的对象权限上下文。正式销售归属与实际提交人分别固化，复购生效不修改客资、商机或首次成交时间。
- 报名履约和财务任务处理、驳回、创建人终止均先锁定订单及当前审批轮次，并校验当前 BPM 任务、轮次、订单/轮次版本与节点幂等键。流程取消仍由 BPM 公共 API 执行，ZSJOS 只保存订单业务状态、轮次和取消原因快照。
- ZSJOS owns order, item, immutable round snapshot and business status. A process result listener maps BPM approval to `order.status.effective` and Opportunity `won`, or rejection/cancellation to `order.status.revision_required` and Opportunity `following`.
- 审批人视角的筛选方案沿用客资筛选方案的草稿/发布版本机制，audience 固定为 `reviewer`，能力值仅允许 `handled=todo|done` 和 `task_definition_key=registrationReview|financeReview`。筛选项稳定编码使用小写下划线格式 `registration_review` / `finance_review`，与保持 BPM 契约的驼峰条件值相互独立；读取历史配置时兼容旧筛选项编码，并在下一次保存或发布时规范化。列表查询先在订单域按订单号、学员姓名或手机号解析流程实例集合，再将租户、流程定义、任务节点和流程实例条件传给 BPM，确保统计、分页和对象授权一致。
- 工作台业务附件选择后先保留本地文件和预览地址，确认提交时才通过 Infra 文件 API 上传 COS；任一上传失败都不会发送业务命令，成功引用和失败项会保留以便重试。删除只移除当前表单引用，不物理删除已上传文件。
