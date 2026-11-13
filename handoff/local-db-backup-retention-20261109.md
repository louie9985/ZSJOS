# Database backup retention

## Registration 2026-11-09
- Environment local, fallback after absent ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment.
- Owner Codex /root, branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Goal backup only before pending database changes, compressed verified latest-only retention, temporary MySQL anonymous volume cleanup.
- Scope script/sql/mysql/tools/zsjos_db.py, script/sql/mysql/tools/test_backup_retention.py, docs/operations/production-deployment.md, this record.
- Non-goals production execution, existing remote backup/volume deletion, migrations, unrelated dirty files, commits and publishing.
- Dependencies existing migration lock and mysqldump. Target branch and integration order None.
- Verification focused Python regression tests and isolated Docker anonymous-volume lifecycle check.

## Delivery
- Implemented pending-change backup gating under migration lock, streaming gzip, full decompression CRC and dump completion-marker validation, atomic promotion, same-database managed backup retention. Failed backups preserve the prior backup and block migration.
- Temporary MySQL cleanup explicitly requests anonymous-volume removal. Production Compose uses a fixed named backup volume, so repeated backup files accumulate inside that volume. Existing production orphan-volume provenance remains unverified. Existing --rm already requests auto cleanup; the missing explicit --volumes alone does not prove the source of production orphan volumes.
- Seven regression tests passed. Isolated local Docker mysql:8 create/remove lifecycle confirmed its anonymous volume was removed. Scoped diff checks passed.
- Production deployment, production volume inventory and cleanup, real mysqldump/restore acceptance remain unperformed. Verification is mocked for dump contents and migration gating.
- Ownership released.
