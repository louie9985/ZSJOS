# Identity address-code collection workstream

- Environment: local (environment variable and environment file absent).
- ID: identity-area-20261104; owner: Codex root.
- Branch: main; worktree: D:\ZSJ-OS; base: c9964acf71fdfec268c9e08c148308e9c81f1827.
- Goal: national historical identity address-code lookup and manual fallback, preserving existing collection lifecycle and submitted snapshots.
- Non-goals: order/cancellation changes, rewriting submitted archives, changing permissions, runtime restart/deployment.
- Ownership: new System identity-area API/service/DAL and tests; ZSJOS studentinfo fields/form/draft/reuse/archive and VOs/value DO tests (excluding StudentInfoTaskService and cancellation tests); H5 studentInfo page/API/validation/tests; directly affected Admin/Workbench archive adapters after inspecting subtree rules; new address-code dataset/import tools and migration/schema manifests; collection API and operations documentation; this handoff.
- Dependencies: reviewed source data and exact local database/ledger inspection; preserve concurrent order/cancellation work and shared documentation changes.
- Verification: data coverage/checksum and source validation; focused System/ZSJOS tests; frontend type/build/browser flows; controlled MySQL initial/repeat/partial-failure verification and scoped local comparison.
- Target branch/integration order: None.
- Status: registered; implementation pending.

## Delivery — 2026-11-04 (Asia/Shanghai)

- Execution context: local/main/D:\ZSJ-OS unchanged. HEAD advanced externally to f6712f0bebcb061664029f8e102e0ee634329406 during work; concurrent order/cancellation files were not edited by this workstream.
- Result: implemented System historical address-code API/DAL/service, ZSJOS token-scoped lookup and additive submission/draft input, source snapshots, historical/null-path archive reads and reuse, H5 automatic lookup/manual selection/manual historical text and draft restoration. Existing clients retain their original validation route. Existing order, task cancellation, permissions, uploads and submission confirmation were retained.
- Source: pinned areacodes e2c2b2e1fb4ce1fb76e879aa0b80e229adaf950c (CC0); 6,824 original records, 6,246 county records / 5,893 codes. Multiple historical labels yield manual fallback. No claim of complete police assignment coverage.
- Database: user confirmed both configured targets are local and either may be used. Applied V362 to configured backend target 192.168.2.17:3306/ruoyi-vue-pro (MySQL 8.4.11), after inspecting V361 ledgers. Added reference table and nullable snapshot column; 62 existing value rows retained and zero existing snapshots rewritten. Verified 6,246 rows, both example Chinese names via HEX, structure and module ledger; recorded executed file checksum. Verification database verify_identity_20261104163452 retained, no data deletion.
- Changed files: new System api/ip/IdentityAreaApi*, dto/IdentityAreaRespDTO, service/ip/IdentityAreaService, dal/ip IdentityAreaDO/Mapper and service test; ZSJOS StudentInfoVO, PublicStudentInfoFormController, StudentInfoFormValueDO, StudentIdentityAreaService/test, StudentInfoFormService, StudentArchiveService, StudentInfoReusePolicy; H5 studentInfo API/page and dedicated identity-area browser fixture/round scripts; SQL V362, desired/fresh schema, data/identity-area/*, identity_area_data.py, verify_identity_area.py; collection API/identity-area operations docs; this record. H5 build-info was already dirty and was updated by build; unrelated existing artifacts were preserved.
- Verification: 52 focused Java tests passed (4 System + 48 ZSJOS), 4 identity frontend tests passed, H5 production build passed, Workbench typecheck passed. Admin typecheck blocked by existing MES unused handleRefresh at src/views/mes/pro/task/edit/index.vue:86, outside scope. Static DB check passed. Controlled MySQL prerequisite failure, partial DDL failure/recovery and repeat succeeded. Complete fresh bootstrap + 383 pending migrations + ledger integrity passed, zero schema drift. Scoped target data/structure/Chinese HEX checks passed. git diff --check passed.
- Browser: ego-browser fixture using production H5 verified historical automatic lookup, unknown-code manual text, draft persistence/reload and existing confirmation + submit. These are isolated fixture checks, not real account/API acceptance. Screenshots could not be captured because Ego window remained minimized; visual desktop/mobile acceptance remains unverified. Ego reported an available update; no upgrade performed.
- Dependencies/integration: no npm/Maven dependencies added, no permission grants, no branch/commit/push, no application restart/deployment. Release requires paired backend + H5; database additive change is already applied. Backend HTTP/account and visual acceptance remain outstanding. Reference-data updates require reviewed source/version and migration compatibility handling.
- Status: local implementation and scoped database delivery complete; ownership released. Remaining release checks above are not claimed complete.

## Registration update — 2026-11-04 direct manual fallback
- Owner: Codex root; environment local; same worktree/branch. Reacquire H5 studentInfo page and collection API documentation ownership only, plus this record.
- User correction: remove selection/mode/retry buttons; failed recognition exposes a text input directly. Preserve backend compatibility and previously saved drafts. Automatic labels must wrap rather than truncate.
- Verification: H5 build, scoped diff and browser desktop/mobile automatic/manual/draft flows. No database or runtime deployment changes.

## Delivery — 2026-11-04 direct manual fallback (Asia/Shanghai)
- Same local/main worktree and HEAD f6712f0bebcb061664029f8e102e0ee634329406.
- Removed all region mode, selection and retry buttons from public H5. Failed lookup directly exposes a two-line text field. Successful historical label wraps. Existing selected-region drafts convert to text using authoritative area labels; backend compatibility unchanged.
- Files: frontend/h5/src/pages/studentInfo/index.vue, docs/api/student-information-collection.md, this handoff. Existing dirty build-info updated by normal build.
- Verified: H5 production build passed; fixture browser confirmed zero buttons/selects in region field, manual input persisted across reload, and automatic historical output. Inspected desktop and 390px mobile screenshots; truncation and narrow-selector issues resolved. Screenshot artifacts in C:/Users/EDY/AppData/Local/Temp/identity-area-preview-20261104/simple-*.png. No real registration submission or deployment.
- No dependencies, DB writes, service restart, commit or push. Ownership released.

## Registration update — 2026-11-04 plain region labels
- Same local/main context and HEAD. Owner Codex root reacquires H5 studentInfo page, StudentIdentityAreaService and its test, collection API docs and this record.
- User requests removing historical-division presentation. Display/persist plain region labels for new answers; keep source/status/version in metadata and leave submitted records untouched.
- Verification: focused backend test, H5 build, scoped text/diff checks. Mechanical presentation change, no layout or database changes.

## Delivery — 2026-11-04 plain region labels (Asia/Shanghai)
- Same local/main context/HEAD. Removed historical wording from H5 result and help text; new backend region label snapshots contain only the region name, with source/status/version retained separately. Existing submitted rows unchanged.
- Files: H5 studentInfo page, StudentIdentityAreaService and existing test expectation, collection API doc, this handoff. H5 generated build-info updated by build.
- Verification: StudentIdentityAreaServiceTest passed; H5 production build passed; scoped phrase search confirmed no historical suffix/help text in active page/service; diff check passed. No database writes, deployment, restart or dependency changes. Ownership released.
