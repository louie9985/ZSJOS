# Configurable business notifications

## Contract

Business modules register notification scenes in code through `NotifySceneProvider` and publish
typed `NotifyBusinessEvent` values through `NotifyBusinessEventApi`. Administrators configure
tenant rules against that catalog; arbitrary request interception and executable expressions are
not supported.

The ZSJOS catalog contains 42 lead scenes covering creation, dispatch and ownership, follow-up,
qualification, appeal, complaint, public-pool, duplicate, and transfer workflows. Complaint
decisions use distinct `zsjos.lead.complaint_founded` and `zsjos.lead.complaint_unfounded` scenes.
Both resolve the complaint record's actual employee or partner complainant; the founded scene also
retains the snapshotted owner and current direct-leader recipients. The scene response is the source
of truth for available variables, recipient roles, sensitive markers, and actions.

Fresh environments and migration V016 provide one enabled global template for every registered
scene. Templates do not send messages by themselves. Notification rules remain tenant-owned. V075
creates the initial `zsjos.lead.created` rule only when a tenant has no rule for that scene. V080
splits an untouched V075 default into an operator-only submission-success rule and a separate
`new_media_provider` rule. The latter is resolved only when a salesperson submits a self-sourced
Lead with an explicitly selected new-media provider, and renders
`{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。`.
Without that selection, no provider notification is produced. Ordinary new-media submissions also
do not resolve this recipient role. V080 preserves enabled, disabled, or edited administrator rules
and does not backfill historical messages. V016 and V080 insert only missing active template codes
and never overwrite administrator-created or modified templates.

V177 creates a separate `wecom` template and rule for each non-deleted business `in_app` template
and rule present when the migration runs. It preserves the source rule's status, recipients, action,
and timing fields, is repeatable, and does not enable the tenant WeCom channel or user push preference.
Generated rules affect only future business events; historical in-app messages are not re-sent.

`in_app` and `wecom` rules use `system_notify_business_outbox` and join the publishing business transaction.
The unique boundary is tenant + source event key + target rule. Workers claim rows with a unique
`claim_token`; completion and failure updates must still own that token, so an expired worker cannot
overwrite a newer claim. Retryable failures use 1, 5, and 30 second delays; permanent failures stop
immediately. Successful rows are retained for 30 days, failed rows for 90 days, and rendered in-app
messages for three years. WebSocket delivery remains an after-commit invalidation emitted from the
persisted in-app message. SMS and other external channels remain after-commit best effort.

WeCom uses the existing payload JSON with `deliveryFormat=wecom-outbox-v1`; no schema change is
required. The first worker freezes typed recipients and rendered content, and prepares each click
URL before sending. Recipient checkpoints require the current tenant, claim token and an unexpired
lease, and renew the lease. A successful or permanently failed recipient is never resent because
another recipient failed. Disabled rules stop pending delivery; current channel/application settings
and recipient push preferences are still checked at send time. An explicitly disabled dedicated
application does not fall back to the ADMIN application.

Recipient states are `pending`, `sending`, `succeeded`, `skipped`, `failed`, and `uncertain`.
Unbound/opted-out recipients are `skipped`, not provider-confirmed delivery. Explicit transient
provider rejection follows the outbox retry schedule; explicit invalid-token rejection may refresh
and resend once. POST timeout, malformed response, or interrupted `sending` becomes `uncertain`
and is not automatically resent, because provider acceptance cannot safely be excluded. This
prevents blind duplicate sends but does not promise exactly-once delivery or automatic recovery
of uncertain sends. The one-time 30-minute click-ticket contract is unchanged.

For `publishConfirmed`, WeCom success (`externalId=WECOM_QUEUED`) confirms durable queue acceptance,
not provider delivery. Scheduled reminder deduplication records that acceptance. Inspect the outbox
for eventual delivery results. Other channels retain their existing confirmation behavior.

`GET /system/notify-rule/delivery-page` requires `system:notify-rule:query` and the current tenant.
It accepts `pageNo`, `pageSize`, `ruleId`, `sceneCode`, and outbox `status` (`pending`, `processing`,
`succeeded`, `failed`). It returns channel, rule, timestamps, retry count, sanitized error code and
per-recipient typed ID/status/attempt count/error code. Message bodies, original business payloads,
provider credentials and click tickets are never returned. This additive API does not change existing
Admin or Workbench rule/message response contracts or assign permissions. No delivery-management UI
or automatic/manual resend command is introduced.

