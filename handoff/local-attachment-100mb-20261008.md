# Workstream attachment-100mb-20261008

## Delivery 2026-10-08 (Beijing)

- Environment local; branch main; base/HEAD unchanged at d2f836006ff07b5517bcd6d485d72df788bf3cb0. Owner /root; ownership released after delivery.
- Delivered registered 100 MiB business attachment limits, frontend checks/hints, new form defaults and upload timeouts. Preserved explicitly saved field limits and special image/import/media rules. Updated API/frontend documentation and docs/operations/attachment-upload-limits.md.
- Focused Maven suite passed 96 tests; final account/exam suite passed 40 tests (includes repeated coverage), BUILD SUCCESS. Workbench focused suite passed 24 tests; Workbench typecheck and H5 vue-tsc passed. Admin full typecheck blocked by 12 existing duplicate identifier diagnostics in untouched src/api/zsjos/withdrawal/index.ts.
- Chromium real-component fixtures passed on Admin and Workbench: 100 MB hint, ZIP exactly 104857600 bytes accepted, plus one byte rejected. File sizes and responses were simulated without real large-payload storage. Desktop/mobile screenshots inspected for changed hints. Admin fixture initially imported a different Vite module URL; using the actual loaded resource URL resolved the test issue.
- Admin timeout expression passed four cases: default, short, zero and long. Scoped UTF-8 reads and git diff --check passed; reviewed changes against pre-task content snapshot. Temporary verification servers stopped.
- Unverified: actual 100 MB transport/storage/proxy limits and deployment. No shared service restart, deployment, SQL, dependency changes, commit or push. Configured forms require normal editing/publication to change explicit limits; historical snapshots preserved.

- Registration: 2026-10-08 Beijing time; environment local; owner /root.
- Goal: raise general business attachment limits to 100 MiB (UI MB); preserve types, counts, permissions and user changes.
- Branch: main; worktree: D:/ZSJ-OS; base/HEAD: d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- Non-goals: avatars, inline rich-text images, import processing, existing larger media channels, stored form configuration, SQL, deployment, shared service changes.
- Dependencies: existing System/ZSJOS/Infra APIs only; target branch/integration order None.
- Verification: focused upload boundary tests, frontend static checks, backend focused tests/compile, browser inspection where available, scoped diff and UTF-8 checks.
- Ownership: files below plus focused attachment tests in System/ZSJOS, docs/operations/attachment-upload-limits.md and this handoff; existing unrelated edits retained.
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/withdrawal/WithdrawalServiceImpl.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/studentcontact/StudentContactServiceImpl.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/examcalendar/ExamScheduleAttachmentService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/registration/RegistrationConstants.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/account/MediaAccountProfileController.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/positioning/PositioningEvidenceService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/positioning/PositioningCardService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/LeadSubmitterFeedbackConstants.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/LeadConstants.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/positioninginterview/PositioningInterviewService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/ExamAttachmentErrorCodes.java`
- `backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/notice/NoticeServiceImpl.java`
- `frontend/workbench/src/components/AccountProfilePanel.tsx`
- `frontend/workbench/src/components/PositioningInterviewDialog.tsx`
- `frontend/workbench/src/components/PositioningEvidence.tsx`
- `frontend/workbench/src/components/PositioningCardAttachments.tsx`
- `frontend/workbench/src/components/PositioningAttachmentPicker.tsx`
- `frontend/workbench/src/services/examScheduleAttachments.ts`
- `frontend/h5/src/components/ImageUploader.vue`
- `frontend/h5/src/pages/lead/submit.vue`
- `frontend/admin/src/views/zsjos/forcedForm/index.vue`
- `frontend/admin/src/views/hrm/employee/detail/EmployeeMaterialFiles.vue`
- `frontend/admin/src/views/hrm/employee/detail/EmployeeContractForm.vue`
- `frontend/admin/src/views/hrm/recruit/candidate/RecruitCandidateForm.vue`
- `frontend/admin/src/views/hrm/portal/performance/assessment/process/PerformanceAppealForm.vue`
- `frontend/admin/src/views/bpm/processInstance/detail/ProcessInstanceOperationButton.vue`
- `frontend/admin/src/components/FormCreate/src/config/useUploadFileRule.ts`
- `frontend/admin/src/components/UploadFile/src/UploadFile.vue`
- `frontend/admin/src/views/system/notice/NoticeEditor.vue`
- `frontend/workbench/src/components/NoticeEditorDialog.tsx`
- `frontend/workbench/src/services/api.ts`
- `frontend/h5/src/api/request.ts`
- `frontend/admin/src/config/axios/service.ts`
- `docs/frontend/exam-schedule-attachments.md`
- `docs/api/zsjos-sales-order.md`
- `docs/api/zsjos-lead-submission-dispatch.md`
- `docs/api/registration-fulfillment-api.md`
- `docs/api/positioning-service-application.md`
- `docs/api/positioning-interview.md`
- `docs/api/media-account-profile.md`
- `docs/api/lead-submitter-feedback.md`
- `docs/api/exam-calendar.md`
- `docs/frontend/notice-attachments.md`
- `docs/frontend/bpm-attachment-upload.md`
- `frontend/workbench/src/services/examScheduleAttachments.test.ts`

- Scope update: frontend/admin/src/config/axios/index.ts upload helper also owns multipart requests passed as plain objects (Axios converts after request interception).

- Scope update: MediaAccountProfileService.java has a second byte-array size guard; align it with its controller before final verification.

- Scope update: frontend/admin/src/config/axios/index.ts upload helper also owns multipart requests passed as plain objects (Axios converts after request interception).
