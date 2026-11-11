# 考试资料收集移除微信号前端记录

- Environment: local (ZSJOS_AGENT_ENV unset, previous local environment verified).
- Owner: /root/frontend_acceptance, coordinated by /root.
- Branch/worktree: main, D:/ZSJ-OS. Base: current main HEAD recorded by Git before edits.
- Goal: three fixed required fields (name/mobile/id_card); new and existing public forms exclude wechat from rendering, validation and submission; preserve historical archive/submission display.
- Ownership: frontend/workbench/src/components/examCollection/ExamCollectionDialogs.tsx; frontend/h5/src/pages/examCollection/index.vue; frontend/h5/src/utils/examCollectionValidation.ts; frontend/h5/tests/exam-collection-validation.test.mjs; temporary frontend fixtures and browser evidence; this record.
- Dependencies: backend_acceptance owns effective fields and archive merge compatibility; no new dependencies or database/service changes.
- Verification: focused H5 validation regressions, Workbench typecheck/build and H5 build, isolated browser new/legacy field schemas and submitted payload inspection.
- Integration target/order: None; root coordinates review, no commit by contributor.

## Delivery
- Base commit 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Changed only the four registered production/test files. Creation prompt now lists name/mobile/id_card; H5 effectiveExamFields filters legacy wechat consistently for rendering, validation and submitted values. Historical archive/submission components were not modified.
- Validation: 9/9 H5 focused tests passed, including a legacy required wechat snapshot and stale answer exclusion. H5 production build and Workbench production build (including type checks) passed. Scoped diff check passed.
- Ego space47 real Vue component fixture verified both new and legacy schemas render three inputs, legacy submission succeeds without wechat and actual submitted keys are exactly name/mobile/id_card. Mobile390 screenshot three-fields-mobile.png inspected. Fixture isolates all business API prefixes with 503; this was frontend fixture verification, backend agent owns corresponding live-fields/archive compatibility tests.
- Temporary H5 Vite server port5191 owned session61020, configured only under frontend/h5/tmp/exam-no-wechat, no backend proxy. Browser space47 finished with no retained pages. Build metadata and tmp/artifacts excluded from intended submission. No commit or database/service configuration mutation by this contributor.
- Closed owned fixture process session61020 after verification. Production services were not operated.
