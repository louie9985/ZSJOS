# Maintenance Mode API

Maintenance mode is one global, database-authoritative System setting. It is not tenant-scoped.

## Read state

`GET /admin-api/system/maintenance-mode` is public and returns `{ "enabled": boolean }` inside the standard response wrapper.

## Change state

`PUT /admin-api/system/maintenance-mode` accepts `{ "enabled": boolean }`. Only an authenticated user with the stable `super_admin` role may call it. The operation is audited by the existing System operation-log facility.

When enabled, `GET`, `HEAD`, and `OPTIONS` remain available. Other requests return HTTP 503 with the stable message `系统维护中，请稍后再试`, except the fixed authentication, SMS/social callback and maintenance-toggle routes required for access recovery. There is no role or IP bypass for ordinary writes.

Schedulers that mutate Lead, business-task, or work-plan state return before tenant scanning while maintenance is enabled. Deadlines continue to use natural time and are not extended. Real enterprise-WeChat delivery is outside the current implementation scope.
