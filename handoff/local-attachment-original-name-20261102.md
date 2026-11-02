# Attachment original names — 2026-11-02
- Environment: local (environment variable and /etc marker absent).
- Workstream/owner: attachment-original-name-20261102 / current Codex chat.
- Goal: persist original attachment names for collection/archive uploads and reuse; retain UUID encrypted storage and historical fallback.
- Non-goals: historical reconstruction, storage changes, frontend redesign, service restart, commit/push.
- Branch/worktree/base: main / D:/ZSJ-OS / 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Ownership: StudentInfoImageService.java, StudentInfoImageDO.java, new StudentInfoOriginalNameTest.java; V360__student_attachment_original_name.sql; new test_student_attachment_original_name.py; docs/api/student-information-collection.md; this record.
- Dependencies: existing uncommitted attachment reuse implementation is preserved; additive migration before matching backend. User authorized scoped schema update to local-config database 192.168.2.17 / ruoyi-vue-pro.
- Verification: focused service tests; MySQL initial/replay/partial/failure/recovery, UTF-8 and existing-row preservation; frontend name consumer inspection.
- Integration target/order: None.

## Delivery
- Added nullable original_name to attachment records via V360 upgrade; historical SQL left intact for existing environments. New public/archive uploads persist a sanitized basename; list/download metadata uses it, with historical generated-name fallback. Reuse copies the original-name metadata. UUID encrypted object naming is unchanged.
- Existing uncommitted reuse logic and unrelated edits preserved. H5 CollectionImages, Vue RegistrationStudentInfo, React StudentInfoPanel/StudentArchivePanel already consume Image.name; API shape unchanged.
- Verification: Maven focused reactor test completed with exit 0, StudentInfoOriginalNameTest 2/2 and StudentInfoImagesAndRulesTest 7/7. Tests cover public/archive uploads, reload projection, reuse, independent UUIDs for identical names, legacy fallback and client path/control stripping.
- MySQL 8.4.11: initial/replay, partial DDL, missing prerequisite, incompatible column, ledger-write failure and recovery passed. UTF-8 values and HEX checked. Isolated fixture databases attachment_name_20261102153113_* retained. First fixture-only attempt used a nonexistent ledger id ordering; corrected to version before rerun; no business-database changes occurred in that failed attempt.
- Authorized local-config database migration applied twice; all 12 existing records unchanged; both ledgers and Chinese metadata HEX verified. Structural/ledger preflight backup retained under backups/mysql/attachment-name-20261102153113.json (no business payload).
- No application restart/deployment, real-account upload, live browser or COS write was performed. Running backend needs matching new build before new uploads show original names. UI rendering code unchanged; browser acceptance remains pending with updated backend.
- Scoped diff check passed. No commit/push. Ownership released.
