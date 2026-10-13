# Local publication — 2026-10-13 Beijing

## Registration
- Environment: local; owner: Codex /root; branch: main; worktree: D:/ZSJ-OS; base: 45f1edbe24b316b5f5924416c4fc18977658ff5f.
- Goal: user-authorized commit and push of pending source, tests, SQL and delivery documentation to origin/main.
- Ownership: this record and Git publication of current pending feature files. Non-goals: feature edits, database execution, services, deployment, cleanup, new dependencies or branch changes.
- Dependencies/integration: reviewed current feature handoffs; origin/main equals base. Commit snapshot then normal push. Preserve root temporary/report artifacts, backend/.mvn, frontend/workbench/tmp, Python caches and TypeScript build info locally.
- Verification: staged scope, conflict-marker/private-key pattern checks, migration/static consistency, remote identity. Feature runtime evidence remains in its own handoffs; this publication does not claim full release acceptance.

## Delivery preparation — 2026-10-13 Beijing
- Context: registration above; pre-publication HEAD 45f1edbe24b316b5f5924416c4fc18977658ff5f; owner /root.
- Snapshot: 189 source/test/SQL/documentation/handoff files covering content-review selection and scoped student access, calendar views/palette/attachments/notifications, order and notice presentation; includes Core V315 and V316. Existing contents preserved.
- Checks: zsjos_db.py check PASS; staged conflict-marker and high-confidence private-key/token scans found zero matches. Whitespace check reports only a pre-existing extra EOF blank line in test_content_review_profile.py:60, retained. No new dependency or runtime test executed by publication; feature-specific results/limitations remain in the corresponding handoffs.
- Integration: commit snapshot and normal push to origin/main authorized by user; final commit and remote result verified and reported in conversation. This entry is prepared before publication and does not itself assert remote success.
- Remaining: excluded local caches/probes/browser artifacts, backend/.mvn, .workbuddy and daily report/archive retained; deployment and full runtime acceptance outside publication scope. Ownership released on completion of Git operation.
