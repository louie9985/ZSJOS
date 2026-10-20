# 报名履约学员信息收集表

- Environment: local; ZSJOS_AGENT_ENV unset, /etc/zsjos/agent-environment absent.
- Workstream: local-registration-student-info-20261016; owner Codex /root; branch main; worktree D:/ZSJ-OS; base d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2; integration None.
- Goal: implement approved per-registration single-submit collection, configurable 30 fields/category rules, image-only materials, generated-and-sent fulfillment gate, H5 fold UI.
- Non-goals: SMS, master-data overwrites, BPM, service lifecycle operations, role grants, Git publication.
- Ownership: studentinfo service/DAL/VO/controllers/tests; registration collection controller and RegistrationServiceImpl/RegistrationCaseRespVO; LeadManagement collection entry removal and student service collection projections; Workbench studentInfo services/components and collection usages in LeadDetail/StudentDetail/RegistrationPages; Admin studentInfo config and registration components/API; H5 studentInfo page/API/components; collection SQL/migration/verifier and relevant schema artifacts; docs/api/student-information-collection.md, docs/api/registration-fulfillment-api.md and affected architecture/operations docs; this record.
- Dependencies: existing Infra FileApi, System dictionary/area/category APIs and configured permissions. No new dependencies.
- Preserve existing dirty edits, especially RegistrationPages.tsx, StudentServiceReadService.java, MyStudentServiceImpl.java and docs/architecture/data-and-permission-flow.md; previous pending-class workstream delivered and released ownership.
- Verification: focused Java tests/compile, both internal frontend type/build checks, H5 build and browser 320/390/768/1440; controlled MySQL initial/replay/partial/failure/ledger and UTF-8 checks, local additive application after backup.
- Preflight: tenant 1 local database has zero collection forms, answers and configurations; V183/V322 applied. Existing V183 remains immutable; use additive upgrade V323. No data deletion authorized or needed.

## Ownership coordination

- RegistrationPages.tsx MyStudentsPage contextHeader and overviewContent/sidebarAfterStatus JSX are owned by local-student-service-sidebar-20261016. This workstream pauses all writes to that file until their delivery/release; other collection files, including StudentDetail.tsx, remain owned here. Preserve their StudentServiceCard and slot integration when adding the collection entry.
- Latest user decision: JPG/JPEG and PNG only; no WebP dependency or acceptance.

## Delivery — 2026-10-16

