# local-class-exam-date-display-20261021

## Registration — 2026-10-21 17:10 Beijing
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent). Owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base/HEAD: 404387de566fcb9abf4b264f0e005bf0566ea209. Target branch/integration order: None.
- Goal: display actual frozen class dates instead of full exam names in Workbench class cards and table.
- Non-goals: backend/API/DB changes, calendar selection changes, Admin UI changes, grants, dependencies, Git or service operations.
- Ownership: frontend/workbench/src/pages/DeliveryClassPage.tsx (date presentation only); docs/api/delivery-class-management.md (date presentation contract only); this record. Preserve existing changes to exception classes and all other dirty work. Historical overlapping workstreams have delivered; use this separate record to avoid concurrent shared-log appends.
- Dependencies: existing response startDate/endDate/exactDate fields derived from frozen snapshots. User instruction supersedes legacy documentation requiring raw snapshot fallback; missing dates show 未设置 without inventing dates.
- Verification: existing focused class tests, npm run typecheck; real Chrome using existing isolated batch fixture with seeded single-day/range/legacy/missing/system rows, desktop table/cards and mobile cards; scoped diff check. Temporary verification scripts/artifacts remain outside tracked source.

## Delivery — 2026-10-21 17:12 Beijing
- Environment/owner/branch/worktree: unchanged from registration; HEAD remains 404387de566fcb9abf4b264f0e005bf0566ea209.
- User goal/result: Workbench class cards and table now share classExamDate, showing frozen startDate with exactDate compatibility and a different endDate as a range. Missing dates show 未设置; system classes show —. Full exam names are never used as date fallback. No persisted data changed.
- Decisions: current explicit user request replaces the historical raw-name display fallback; synchronized the affected date contract in docs/api/delivery-class-management.md. Preserved pre-existing exception-class presentation and documentation changes.
- Changed files: frontend/workbench/src/pages/DeliveryClassPage.tsx; docs/api/delivery-class-management.md; this record.
- Verification: npm run typecheck passed; 9/9 tests passed across DeliveryClassExamCalendar.test.ts, delivery-class-review-fixes.guard.test.ts, deliveryClassBatch.test.ts. Chrome isolated fixture passed desktop cards/table (1440) and mobile cards with table preference (390), covering single date, range, exactDate compatibility, missing dates and system classes; no full names, page errors or horizontal overflow. Desktop table and mobile screenshots visually inspected. Scoped git diff --check passed.
- Evidence: C:/Users/EDY/AppData/Local/Temp/zsjos-class-exam-date-browser.py; screenshots under C:/Users/EDY/AppData/Local/Temp/zsjos-class-exam-date-browser/. Existing local Vite service reused without reconfiguration; all API requests use the pre-existing fixture adapter.
- Dependency/integration impact: None; no API/schema/dependency/permission changes. Production build not applicable to this local field presentation edit. No deployment/restart, live-account acceptance, commit/push or external state mutation. Remaining: deployed/live-account verification is separate. Ownership released.
