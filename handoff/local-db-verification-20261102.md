# local-db-verification-20261102

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex; goal: separate fresh/runtime verification and truthful findings; default routes exclude new_media.
- Non-goals: database/service writes, permission repairs, deployment, migration checksum changes.
- Branch: main; worktree: D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Ownership: script/sql/mysql/verify-bootstrap.sql, verify/core.sql, tools/zsjos_db.py, tools/test_runtime_verification.py, docs/operations/database-migrations.md, this record.
- Dependencies/integration: None. Verification: focused Python tests, read-only MySQL execution where available, scoped diff.
- Scope update: own tools/test_schema_drift.py to update the existing warn-only regression to the approved fail-closed contract.

## Delivery 2026-11-02 (Beijing time)
- Context: registration branch/worktree/HEAD unchanged; owner Codex.
- Result: separated runtime/fresh assertions, current published default route excludes new_media, legacy numbering and contact aliases recognized; FAIL/MISSING now block final success.
- Files: tools/zsjos_db.py, tools/test_runtime_verification.py, tools/test_schema_drift.py, verify-bootstrap.sql, docs/operations/database-migrations.md, this record. verify/core.sql unchanged.
- Evidence: 3 reporting tests and 23 schema tests pass; scoped diff check passes. Local MySQL read-only verifier executed in fresh mode and twice in runtime mode; runtime 318 rows, 312 PASS, 3 SKIP, 3 FAIL. No database/service writes.
- Remaining: contact claim completeness, area sorting, duplicate role-menu records remain unrepaired. Full fresh-chain and deployed-environment validation not performed; historical seed route compatibility needs fresh-chain acceptance before fresh deployment. Dependency/integration: None; no commit/push. Ownership released.
- Evidence correction: the 318 output lines include informational rows; actual runtime status counts are 251 PASS, 3 SKIP, 3 FAIL (not 312 PASS). Both repeated runtime executions agree. Fresh-mode read-only audit of the populated database reports 7 FAIL, as expected for initialization-only conditions.