- Implemented registration-case collection ownership, immutable field/category snapshots, 30 configurable fields, product-category descendant matching, one successful submission, token rotation/revocation, versioned/idempotent send confirmation and independent fulfillment gate. Normal completion does not invalidate pending collection; ended/cancelled tasks and terminated/superseded orders do. No master-data overwrite or BPM introduced. Old Lead generation/tab entry removed and service projections use registrationCaseId.
- Admin configuration supports groups, dictionary bindings, image limits, category roots and actual-product preview. Workbench and Admin both expose generation/QR/link, sent checkbox, data/sensitive/image reads and export. H5 uses existing theme/Vant with grouped fold UI and desktop/mobile layout; JPG/JPEG/PNG only, identity front/back one each, other materials multiple images, progress/retry/delete/preview and submission confirmation.
- Images are AES-GCM ciphertext in Infra, exposed only through token/object-authorized reads; type/decoder/size/count/ownership checks, upload rate limits, rollback compensation and scoped expired-unsubmitted cleanup handler included. Cleanup schedule is not activated automatically.
- Parallel ownership: local-student-service-sidebar-20261016 delivered and released RegistrationPages.tsx before collection integration. Its contextHeader removal, sidebarAfterStatus and StudentServiceCard integration remain preserved. This workstream added only collection wiring afterward. Existing pending-class and other dirty changes preserved.
- PASS isolated javac compilation against current affected sources, including prior dirty dependencies. JUnit collection/config/controller/permission/encryption/image/cleanup and registration service/early-delivery/item/summary regression: 79/79. Evidence in frontend/workbench/tmp/registration-student-info/java. This is focused compile/test evidence, not a full server startup or authenticated runtime.
- PASS Workbench final typecheck and production build; H5 type/build and identity validation 2/2; Admin production build and scoped ESLint. Admin full ts:check remains blocked only by pre-existing duplicate fields in src/api/zsjos/withdrawal/index.ts (12 diagnostics). Existing large-bundle/Browserslist warnings retained.
- PASS real-component Chromium: H5 1440/768/390/320 widths, all fields, conditional required, multiple images, identity slots, retry/delete, submission and expired state; Workbench/Admin 1440/390 generation/QR, sent-only completion while student remains unsubmitted, regeneration clears sent, denied operations. Admin configuration and product preview checked. Fixtures use synthetic APIs and do not establish authenticated target acceptance. Screenshots in frontend/workbench/tmp/registration-student-info.
- PASS V323 controlled MySQL first/replay, partial-column recovery, prerequisite/legacy/type conflicts, ledger-write failure/recovery, UTF-8 HEX and current schema columns/indexes. Historical V183/bootstrap preserved; current target schema updated. No role-menu grants modified.
- Local ruoyi-vue-pro: V323 applied after scoped backup backups/mysql/registration-student-info-20261016171347013100.json. Replay and comparison PASS; read-only verifier reports tables 5/5, permissions 11/11, duplicate permissions 0, case unique-index columns 3/3 and both version ledgers 1/1. Forms/config remain empty.
- Remaining activation: publish new configuration with administrator-maintained dictionaries and actual education category IDs; assign required permissions via role management; confirm reachable HTTPS H5 base and encryption key, deploy matching backend/three frontends and perform real-account/storage/concurrency acceptance. No service restart/deployment, actual grants, real records or Git publication performed. Full historical bootstrap chain and live concurrent request stress were not executed; scoped migration recovery and transaction/unit tests do not substitute for those checks.
- Ownership released on this delivery.

## Registration update — 2026-10-18 dictionary activation

- Same owner/worktree/branch/base as initial registration; environment local (environment variable unset, marker absent). Reopened scope: V324 dedicated collection dictionary migration, StudentInfoFields presets/tests, scoped SQL verifier, collection documentation and this record. No role changes, remote writes, service restart or template publication.
- User authorized creating/synchronizing collection business dictionaries. V323 applied locally; preserve it and add V324. Dedicated dictionaries: ethnicity (56 ethnic groups), retraining (yes/no), education level (eight choices), registration level (four choices); sex reuses system_user_sex. Preserve administrator labels/status/custom entries and stored template snapshots.
- Verify initial/replay/partial/failure/UTF-8/ledgers in retained controlled databases, back up then apply local additive SQL; focused field tests/compile and diff. Workbench proxies remote backend: local sync does not activate remote.

## Delivery — 2026-10-18 dictionaries

- Added V324__student_information_dictionaries.sql after applied V323. Four dedicated types and 70 entries: ethnicity 56, retraining 2, current education 8, registration level 4. Automatic IDs; missing-only inserts retain administrator labels/status/deletions and custom choices. Does not modify role assignments, existing template fields or publication state.
- StudentInfoFields presets bind the four dictionary types; gender keeps system_user_sex. Added preset binding test and controlled dictionary SQL verifier. Updated API and migration operations docs.
- PASS controlled MySQL initial/replay/count/HEX, partial seed recovery preserving modified labels and disabled choices, missing prerequisite rejection, ledger-write failure rollback and recovery. Applied to local ruoyi-vue-pro after backup backups/mysql/student-info-dictionaries-20261018104817750097.json; replay passed and V324 appears in both ledgers.
- PASS isolated current-source javac compile and focused collection/registration regression 80/80; scoped diff checks. Existing dirty work preserved. No dependencies, service operations, Git publication or remote database writes.
- Remote Workbench target 192.168.2.17 is not synchronized by this local operation. Existing table configuration remains empty/unpublished; choose actual education category and publish configuration through Admin after matching backend deployment. Dictionary cache/live target acceptance not verified. Ownership released.

