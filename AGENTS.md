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

- **MUST** report a concrete conflict before changing behavior or documentation. When the priority above or an existing explicit user decision resolves it, state that basis and continue; otherwise pause only the changes that depend on the unresolved business or contract decision.
- **MUST** follow the applicable direction, including existing user confirmation, and synchronize directly affected documentation.
- **MUST NOT** silently choose whichever source is easiest to implement.

## 2. Read before acting

- Requests to discuss, analyze, diagnose, inspect, review, or explain are read-only by default. If the user also explicitly requests a fix or implementation, execute that authorized part; otherwise **MUST NOT** edit files.
- Clearly requested, scoped and reversible local changes may proceed after a brief statement of scope and verification. Wait only for a material business ambiguity, an unresolved contract conflict, a required scope expansion, or an explicit repository approval boundary, including the contract exceptions in section 3 and controlled operations in sections 4 and 5. Continue independent authorized work while a decision is pending.
- User confirmation remains valid for the same goal, scope and operation throughout the task. Ask again only when the target, risk or impact materially changes; approval of a general task does not authorize the separately controlled operations in sections 4 or 5.
- For complex changes, explain known facts, material assumptions, non-goals, affected scope and verification. For a small change, one or two sentences suffice; do not require a design document or empty template fields.
- Before editing, **MUST** inspect the target implementation, applicable rules and current Git changes. Inspect public interfaces, database/configuration sources and cross-module contracts only when affected; consult similar implementations when the pattern is unclear. Reuse material already read in the current context unless it changed or is no longer available.
- **MUST** preserve user changes. Do not reset, overwrite, reformat, stage, commit, push, switch branches, or clean unrelated work unless explicitly requested.
- **SHOULD** keep changes limited to the requested behavior and its tests or directly affected documentation.
- Continue through the authorized implementation, directly affected documentation, necessary verification and delivery record. Stop when these completion conditions are met; report unrelated issues without automatically fixing them or expanding the task.

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

- Migrations, bootstrap seeds, standalone deployment SQL and their generators **MUST NOT** insert, inherit, restore, reassign, revoke or reconcile `system_role_menu` assignments. They may define menu/button permission metadata; administrators assign permissions through System role management. Read-only authorization audits and isolated test fixtures are allowed. Existing database grants are not reset by source cleanup. Applied-file checksum differences require a separately reviewed rollout and must not be silently reconciled.

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

## 4. Risk, external state, and database initialization and synchronization

### Encoding and charset contract

- Repository source files, SQL files, HTTP responses, and persisted user-visible text **MUST** use UTF-8; new text files **SHOULD** include an explicit UTF-8 declaration when the format supports one.
- MySQL commands that insert or update Chinese text **MUST** establish `utf8mb4` on the client connection (for example `mysql --default-character-set=utf8mb4` and `SET NAMES utf8mb4`). A UTF-8 SQL file alone does not guarantee a UTF-8 client connection.
- PowerShell here-strings, pipes into `docker exec -i`, IDE database consoles, and CI runners are separate encoding boundaries. The command's file encoding, pipe encoding, MySQL client character set, table/column charset, and application/JDBC connection charset **MUST** be treated as independent checks.
- **MUST NOT** use a terminal's rendered output as proof that persisted text is correct. Garbled output may be a display/code-page problem, while values such as `ä¼...` indicate the original UTF-8 bytes were decoded as another charset and written back (double encoding).
- Before delivering a database text change, verification **MUST** query the value through a UTF-8 client and inspect `HEX(column)` for representative Chinese text. If a repair is needed, the SQL source and the controlled development database **MUST** be corrected together.
- Migration files that seed or repair Chinese labels **MUST** set `SET NAMES utf8mb4`, document the exact data scope and repeatability, and avoid copying text from a garbled terminal rendering.
- Java/Spring services **MUST** keep HTTP, servlet, JSON, JDBC, and resource encodings aligned to UTF-8. Frontend builds and browser checks should verify the response bytes and rendered text when a user-visible label changed.

