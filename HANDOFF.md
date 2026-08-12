# AI Handoff Guide

This file is the stable repository-wide guide for AI workstream handoffs. Parallel workstreams keep their active, append-only logs in uniquely owned files under `handoff/`; this root file is not updated for each AI turn and does not maintain a dynamic index.

## Workstream layout

- Use one file per workstream: `handoff/<workstream-id>.md`.
- Use the same workstream ID in the AI branch name: `codex/<workstream-id>`.
- Assign one branch, one worktree, and one handoff-file owner to each active workstream.
- Discover workstreams by listing the `handoff/` directory rather than editing a shared index.
- Preserve completed workstream files as delivery history; update their status instead of deleting them.

## Required workstream metadata

Each workstream file records its ID, status, goal, non-goals, branch, absolute worktree path, base commit, target branch, ownership scope, owner, dependencies, integration order, and verification plan before implementation begins.

Allowed status values are `planned`, `active`, `blocked`, `ready-to-merge`, and `merged`.

## Per-turn entry template

```markdown
### YYYY-MM-DD HH:mm:ss +08:00

- Branch: ...
- Worktree: ...
- HEAD commit: ...
- User goal: ...
- Key decisions: ...
- Result: ...
- Changed files: ...
- Verification: ...
- Dependency / integration impact: ...
- Remaining work: ...
```

Use Beijing time (`Asia/Shanghai`, UTC+08:00). Append entries chronologically, use `None` when a field is not applicable, and record corrections as new entries instead of rewriting history. Do not include passwords, tokens, personal data, complete sensitive payloads, or unnecessary conversation transcripts.

## Legacy shared entries

The entries below predate workstream-isolated handoff files. They remain unchanged as historical delivery records; new task turns must be recorded in the active workstream file under `handoff/`.

### 2026-08-10 14:53:49 +08:00

- User goal: Create a repository handoff file that records every completed AI interaction.
- Key decisions: Use one root-level append-only file; record structured delivery summaries instead of full transcripts; treat a completed user request and final AI response as one interaction; allow `HANDOFF.md` appends as the sole standing write exception for read-only tasks; enforce the process through the root `AGENTS.md`.
- Result: Added the handoff log, its reusable entry template, and repository-wide AI recording requirements.
- Changed files: `HANDOFF.md`, `AGENTS.md`.
- Verification: Reviewed the Markdown structure and rule scope; `git diff --check` completed successfully; final diff confirmed that only `HANDOFF.md` and `AGENTS.md` changed.
- Remaining work: None.

### 2026-08-10 15:13:34 +08:00

- User goal: Determine whether the current database initialization scripts can initialize System and all other databases/modules.
- Key decisions: Interpret the runtime as one shared MySQL schema rather than one database per module; distinguish enabled runtime modules from optional repository modules; treat missing enabled-module tables and unrecorded migration versions as blockers to claiming complete initialization.
- Result: The bootstrap requires a pre-created empty schema and does not create databases. Its 199-table baseline covers most enabled System, Infra, BPM/Flowable, Pay, CRM, AI, ZSJOS, and Quartz structures, but it omits enabled CRM tables `crm_owner_record` and `crm_performance_config`; optional disabled modules are not covered. The working-tree baseline contains V017/V018-era structure/data changes, while baseline version registration and verification currently stop at V016.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected the bootstrap source chain, README, runtime Maven dependencies and datasource configuration; compared 82 enabled-module `@TableName` mappings against the 199-table schema baseline; confirmed no `CREATE DATABASE` or `USE` statement; no MySQL client or service was available for a controlled empty-schema execution.
- Remaining work: If complete fresh-environment initialization is required, first confirm scope, then add the two missing CRM structures, synchronize V017/V018 baseline registration and verification, and execute bootstrap plus verification against a disposable empty MySQL schema.

### 2026-08-10 15:16:48 +08:00

- User goal: Determine how to adapt `AGENTS.md` and the handoff mechanism for parallel development across multiple branches and workstreams.
- Key decisions: Recommend one branch and one Git worktree per workstream; replace the shared append-only log with a stable root guidance file plus one uniquely named handoff file per workstream; assign each file to one owning branch or agent; avoid a frequently updated shared index; record branch, worktree, base commit, ownership scope, dependencies, verification, integration order, and remaining work.
- Result: Completed a read-only assessment and identified the current single shared `HANDOFF.md` as a merge-conflict hotspot. The repository currently has only `main` and one worktree, which also contains substantial uncommitted business changes, so those changes must be classified before safely creating parallel worktrees.
- Changed files: `HANDOFF.md` only, as required by the existing read-only logging exception.
- Verification: Inspected Git status, all local and remote branches, the worktree list, and the current `AGENTS.md` and `HANDOFF.md` rules.
- Remaining work: Await explicit confirmation of the proposed multi-workstream policy and classification of the current uncommitted changes before modifying repository rules or creating branches and worktrees.

