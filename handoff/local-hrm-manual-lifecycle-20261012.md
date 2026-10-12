# HRM manual lifecycle / 2026-10-12

## Registration
- Environment: local (ZSJOS_AGENT_ENV unset; no environment file).
- Workstream: hrm-manual-lifecycle-20261012; owner: current Codex chat.
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Goal: implement user-approved free HRM archive editing, correction vs business changes, planned/actual dates, manual plans, message-only lifecycle reminders and affected projections.
- Non-goals: salary scheduler, performance timeout, existing birthday/contract/anniversary tasks, account permissions, deployment/shared database writes.
- Ownership: backend/yudao-module-hrm employee, home, employee reminder APIs/services/jobs/tests; frontend/admin HRM employee/recruit/portal/home/reminder APIs/views/tests; new lifecycle notification provider under backend/yudao-module-zsjos service; scoped HRM schema/migration/verification artifacts; docs/api/hrm-manual-lifecycle.md and directly affected HRM/EAM docs; this handoff. Existing unrelated dirty files are not owned.
- Dependencies: existing System dictionary/notification APIs, HRM public API and EAM lifecycle events. No new dependency.
- Verification: focused HRM tests/reactor compile, notification tests, Admin typecheck and browser verification, isolated MySQL fresh/repeat/partial-failure migration verification, scoped diff.
- Target branch/integration order: None.
- Shared database correction, service/release changes require separately scoped authorization; no such operations currently planned for execution.

## Scope update: message navigation
- Own additive HRM-only navigation branches in Admin Message.vue / NotifyMessageDetail.vue and Workbench notifyMessageAction.ts plus new hrm notification helper. Preserve existing points-redemption edits in the latter; no active handoff ownership found for that file. New reminder implementation reuses System APIs directly from its existing HRM owner, with no dependency change.

- Scope update: EAM EamEmployeeAssetServiceImpl planned-leave projection uses additive HRM DTO field; existing actual-leave fallback retained. HRM/Admin focused test fixtures and verification scripts are owned.
- Scope update: additive HRM destination in ZSJOS WecomClickTicketService and its existing focused test; Workbench HRM notification focused test. No existing edits in these files at inspection.

## Delivery — 2026-10-12
- Implemented editable lifecycle fields, manual correction/business/plan modes, planned vs actual dates, versioned and idempotent transactions, plan revision/cancellation/confirmation, preserved dictionary labels, and manual command adapters. Scheduled employee state/position mutations are disabled; old Quartz handler dispatches only new lifecycle messages.
- Added independent reminder scope/recipient configuration, preview, advance/due deduplication, FIXED typed ADMIN recipients, delivery-time validity checks, System outbox reuse, Admin/Workbench/WeCom destinations. HRM notification provider uses lazy business-service injection to avoid a registration cycle. Existing salary/performance schedulers and birthday/contract/anniversary tasks unchanged.
- Updated actual-only HR/payroll statistics, employee/portal/date projections and Excel contracts, EAM planned-leave DTO consumption, upgrade migration and fresh HRM schema, and docs/api/hrm-manual-lifecycle.md.
- Backend: 95 distinct focused tests passed across HRM commands/plans/reminders/home/history/Spring wiring, EAM and WeCom destination handling. Latest evidence: temporary hrm-manual-final-tests.log, hrm-manual-last-tests.log, hrm-manual-wiring-tests.log and module surefire reports.
- Frontend: Workbench typecheck passed; 41 notification/embed helper tests passed; Admin and Workbench production bundling passed with isolated output directories. Admin vue-tsc reports only 12 pre-existing duplicate-field diagnostics in src/api/zsjos/withdrawal/index.ts; no HRM diagnostics.
- Browser: real Chrome with actual Vue components and synthetic isolated API transport at 1440/390 widths; editable fields/save retains manual status; plan confirmation carries plan version; reminder preview/save; failed load disables submission; visual inspection and no document horizontal overflow. Workbench authenticated iframe/menu/API acceptance and real WeCom delivery were not run.
- MySQL 8: isolated fresh HRM schema + V002 + V003, old-schema upgrade, replay, partial DDL recovery, missing prerequisite, mid-execution and ledger-write failures/recovery passed. Confirmed no employee status changes, preserved original values and uncertainty marker, UTF-8 HEX, matching fresh/upgrade columns and successful version ledgers only after completion. No whole-product production bootstrap acceptance is claimed.
- Shared development DB read-only inspection: ruoyi-vue-pro has 46 employee rows, all tenant 1; 0 new lifecycle columns; HRM versions V001/V002. No shared DB writes, job enablement, service restart, role grants, commits or deployment. V003 and coordinated service/frontend release remain necessary for runtime acceptance.
- Historical unrelated dirty changes preserved, including points-redemption branches in notifyMessageAction.ts.
- Ownership released; target/integration: None.