The following require explicit authorization for the operation and its target/impact, even when related to the task. Reuse an existing authorization covering those details; do not ask for the same approval again unless they materially change:

- Clearing or deleting database data, accounts, roles, permissions, or files in bulk.
- Rewriting a migration that has been applied to an already-deployed environment whose upgrade compatibility must be preserved, or performing an irreversible schema change.
- Overwriting a large directory or replacing a runtime implementation wholesale.
- Changing real account permissions or other shared external state.
- Starting, stopping, or reconfiguring an external/shared service.
- Creating, deleting, or switching Git branches or worktrees, or rebasing or merging branches.
- Creating commits, pushing branches, or publishing artifacts.

- **MUST** identify exact targets and expected impact before requesting confirmation.
- SQL that deletes or rebuilds data **MUST** state deletion scope, insertion order, relationships, repeatability, and recovery approach.
- **MUST NOT** include tokens, passwords, personal data, or complete sensitive payloads in logs, documentation, or final reports.

Database initialization and synchronization rules:

- MySQL initialization artifacts belong under `script/sql/mysql/`; `bootstrap.sql` is the fresh-environment entry point.
- The bootstrap is non-destructive: it MUST NOT drop databases or tables or delete business rows in bulk.
- When a SQL or migration script is incorrect during active development and compatibility with an already-deployed environment is not required, the preferred and required default is to correct the original bootstrap, baseline, or numbered migration script and apply the corresponding schema or data correction directly to the development database in the same change. **MUST NOT** add another numbered migration merely to avoid correcting the current development baseline.
- Applying a correction directly to the development database does not replace correcting its SQL source. Correcting only the database or only the script is incomplete.
- Before directly changing a development database, the AI **MUST** inspect the current database state, identify the exact target objects or rows and expected impact, preserve unrelated data, and obtain any confirmation required by this section for shared external state. The development database correction **MUST** be scoped, repeatable or otherwise safely controlled, and recoverable where practical.
- Numbered migrations under `script/sql/mysql/migrations/` remain required when an already-deployed environment must be upgraded without rebuilding its baseline. Such migrations must be repeatable and record their version in `zsjos_schema_version` where applicable. In this case, historical scripts already used by those environments **MUST NOT** be edited as a substitute for an upgrade migration.
- Before delivery, execute the changed SQL from its documented prerequisite state in a controlled database and verify the affected schema, data, constraints, version records, migration dependencies and applicable repeatability. Compare the affected result with the intended development database state after the direct correction. A successful direct database edit alone is not verification that the SQL can reproduce the result.
- Changes to the baseline, bootstrap chain, migration order or cross-version dependencies, and release acceptance, require the complete applicable fresh/upgrade verification and read-only schema/scoped-data comparison with the development database. Fresh-production initialization must succeed from the baseline through the latest version in a controlled database; this does not authorize execution against production. A local SQL correction does not require unrelated full-chain checks. Reuse evidence only for unchanged code and prerequisite state; rerun checks invalidated by the change. SQL text, version markers or exit status alone are insufficient evidence.
- Any intentionally edited baseline or compatibility migration **MUST** document its deployment scope, prerequisites, execution order, repeatability, exact data scope, and rollback limitations.
- Dictionary types and dictionary data are separate concerns. The bootstrap may include system-owned dictionary data, but ZSJOS business dictionary data requires a separately reviewed file and explicit confirmation before synchronization.
- The bootstrap must create empty `zsjos_lead_category` and `zsjos_lead_source_channel` types without inventing business options.
- Fresh-environment seeds must not include local leads, products, SKUs, orders, uploaded files, test accounts, tokens, or machine-specific configuration.
- The initial administrator password may be stored only as a BCrypt hash in SQL. Plaintext passwords, tokens, and personal data MUST NOT be added to `AGENTS.md`, logs, or operational documentation.
- Local and production must use the same schema baseline and migration order. A read-only verification script and schema-difference check are required before release.
- Database scripts must document dependencies, execution order, repeatability, rollback limitations, and the exact data scope they seed.

## 5. Dependencies, code, and documentation

