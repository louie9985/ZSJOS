# Restore sales personnel scope — 2026-10-28

- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Owner/workstream: Codex / local-sales-personnel-restore-20261028.
- Branch/worktree/base: main / D:/ZSJ-OS / 0628f72f2c6208a6cc553f3f29b893de8f4ec697.
- Goal: restore pre-delivery sales current personnel navigation to enabled sales_specialist users; prevent department supervisors without that post from entering individual target rows.
- Non-goals: change delivery behavior, historical facts/targets, database, grants, deployment, commits or branches. Preserve later enabled-only historical presentation and frozen attribution fixes.
- Ownership: PerformanceAccess.java and PerformanceAccessTest.java under service/performance; docs/api/sales-performance.md; this record.
- Dependencies: existing System post/user APIs and domain context; no new dependencies. Target branch/integration order: None.
- Verification: focused access/target/domain/calculation tests; inspect frontend consumption and target aggregation; scoped diff checks. No shared build outputs if concurrent compilation is active.
- Conflict resolution: current documentation's all-enabled-department current personnel scope conflicts with the user's explicit sales/delivery independence and restoration request; user request takes precedence.

## Delivery
- Restored current sales tree candidates to access.sales(); delivery domain retains visibleEmployees(). Supervisors without sales_specialist no longer enter current sales target rows. No role-name inference or client filtering added.
- Reviewed target aggregation: existing recorded historical targets remain in aggregation; missing targets continue to derive from access.sales(). Current-follow-up assignees already use sales() for sales. Historical contribution/fact authorization remains separate and unchanged. This is a personnel-scope restoration, not a blanket rollback of later performance features.
- Workbench consumes the server tree and filters target rows against it; no Vue sales-performance page or frontend source change. API documentation now states the restored post scope and sales/delivery independence.
- Maven reactor compilation and selected tests ran: 85 tests, 84 passed, 1 pre-existing error in PerformanceDomainTest.controllerBoundariesCannotBeSelectedByRequestParameters (expects old delivery controller annotation; current controller uses independent DeliveryPerformance services). Access tests: 29/29 passed, including supervisor exclusion and sales/delivery context restoration. Target/history aggregation regressions passed. Separate DeliveryPerformanceWiringTest passed, verifying independent sales/delivery detail beans.
- Logs: %TEMP%/zsjos-sales-personnel-restore-20261028.log and %TEMP%/zsjos-sales-personnel-wiring-20261028.log; reports under backend/yudao-module-zsjos/target/surefire-reports. Scoped git diff --check passed.
- No database, accounts, service restart, deployment, commit or push. Authenticated API/browser validation of the deployed page remains unverified because the running service has not been updated by this task. Local source delivery complete; ownership released.
