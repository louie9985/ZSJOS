# Ownership and Change Boundaries

Lead sales-to-submitter feedback belongs to the ZSJOS Lead domain, with immutable reply
records and tenant-owned file bindings. Infra stores files; System owns configurable
notifications and their delivery/read state. Workbench composes replies; Partner H5 reads
its own replies. This feature does not reuse the generic feedback work-order lifecycle.
See [the API contract](../api/lead-submitter-feedback.md).

## Ownership matrix

Student information collection is owned by the ZSJOS Lead domain, with Admin-only
configuration, Workbench Lead/student projections and public H5 token access. It consumes
System dictionary/area APIs and framework encryption; it does not change Lead or order
master data. See [the collection contract](../api/student-information-collection.md).

| Change | Primary owner | Required checks | Prohibited shortcut |
| --- | --- | --- | --- |
| Employee page or workbench shell | `frontend/workbench` | Authorized menu mapping, typed API, desktop/mobile UI | Duplicating the page in Vue without an admin use case |
| Administrator page or configuration | Vue administration frontend | Existing API/dictionary/permission utilities | Building an independent admin UI in the workbench |
| ADMIN/MEMBER identity, role, menu, dictionary, tenant, OAuth tokens | `yudao-module-system` and existing framework contracts | Existing public API and authorization behavior | Reimplementing shared identity truth in `yudao-module-zsjos` |
| PARTNER account, profile, and business-subject resolution | `yudao-module-zsjos`, consuming System OAuth APIs | Account/subject state, tenant isolation, typed user ID, object authorization | Treating Partner Account ID or Partner ID as a System user ID |
| Business notification templates, tenant rules, message snapshots, and delivery | `yudao-module-system` | Registered scene contract, ownership API, tenant isolation, idempotency | Letting a business module write System notification tables or accepting arbitrary URLs/expressions |
| Workflow and approval execution | `yudao-module-bpm` | Public API, stable process/business keys, status events, idempotent business update | Reimplementing process tasks or workflow history in `yudao-module-zsjos` |
| Product, SKU, and product-driven rule definition | Future approved product capability | Public rule-result contract, stable identifiers, versioning | Inferring rules from names or querying another module's DAL or tables |
| Existing CRM or other domain behavior | Owning business module | Public module boundary and data-scope rules | Cross-module DAL access or copied domain logic; see the approved ZSJOS lifecycle exception below |
| New Zhongshijian business behavior | `yudao-module-zsjos` | VO, Service, authorization, tenant, tests | Putting business rules in controllers or framework modules |
| Forced forms, versions, send batches, recipients and submissions | `yudao-module-zsjos`, consuming System and Infra APIs | Immutable versioning, range snapshot, dictionary snapshot, attachment binding, Workbench gate tests | BPM/task duplication, static options, or cross-module DAL access |
| Lead provider and performance attribution | `yudao-module-zsjos` Lead snapshots | First-count freeze, canonical provider authorization, no current-state historical inference | Reusing `source_*` or current Partner/department ownership as a fallback provider truth |
| Generic business tasks and work plans | `yudao-module-zsjos` | Command service, scene provider, object permission, state tests | Domain services writing task DAL directly or copying BPM task state |
| Frontend protocol constants | Shared protocol owner in that frontend | Backend contract alignment | Literal copies in page components |
| Feature-private constants | Owning feature or component | Local clarity and tests | Growing a global constants dumping ground |
| Administrator-maintained options | Dictionary or business API | Loading/empty/error behavior | Static production arrays |
| Initialization data | `script/sql/` when approved | Scope, ordering, relationships, repeatability | Executing destructive SQL without confirmation |
| Environment values and secrets | Environment configuration | Startup and proxy verification | Committing credentials or machine-specific URLs |

### ZSJOS lead, order, and service lifecycle exception

The Zhongshijian person, partner, lead, lead-activation, opportunity, order,
registration-service, student-service-relation, payment-order, payment-transaction
reference, order-payment-allocation, customer-account ledger, refund, task, and
business-event lifecycle is an independently owned ZSJOS domain. Its tables,
DAL, services, transactions, business state machines, and APIs belong to
`yudao-module-zsjos`; CRM Customer, Clue, Business, and Contract tables or domain
services are not runtime persistence or behavior dependencies for this lifecycle.

This is a deliberate domain-boundary decision caused by different lifecycle and
repurchase semantics, not permission to copy arbitrary existing modules. System-owned
departments, posts, dictionaries, tenants, OAuth tokens, and internal employee permissions
continue to come through the existing system contracts. Partner accounts and profiles are ZSJOS-owned
and use the shared OAuth token service through its public API. External payment
channels may execute funds movement and report channel payment facts, while ZSJOS owns
the lead-bound payment request, channel-transaction reference, order allocation,
customer account and immutable ledger, and refund business records. Channel identifiers
remain external references and do not replace ZSJOS business keys. Any future CRM
integration requires a separately approved public API or message contract and must not
introduce cross-module DAL access.

Product, SKU, and product-rule internals are outside this lifecycle document until their
owner and contract are separately approved. ZSJOS may consume a public rule-result
contract and must persist the stable product identifiers, resolved rule snapshot, and
rule version used for a payment request or order. ZSJOS must not infer approval,
registration, or service behavior from product names, prices, labels, departments, or
another module's private tables.

