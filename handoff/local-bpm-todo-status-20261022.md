# Registration — local-bpm-todo-status-20261022

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root; branch main; worktree D:/ZSJ-OS; base 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal: return the actual BPM task status in todo-page responses.
- Non-goals: workflow, historical data, permissions, SQL, dependencies, deployment, service or Git operations; preserve unrelated changes.
- Ownership: BpmTaskConvert.java; new BpmTaskConvertTodoStatusTest.java in its adjacent test package; docs/api/bpm-task-status.md; this record.
- Dependencies: existing task-local variables loaded by getTaskTodoPage; existing FlowableUtils status accessor. No other workstream owns these files in current records.
- Target branch / integration order: None.
- Verification: focused BPM conversion tests and module compilation; inspect both Workbench and Admin consumers of the existing status field; scoped diff checks. No authenticated environment acceptance assumed.

## Delivery — 2026-10-22

- Todo conversion now extracts each task's actual local-variable status with FlowableUtils before process-summary enrichment, including when that summary is missing. Missing status remains missing; pagination and ordering are retained.
- Added four regression cases: mixed RUNNING/WAIT statuses with process summary and pagination, missing summary, absent status, empty page. Added API contract documentation.
- Verification: Maven reactor `mvn -pl yudao-module-bpm -am test -Dtest=BpmTaskConvertTodoStatusTest,BpmPartnerStartSubjectConvertTest,BpmTaskNotificationSubjectTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false` passed, 9 tests, 0 failures/errors, including 4 new cases. Main and test compilation passed. Log: C:/Users/EDY/AppData/Local/Temp/bpm-todo-status-test.log. Initial test fixture imports and interface mocks were corrected; final fixtures mock concrete Flowable entities so Hutool can discover bean properties.
- Consumers inspected: Workbench api.ts returns this response unchanged and BpmApprovalCenterPage renders task.status; Vue Admin task API uses the same route and existing enum values. No frontend contract or code change required. Scoped whitespace checks passed.
- Limits: no authenticated live HTTP/browser acceptance, backend deployment/restart, database mutation or Git publication. Updating the running backend is required for the UI to receive corrected responses. Ownership released.
