# local-collection-idempotency-20261102
- Goal: prevent duplicate collection generation from effect replay and retries.
- Non-goals: backend contracts, database, deployment.
- Environment: local (environment variable and marker absent).
- Branch: main; worktree: D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Owner: current Codex chat. Scope: StudentInfoLinkModal.tsx and this record.
- Dependencies: existing backend command idempotency. Integration: None.
- Verification: Workbench typecheck; inspect stable request identity across replay/retry. Live account flow pending.
`nDelivery: generation command retains its original version and idempotency key across effect replay and retries. Existing client adapter changes preserved. Typecheck passed. Live browser/account reproduction not performed; screenshot root cause remains a code-supported hypothesis. Ownership released.
