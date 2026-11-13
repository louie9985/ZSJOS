# Review fixes 2026-11-09

## Registration

- Environment local. ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent.
- ID local-review-fixes-20261109, owner /root, branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Goal fix three confirmed review findings. Scope includes ExamCollectionTodoTest.java, ExamCollectionPage.tsx, PmsKnowledgeSharePage.java and its controller test, docs/api/exam-material-collection.md, docs/api/pms-knowledge-public-share.md and this record. Verification resources use artifacts/review-fixes-20261109 only.
- Existing registrations for these files have delivered and released ownership. Preserve all pre-existing changes.
- Dependencies existing typed exam API, browser CSS support and existing Java/React test facilities. Integration target/order None.
- Verification focused Java tests, Workbench typecheck, browser fixture checks for refresh and image fallback, scoped diff checks.
- Non-goals database changes, deployment, dependency additions and Git publication.

## Delivery 2026-11-09 Beijing time

- Branch/worktree/base unchanged from registration. HEAD 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Updated legacy ExamCollectionTodoTest to verify completion by generation key and all-assignee cancellation.
- ExamCollectionPage reload refreshes the entire loaded page range, replacing stale list projections while retaining selection. Additional-page requests still append. Failed refreshes preserve rows; retries refresh the same range. No backend contract changes.
- PmsKnowledgeSharePage retains original body images and links. Modern browsers display cloned preview triggers; unsupported Popover CSS branches hide previews and preserve originals in document order. Added controller assertions for originals and linked originals.
- Changed files are the six implementation/test/API-document paths in the registration plus this record. Existing unrelated changes preserved.
- Verification passed. Workbench npm run typecheck. Current changed Java sources compiled with javac against available repository classes/dependencies; JUnit launcher ran 8 tests in ExamCollectionTodoTest and PmsKnowledgeSharePageControllerTest, all passed. This was a focused current-source run, not a full Maven reactor build.
- Browser fixture on isolated port 19879 passed. Forty rows loaded, detail refresh requested pages [1,2], first task updated to COMPLETED with 2/2 progress, selection retained. Refresh failure retained all forty rows; retry recovered. Normal image preview opened. Desktop/mobile (390px) fallback CSS branch preserved both inline originals and the linked original, hiding triggers/preview copies. Fallback branch simulated by feature-query substitution in generated fixture HTML; no old-browser engine was available. Screenshots inspected. Fixtures and command arguments under artifacts/review-fixes-20261109. Scoped git diff --check passed.
- Integration impact None. No database migration or shared external-state changes. Real-account acceptance and deployment remain unverified. Ownership released.
