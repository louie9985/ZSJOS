# Local database backup cleanup — 2026-10-12

## Registration
- Workstream: local-db-backup-cleanup-20261012; owner: Codex /root.
- Environment: local (ZSJOS_AGENT_ENV unset; environment marker absent).
- Branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Target branch / integration order: None.
- Goal: inventory local MySQL backups and retained validation schemas; prepare an exact, recoverable cleanup for user approval.
- Non-goals: application changes, business-data repairs, permission changes, service lifecycle operations, Git operations, binary-log purging, deleting original recovery evidence.
- Ownership: this handoff file; backups/mysql/cleanup-20261012/ inventory, plan and recovery artifacts. Database deletion is pending explicit scope approval.
- Dependencies: local Docker yudao-mysql; existing repair test scripts and recovery documentation; preserve concurrent work and all schemas outside the approved exact list.
- Verification: source provenance, schema/table inventory, active connections, physical allocation; before deletion export every approved schema with routines/events/triggers, verify archive hashes and readability, recheck target scope and active sessions; after deletion verify exact absence and protected database/schema/evidence preservation.

## Inspection / pending approval
- MySQL 8.4.11; 558 schemas. Live application connections observed only on ruoyi-vue-pro.
- Identified 172 retained repair-test schemas from V304–V310, provider-identity tests and repair replay tests. Exact names and accounting are in backups/mysql/cleanup-20261012/plan.md and candidates.json.
- Preserve ruoyi-vue-pro, ptml, system schemas, all other unapproved schemas, all 16 backup/evidence tables inside ruoyi-vue-pro, and all 35 existing files under backups/.
- No database or existing backup file has been deleted. Bulk DROP DATABASE requires explicit authorization under root AGENTS.md section 4 and local database rules.

## Prepared recovery — pending deletion approval
- Candidate directories allocate 10,478,501,888 bytes (9.759 GiB); MySQL data/index estimate is 6.710 GiB. Binary logs allocate about 9.973 GiB and remain out of scope.
- recovery.sql.gz exported all 172 candidate schemas, including data, routines, events and triggers. mysqldump exited 0; full gzip decompression/CRC and dump-completion trailer verified.
- Archive: 450,491,742 bytes; uncompressed: 5,820,022,547 bytes. SHA-256: 11093e7f178e63255b9f1eedec395a65fc0bec9a5f1fa808db20b68c35664d21.
- Archive database-section names match all 172 candidates exactly, excluding protected schemas.
- No cross-schema foreign keys or enabled events were returned by information_schema checks. Observed target connection during export was this backup process; application connections remain on ruoyi-vue-pro.
- Restore rehearsal has not been performed; no schemas or existing backup files have been deleted. Source code, permissions, services and Git state were not changed.
- Await explicit authorization to delete only the 172 exact candidates after refreshing preflight. Preserve every unlisted schema and all existing recovery evidence.

## Authorization / execution update
- User explicitly confirmed deletion of the reviewed 172-schema list in this chat. Approval covers local Docker yudao-mysql only, with the existing recovery archive and all documented exclusions.
- Ownership now includes the exact candidates in candidates.json and the guarded execution/audit artifacts in this workstream directory. No expansion to other schemas, backup tables, binary logs or existing files.
- Reuse the registration above. Recheck backup SHA-256, schema/table inventory, recent writes and active sessions; record each successful deletion and verify protected schemas and evidence afterward.

## Delivery — 2026-10-12
- Executed the user-confirmed cleanup with execute_cleanup.py --apply-confirmed-172. Preflight matched the fixed recovery SHA-256 and all 3608 candidate table definitions to the archive; no active candidate connection, cross-schema foreign-key dependency, enabled candidate event or table update newer than the archive was found.
- Deleted exactly 172 schemas and recorded each successful DROP in deleted.jsonl. Afterward 386 schemas remain; their set equals the original set minus the approved candidates exactly.
- ruoyi-vue-pro retains its 552-table inventory. CHECKSUM TABLE EXTENDED for the 16 backup/evidence tables and both migration version tables is unchanged.
- MySQL data-directory allocation fell from 27,506,600 KiB to 17,273,820 KiB (approximately 9.76 GiB). Binary logs were not purged; no service, Git or Docker disk-compaction operation was performed.
- All 35 pre-existing backup files remain (3,650,794,989 bytes). Retained recovery.sql.gz (450,491,742 bytes) plus manifest, backup verification, execution preflight, per-schema journal and cleanup result in backups/mysql/cleanup-20261012/.
- Recovery archive hash, full gzip integrity and exact schema sections were verified; restoration rehearsal was not performed. Windows Docker virtual-disk file shrinkage is not claimed.
- Authorized scope complete. No application or migration source changes, no business/permission changes, no unrelated cleanup. Ownership released.

## Second cleanup registration update — 2026-10-13 Asia/Shanghai
- User confirmed execution of the reviewed 364-schema cleanup. Owner Codex /root resumes this workstream; environment local; branch main; worktree D:/ZSJ-OS; current base 45f1edbe24b316b5f5924416c4fc18977658ff5f. Target branch/integration: None.
- Scope: this record; backups/mysql/cleanup-20261012-round2/ artifacts; only the 364 previously reviewed schemas. Preserve 20 unresolved schemas, ruoyi-vue-pro, ptml, four system schemas, existing backup files and binary logs. Newly created schemas are outside approval.
- Reconstruct the approved set from the original inventory, minus the first 172 deleted schemas, plus the four explicitly reviewed order_actors_v312 schemas, minus the 20 unresolved schemas. Freeze exact names before backup.
- Verification: export data/schema/routines/triggers/events; verify gzip, SHA-256 and exact database sections; compare archived/current table definitions; recheck activity and dependencies; journal exact deletion and verify all unapproved schemas and business evidence. No service, code, permission, branch or migration changes.

## Second cleanup delivery — 2026-10-13 Asia/Shanghai
- Deleted exactly the approved 364 schemas, verified against frozen manifest SHA-256 b17630a29c9bbe3655f8631b015c11b2a435ad47033e2b866a9fc30b4a712d92 and the per-schema journal. Snapshot contained 435 schemas because 45 new schemas had appeared after review; those were preserved.
- Remaining 71 schemas: 20 unresolved, 45 newly created/unapproved, ruoyi-vue-pro, ptml and four system schemas. No unapproved schema is missing and no approved schema remains.
- Recovery archive: backups/mysql/cleanup-20261012-round2/recovery.sql.gz; 19,975,550 bytes compressed / 146,072,017 uncompressed. SHA-256 a8e00caf4d93d29fe21cba4298a18216b0221c48ffa59f8680e3f9318f93b72a. Full gzip integrity, completion marker and exact 364 database sections verified; no restore rehearsal.
- Initial preflight stopped before deletion because the object count included nine synthetic failure-test views. Corrected the local verifier to parse and compare view definitions as well as tables; all 15,594 tables and nine views matched the archive before execution.
- Activity, newer table writes, cross-schema foreign keys/views/stored objects, enabled events and configured datasource references checked. Protected evidence/version checksums and business table inventory were unchanged afterward.
- Data directory decreased from 17,835,728 KiB to 15,013,480 KiB, approximately 2.69 GiB. Existing backup files preserved; archive SHA rechecked after deletion. No binary-log purge, runtime change, permissions change, Git operation or Docker disk compaction.
- Exact manifest, preflight, deletion journal, backup verification, result and restoration instructions retained in the round2 directory. Approved cleanup complete; ownership released.