- Before adding an npm or Maven dependency, **MUST** show why existing dependencies are insufficient and explain maintenance, size, and security impact; add it only with explicit confirmation covering that dependency and impact. Reuse an existing confirmation under section 2.
- **SHOULD** follow existing framework and module patterns before introducing an abstraction.
- **SHOULD** keep one clear responsibility per file and avoid unrelated refactors or speculative shared utilities.
- Comments **MUST** explain non-obvious business reasons, boundaries, invariants, authorization, transaction behavior, or compatibility. **MUST NOT** narrate self-explanatory code.
- Directly affected architecture, API, navigation, development, and operational documentation **MUST** be updated with behavior changes.
- The AI **MAY** proactively update the applicable `AGENTS.md` and constraint documentation when an explicit user correction establishes a durable, reusable repository rule. It **MUST** place the rule in the narrowest applicable scope, preserve higher-priority instructions, and report the update in the final response.
- The AI **MUST NOT** turn a one-time request, temporary workaround, credential, environment value, or unconfirmed preference into a durable constraint.
- User-visible active product surfaces and delivery documentation **MUST** use the Zhongshijian product context. **MUST NOT** blindly rename upstream package names, dependencies, database identifiers, or internal framework symbols.

## 6. Verification and delivery

Verification is proportional to the affected behavior and risk, with evidence mandatory. Commands below and in subtree instructions are entry points, not a checklist to run in full for every edit:

- Documentation, comments and mechanical text corrections: scoped diff/content checks and relevant link checks. Add runtime or visual checks only if executable examples, build inputs or rendered product layout are affected.
- Pure logic and service behavior: focused tests and applicable type or compile checks. Broaden coverage when shared behavior or multiple callers are affected.
- API behavior: focused tests plus a real request or contract verification when an environment is available.
- Permission, authentication and tenant behavior: affected allowed/denied and isolation cases, including applicable empty/failure states. Shared contracts require verification of every affected consumer, including both frontends where applicable.
- UI interaction: affected flow tests, applicable static checks and real-browser verification. Visual changes require browser inspection at affected widths; shared layout or responsive changes require desktop and mobile widths. Run a production build when bundling, dependencies, routes, assets, build configuration or release acceptance are affected.
- SQL or initialization: controlled execution and result verification under section 4, including scope, relationships/order and applicable repeatability; destructive execution still requires explicit authorization.
- Runtime wiring: related module build and, when relevant, application startup or endpoint discovery.

- Do not introduce new test facilities or dependencies for low-risk mechanical edits. Reuse a check that covers multiple requirements instead of running it repeatedly.
- After relevant checks pass, broaden or repeat them only for a new change, new failure or unresolved risk. Report unrelated existing failures without automatically fixing them or blocking a verified local delivery.

- Remote-data views **MUST** handle loading, success, empty, error, retry, and unauthorized states as applicable.
- Distinct actionable failures **MUST NOT** be collapsed into one generic error when the backend exposes a stable distinction.
- **MUST NOT** claim a fix is complete without the corresponding verification evidence.
- If a necessary check cannot run, **MUST** report it as unverified, explain why, and state the remaining risk. Distinguish an environment-blocked check from a check that does not apply to this change.
- Long tasks **SHOULD** report milestones as: diagnosis, change scope, then verification result. Repeated failure requires a root-cause update before another attempt.

## 7. Environment-specific workstream records and optional isolation

- The AI **MUST** resolve the current environment before registering or delivering file-changing work. Resolution order is: the `ZSJOS_AGENT_ENV` environment variable, then `/etc/zsjos/agent-environment`, then `local` as the fallback. Accepted values are `local`, `test`, and `production`; any other explicit value **MUST** be reported as invalid instead of guessed.
- Environment identity **MUST NOT** be inferred from the Git branch, repository path, IP address, or hostname. Server roles can change while those values remain the same.
- The designated shared-main workstream record is selected by environment: `local` uses `handoff/main.md`, `test` uses `handoff/test_main.md`, and `production` uses `handoff/pro_main.md`.
- Test-server work **MUST NOT** append routine entries to `handoff/main.md` or `handoff/pro_main.md`. Production-server work **MUST NOT** append routine entries to `handoff/main.md` or `handoff/test_main.md`.

