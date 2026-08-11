# Data and Permission Flow

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
- The current codebase does not yet provide `@ZsjosPermission`; this is a known implementation gap that must be closed before object-bearing ZSJOS workflows are considered permission-complete.

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
```

- `source_user_id` is the original Lead submitter. A later `LeadActivation` submitter
  does not inherit visibility to the existing Lead from the activation alone.
- A user who is both submitter and owner receives the Lead once, with both relation
  types in the response.
- Page, status-count, and single-record queries apply the same rule. A direct detail
  request cannot bypass row visibility.
- 员工工作台使用固定接口：`GET /zsjos/lead/inbox/submitted/page` 与 `/filter-profile` 只消费提交人方案，要求 `zsjos:lead:query-submitted`；`GET /zsjos/lead/inbox/owned/page` 与 `/filter-profile` 只消费负责人方案，要求 `zsjos:lead:query-owned`。成交审批使用独立的 `reviewer` 方案，通过 `/zsjos/sales-order/approval/filter-profile` 和 `inbox-page` 消费待处理/已处理及报名履约中心/财务结算中心审批环节；页面不允许用前端选项扩大服务端返回的任务范围。
- 通用 `GET /zsjos/lead/page` 继续服务管理端；一旦请求携带 `audience`，Service 仍校验对应视角权限，前端隐藏控件不能代替授权。
- 一旦指定视角，`submitter` 必须限定 `lead.source_user_id = currentUserId`，`owner` 必须限定 `lead.owner_user_id = currentUserId`；`query-all` 不得把“我的”视角扩大成全租户数据。未指定视角的通用管理查询继续遵循原有 `query-all` 或提交人/负责人关系范围。
- `zsjos_lead_inbox_filter_scheme` 保存租户级草稿和当前已发布配置，`zsjos_lead_inbox_filter_version` 保存不可变发布快照。列表查询和数量统计只消费已发布版本；保存草稿不影响工作台，回滚通过复制历史快照并发布新版本完成。
- 管理端只能从后端按视角返回的条件能力白名单选择字段和值，不得提交 SQL、列名或任意表达式。`submitter` 与 `owner` 只允许客资主状态和分配状态；`reviewer` 只允许处理状态和 BPM 任务节点。不同视角的字段不得混用。
- 收件箱归类是对客资主状态和分配状态的只读投影，不是新的持久化状态。前端只能展示服务端返回的筛选项，不得自行补齐尚未实现的跟进、申诉、机会或订单状态。
- 客资状态由后端拆分投影：`qualificationStatus` 表示待判定/已判有效/已判无效，`followUpStatus` 表示待首跟/跟进中/成交待审核/已成交，`assignmentStatus` 表示分配生命周期，`operationalStatus` 表示挂起等控制状态。前端不得根据 `status`、分配字段或机会状态自行拼装按钮和用户状态；写操作只能消费 `availableActions`。
- Full submitted mobile and WeChat values are returned to an authorized submitter,
  owner, or `query-all` administrator. Frontends must not broaden that authorization.
- `query-all` is an explicit permission-based bypass for the current tenant; it is
  never inferred from a role, post, department, or display name.
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
- ZSJOS owns order, item, immutable round snapshot and business status. A process result listener maps BPM approval to `order.status.effective` and Opportunity `won`, or rejection/cancellation to `order.status.revision_required` and Opportunity `following`.
- 审批人视角的筛选方案沿用客资筛选方案的草稿/发布版本机制，audience 固定为 `reviewer`，能力值仅允许 `handled=todo|done` 和 `task_definition_key=registrationReview|financeReview`。筛选项稳定编码使用小写下划线格式 `registration_review` / `finance_review`，与保持 BPM 契约的驼峰条件值相互独立；读取历史配置时兼容旧筛选项编码，并在下一次保存或发布时规范化。列表查询先在订单域按订单号、学员姓名或手机号解析流程实例集合，再将租户、流程定义、任务节点和流程实例条件传给 BPM，确保统计、分页和对象授权一致。
- 工作台业务附件选择后先保留本地文件和预览地址，确认提交时才通过 Infra 文件 API 上传 COS；任一上传失败都不会发送业务命令，成功引用和失败项会保留以便重试。删除只移除当前表单引用，不物理删除已上传文件。
