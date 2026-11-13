# Version update notifications

## Registration

- Environment local, ZSJOS_AGENT_ENV and environment file absent. Owner Codex /root.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Integration None.
- Goal version-update publication using existing notice audience tree, persistent explicit acknowledgement, global popup with minimize animation and header history.
- Scope system notice controller/service/VO/DO/mappers and focused tests, Workbench notice editor/services/new version-update component/main integration, Admin notice protocol compatibility, V369 migration, directly affected API docs and this record. Temporary isolated verification artifacts are task-owned.
- Non-goals role grants, unrelated notice changes, dependencies, deployment and Git publication.
- Dependencies existing System notice permissions, frozen recipients, Infra files, WebSocket hints, Workbench overlay coordination and Ant Design.
- Verification focused backend tests and compilation, frontend typecheck/build, controlled MySQL initial/repeated/partial/failure execution, browser interaction at desktop/mobile widths, scoped diff review.

## Scope correction 2026-11-11

- User requires version updates as an independent feature. Withdrawn all task additions to existing notice APIs, tables, frontend editors, statistics, and header integration. Pre-existing notice changes preserved. No migration or database change had been performed.
- Revised scope requires independent ZSJOS version-update controller/service/DAL/VO, version-update tables and menu/button permissions, independent publication UI and recipient selection using System public APIs, plus Workbench popup/history. Common UI primitives may be reused.
- Independent implementation and its verification remain pending.

## Independent implementation registration

- Environment local, branch main, worktree D:/ZSJ-OS, base unchanged, owner /root. Integration None.
- Ownership: new versionupdate packages in ZSJOS controller/service/DAL/tests; new frontend/workbench/src/services/versionUpdates.ts, components/versionUpdates/*, pages/VersionUpdatesPage.tsx; targeted main.tsx, constants.ts and layouts/RouteHost.tsx integration; V369__version_updates.sql and its scoped verification script; docs/api/version-updates.md and directly affected navigation/architecture records; this handoff.
- Independent menu permissions zsjos:version-update:query/create/update/publish/offline/read/acknowledge. Management applies explicit feature scope to this feature's records; recipient endpoints enforce the frozen user relationship. Existing notice files are outside write ownership.
- Verify using focused backend tests, compile, controlled MySQL and existing frontend checks plus actual browser fixtures. No new dependencies.

## Implementation verification progress

- Independent endpoints, tables, permissions, management page, header history and acknowledgement animation implemented. Existing notice domain has no task changes.
- V369 applied to local Docker yudao-mysql / ruoyi-vue-pro after exact prerequisite inspection. Scoped isolated MySQL tests pass, including role-grant fingerprint preservation. No business updates were published.
- Initial full frontend build and 4 backend focused tests passed. Browser fixtures found and corrected a stale-detail response reopening the acknowledged popup and a BusinessTable action-slot integration issue. Final checks pending.

- Ownership extension for mapped-table completeness: append only version-update table definitions to script/sql/mysql/schema/core.sql and 00-bootstrap-schema.sql. Existing exam-collection content mismatch belongs to another workstream and is preserved. Verification adds an isolated full fresh baseline/migration attempt plus scoped schema comparison.

## Delivery — 2026-11-11

- Independent ZSJOS version-update domain delivered locally with two tenant-owned tables, seven configured permissions, draft/edit/publish/offline management, authoritative department/person selection, immutable publication roster, explicit first acknowledgement, count/person statistics, header history, realtime/focus/reconnect recovery, coordinated popup queue and shrink-to-header animation. Reduced motion uses a fade. Inline images retain validated file identities and receive fresh signed URLs on read.
- Existing notice APIs, data objects, mappers and services have no task diff. Pre-existing notice layout edits remain owned by their original workstreams.
- Passed backend reactor compilation and 7 focused service/permission/file-reference tests; Workbench production build and 33 menu tests; Admin existing Workbench-component filter tests (9). Existing Vite chunk warnings remain.
- Browser fixtures used the actual React components at desktop 1440 and mobile 390 widths. Verified tree cascade excludes unqualified users, draft payload, publish confirmation, acknowledgement animation, persisted-state history projection, failed acknowledgement retry, multiple pending versions, read-only no-auto-popup, reduced-motion fade and loading-error entry. Screenshots inspected. Ego task space 83 closed after verification.
- Scoped MySQL 8.4 verification passed initial/repeat, prerequisite refusal, partial DDL recovery, ledger failure rollback/recovery, permission identity conflict, UTF-8 HEX, recipient uniqueness and first-acknowledgement preservation. Applied V369 to local Docker yudao-mysql / ruoyi-vue-pro with both ledgers, repeat and table-structure comparison, preserving role-grant fingerprint.
- Added the two new table definitions to desired schema and fresh baseline. A full isolated fresh chain frozen through V369 passed 390 migration/ledger integrity checks and new-table structure comparison with local. Existing seed core/V061 remains governed by its historical SQL provenance checks. An earlier moving-workspace run reached V369 but its final audit saw concurrent V370, so the successful repeat used a source snapshot and V369 boundary.
- General static `zsjos-db check` remains blocked by a pre-existing exam-collection content-column difference between desired core schema and bootstrap. This unrelated difference was preserved and isolated. Fresh execution through V369 itself passed.
- No running backend endpoint was available on local port 48080, so real-account HTTP, actual file-storage rendering and end-to-end WebSocket delivery remain unverified. Browser evidence is fixture-based. Deployment, service restart, real role grants, commit and push are outside this local delivery. Administrators configure the independent permissions through role management before operational use.
- API and navigation/ownership documentation synchronized. Ownership released.

## Image-preview follow-up registration

- Same local/main worktree and owner /root. Goal enable inline image preview in version-update popups/history/detail.
- Scope SafeRichText.tsx additive independent preview option with existing defaults preserved, VersionContent.tsx opt-in, feature API documentation and this record; isolated browser fixtures. Existing SafeRichText edits are preserved.
- Verify typecheck, existing rich-text tests if available, actual browser desktop/mobile image preview/close/acknowledgement flow.

## Image-preview follow-up delivery

- SafeRichText now exposes a previewImages option, retaining announcementTables as its default for existing callers. VersionContent enables previewImages independently for popup, history and management detail. Inline images support click/Enter/Space, zoom and multi-image navigation.
- Passed Workbench typecheck and 24 existing notice/attachment tests. Actual-component static browser fixture verified linked-image clicks stay in the app, zoom, image switching, keyboard opening, mobile preview, closing preview and subsequent acknowledgement. Mobile screenshot inspected. Ego space 91 and task-owned static preview server closed.
- Shared Vite service was unresponsive, so verification used a task-owned bundled fixture on loopback port 5190. Real-account acceptance and deployment remain unverified. Prior rich-text layout edits preserved. Ownership released.
