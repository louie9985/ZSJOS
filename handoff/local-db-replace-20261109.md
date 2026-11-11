# Local database replacement 2026-11-09

## Registration
- Environment local, ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent.
- Workstream local-db-replace-20261109, owner Codex /root, branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Goal replace local yudao-mysql ruoyi-vue-pro using D:/zsjos-test-20261109T015733Z.sql.gz, then apply missing migrations.
- Ownership this record, unique backup directory backups/mysql/local-replace-20261109, local target database, temporary orchestration outside repository.
- Non-goals source/SQL changes, other databases, Redis data, permissions reconciliation, commits or publishing.
- Authorization user explicitly requested replacement and missing migration execution, and approved pausing/restoring the local backend.
- Dependencies MySQL 8.4.11, repository migration runner and current SQL files. Integration None.
- Plan validate gzip CRC and completion marker, stop local backend PID 6484, create and verify full recoverable backup, replace only target schema mapping dump database name, inspect both ledgers and preflight/checksums, apply missing scripts fail-closed, verify schema/rows/Chinese HEX, restore backend and check health.

## Delivery
- Input gzip fully decompressed with CRC verification and mysqldump completion marker. Source MySQL 8.4.11, source database zsjos, 578 tables. Database selection statements mapped explicitly to ruoyi-vue-pro, source file retained.
- Stopped original local backend PID 6484 under explicit user approval. No other runtime was stopped.
- Full original local database backup including tables/data/triggers/routines/events retained at D:/ZSJ-OS/backups/mysql/local-replace-20261109/before-replacement.sql.gz (301878199 bytes). Full decompression CRC and completion marker passed before target replacement. This is a replacement-specific recovery backup, separate from managed latest-only migration backup retention.
- Replaced only local yudao-mysql ruoyi-vue-pro. Restored input successfully without SQL errors. Imported Core V362 and existing eam/fms/hrm/pms/payment ledger history.
- Existing runner manifests own core/eam/fms/hrm/pms. Historical payment entries have no standalone manifest and remain preserved. All owned module preflight/checksums passed, no unexplained drift. Missing migrations were precisely Core V363, V364 and V365. Executed these files in order with UTF-8 and fail-closed SQL execution, then registered their artifact hashes using existing runner logic. No historical ledger reconciliation. No migration source changed.
- First verification invocation could not resolve repository-relative SOURCE inside the container. Retried by recursively expanding the existing verification SOURCE files from the repository before execution. Full verification completed.
- Final 586 tables, Core V365 present in both ledgers, no pending migrations, no blockers, no schema drift across core/eam/fms/hrm/pms. Integrity verifier passed 199 file checks and deferred 188 historical seed provenance entries to their existing SQL checks. Sole active tenant id 1. Exam collection menu Chinese text matched UTF-8 HEX E88083E8AF95E8B584E69699E694B6E99B86. Counts immediately after migration were 6906 Leads and 974 orders.
- Data-grade warnings remain in the supplied dataset. V054 Lead counters and number data, V071 duplicate role-menu rows, V125 student-number compatibility, V157 work-order schema/menu historical check, V188 pending-class snapshot, V208 template layout, lead_category_empty, lead_filter_schemes, person_contact_claim_completeness_v043, registration_exact_default_routes, source_channel_empty, system_area_other_last. These were not repaired within the database replacement scope. Full results in backups/mysql/local-replace-20261109/verification.log.
- Restored original local backend launch command/profile using existing JAR, new PID 17532. Started YudaoServerApplication successfully in 70.337 seconds, port 48080. GET http://127.0.0.1:48080/actuator/health returned HTTP 200 and UP. Startup log had zero ERROR lines at verification time. Business UI/account acceptance was not performed.
- Ownership released. No source edits, commits or publication in this workstream.
