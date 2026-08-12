# Workstream Handoff: lead-inbox-lazy-loading

- Workstream ID: `lead-inbox-lazy-loading`
- Status: `active`
- Goal: Improve the submitted and owned lead inbox lazy-loading experience with stable 20-item batches, early sentinel prefetch, distinct initial and incremental states, retryable incremental failures, and scroll reset on query changes.
- Non-goals: Cursor pagination; list virtualization; backend API, permission, dictionary, database, or filter-profile changes; new npm dependencies; changes to sales-order approval or other inboxes; modifying the dirty primary worktree; commits, push, merge, or publication.
- Branch: `codex/lead-inbox-lazy-loading`
- Worktree: `D:\ZSJ-OS-worktrees\lead-inbox-lazy-loading`
- Base commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- Target branch: `main`
- Ownership scope: `frontend/workbench/src/pages/LeadManagementPage.tsx`; focused lead-management utilities and tests under `frontend/workbench/src/services/`; directly required styles in `frontend/workbench/src/styles.css`; directly affected lead inbox documentation; this handoff file.
- Owner: Codex `lead-inbox-lazy-loading` workstream.
- Dependencies: Existing lead inbox page APIs and published server-owned filter profiles. No new code dependency is planned. The primary worktree has an uncommitted submission-guard change in `LeadManagementPage.tsx` that is intentionally absent from this committed base.
- Integration order: First preserve or commit the primary worktree submission-guard change, then integrate this workstream and resolve `LeadManagementPage.tsx` by retaining both independent behaviors. Do not overwrite either change set.
- Verification plan: Focused lead-management unit tests; full workbench tests; TypeScript typecheck; production build; desktop and mobile browser checks against an available authenticated runtime covering initial loading, incremental loading, completion, empty, initial failure, incremental failure and retry where the environment permits; `git diff --check`.

## Entries

### 2026-08-12 11:25:18 +08:00

- Branch: `codex/lead-inbox-lazy-loading`
- Worktree: `D:\ZSJ-OS-worktrees\lead-inbox-lazy-loading`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Create an isolated branch and implement the complete first-stage optimization for lazy loading in the left lead inbox list.
- Key decisions: Kept the backend contract and fixed batch size at `20`; replaced the scroll-distance handler with an `IntersectionObserver` rooted in the actual list container and a `240px` preload margin; separated initial loading/failure from incremental loading/failure; retained loaded rows on incremental failure and stopped automatic retries until the user retries; reset scroll and discard stale results on query changes; determined `hasMore` from the backend page boundary instead of the deduplicated row count; did not add cursor pagination, virtualization, dependencies, backend behavior, or permission changes.
- Execution or analysis result: Submitted and owned lead inboxes now show five initial skeleton rows, distinguish unauthorized and retryable initial failures, prefetch the next 20-row page through a bottom sentinel, preserve existing rows on next-page failure with a local retry action, display completion state, and reset to the first page and top scroll position after search/filter changes. The isolated development server is available at `http://127.0.0.1:5177`.
- Changed files: `frontend/workbench/src/pages/LeadManagementPage.tsx`; `frontend/workbench/src/services/leadManagement.ts`; `frontend/workbench/src/services/leadManagement.test.ts`; `frontend/workbench/src/styles.css`; `docs/architecture/data-and-permission-flow.md`; `handoff/lead-inbox-lazy-loading.md`.
- Verification evidence: Focused lead-management tests passed (`11`); full workbench tests passed (`63`); `npm run typecheck` passed; `npm run build` passed with only the existing bundle-size warning; `git diff --check` passed; isolated Vite server started and returned the login UI on both `127.0.0.1:5177` and `localhost:5177`.
- Dependency or integration impact: No new dependencies or lock-file changes. The primary worktree has an uncommitted submission-guard edit in the same `LeadManagementPage.tsx`; integration must preserve that change while applying this workstream. The isolated worktree installed the existing lock-file dependencies locally for verification only.
- Remaining work: Browser verification of the authenticated lead list at desktop and mobile widths, including actual sentinel paging and incremental retry, remains unverified because the isolated origin has no reusable login session and no credentials were supplied. No commit, merge, push, or publication was performed.
