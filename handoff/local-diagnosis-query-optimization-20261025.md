# Workstream local-diagnosis-query-optimization-20261025

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root; branch main; worktree D:/ZSJ-OS; base 404387de566fcb9abf4b264f0e005bf0566ea209; target branch/integration order None.
- Goal: reduce diagnosis query amplification while proving unchanged query results.
- Non-goals: schema/data changes, grants, deployment/restart, frontend behavior, dependencies, Git publication.
- Ownership: this record; DiagnosisMetricMapper.java, DiagnosisMetricService.java, WeeklyDiagnosisService.java under backend/yudao-module-zsjos; DiagnosisMetricServiceTest.java, WeeklyDiagnosisServiceTest.java, DiagnosisSqlContractTest.java in their existing account test directory; script/sql/mysql/tools/verify_diagnosis_query_equivalence.py; docs/api/weekly-account-diagnosis.md.
- Dependencies: delivered weekly-diagnosis/loading/clock changes, preserved in the dirty worktree. No active conflicting writer found. Existing feature and object authorization, tenant 1, frozen cohort and revision contracts remain authoritative.
- Verification: focused Maven tests/compilation including permission and SQL binding; read-only actual old/new SQL comparisons in a consistent MySQL snapshot, boundary samples using SELECT-only CTEs, representative EXPLAIN ANALYZE; scoped diff checks. No database writes or UI changes.

## Delivery

- Removed full profile projection from weekly history metrics; the existing Controller feature permission and Service read object annotation remain, together with tenant-aware account/entry reads and association validation. Missing account and foreign entry fail before metrics. No permissions or API shapes changed.
- Standard 7/28-day publication windows now share one cutoff/version selection and both aggregates in a request. Original query remains for nonstandard historical bounds. Preview validation and persistence reuse publication results and ordered likes-history reads; current input, latest revisions, frozen cohort, live deals and snapshots retain their existing rules. No cross-request cache.
- Verification: Maven reactor BUILD SUCCESS; 91 focused tests, zero failures/errors/skips, covering metrics/cache/query counts, history, configured permissions/object authorization, profile submission, reminder and period/SQL binding. Log: C:/Users/EDY/AppData/Local/Temp/zsjos-diagnosis-query-tests.log. The first command did not start tests due to PowerShell splitting an unquoted Maven property; quoting the property resolved it.
- Actual MySQL 8.4.11: read-only consistent-snapshot old/new comparison passed 444 cases across 148 batch-associated accounts, plus 4 SELECT-only boundary fixtures. All original 7/28 counts and missing-time counts match; no fixture/business writes or schema/migration changes. Verification script is retained in the recorded scope.
- Representative local warmed comparison, 24 alternating runs per version: original pair median 2.324 ms, merged query 1.214 ms (approximately 48% lower). New EXPLAIN ANALYZE approximately 1.3 ms. This measures only representative SQL, not an authenticated HTTP request or target-environment page load.
- Documentation synchronized. UI files, grants, migrations, services, dependencies and Git publication untouched. Existing unrelated dirty work preserved. No deployment/restart or real-account acceptance performed; target runtime improvement remains unverified until deployment.
- Ownership released after delivery.
