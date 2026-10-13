# 内容审批账号资料补全

## Registration — 2026-10-13
- Environment: local; ZSJOS_AGENT_ENV unset and /etc/zsjos/agent-environment absent.
- Workstream: local-content-review-profile-20261013; owner: /root.
- Goal: read account profile/diagnosis at submission and add a missing-only migration for existing tenant-1 approval snapshots.
- Non-goals: permissions, BPM instance/state changes, unrelated account fields, remote deployment, service restart.
- Branch: main; worktree: D:/ZSJ-OS; base: 45f1edbe24b316b5f5924416c4fc18977658ff5f; integration: None.
- Ownership: ContentReviewBatchService.java and its focused tests; new ContentReviewAccountProfile.java/test; V316 migration and focused MySQL verification script; docs/api/content-review-batch.md, migration README and dedicated operations document; this record.
- Dependencies: existing account detail values/snapshots and diagnosis label columns. Prior content-review self-selection work delivered and ownership released; preserve its uncommitted changes.
- Verification: focused Java tests; MySQL initial/replay/partial/failure recovery fixtures; scoped local database preflight, backup, apply, UTF-8 HEX and preserved-state comparison. No new dependencies.
- Decision: explicit user request authorizes a new migration and existing approval fill. Existing nonempty values are preserved; repair provenance identifies current-account enrichment, not reconstructed historical facts.

## Delivery — 2026-10-13 17:14 北京时间
- Context: registration branch/worktree/HEAD unchanged; owner /root; ownership released.
- Result: added stored-label account profile projection and submit-time refresh for product_goal, work_format, publish_rhythm and diagnosis bottleneck. New submissions clear stale draft values and legacy problem fallback for these fields; identity and unrelated context remain intact. Added V316 missing-only tenant-1 historical enrichment with field provenance, scoped account/student association checks, transactional updates, optimistic version increment and both ledgers.
- Changed files: ContentReviewBatchService.java; new ContentReviewAccountProfile.java and ContentReviewAccountProfileTest.java; ContentReviewBatchServiceTest.java; V316__content_review_account_profile.sql; test_content_review_profile.py; docs/api/content-review-batch.md; docs/operations/content-review-account-profile.md; migrations/README.md; this record. Preserved all prior concurrent changes.
- Verification: final focused Maven reactor BUILD SUCCESS, 44 tests passed (3 projection + 41 service), including actual submit argument capture; log %TEMP%/content-review-profile-maven-final.log. MySQL initial/replay/partial/prerequisite/ledger-failure checks passed on retained cr316_1013171102_* schemas with business-compatible collation. Static database check and scoped diff checks passed. No UI change, therefore visual/build checks not applicable; real authenticated submission remains unverified until backend deployment.
- Local execution: yudao-mysql / ruoyi-vue-pro; 203 active batch rows inspected, 179 enriched, 683 fields (226 goal, 230 form, 227 rhythm; no new bottleneck). Before backup backups/mysql/content-review-profile-20261013-171134/before.sql. Preserved-state comparison, nonempty snapshot comparison, idempotent replay zero change, UTF-8 HEX and both ledgers passed. Initial collation error rolled back all rows and both markers; explicit comparison collation fixed and all focused fixtures rerun before successful apply.
- Integration/dependency impact: no dependency, permission, schema, workflow-state or service changes; no commit/push. V316 follows V315. Local existing records repaired; new runtime behavior requires backend restart/deployment. No remote database updated; screenshot environment not verified.
- Remaining: deploy backend and V316 in intended remote environment after its preflight/backup; authenticated submission/review acceptance. Missing source data stays empty by design.
