# Examination material export

## Registration — 2026-11-10 Beijing time
- Environment local, variable and /etc environment file absent. Owner Codex /root.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Integration None.
- Goal: task-persisted export layout and ordered per-material naming, preview and authenticated ZIP download, current archive/raw submission modes, query permission for exam exports.
- Scope: new ExamCollectionExport VO/controller/service/naming and tests; task DO export settings field; permission provider and summary controller/service; Workbench ExamCollectionPage, StudentSummaryPage, services/examCollection, new export drawer; V368 export settings migration and applicable manifest; docs/api/exam-material-collection.md; this record; private verification artifacts.
- Non-goals: unrelated existing changes, deployment, role grants, branch/commit/push operations.
- Dependencies: existing Infra FileApi, archive adapter, encrypted attachment services, System query permission, JDK ZIP, existing frontend components. No new dependencies.
- Verification: focused Java export and permission tests/compile, frontend typecheck, desktop/mobile browser fixture interactions, scoped diff checks, controlled MySQL first/repeat/partial/failure/recovery and local targeted migration with ledger/UTF-8 checks.
- Existing overlapping files contain earlier uncommitted work. Preserve current contents and apply scoped additions only. No other active writer identified for these changes.

## Registration update
- Ownership adds script/sql/mysql/tools/test_exam_collection_export.py for required isolated migration regression evidence.

## Verification note
- Global zsjos-db check is blocked by a pre-existing desired/baseline difference at exam task content (schema/core.sql contains content, 00-bootstrap-schema.sql lacks it). Both files had prior changes and are outside this workstream ownership. V368 upgrade verification runs independently against V367 prerequisites.

## Delivery — 2026-11-10 11:00:59 Beijing time
- Branch/worktree/base unchanged from registration, HEAD 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Owner Codex /root. Integration None.
- Implemented task-persisted export preferences with independent optimistic revision, ordered global/per-material filename tokens and literal text, class/material/student/flat layouts, current archive versus separate original submissions, server-authorized scope filters, deterministic preview fingerprint, bounded ZIP generation through existing attachment binding and decryption APIs, no-store HTTP responses and temporary ZIP cleanup. Query permission now suffices for examination ZIP and summary Excel. Existing unrelated modifications preserved.
- Changed files owned by this task: new controller/admin/examcollection/ExamCollectionExportController.java and vo/ExamCollectionExportVO.java; new service/examcollection/ExamCollectionExportService.java and ExamCollectionExportNaming.java; existing ExamCollectionTaskDO.java (exportSettingsJson only), ExamCollectionPermissionProvider.java (export inherits query), ExamCollectionSummaryController.java (query-only export); new ExamCollectionExportTest.java and existing ExamCollectionPermissionTest.java assertions; Workbench new components/examCollection/ExamExportDrawer.tsx, pages/ExamCollectionPage.tsx (entry only), pages/StudentSummaryPage.tsx (exam gate only), services/examCollection.ts (typed export client only); V368 migration, test_exam_collection_export.py, docs/api/exam-material-collection.md and this record.
- Passed final reactor Maven focused tests and prerequisite compile: ExamCollectionExportTest 8, ExamCollectionPermissionTest 2, ExamCollectionSummaryTest 3, total 13 with zero failures. Covers safe/ordered names, duplicate paths, raw unmatched submissions and bound byte reads, current archive ZIP byte fidelity, old-task defaults/save conflict, stale preview rejection, size limits, foreign selection, denied task, material override and empty scope. Workbench typecheck passed after final UI changes. Scoped diff/UTF-8 checks passed.
- MySQL 8.4.11: isolated initial/repeat/missing prerequisite/partial DDL/ledger failure/recovery/incompatible column/Chinese HEX checks passed. Retained fixtures verify_exam_export_ff44b893, verify_exam_export_2e0c5098, verify_exam_export_58ef2eb4. Inspected local ruoyi-vue-pro had V367 in both ledgers and two exam tasks, then applied V368 twice. Column structure matches controlled database and both V368 markers exist; task count/original-content checksums preserved. No business values or grants changed.
- Ego-browser actual rendered fixture at 1440 desktop and 390 mobile passed initial error/retry, save, preview invalidation, download fingerprint payload, filename-token reordering and no document horizontal overflow. Synthetic HTTP only. Screenshots C:/Users/EDY/AppData/Local/Temp/exam-export-desktop.png and exam-export-mobile.png were visually inspected. Later screenshot attempt after preview auto-scroll changes was blocked by minimized browser window; interactions/token ordering still passed. Temporary fixture under frontend/workbench/tmp/exam-material-export is verification-only.
- Decisions: bounded synchronous generation (2000 files / 512 MB) and 10000-row task scan guard. Existing generic export pipeline materializes byte[] and returns Infra signed links; extending it for large private asynchronous ZIP jobs remains future work. Raw exports always preserve one folder per formal submission, with selected layout inside. Admin continues filtering unsupported zsjos/examCollection/index, verified in adminRouteFilter.ts.
- Remaining acceptance: real authenticated HTTP/full storage flow requires updated running backend; this task performed source compilation, focused service tests, MySQL execution and browser synthetic fixtures. Existing shared backend was not restarted. Global zsjos-db check remains blocked by the pre-existing content-column baseline/desired mismatch described above. No full fresh-chain verification claimed. No commits, branch operations, deployments or role changes performed.
- Ownership released.

