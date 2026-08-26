# ZSJ-OS Repository Instructions

This file defines durable repository-wide rules for AI-assisted development. A nested
`AGENTS.md` adds rules for its subtree and takes precedence when it is more specific.

## 1. Instruction priority

Use this order when repository guidance conflicts:

1. The user's current, explicit request.
2. The nearest `AGENTS.md` in the target file's directory tree.
3. Parent `AGENTS.md` files, up to this root file.
4. Approved architecture, contract, and decision documents.
5. Module documentation.
6. The current implementation, which is evidence of behavior but not automatically the desired design.

- **MUST** report a concrete conflict before changing behavior or documentation.
- **MUST** follow the direction confirmed by the user and synchronize directly affected documentation.
- **MUST NOT** silently choose whichever source is easiest to implement.

## 2. Read before acting

- Requests to discuss, analyze, diagnose, inspect, review, or explain are read-only. **MUST NOT** edit files unless the user subsequently authorizes implementation.
- Before any behavior change, **MUST** state the known facts, assumptions, non-goals, affected scope, and verification plan, then wait for confirmation.
- A purely mechanical correction with no behavior change may be made directly only when the user explicitly asks for that correction.
- Before editing, **MUST** inspect the relevant implementation, public interfaces, database or configuration sources, similar repository patterns, and current Git changes.
- **MUST** preserve user changes. Do not reset, overwrite, reformat, stage, commit, push, switch branches, or clean unrelated work unless explicitly requested.
- **SHOULD** keep changes limited to the requested behavior and its tests or directly affected documentation.

## 3. Sources of truth

- Server-owned menus, permissions, dictionaries, departments, posts, users, visibility, and ordering are authoritative.
- **MUST NOT** create frontend mock data, static option arrays, duplicate menu trees, or inferred permissions as a substitute for an available backend source.
- **MUST NOT** infer roles, organization structure, menu depth, routes, fields, or permissions from names or UI labels.
- Administrator-maintained data **MUST** come from a dictionary or business API in production flows.
- The workbench may call existing system and business APIs. New ZSJOS-owned backend behavior belongs in `yudao-module-zsjos`, but existing system or CRM capabilities **MUST NOT** be copied there merely to centralize calls.

### Administration and employee workbench contract

- `frontend/admin` and `frontend/workbench` are independent frontend projects for materially different users, menus, and interaction workflows. The administration frontend **MUST** retain its Vue and `pnpm` conventions, and the employee workbench **MUST** retain its React and `npm` conventions unless an approved architecture change says otherwise.
- The two frontends **MUST** share the established backend APIs, ADMIN authentication, tenant context, and server-owned menu and permission system. They **MUST NOT** duplicate backend business capabilities, introduce a client-specific authentication or authorization truth, maintain static production permissions, or infer access from role names.
- Each frontend **MUST** render only server-authorized menus and operations that its own runtime supports. Their page sets, navigation, presentation, and interactions are not required to match mechanically. Approved `admin_embed`, documented dual-frontend menu coverage, and explicitly confirmed business exceptions remain valid and **MUST NOT** be treated as prohibited duplication.
- A change to a backend, authentication, tenant, menu, or permission contract consumed by both frontends **MUST** identify and verify both consumers. Adapting or testing one frontend **MUST NOT** be treated as compatibility evidence for the other.
- When stable framework-independent behavior is duplicated across both frontends, implementations **SHOULD** evaluate a shared package for protocol types, constants, normalization, pure utilities, design tokens, static assets, or transport and authentication cores with frontend-specific adapters. Vue and React components, stores, hooks, router integrations, and framework adapters **MUST** remain owned by their respective projects unless a separately approved cross-framework design establishes a safe boundary.
- A shared-package proposal **MUST** describe the duplicated behavior, public boundary, versioning, package-manager and build integration, migration scope, and maintenance impact before implementation. It remains subject to the dependency confirmation rules below; duplication alone does not authorize a new workspace, dependency, or broad frontend refactor.

### Configurable permission contract

