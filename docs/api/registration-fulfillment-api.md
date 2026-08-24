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

When `registrationReview` first passes, the service creates the order-unique case and publishes
`zsjos.registration.task_created`. The default in-app rule resolves the intersection of enabled
users who hold `zsjos:registration:query-pool` and enabled users in the configured registration
approval department subtree (including the configured root department). System persists the message and emits the existing post-commit
WebSocket hint.

When a case is assigned to a study planner, `zsjos.registration.planner_assigned` sends the
assigned planner the message `客资{{lead.no}}已分配给你。` and emits the same
post-commit WebSocket refresh hint. Reassignments use a planner-specific event key, while a
repeated assignment to the same planner is idempotent.
The assignment event is published only when `studyPlannerUserId` actually changes; a new command key
that writes the same planner does not create another notification.

When completion assigns a content director, `zsjos.registration.director_assigned` creates one durable
in-app message and the standard post-commit WebSocket hint for that director.

## My Students

- `GET /zsjos/student/my-page`
- `POST /zsjos/student/my/search-page`
- `GET /zsjos/student/my/{personId}`
- `GET /zsjos/student/my/by-service/{relationId}`

“我的学员”响应按 Person 聚合，但 `services` 中每一项同时返回该服务真实订单所关联的 `leadId`（仅内部技术链接）和 `leadNo`（用户可见业务编号）。Workbench 使用当前服务项的 Lead 复用统一 `LeadDetail`，不得以 Person 下其他服务或其他 Lead 作为回退。学习规划师视角以订单商品快照展示“成交产品”，不展示销售“意向产品”；最近联系、联系历史、下次联系时间和联系任务时效均按当前 `serviceRelationId` 使用学员联系接口，不读取或展示 Lead 销售跟进。订单等获准历史仍为只读页签。`contact-context.availableActions` 明确投影 `ACCEPT`、`FIRST_CONTACT`、`STUDY_PLAN`、`FOLLOW_UP`、`EDIT_BASIC_INFO`、`ASSIGN_CONTENT_DIRECTOR`、`ASSIGN_CAREER_PLANNER`；未接收的负责人只可能获得 `ACCEPT`，已接收后才投影当前唯一阶段动作和获准辅助操作。前端投影与 Controller 功能权限、服务关系对象权限必须同时成立。

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
Registration notifications identify the business object with `leadNo` or `orderNo`; customer/student names are not included in titles, summaries, bodies or structured notification parameters. Planner and director assignment messages retain the Lead business number when available and may use the order number for an order without a Lead relation.
For databases where V085 was already applied, V087 forward-repairs missing registration business-number parameters and any safely resolvable residual `student.name` snapshot. V087 never substitutes an internal Lead ID and blocks when the tenant-scoped order/Lead relation cannot provide a stable business number.

## Student acceptance and contact chain

Each active service relation is accepted independently through `/zsjos/student/service/{serviceRelationId}/accept`. Acceptance creates the first-contact task. First contact, study plan, and recurring contact use separate read/submit contracts; successful first contact advances to study plan, successful study plan advances to recurring contact, while failed first/study submissions repeat their current task type. Every submission stores an immutable record and requires a future next-contact time and remark. Failure additionally requires `zsjos_student_contact_unsuccessful_reason`; a next time beyond the published first-contact or study-plan interval requires `zsjos_student_contact_extension_reason`, description, and BPM approval under process key `zsjos_student_contact_extension`.

`PUT /zsjos/student/service/{serviceRelationId}/basic-info` accepts `name`, `mobile`, `wechatId`, and required `reason`. It requires `zsjos:student:update-basic-info`, an accepted active service, and the current service owner. Mobile and WeChat cannot both be blank; Person contact uniqueness and mobile format are revalidated. The command updates only the Person identity master and writes a `student_basic_info_updated` event containing changed field names, operator, reason, and Person/service references without full contact values. Lead submissions, intended products, regions, categories, orders, and historical snapshots are not rewritten.

Contact-record and extension-history reads use the standard `pageNo`/`pageSize` contract. Extension history also accepts `statusScope=pending|history|all`, with deterministic `submittedAt,id` ordering. Attachment uploads are scoped to `/zsjos/student/service/{serviceRelationId}/attachments`; submitted file references must have been created by the same operator in that service relation's upload namespace. Contact submissions and configuration copy, update, and publish commands require an idempotency key; replays must match the original immutable request fingerprint as well as the relation/task or configuration identity and expected version. Existing tasks continue to render and validate against their captured configuration version rather than the latest published configuration.

The overview exposes optional one-time content-director and career-planner assignments only after acceptance. Candidates come exclusively from the corresponding `/system/user-relation` scene. These assignments create no task and do not gate contact work. Contact tabs and records remain scoped to the selected service relation; collaborator visibility is projected by the published tenant configuration.

## Content director students

- `GET /zsjos/media-students/page` returns the current director or operator scope. Directors are resolved from active service relations and account responsibility; operators are resolved only from accounts and media workflow tasks they participate in.
- `GET /zsjos/media-students/{personId}` returns course services plus third-party accounts, positioning history, content production history, talk records, the media operation timeline, and the positioning-to-operation-to-graduation task line.
- Both endpoints require `zsjos:media-student:query-my`. Page visibility never grants object access: detail and command endpoints recheck the current service relation, account responsibility, or task assignment.
