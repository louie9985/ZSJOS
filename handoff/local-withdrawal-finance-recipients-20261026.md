# Withdrawal finance recipients

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Workstream: local-withdrawal-finance-recipients-20261026; owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5; integration: None.
- Goal: restrict withdrawal finance recipients and new process assignees to enabled, review-authorized members of configured finance center including all descendants.
- Ownership: this record; WithdrawalServiceImpl.java; WithdrawalServiceImplTest.java; new WithdrawalFinanceRecipientsTest.java in withdrawal test package; docs/api/withdrawal-and-offline-payout.md.
- Dependencies: existing SalesOrderObjectPermissionService.isFinanceCenterMember and System department API. Its unrelated dirty changes remain owned by their existing workstream and will not be edited.
- Non-goals: role grants, database writes, historical task/message repair, approval command changes, deployment/restart.
- Evidence: tenant 1 database finance_dept_id=1040; department 1040 finance center, current child 1041. All current withdrawal notify rules have empty specified_user_ids.
- Verification: focused withdrawal tests, real finance membership resolver tests with mocked System/config sources, compile and scoped diff checks.

## Delivery status

- Implemented finance-center membership filtering before review-permission selection. Reuses existing configured-root/all-descendants resolver; affects new BPM assignees and newly published finance event payloads. Applicant notifications and historical records unchanged.
- Updated apply test wiring and added focused recipient cases for root, child, deeper descendant, outside administrator, disabled/no-department/no-permission users, missing configuration and empty candidates. Updated API contract.
- Scoped git diff --check passed. System department implementation inspected: traverses every child level; database query verified current root/child and empty specified recipients with UTF-8/HEX for department labels.
- Maven verification remains environment-blocked: native memory allocation failure, including after limiting heap to 768 MB and active processors to 2. Final host check reported only about 875 MB free virtual memory. No passing compile/test claim; no surefire reports produced for these runs. Remaining check: rerun focused tests when virtual-memory capacity permits, then backend deployment and real-account acceptance separately.
- No database writes, role changes, historical-task rewrites, service restart or Git publication. Source implementation delivered with verification limitation; ownership released.
