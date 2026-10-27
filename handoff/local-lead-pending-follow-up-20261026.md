# local-lead-pending-follow-up-20261026

- Environment: local (ZSJOS_AGENT_ENV unset, environment marker absent).
- Owner: Codex /root; branch: main; worktree: D:/ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5; target branch/integration: None.
- Goal: allow follow-up during deal approval without changing approval lifecycle or existing authorization.
- Non-goals: grants, database changes, dependencies, deployment, Git publication; invalid/suspended/closed expansion pending clarification.
- Ownership: LeadManagementServiceImpl.java, LeadFollowUpServiceImpl.java and their existing tests under backend/yudao-module-zsjos; docs/api/zsjos-lead-submission-dispatch.md; this handoff record. Use this independent record because another workstream owns handoff/main.md.
- Dependencies: existing server availableActions, follow-up permission and object authorization, existing Workbench follow-up components; Admin history consumer remains read-only.
- Verification: focused backend service/action/object-permission tests and compile; inspect both frontend consumers; reuse existing isolated browser fixture where applicable; scoped diff checks. Preserve unrelated dirty work.
- Decision: user's explicit request overrides the documented exclusion of deal-pending-approval follow-up; synchronize that contract.

## Delivery

- User clarified scope: only deal-pending-approval; invalid/suspended/closed remain restricted.
- Backend now projects ADD_FOLLOW_UP for valid Leads with pending-approval Opportunities when existing feature/object permissions allow. Follow-up writes retain deal_pending_approval, Lead status and order lifecycle; existing dictionary snapshots and future reminder requirements retained.
- Updated the existing follow-up API contract. Workbench consumes server actions in detail and reuses panel/modal without client changes. Admin's existing Lead page only reads history; no new edit UI or API shape introduced.
- Verification: Maven compile and focused 57 tests passed (20 follow-up service, 3 action projection/lifecycle, 34 object permission). Workbench 38 focused tests passed. Chrome synthetic transport passed pending-approval panel/modal saves at 1440/390 widths using the existing sales-stage fixture transformed in memory. Scoped diff checks passed.
- Broader initial run: 113 of 115 tests passed; two existing educationRestoreProjectionNeedsPermissionAndManagedOwner cases fail because LeadRestoreOwnerPolicy.teamTransferPolicy is not injected. Outside scope; left unchanged. Browser script was adapted to current Radio.Button labels and direct submit behavior after finding stale Select/confirmation assumptions in the old fixture script.
- Evidence: TEMP/zsjos-pending-follow-up-maven.log (broader existing failures), TEMP/zsjos-pending-follow-up-focused.log (passing focused run), TEMP/zsjos-pending-follow-up-browser.py (browser reproduction).
- Limits: no authenticated live-account/API acceptance, deployment/restart, schema/data/grant changes or Git publication. Existing unrelated changes preserved. Ownership released.