Approval execution is not part of this CRM exception. BPM owns process definitions,
instances, approval tasks, assignees, countersigning, rejection, cancellation, and
workflow history. ZSJOS owns the corresponding order, refund, or service business
record, its business status and snapshot, and BPM reference identifiers. ZSJOS starts
and observes BPM through public APIs and status events; it does not duplicate BPM task
state in ZSJOS tables. A workflow whose business state depends on an individual parallel
task result requires a BPM-owned public task-result event carrying stable process, task,
action, operator, occurrence-time, and idempotency identifiers.

## Backend boundaries

- Prefer an existing public API or service contract when one module needs another module's capability.
- Do not import another module's mapper or directly query its table from `yudao-module-zsjos`.
- Controllers own HTTP translation, validation entry, permission annotations, and response wrapping.
- Services own business policy, orchestration, and transaction boundaries.
- DAL owns persistence for data that belongs to the same module.
- Request and response VO types define the HTTP contract; persistence DO types stay internal.
- Distinct stable failures require distinct error codes when a caller can act differently.
- A business module owns its notification scene catalog, recipient and variable resolution, and
  transaction-bound event publication. System owns rule validation, rendering, message persistence,
  idempotency, and the post-commit WebSocket hint. Modules integrate through
  `NotifySceneProvider` and `NotifyBusinessEventApi`, never through cross-module notification DAL.
- Notification delivery preserves `{userType,userId}` end to end. System owns channel dispatch and
  typed SMS logging; an external identity owner may implement `NotifyRecipientMobileProvider` to
  resolve its enabled account without making System depend on that module or reinterpret its ID as ADMIN.
- BPM keeps external initiators as typed subjects in Flowable and notification contracts. UI conversion
  may use the snapshotted external display name, but must not parse an external subject as a System user ID.
- Authorization is cumulative: Controller feature permission uses `@PreAuthorize`, list and aggregate visibility uses Yudao DataPermission or an explicitly reviewed SQL scope, and single-object or object-bearing commands use the module-owned `@ZsjosPermission` at the Service boundary.
- Passing feature permission, list scope, or object permission never implies either of the other checks. Batch mutations must authorize every target object before applying changes.
- Forced-form administration belongs in `yudao-module-zsjos` but must obtain user, department,
  post, dictionary and OAuth-client facts through System public APIs. It owns the form/version/batch/
  recipient/submission/file business tables, freezes recipient and dictionary snapshots, enforces the
  one-form-one-completion invariant, and maps cross-module validation failures to stable ZSJOS error
  codes. It must not query System tables directly, create BPM tasks for forced forms, or write
  administrator-maintained dictionary options.
- Forced-form attachments use Infra file creation for temporary uploads, then ZSJOS owns only the
  upload token, form/field/user relationship, lifecycle status and submission binding. A cleanup job may
  expire stale ZSJOS temporary rows and ask Infra to delete the corresponding file best-effort, but
  historical submissions may reference only successfully bound rows.

## Frontend boundaries

- Page components coordinate UI and feature state.
- Typed services own HTTP paths and external response normalization.
- Shared authentication infrastructure owns token, tenant header, refresh, and logout behavior.
- Backend menu data owns navigation availability; a local registry owns which React component can render a known route.
- A visual reference owns appearance guidance only. It does not own data, routing, or permission behavior.

For lead specified-assignment management, the Vue administration frontend owns the
global administrator workflow and the React workbench owns the department-scoped
manager workflow. Both consume the same ZSJOS relationship APIs; neither frontend owns
or reconstructs relationship truth from roles, post names, or static user lists.

The confirmed dual-frontend menu-completeness requirement is a scoped exception to the
default administrator-page split: every active ZSJOS-facing page menu also has a React
renderer, including personnel, partner, impersonation, audit, cashback, withdrawal,
user-relation, maintenance, and notification-rule administration. These React pages
reuse the same System or ZSJOS APIs and permission strings as Vue; they do not move
backend ownership, duplicate administrator-maintained data, or infer access from role
names. The route-to-renderer inventory is `docs/frontend/zsjos-menu-coverage.md`.

## Documentation boundaries

- `AGENTS.md` contains durable agent behavior, commands, risk rules, and verification expectations.
- `docs/architecture/` contains cross-module topology, data flow, and ownership explanations.
- Module `docs/` contain implementation contracts and module-specific development guidance.
- One-time requirements, credentials, temporary ports, and current task plans do not belong in durable instruction files.
- When code behavior changes, update only the documents whose claims are directly affected; do not use a feature task for unrelated documentation cleanup.

## Change procedure

1. Identify the data and behavior owner.
2. Read the nearest `AGENTS.md` and only the relevant architecture documents.
3. Inspect current interfaces, implementation, tests, and Git changes.
4. State facts, assumptions, non-goals, affected scope, and verification plan.
5. Obtain confirmation for behavior changes and separately for destructive or external-state operations.
6. Implement within the owner boundary.
7. Verify according to risk and synchronize directly affected documentation.
8. Report changed behavior, evidence, skipped checks, and residual risks.