### 2026-08-10 15:19:41 +08:00

- User goal: Clarify that all current uncommitted changes belong to one existing workstream and limit the discussion to rules for future parallel development.
- Key decisions: Preserve the current workstream as-is; apply the proposed branch, worktree, ownership, isolated handoff, dependency, and integration rules only to future workstreams; do not modify repository policy files during this discussion.
- Result: Refined the recommended policy boundary so that adopting multi-workstream rules will not require splitting or relocating the current uncommitted changes.
- Changed files: `HANDOFF.md` only, as required by the existing read-only logging exception.
- Verification: Confirmed the user's stated ownership boundary and checked that the appended entry follows the existing chronological handoff format.
- Remaining work: Review and approve the proposed future-development rules before updating `AGENTS.md` and restructuring the handoff mechanism.

### 2026-08-10 15:25:19 +08:00

- User goal: Determine how to provide one-command production setup for all required databases.
- Key decisions: Define the production target as one shared MySQL schema for currently enabled runtime modules plus Redis, not every optional repository module; recommend a production Compose entry point with a dedicated init/migration job, production-only configuration, secret injection, health gates, verification, and separate fresh-install versus upgrade behavior.
- Result: Produced a read-only implementation recommendation. Confirmed the existing upstream Docker sample is stale and unsafe for production, its referenced build and SQL paths do not exist, no production Spring profile or Flyway/Liquibase integration is present, and the current database baseline must first close the missing CRM table and V017/V018 consistency gaps.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected tracked Docker Compose, Docker environment, Dockerfile, deployment shell, Spring runtime configuration, frontend production environment, SQL bootstrap contract, and local Docker Compose availability; no services were started and no production state was changed.
- Remaining work: Await confirmation of the stated single-host Compose scope, enabled-module boundary, non-goals, affected files, and verification plan before implementing production deployment behavior.

### 2026-08-10 17:59:36 +08:00

- User goal: Implement the approved `zsjos-db` one-command schema generation, migration, drift detection, verification, testing, and single-server production database deployment system for MySQL 8 and Redis, with only current Core modules enabled by default and a clear future optional-module path.
- Key decisions: Keep one operator-facing command backed by reviewed forward-only migrations; use SQL as the desired schema source and Atlas only to generate candidate DDL; preserve the legacy version table while adding per-module SHA-256 records; default the explicit release manifest to `ZSJOS_DB_MODULES=core`; require independent DDL and application users, a migration lock, logical backup, drift blocking, and post-migration verification; never derive or execute live production repair SQL.
- Result: Added the cross-platform CLI and production wrappers, Core manifest/schema/verification, V020 unified metadata and missing CRM tables, baseline/version synchronization, Compose-based MySQL/Redis/migrator deployment, secret-backed user initialization, CI migration checks, and operational documentation. Fresh Bootstrap and existing upgrades now share the same target schema and migration history; enabled optional modules can later add independent V001+ migrations without rebuilding or deleting Core data.
- Changed files: `.gitignore`; `.github/workflows/database-migrations.yml`; `zsjos-db`; `zsjos-db.ps1`; `deploy/production/**`; `docs/operations/database-migrations.md`; `script/sql/mysql/tools/zsjos_db.py`; `script/sql/mysql/modules/**`; `script/sql/mysql/schema/**`; `script/sql/mysql/verify/**`; `script/sql/mysql/migrations/V020__unified_schema_migration_and_crm_tables.sql`; and directly synchronized MySQL Bootstrap, seed, verification, and migration documentation files. Existing concurrent V018/V019 work was preserved and incorporated into the final migration order.
- Verification: Static checks and both root entry points passed; controlled MySQL 8 tests passed for fresh Bootstrap, verification, non-empty protection, V019-to-V020 upgrade, idempotent replay, schema drift blocking, and applied-checksum blocking; an earlier full migration path passed lock, logical backup, Bootstrap, verification, and a final READY plan; Compose configuration and workflow YAML parsed; Bash scripts passed `bash -n` inside the MySQL image; `git diff --check` passed; no test containers or Python cache remain. The final migrator image rebuild could not fetch `python:3.11.7-slim-bookworm` because Docker Hub authentication timed out, and a repeat of the host-based full CLI path was unavailable because the Windows host lacks `mysql` and `mysqldump`.
- Remaining work: Build and publish the immutable migrator image in CI or a network environment with Docker Hub access, then run `plan`, `migrate`, and `verify` against the deployment target using reviewed secrets before starting the application release. No files were staged, committed, pushed, and no production or shared service state was changed.

