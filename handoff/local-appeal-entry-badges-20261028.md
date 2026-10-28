# local-appeal-entry-badges-20261028
- Environment: local; owner: Codex current chat; branch: main; worktree: D:\ZSJ-OS.
- Base: 0628f72f2c6208a6cc553f3f29b893de8f4ec697 (registration context; verify current HEAD below).
- Goal: include appeals in workflow lists and show authoritative appeal pending count on home/navigation.
- Non-goals: permissions, DB, deployment, historical recovery, order workflow policy.
- Ownership: frontend/workbench/src/services/approvalNavigation.ts, components/MenuTaskBadgeProvider.tsx, main.tsx, services/approvalNavigation.test.ts; docs/api/zsjos-lead-appeal.md; this record.
- Dependencies: existing appeal inbox and BPM task APIs; current user confirmation supersedes old exclusion policy.
- Verification: focused tests, TypeScript, browser interaction when available; target branch/integration: None.

- Verification scope update: own frontend/workbench/test/appeal-badges.html, appeal-badges.tsx, appeal-badges-browser.py for browser regression.

## Delivery
Removed only appeal process exclusion from workflow list/count requests. Added permission-gated appeal inbox total to existing homepage/navigation badge resolver and existing refresh cycle. Documentation updated. Verification: 7 focused Vitest cases passed; tsc --noEmit passed; browser regression passed at 390 and 1440 widths for pending counts, query failure, retry, completion and revoked permission; git diff --check passed. Browser used synthetic API responses, not real supervisor acceptance. No application deployment, restart, permissions or database change. Ownership released.

## Scope update: embedded appeal review
User requests actual appeal handling UI inside workflow. Ownership reopened for components/bpm/BusinessTaskApproval.tsx, components/LeadAppealReviewDetail.tsx, docs/api/zsjos-lead-appeal.md, test/appeal-workflow.html, test/appeal-workflow.tsx, test/appeal-workflow-browser.py and this record. Same branch/environment/owner. Reuse authoritative task-target API and shared decision component; no DB or deployment. Verify types and browser pending/done/error/retry behavior.

## Embedded appeal delivery
Workflow appeal tasks now resolve through existing BPM business-target endpoint and render the same LeadAppealReviewDetail used by the appeal inbox, with business decisions, dirty/busy host integration, and explicit read-only done view. API contract checked: appeal target provides appealId/leadId/handled, while original authorized task ID is passed to review-detail. No generic-decision fallback. TypeScript passed; desktop/mobile browser fixture passed shared content, error/retry, task correlation, dirty-state propagation and done read-only cases; screenshots inspected. Browser used synthetic data, no real decision submitted. Diff checks passed. No runtime deployment. Ownership released.
