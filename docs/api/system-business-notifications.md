# Configurable business notifications

## Contract

Business modules register notification scenes in code through `NotifySceneProvider` and publish
typed `NotifyBusinessEvent` values through `NotifyBusinessEventApi`. Administrators configure
tenant rules against that catalog; arbitrary request interception and executable expressions are
not supported.

The ZSJOS catalog contains 20 lead scenes: created/activated, assigned/reassigned,
accepted/rejected, acceptance expired, public-pool entry, claimed, administrator transfer,
follow-up recorded, category changed, qualification suspension and its four disposition results,
plus appeal submission, overturn, and uphold. The scene response is the source of truth for
available variables, recipient roles, sensitive markers, and actions.

Fresh environments and migration V016 provide one enabled global template for every registered
scene. Templates do not send messages by themselves: each tenant must still create and enable its
own notification rules. V016 inserts only a missing active template code and never overwrites an
administrator-created or modified template.

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
metadata. Templates support `{{lead.id}}` dotted variables and retain compatibility with legacy
`{name}` placeholders. A scene template is rejected when it references variables outside that
scene's published catalog.

## Actions and realtime delivery

Allowed action codes are `message_detail`, `business_detail`, and `none`. In the current scene
catalog they mean message details, authorized lead details, and close-only respectively; no URL is
stored or executed.

The backend persists the rendered title, summary, full content, rule, scene, business identity,
action, and source event key. After commit it emits:

```json
{ "type": "notify-message-new", "content": { "messageId": 123 } }
```

Clients treat this as an invalidation hint, fetch `/system/notify-message/my-get`, deduplicate by
message ID, and display title plus summary at the bottom right. Clicking marks the message read and
refreshes the bell. A lead action verifies the real business API first; absent menu or object access
falls back to message details with a `Message` explanation.

The separate `zsjos_lead_assignment` event remains a lightweight prompt-refresh channel for the
pending-assignment modal. Clients reload pending assignments and retain polling as the disconnect
fallback.

## Proxy requirements

Development and production proxies must forward `/infra/ws` directly to the backend with HTTP/1.1
WebSocket Upgrade and Connection headers. `/infra/ws` is not under `/admin-api`. A release check
must confirm a `101 Switching Protocols` response and delivery across backend nodes when a shared
sender is configured.