### 2026-08-10 18:18:55 +08:00

- User goal: Read the project and prepare to start it, with execution plan confirmation required before starting.
- Key decisions: Perform read-only discovery first; do not start services before user confirmation; avoid the repository Compose file because its relative build contexts are absent in the current checkout.
- Result: Identified Spring Boot backend on port 48080 using local MySQL 3306 and Redis 6379, Vue admin Vite dev server, and available Maven/JDK/Docker/pnpm tooling. No relevant listeners are currently active; frontend dependencies are not installed.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected repository instructions, README/configuration, Compose mappings, package scripts, Git status, installed tools, dependency directories, and TCP listeners. No service was started and no shared state was changed.
- Remaining work: Await confirmation of the proposed startup plan and whether to install frontend dependencies and start local MySQL/Redis containers.

### 2026-08-10 18:23:11 +08:00

- User goal: Require MySQL, Redis, and MinIO to run in Docker as part of the local project startup.
- Key decisions: Keep backend and frontends on the host; avoid changing or deleting the existing Docker database volumes; propose a separate local-development container set with independent volumes and host port mappings; do not automatically switch administrator-owned file storage configuration to MinIO.
- Result: Refined the startup plan after discovering existing internal-only MySQL and Redis containers. The existing MySQL container is unhealthy because its persisted root credential differs from the currently mounted secret, while Redis is healthy; neither publishes a host port.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected container state, health history, mounts, networks, port bindings, production Compose configuration, secret-file presence without reading secret values, and backend S3/MinIO integration ownership.
- Remaining work: Await explicit confirmation before creating the new Docker containers and volumes, initializing the fresh development schema, installing frontend dependencies, and starting the backend and frontend processes.

### 2026-08-10 18:27:15 +08:00

- User goal: After deleting the old containers, determine how to continue starting the local project.
- Key decisions: Preserve the existing `zsjos-local_*` Docker volumes; do not assume they are empty; recreate containers with host port mappings using a local-only startup configuration because the repository has no complete local Compose file and the production Compose omits host ports and MinIO.
- Result: Confirmed that containers are absent while local MySQL, Redis, and MinIO data volumes remain. Frontend dependencies are still not installed.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected Docker containers, volume labels and creation times, production environment/configuration, available images, repository Compose files, Git status, and frontend dependency directories. No containers were recreated and no data was deleted.
- Remaining work: Await confirmation to recreate Docker services using the preserved volumes, initialize or migrate the database as needed, install frontend dependencies, and start backend plus both frontend dev servers.

### 2026-08-10 18:44:19 +08:00

- User goal: Start the complete local project after approving the Docker-based dependency plan.
- Key decisions: Preserve the inaccessible historical MySQL volume; create a separate fresh development MySQL volume; reuse the existing local Redis and MinIO volumes; initialize the fresh schema from the repository Bootstrap; align missing Flowable 8 schema metadata with the bundled official SQL; run backend and both frontends on the host.
- Result: Started Docker MySQL, Redis, and MinIO; initialized 203 database tables; built and started the Spring Boot backend; installed locked frontend dependencies; started the administration frontend and employee workbench. Runtime URLs are backend `http://localhost:48080`, admin `http://localhost:5173`, workbench `http://localhost:5174`, MinIO API `http://localhost:9000`, and MinIO console `http://localhost:9001`.
- Changed files: `HANDOFF.md` only in tracked source; generated Maven targets and ignored frontend dependency directories were refreshed. Docker containers, a new development MySQL volume, and development database contents were created.
- Verification: Maven package completed successfully with tests skipped; backend actuator reported `UP`; both frontend roots and MinIO health returned HTTP 200; Redis returned `PONG`; MySQL responded to `mysqladmin ping`; all required ports are listening. Git status contains only the pre-existing untracked `.codex/` and the required `HANDOFF.md` update.
- Remaining work: MinIO is running but is not automatically selected as the administrator-owned file storage configuration. The workbench currently requires a hoisted dependency layout because two imported packages are not declared directly; this should be corrected separately before a clean reproducible install is claimed.

