# Async Export and Business Audit API

ZSJOS exports are server-owned asynchronous tasks. The supported type catalog is fixed to `lead`, `order`, `finance_order`, `cashback`, `withdrawal`, and `exam_calendar`; each type has its own create permission. A task stores the submitted filter and permission snapshot, but access remains creator-only and the type permission is rechecked before download. Exam exports require an inclusive date range, preserve current exam visibility and recheck exam query permission; see [exam export fields and permissions](exam-calendar.md).

`POST /admin-api/zsjos/export-task` creates a queued task. `GET /admin-api/zsjos/export-task/page` returns only the current user's tasks. `POST /admin-api/zsjos/export-task/{id}/cancel` conditionally cancels a queued or active task. `GET /admin-api/zsjos/export-task/{id}/download-url` returns a five-minute private-file URL only for an unexpired ready task and is forbidden during read-only impersonation.

Workers use a 30-minute lease and at most three attempts. Retry delays are 30 then 60 seconds. Generation is limited to 100,000 rows; ready files expire after seven days and terminal tasks inactive for 90 days are logically deleted. Cancellation, worker completion, retry, failure, and expiry all use status-and-version conditional updates so only the first concurrent transition succeeds.

Generated files include exporter, export time, and task number as metadata columns, including when the business result is empty. The registered spreadsheet providers use the ZSJOS-owned domain services and the repository Excel facility; unsupported types are rejected rather than producing placeholder files.

The six supported providers are registered by the ZSJOS module. `lead` accepts the existing lead-management filter and applies the creator's current lead visibility. `order` accepts the current user's submitted-order filter and exports only orders submitted by that user. `finance_order` requires `zsjos:export:finance-order` plus current membership in the configured finance-center department tree, checked both when the task is generated and when it is downloaded. It ignores BPM todo/done grouping and exports the complete tenant finance ledger matching the keyword and advanced filter. Advanced filters are compiled into tenant-scoped, paginated SQL; the service does not materialize a potentially unbounded matched-ID list. It batches current-round BPM node summaries and never exports voucher files or URLs. `cashback` and `withdrawal` require their dedicated export permissions; withdrawal exports additionally require configured management-query permission at creation, execution and download and use the full application-time card snapshot (2026-09-29 user-approved contract). Both frontend entry points require export-task query permission for retrieval. Personal and Partner projections remain masked. See [withdrawal export fields](withdrawal-and-offline-payout.md). Provider-specific filters are validated before a task is created.

Workers rebuild a temporary security context for the task creator, recheck that the account is enabled and still has the type-specific export permission, and always restore the worker thread's previous context. Revoked access terminates the task with `PERMISSION_REVOKED` instead of retrying. Domain list services remain authoritative for visibility and tenant filtering.

`GET /admin-api/zsjos/business-audit/page` requires `zsjos:audit:query` and queries the fixed business category/action catalog. `GET /admin-api/zsjos/business-audit/impersonation-page` requires `zsjos:audit:query-impersonation` and queries the separate impersonation request log. Audit details reject sensitive contact/card keys and never persist filters, exported file content, query strings, request bodies, or response content.

Flowable timer jobs restore the persisted job tenant before resolving process instances,
tasks, models, or timeout actions. Their async execution adapter carries that tenant into
the execution audit hook and restores the worker thread afterward. A failed notification
or automatic timeout action is recorded as `FAILURE` while Flowable retains its normal job
retry behavior; a missing HTTP request tenant is never replaced with a default tenant.

Since 2026-10-14, the Zhongshijian BPM configuration binds the audited Runnable factory
both to the engine configuration and to a preconfigured `AbstractAsyncExecutor` before
startup. Flowable 8 does not copy the engine factory into a Spring-provided executor.
Unsupported executor implementations fail configuration explicitly. The
`SelfAuditingRunnable` marker prevents a second audit from the shared task decorator.
The job's positive numeric tenant is authoritative (the deployed business tenant is 1).
Missing, blank or malformed job tenants are rejected inside Flowable's job failure
boundary, before business execution, so its existing retry handling still applies.
No submitting-thread tenant or default is substituted for business job execution.
Jobs without a valid tenant remain rejected; their failure audit may use the configured
single tenant under the audit-only rule below, without executing or replaying the job.

The async starter registers `taskExecutor` as an alias of `applicationTaskExecutor`
after bean definitions are available, unless a default executor already exists.
Unqualified `@Async` calls therefore use the existing pool and its TTL/audit decoration;
pool sizing and scheduler ownership are unchanged. Audit callback failures do not
change the business return value or replace the original job exception.

Deployment verification must check the loaded build, the actual executor factory,
one real tenant-1 job and its single result audit, and ordinary `@Async` routing.
Build/test success is not deployment acceptance. No migration or historical audit
backfill is required; rolling back the backend artifact restores the previous behavior
and may reintroduce the audit failure. Quartz recovery/misfire INFO messages alone are
not evidence of this defect.

Quartz completion auditing runs outside the `@TenantJob` business scope. When a
`SYSTEM_JOB` or `ASYNC` audit has neither an explicit execution tenant nor an ambient tenant,
the ZSJOS audit hook resolves and validates the single tenant returned by
`TenantFrameworkService`, writes inside `TenantUtils.execute`, and restores the
worker context afterward. Empty, invalid or ambiguous tenant configuration rejects
the audit write. This includes tenantless system async work such as Quartz's async
log update, which the shared decorator sees as a `FutureTask`. The confirmed Zhongshijian
business tenant is 1; its identity and enabled status are still resolved through the
tenant service rather than globally defaulting worker threads. Only the audit write
enters that tenant scope, after the business action has finished. Explicit execution
tenants retain precedence over ambient tenants; Redis execution types keep their
existing behavior. Audit callback failures remain isolated from
the Quartz business result and retry policy. The outer Quartz result still follows
the existing tenant-job aggregation behavior and does not prove individual issuance
success. No historical audit backfill or business-job replay is performed.
