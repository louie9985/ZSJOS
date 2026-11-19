# V378 schema and local migration
- Beijing date: 2026-11-19; environment local (no explicit marker); owner current chat.
- Goal: complete V378 desired/baseline structure, verify, and apply pending local migrations.
- Non-goals: deployment, restart, permission grants, commit/push.
- Branch main; worktree D:/ZSJ-OS; base 21631a8a98a62c41a3c71596a6739e03a535c3b6.
- Ownership: schema/core.sql and 00-bootstrap-schema.sql under script/sql/mysql, docs/operations/database-migrations.md, this record. Preserve existing edits.
- Dependencies: V374–V378; local yudao-mysql/ruoyi-vue-pro.
- Verification: static check, V378 lifecycle fixture, complete fresh chain, backed-up local migration, final schema/ledgers/UTF-8 checks.
- Integration target/order: None.

- Scope update: test_enrollment_agreement.py fixture copy must exclude generated columns; existing SELECT * causes MySQL error 3105 before migration verification.

## Delivery 2026-11-19T13:12:12.090239+08:00
- Branch/worktree/HEAD unchanged from registration. Preserved existing source changes. Ownership released.
- Added V378 agreement table and six nullable form snapshot columns to desired Core and fresh baseline; operational documentation updated. Fixed existing V378 fixture copy to exclude generated columns (MySQL error 3105); migration SQL unchanged.
- Static check, scoped diff check and V378 prerequisite/initial/partial recovery/replay/menu/UTF-8/ledger checks passed in agreement_0dc01ce2a426. Complete fresh chain: 399 migrations, all modules, final schema/ledger/replay/UTF-8/bootstrap guards passed in retained v378_dc70545c; fresh data-grade findings zero.
- Full pre-upgrade backup completed with dump footer: backups/mysql/pre-V374-V378-20261119130933.sql. Runtime check found no Java process. Migration preflight had no blockers or unexpected drift after earlier authorized backup-table cleanup.
- Local ruoyi-vue-pro: V374, V375, V376 applied and recorded in both ledgers. V377 stopped with populated Flowable engine missing ACT_GE_PROPERTY.next.dbid; both ledgers lack V377/V378 and enrollment agreement table is absent. No counter fabricated, no guard bypass, no V378 out-of-order application.
- Remaining: recover original Flowable counter from authoritative history, then resume V377/V378 and compare affected target schema with verified fresh schema. V378 target database application remains blocked; source and controlled execution verified. No service restart/deploy/commit/push.

## Scope update 2026-11-19: V377 UUID correction
- Reacquire ownership for V377 migration, new test_flowable_metadata.py, migrations/README.md, operations database-migrations.md and this record. Goal: explicit reviewed UUID execution mode; preserve unknown/database-generator guards and all schema fingerprints. User authorized fix and continuation. No runtime configuration changes. Verify isolated MySQL refusal/success/replay/rollback cases and local application. Existing V377 not applied to target; deployed artifacts are not reconciled.

## Delivery 2026-11-19T13:22:47.563186+08:00
- Same branch/worktree/base. V377 now requires an explicit operator-verified strong-uuid mode to permit missing next.dbid on a populated engine. Unknown/unset/database mode protection remains; existing counters and schema fingerprint checks preserved. No Java generator changes. Flowable 8 Spring Boot sources default to StrongUuidGenerator and repository contains no override. No application processes/connections at preflight; Java PID 30324 is VS Code Java tooling.
- New isolated MySQL test passed UUID, missing/unknown/database mode refusals, counter preservation and invalid counter refusal, empty engine, replay, ledger rollback/recovery and wrong-schema refusal (retained v377_d98c0b58 fixtures). Static and scoped diff checks passed. Earlier complete fresh-chain evidence reused for unchanged V378/baseline; changed V377 empty/populated branches directly tested.
- Full backup completed: backups/mysql/pre-V377-V378-20261119132101.sql. Applied V377/V378 via authoritative runner under migration lock with explicit UUID session declaration. Both ledgers recorded; pending and unexpected schema drift empty; 205 file-integrity checks passed, historical seed provenance handled separately. All 43 non-property Flowable tables have identical pre/post CHECKSUM TABLE results; next.dbid remains absent; agreement Chinese HEX verified.
- Data-grade verifier retains 14 warnings (previous categories plus V073 planner checklist); not repaired or represented as data acceptance. No restart/deploy/commit/push. Ownership released. Original pending migration goal completed locally.
