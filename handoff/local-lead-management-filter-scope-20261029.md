## Registration — local-lead-management-filter-scope-20261029
- Environment local; owner Codex /root; branch main; worktree D:/ZSJ-OS; base ba3e687e678ce92b67f16615244e47007d0892d4; integration None.
- Goal: retain management visibility when filtering sales progress or follow-up conditions. Non-goals: legacy owner inbox semantics, grants, SQL, deployment, unrelated edits.
- Ownership: LeadMapper.java, LeadFollowUpResponsibilityTest.java, docs/api/zsjos-lead-submission-dispatch.md, this record.
- Dependencies: existing service-resolved visibility and published filters. Verification: H2 SQL result regressions for source/owner/empty/all scopes and legacy responsibility; consumer inspection; focused Maven tests.
## Delivery — 2026-10-29
- Added management-audience guard to preserve service-resolved source/owner visibility for following/today/overdue; legacy responsibility remains unchanged. Updated API contract clarification.
- Added executed H2 SQL result regression covering source-only, owner-only, unrelated users, empty scope and query-all for all three filters. Regression and existing legacy list-responsibility test passed. LeadMapperSqlTest 17/17 and LeadManagementServiceImplTest 66/66 passed; total 89 passed / 97 executed.
- Maven production/test compilation passed. Eight existing calendar/search tests failed because their fixture lacks submitted_at used by calendar SQL; unrelated to the changed scope branch, not repaired. Scoped diff check passed.
- Consumer inspection: Workbench sends audience=management through GET page / POST search-page; Vue Admin uses same endpoints without the affected audience filters, unchanged. No real-account HTTP/browser or production acceptance performed; no deployment/service restart/database writes/grants/commit/push. Ownership released.
