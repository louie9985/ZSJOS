# Configurable business notifications

## Contract

Business modules register notification scenes in code through `NotifySceneProvider` and publish
typed `NotifyBusinessEvent` values through `NotifyBusinessEventApi`. Administrators configure
tenant rules against that catalog; arbitrary request interception and executable expressions are
not supported.

The ZSJOS catalog contains 41 lead scenes covering creation, dispatch and ownership, follow-up,
qualification, appeal, complaint, public-pool, duplicate, and transfer workflows. Complaint
decisions use distinct `zsjos.lead.complaint_founded` and `zsjos.lead.complaint_unfounded` scenes.
Both resolve the complaint record's actual employee or partner complainant; the founded scene also
retains the snapshotted owner and current direct-leader recipients. The scene response is the source
of truth for available variables, recipient roles, sensitive markers, and actions.

`zsjos.lead.submitter_assist_requested` is published when the current owner records the first
follow-up as `unreachable` for a `submitted + owned` Lead. Its `submitter` recipient resolves to the
original internal `lead.sourceUserId`; for partner-submitted Leads it resolves through the bound
partner account. The template uses `{{lead.no}}` for the user-visible Lead number and may include the
follow-up result, remark, and occurrence time. Internal submitters also receive a ZSJOS
`lead_submitter_assist` business task with action `OPEN_LEAD_SUBMITTER_SUPPLEMENT`; partner
submitters receive only the configured message because partner IDs are not ADMIN user IDs.

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

`in_app` rules use `system_notify_business_outbox` and join the publishing business transaction.
The unique boundary is tenant + source event key + target rule. Workers claim rows with a unique
`claim_token`; completion and failure updates must still own that token, so an expired worker cannot
overwrite a newer claim. Retryable failures use 1, 5, and 30 second delays; permanent failures stop
immediately. Successful rows are retained for 30 days, failed rows for 90 days, and rendered in-app
messages for three years. WebSocket delivery remains an after-commit invalidation emitted from the
persisted in-app message. Other configured channels remain after-commit best effort and are not
covered by the durable delivery guarantee.

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
the Lead appeal tab only as its authorized fallback. Submitter-assist message clicks use the same
authorized Lead overview fallback; the dedicated Workbench todo action opens the submitter
supplement panel directly.

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
