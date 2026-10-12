# Closed Lead display and legacy history diagnosis

## Registration — 2026-10-12 Beijing
- ID/owner: local-closed-lead-display-20261012 / Codex.
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target branch/integration: None.
- Goal: show closed status instead of pending qualification on Workbench Lead list/table/overview; read-only assessment of restoring the 102 imported closed Leads' history.
- Non-goals: no backend contract, permissions, filters, database mutations, runtime lifecycle, dependencies, commits or branches.
- Ownership: frontend/workbench/src/services/leadManagement.ts and leadManagement.test.ts; frontend/workbench/src/pages/LeadManagementPage.tsx; frontend/workbench/src/components/LeadDetailOverview.tsx; new frontend/workbench/test/closed-lead-status.html, closed-lead-status.tsx, closed-lead-status-browser.py; frontend/workbench/docs/closed-lead-status.md; this record.
- Dependencies: existing server status, established status labels and UI tokens. Prior appeal visibility/filter refresh delivered; preserve their existing changes. Local Docker ruoyi-vue-pro tenant 1 and ptml used read-only for diagnosis.
- Verification: focused Vitest and typecheck; existing Vite on 5174 with isolated synthetic fixture, desktop/mobile overview and table/inbox checks; scoped diff. History matching by explicit legacy import identifiers, no numeric identity-domain inference.

## Delivery — 2026-10-12 Beijing
- Workbench inbox, table status cell and detail overview now display the explicit closed lifecycle status instead of the legacy pending qualification fallback. Overview uses the existing gray style and labels the field 客资状态. Other qualification labels remain unchanged; no API or action contract changes.
- Tests: 36 Vitest cases passed (leadManagement helpers, overview lifecycle, management guard); npm run typecheck passed. Real Chrome with synthetic responses passed closed and pending checks at 1440/390 in split/table preference/overview (6 scenarios, mobile table preference intentionally renders inbox). Desktop/mobile screenshots inspected under the current user's temporary zsjos-closed-lead-status directory. Scoped git diff --check passed.
- Browser fixture repaired its initial selector and mobile-layout assumption; these were verification-script errors, not product failures. No service was started or stopped.
- Read-only diagnosis: 102 closed tenant-1 Leads match legacy records; 103 old closure events with time/reason, 28 with operator identifiers and name snapshots, no attachments. Source identifiers match 9 existing events; 94 unmatched old events affect 93 Leads. New-system 101 `other` assignment rows are `legacy assignment history baseline`, with null owners; old assignment logs are absent. This is a recovery candidate count, not an approved write manifest. Missing actor identities require verified mapping and absent historical actions must not be invented.
- Files: exactly registered scope. Existing appeal/provider/refresh edits preserved. No database writes, migration, dependency, backend, deployment, branch or commit changes. Server runtime acceptance remains unverified. Ownership released; integration None.
