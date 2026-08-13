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