- Unless the user explicitly requests otherwise, new AI file-changing work **MUST** use the currently checked-out local branch and worktree. In the primary repository, the default development location is the existing local `main` worktree.
- The AI **MUST NOT** create, delete, or switch Git branches or worktrees for new work unless the user explicitly requests that operation. Branch and worktree operations remain subject to the separate explicit-confirmation requirements in sections 2 and 4.
- File-changing tasks in the same worktree **MUST** be serialized. Concurrent AI tasks may inspect or analyze the repository, but they **MUST NOT** modify files in a shared worktree.
- Before its first file change, the active workstream **MUST** register its ID, goal, non-goals, branch, absolute worktree path, base commit, ownership scope, owner, dependencies and verification plan in `handoff/<workstream-id>.md`. Record target branch and integration order when branch integration is planned; otherwise use `None`. Work performed directly on the shared `main` worktree **MUST** use the environment-designated record above.
- Reuse the active registration for subsequent turns. Append a registration update only when scope, owner, dependencies, verification plan or execution context changes; fixed metadata may reference the existing registration. Do not repeat registration merely because a new turn starts.
- Each file **MUST** have one active workstream owner. A workstream **MUST NOT** modify files outside its recorded scope without first updating its handoff record and coordinating any affected workstream.
- Only when the user explicitly requests isolated or parallel development, each file-changing workstream **MUST** use its own branch and worktree, start from a committed base, and avoid dependencies on another workstream's uncommitted changes. AI-owned branches **MUST** use the `codex/<workstream-id>` naming convention unless the user specifies otherwise.
- For an explicitly requested isolated workstream, the worktree **MUST** belong to exactly one workstream. Before integration, the workstream **MUST** record its final commit, verification evidence, unresolved risks, dependency state, and status as `ready-to-merge`; affected checks **MUST** be rerun on the integration branch before it is marked `merged`.
- Commit, rebase, merge, push, and publication operations remain subject to the explicit-confirmation requirements in sections 2 and 4.

## 8. AI file-change handoff log

- The repository-root `HANDOFF.md` is the stable handoff guide and legacy-log archive. It **MUST NOT** receive per-turn entries or a dynamically maintained workstream index.
- Every completed AI task turn that adds, deletes, or modifies any repository file **MUST** append one structured delivery entry to the active workstream's handoff file before sending the final response. Shared-main work uses the environment-designated record from section 7; isolated work uses `handoff/<workstream-id>.md`. Only that workstream's owner may append to the file.
- A file-changing task turn means one user request and its final AI response that changes any repository file, including source code, tests, scripts, SQL, configuration, documentation, or repository rules. Commentary updates, tool calls, and intermediate messages **MUST NOT** be recorded as separate entries.
- Turns that make no repository file changes **MUST NOT** append a handoff entry. This includes discussion, analysis, diagnosis, inspection, review, and explanation requests that remain read-only under section 2.
- Each entry **MUST** include Beijing time, branch, worktree, HEAD commit, user goal, key decisions, execution or analysis result, changed files, verification evidence, dependency or integration impact, and remaining work. Unchanged branch/worktree/base metadata may reference the workstream registration; record the current HEAD or explicitly state it is unchanged. Use `None` for inapplicable fields without requiring additional integration analysis for a single-worktree task.
- Entries **MUST** be appended in chronological order. Existing entries **MUST NOT** be rewritten or deleted; corrections must be recorded in a new entry.
- Handoff files **MUST NOT** contain passwords, tokens, personal data, complete sensitive payloads, or unnecessary conversation transcripts.

## 9. AI coding execution defaults

### 默认执行原则