- User-visible page or view access **MUST** be represented by server-owned menu permission configuration, and user-visible operations such as create, edit, delete, export, submit, approve, or audit **MUST** be represented by server-owned menu/button permission configuration by default.
- Page menus whose parent is the Workbench `/zsjos` root **MUST** store a relative child `path` such as `my-students`; they **MUST NOT** repeat the parent prefix as `/zsjos/my-students`. Frontend route constants and delivery documentation may use the resolved public URL `/zsjos/my-students`.
- Frontends **MUST** consume the server-returned menu and permission state to control routes and action entry points. Backends **MUST** independently enforce the corresponding permission identifiers; hiding a frontend control is not authorization.
- Permission identifiers declared at backend authorization boundaries, including identifiers used by annotations such as `@PreAuthorize`, are the stable link to configured menu/button permissions and are not prohibited hardcoding. Role names, user IDs, static permission lists, and authorization decisions **MUST NOT** be hardcoded as substitutes for configured permissions.
- Object visibility, data scope, ownership constraints, lifecycle preconditions, and rules such as "only the creator may edit an unsubmitted record" are domain authorization or business invariants. They **MAY** be enforced in backend code and **MUST NOT** be misrepresented as menu/button permissions when configuration cannot express them safely.
- If a required page or operation permission cannot be implemented through the established menu/button permission mechanism, the AI **MUST** first explain the concrete limitation, proposed hardcoded rule or value, code location, affected scope, risks, and alternatives, and **MUST** obtain explicit user confirmation before hardcoding it.

### Dictionary selection and snapshot contract

- User-editable, stable business enumeration choices shown in dropdowns or equivalent selectors **MUST** use administrator-maintained dictionary types and entries by default. Frontends and backends **MUST NOT** substitute hardcoded option arrays, labels, or business-enum choices for an available dictionary source.
- Selectors for entities such as users, departments, posts, products, SKUs, customers, or other business records **MUST** use the authoritative system or owning business API and are not dictionary selectors. Workflow commands, state-machine actions, and other non-configurable technical choices **MUST** use their authoritative framework or business contract rather than being disguised as administrator-editable dictionaries.
- When a dictionary selection is persisted as part of a business record, the owning business data **MUST** store both the selected dictionary value/code and the display label at selection time as a snapshot. It **MUST** also store the dictionary type or version when required to disambiguate or reproduce the selection.
- Historical detail, workflow forms, notifications, exports, and other persisted-record projections **MUST** display the stored snapshot label. They **MUST NOT** silently re-resolve the current dictionary label as the historical value, so later dictionary renaming, disabling, or deletion does not alter the recorded business meaning. A new user selection creates a new snapshot; an unchanged historical selection retains its existing snapshot.
- Transient query filters do not create business snapshots merely because they use a dictionary selector. Existing historical records that predate a snapshot field **MUST NOT** be assigned invented historical labels; any compatibility fallback or data repair requires an identified source and documented scope.
- If a business dropdown cannot use the established dictionary mechanism, the AI **MUST** first explain the concrete limitation, proposed hardcoded values, code location, affected scope, snapshot implications, risks, and alternatives, and **MUST** obtain explicit user confirmation before hardcoding it.

### Lead identifier contract

- Every user-visible Lead identifier **MUST** use the business number `leadNo`, including frontend labels and values, notifications, BPM read-only forms, task titles, exports, and delivery documentation.
- Internal `leadId` or `id` values **MUST** remain internal identifiers for primary and foreign keys, API paths and commands, permissions, routing, event identity, React/Vue keys, and other technical relationships. They **MUST NOT** be presented as the customer-facing "客资编号" or used as a fallback when `leadNo` is unavailable.
- Compatibility fields or notification variables that expose an internal identifier **MUST** be explicitly labeled "内部客资ID". Existing historical migrations, rendered messages, and started workflow snapshots are not rewritten merely to adopt this presentation contract.

Read only the architecture documents relevant to the task:

- Cross-runtime or startup work: `docs/architecture/system-overview.md`
- Authentication, tenant, menu, dictionary, organization, or permission work: `docs/architecture/data-and-permission-flow.md`
- New modules, APIs, SQL, configuration, or cross-module work: `docs/architecture/ownership-and-change-boundaries.md`

### Yudao alignment and reusable facilities

