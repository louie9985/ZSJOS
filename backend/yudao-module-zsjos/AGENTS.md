# ZSJOS Java Module Instructions

These rules apply to `yudao-module-zsjos` and extend the repository root instructions.

## Module ownership

- New Zhongshijian-owned business behavior belongs in this module.
- Existing framework, system, infrastructure, and BPM behavior **MUST** be reused through a public API or established service boundary. **MUST NOT** copy that implementation here merely to simplify a caller.
- CRM and other domain modules are implementation references only unless a separate integration is approved. Their tables, DAL, and domain services **MUST NOT** become ZSJOS dependencies by default.
- Cross-module code **MUST NOT** query another module's tables directly or depend on its DAL implementation.
- New dependencies **MUST** point in an allowed direction and require the dependency review defined in the root instructions.

## BPM integration

- Approval and workflow features **MUST** use `yudao-module-bpm` for process definitions, process instances, tasks, assignees, countersigning, rejection, cancellation, copy recipients, and workflow history.
- ZSJOS business tables **MUST** retain the business record, business status, immutable business snapshot when required, and BPM reference identifiers such as `processInstanceId`. A stable ZSJOS business identifier **SHOULD** be used as the BPM `businessKey`.
- Starting a workflow **MUST** use the BPM public API. BPM status or task events **MUST** drive idempotent ZSJOS business-state updates through a documented listener or public event boundary.
- ZSJOS **MUST NOT** create parallel approval-task or workflow-history tables that duplicate BPM-owned task state. ZSJOS may retain a business approval round or snapshot when it has independent audit or resubmission meaning.
- Frontends **SHOULD** reuse BPM task, pending-work, and process-detail capabilities while continuing to obtain ZSJOS business data from ZSJOS APIs.
- Each integration **MUST** define and verify its process-definition key, business key, BPM-to-business status mapping, resubmission behavior, cancellation behavior, event idempotency, and missing or undeployed process-definition failure.

## Authorization and data access

ZSJOS business operations **MUST** enforce three independent and cumulative layers:

1. Controller feature permission through `@PreAuthorize` for the operation permission code.
2. List, statistics, export, and batch-query scope through Yudao DataPermission when its model applies, or an explicit reviewed SQL condition when the business relationship cannot be represented by the generic rule.
3. Single-object and object-bearing command permission through a module-owned `@ZsjosPermission` check at the Service boundary.

- Passing one layer **MUST NOT** imply passing either of the other layers. Feature permission does not grant access to every object; list visibility does not grant mutation rights; object ownership does not bypass feature permission.
- `@ZsjosPermission` **MUST** support a stable business type, a SpEL-resolved business identifier, and the required object permission or action. The current absence of this annotation is an implementation gap, not permission to omit object checks from new behavior.
- Single-record reads, updates, deletes, transitions, transfers, approval submission, and other object-specific actions **MUST** declare the applicable object check. Batch commands **MUST** validate every identifier and fail atomically on any unauthorized object.
- Object permissions and their persistence belong to `yudao-module-zsjos`. The implementation may follow the proven Yudao CRM annotation/AOP shape, but **MUST NOT** depend on CRM permission tables, DAL, services, enums, or CRM-specific public-pool and subordinate semantics.
- Administrator bypass, hierarchy, owner, collaborator, read, write, and action semantics **MUST** come from confirmed system permissions and ZSJOS business relationships. They **MUST NOT** be inferred from usernames, role names, post names, department labels, or frontend state.
- Unauthorized object access **MUST** return a stable ZSJOS permission error and **MUST NOT** execute any business mutation.

## Java structure and contracts

- Follow the existing Controller -> Service -> DAL/API structure.
- Controllers **MUST** use explicit request and response VO types and return the repository's standard response wrapper. They **MUST NOT** expose persistence DO types.
- Authorization, tenant isolation, validation, transactions, and data-scope behavior **MUST** use the approved framework and module mechanisms above. **MUST NOT** be collapsed into a controller-only check or an unreviewed ad hoc query.
- Dictionary codes, post codes, business constants, and error codes **MUST** be owned by focused module constants or enums, not repeated as string literals across services.
- Stable failure cases **MUST** use distinct module error codes and actionable messages rather than one generic failure.
- Services own business decisions and transaction boundaries. Controllers should translate HTTP input and output, not implement business rules.
- Comments **MUST** explain non-obvious business rules, authorization, transaction boundaries, or compatibility constraints.

## Lead pool terminology

- The legacy ZSJOS technical value `assignment_status=public_pool` means the unowned first-come claim pool (抢单池).
- The aging collaboration pool and the manually released public sea are owner-preserving collaboration views. They must not reuse the claim-pool assignment value, claim endpoints, direct-claim behavior, or business wording.
- Manual public-sea release must preserve Lead owner, primary status, and assignment status. Collaboration alone does not change ownership; when the approved aging-pool or manual-public-sea collaborator submits an order, that transaction permanently transfers Lead and Opportunity ownership to the collaborator and freezes the order's formal sales owner accordingly.

## Database and SQL

- Schema and initialization changes **MUST** preserve tenant rules, logical deletion conventions, audit columns, indexes, and established naming patterns.
- Do not modify an applied historical migration. Add a new migration or confirmed initialization script as appropriate to the repository's current mechanism.
- Initialization scripts **MUST** define deletion scope, dependency order, key relationships, repeatability, and recovery expectations before execution.

## Verification commands

For module behavior, run from the repository root:

```powershell
mvn -f backend/pom.xml -pl yudao-module-zsjos -am test
```

When changes affect application assembly, scanning, configuration, or runtime wiring, also build the server dependency graph and verify startup or endpoint discovery when the environment is available:

```powershell
mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package
```

- Add focused tests for changed service behavior, authorization, error mapping, and persistence relationships. Permission changes **MUST** cover feature allowed/denied, list in-scope/out-of-scope, object read/write/owner or action checks, batch mixed authorization, tenant isolation, and applicable administrator behavior.
- Report environment-dependent checks such as MySQL, Redis, tenant data, or real HTTP requests separately.
