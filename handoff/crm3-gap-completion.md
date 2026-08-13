# CRM3 Gap Completion

- Workstream ID: `crm3-gap-completion`
- Goal: Complete the confirmed CRM3 delivery gaps for account/partner lifecycle, notifications and maintenance, asynchronous CRM exports and audit/readonly impersonation, cashback, withdrawal approval, and offline payout recording.
- Non-goals: Real SMS delivery, real WeCom business delivery, export email delivery, refunds/order reversal, automatic tax or fees, bank transfer integration, automatic reconciliation, remote push, or execution of database migrations against a real environment.
- Branch: `codex/crm3-gap-completion`
- Worktree: `D:\ZSJ-OS-worktrees\crm3-gap-completion`
- Base commit: `d64cd28c397e1a161a1a2372cd08642d254aced6`
- Target branch: `main`
- Ownership scope: System account validation and session revocation contracts; ZSJOS personnel/partner business state; System business-notification catalog/audit and maintenance gate; ZSJOS CRM export, audit, readonly impersonation, cashback, withdrawal and payout domain; directly affected Admin/Workbench pages, SQL migrations, BPM definitions, tests and documentation; this handoff.
- Owner: Codex `/root`
- Dependencies: Existing Yudao System authentication/permission/notification facilities, Infra private file storage, BPM public APIs, Redis/Lock4j facilities, and current ZSJOS lead/order/product models. No new npm or Maven dependency is planned.
- Integration order: Account and partner lifecycle -> notifications and maintenance -> exports/audit/readonly impersonation -> cashback -> withdrawal and payout. Cashback order integration is serialized after `order-supervisor-confirmation` is integrated or its overlapping order files are reconciled.
- Verification plan: Focused service, permission, concurrency and state tests; ZSJOS/System/BPM module tests and compilation as affected; MySQL migration syntax/repeatability review; Workbench/Admin tests, typecheck and production builds; desktop/mobile browser verification when an authenticated runtime is available; no real migration or external-service mutation.
- Status: `in-progress`

## Known facts and assumptions

- The primary `main` worktree contains an unrelated uncommitted duplicate-contact SQL parser fix and must remain untouched.
- `profile-center` and `order-supervisor-confirmation` have uncommitted overlapping frontend/order work; this workstream will not modify their owned files until integration or explicit reconciliation.
- Yudao roles, posts, permissions and data scopes remain authoritative; no primary-business-identity or generic actor table will be added.
- Existing lifecycle rules override stale delivery-document order and appeal rules. Confirmed delivery deviations are recorded in the approved plan for this workstream.

## Delivery Entries

## Stage 1 checkpoint - 2026-08-13 19:53:21 +08:00

- Branch: `codex/crm3-gap-completion`
- Worktree: `D:\ZSJ-OS-worktrees\crm3-gap-completion`
- HEAD commit before stage commit: `d64cd28c397e1a161a1a2372cd08642d254aced6`
- User goal: Continue the approved CRM3 gap-completion implementation, beginning with account, personnel, and partner lifecycle behavior.
- Key decisions: Keep System users, roles, posts, permissions, and sessions authoritative; keep login validation compatible with historical passwords; use a ZSJOS personnel-state projection; retain partner profiles after conversion; add no role grants; use unoccupied menu IDs `6850` through `6855`.
- Execution result: Implemented tenant-scoped login-identifier validation, password rules, username/mobile login, session revocation, last-enabled-super-admin protection, personnel state, partner account lifecycle and conversion, Lead source-department snapshots, Admin pages, migration V048, API and architecture documentation. Corrected duplicate System error codes and the original V048 menu-ID collision.
- Changed files: System auth/user/permission API, services, VOs and tests; ZSJOS personnel/partner/Lead model, services, controllers and tests; Admin account/personnel/partner pages and APIs; Core schema, bootstrap, V048, verification SQL and migration documentation; directly affected architecture/API documentation; this handoff.
- Verification evidence: System focused tests 83 passed; ZSJOS focused tests 14 passed; dependency-graph compilation passed as part of both test runs; Admin production Vite build passed after formatting; focused Prettier and ESLint passed; `git diff --check` passed; V048 menu IDs and bound-user unique index were checked repository-wide with no remaining collision.
- Dependency or integration impact: Adds public System user creation and organization-update DTO/API methods used by ZSJOS. No new npm or Maven dependency. No database migration was executed. No remote branch was pushed and no shared service was changed.
- Remaining work: `zsjos-db check` confirms desired Core schema and fresh baseline are byte-identical, then stops on pre-existing missing Core mappings for `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`; full SQL execution/repeatability remains unverified without an approved disposable database. Authenticated browser desktop/mobile verification is unavailable. Concurrent removal/disable of different last super-admin accounts has no shared database lock and remains a concurrency risk. Continue with stages 2 through 5.

## Stage 2 checkpoint - 2026-08-13 20:12:04 +08:00