## Registration update — 2026-10-18 visual polish

- Reopened same local/main workstream for approved H5 dictionary popup and branded QR share card. Ownership: H5 collection picker/page/browser fixture; Workbench and Admin collection QR card components, local export helpers and supplied logo assets; link modal/Admin collection integration; collection docs and this record. No API/DB/dependencies/publication changes.
- Reuse Vant Popup/Picker for short choices and searchable list for long dictionaries. QR renders exact supplied link with existing QR libraries; supplied Logo above title, readable quiet zone, PNG export through native canvas. Verify browser desktop/mobile and QR export, H5/build/type checks; preserve concurrent changes.

## Delivery — 2026-10-18 visual polish

- Replaced H5 native dictionary selects with CollectionSelect: Vant Picker for short lists, searchable list for >10 options, confirm/cancel, selected labels and disabled derived gender; authoritative dictionary API unchanged.
- Added supplied Logo assets and branded QR cards in Workbench/Admin with exact approved title, scan guidance and 800x1080 PNG export. Link/time/status/regenerate/revoke controls remain outside share card. No dependency/API/database changes.
- PASS H5 production build and real-component regression at 1440/768/390/320, picker confirm/cancel and searchable choice/empty state; Workbench typecheck and build; scoped Admin ESLint. Workbench/Admin browser verified logo/title/mobile fit and image download. OpenCV decoded both exported PNGs to the exact fixture link. Screenshots/exports: frontend/workbench/tmp/registration-student-info/qr-export-workbench.png, qr-export-admin.png, picker-mobile.png, qr-modal-desktop.png, qr-modal-mobile.png and qr-admin-mobile.png.
- Kept existing concurrent changes. Browser fixtures are synthetic, not authenticated deployment acceptance. No backend restart, deployment or Git publication performed. Ownership released on delivery.
- PASS Admin production build after visual changes; all three production builds passed. Existing bundle-size/Browserslist warnings only.

## Registration update — 2026-10-18 unified collection links

- Reopen existing local/main workstream; owner Codex. Base d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2, worktree D:/ZSJ-OS, integration None; other registration metadata unchanged.
- Scope/ownership: Workbench StudentInfoLinkModal.tsx; Admin RegistrationStudentInfo.vue; Admin ResourceLinkInput README; collection API documentation and this record. Reuse existing React ResourceLink and Vue ResourceLinkInput as explicitly requested. Preserve QR card and management; no API, database, dependency or deployment changes.
- Verification: scoped lint/type checks and both real-component browser fixtures at desktop/mobile; complete fragment-token href/copy and QR download. Existing concurrent edits preserved.

## Delivery — 2026-10-18 unified collection links

- Workbench replaces raw URL and bespoke copy button with existing ResourceLink resource card. Admin uses existing readonly ResourceLinkInput with append copy button and full-width responsive layout. Updated component usage and collection API documentation. QR card and lifecycle commands preserved.
- PASS Workbench typecheck, 46 existing ResourceLink/ResourceLinkInput tests, scoped Admin ESLint, both frontend production builds and scoped diff whitespace check. Existing bundle-size/Browserslist warnings only.
- PASS real-component browser fixtures in both clients at 1440/390/320px: complete fragment-token href and clipboard, external new-tab attributes, component fits viewport, Admin readonly, QR PNG download. Fixed test-script console encoding with ASCII Unicode escapes; no product encoding change. Fixtures are synthetic, not authenticated runtime acceptance.
- No database, API, dependencies, service restart/deployment or Git publication changes. Existing concurrent work preserved; ownership released.

## Registration update — 2026-10-18 collection H5 origin