## HTTP APIs

All paths below are under the administration API prefix and use the standard response envelope.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/system/notify-scene/list` | Registered scene and variable catalog |
| `GET` | `/system/notify-rule/page` | Current-tenant rule page |
| `GET` | `/system/notify-rule/get?id=` | Current-tenant rule detail |
| `POST` | `/system/notify-rule/create` | Create and validate a rule |
| `PUT` | `/system/notify-rule/update` | Update and validate a rule |
| `DELETE` | `/system/notify-rule/delete?id=` | Delete a rule |
| `PUT` | `/system/notify-rule/update-status` | Enable or disable after validation |
| `GET` | `/system/notify-message/my-get?id=` | Read one message owned by the current user |

Existing template and message APIs include `title`, `summary`, `sceneCode`, and controlled action
metadata. Lead templates use `{{lead.no}}` for the user-visible 客资编号. `{{lead.id}}` remains a
compatibility-only internal identifier and is labeled 内部客资ID in the variable catalog. Templates
retain compatibility with legacy
`{name}` placeholders. A scene template is rejected when it references variables outside that
scene's published catalog.

## Actions and realtime delivery

Allowed action codes are `message_detail`, `business_detail`, and `none`. In the current scene
catalog they mean message details, an authorized business action, and close-only respectively; no
URL is stored or executed. Workbench derives the Lead destination from the registered `sceneCode`
and internal business ID. Appeal scenes target `tab=appeals`, complaint result scenes target
`tab=complaints`, follow-up and reminder scenes target `tab=follow-ups`, and other Lead scenes target
`tab=overview`. The resulting route is
`/zsjos/leads/manage?leadId={internalLeadId}&tab={overview|follow-ups|orders|appeals|complaints}`.
`zsjos.lead.appeal_submitted` keeps its reviewer-inbox action when that task is available and uses
the Lead appeal tab only as its authorized fallback.

The backend persists the rendered title, summary, full content, rule, scene, business identity,
action, and source event key. After commit it emits:

```json
{ "type": "notify-message-new", "content": { "messageId": 123 } }
```

Clients treat this as an invalidation hint, fetch `/system/notify-message/my-get`, deduplicate by
message ID, and display title plus summary at the bottom right. Realtime cards, the bell popup, and
the full message center all execute the same persisted-message action resolver. Clicking marks the
message read and refreshes the bell. A Lead action verifies the real business API first and consumes
its server-projected `visibleTabs`; a requested hidden tab falls back to the overview rather than
bypassing permission. Absent menu or object access falls back to message details with a `Message`
explanation.

V090 adds separate founded and unfounded complaint-result templates and, for each non-deleted
tenant lacking an equivalent rule, one enabled `in_app`/`business_detail` rule addressed to
`complainant`. It uses `lead.no` and `complaint.handlerOpinion`, preserves existing rules and
historical messages, and does not itself execute against an existing environment.

The bottom-right popup duration is tenant-configured through the lead follow-up rule and defaults
to five minutes; the accepted range is 1 to 30 minutes. This does not control the pending-assignment
modal. Assignment, reassignment, acceptance, and claim completion do not publish business-message
events. Their historical scene catalog entries remain for configuration and existing-message
compatibility, while `zsjos_lead_assignment` alone refreshes the functional assignment modal.

The separate `zsjos_lead_assignment` event remains a lightweight prompt-refresh channel for the
pending-assignment modal. Clients reload pending assignments and retain polling as the disconnect
fallback.

## Proxy requirements

Development and production proxies must forward `/infra/ws` directly to the backend with HTTP/1.1
WebSocket Upgrade and Connection headers. `/infra/ws` is not under `/admin-api`. A release check
must confirm a `101 Switching Protocols` response and delivery across backend nodes when a shared
sender is configured. Browser clients authenticate the handshake with the current OAuth access
token in the `token` query parameter; refresh tokens are only used by the HTTP token-refresh flow.
Business notification templates for ZSJOS Lead, sales-order and registration scenes must use business identifiers rather than customer or student names. Lead scenes expose `lead.no`; sales-order scenes expose `order.no`; registration scenes expose `lead.no` and/or `order.no` according to their business relation. The scene registry rejects the former `lead.name`, `order.studentName` and `student.name` variables, and delivery fails closed for stale templates until they are migrated.

Sales-order supervisor confirmation adds two tenant-scoped scenes: `zsjos.sales_order.supervisor_requested` (recipient role `supervisor`) and `zsjos.sales_order.supervisor_decided` (recipient role `requester`). Their payload includes `order.no`, request center, requester/supervisor names, separate request and decision reasons, decision, confirmation ID and controlled task identifiers. Message clicks resolve those identifiers through the ZSJOS notification-target API, which rechecks the persisted recipient relationship and order object permission before returning a relative approval target. The first scene links the designated supervisor to the supervisor-confirmation view; the second links only the requester to the ordinary approval view. V093 creates a default only when the tenant has no rule for that scene, preserving administrator rules and avoiding duplicate delivery by confirmation event key.

Applied V085 databases are repaired only by forward migration V087; V085 is not rewritten. V087 also covers logically deleted message snapshots, restores the missing registration business-number parameter, and fails closed when the stored JSON or tenant-scoped business relation cannot be resolved safely. It does not infer a removed name or expose an internal Lead ID.
### Lead 提交人协助

| Scene | 默认收件人 | 说明 |
| --- | --- | --- |
| `zsjos.lead.submitter_assist_requested` | `submitter` | 向冻结的内部提交人或 Partner 提交账号发送协助请求主消息 |
| `zsjos.lead.partner_assist_reminder` | `partner_owner` | 仅兼职提交时发送给当前所属员工或 Lead 历史归属员工，提醒其转告兼职人员 |

两个 scene 提供 `assist.requestId`、`assist.problem`、`assist.expectedAssistance`、
`assist.remark`、`assist.attachmentNames` 变量，并继续使用 `lead.no` 作为用户可见客资编号。

## 编导与运营通知补全

场景、默认接收人、可配置提醒和双端消费边界见 [编导与运营业务通知](director-operator-notifications.md)。V269 增加缺失站内配置，并为尚无企微规则的业务场景复制站内规则及模板；保留已有企微场景配置（含停用规则）、个人推送偏好及历史消息。两个渠道仍独立投递、独立维护，不提供自动送达同步保证。

### Partner operator identity

Lead creation (including approved duplicate submissions) and contact activation freeze `operatorUserType` in the event payload from the authenticated submission identity. Operator recipients retain PARTNER account IDs or ADMIN user IDs even when their numeric values coincide. Explicit unknown types are not converted to ADMIN; legacy employee events without the field retain their existing interpretation. Partner operator and submitter display names resolve through Partner accounts and Partner records, never through a same-number employee. Historical rendered messages and deliveries are not rewritten or replayed.

### Withdrawal and Partner WeCom destinations

Unapplied V269 additionally supplies defaults for the five existing withdrawal scenes. `submitted` and `finance_reminder` target `finance`; `approved`, `rejected`, and `paid` target `applicant` and `finance`. Existing rules, including disabled rules, remain authoritative. The finance summary uses `none`; record-specific rules use `business_detail`. New templates use the business `withdrawal.no`, amount, and rejection reason where applicable; `withdrawal.id` remains a compatibility-only internal ID. New tenants initialize these defaults through System's existing API, including matching WeCom templates. No cancellation or cashback-maturity notification policy is introduced.

The same V269 WeCom mirroring covers existing Lead submitter-feedback and supplement rules; no second copy or unconditional enablement is added. Shared test execution remains gated by V268, matching runtime deployment, refreshed configuration backup and scoped authorization; source and isolated SQL verification do not constitute test-server synchronization.

Partner `sales_order` WeCom business-detail tickets freeze the order's associated Lead ID only when that Lead belongs to the recipient's Partner account. They open the existing `/lead/{id}` H5 route, whose authenticated detail API rechecks current ownership and projects visible order records. Missing orders, missing links and mismatched ownership fall back to the message center. ADMIN destinations are unchanged. One-time consumption and configured short ticket TTL remain unchanged; the H5 in-app sales-order message still uses its existing list fallback.

### Partner in-app sales-order navigation

Partner message detail first authorizes the stored message against the current PARTNER account.
For `actionType=business_detail` and `bizType=sales_order`, the ZSJOS target service returns
`businessTarget=/lead/{id}` only when the order's Lead belongs to the authenticated Partner.
Missing/deleted/foreign relationships yield no target and a `targetUnavailableReason`; the H5 stays
on the readable message with that explanation. `message_detail` and `none` never expose a business
action. Normal route permission and authenticated Lead detail ownership checks remain in force.
Internal IDs appear only in technical routes, never as the displayed 客资编号.
