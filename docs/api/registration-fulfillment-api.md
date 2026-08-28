# 报名履约与学员 API

All endpoints are tenant-scoped Admin APIs and use the standard `CommonResult` wrapper. User-visible Lead values are always `leadNo`; internal IDs remain relationship and route identifiers.

## Checklist configuration

- `GET /zsjos/registration-checklist-config`
- `POST /zsjos/registration-checklist-config/draft/copy`
- `PUT /zsjos/registration-checklist-config/draft`
- `POST /zsjos/registration-checklist-config/publish`

Writes carry the template version and idempotency key. A version contains ordered checklist items and
ordered route options. Administrators can add, remove, reorder and enable/disable ordinary items,
choose manual-checkbox or attachment type, mark attachment items required, and map routes to System
department IDs plus the stable assignee type. The fixed `study_planner` item cannot be removed,
disabled, or renamed. New cases snapshot the published version and never follow later edits.

## Public pool

- `GET /zsjos/registration/pool-page?keyword=`：按订单号、学员姓名或手机号分页查询公共池。
- `POST /zsjos/registration/pool/search-page`：在报名履约公共池固定权限范围内组合关键词与 `advancedFilter`，先筛选再分页。
- `GET /zsjos/registration/{id}`
- `GET /zsjos/registration/study-planner-candidates`
- `GET /zsjos/registration/{id}/routes/{routeId}/candidates`
- `PUT /zsjos/registration/{id}/items/{itemId}`
- `PUT /zsjos/registration/{id}/study-planner`
- `PUT /zsjos/registration/{id}/routes`
- `POST /zsjos/registration/{id}/items/{itemId}/attachments`
- `DELETE /zsjos/registration/{id}/items/{itemId}/attachments/{attachmentId}`
- `POST /zsjos/registration/{id}/complete`

Every command carries `version` and `idempotencyKey`. Item and planner updates return the latest
registration detail, allowing clients to update one row without reloading the whole page. The
response keeps protocol codes such as `pending` and `pending_approval` for machine logic and adds
Chinese `statusLabel`, `orderStatusLabel`, `completionBlockCode`, and `completionBlockReason` for
display. Stable errors distinguish finance pending, finance revision required, stale versions,
reused keys, incomplete checklists, ineffective orders, invalid planners, terminal cases and
object authorization failures.

At least one snapshotted route must be selected. Study-planner candidates are enabled users holding
role code `study_planner` inside the selected department subtree; content-director candidates are
enabled users holding post code `content_director` inside that subtree. Completion revalidates every
selected assignee. Attachment items allow at most nine files, 20 MB each, in JPG/PNG/WebP/PDF/Word/
Excel formats; required attachment items must contain at least one file. Department and assignee names
are stored as completion-time snapshots on the case routes.

Attachment validation requires the detected MIME type to exactly match the file extension; unknown
`application/octet-stream` values and raw `application/zip` are not accepted as Office documents.
Each successful upload stores its exact attachment ID on the idempotency command. A replay returns
only that attachment after rechecking the case and checklist-item relationship; missing historical
results fail with `1_900_010_020` instead of guessing by file name and size. If Infra storage succeeds
but the business transaction fails before completion, the service invokes Infra's idempotent delete
compensation. User-requested attachment deletion continues to remove only the business reference and
does not physically delete the Infra file.
Workbench previews JPG/PNG/WebP images and PDF files in the registration detail. Word and Excel
attachments remain downloadable/openable but are not rendered in the application.

When `registrationReview` first passes, the service creates the order-unique case and publishes
`zsjos.registration.task_created`. The default in-app rule resolves the intersection of enabled
users who hold `zsjos:registration:query-pool` and enabled users in the configured registration
approval department subtree (including the configured root department). System persists the message and emits the existing post-commit
WebSocket hint.

When registration completion has created the student's service relations,
`zsjos.registration.planner_assigned` sends the assigned planner the message
`学员{{student.name}}（{{student.no}}）已分配给你。` and emits the same post-commit WebSocket refresh hint. Selecting or
changing the planner on an incomplete case does not notify. One completion publishes one event keyed
by registration case and planner even when the order contains multiple service items. The durable
message uses `bizType=student` and internal `personId` as its click target, so Workbench opens the
corresponding My Students detail independently of the current list page.

