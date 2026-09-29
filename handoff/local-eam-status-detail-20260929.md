# EAM status/detail repair

## Registration — 2026-09-29 (Beijing time)
- Workstream: eam-status-detail-20260929; environment: local (environment variable and environment file absent).
- Owner: current chat /root; branch: main; worktree: D:/ZSJ-OS; base HEAD: 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- Goal: repair asset details failing after direct status changes, and preserve usage assignments during status-only updates.
- Non-goals: no historical data repair, permission changes, dependencies, service restart/deployment, branch/worktree operations, commits or pushes; preserve unrelated worktree changes.
- Ownership: EamAssetController.java; EamAssetServiceImpl.java; EamAssetMapper.java; focused asset controller/service/mapper tests; EAM test schema only where needed to match existing asset fields; docs/api/eam-office-procurement-assets.md; this handoff file. No other workstream owns these files in observed active records. Separate record avoids modifying the already-dirty shared handoff/main.md.
- Dependencies: existing EAM/MyBatis test facilities and authoritative HRM/System APIs; no dependency additions.
- Verification: reproduce empty employee/department lookup failure; verify status-only persisted updates retain assignment/snapshot, version and audit behavior; focused Maven tests/compile; scoped diff checks. Live API validation depends on an available updated backend and authorized service lifecycle.
- Target branch/integration order: None.

## Delivery — eam-status-detail-20260929 — 2026-09-29 14:55:34 Beijing time
- Context: local/main/D:/ZSJ-OS; HEAD remains 5fadfb3e9be563ac4cb3e5c142308549944aa465; dedicated handoff record `handoff/local-eam-status-detail-20260929.md`; unrelated existing changes preserved.
- User goal/result: 修复资产直接调整状态后查看详情报错，并保证状态纠正不清空资产归属。详情组装对空员工/部门 ID 安全；关联记录缺失时使用员工姓名快照，部门名称保持空值；状态纠正显式保留使用员工、使用部门、姓名快照、版本和流水字段。
- Changed files: EamAssetController.java; EamAssetServiceImpl.java; EamAssetServiceImplTest.java; EamAssetControllerTest.java; EamAssetStatusPersistenceTest.java; EAM H2 create_tables.sql sync for existing asset fields; docs/api/eam-office-procurement-assets.md; this handoff record.
- Verification: `mvn -f backend/pom.xml -pl yudao-module-eam -am -Dtest=EamAssetControllerTest,EamAssetServiceImplTest,EamAssetStatusPersistenceTest,EamAssetChangeLogServiceImplTest,EamAssetStatusEnumTest -Dsurefire.failIfNoSpecifiedTests=false test` BUILD SUCCESS; 35 tests passed, zero failures/errors/skips. Persistence regression covers all nine statuses, usage/snapshot retention, version/audit/change-log fields, unmatched historical employee snapshot, and explicit clear-usage behavior. `git diff --check` and strict UTF-8 validation passed for 8 task files.
- Diagnosis: direct status update constructed a partial DO with null association fields; MyBatis `ALWAYS` update strategy wrote those nulls. Detail projection looked up null IDs in mocked/real association maps; this path failed for null-associated assets. Existing historical rows with already-lost associations are not automatically repaired.
- Dependencies/integration impact: no new dependency, migration, database write, account/role/menu permission change, branch/worktree operation, commit/push, or service restart. Live authenticated API/browser validation remains unverified because the running backend was not restarted.
- Remaining: restart the backend and verify the authorized Admin flow against the target environment; separately authorize and scope any historical data repair if reliable source records are available.