### 2026-08-10 18:59:33 +08:00

- User goal: Assess which foundational settings the current Yudao Infra module still lacks and identify practical improvements.
- Key decisions: Treat the request as read-only analysis; distinguish missing production configuration and governance from missing Infra features; keep business options in dictionaries or owning business APIs and keep deployment, backup, and secrets outside the Infra parameter table.
- Result: Confirmed that Infra already provides parameters, file storage, jobs, API logs, Redis and service monitoring, code generation, data-source configuration, WebSocket, and API documentation. Recommended prioritizing a production security baseline, an approved private MinIO master configuration and file policy, enabled log-retention jobs, actionable monitoring and alerting, environment-safe parameter governance, and least-privilege Infra roles; stale demo and disabled-module seeds should be removed from the Core baseline through a separately reviewed migration.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected Infra controllers, services, security rules, cleanup jobs, frontend management views, runtime YAML, SQL seeds, architecture boundaries, current Git changes, and read-only local database summaries. The local database has 11 active file configurations with a sample cloud configuration as master, three paused cleanup jobs, 59 unprocessed API error logs, and no access logs because local access logging is disabled. No configuration, database row, service, or external state was changed.
- Remaining work: Confirm which recommendation group to implement first; any behavior, SQL seed, permission, or production-profile change requires a scoped facts/assumptions/non-goals/verification proposal and user confirmation before editing.

### 2026-08-10 19:08:37 +08:00

- User goal: Determine whether the proposed AI-CRM workbench requirements for twelve departments can be abstracted into reusable foundational modules.
- Key decisions: Organize capabilities by stable business ownership rather than department names; reuse System, BPM, Infra, and System notification facilities; separate plans, business tasks, SLA work orders, and approvals; model a person/student master with multiple independent service relations and domain cases instead of one universal lifecycle status; keep department workbenches as permission-scoped compositions of shared capabilities.
- Result: Identified reusable ZSJOS platform capabilities for object authorization and user relations, work planning, business tasks, work orders, metric targets and fact snapshots, event/notification integration, forms/rules, files/evidence, and projections/reporting. Identified separate domain modules for customer and sales, product/course/SKU, order/payment/refund, registration and student service, content/media production, examination/employment/quality cases, HR, and administration assets. Confirmed that the current module already contains lead, opportunity, person, product/SKU, business-task, business-event, user-relation, and lead-specific object-permission foundations, but most later lifecycle domains remain target design rather than implemented APIs.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected repository and ZSJOS instructions, architecture and permission documents, the target lead-order-service lifecycle model, current ZSJOS data objects/controllers/services, BusinessTask implementation, System notification facilities, BPM task capabilities, and current Git state. Found two documentation-to-code conflicts: product ownership remains documented as future-confirmed although ZSJOS product/SKU code exists, and module guidance says `@ZsjosPermission` is absent although a lead-only implementation now exists. No business code, schema, configuration, service, or external state was changed.
- Remaining work: Confirm the proposed module map and select a first bounded design slice. Before implementation, reconcile product ownership and object-permission documentation, then produce the required facts, assumptions, non-goals, affected scope, data contracts, and verification plan for user confirmation.

### 2026-08-10 19:15:12 +08:00

- User goal: Exclude the customer/student business area because another team owns it, and identify AI-CRM areas that this workstream can independently build.
- Key decisions: Treat customer, student, sales-conversion, registration, and payment lifecycle tables as out of scope; prioritize reusable workbench capabilities and the New Media content-production domain; defer HR, examination, employment, quality, and finance areas until their upstream data owners and external integrations are confirmed.
- Result: Recommended a non-customer workstream consisting of a decoupled workbench task center, monthly/weekly/daily work plans, SLA work orders, metric targets and fact snapshots, business-event notification integration, and then a New Media module covering platform accounts, responsibility, content planning, publishing records, shared materials, and video production work orders. The current workbench contains lead-specific pages and the current `BusinessTaskServiceImpl` is lead-coupled, so decoupling the task presentation through scene providers is a bounded prerequisite. Files remain owned by Infra while media metadata and permissions remain business-owned.
- Changed files: `HANDOFF.md` only.
- Verification: Inspected current workbench pages/services, ZSJOS controllers, data objects and BusinessTask implementation, repository architecture boundaries, module list, and Git state. Confirmed no HR, asset, media, exam, employment, or quality domain implementation is currently present in ZSJOS. No business code, schema, configuration, service, or external state was changed.
- Remaining work: Confirm whether to start with the shared workbench foundation or the New Media domain; before implementation, confirm exact non-customer scope, ownership of employee/account data, integration boundaries, and the verification plan.

