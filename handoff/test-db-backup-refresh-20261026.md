# Database backup refresh — test-db-backup-refresh-20261026

## Registration — 2026-10-26T09:58:14.852347+08:00
- Owner: Codex /root; environment test from /etc/zsjos/agent-environment; branch main; absolute worktree /opt/zsjos; base 8279212fb97129ba6b006dbb0452ad843dfde4b5; target branch/integration order None.
- Goal and authorization: user explicitly requests new database backup and cleanup of old backups, continuing the earlier backup/ task.
- Ownership: new private backup/ artifacts, its current-path pointer; old backup/zsjos-test-20261021T075320Z.sql.gz and four sidecars; backups/mysql/zsjos-20261015-133725.sql, zsjos-20261018-135326.sql and its checksum; this uniquely owned record. Existing dirty files, other handoff records and repair evidence remain owned by their existing workstreams.
- Non-goals: Docker-volume backup cleanup (separate completed workstream); repair evidence cleanup; live database writes/restoration; service lifecycle; source/SQL/config changes; Git operations.
- Dependencies: running zsjos-mysql-1 MySQL 8.4.11, existing mounted secret; configured database zsjos. Preflight 572 InnoDB tables, zero views/routines/triggers/events; 74 GiB free.
- Execution: freeze exact old-file manifest; single-transaction utf8mb4 schema/data export directly through gzip; verify before deleting only manifested unchanged old backup files. Permanent deletion removes those historical restore points; no copy of them is created, latest verified archive remains. Preserve all other files.
- Verification: dump exit and stderr, full gzip CRC/UTF-8, exact schema/table inventory, data/completion markers, SHA-256 readback and restricted permissions; immutable-stat guard before unlink, exact deletion and protected-file metadata comparison afterward. No restore rehearsal. External objects and MySQL system accounts are outside scope.

## Delivery — 2026-10-26T10:01:32.972680+08:00
- Context: registration above; test/main /opt/zsjos; HEAD unchanged 8279212fb97129ba6b006dbb0452ad843dfde4b5; integration/dependency impact None.
- Result: latest full logical backup /opt/zsjos/backup/zsjos-test-20261026T015814Z.sql.gz; 253889015 compressed bytes / 3890461197 uncompressed bytes; 572 tables.
- Verification PASS: mysqldump exit 0/empty stderr; full gzip CRC and strict UTF-8; exact database/table inventory, data and completion markers; SHA-256 2a9c98105d80b695f85f5157c141d2c9e9113c798ec607fcc5443da1ead9a2a9 and independent sha256sum -c; directory 0700/archive and sidecars 0600.
- Cleanup: user-authorized removal of exactly 3 superseded database archives and 5 sidecars (8 regular files; 5,163,230,051 bytes, about 4.809 GiB). Fixed inode/device/size/mtime checks before deletion and flushed per-file journal. All 10 protected files retain exact metadata. Repair evidence and Docker-volume backups preserved; latest-path pointer updated only after verified completion.
- Execution correction: normal-user unlink completed five backup/ files, then stopped at root-owned backups/mysql directory. Rechecked verification hash, deletion journal, remaining targets and protected-file metadata; existing sudo used only for three remaining exact manifested unlinks. No directory permissions changed.
- Files: this record; backup/ new archive/sidecars/private refresh-20261026 plan, verifier, journal and result; pointer; 8 manifested old backup deletions. Existing dirty source/frontend/handoff files untouched. No database writes, restore, services, Git commit/push or application changes.
- Limits: no restore rehearsal; external uploaded objects and MySQL system accounts excluded. Deleted historical restore points have no new recovery copy. Latest verified archive retained.
- Remaining work None; authorized task complete; ownership released.

## Registration update — 2026-10-26T18:09:25.670847+08:00
- Owner /root resumes same backup workstream; environment test; branch main; worktree /opt/zsjos; HEAD/base unchanged 8279212fb97129ba6b006dbb0452ad843dfde4b5; integration None.
- User requests obtaining the latest backup again. Scope: new timestamped backup/ archive, inventory/checksum/verification sidecars, latest pointer and this record. Prior cleanup is complete; this run creates a fresh backup and preserves existing files. No database writes, service lifecycle or Git operations. Preserve all concurrent dirty/untracked work.
- Preflight: configured zsjos database on zsjos-mysql-1, MySQL 8.4.11, 572 InnoDB tables and zero views/routines/triggers/events, 78 GiB available. Verification: export exit, gzip CRC/UTF-8, exact table inventory, completion/data markers, SHA-256 readback and restricted permissions; no restore rehearsal.

## Delivery — 2026-10-26T18:11:28.463096+08:00
- Context: registration update above; owner /root; test/main /opt/zsjos; HEAD unchanged 8279212fb97129ba6b006dbb0452ad843dfde4b5; dependency/integration impact None.
- Latest backup: /opt/zsjos/backup/zsjos-test-20261026T100925Z.sql.gz; 262450166 compressed bytes / 4045045903 uncompressed bytes; 572 tables; single-transaction utf8mb4 export includes schema/data and enabled routine/event/trigger export (zero such objects).
- Evidence PASS: mysqldump exit 0 and empty stderr; full gzip CRC and strict UTF-8 read; exact database/table inventory, data statements and completion trailer; SHA-256 ff76bd5281ce4dee4ee5629397f9ffe09180d02d081711670b4e68f2117c69bb independently verified using sha256sum -c. Archive 0600 under private 0700 directory.
- Files: new archive plus inventory/checksum/stderr/verification sidecars, latest pointer and this record. Previous backups and all unrelated work preserved. No database mutation, restore, service operation, commit or push.
- Limits: restore rehearsal not performed; external uploaded objects and MySQL system accounts excluded. Remaining work None; latest backup delivered; ownership released.
