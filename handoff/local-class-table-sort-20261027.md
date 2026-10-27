# 班级表头排序

## Registration — 2026-10-27
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- ID: local-class-table-sort-20261027; owner: current Codex chat.
- Goal: Workbench class name, exam date and service-count header sorting, globally before pagination and within groups.
- Non-goals: permissions, database, deployment, dependencies, commits, other table columns.
- Branch: main; worktree: D:\ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5; integration: None.
- Ownership: DeliveryClassGroups.tsx; services/deliveryClassGroups.ts; DeliveryClassPageReqVO.java; DeliveryClassGroupService.java; DeliveryClassGroupServiceTest.java; test/delivery-class-sort-browser.py; docs/api/delivery-class-management.md sorting paragraph; this record.
- Dependencies: existing scoped class queries and response summaries; no active ownership found for these implementation files. Preserve unrelated edits.
- Verification: focused backend tests/compile, frontend typecheck, isolated real-browser sorting and pagination requests; inspect Admin consumers for compatibility.

## Delivery — 2026-10-27
- Added controlled name/date/count header sorting and grouped endpoint sort validation; sort before root/child pagination, preserve system ranks, missing dates last, earliest visible child date for mixed groups. Switching sort resets page and child restoration.
- API documentation synchronized. Admin calls only the original flat `/delivery-class/page` and `/my-page`; no Admin behavior changes or group endpoint consumers found.
- PASS: Maven reactor focused `DeliveryClassGroupServiceTest` (15 tests, including four new sorting/validation cases), compilation included. Report timestamp 18:44; test classes compiled after final test-source changes.
- PASS: full Workbench `npm run typecheck`; existing classGroupRestore/deliveryClassGroups Vitest suites (8 tests).
- PASS: isolated Chrome desktop header ascending/descending/clear, page reset, sorted child request pagination and error/retry. Mobile 390px retains existing forced card layout without horizontal overflow. Desktop and mobile screenshots captured under temporary zsjos-class-sort; desktop inspected.
- Browser uses synthetic API adapter and verifies requests/interaction; backend tests verify actual service ordering against scoped H2 fixtures. Live-account HTTP acceptance and production-volume performance remain unverified; running backend was not restarted/deployed. Explicit sorting loads full authorized matching summaries for global aggregation.
- Browser test selectors corrected for icon-bearing accessible names and Ant Design two-character button spacing; mobile check follows the established card-only contract.
- No database, permissions, dependency, commit or deployment changes. Ownership released.