When completion assigns a content director, `zsjos.registration.director_assigned` creates one durable
in-app message and the standard post-commit WebSocket hint for that director.

## My Students

- `GET /zsjos/student/my-page`
- `POST /zsjos/student/my/search-page`
- `GET /zsjos/student/my/{personId}`
- `GET /zsjos/student/my/by-service/{relationId}`

“我的学员”响应按 Person 聚合，Person 与当前 `serviceRelationId` 共同构成详情主体。`services` 中每一项仅在该服务真实订单关联 Lead 时返回 `leadId`（内部技术链接）和 `leadNo`（用户可见业务编号）；复购等合法服务可以没有 Lead。Workbench 的统一学员详情在无 Lead 时仍展示 Person 档案、成交课程、联系上下文和获准标签/动作，并用 `personNo` 标识学员；不得伪造 Lead、借用同 Person 下其他服务的 Lead，或降级为缺少业务操作的简化详情。学习规划师视角以订单商品快照展示“成交产品”，不展示销售“意向产品”；最近联系、联系历史、下次联系时间和联系任务时效均按当前 `serviceRelationId` 使用学员联系接口，不读取或展示 Lead 销售跟进。真实 Lead 存在时，订单等获准历史仍为只读页签。`contact-context.availableActions` 明确投影 `ACCEPT`、`FIRST_CONTACT`、`STUDY_PLAN`、`FOLLOW_UP`、`EDIT_BASIC_INFO`、`ASSIGN_CONTENT_DIRECTOR`、`ASSIGN_CAREER_PLANNER`；未接收的负责人只可能获得 `ACCEPT`，已接收后才投影当前唯一阶段动作和获准辅助操作。前端投影与 Controller 功能权限、服务关系对象权限必须同时成立。接收、首联、学习计划、普通跟进、交付阶段、基础信息修改、协作者分配和复购录入成功后，Workbench 必须强制刷新列表投影并独立重载当前 Person 的当前 `serviceRelationId`；详情刷新不得依赖列表请求去重或当前分页命中，且刷新后必须保持当前课程服务选中。

Results are read-only, grouped by Person, scoped to active service relationships directly owned by or
routed to the current user, and aggregate order/course services without exposing sales actions. Both
assigned study planners and content directors use the same endpoints and page.
Route visibility requires a selected, tenant-matched route and an active, tenant-matched service
relationship. The technical Lead link is resolved through that directly owned service relation's
actual order; another Lead sharing the same Person never supplies the link.
The search endpoint applies the same owner/route scope before evaluating `advancedFilter`, performs
database pagination over matching Person IDs, and then assembles service details. Multiple positive
service conditions in one AND group must be satisfied by the same visible service relationship.
The response exposes `leadNo` as the only user-visible Lead number. When the current user directly
owns an active service relationship for the student, the detail also returns internal `leadId` solely
for loading the related Lead overview, follow-up history, and customer order history. Clients must not
render `leadId` as a customer-facing identifier. Routed collaborators that do not own the active service
relationship continue to receive the course-rights detail without this internal Lead link.
The three sales-history surfaces are read-only for the service owner. The student permission may invoke
the existing Lead detail, follow-up query, and customer-order query endpoints, but object authorization
still requires the active service relationship. It grants no Lead update, follow-up creation, qualification,
order creation, appeal, or complaint access. The Workbench student detail therefore renders only
`概览`, `跟进记录`, and `订单记录`; it does not render `申诉记录` or `投诉记录`.
Each service keeps the internal `productSnapshot` compatibility field and additionally returns
`courseName`, `skuName`, `categoryPath`, and `attributeValues` for user-facing display. Clients
must render these structured fields and must not display the raw JSON snapshot.
Registration notifications identify the business object with `leadNo` or `orderNo`; planner assignment messages use the student name and `personNo` because the recipient is being assigned a student, not a Lead. Newly created student numbers use `XYyyyyMMddHHmmss` plus a four-digit sequence that resets daily per tenant in Beijing time and wraps from `9999` to `0001`; existing `P + UUID` values are preserved. Historical delivered-message snapshots remain unchanged.
For databases where V085 was already applied, V087 forward-repairs missing registration business-number parameters and any safely resolvable residual `student.name` snapshot. V087 never substitutes an internal Lead ID and blocks when the tenant-scoped order/Lead relation cannot provide a stable business number.

