# self-followup-required-20261028
- Environment: local (environment variable unset; /etc/zsjos/agent-environment absent).
- Goal: require future next follow-up time for sales/education automatic self-sourced creation.
- Non-goals: provider-linked submission, historical data, deployment, database, permissions.
- Branch/worktree/base: main; D:/ZSJ-OS; 0628f72f2c6208a6cc553f3f29b893de8f4ec697.
- Owner: current Codex chat. Target branch/integration: None. Dependencies: existing automation contract.
- Ownership: LeadSelfSourcedAutomationService.java and its Test.java; Workbench LeadSubmissionPage.tsx; Admin LeadCreateDialog.vue; docs/api/zsjos-lead-submission-dispatch.md; this record.
- Verification: focused Java tests; frontend static checks; available browser validation; scoped diff.
- Decision: user explicitly replaces optional-time rule with mandatory future time; preserve unrelated concurrent changes.

- Ownership update: existing frontend/workbench/test/self-sourced-auto-browser.py regression script also included.

## Delivery
- Implemented: both sales/education forms require future next-follow-up time; backend automation rejects null/past time with existing LEAD_FOLLOW_UP_TIME_INVALID. Provider-linked branch retains existing behavior. Updated API documentation and focused backend/browser regressions.
- Passed: Workbench npm run typecheck; scoped git diff --check; browser script Python syntax compilation.
- Blocked: Admin pnpm ts:check failed native/heap memory allocation. Maven focused test failed native memory allocation, including retry with Xmx512m and ActiveProcessorCount=2; no passing Java test result claimed.
- Browser regression not executed: fixture ports 5193/5194 unavailable and current machine cannot reliably allocate memory (Node, JVM and initial Git allocation failures). Real browser, Admin typecheck and Java test acceptance remain outstanding.
- No database changes, deployment, restart, commits or push. Ownership released.

## Verification retry — 2026-10-28
- User requested Admin check retry. pnpm ts:check completed without OOM, exit 2: 12 TS2300 diagnostics for six duplicate WithdrawalVO fields in frontend/admin/src/api/zsjos/withdrawal/index.ts (15–16 and 40–45). Source inspection confirms duplicates. No diagnostics in changed LeadCreateDialog.vue. Unrelated withdrawal source preserved; full Admin check remains failing. Other prior verification limitations unchanged.

## Registration update — withdrawal type cleanup
- User authorized fixing the duplicate fields blocking Admin typecheck.
- Context unchanged: local/main, D:/ZSJ-OS, base 0628f72f2c6208a6cc553f3f29b893de8f4ec697; owner current chat.
- Added ownership: frontend/admin/src/api/zsjos/withdrawal/index.ts (clean before edit).
- Scope: remove only six identical duplicate optional declarations; no API/behavior changes.
- Verification: pnpm ts:check and scoped diff; target/integration None.

## Delivery — withdrawal type cleanup
- Removed six identical duplicate optional declarations, retaining original types and field availability.
- Passed: pnpm ts:check (exit 0); scoped git diff --check. No runtime behavior changes; no deployment/commit/push. Ownership released.
- Admin verification blocker resolved; previous Java/browser verification limitations remain unchanged.