- ZSJOS modules **MUST** align with established Yudao Maven, package, Controller -> Service -> DAL/API, VO/DO, response-wrapper, error-code, validation, transaction, tenant, logical-delete, audit-field, and test conventions unless an approved ZSJOS requirement needs a documented exception.
- `yudao-framework`, `yudao-module-system`, `yudao-module-infra`, and `yudao-module-bpm` are approved foundational facilities for ZSJOS. When they already provide a required capability, implementations **MUST** prefer their framework mechanism or public API over a parallel ZSJOS implementation.
- Foundational status does not require every ZSJOS module to depend on every facility. Dependencies remain demand-driven and subject to the dependency review in section 5.
- Workflow definitions, process instances, approval tasks, assignees, countersigning, rejection, cancellation, copy recipients, and workflow history **MUST** use `yudao-module-bpm`. ZSJOS owns its business records, business state, snapshots, and BPM reference identifiers, and consumes BPM through its public API and status-event boundary.
- `yudao-module-pay`, `yudao-module-report`, and `yudao-module-member` are optional shared capabilities, not default infrastructure. Their adoption requires feature-specific ownership and dependency confirmation.
- CRM, ERP, WMS, MES, Mall, AI, IoT, IM, and MP are domain modules, not foundational facilities. Their presence in the repository **MUST NOT** create an implicit ZSJOS runtime dependency or authorize reuse of their tables, DAL, or domain services.
- Alignment with another Yudao module means reusing engineering patterns and approved facilities. It **MUST NOT** be interpreted as copying that module's domain model, database schema, static data, or private implementation.

## 4. Risk and external state

The following require separate, explicit confirmation even when related to the task:

- Clearing or deleting database data, accounts, roles, permissions, or files in bulk.
- Rewriting an applied migration or performing an irreversible schema change.
- Overwriting a large directory or replacing a runtime implementation wholesale.
- Changing real account permissions or other shared external state.
- Starting, stopping, or reconfiguring an external/shared service.
- Creating, deleting, or switching Git branches or worktrees, or rebasing or merging branches.
- Creating commits, pushing branches, or publishing artifacts.

- **MUST** identify exact targets and expected impact before requesting confirmation.
- SQL that deletes or rebuilds data **MUST** state deletion scope, insertion order, relationships, repeatability, and recovery approach.
- **MUST NOT** include tokens, passwords, personal data, or complete sensitive payloads in logs, documentation, or final reports.

## 5. Dependencies, code, and documentation

- Before adding an npm or Maven dependency, **MUST** show why existing dependencies are insufficient and explain maintenance, size, and security impact; add it only after confirmation.
- **SHOULD** follow existing framework and module patterns before introducing an abstraction.
- **SHOULD** keep one clear responsibility per file and avoid unrelated refactors or speculative shared utilities.
- Comments **MUST** explain non-obvious business reasons, boundaries, invariants, authorization, transaction behavior, or compatibility. **MUST NOT** narrate self-explanatory code.
- Directly affected architecture, API, navigation, development, and operational documentation **MUST** be updated with behavior changes.
- The AI **MAY** proactively update the applicable `AGENTS.md` and constraint documentation when an explicit user correction establishes a durable, reusable repository rule. It **MUST** place the rule in the narrowest applicable scope, preserve higher-priority instructions, and report the update in the final response.
- The AI **MUST NOT** turn a one-time request, temporary workaround, credential, environment value, or unconfirmed preference into a durable constraint.
- User-visible active product surfaces and delivery documentation **MUST** use the Zhongshijian product context. **MUST NOT** blindly rename upstream package names, dependencies, database identifiers, or internal framework symbols.

## 6. Verification and delivery

Verification is proportional to risk, but evidence is mandatory:

- Pure logic: focused unit tests plus type or compile checks.
- API behavior: focused tests plus a real request or contract verification when an environment is available.
- Permission behavior: authorized and unauthorized cases, including empty and failure states.
- UI behavior: tests, typecheck, production build, and browser checks at desktop and mobile widths.
- SQL or initialization: syntax, relationship/order review, repeatability, and a controlled execution plan; destructive execution still requires confirmation.
- Runtime wiring: module build and, when relevant, application startup or endpoint discovery.

- Remote-data views **MUST** handle loading, success, empty, error, retry, and unauthorized states as applicable.
- Distinct actionable failures **MUST NOT** be collapsed into one generic error when the backend exposes a stable distinction.
- **MUST NOT** claim a fix is complete without the corresponding verification evidence.
- If a check cannot run, **MUST** report it as unverified, explain why, and state the remaining risk.
- Long tasks **SHOULD** report milestones as: diagnosis, change scope, then verification result. Repeated failure requires a root-cause update before another attempt.

## 7. Database initialization and synchronization

