## Registration - local-newcomer-pool-20261009

- Owner: Codex /root; environment: local (fallback); branch: main; worktree: D:/ZSJ-OS; base: cf11407a4b8a2d1485165477ad31c6278f2cec58. Target branch/integration: None.
- Goal: implement supervisor-configured daily newcomer priority for automatic Lead dispatch.
- Non-goals: no specified/self/claim routing changes, role grants, deployment, shared service lifecycle, or unrelated worktree edits.
- Ownership: ZSJOS lead dispatch/subordinate services, related DO/Mapper/VO/controller and focused tests; Workbench subordinate sales page and API types; relevant SQL core schema/bootstrap/migration/manifest; directly affected lead dispatch documentation; this handoff record.
- Dependencies: existing System user/department scope API, Redis rotation and reservation, MySQL migration chain. Single writer for all owned files.
- Verification: focused backend tests/compile, Workbench typecheck and browser flow, controlled MySQL migration execution/repeatability, scoped diff and docs checks. Report any environment-blocked verification.

## Scope update

- Include newcomer object-permission provider and focused tests, subordinate advanced-filter label, and a retained isolated MySQL verification script. These support the same feature; no shared database mutation or service lifecycle operation is included.

## Local integration continuation

- After the local database/backend prerequisites were explained, user requested continuation twice. Scope now includes applying the verified V295 to `yudao-mysql` / `ruoyi-vue-pro` and restarting the existing local Java backend on port 48080 with the same local profile. Preserve role assignments, back up affected schema/ledgers/menu metadata, and verify against the retained isolated fresh schema. Role permission assignment remains administrator-owned.
- Directly affected documentation includes `docs/architecture/data-and-permission-flow.md` and `docs/frontend/zsjos-menu-coverage.md` for the new independent operation permission and Workbench-only editing entry.

## Verification milestone — 2026-10-09

- Implemented config and Beijing daily counts, priority auto-dispatch scan with ordinary fallback, current quota read under lock, atomic acceptance increments, history priority marker, independent button and object permission checks, Workbench config dialog/status/remaining quota, and advanced-filter state.
- Backend: 44 focused tests passed across LeadDispatchServiceImplTest, NewcomerPoolServiceTest, SubordinateSalesServiceImplTest, SubordinateNewcomerPermissionTest and SubordinateSalesObjectPermissionProviderTest. Includes existing dispatch regressions, priority/fallback/rotation, success-only consumption, stale acceptance, Beijing boundary, config preservation, managed scope and method-security allow/deny.
- Workbench: final `npm run typecheck` passed. No new dependencies. Scoped `git diff --check` passed.
- MySQL 8.4.11: `python script/sql/mysql/tools/test_newcomer_pool.py --fresh` passed initial/repeat/partial/prerequisite/wrong-index/wrong-type/ledger-failure/existing-marker recovery, Chinese HEX and no role grants; actual Mapper SQL passed 32 concurrent increments, tenant/date isolation and config preservation. Full bootstrap plus Core migration chain passed. Isolated schemas retained with prefix `newcomer_20261009132710`.
- Local integration: V295 applied to `yudao-mysql` / `ruoyi-vue-pro`; schema matches the isolated fresh schema; original history row count and role-menu count/sum preserved. Menu/ledger backup: `C:/Users/EDY/AppData/Local/Temp/newcomer-v295-before-20261009134247.sql`. No historical usage backfill or role grants.
- Runtime: `mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package` passed after stopping the old Java process that locked the target JAR. Local profile restarted on port 48080, PID 44252; startup confirmed. Runtime logs under `C:/Users/EDY/AppData/Local/Temp/zsjos-newcomer-20261009134505.*.log` (may contain existing application sensitive request logging; do not copy raw logs into delivery).
- Browser: user logged into http://127.0.0.1:5174/zsjos/subordinate-sales. New backend successfully loads sales details; config button absent without its grant. Full config save/disable/re-enable and visual dialog verification remain pending administrator assignment of `zsjos:subordinate-sales:newcomer-pool` to the chosen test role. Requested through user input; no grant applied by this workstream.
- No commit/push/branch changes. Pre-existing unrelated changes preserved. Ownership retained until pending browser verification is finished or explicitly handed off.
