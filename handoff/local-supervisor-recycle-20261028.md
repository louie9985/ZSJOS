# Registration — local-supervisor-recycle-20261028
- Environment local (environment variable and identity file absent); owner Codex /root; branch main; worktree D:/ZSJ-OS; base 0628f72f2c6208a6cc553f3f29b893de8f4ec697; integration None.
- Goal: user-approved supervisor recycle of managed owned sales leads regardless of main status, preserving main status and historical orders and enabling state-appropriate reassignment and tasks.
- Non-goals: permission grants, unmanaged or unowned leads, education team expansion, database or deployment operations.
- Ownership: SupervisorLeadActionPolicy, SubordinateSalesCommandService, LeadQualificationService/Impl, focused related tests; frontend/workbench/src/services/subordinateLeadActions.ts and its tests; docs/api/zsjos-subordinate-sales.md; this record. Concurrent my-operations ownership excluded.
- Dependencies: existing lead lifecycle, assignment, object authorization, public sea and opportunity; verification: focused backend tests, frontend tests/typecheck and browser flow where available; scoped diff.

## Registration update
- User clarified: do not recycle leads with approvals in progress; public-sea leads may be recycled and their active public-sea cycle and related pending processes must be ended, retaining historical orders and audit records. Verify applicable approval boundaries and lifecycle effects.

## Registration update — approval guard
- Ownership adds ZsjosErrorCodeConstants.java and focused approval-guard tests for a distinct actionable rejection; coordination required before touching any concurrent shared files. Approval query must include order, Lead appeal and transfer-request processes.

## Registration update — directly associated approvals
- User clarified that only approvals directly associated with the Lead block recycling; repurchase approvals linked only to a person do not. Preserve historical orders and audit records. Current implementation must also address public-sea cycle termination and pending tasks.

## Registration update — legacy public-sea compatibility
- Ownership adds LeadPublicSeaPoolCompatibilityRecordMapper use from LeadQualificationServiceImpl and its focused tests. Recycle ends the single active compatibility association with logical deletion while retaining its physical row and the Lead recycle audit; no bulk deletion.

## Registration update — released shared action projection
- Concurrent local-my-operations-20261028 delivery released LeadManagementServiceImpl and LeadManagementServiceImplTest. Ownership adds these two files for sales supervisor action projection and focused tests; retain its delivered changes.

## Delivery — local-supervisor-recycle-20261028
- Sales supervisor managed owned Leads may be recycled regardless of main status or close time. Directly linked pending sales-order approval, Lead appeal and transfer-request approval reject with code 1900003182; person-only repurchase is excluded per user confirmation. Current Lead status and historical orders remain. Active public-sea cycle ends, active legacy compatibility association is logically deleted (physical row retained but not displayed by ordinary active queries), pending Lead qualification/first-follow-up/reminder tasks cancel, and audit/history/notifications are retained. Existing education command behavior remains separate.
- Sales recycled transfer retains business status, updates opportunity owner and schedules first follow-up for open submitted/valid/converted Leads and qualification for submitted; terminal/suspended statuses do not create those tasks. Obsolete opportunity reminder is cleared without deleting follow-up history. Recycled non-submitted Leads cannot release to claim pool because that path changes main status. Legacy qualification sales recycle/transfer paths are guarded; Workbench batch and backend action projection updated. API documentation synchronized.
- Verification: isolated backend snapshot Maven reactor BUILD SUCCESS, 105 focused tests across LeadManagementServiceImplTest, LeadQualificationServiceImplTest and SupervisorLeadActionPolicyTest; Workbench subordinateLeadActions.test.ts 4 tests passed; tsc --noEmit --incremental false passed; scoped git diff --check passed. Synthetic real Chrome fixture verified valid and suspended owned Lead selections enable and open batch-recycle dialog; no actual business command sent. First Maven attempt failed native memory allocation; bounded-memory retry succeeded after fixing test fixture issues.
- Remaining validation: no actual HTTP/real-account end-to-end test or database concurrency test. Approval/recycle paths use current locking reads, but existing opposing Lead/order/cycle lock orders can cause a transaction deadlock and rollback; behavior under concurrent traffic and actionable retry feedback remain unverified. No real business rows changed, service restarted, deployment, role grants, branch or commit/push. Browser fixture temp outputs under frontend/workbench/tmp/supervisor-recycle-ui-20261028; independent Maven snapshot under system temp. Ownership released.
