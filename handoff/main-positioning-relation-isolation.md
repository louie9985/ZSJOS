# main-positioning-relation-isolation
- Scope: PositioningInterviewService, PositioningCardService, their two service tests, V203 development correction, schema/core.sql, 00-bootstrap-schema.sql, tools/test_positioning_relation_isolation.py, docs/architecture/data-and-permission-flow.md, this handoff. V218 unchanged; no V219.
- Goal: Exact service-relation attachment/import isolation and independent interview drafts.
- Base/HEAD: f338087e9aaa8b8d881cd0aee80427d8773ab284; target main; dependencies: existing Tenant/File/MyBatis/SQL tools; no new dependency.
- Order: ownership validation -> V203 repair and tests -> isolated MySQL and reactor tests.
- Verification: cross-relation rejection before URL signing/import, legacy bound compatibility, same-director separate drafts, repeated SQL correction. Shared DB not modified.
- Non-goals: tasks, notifications, materials, frontend.
- Owner: /root/collab_data_impl; branch main; worktree D:\ZSJ-OS.

## Delivery Entry - 2026-09-13 17:07 +08:00
- Goal: 定位访谈附件和定位卡创建/导入严格绑定 serviceRelationId，修正 draft 唯一键。
- Changes: PositioningInterviewService uses relation-scoped upload directory; bound attachments resolve ownership by interview relation; unknown temporary uploads are rejected; PositioningCardService requires exact target relation and source card/submission/account relation match, removes person-level relation findFirst and account-wide source expansion. V203 generated draft key now uses service_relation_id in V203, core schema and bootstrap baseline; no new V219 migration.
- Tests: Reactor focused suite `mvn -f backend/pom.xml -pl yudao-module-zsjos -am '-Dtest=PositioningInterviewServiceTest,PositioningCardServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` exited 0: 42 tests, 0 failures/errors (session 59961). Initial combined run had transient workspace interference but no final failure.
- SQL/shared DB: no shared database writes; V203 baseline correction and schema source updated, but controlled production-like database replay and development DB synchronization remain unverified.
- Remaining: root must review source submission relation fields and existing data compatibility; full cross-group tests/browser validation pending. Do not claim complete positioning workflow closure.

## Delivery Correction - 2026-09-13 17:20 +08:00

- Branch/worktree: main / D:\ZSJ-OS; HEAD f338087e9aaa8b8d881cd0aee80427d8773ab284; owner /root/collab_data_impl.
- User goal: 修复定位访谈附件、关系草稿唯一性、定位卡创建与导入串组问题。
- Decisions: exact source relation required; no relation inference; target account/relation tenant checked against current tenant; query-all read never substitutes for director authorization to import. New temporary paths include relation ID, old bound attachment relation comes from interview, old unknown temporary attachments fail closed. V203 remains the development baseline correction, not a new V219 upgrade.
- Changed files: PositioningInterviewService.java; PositioningCardService.java; their service tests; V203__positioning_interview_template.sql; schema/core.sql; 00-bootstrap-schema.sql; tools/test_positioning_relation_isolation.py; docs/architecture/data-and-permission-flow.md; this record.
- Verification: final reactor session 96782 exited 0: 46 tests (20 card + 26 interview), 0 failures/errors. Eight new Java tests cover attachment download signing denial, bound-history compatibility, unknown/cross-relation temporary save/remove rejection, missing target relation, forged source combinations, query-all-only import denial, explicit cross-tenant target rejection. Existing valid import tests pass. Earlier 3 failures came from test-global tenant state switching submitReview into its locking branch; corrected test fixtures, not business policy.
- SQL evidence: python script/sql/mysql/tools/test_positioning_relation_isolation.py session 40349 exited 0. Real isolated MySQL bootstrap plus full V203, original student-unique expression simulated then repaired, same-director/student independent relation drafts, same-relation duplicate rejection, cross-tenant uniqueness, repeatability, fresh/repaired table equality and Chinese HEX all passed; container cleaned up. Initial temporary MySQL startup socket race was fixed in this test by waiting for final @@port=3306.
- Integration impact: No dependencies, shared database writes, real permissions, messages, branch/commit/push, task/notification/material/frontend changes.
- Remaining: Shared development database V203 correction is not applied or diffed; needs scoped authorized synchronization. Full latest-migration replay and browser/API validation remain unverified. This is relation isolation of specified paths, not the complete group positioning redesign or task closure. Attached historical files omitted from a later save become unbound; legacy directory uploads then require re-upload.

## Delivery 2026-09-13 20:45 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: continue real validation and repair director workflow.
- Decision: preserve created material ID when approval submission fails, so retry updates existing draft instead of duplicating it.
- Result: ViralAccountMaterialForm now tracks persistedMaterialId and fetches current version before update.
- Changed files: frontend/workbench/src/components/ViralAccountMaterialForm.tsx
- Verification: pending focused workbench typecheck/build.
- Integration impact: frontend material creation/approval retry only; no dependency changes.
- Remaining: run typecheck and browser verification of student overview.


## Delivery 2026-09-13 20:52 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: unify student interview and account positioning-card object model.
- Decision: interview is student-level; positioning card is account-level, created from student overview, with unbound drafts auto-bound after account creation.
- Result: synchronized design specification with the approved two-level model.
- Changed files: docs/superpowers/specs/2026-09-11-director-positioning-card-design.md
- Verification: documentation consistency reviewed against implemented service and frontend behavior.
- Integration impact: documentation only.
- Remaining: browser end-to-end student detail verification.


## Delivery 2026-09-13 21:05 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: allow positioning-card creation from student overview before account creation.
- Result: CREATE_POSITIONING_CARD action now opens without requiring selected account; account selector is optional and import action guards empty account.
- Changed files: frontend/workbench/src/pages/MediaStudentsPage.tsx
- Verification: npm run typecheck passed.
- Integration impact: enables student-level draft entry while preserving account-specific selection when available.
- Remaining: browser click-level validation.


## Delivery 2026-09-13 21:18 Beijing
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: make no-account positioning-card draft saveable from student overview.
- Result: removed frontend account requirement from draftSaveTask; requests now send optional accountId and preserve student/service/template context.
- Changed files: frontend/workbench/src/pages/MediaStudentsPage.tsx
- Verification: npm run typecheck passed.
- Integration impact: aligns autosave/manual save with nullable backend accountId; submit-review remains backend-gated on account binding.
- Remaining: browser click-level validation.