## Student acceptance and contact chain

Each active service relation is accepted independently through `/zsjos/student/service/{serviceRelationId}/accept`. Acceptance creates the first-contact task. First contact, study plan, and recurring contact use separate read/submit contracts; successful first contact advances to study plan, successful study plan advances to recurring contact, while failed first/study submissions repeat their current task type. Every submission stores an immutable record and requires a future next-contact time and remark. Failure additionally requires `zsjos_student_contact_unsuccessful_reason`; a next time beyond the published first-contact or study-plan interval requires `zsjos_student_contact_extension_reason`, description, and BPM approval under process key `zsjos_student_contact_extension`.

The delivery-stage projection advances directly from `study_plan` to `supervision`; there is no
planner group/handoff stage. V123 moves an existing current `group_handoff` projection to
`supervision` and clears only that relation's retired current-stage facts. Historical contact-record
snapshots are not rewritten.

`PUT /zsjos/student/service/{serviceRelationId}/basic-info` accepts `name`, `mobile`, `wechatId`, and required `reason`. It requires `zsjos:student:update-basic-info`, an accepted active service, and the current service owner. Mobile and WeChat cannot both be blank; Person contact uniqueness and mobile format are revalidated. The command updates only the Person identity master and writes a `student_basic_info_updated` event containing changed field names, operator, reason, and Person/service references without full contact values. Lead submissions, intended products, regions, categories, orders, and historical snapshots are not rewritten.

Contact-record and extension-history reads use the standard `pageNo`/`pageSize` contract. Extension history also accepts `statusScope=pending|history|all`, with deterministic `submittedAt,id` ordering. Attachment uploads are scoped to `/zsjos/student/service/{serviceRelationId}/attachments`; submitted file references must have been created by the same operator in that service relation's upload namespace. Contact submissions and configuration copy, update, and publish commands require an idempotency key; replays must match the original immutable request fingerprint as well as the relation/task or configuration identity and expected version. Existing tasks continue to render and validate against their captured configuration version rather than the latest published configuration.

The overview exposes optional one-time content-director and career-planner assignments only after acceptance. Candidates come exclusively from the corresponding `/system/user-relation` scene. These assignments create no task and do not gate contact work. Contact tabs and records remain scoped to the selected service relation; collaborator visibility is projected by the published tenant configuration.

## Content director students

- `GET /zsjos/media-students/page` returns the current director or operator scope. Directors are resolved from active service relations and account responsibility; operators are resolved only from accounts and media workflow tasks they participate in.
- `GET /zsjos/media-students/{personId}` returns Person/course context plus third-party accounts, positioning history, content production history, the media operation timeline, and the positioning-to-operation-to-graduation task line. The Workbench media surface renders only overview, account/positioning, and content tabs; it does not load Lead detail, contact history, or the separately retained legacy talk-record APIs, and it filters legacy `talk` events from the visible timeline.
- Both endpoints require `zsjos:media-student:query-my`. Page visibility never grants object access: detail and command endpoints recheck the current service relation, account responsibility, or task assignment.

Positioning cards use one mutable working draft plus immutable submitted field snapshots. Draft saves are
excluded from `positioningCards` and the operation timeline. Submission locks the active service relation
and requires an assigned, enabled operator before creating the next submission. Operator approval advances
only the latest submission to `student_link_pending`; the assigned operator can then generate or regenerate
the student link with `POST /zsjos/positioning-card/{id}/student-link` and permission
`zsjos:positioning-card:student-link-generate`. The command returns
`{ sharePath, expiresAt }`; `sharePath` remains the absolute H5 URL and `expiresAt` is the configured
server-side expiry boundary.

