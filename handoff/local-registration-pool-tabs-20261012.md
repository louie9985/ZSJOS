# Registration — local-registration-pool-tabs-20261012

- Owner: Codex /root; environment: local (environment variable and environment file absent).
- Goal: Workbench registration pool pending/processed/all tabs, default pending, server filtering before pagination.
- Non-goals: state transitions, permissions, database, Admin presentation, dependencies, service lifecycle, Git operations.
- Branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; target branch/integration: None.
- Ownership: RegistrationPoolPageReqVO.java; RegistrationCaseMapper.java; new RegistrationPoolPageTest.java; frontend/workbench/src/pages/RegistrationPages.tsx (pool only); src/services/api.ts (pool query type only); test/registration-pool-status*; docs/api/registration-fulfillment-api.md (pool query only); this record.
- Dependencies: existing registration status constants and query permissions; preserve all existing changes, including independent-service labels. Previous student workstream released ownership.
- Verification: focused backend tests including classification/intersection/pagination and omitted parameter compatibility; Workbench typecheck; browser fixture at desktop/mobile, switching, pagination, terminal refresh, error/retry and request races. No authenticated deployment acceptance assumed.

## Delivery — 2026-10-12

- Implemented Workbench pending/processed/all tabs (default pending). Mapper classifies pending+processing vs completed+cancelled before pagination, intersecting keyword order IDs, advanced-filter case IDs and existing status. Optional statusGroup accepts only pending/processed/all; omission preserves Admin and legacy callers.
- Classification changes invalidate pending list/detail responses and reset list, selection and page. Completion/closure reload page 1 and select a remaining row. Notification/deep-link targeting is preserved for navigation, but no longer reapplied on every classification change.
- Verified: Maven reactor compile and RegistrationPoolPageTest (2) plus RegistrationServiceImplTest (24), all passing. New H2 mapper tests exercise actual pagination, both groups, intersection, empty matches, logical deletion, invalid group validation and legacy omission. These are not live MySQL or authenticated HTTP acceptance.
- Workbench npm run typecheck PASS. Browser fixture using existing Vite on 5174 and real Chrome PASS at 1440 and 390: all tabs physically visible, group results, pagination reset, keyword intersection; desktop complete/close refresh; rapid pending/processed/pending race, error/retry and empty states. Screenshots inspected in TEMP/registration-pool-status-browser.
- Test corrections: mobile completion initially attempted an action in the pre-existing hidden detail pane; restricted terminal-action acceptance to desktop. Retry locator updated to account for Ant Design Chinese button spacing. Final browser run passed.
- Admin consumer inspected: existing GET/POST requests omit statusGroup, preserving unfiltered/status-specific behavior covered by mapper compatibility tests. Admin UI was not changed.
- Limits: backend not restarted/deployed; no authenticated live acceptance. Existing mobile detail pane is hidden by prior layout CSS; mobile detail actions are outside this change. No service lifecycle, DB mutation, dependency, branch, commit or push. Existing unrelated changes preserved. Ownership released.
