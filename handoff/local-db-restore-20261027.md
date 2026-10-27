# local-db-restore-20261027

- Environment: local (environment variable unset; environment file absent).
- Status: active; Owner: Codex root.
- Goal: Replace local yudao-mysql / ruoyi-vue-pro using D:/zsjos-test-20261026T100925Z.sql.gz, explicitly authorized by user.
- Non-goals: Remote changes, password reset, migrations, application/service changes.
- Branch: main; Worktree: D:/ZSJ-OS; Base/HEAD: 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Ownership: handoff/local-db-restore-20261027.md; external D:/zsjos-local-db-restore-20261027/; local target and restore_stage_20261027 schema.
- Dependencies: Local Docker MySQL 8.4.11; no application connections or backend listener observed.
- Target branch / integration order: None.
- Verification: gzip integrity, full backup, isolated fail-fast import, all table counts/checksums/DDL and migration ledgers, UTF-8 HEX and tenant audit.
- Order: backup; isolated restore; verify; recreate only target database; move verified tables; compare. Existing source changes preserved.
- Repeatability/recovery: Snapshot replaces complete target including system tables; repeat from retained source. DDL is nontransactional; full compressed rollback backup retained externally. No automatic migrations.

## Progress and authorized recovery

- Original archive gzip checked; isolated import succeeded after Docker connection interruption. 572-table row/count/checksum/DDL reference saved in external stage.json.
- Full rollback backup: D:/zsjos-local-db-restore-20261027/ruoyi-vue-pro_before_restore.sql.gz; gzip integrity verified; SHA256 bc2826c016cd07846ac3ee98b8c213f1eaf0275ff9ba890b4b851294bfc00871.
- Replaced local target using verified stage tables. Final read-only verification interrupted by Docker client memory allocation failure; retry then blocked by MySQL/WSL unresponsiveness. No result.json success marker exists.
- User explicitly authorized MySQL container restart, then Docker Desktop restart. Both encountered WSL-layer blocking; broader WslService restart requires additional user authorization. Backend was not started.

## Delivery status 2026-10-27T10:23:10.584516+08:00

- Status: replacement executed; final verification blocked; work remains pending.
- Branch/worktree/HEAD: main / D:/ZSJ-OS / 8279212fb97129ba6b006dbb0452ad843dfde4b5 (unchanged).
- Result: Original local schema backed up and replaced by 572 independently restored snapshot tables. Final all-table equality check did not complete because Docker client memory failure and subsequent WSL unresponsiveness prevented database access. Do not treat this as verified completion.
- Verification: source gzip and rollback gzip integrity passed; isolated import passed; all source table counts, extended checksums and DDL captured. Final comparison, migration summary and UTF-8 HEX remain pending.
- Changed repository files: handoff/local-db-restore-20261027.md only. External artifacts: D:/zsjos-local-db-restore-20261027/.
- Recovery: user authorized MySQL and Docker Desktop restart; WSL service restart is awaiting separate approval because it affects all WSL distributions. Docker engine currently unavailable. Backend not started.
- Remaining: restore Docker availability, run python -B D:/zsjos-local-db-restore-20261027/verify.py, inspect result.json, then record successful completion. Retain backup regardless of verification result. No migrations/password resets/remote writes performed.
- Integration impact: None.

## Recovery delivery 2026-10-27T10:38:33.020778+08:00

- Status: verification remains blocked; replacement previously executed. HEAD/branch/worktree unchanged from registration.
- User authorized WslService restart affecting all WSL distributions, then explicitly authorized deleting only the stale Docker engine.sock temporary socket.
- WslService restart required elevation. Graceful stop hung; elevated force restart completed (wsl-restart-result.txt = WSL_RESTART_OK), and WSL distribution listing recovered. Docker Desktop was relaunched.
- Docker startup log identified Secrets Engine failure removing C:/Users/EDY/AppData/Local/docker-secrets-engine/engine.sock. Read-only inspection confirmed a zero-byte reparse-point file.
- Cleanup command was rejected by automatic approval with reason blocked by policy, both before and after explicit user authorization; deletion did not execute. No alternate deletion mechanism attempted.
- Changed repository files: this handoff record only. External recovery scripts/results in D:/zsjos-local-db-restore-20261027/. No further database changes or migrations.
- Remaining: user must exit Docker Desktop, remove only the identified temporary engine.sock, reopen Docker Desktop, then resume verify.py. Full all-table equality and UTF-8/version summary remain unverified. Original rollback gzip and 572-table reference retained.
- Integration impact: None; backend not started.

## Registration update 2026-10-27T11:00:05.541246+08:00