- Reopen same local/main workstream, owner Codex; fixed metadata unchanged. Ownership: backend/yudao-server/src/main/resources/application-local.yaml, collection API documentation and this record. User requested LAN-reachable collection origin instead of loopback.
- Set collection-specific local public-base-url to current WLAN IPv4 192.168.2.17:10086 with dedicated environment override and shared H5 override. Do not change unrelated public surfaces or service lifecycle. Verify parsed configuration, existing URL validation and read-only LAN H5 request; no restart/deployment.

## Delivery — 2026-10-18 collection H5 origin

- Added local collection-specific origin http://192.168.2.17:10086, overridable by ZSJOS_STUDENT_INFO_PUBLIC_BASE_URL then ZSJOS_PUBLIC_H5_BASE_URL. Documented configuration location, precedence and backend reload requirement.
- PASS multi-document YAML parse, exact configuration expression, existing baseUrl validation inspection, LAN H5 route HTTP 200 and scoped diff check. WLAN IP and Workbench target verified live. No backend reload/restart; actual generated-link runtime acceptance remains pending configuration reload. No database/Git publication changes; ownership released.

## Registration update — 2026-10-18 student archive

- Reopen same local/main workstream; Codex owns archive backend controller/service/VO/permission provider/revision DO/mapper/tests, collection image integration, StudentContact tab projection, Workbench archive service/panel/StudentDetail, Admin archive API/panel/my-students, V327 migration and controlled verification, related documentation and this record. Fixed metadata unchanged; integration None.
- Archive binds Person and exact visible service/registration/order item. Separate archive read/update permissions and exact-service visibility. Original submissions immutable; append encrypted revision snapshots with optimistic revision and audit reason/operator/time. Preserve sensitive/export gates and dictionary/area snapshots. Include image maintenance with existing encrypted Infra validation. No real grants, target DB writes, service operations or Git publication.
- Verify focused backend compilation/tests, controlled MySQL initial/replay/partial/failure/UTF-8/ledgers, both frontend checks/builds and browser read/edit/denied/mobile fixtures. Preserve concurrent work.
- Ownership refinement: include StudentContactServiceImplTest for changed archive-tab permission projection, task browser fixtures and task-only verification outputs. Current staged schema/core.sql belongs to concurrent work and is preserved; V327 is the authoritative upgrade artifact.

## Delivery — 2026-10-18 student archive

- Added StudentArchiveController/Service/VO/revision mapper/DO and exact-service permission provider. Independent configured read/update permissions; cumulative sensitive/export gates; Person/order-item binding checked. Original submitted answers/images unchanged; encrypted append revisions retain unchanged dictionary/area snapshots and historical age. Form locking, optimistic revision and command fingerprint enforce single revision/idempotency; picture validation and archive_only separation preserve original projections.
- Workbench StudentDetail now shows 学员档案 via server-authorized tab; Admin my-students exposes same tab using returned permission state. Both render view/maintenance/history/images/export with real business/dictionary/area APIs. Maintainer/time/reason recorded; no master-data rewrite or approval.
- PASS isolated current-source Java compilation and 44 tests (9 archive, 35 contact regression); scoped Admin ESLint, Workbench typecheck/build and Admin build. Admin full typecheck has only 12 pre-existing duplicate withdrawal API identifiers; archive types have no new errors.
- PASS controlled MySQL V327 initial/replay/partial recovery/prerequisite and ledger-write failures, HEX/permissions/ledgers. Retained student_archive_20261018155005421792_* test databases. Docker verification source differs from live application MySQL; seeded V324 prerequisites only in isolated fixtures. Application DB V327 NOT applied; permission metadata NOT assigned to real roles. Staged concurrent schema/core.sql left unchanged; source upgrade migration is V327.
- PASS synthetic actual-component browser flows in both clients at 1440/390: view, edit/save revision, historical label retention, readonly control absence, mobile fit. Not authenticated runtime acceptance. No backend restart/deployment, real permission changes, Git commit/push.
- Known storage limitation: cancelled/unreferenced internal image uploads remain encrypted until a separately scoped safe cleanup; historical images never physically deleted by maintenance. Ownership released.