- Branch: `codex/crm3-gap-completion`
- Worktree: `D:\ZSJ-OS-worktrees\crm3-gap-completion`
- HEAD commit before stage commit: `a55a068c2e92aa34c97d8313402010848cb6743e`
- User goal: Continue the approved delivery gaps with notifications, scheduler maintenance guards, and a global maintenance-mode control.
- Key decisions: Reuse System notification and Infra configuration facilities; retain authentication SMS infrastructure while excluding SMS from business notification rules; keep real enterprise-WeChat delivery out of scope; use a database-authoritative global switch with no tenant, role, or IP bypass; permit only safe methods and fixed authentication/callback/toggle writes; add no role grants.
- Execution result: Added controlled System-config mutation, public maintenance-state read and super-admin-only audited toggle APIs, HTTP 503 write filtering, Admin control page, maintenance guards for current ZSJOS mutating schedulers, business-channel validation, migration V049, bootstrap verification, API and architecture documentation.
- Changed files: Infra Config public API/service/tests; System maintenance API/controller/service/filter/tests, notification-channel validation/tests and operation-log constants; ZSJOS Lead/task/work-plan schedulers and focused tests; Admin maintenance API/page and notification-rule page; V049/bootstrap/verification/migration docs; maintenance API and permission-flow docs; this handoff.
- Verification evidence: System/Infra focused tests passed, including 14 System maintenance/notification tests; two ZSJOS scheduler tests passed and the full dependency graph compiled; focused Prettier and ESLint passed; Admin Vite production build passed; `git diff --check` passed; V049 config key and menu IDs 6860-6861 were checked repository-wide without collision.
- Dependency or integration impact: Adds a controlled Infra config update contract and System maintenance API consumed by ZSJOS schedulers. No new dependency, role grant, real SQL execution, external message, shared-service mutation, or remote push.
- Remaining work: Repository-wide `vue-tsc` remains blocked by pre-existing missing auto-import declarations beginning in `App.vue`; Vite production build passes. `zsjos-db check` still stops on the pre-existing missing Core mappings `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`. Real SQL repeatability, authenticated browser checks, and the delivery document's unimplemented notification outbox/retention/hidden-audit semantics remain unverified or deferred. Continue with stages 3 through 5.

## Stage 3 checkpoint - 2026-08-13 20:51:23 +08:00

- Branch: `codex/crm3-gap-completion`
- Worktree: `D:\ZSJ-OS-worktrees\crm3-gap-completion`
- HEAD commit before stage commit: `0494a3f81a53a76348dc91fdc02f9928e7d837ee`
- User goal: Continue the approved CRM3 delivery gaps with asynchronous exports, unified business audit, and read-only impersonation while preserving the existing permission system.
- Key decisions: Keep four fixed async-only export types and creator-only access; recheck type permissions on download; keep business and impersonation audit separate; use the target System user's identity and department data scope; block all impersonated writes and cross-tenant visit mode; add no role grants. The repository-owned Excel starter is not added without the separately required dependency confirmation, so actual spreadsheet providers remain unavailable instead of producing placeholders.
- Execution result: Implemented audited 30-minute-idle read-only impersonation with target-account revalidation and conditional session transitions; fixed-catalog sensitive-data-safe business audit; seven-state leased asynchronous export tasks with permission snapshots, conditional concurrency transitions, three attempts, row limit, seven-day expiry and 90-day terminal-task retention; Admin APIs/pages; migration V050, synchronized Core/fresh schema, verification SQL, architecture and API documentation. Export creation rejects types without an installed provider.
- Changed files: ZSJOS impersonation, audit and export controllers, services, mappers, DOs, framework interceptor and focused tests; Admin Axios impersonation propagation, APIs and pages; V050/bootstrap/Core/verification SQL; permission-flow and API documentation; this handoff.
- Verification evidence: Dependency-graph Maven test run compiled all affected modules and passed 15 focused tests (2 interceptor, 3 impersonation service, 3 audit, 7 export); focused frontend Prettier and ESLint passed; Admin Vite production build passed in 30.23 seconds; Core and fresh schema SHA-256 hashes are identical; V050 menu IDs 6870-6878 have no repository collision; `git diff --check` passed.
- Dependency or integration impact: Uses existing System user/permission, Infra private-file and maintenance APIs. No new Maven/npm dependency, role grant, real migration, external message, shared-service mutation, remote push, or provider file was added. A cancellation after Infra file creation can leave an unreferenced file because the public Infra API has no deletion contract; task state remains correct through conditional updates.
- Remaining work: Confirm adding `yudao-spring-boot-starter-excel` to ZSJOS before implementing real lead/order `.xlsx` providers, watermarks and entry-page actions; cashback/withdrawal providers follow their stages. Repository-wide `vue-tsc` remains blocked by pre-existing missing auto-import declarations in unrelated views; Vite production build passes. `zsjos-db check` remains blocked by pre-existing missing Core mappings `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`. Real SQL execution/repeatability, authenticated browser checks and export delivery requests remain unverified.
