# local-student-repeat-search-20261116

- Environment: local (ZSJOS_AGENT_ENV unset; environment file absent).
- Owner: current Codex chat; branch main; worktree D:/ZSJ-OS; base 21631a8a98a62c41a3c71596a6739e03a535c3b6.
- Goal: fix student list clearing on repeated unchanged keyword search, reproduce with real department-two supervisor.
- Non-goals: backend, authorization, schema, concurrent sales merge edits, branch/commit/service operations.
- Ownership: keyword callback in frontend/workbench/src/pages/RegistrationPages.tsx; this record. Preserve existing edits elsewhere in the file.
- Dependencies: stable running Workbench at http://[::1]:5174 and existing test student.
- Integration target/order: None.
- Verification: scoped diff, Workbench typecheck, browser changed keyword / unchanged repeated search / no-results / return to matching keyword.

## Delivery

- Root cause reproduced before editing: unchanged keyword search cleared rows/selection, but unchanged React state did not rerun the loading effect. This was unrelated to transfer persistence or backend data scope.
- Changed only student keyword callback: same keyword explicitly reloads page 1 while preserving visible data; changed keyword invalidates older list responses before reset/reload.
- Verification: npm run typecheck PASS; git diff --check for target PASS. Real browser, department-two supervisor: repeated unchanged search retains test student; no-match search yields 0/0; restoring matching keyword yields 1/1 and correct UNSCHEDULED class detail. No API mutation substituted for UI checks.
- Final screenshot unavailable because browser window was minimized; semantic snapshot and DOM status confirmed final 1/1 state. No service restart, DB edit, permission change, commit or push.
- Ownership released. Existing unrelated modifications in RegistrationPages.tsx preserved.