- MySQL initialization artifacts belong under `script/sql/mysql/`; `bootstrap.sql` is the fresh-environment entry point.
- The bootstrap is non-destructive: it MUST NOT drop databases or tables or delete business rows in bulk.
- When a database change is needed during active development, the preferred workflow is to apply the intended schema/data adjustment directly to the development database and update the corresponding bootstrap/SQL initialization script in the same change. Do not add another numbered migration merely to avoid editing the current development baseline when the change does not need to preserve compatibility with already-deployed environments.
- Numbered migrations under `script/sql/mysql/migrations/` remain required when an already-deployed environment must be upgraded without rebuilding its baseline. Such migrations must be repeatable and record their version in `zsjos_schema_version` where applicable.
- Before delivery, the final bootstrap and SQL scripts MUST initialize a fresh production database successfully from the baseline through the latest version, and the schema/data result MUST be checked against the development database. Any intentionally edited baseline or compatibility migration MUST document its deployment scope and rollback limitation.
- Dictionary types and dictionary data are separate concerns. The bootstrap may include system-owned dictionary data, but ZSJOS business dictionary data requires a separately reviewed file and explicit confirmation before synchronization.
- The bootstrap must create empty `zsjos_lead_category` and `zsjos_lead_source_channel` types without inventing business options.
- Fresh-environment seeds must not include local leads, products, SKUs, orders, uploaded files, test accounts, tokens, or machine-specific configuration.
- The initial administrator password may be stored only as a BCrypt hash in SQL. Plaintext passwords, tokens, and personal data MUST NOT be added to `AGENTS.md`, logs, or operational documentation.
- Local and production must use the same schema baseline and migration order. A read-only verification script and schema-difference check are required before release.
- Database scripts must document dependencies, execution order, repeatability, rollback limitations, and the exact data scope they seed.

## 8. Default local development and optional workstream isolation

- Unless the user explicitly requests otherwise, new AI file-changing work **MUST** use the currently checked-out local branch and worktree. In the primary repository, the default development location is the existing local `main` worktree.
- The AI **MUST NOT** create, delete, or switch Git branches or worktrees for new work unless the user explicitly requests that operation. Branch and worktree operations remain subject to the separate explicit-confirmation requirements in sections 2 and 4.
- File-changing tasks in the same worktree **MUST** be serialized. Concurrent AI tasks may inspect or analyze the repository, but they **MUST NOT** modify files in a shared worktree.
- Before changing files, the active workstream **MUST** register its ID, goal, non-goals, branch, absolute worktree path, base commit, target branch, ownership scope, owner, dependencies, integration order, and verification plan in `handoff/<workstream-id>.md`. Work performed directly on local `main` may reuse and update the designated `main` workstream record.
- Each file **MUST** have one active workstream owner. A workstream **MUST NOT** modify files outside its recorded scope without first updating its handoff record and coordinating any affected workstream.
- Only when the user explicitly requests isolated or parallel development, each file-changing workstream **MUST** use its own branch and worktree, start from a committed base, and avoid dependencies on another workstream's uncommitted changes. AI-owned branches **MUST** use the `codex/<workstream-id>` naming convention unless the user specifies otherwise.
- For an explicitly requested isolated workstream, the worktree **MUST** belong to exactly one workstream. Before integration, the workstream **MUST** record its final commit, verification evidence, unresolved risks, dependency state, and status as `ready-to-merge`; affected checks **MUST** be rerun on the integration branch before it is marked `merged`.
- Commit, rebase, merge, push, and publication operations remain subject to the explicit-confirmation requirements in sections 2 and 4.

## 9. AI file-change handoff log

- The repository-root `HANDOFF.md` is the stable handoff guide and legacy-log archive. It **MUST NOT** receive per-turn entries or a dynamically maintained workstream index.
- Every completed AI task turn that adds, deletes, or modifies any repository file **MUST** append one structured delivery entry to the active workstream's `handoff/<workstream-id>.md` before sending the final response. Only that workstream's owner may append to the file.
- A file-changing task turn means one user request and its final AI response that changes any repository file, including source code, tests, scripts, SQL, configuration, documentation, or repository rules. Commentary updates, tool calls, and intermediate messages **MUST NOT** be recorded as separate entries.
- Turns that make no repository file changes **MUST NOT** append a handoff entry. This includes discussion, analysis, diagnosis, inspection, review, and explanation requests that remain read-only under section 2.
- Each entry **MUST** include Beijing time, branch, worktree, HEAD commit, user goal, key decisions, execution or analysis result, changed files, verification evidence, dependency or integration impact, and remaining work. Use `None` when a field has no applicable content.
- Entries **MUST** be appended in chronological order. Existing entries **MUST NOT** be rewritten or deleted; corrections must be recorded in a new entry.
- Handoff files **MUST NOT** contain passwords, tokens, personal data, complete sensitive payloads, or unnecessary conversation transcripts.
