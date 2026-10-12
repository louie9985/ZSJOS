# 申诉全流程展开

## Registration — 2026-10-12
- ID/owner: local-appeal-full-process-20261012 / Codex. Environment local: variable unset, environment file absent.
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target/integration: None.
- Goal: implement user-approved full-process preview with all cycles/rounds visible, compact material/result comparison, process sidebar and existing common attachment components.
- Non-goals: API/backend/permissions/lifecycle/dependencies/deployment changes.
- Ownership: Workbench components/LeadAppealPanel.tsx, new components/LeadAppealProcess.tsx; styles/components/appeal.css; docs/ui-guidelines.md; test/lead-appeal-review.tsx and lead-appeal-review-browser.py; this record.
- Dependencies: previous detail/cycle/field-label work delivered and released; preserve all unrelated local changes. Existing decision form and common attachment/upload components remain authoritative.
- Verification: focused Vitest, typecheck, real Chrome fixture desktop/mobile, full-cycle visibility, exact task decision authorization, draft/retry and common attachments.

## Delivery — 2026-10-12
- All returned cycles and rounds render without selection or collapse; material/result comparison, full process sidebar, explicit labels, per-round original snapshots and current authorized decision form. AttachmentCard and DeferredAttachmentPicker remain reused through existing adapters. No invented original decision timestamp/person or future rounds.
- Updated UI guidance and existing browser fixture/regression for full history and three-round scenarios. Preserved prior local changes.
- Verification: typecheck PASS; focused Vitest 2 files / 5 tests PASS; existing Chrome regression PASS at 1440/390 including attachment preview/upload retry, draft preservation, decision retry, exact task, restricted/denied/error, split/table and submitter cycle entry. Additional full-cycle checks PASS at 1440/390 (4 records including history, one authorized form, all content visible without switching); screenshots inspected after drawer animation settled. Scoped diff check PASS.
- Browser uses synthetic transport; authenticated runtime acceptance and deployment not performed. No backend/API/database/permission/dependency/service/Git operations. Ownership released.
