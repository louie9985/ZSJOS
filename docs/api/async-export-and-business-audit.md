# Async Export and Business Audit API

ZSJOS exports are server-owned asynchronous tasks. The supported type catalog is fixed to `lead`, `order`, `cashback`, and `withdrawal`; each type has its own create permission. A task stores the submitted filter and permission snapshot, but access remains creator-only and the type permission is rechecked before download.

`POST /admin-api/zsjos/export-task` creates a queued task. `GET /admin-api/zsjos/export-task/page` returns only the current user's tasks. `POST /admin-api/zsjos/export-task/{id}/cancel` conditionally cancels a queued or active task. `GET /admin-api/zsjos/export-task/{id}/download-url` returns a five-minute private-file URL only for an unexpired ready task and is forbidden during read-only impersonation.

Workers use a 30-minute lease and at most three attempts. Retry delays are 30 then 60 seconds. Generation is limited to 100,000 rows; ready files expire after seven days and terminal tasks inactive for 90 days are logically deleted. Cancellation, worker completion, retry, failure, and expiry all use status-and-version conditional updates so only the first concurrent transition succeeds.

Generated files include exporter, export time, and task number as metadata columns, including when the business result is empty. The registered spreadsheet providers use the ZSJOS-owned domain services and the repository Excel facility; unsupported types are rejected rather than producing placeholder files.

The four supported providers are registered by the ZSJOS module. `lead` accepts the existing lead-management filter and applies the creator's current lead visibility. `order` accepts the current user's submitted-order filter and exports only orders submitted by that user. `cashback` and `withdrawal` accept their finance-list filters and require their dedicated export permission. Withdrawal workbooks contain only the masked card number. Provider-specific filters are validated before a task is created.

Workers rebuild a temporary security context for the task creator, recheck that the account is enabled and still has the type-specific export permission, and always restore the worker thread's previous context. Revoked access terminates the task with `PERMISSION_REVOKED` instead of retrying. Domain list services remain authoritative for visibility and tenant filtering.

`GET /admin-api/zsjos/business-audit/page` requires `zsjos:audit:query` and queries the fixed business category/action catalog. `GET /admin-api/zsjos/business-audit/impersonation-page` requires `zsjos:audit:query-impersonation` and queries the separate impersonation request log. Audit details reject sensitive contact/card keys and never persist filters, exported file content, query strings, request bodies, or response content.
