# 报名履约与学员 API

All endpoints are tenant-scoped Admin APIs and use the standard `CommonResult` wrapper. User-visible Lead values are always `leadNo`; internal IDs remain relationship and route identifiers.

## Checklist configuration

- `GET /zsjos/registration-checklist-config`
- `POST /zsjos/registration-checklist-config/draft/copy`
- `PUT /zsjos/registration-checklist-config/draft`
- `POST /zsjos/registration-checklist-config/publish`

Writes carry the template version and idempotency key. The fixed `study_planner` item cannot be removed, disabled, or renamed.

## Public pool

- `GET /zsjos/registration/pool-page?keyword=`：按订单号、学员姓名或手机号分页查询公共池。
- `GET /zsjos/registration/{id}`
- `GET /zsjos/registration/study-planner-candidates`
- `PUT /zsjos/registration/{id}/items/{itemId}`
- `PUT /zsjos/registration/{id}/study-planner`
- `POST /zsjos/registration/{id}/complete`

Every command carries `version` and `idempotencyKey`. Item and planner updates return the latest
registration detail, allowing clients to update one row without reloading the whole page. The
response keeps protocol codes such as `pending` and `pending_approval` for machine logic and adds
Chinese `statusLabel`, `orderStatusLabel`, `completionBlockCode`, and `completionBlockReason` for
display. Stable errors distinguish finance pending, finance revision required, stale versions,
reused keys, incomplete checklists, ineffective orders, invalid planners, terminal cases and
object authorization failures.

When `registrationReview` first passes, the service creates the order-unique case and publishes
`zsjos.registration.task_created`. The default in-app rule resolves enabled recipients from
`zsjos:registration:query-pool`; System persists the message and emits the existing post-commit
WebSocket hint. Repeated approval callbacks reuse the case and event key and do not notify again.

## My Students

- `GET /zsjos/student/my-page`
- `GET /zsjos/student/my/{personId}`

Results are read-only, grouped by Person, scoped to the current planner's active service relationships, and aggregate order/course services without exposing sales actions.
Each service keeps the internal `productSnapshot` compatibility field and additionally returns
`courseName`, `skuName`, `categoryPath`, and `attributeValues` for user-facing display. Clients
must render these structured fields and must not display the raw JSON snapshot.