- User confirmed Docker factory reset and explicitly authorized rebuilding local MySQL/Redis and restoring the supplied snapshot. Engine now has zero containers/images/volumes; prior restored target no longer exists.
- Scope extends to new local yudao-mysql and yudao-redis containers and their dedicated volumes; credentials read privately from existing script/docker/docker.env. Restore only supplied database, backend remains stopped. Use MySQL 8.4.11 matching prior engine; limit MySQL container memory to 2 GiB to reduce host memory pressure.
- Verification reuses unchanged stage.json (572-table exact counts/checksums/DDL) captured from successful isolated source import; compare newly restored database against it, and verify UTF-8 HEX and migration/tenant summaries. Preserve original and rollback archives.

## Recovery delivery 2026-10-27T11:25:17.398065+08:00

- Status: local MySQL and Redis rebuilt after user-confirmed factory reset; supplied database restored; V351 disposition awaits user decision. Branch/worktree/HEAD unchanged from registration.
- User explicitly authorized recreating services and restoring data. Created yudao-mysql (mysql:8.4.11, 3306, 2 GiB memory limit, 3 GiB memory+swap) and yudao-redis (redis:6-alpine, 6379), with dedicated yudao-system_mysql / yudao-system_redis volumes and unless-stopped policy. Existing docker.env supplied MySQL credentials privately; backend not started. Redis cache is fresh after factory reset.
- Official Docker Hub pulls encountered intermittent EOF. MySQL pull succeeded. Redis official linux/amd64 manifest/config/layers fetched through host HTTPS, digest checked, loaded as redis:6-alpine; provenance retained in redis-image-source.json. Redis PING returned PONG.
- Imported unchanged prepared source transport with only USE database redirection into ruoyi-vue-pro; import passed. Original source archive and rollback archive retained.
- Full comparison: 569/572 tables match exact counts, extended checksums and DDL. The other three contain precisely one new menu record and two V351 ledger records, installed 2026-10-27 11:11:45 Beijing by an operation outside this restore script. New menu 602395 has zsjos:sales-order:query-all and zero role assignments. No V351 menu/ledger insert exists in supplied archive.
- Isolated proof: copied the three tables to restore_stage_20261027 excluding only these V351 additions; all three row counts and checksums exactly match source stage.json. Proof saved as v351-difference-proof.json. Production/local target additions preserved pending user decision; no automatic migration or deletion performed by this workstream.
- Data checks: 6211 leads and 845 orders, all tenant 1. Snapshot-preserved users: 91 tenant-1 plus 5 historical non-tenant-1 rows; no tenant cleanup. Connection utf8mb4; system menu HEX E7B3BBE7BB9FE7AEA1E79086. Current module versions core V351 (source V350), eam V012, fms V002, hrm V004, payment V241, pms V004.
- Evidence: D:/zsjos-local-db-restore-20261027/stage.json, target.json, current-summary.json, v351-difference-proof.json, rebuild-import.stderr and redis-image-source.json. Strict verify.py deliberately failed on the three V351 differences; do not claim exact snapshot equality.
- Changed repository files: this handoff record only. Integration impact: None; no backend deployment, password reset, real role grant or remote database write.
- Remaining: user chooses preserve V351 or remove only its new metadata/ledger rows to match source V350 exactly. Isolated three-table validation schema retained pending decision; rollback backup retained permanently for this operation.

## Final acceptance 2026-10-27T11:53:17.217332+08:00

- Status: completed; ownership released. HEAD/branch/worktree unchanged from registration. User explicitly chose to retain V351 and accept its difference from source V350.
- Restore baseline accepted: 569 tables matched source counts/checksums/DDL; isolated exclusion checks proved the other 3 differ only by the V351 menu and two ledger additions. All 572 tables accounted for.
- Final live observation: backend port 48080 is now listening (PID 26400), Quartz scheduler registered, audit writes through 11:52 Beijing; 17 tables differ from initial restore verification, including Flowable/Quartz, notifications, audit/events/tasks, lead/public-sea/cashback and media snapshots. These subsequent writes were preserved; no claim that current live data remains byte-identical to source. All current table definitions match accepted restore except auto-increment advancement.
- Current V351 menu and both ledgers verified present; representative Chinese HEX passed; Redis PING returned PONG. Final result: D:/zsjos-local-db-restore-20261027/result.json. Restored source and pre-replacement full rollback archive retained.
- Changed repository files: this handoff only. No new data writes, service restarts, migrations, permission grants or password resets in this acceptance turn. Integration impact: None.
- Retained diagnostic artifact: restore_stage_20261027 contains only the three isolated comparison tables, not used by application. Remaining requested work: None.