The anonymous student contract is `GET /public-api/zsjos/positioning-confirmation/detail` and
`POST /public-api/zsjos/positioning-confirmation/decision`. Both carry the opaque token only in
`X-Positioning-Token`; the share route stores it after `#token=`. Public responses expose account display
data, submission time, field/value/label snapshots and decision state, but no card number, internal IDs,
user IDs, or token. A decision, regeneration, or non-current submission invalidates the active link.
Expiry also invalidates it. Missing, expired, revoked, already-used, and unknown tokens deliberately share
one stable invalid-link error so callers cannot enumerate link state.
`request_changes` requires a 1-500 character comment and returns the card to the director draft stage;
`agree` atomically marks the current submission `confirmed`, supersedes the account's previous
`confirmed` submission, consumes the link, and completes the card round. A later edit starts through
`POST /zsjos/positioning-card/{id}/start-revision?version=...`; only the original director may restore
the effective submission snapshot into the same card's draft workspace. The prior effective submission
remains available to production-ticket creation until the revised submission is confirmed.

New submissions always follow `co_creating -> operator_feasibility -> student_link_pending ->
student_confirm -> confirmed`, including cards whose `professionalRisk` snapshot is true. New submissions
do not create an IP BPM instance. The IP result listener remains only for instances already in
`ip_review` before rollout. Historical `trial_14d` cards with `student_agreed` submissions are not
migrated; runtime reads treat a non-archived historical agreement as effective and allow its original
director to start a revision. Historical `archived`, `ip_rejected`, and other audit values remain read-only.

Generated links must be absolute and target the anonymous H5 page, not the authenticated Workbench.
The backend reads that origin from `ZSJOS_PUBLIC_H5_BASE_URL` and rejects link generation before any
token or workflow mutation when the value is absent or invalid. Production configuration and reverse
proxy requirements are documented in `docs/operations/positioning-confirmation-deployment.md`.
# 编导学员级阶段

编导通过 `GET /zsjos/student/service/{relationId}/contact-context` 获取服务关系负责人、编导、
运营负责人、`directorStage`、访谈预约时间、动态字段和服务端 `availableActions`。学习规划师
展示值来自服务关系 `ownerUserId/ownerUserName`，不得使用职业规划师字段替代。
被指派运营也可读取该上下文，且必须匹配服务关系当前 `operatorUserId`；服务端不下发接收、
首联、学习计划、普通跟进或基础资料修改动作。`/contact-records` 的只读授权边界保持兼容，
但媒体 Workbench 不请求或展示联系历史。运营的媒体业务操作仍通过账号、定位卡、内容和拍剪接口完成。

阶段命令拆分为四个接口，阶段由 URL 固定，客户端不能伪造阶段。草稿接口的 `version` 是对应阶段的独立草稿版本，不是服务关系全局版本；草稿保存不改变服务关系全局版本，正式提交才推进业务版本：

- `POST /zsjos/student/service/{relationId}/precheck/draft`
- `POST /zsjos/student/service/{relationId}/precheck/submit`
- `POST /zsjos/student/service/{relationId}/interview/draft`
- `POST /zsjos/student/service/{relationId}/interview/submit`

```json
{
  "interviewAt": "2026-08-28T10:00:00",
  "data": {},
  "version": 1,
  "idempotencyKey": "client-command-id"
}
```

预审不加载业务表单，`data` 必须为空；提交接口要求 `interviewAt`。采访草稿固定当前发布模板版本，
提交时服务端校验必填字段，并把模板 ID、模板版本、字段定义和值写入不可变快照。预审提交后进入 `interview`，
采访提交后进入 `positioning_ready`。所有写入均校验当前编导、服务状态和服务关系版本。

