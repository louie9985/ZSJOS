# Ownership and Change Boundaries

## Ownership matrix

| Change | Primary owner | Required checks | Prohibited shortcut |
| --- | --- | --- | --- |
| Employee page or workbench shell | `frontend/workbench` | Authorized menu mapping, typed API, desktop/mobile UI | Duplicating the page in Vue without an admin use case |
| Administrator page or configuration | Vue administration frontend | Existing API/dictionary/permission utilities | Building an independent admin UI in the workbench |
| User, role, menu, dictionary, tenant, authentication | `yudao-module-system` and existing framework contracts | Existing public API and authorization behavior | Reimplementing system truth in `yudao-module-zsjos` |
| Business notification templates, tenant rules, message snapshots, and delivery | `yudao-module-system` | Registered scene contract, ownership API, tenant isolation, idempotency | Letting a business module write System notification tables or accepting arbitrary URLs/expressions |
| Workflow and approval execution | `yudao-module-bpm` | Public API, stable process/business keys, status events, idempotent business update | Reimplementing process tasks or workflow history in `yudao-module-zsjos` |
| Product, SKU, and product-driven rule definition | Future approved product capability | Public rule-result contract, stable identifiers, versioning | Inferring rules from names or querying another module's DAL or tables |
| Existing CRM or other domain behavior | Owning business module | Public module boundary and data-scope rules | Cross-module DAL access or copied domain logic; see the approved ZSJOS lifecycle exception below |
| New Zhongshijian business behavior | `yudao-module-zsjos` | VO, Service, authorization, tenant, tests | Putting business rules in controllers or framework modules |
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
users, departments, posts, roles, dictionaries, tenants, authentication, and
permissions continue to come through the existing system contracts. External payment
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
- Authorization is cumulative: Controller feature permission uses `@PreAuthorize`, list and aggregate visibility uses Yudao DataPermission or an explicitly reviewed SQL scope, and single-object or object-bearing commands use the module-owned `@ZsjosPermission` at the Service boundary.
- Passing feature permission, list scope, or object permission never implies either of the other checks. Batch mutations must authorize every target object before applying changes.

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
