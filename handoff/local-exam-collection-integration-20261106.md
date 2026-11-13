# Exam collection integration into main

- Environment local, ZSJOS_AGENT_ENV unset and existing local context confirmed.
- Owner Codex /root/backend_acceptance, coordinated by /root. Workstream local-exam-collection-integration-20261106.
- User authorized saving current main business changes first, then merging codex/exam-material-collection into main.
- Worktree D:/ZSJ-OS, branch main, base e23674d600. Feature source 2ade4d8b39.
- Scope existing completed media contribution, follow-up retention, lead selection card and order association fixture changes, followed by feature merge conflicts and directly required migration renumbering/verification. Their handoffs release ownership.
- Excludes generated build metadata, bytecode caches, private reports, temporary artifacts and unrelated untracked files. No push/deployment/application database migration.
- Integration order current main save commit, then feature merge commit. Preserve main identity-area V362 and renumber undeployed exam migration V363 with isolated execution evidence.
- Verification scoped diff, semantic conflict review, Workbench/H5 builds, relevant Java tests/compile, isolated migration initial/replay/failure recovery. Reuse prior unchanged business validation recorded in their handoffs.

## Integration progress
- Main completed changes saved as 93ae3770d2 (24 scoped files). Reviewed released workstream records and preserved generated/unrelated files unstaged.
- Feature 2ade4d8b39 merged with --no-ff --no-commit without textual conflict. Semantic collision resolved by retaining identity-area V362 and renumbering undeployed exam migration V363, prerequisite V362 in both ledgers. Updated verification/runtime preparation paths and current rollout documentation; historical feature evidence retains original numbering.
- Added identityAreaSnapshot to restoration business-value comparison and regression case, preserving main's identity-area provenance when visible labels match across revisions. Main order-lead association/identity reuse, follow-up retention and contribution logic retained.
- Workbench build (including tsc) and H5 build (including vue-tsc) passed. Frontend read-only reviewer confirmed shared api.ts retains associatedLeadId, leadProfile.submittedAt and source candidate routes while adding exam action.
- V363 executed in isolated MySQL prefix exam_collection_verify_20261106112559234541: initial/replay/prerequisite and ledger failures/partial recovery/index and column recovery/uniqueness/current-read race/Chinese HEX/summary behavior passed. Source V362 ledger exists; source application menus, grants and ledgers unchanged. No main database migration.
- Maven reactor relevant tests running in owned session45676; final result recorded before merge commit.

## Verification and integration delivery
- Broad Maven selection compiled the complete ZSJOS module and ran 645 tests, with 2 failures, 5 errors and 20 skips. One integration assertion expected the old archive limit of 33 fields; updated that test to preserve registration limit33 and allow archive limit34 including wechat, rejecting35.
- Remaining six broad-selection failures are in unchanged main files: StudentInfoAttachmentValidatorTest old template32/33 assertion; StudentDeliveryParticipationMapperTest two H2 schema gaps (media_ready/teaching_closed); StudentServiceObjectPermissionProviderTest two null mocks; StudentArchiveExtraControllerTest original authorization fixture. Their implementation/tests have no merge diff and were preserved.
- Final focused Maven reactor succeeded: 251 ZSJOS tests plus 4 Infra file-path tests, zero failures/errors/skips. Covers exam collection, archive restoration/provenance, identity-area/form/reuse, summary, media contribution, sales-order/follow-up retention and compensation.
- Workbench 24 focused tests and H5 8 validation tests passed. Both production builds include successful type checks. V363 isolated database result above remains valid; migration numbers are unique with V361 student-summary, V362 identity-area, V363 exam-collection.
- Original main pending metadata and unrelated untracked files remain local and excluded. No branch push, deployment, real role changes or main database migration performed. Integration ownership released after the merge commit.