运营候选人与指派复用协作者接口，类型为 `operator`：候选人来自服务端配置的
`content_director_operator` 人员关系，source 为当前编导、target 为运营。运营归属写入同一学员
全部有效服务关系的 `operatorUserId`，并同步未归档定位卡的当前运营；运营只能读取被
指派学员全部账号与定位卡，并在具备定位卡操作权限时逐账号确认或退回。
#### Director form dictionary snapshots (V129)

Director interview and account-positioning enum fields use System dictionaries. Configuration
fields of type `select`, `multi_select`, `radio`, `checkbox_group` or `dict` must provide a
valid enabled `dictType`; the server validates both configuration and submitted values.
Submitted service-relation form payloads retain `dictSnapshots` with the selected value,
dictionary type and label at submission time. Historical detail must display the snapshot
label and must not re-resolve the current dictionary after an administrator renames or disables
an item. The V129 seed is repeatable and is not executed by application startup.
## 编导可配置表单

资料预审接口只接受空 `data`，用于确认资料和保存或提交采访预约。采访接口使用当前已发布的
`director_interview` 模板；首次草稿冻结模板版本，草稿和提交均保存字段、值及字典标签快照。
后续保存和提交始终按该 `templateVersionId` 的已发布或已归档版本校验；发布新模板不会迁移
已有草稿。未改变的历史字典选项继续保留原 `labelSnapshot`，即使当前字典已经改名或停用。
采访 `region` 字段从 System `/system/area/tree` 选择，业务值保存地区 `code` 与服务端生成的
`labelSnapshot`。旧草稿中的原始地区文本可以继续读取和保存草稿，但正式提交前必须重新选择
当前有效地区；服务端不信任客户端传入的地区标签。

模板管理接口为 `/admin-api/zsjos/director-interview-template/**`、
`/admin-api/zsjos/positioning-template/**` 和 `/admin-api/zsjos/director-config`。定位卡业务端通过
`GET /admin-api/zsjos/positioning-card/published-template` 获取当前发布模板，创建草稿时保存
`personId + accountId + serviceRelationId + templateVersionId` 及完整快照。

定位卡复用导入使用以下接口，并同时要求 `zsjos:positioning-card:create` 与
`zsjos:positioning-card:query`：

- `GET /admin-api/zsjos/positioning-card/import-sources?studentPersonId=...&accountId=...&serviceRelationId=...`
  返回当前编导对目标学员可读的全部已提交定位卡版本，包含当前账号和该学员其他账号；未提交草稿不进入候选。
- `POST /admin-api/zsjos/positioning-card/import` 将指定 `sourceSubmissionId` 导入目标账号草稿。
  目标已有草稿时必须携带 `targetDraftId + version`，并继续使用乐观锁；没有草稿时创建新的账号定位卡草稿。

导入始终使用当前发布模板，按稳定字段 `key` 复制类型兼容的值。新增字段留空，已删除、类型不兼容或
字典类型改变的字段跳过；未改变的字典选择沿用来源提交中的 value 与 label 快照。来源提交只读，导入
不会修改历史定位卡、提交序号、审核状态或学员决定，也不会导入历史试运行日期。

Workbench 的“导入 JSON”是定位卡草稿表单的本地辅助能力，不新增后端导入接口，也不读取
历史定位卡，因此只沿用填写定位卡所需的 `zsjos:positioning-card:create` 权限。浏览器接受
不超过 1 MiB 的 UTF-8 `.json` 文件或粘贴文本，原始文件不会上传。JSON 顶层必须是普通对象，
仅按当前草稿冻结模板的稳定字段 `key` 匹配，例如：

```json
{
  "identityTags": ["expert"],
  "strongStoryHook": "十年一线实战经验",
  "recommendedMatchRate": 85
}
```

字典字段必须使用 System 接口返回的稳定 `value`，不得使用展示 label。`null` 表示清空命中
字段；未提供、未知、附件、类型不符、字典无效或违反模板约束的字段不会覆盖当前值。界面先按
“可导入 / 将清空 / 已跳过”预览，确认后仅合并合法字段并立即调用现有定位卡草稿创建或更新
接口。后端仍按冻结模板版本重新校验完整草稿、解析字典 label 快照并执行对象权限与乐观锁检查。