## Registration update — 2026-11-10 export UI simplification
- Same local environment, branch, worktree and HEAD as delivery above. Owner Codex /root resumes ownership of ExamExportDrawer.tsx, directly affected API documentation and this record, plus private browser fixtures.
- Goal from user correction: remove redundant permission banner, simplify filename controls and explain folder outcomes using concrete examples. Preserve saved naming rules and existing API operations.
- Verification: Workbench typecheck, desktop/mobile browser fixture, naming order/custom text/save-preview request checks and scoped diff. Integration None.

## Delivery — 2026-11-10 11:18:49 Beijing time — export UI simplification
- Same environment/branch/worktree/HEAD as registration above. Owner Codex /root. Integration None.
- User correction addressed: removed redundant permission/limit banner; replaced per-token select/arrow/delete rows with compact ordered removable tags and concrete filename example; naming choices carry examples; folder radio choices use plain wording and directory examples. Custom text and per-material overrides are collapsed by default. Low-frequency filters now default collapsed with a configured-state indicator.
- Changed ExamExportDrawer.tsx, docs/api/exam-material-collection.md and this record. Existing settings arrays and endpoint requests preserved. No backend/schema/dependency changes.
- Verification: npm run typecheck passed; browser synthetic HTTP fixture at 1440 desktop and 390 mobile exercised folder change, custom text addition, saved naming/layout payload, preview failure/retry, absence of permission banner and no page horizontal overflow. Desktop screenshot visually inspected. Screenshots C:/Users/EDY/AppData/Local/Temp/exam-export-simple-desktop.png and exam-export-simple-mobile.png. Scoped diff checks passed. Real-account acceptance remains outside fixture evidence.
- Initial typecheck command used repository root and failed for missing package.json; rerun from frontend/workbench passed. Initial browser window-target/stale-ref attempts corrected using current page snapshot and semantic actions.
- Ownership released.

### Verification clarification
- Desktop visual inspection passed. The 390px emulated DOM check reported no horizontal overflow, but subsequent resized native screenshots had incomplete repaint and were not accepted as mobile visual evidence. Browser interaction and saved-request assertions passed. Full narrow-width visual acceptance remains unverified.

## Registration update — 2026-11-10 layout correction
- Local environment, same main worktree and owner. Scope resumes ExamExportDrawer.tsx, new scoped exam-export.css, API documentation and this record. Goal is consistent alignment and spacing. Verification typecheck and browser desktop/narrow layout.

## Delivery — 2026-11-10 11:27:33 Beijing time — spacing correction
- Same local main worktree and HEAD. Changed ExamExportDrawer.tsx, new styles/pages/exam-export.css, API note and this record. Scoped CSS uses existing design tokens, three section groups, consistent label/field spacing, aligned disclosure text with right-hand arrows, aligned expanded bodies and inline class switch.
- Typecheck passed. Browser fixture with production tokens visually inspected on desktop; measured all seven default labels/disclosures at the same left coordinate. Expanded Ant Design 6 body verified 16px gap and 12px top/zero horizontal padding. Narrow 390px drawer fixture checked for overflow. Browser evidence is synthetic HTTP. No backend behavior changes. Ownership released.

## Registration update — full collection preview
- Same local main context. Scope ExamCollectionBodyPreview.tsx, ExamCollectionDialogs.tsx, preview CSS, API documentation and this record. Full field display from selected API definitions, desktop/mobile rendering, no submission in preview. Verify typecheck and browser fixture.

## Delivery — 2026-11-10 11:35:37 Beijing time — full preview
- Same local main context. Updated ExamCollectionBodyPreview.tsx and creation caller to consume full selected field definitions, added isolated exam-form-preview.css, updated API note. Preview shows basic inputs, required markers, material controls, identity sides, configured upload constraints/notes and disabled submit. Existing rich text rendering/sanitization retained; escaped attribute delimiters too.
- Typecheck passed. Browser fixture verified three readonly fixed fields, both identity slots, JPG/PNG 10MB versus PDF/DOCX 20MB and configured counts, submit and mobile switch with 375px iframe. Isolated sandbox preview has no write requests. Presentation follows public H5 group/grid conventions with a dedicated static preview stylesheet, not the live Vue runtime. Real-account acceptance not claimed. Ownership released.

## Registration update — actual public styles
- Same local main context. Scope expands to extract existing scoped CSS from H5 exam page and CollectionImages into shared CSS sources, preview imports actual H5 base/theme/Vant styles. Shared files are plain styles, no new package/dependency. Verification both frontend builds and browser preview.

## Delivery — 2026-11-10 11:39:27 Beijing time — public style reuse
- Same local main context. Extracted actual H5 exam-page and attachment scoped CSS into shared files and retained scoped src imports on both Vue consumers. Preview uses matching class names and actual H5 base/default coral/theme overrides/Vant CSS. Removed this workstream own imitation stylesheet. No package dependency additions. Workbench build now reads existing H5 Vant CSS, requiring H5 dependencies installed.
- Workbench and H5 production builds passed. Workbench reports existing large-chunk/mixed-import warnings. Browser static fixture confirms full form and style rendering, screenshot exam-preview-public-style-final.png inspected. Scoped diff checks passed. Default coral theme used deliberately; per-user H5 themes are not propagated cross-origin. Real-account acceptance remains unverified. Ownership released.
