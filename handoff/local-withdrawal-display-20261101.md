# local-withdrawal-display-20261101
- Environment: local (environment variable and marker absent).
- Goal: withdrawal applicant suffix and distinct status tag backgrounds, including detail.
- Non-goals: backend/API, database, permissions, export, deployment.
- Branch: main; worktree: D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Owner: current Codex chat. Scope: WithdrawalPage display lines in frontend/workbench/src/pages/ManagementPages.tsx and this record.
- Existing metric-page work is delivered and its changes will be preserved. Dependencies: existing Ant Design Tag and server status labels.
- Integration: None. Verification: scoped diff, TypeScript; browser unavailable (ego-browser command missing).

## Delivery
- List and detail now display the legacy applicant suffix as （兼职）.
- Status tags use Ant Design orange (pending review), blue (approved), red (rejected), green (paid), default gray (cancelled). Labels continue to come from the server; unknown statuses retain the default tag appearance.
- Browser visual verification remains unavailable: ego-browser executable missing. No live-account verification or deployment.
- Ownership released on delivery.
- Verification: npm --prefix frontend/workbench run typecheck passed; scoped git diff --check passed.
