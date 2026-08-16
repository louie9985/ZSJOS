# Read-only Impersonation API

ZSJOS impersonation is a temporary read-only view of an enabled System user. It never creates a login token and does not copy roles, departments, menus, or permissions into ZSJOS.

`POST /admin-api/zsjos/impersonation/start` requires `zsjos:impersonation:start`, an enabled target user, a non-empty reason, and a normal tenant context. It returns the session identifier and immutable administrator/target name snapshots. A user cannot target their own account.

The target selector uses System's `GET /admin-api/system/user/simple-list`. That contract already returns enabled users only and exposes the simple user fields (`id`, `nickname`, avatar, sex, and department metadata); clients must not require `status` or `username` fields or invent a second enabled-user filter.

The browser sends the returned identifier in `X-ZSJOS-Impersonation-Session` for subsequent ZSJOS reads. The server accepts only `GET`, `HEAD`, and `OPTIONS`, revalidates the original administrator and active session, changes the request identity to the target user before controller and method-permission evaluation, and writes a dedicated request audit containing only method and path. Query strings, request bodies, filters, and response content are not recorded.

`POST /admin-api/zsjos/impersonation/{id}/end` ends an active session owned by the current administrator. Sessions idle for 30 minutes expire through the minute scheduler; maintenance mode pauses that scheduler without extending the natural idle deadline. Impersonation cannot be combined with cross-tenant visit mode.

When either client receives business error `1900007002` (`IMPERSONATION_SESSION_INVALID`), it removes the stored session and synchronizes the visible impersonation state. The failed request is still returned to the caller and is never replayed automatically without the impersonation header, preventing an implicit switch back to administrator data scope.
