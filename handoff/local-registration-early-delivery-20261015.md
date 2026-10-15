# 报名提前交付工作流

- Workstream: local-registration-early-delivery-20261015; owner: Codex.
- Environment: local (environment variable and environment file absent; fallback).
- Branch: main; worktree: D:\ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184.
- Goal: 按用户确认方案保存分班即交付，财务通过后人工完成；补齐取消联动和双端展示。
- Non-goals: 部署、重启、历史批量流转、权限分配、订单生效和业绩口径变更。
- Ownership: registration service/VO/mapper/notification and focused tests; SalesOrderServiceImpl approval-result hook and tests; student service read projections; Workbench RegistrationPages and corresponding types/student detail; Admin registration-pool and student surfaces/types; registration API/operations docs; this record.
- Dependencies: existing registration-per-item and student-service contracts, existing BPM/notification/task APIs. Preserve unrelated performance and notice work.
- Verification: focused Java tests/compile, Workbench and Admin typechecks, targeted desktop/mobile browser fixtures where available, scoped diff. Serialize shared build outputs.
- Target branch / integration order: None.
- Authorization: user explicitly approved complete implementation plan; no external service or data changes.

## Scope update
- Concurrent local-class-free-dates-20261015 owns the delivery class portion of services/api.ts. Its edits appeared during verification and are preserved. This task has completed its RegistrationCase/MyStudent type changes and releases write ownership of that shared file; any subsequent fix must re-read and patch only that section.
- Additional ownership: focused registration browser fixtures in both frontends; standalone notification configuration SQL and isolated verifier (no numbered migration or target DB write).
- New cancellation notification scene requires administrator notification configuration. Provide and verify scoped provisioning artifact; do not silently publish with an unrelated scene/template.

## Delivery — 2026-10-15

- Implemented early service activation in class/legacy planner saves; completion reuses service identity, acceptance/progress and current transferred ownership. Finance and required checklist/attachment completion gates remain.
- Rejection stops early delivery while retaining registration cases; product end scopes cancellation to that case; order termination/continuation reads current service rows FOR UPDATE. Pending first-contact, study-plan, contact, assistance, delivery-stage tasks and contact extensions are cancelled; historical records retained.
- Order-before-case locks, service row locks, versions and existing item uniqueness protect conflicting commands. Cancelled services cannot be reactivated by completion/save. Real concurrent authenticated requests were not executed.
- Both frontends expose delivery state/current class and finance status, prohibit overwriting delivered assignments, retain original snapshots, and direct class changes to existing class management. Admin drawer now fits narrow screens. Workbench retains its existing mobile list-only registration layout.
- Notifications: initial assignment targets each service; completion does not republish. New cancellation scene requires the supplied notification template/rule before runtime acceptance. No target database configuration applied.

### Verification
- Java: 129 focused tests passed (registration, student read/contact/object permissions, approved/rejected/cancelled order callbacks), then 59 affected tests passed after final current-read/lock change, including 14 early-delivery cases. Logs: .tmp-registration-final-java.log and .tmp-registration-lock-final.log.
- Initial Mockito self-attach failure resolved with existing mockito-core 5.23.0 javaagent; no dependency added. Earlier broad SalesOrderServiceImplTest run found unrelated keywordMixedProcessIdsAreNormalizedBeforeTaskLoading invocation-count failure; not changed.
- Workbench npm run typecheck passed. Admin pnpm ts:check remains blocked solely by pre-existing duplicate fields in src/api/zsjos/withdrawal/index.ts; changed files have no diagnostics.
- Browser: Workbench desktop save/finance gate/complete, sibling isolation, denial and retry passed; mobile 390px product list passed. Admin 1440/390px save-to-delivery and finance gate passed, desktop completion passed. Fixtures use synthetic APIs only; no live account or business writes.
- MySQL isolated notification script passed initial/replay/prerequisite failure/partial recovery/admin-disable preservation and UTF-8 HEX checks. Retained schemas: registration_notify_20261015144949750892 and suffix _partial. No numbered migration or ledger change.
- Scoped diff checks passed. API and operations docs synchronized. Shared api.ts class-date changes and all unrelated concurrent work retained.

### Remaining runtime acceptance
- Not deployed/restarted; no historical batch activation; no commits/push. Provision cancellation notification via approved target configuration and deploy both consumers with backend.
- Verify authenticated pending-finance assignment → planner reception/contact → finance approval → manual completion, notification delivery and simultaneous save/reject on the target database. Fixture and mock-based lock tests do not establish live MySQL concurrency acceptance.
- Ownership released on this delivery; shared api.ts registration type section already released above.
