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

## Registration update — 2026-10-30T09:53:03.089461+08:00
- Owner /root resumes backup workstream; environment test from /etc/zsjos/agent-environment; branch main; worktree /opt/zsjos; base/HEAD ba8c788f470eb96d22e8729a2d8f711d83c0c370; target branch/integration None.
- Goal: user-requested latest configured zsjos database backup to backup/. Ownership: new timestamped archive and inventory/checksum/verification sidecars, pending/latest pointers and this record. Preserve existing backups and all unrelated concurrent work.
- Non-goals: data cleanup/mutation, database restoration, services, application/SQL source changes, Git operations.
- Dependencies: existing zsjos-mysql-1 MySQL 8.4.11 and secret mount; 574 InnoDB tables, zero views/routines/triggers/events; 53 GiB free. Stream single-transaction utf8mb4 dump through gzip.
- Verification: dump exit/stderr, full gzip CRC/UTF-8, exact table inventory and database scope, completion/data markers, SHA-256 readback, permissions; no restore rehearsal. External uploaded files/system accounts excluded.

## Delivery — 2026-10-30T09:55:16.617820+08:00
- Context: registration update above; owner /root; test/main /opt/zsjos; HEAD unchanged ba8c788f470eb96d22e8729a2d8f711d83c0c370; dependency/integration impact None.
- Latest backup: /opt/zsjos/backup/zsjos-test-20261030T015303Z.sql.gz; 307874103 compressed bytes / 4829517140 uncompressed bytes; 574 tables. Single-transaction utf8mb4 export with routines/events/triggers enabled (none present).
- PASS: mysqldump exit 0/empty stderr; full gzip CRC and strict UTF-8; exact database/table inventory, data statements and completion marker; SHA-256 9735b2dfd3983513db5171280bece09e5a5b581bec448757d646df77b1d18f10 and independent sha256sum -c. Private directory 0700/archive 0600.
- Files: new archive, inventory/checksum/stderr/verification sidecars, latest pointer and this record. Previous backups and unrelated work preserved. No database mutation, restore, service lifecycle, commit or push.
- Limits: no restore rehearsal; external uploaded objects and MySQL system accounts excluded. Latest backup complete; remaining work None; ownership released.

## Registration update — 2026-10-30T13:29:38.963227+08:00
- Owner /root; environment test; main /opt/zsjos; HEAD unchanged ba8c788f470eb96d22e8729a2d8f711d83c0c370; target branch/integration None.
- User explicitly requests latest database backup and cleanup of previous backups. Scope: new backup/ timestamped archive and sidecars, backup/refresh-20261030T052938Z private fixed cleanup manifest/result, latest pointer and this record; delete only three previous complete archives (20261026T015814Z, 20261026T100925Z, 20261030T015303Z) plus their four sidecars each, after new archive verification.
- Exact impact: 15 regular files, 824255846 bytes permanently removed; no copy of old restore points created. Preserve repair/cleanup evidence, all other paths, concurrent work and database data. No services or Git operations.
- Dependencies: configured zsjos on zsjos-mysql-1, MySQL 8.4.11; 574 InnoDB tables, no views/routines/triggers/events; 53 GiB available.
- Verification: single-transaction utf8mb4 export exit/stderr; full gzip CRC/UTF-8, table/database inventory, completion/data markers, SHA256 readback, private permissions; recheck all frozen file stats before exact unlink; retained file comparison and deletion journal. No restore rehearsal; external files/system accounts excluded.

## Delivery — 2026-10-30T13:31:44.201023+08:00
- Context: registration update above; /root; test/main /opt/zsjos; HEAD unchanged ba8c788f470eb96d22e8729a2d8f711d83c0c370; integration/dependency impact None.
- New archive /opt/zsjos/backup/zsjos-test-20261030T052938Z.sql.gz; 311213737 compressed / 4887868206 uncompressed bytes; 574 tables. Export exit 0, empty stderr, full gzip CRC/UTF-8, exact table/database inventory, data/completion marker, SHA256 477f9b4d1199780dbe88961a4e1599cc7f8a5907a11df671d3ea2d1d0c7dbb7a and independent checksum readback PASS; archive 0600/directory0700. Latest pointer updated.
- Deleted precisely 3 old archives and 12 sidecars (15 files, 824255846 bytes); frozen metadata guards and flushed per-file journal; all 6 protected files unchanged. backup/ now has only the latest full archive. Other directories and repair evidence preserved.
- Files: archive and sidecars, latest/pending-cleanup pointers, backup/refresh-20261030T052938Z manifest/journal/result, this record. All unrelated work preserved. No database writes, service lifecycle, commits or pushes.
- Limits: no restore rehearsal; external uploaded objects and MySQL system accounts excluded. Removed historical restore points have no newly created copy. User-authorized backup/cleanup complete; ownership released.
