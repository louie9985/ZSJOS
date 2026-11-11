# Exam collection upload limits

Environment local (environment variable and /etc marker absent). Workstream local-exam-upload-limits-20261111. Owner Codex. Branch main. Worktree D:/ZSJ-OS. Base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Integration None.

Goal per-material size/count settings with defaults 10 MB and 1. Scope ExamCollectionDialogs.tsx, services/examCollection.ts, ExamCollectionVO.java, ExamCollectionFields.java, ExamCollectionTaskService.java, ExamCollectionFieldsTest.java and docs/api/exam-material-collection.md. Preserve existing edits. Dependencies existing field snapshot and H5 validation. Verification focused Java tests, Workbench typecheck and browser where available.

## Delivery

Implemented per-selected attachment dropdowns, snapshot limits and idempotency hashing, preview propagation. Limits 1–100 MB and 1–9 files, defaults 10/1. Identity retains one per side. No SQL required. Maven focused tests 15 passed, Workbench typecheck passed, real browser localhost:5174 checked defaults and changing quantity to 2. Screenshot C:/Users/EDY/AppData/Local/Temp/exam-limits-verified.png. Live create/upload roundtrip and deployment unverified, running backend was not restarted. Ownership released.

## Follow-up registration

Owner Codex resumes local workstream on main at D:/ZSJ-OS. Scope expanded to frontend/h5/src/pages/examCollection/index.vue, frontend/h5/src/pages/studentInfo/CollectionImages.vue and Workbench ExamCollectionBodyPreview.tsx. Goal permanent count next to field labels and full-capacity upload hiding, preview parity. Existing shared upload hide logic retained. Verification H5 build, existing validation checks and browser fixture.

## Follow-up delivery

Persistent field-label counts added on exam H5 and preview, identity-side counts added to existing shared uploader. Existing capacity guard verified hiding file input while full and restoring after delete. H5 production build passed, five exam validation tests passed, Workbench typecheck passed. Real browser against isolated localhost:5199 API fixture verified 0/1 -> 1/1 with zero file inputs -> delete -> 0/1 with upload restored. Screenshot C:/Users/EDY/AppData/Local/Temp/exam-count-full.png. Live backend upload and deployment remain unverified. Ownership released.
