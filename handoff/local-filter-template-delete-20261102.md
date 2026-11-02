# Template dropdown deletion
- Environment: local; workstream: filter-template-delete-20261102; owner: current Codex chat.
- Goal: personal template trash icon in dropdown with confirmation.
- Non-goals: backend, permissions, database, deployment, existing relative-time changes.
- Branch: main; worktree: D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Ownership: AdvancedFilter.tsx, task-specific tmp/filter-template-delete fixtures, this record, docs/frontend/advanced-filter-template-deletion.md.
- Dependencies: existing template API and Ant Design; previous relative-time ownership released.
- Verification: typecheck, focused filter tests, browser interaction where available.
- Target branch/integration order: None.

## Delivery
- Added personal dropdown trash icon, named confirmation, request loading/error handling and list refresh; preserves draft/applied filters. Existing relative-time edits preserved.
- Workbench typecheck and 11 focused filter tests passed; scoped diff check passed.
- Ego browser isolated API fixture: personal-only icon, click does not select template, cancel preserves template, confirmed delete calls correct ID. No real records deleted.
- Production account/API acceptance, mobile visual inspection and failure-path browser testing remain unverified. No commit, push or deployment.
- Ownership released.
