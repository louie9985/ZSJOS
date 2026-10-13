# Publication — 2026-10-13 Beijing

## Registration
- Environment: local (variable and marker absent); owner: Codex /root; branch/worktree: main / D:/ZSJ-OS; base: 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a.
- Goal: user-authorized commit and push of pending code, tests, SQL, documentation and handoffs to origin/main.
- Ownership: publication staging and this record; preserve feature implementations. Non-goals: database/service operations, deployment, feature changes, dependency changes or artifact cleanup.
- Dependencies: feature handoff evidence; fetched origin/main equals base. Integration order: scoped snapshot commit then normal push.
- Verification: database static consistency, relevant checksum regressions, staged diff/conflict/secret-pattern checks, migration identities and remote hash. Exclude existing probes, screenshots, backend/.mvn, frontend/workbench/tmp and generated caches; retain all on disk.

## Delivery preparation — 2026-10-13 Beijing
- Context: registration above; pre-commit HEAD 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a; owner /root.
- Result: prepared 129-file snapshot of pending implementation, tests, Core V314/PMS V002, documentation and handoffs. No implementation rewritten for publication. Existing temporary artifacts and four tracked build caches remain unstaged.
- Verification: database static consistency PASS; checksum/execution/numbering suites 15 tests PASS; staged whitespace check PASS; conflict-marker and high-confidence private-key/token scans found zero matches. Reviewed feature handoff evidence including calendar, points, and migration compatibility work.
- Limits: full application builds, browser and all-module database acceptance were not rerun for this publication. Calendar handoff reports an all-module fresh PMS V002 prerequisite failure; Core verification passed there. Publication does not resolve or supersede that acceptance limitation.
- Integration: user-authorized normal commit/push to origin/main follows this record. Actual commit identity and remote outcome are verified and reported in the conversation after execution. No database or service operation. Remaining: feature deployment acceptance; temporary artifacts/caches retained locally. Publication-record ownership released on completion.
