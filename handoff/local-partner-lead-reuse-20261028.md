# Registration
- Environment local; owner Codex /root; branch main; worktree D:/ZSJ-OS; base 42fa03db3302282101beb2294b2e90c23991f4e6; integration None.
- Goal: reuse LeadManagementPage for partner leads with existing scoped read-only APIs. Non-goals: backend, permissions, database.
- Ownership: LeadManagementPage.tsx, SubordinatePartnerPage.tsx, docs/partner-lead-reuse.md under frontend/workbench, this record.
- Verification: TypeScript, focused existing tests, browser if available. Preserve unrelated changes.

## Delivery
- Reused shared lead page table and drawer through partnerId; retained scoped partner APIs, read-only detail and submission owner snapshot; removed duplicate partner table/detail state. Added scoped documentation.
- Typecheck passed; existing leadManagement tests 26/26 passed; scoped diff check passed. Browser unverified: no browser connector exposed and playwright cannot be resolved from workspace. Desktop/mobile rendering and live account flow remain unverified. No service/dependency changes. Ownership released.

## Registration update: quick filters
- User confirmed backend quick-filter support. Same local environment/worktree/owner. Ownership adds managementApi.ts, PartnerManagementController.java, LeadManagementServiceImpl.java, LeadMapper.java and focused lead tests. Reacquire prior page/document ownership. Non-goals: grants, schema, services restart. Verification: focused tests, compile/typecheck; browser where available.

## Quick-filter delivery
- Partner-scoped profile endpoint reuses published management filter configuration behind existing partner permissions. Shared page forwards quick-filter selections; service resolves shared configuration; mapper intersects filters with partner ID and handles matchNone. Existing no-audience callers retain their query behavior. Vue admin caller inspected; no parameter changes required.
- Maven reactor focused test run passed: LeadMapperSqlTest 15 and LeadInboxFilterConfigServiceImplTest 15. Frontend typecheck and leadManagement tests 26 passed; diff check passed. First test fixture omitted valuesByField; corrected and rerun successfully. Backend compiled during test run. Browser/live endpoint and account acceptance remain unverified (browser tooling unavailable; running backend not restarted). No database or grants changed. Ownership released.
