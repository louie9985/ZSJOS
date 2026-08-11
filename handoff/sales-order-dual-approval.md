# Sales Order Dual Approval Workstream

- Workstream ID: `sales-order-dual-approval`
- Goal: Implement direct sales-order entry for eligible leads and BPM-backed parallel approval by the registration-fulfillment and finance-settlement department pools.
- Non-goals: Payment-channel collection, refunds, cancellation, registration fulfillment execution after approval, service-relation activation, production migration execution, deployment, and shared service reconfiguration.
- Branch: `codex/sales-order-dual-approval`
- Worktree: `D:\ZSJ-OS-worktrees\sales-order-dual-approval`
- Base commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- Target branch: `main`
- Ownership scope: `backend/yudao-module-zsjos`, employee workbench sales-order entry and approval views, sales-order BPM definition, V023 database migration and synchronized desired-schema/bootstrap artifacts, directly affected API/business/architecture documentation, and this handoff file.
- Owner: Codex `/root`
- Dependencies: V021 and V022 must be integrated before V023. Existing System user/department, Infra file, ZSJOS product/SKU, lead/opportunity, and Yudao BPM public APIs remain authoritative.
- Integration order: Integrate V021, then V022, then this workstream. Resolve any shared schema/bootstrap or lead action projection changes during integration and rerun affected checks on the integration branch.
- Verification plan: Focused ZSJOS service/controller tests; Maven module tests and compile; workbench tests, typecheck, build, and desktop/mobile browser checks; SQL manifest/schema/repeatability checks; BPM process-definition validation; real API checks when an authorized runtime is available.
- Status: `in-progress`
- Final commit: None
- Verification evidence: None
- Unresolved risks: The base commit does not contain V021 or V022, so V023 migration-order checks can only be finalized after those predecessors are integrated. Runtime BPM deployment and service restart require separate confirmation.

## Delivery Log

### 2026-08-11 00:03:00 +08:00

- Branch: `codex/sales-order-dual-approval`
- Worktree: `D:\ZSJ-OS-worktrees\sales-order-dual-approval`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Implement direct sales-order entry for eligible leads, the confirmed field contract including one province/city cascader, and parallel registration-fulfillment plus finance-settlement approval pools with rejection correction and resubmission.
- Key decisions: Migration is fixed at V023 and depends on V021 then V022; order entry does not require a payment callback; active orders are unique per lead; province/city names and five dictionary fields are server validated; approval authorization is derived from configured departments and the user's BPM task rather than role names; rejected orders are corrected in place and produce a new immutable approval round and BPM instance.
- Execution or analysis result: Added sales-order/order-item/approval-round/config persistence, APIs, validation, snapshots, idempotency and object authorization; added dual-branch BPMN and status listener; connected sales entry, rejection correction, approval inbox, voucher upload, multi-SKU amount entry and combined province/city selection in the workbench; synchronized V023, desired schema, bootstrap/verification artifacts and business/API/operations documentation.
- Changed files: `backend/yudao-module-zsjos` sales-order implementation and lead action projection; `frontend/workbench` sales-order entry/approval UI, API/types/routes and validation tests; `script/bpm/zsjos_sales_order_dual_approval.bpmn20.xml`; `script/sql/mysql/migrations/V023__sales_order_dual_approval.sql` plus synchronized schema/bootstrap/check files; directly affected architecture, business, API and deployment documents; this handoff file.
- Verification evidence: ZSJOS compile passed; focused `LeadManagementServiceImplTest` and `SalesOrderServiceImplTest` passed 24/24; frontend typecheck passed; frontend tests passed 49/49; frontend production build passed with the existing large-chunk warning; BPMN parsed as XML; desired schema and bootstrap schema are byte-equivalent; `git diff --check` passed. Full ZSJOS tests ran 94 tests with 93 passing and one unrelated existing failure in `LeadFollowUpRuleServiceImplTest.updatesTimeoutAndAdvancesVersion` (`1900003023`); no files for that rule were changed by this workstream.
- Dependency or integration impact: V021 and V022 are absent from the base and must be integrated before V023. `zsjos-db.ps1 check` therefore reports the expected discontinuity `[1..20,23]`. Administrators must configure valid registration/finance department IDs, assign the approval menu for discoverability, and publish the BPMN in a controlled deployment; business authorization remains dynamic by department and task.
- Remaining work: No commit, migration execution, BPM deployment, service restart, real API request, or browser QA was performed because those operations require integration/runtime availability or separate confirmation. Rerun database checks and affected tests after V021/V022 integration, then perform authorized desktop/mobile runtime verification.
