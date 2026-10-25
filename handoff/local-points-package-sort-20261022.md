# local-points-package-sort-20261022

## Registration
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root. Branch: main. Worktree: D:/ZSJ-OS. Base: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal: return points packages in ascending credits order, then ascending ID, for both management and employee selection.
- Non-goals: request history sorting, database/schema/data/permissions, frontend layout, dependencies, deployment/restart and Git publication.
- Ownership: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/pointsredemption/PointsRequestService.java (packages query only), docs/api/points-redemption.md (package ordering), this handoff record. Targets have no pre-existing changes; unrelated dirty work preserved.
- Dependencies: existing shared package-list/packages endpoints; Admin and Workbench consume their returned array order. No active overlapping ownership identified. Target branch/integration order: None.
- Verification: focused existing points tests with Maven reactor compilation; read-only local MySQL numeric-order query; inspect both frontend API and render consumers; scoped diff checks. No frontend code/layout change.

## Delivery
- Changed the shared package query to credits_amount ASC, id ASC and documented the returned order. Both package endpoints retain their original enabled filtering; no request-history changes.
- Verification: Maven reactor test command exited 0; four existing tests passed (PointsProcessResultTest, PointsPackagePermissionProviderTest, PointsObjectPermissionProviderTest), including compilation of the changed service. Read-only local MySQL queries verified all seven current tenant-1 packages in numeric order (100, 500, 1000, 2000, 3000, 5000, 8000), and a derived-table fixture verified numeric ordering and equal-credit ID ordering without writes. Scoped diff check passed.
- Admin API/list and Workbench API/list/cards/selector reviewed: all retain backend array order; no frontend source, layout or build-input changes. Browser and authenticated live-endpoint acceptance were not performed; source inspection is the compatibility evidence, not runtime acceptance.
- No database/data/grant changes, service restart/deployment, commits or pushes. Backend deployment is required for the running application to use the new order. Ownership released.