- 当前仓库、当前工作树和当前分支是默认工作范围。只读检查、代码搜索、局部测试、编译、日志诊断和开发服务状态检查可直接执行；明确要求的局部修改按第 2 节授权与完成边界执行。
- 已由本文件、仓库脚本、配置或模块文档明确的事实不得重复询问。命令失败时先检查路径、参数、依赖、环境变量和脚本帮助，再报告具体阻断原因。
- 新增依赖、分支/工作树操作、提交、推送、发布，以及生产或共享外部状态变更遵循第 4、5 节；已经明确授权的同一操作、目标和影响不重复确认。

### 本地开发默认上下文

- 默认开发租户为 `tenant_id = 1`，ADMIN 请求默认使用 `tenant-id: 1`；公共接口按接口契约使用 `tenantId` 查询参数。
- 数据库名、账号、密码、服务地址和端口必须优先从本地配置、Docker Compose 和环境文件读取；不得在本文件记录密码、令牌或个人数据。
- 多租户任务、租户隔离验证或现有租户不确定时，必须查询配置、API 或数据库，不得根据角色名、页面文案或历史记录猜测。

### 本地数据库操作

- 本地开发数据库的查询、结构检查和备份可直接执行。在已授权任务内，检查目标和影响后可执行非破坏、可重复迁移、非破坏验证脚本和明确范围的非删除数据修复，无需重复询问权限。
- 删除或重建指定表、指定租户或指定业务数据仍属于需明确授权的操作；指定范围不豁免批量删除审批。执行前必须说明目标范围、备份位置、执行顺序、幂等性、恢复方式和预期影响；SQL 必须保留租户条件或明确说明系统表例外。
- 生产、共享测试和远程数据库写入，以及批量删除、全库操作、无范围 `TRUNCATE`、`DROP DATABASE` 和不可逆结构变更，必须取得涵盖具体操作、目标和影响的明确授权。只读检查可直接执行；已有有效授权不重复索取。
- MySQL 写入中文前使用 `mysql --default-character-set=utf8mb4` 并执行 `SET NAMES utf8mb4`；完成后检查代表性文本的 `HEX()`。
- 数据库初始化、迁移、备份、回滚限制和校验规则详见 [`docs/operations/database-migrations.md`](docs/operations/database-migrations.md)。

### 标准命令入口

以下命令按第 6 节及目标目录规则选择，不是每轮全部执行的清单。使用已有脚本支持的过滤参数运行相关测试；扩大检查须与实际影响相符。

- Windows 数据库工具：`./zsjos-db.ps1 check`、`test-fresh`、`test-upgrade`、`test-guardrails`；类 Unix 使用 `./zsjos-db`。
- 后端：`mvn -f backend/pom.xml -pl yudao-module-zsjos -am test`；编译使用 `-DskipTests compile`。
- Admin：在 `frontend/admin` 运行 `pnpm ts:check`、`pnpm lint`、`pnpm build:local`。
- Workbench：在 `frontend/workbench` 运行 `npm run typecheck`、`npm run build`。
- Docker、数据库连接和模块专项命令以对应脚本及文档为准；按改动范围选择验证，不因全仓库存在无关失败而阻塞局部交付。

### 业务契约摘要

- 菜单、权限、字典、组织、用户和可见性以服务端为准；认证、租户、菜单、权限和字典流程详见 [`docs/architecture/data-and-permission-flow.md`](docs/architecture/data-and-permission-flow.md)。
- ZSJOS 模块归属、API、SQL、依赖和跨模块边界详见 [`docs/architecture/ownership-and-change-boundaries.md`](docs/architecture/ownership-and-change-boundaries.md)。
- 跨运行时、启动、前后端验证和系统结构详见 [`docs/architecture/system-overview.md`](docs/architecture/system-overview.md)。
- Lead 对外标识使用 `leadNo`；BPM 流程使用 `yudao-module-bpm`；详细约束以相关架构和业务文档为准。

### 文档维护

- 本文件只维护稳定的 AI 执行边界、默认上下文、命令入口和契约摘要；模块细节、接口细节和数据库专项流程放在对应文档。
- 稳定事实应补充到最窄适用范围的文档，并在 handoff 记录中说明；不得把一次性环境值、密码、令牌或个人数据写入本文件。
