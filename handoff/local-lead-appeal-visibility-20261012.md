# 客资申诉进度可见性

## Registration — 2026-10-12
- ID/owner: local-lead-appeal-visibility-20261012 / Codex.
- Environment: local (environment variable unset, /etc/zsjos/agent-environment absent).
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target/integration: None.
- Goal: server-projected appeal summary on lead list/detail, overview alert, independent status, tab badge, authorized pending-task link.
- Non-goals: no changes to qualification/appeal rules, role grants, SQL, dependencies, deployment or unrelated edits.
- Ownership: LeadManagementRespVO.java, LeadManagementServiceImpl.java, LeadAppealMapper.java, LeadAppealService.java, LeadAppealServiceImpl.java; new LeadAppealSummaryService.java and focused tests; frontend/workbench/src/services/api.ts, new services/leadAppealSummary.ts and tests, components/LeadDetail.tsx, LeadDetailOverview.tsx, new LeadAppealSummary.tsx, pages/LeadManagementPage.tsx; new test/lead-appeal-visibility fixtures; docs/api/supervisor-lead-overturn.md; this record.
- Dependencies: preserve existing local edits; prior lead filter work delivered. Reuse System permission and BPM task APIs.
- Verification: focused backend/frontend tests, compile/typecheck, desktop/mobile browser fixture with allowed/denied and appeal lifecycle cases; scoped diff checks. Shared response additive compatibility review for Admin/H5.

- Ownership update: also SubordinateSalesPage.tsx (same list badge) and LeadManagementServiceImplTest.java (inject new collaborator and verify projection wiring).

## Delivery — 2026-10-12
- Completed: batched latest appeal summary on authorized employee lead lists/details; BPM-assigned, permission-checked handler link; overview warning retaining current validity; independent appeal status; processing tab label; management inbox/table and subordinate table badges.
- Presentation: use responsible-supervisor wording because the established appeal workflow also covers education owners. Preserve theme geometry; only prevent appeal status caption from shrinking.
- Verification: backend reactor compile passed; 80 focused tests passed (summary 3, handle access 3, existing appeal 22, management 52); after adding projection-wiring assertions management 52 rerun passed. Frontend 16 focused tests and final typecheck passed. Real Chrome with synthetic HTTP passed management/subordinate at 1440/390, view/process links, route identity, denied handler, second/third-stage labels, completed/absent states, and management table badge. Desktop/mobile screenshots visually inspected at C:/Users/EDY/AppData/Local/Temp/zsjos-lead-appeal-visibility. Scoped diff --check passed.
- Consumer compatibility: inspected Admin LeadManagementVO and request unwrap (additive unknown response property tolerated); H5 Partner projection remains without summary population. No shared auth contract or existing response field changes. Authenticated runtime acceptance for all consumers not performed.
- Limits: no service restart, deployment, live business command, SQL or role changes. Live account/BPM integration remains unverified until updated backend is running. Browser fixture verifies UI behavior, not production data.
- Existing unrelated changes preserved. Ownership released; target/integration None.
