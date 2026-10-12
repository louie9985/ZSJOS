# 申诉原无效结论字段说明

## Registration — 2026-10-12
- ID/owner: local-appeal-field-labels-20261012 / Codex.
- Environment: local (ZSJOS_AGENT_ENV unset; environment file absent).
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target/integration: None.
- Goal: add explicit 无效原因： and 备注： labels to the original invalid conclusion in LeadAppealPanel.
- Non-goals: no business, API, permission, snapshot, layout or dependency changes.
- Ownership: frontend/workbench/src/components/LeadAppealPanel.tsx; frontend/workbench/docs/ui-guidelines.md; this record.
- Dependencies: previous appeal detail and cycle work delivered with ownership released; preserve all existing local edits.
- Verification: scoped content/diff check, Workbench typecheck, existing browser fixture at desktop/mobile widths with synthetic transport.

## Delivery — 2026-10-12
- Added 无效原因： and 备注： to the existing snapshot text; retained values, fallback behavior and layout. Updated the appeal UI guidance.
- Verification: npm run typecheck PASS; scoped git diff --check PASS; Chrome fixture checks PASS at 1440/390 for current values and historical missing snapshots. Screenshots visually inspected under C:/Users/EDY/AppData/Local/Temp/zsjos-appeal-field-labels.
- Initial browser checks incorrectly expected 未记录原因 / 标签未保存; verified the unchanged helper returns 标签未配置 and corrected the ad hoc assertion. No production fallback change.
- Browser transport was synthetic; existing fixture image URL was unavailable, so attachment preview displayed its existing error state. No attachment changes or authenticated runtime acceptance claimed.
- Preserved prior local edits. No deployment, dependency, database, Git commit or service changes. Ownership released.
