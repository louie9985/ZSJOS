# Notice content capacity

## Registration — 2026-10-12 (Beijing)

- ID/owner: test-notice-content-capacity-20261012; Codex /root.
- Environment: test, resolved from /etc/zsjos/agent-environment; branch main; worktree /opt/zsjos; base cf11407a4b8a2d1485165477ad31c6278f2cec58.
- Goal: User authorized only expanding system_notice.content from TEXT to MEDIUMTEXT in the current test database zsjos-mysql-1/zsjos. Preserve all existing content and other column attributes.
- Non-goals: No frontend/backend logic, validation, permissions, business-row edits, service operation, dependency or Git publication.
- Ownership: this record; migrations/V295__notice_content_capacity.sql, schema/core.sql and 00-bootstrap-schema.sql under script/sql/mysql; script/sql/mysql/tools/test_notice_content_capacity.py; docs/operations/notice-content-capacity.md. Existing unrelated edits in the schema files are preserved.
- Dependencies: Existing MySQL 8.4.11, Core V294 in both ledgers, 36 existing notice rows; current content is TEXT NOT NULL, utf8mb4/utf8mb4_unicode_ci, comment 公告内容. Existing deployed migrations/checksums remain unchanged.
- Verification: Static source check; retained isolated MySQL initial/repeat/partial/prerequisite/ledger-failure cases and >64KB Chinese content; full fresh chain with existing verifier; protected target backup and before/after row/schema comparison, UTF-8 HEX, both V295 ledger records and repeatability. Production writes and service restarts excluded.
- Target branch/integration order: None.

## Delivery — 2026-10-12 17:17 Beijing

- Owner/environment/branch/worktree/base: registration above; HEAD unchanged. Scope delivered and ownership released.
- Result: Applied only V295 to authorized test database zsjos-mysql-1/zsjos. system_notice.content is MEDIUMTEXT, capacity 16,777,215 bytes. Existing 36 notice rows have identical complete-row hashes before/after; all other database column metadata, indexes and older ledger rows unchanged. No application restart or frontend/backend behavior change. Users retaining the editor can retry saving.
- Files: New V295 migration, focused retained-schema MySQL verifier, operational documentation and this record; one content type substitution in each Core schema source, preserving their existing unrelated edits. Regenerated tracked Python cache restored to its originally clean bytes.
- Verification: Six isolated MySQL 8.4.11 cases passed: initial expansion, already-expanded partial state, missing prerequisite, wrong column contract, failed second-ledger write and existing V295 markers with missing expansion. Every case verifies repeatability, recovery, original row/column retention, UTF-8 HEX and successful 90,000-byte Chinese content storage; the initial TEXT case rejects that payload as expected. Failure cases leave success markers unchanged, including when the mysql client continues after errors.
- Full fresh evidence: Retained nc295_1012171421304357_fresh completed bootstrap, 311 pending migrations, core SQL verification and non-empty bootstrap guard. Initial additional whole-schema assertion reported existing unrelated zsjos_registration_class_assignment.category_id nullability drift (desired NOT NULL, migrated nullable). Verifier now reports unrelated drift and asserts affected notice structure; rerun of that comparison on the unchanged completed fresh database passed, including MEDIUMTEXT and both V295 markers. No unrelated schema repair. Baseline/migration inputs were unchanged during this verification.
- Static evidence: bash ./zsjos-db check and scoped git diff --check passed; both Core schema sources byte-identical and valid UTF-8. Migration SHA-256: 6ecebd1e0d55529ce9b930394c66663ab1f94d1d4169c3e4893bdd5144f7b24b. Source metadata matches the controlled and target notice columns.
- Protected backup/evidence: /opt/zsjos-runtime/backups/notice-content-capacity-20261012-171511/ contains notice-and-ledgers.sql, before.json, after.json and verification.json with private permissions. Target repeat execution preserved both V295 records and notice data. Representative persisted content and column-comment HEX verified through utf8mb4 client.
- Dependency/integration impact: Core V295 follows V294; fresh baseline definitions synchronized. No historical migration/checksum reconciliation, dependency, role assignment, service operation, Git commit or publication. DDL may briefly block writes; retain expanded column on rollback rather than narrowing longer content.
- Remaining: User's actual resubmission was not performed on their behalf; database capacity is verified. Unrelated class-assignment nullable-column drift remains. No remaining implementation work for this scoped expansion.


## Integration correction — 2026-10-13 Beijing

- User authorized origin/main integration while retaining cloud migration numbers. Remote V295 is newcomer dispatch and remote versions extend through V312; notice source is now V313__notice_content_capacity.sql, requiring V312 in both ledgers. Test and active operations references follow V313. Earlier entries retain actual historical V295 execution evidence.
- Read-only target check confirms MEDIUMTEXT and notice V295 in both ledgers. No existing ledger/checksum was relabeled and no shared database migration was executed. This deployed old-number state requires a separately reviewed recovery before upgrading; source renumbering does not resolve it automatically.
- Ownership/delivery: coordinated by test-merge-origin-v313 in handoff/test_main.md; original notice feature and data retention contract preserved.
