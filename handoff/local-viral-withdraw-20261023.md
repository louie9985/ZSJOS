# Workstream registration — local-viral-withdraw-20261023

- Beijing date: 2026-10-23; environment: local (variable/file absent); owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal: restore Mine material withdrawal and edit/resubmit entries for both viral types.
- Non-goals: backend/BPM state changes, grants, SQL, deployment, unrelated dirty work.
- Ownership: frontend/workbench/src/pages/MaterialLibraryPage.tsx; frontend/workbench/test/material-review*; docs/api/material-library.md; this log.
- Dependencies: existing server CANCEL/UPDATE actions, BPM starter cancellation and material forms. Preserve existing page diff.
- Verification: typecheck, existing real Chrome lifecycle/permission/error fixtures at desktop/mobile; scoped diff.
- Target branch/integration order: None.

## Delivery — 2026-10-23 Beijing time

- Context: registration branch/worktree/base unchanged; HEAD 404387de566fcb9abf4b264f0e005bf0566ea209; owner released.
- Diagnosis: detail conversion to mode=view removed lifecycle actions although server CANCEL and cancellation-to-DRAFT handling remain.
- Result: restored Mine withdrawal with required reason and retained failure/retry, plus UPDATE-gated edit/resubmit for both viral forms. Refresh explicitly retains withdrawn detail even with IN_APPROVAL filter. Reused existing APIs and preserved all prior page edits.
- Files: MaterialLibraryPage.tsx; material-review.py/.tsx; material-review-flow-browser.py (precise button locators); docs/api/material-library.md; this log.
- Verification: npm run typecheck passed. Real Chrome synthetic transport lifecycle suite passed for account and content, each at 1440/390px: withdrawal, reason, retry, denied permission/action, draft resubmit and effective revision submission. Mobile screenshot inspected. Scoped git diff --check passed.
- Supplemental flow-panel browser suite: first exposed obsolete ambiguous button locator (corrected); rerun blocked by existing port 5174 becoming unavailable (ERR_CONNECTION_REFUSED). No service restart performed. This supplemental flow-panel check remains unverified; core lifecycle suites completed before service loss.
- Dependencies/integration: None. No backend, SQL, grants, dependencies, service changes or deployment. Real authenticated backend acceptance remains pending; fixtures do not establish deployment or account authorization.