### 2026-08-10 18:08:44 +08:00

- User goal: Obtain the direct macOS zsh commands for preparing and running the implemented production database deployment and migration workflow.
- Key decisions: Invoke the repository Bash wrappers explicitly from zsh; keep the default release module manifest at Core only; generate local secret files outside version control before starting MySQL and Redis.
- Result: Provided the fresh-install preparation, Compose startup, plan, migrate, verify, and subsequent-release command sequences for macOS Terminal.
- Changed files: `HANDOFF.md` only.
- Verification: Commands were matched against `deploy/production/.env.example`, `compose.database.yml`, and the production `zsjos-db` wrapper previously verified with `bash -n`.
- Remaining work: Replace the development image/release values with immutable production release identifiers before the real deployment.

### 2026-08-10 18:14:00 +08:00

- User goal: Clarify whether the production commands repeatedly create MySQL and Redis Docker containers and why another database stack might appear.
- Key decisions: Distinguish stable Compose services from disposable migrator jobs; explain Compose project identity and the boundary between this deployment stack and independently created existing containers.
- Result: Confirmed that repeated `up -d` calls against the same project reuse the same MySQL and Redis containers and persistent volumes, while each `plan`, `migrate`, or `verify` creates one temporary `db-migrator` container that is removed by `--rm`. A separate MySQL/Redis stack is created only when the existing services belong to a different Compose project or were started independently.
- Changed files: `HANDOFF.md` only.
- Verification: Matched the explanation against `COMPOSE_PROJECT_NAME=zsjos`, named Compose services and volumes, and the production wrapper's `docker compose run --rm db-migrator` behavior.
- Remaining work: Before production execution, identify whether the server should use this Compose-managed MySQL/Redis pair or reuse an existing external pair; do not run both unintentionally.

### 2026-08-10 18:20:19 +08:00

- User goal: Clarify why Python commands appeared in the macOS execution instructions.
- Key decisions: Separate production operator commands from developer and CI validation commands; keep Python as an internal implementation detail of the migrator image and wrapper.
- Result: Clarified that production requires only the `deploy/production/zsjos-db` Bash wrapper. Direct `python3` commands are optional repository development tests, while the production image supplies its own Python runtime.
- Changed files: `HANDOFF.md` only.
- Verification: Confirmed the production wrapper invokes `docker compose run --rm db-migrator` and the migrator image entry point invokes the Python CLI internally.
- Remaining work: None.

### 2026-08-10 18:41:50 +08:00

- User goal: Obtain a one-command export of every table and all current values from the deployed database.
- Key decisions: Target the current production Compose-managed MySQL database; read the root password from the mounted Docker secret instead of exposing it in shell history; include schema, rows, views, routines, triggers, and events in a transactionally consistent logical dump.
- Result: Provided a host-side `docker compose exec` plus `mysqldump` command that writes a timestamped SQL file under `deploy/production/backups/` without changing database state.
- Changed files: `HANDOFF.md` only.
- Verification: Matched the command against `deploy/production/compose.database.yml`, including the `mysql` service, `MYSQL_DATABASE` environment variable, and `/run/secrets/mysql-root-password` secret path; no export was executed against a live database.
- Remaining work: Run the command on the host where the production Compose stack is running, then verify the dump file is non-empty and store it securely.

### 2026-08-10 18:47:03 +08:00

- User goal: Resolve the `service "mysql" is not running` error encountered while exporting the current database.
- Key decisions: Use the already running `yudao-infra` MySQL container instead of starting the separate, currently absent `zsjos` production Compose stack; keep all investigation read-only and avoid exposing the root password.
- Result: Determined that the active container is `yudao-mysql` and its business database is `ruoyi-vue-pro`; prepared a direct `docker exec` dump command for that container.
- Changed files: `HANDOFF.md` only.
- Verification: `docker compose ls`, `docker ps -a`, and the target Compose status confirmed `yudao-infra` is running while the `zsjos` stack has no containers; a read-only MySQL query confirmed the database name; `mysqldump 8.4.11` is available inside `yudao-mysql`.
- Remaining work: Run the corrected export command and confirm the resulting SQL file is non-empty before storing it securely.
