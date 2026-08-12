# Workstream Handoff: subordinate-sales-integration

- Workstream ID: `subordinate-sales-integration`
- Status: `active`
- Goal: Integrate the committed main baseline with the aging collaboration pool, invalid-lead task cleanup, and lead-inbox lazy-loading workstreams as the committed base for subordinate-sales management.
- Non-goals: Implement subordinate-sales behavior, execute database migrations, change real permissions, push, or merge into `main`.
- Branch: `codex/subordinate-sales-integration`
- Worktree: `D:\ZSJ-OS-worktrees\subordinate-sales-integration`
- Base commit: `405f9ed301dc613349c3823d6217cd28b165bed4`
- Target branch: `main`
- Ownership scope: Merge conflict resolution for the three named workstreams, migration-number coordination, affected integration documentation, and this handoff file.
- Owner: Codex `/root`
- Dependencies: `codex/aging-collab-pool`, `codex/invalid-lead-task-cleanup`, and `codex/lead-inbox-lazy-loading` committed workstreams.
- Integration order: Aging collaboration pool, invalid-lead task cleanup with migration renumbering, then lead-inbox lazy loading; subordinate-sales work starts only after this branch is committed and verified.
- Verification plan: Git conflict review, migration manifest/static database check, focused backend tests, Workbench tests/typecheck/build, and `git diff --check`.

## Delivery Entries

