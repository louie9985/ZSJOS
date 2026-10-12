# 教务指定客资挂起恢复 — 2026-10-10

## Registration
- Environment: local (environment variable/file absent); branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; owner: /root; integration: None.
- Goal: allow configured managers to restore education self-sourced and accepted partner-specified leads to the original eligible education owner, regardless of current acceptance permission or partner assignment relation.
- Non-goals: real lead/role mutations, migrations, service restarts, deployment, branches, commits or unrelated changes.
- Ownership: LeadRestoreOwnerPolicy.java; LeadRestoreOwnerPolicyTest.java, LeadQualificationServiceImplTest.java, LeadManagementServiceImplTest.java under the ZSJOS lead package; frontend/workbench/test/education-lead-restore.tsx and education-lead-restore-browser.py; docs/api/zsjos-lead-submission-dispatch.md, docs/api/partner-specified-assignment.md, docs/api/zsjos-education-self-sourced.md; only the education restoration paragraph in docs/architecture/data-and-permission-flow.md; this record.
- Dependencies: existing System account/department/personnel eligibility, configured operation permission and manager scope, common restoration transaction. Preserve existing architecture document edits in other sections and all unrelated work.
- Verification: focused backend owner eligibility/action/command/lifecycle/permission tests; Workbench and Admin contract/static checks; existing Workbench real-component browser fixture with synthetic transport. Real backend restoration is outside scope.
- Approved rule replaces previous self-sourced-only education restoration scope. Revoked acceptance permission does not revoke existing ownership or authorize follow-up/judgment actions.
