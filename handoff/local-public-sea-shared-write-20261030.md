# 公海 A/B 协同编辑
- ID: local-public-sea-shared-write-20261030; environment: local (ZSJOS_AGENT_ENV unset, marker absent)
- Owner: current Codex chat; branch: main; worktree: D:/ZSJ-OS; base: ba8c788f470eb96d22e8729a2d8f711d83c0c370
- Goal: A follow-up in waiting/assigned; A/current B basic-info editing during active public sea.
- Non-goals: deal authorization, transfer, task responsibility, database writes, deployment, commits.
- Ownership: service/lead/{LeadPublicSeaWritePolicy,LeadCollaborationService,LeadBasicInfoService,LeadFollowUpServiceImpl,LeadObjectPermissionService,LeadManagementServiceImpl,LeadPublicSeaPoolServiceImpl}.java; corresponding focused service tests; docs/api/zsjos-lead-public-sea-pool.md; this record.
- Dependencies: existing permission identifiers, shared backend actions consumed by Admin/Workbench. Preserve concurrent public-sea page work.
- Verification: focused Maven tests/compile; inspect both frontend action consumers; available frontend/browser checks. Target branch/integration order: None.
## Delivery
- Implemented action-specific shared policy: A follows in waiting/assigned, current B follows in assigned/historical pending; A/current B edit active pool basic information. Existing feature permissions, lifecycle checks, locked service validation and pool conflict rejection remain enforced.
- Preserved deal restrictions and B task responsibility. A records actual authorship without completing B reminders or creating A personal pool reminders. Updated API contract and Workbench server action projection; Admin existing read/history consumer unchanged.
- Verification: final Maven focused suite (7 classes) passed, including actor/state matrix, locked permission checks, actual author/reminder behavior and A/B detail actions. Workbench existing public-sea/assist guards 5/5 passed. Scoped git diff --check passed. No frontend source/API shape change.
- Limitations: no real-account HTTP/browser or concurrent database acceptance; no runtime deployment/restart, database changes, grants, commits or push. Concurrent unrelated work preserved. Ownership released.
