# Order actor repair workstream

- ID: local-order-actors-20261012; environment: local (no explicit override/file); status: active.
- Owner: current Codex chat; branch: main; worktree: D:\ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Goal: implement the user-approved fixed tenant-1 order actor and snapshot repair and execute the same verified SQL locally.
- Non-goals: production, permissions, BPM history, customer ownership/source, unrelated financial facts, deleted orders, other workstreams.
- Scope: V312__order_actor_snapshot_repair.sql; tools/order_actor_repair*.py and order_actor_repair_manifest.json; SalesOrderDO.java; SalesOrderSnapshot.java; new OrderActorRepairSnapshotTest.java; docs/operations/order-actor-snapshot-repair.md; docs/api/zsjos-sales-order.md; this record. Private evidence: backups/mysql/order-actors-v312-*.
- Existing SalesOrderDO edits preserved; prior history-repair ownership released. V312 unoccupied at registration.
- Dependencies: applied V311 and earlier repair ledgers; existing MySQL 8 and PyMySQL. No new dependencies. Target branch/integration order: None.
- Verification: fresh prerequisite schema and isolated local clone, first/repeat/failure/partial/conflict/ledger/rollback; whole-table preservation; actual Java reader; local execution only after isolated validation. Authenticated UI separately reported.
- Approved exceptions: retain account 400; restore one order and its ORDER attribution to account 30 but preserve Lead; 23 old-name mappings retained; 15 missing names filled from approved current accounts; actual BPM starter retained.

## Delivery — 2026-10-12 19:31:12 +08:00

- Branch/worktree/base: unchanged from registration; HEAD c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Status: completed; ownership released.
- Result: V312 source, fixed 95-order identifier/hash manifest, generator/preflight/apply/verify/isolated-test/local-evidence tools, additive order snapshot DO/reader, focused Java tests, API and operations documentation implemented. Existing edits preserved.
- Local execution: same tested SQL committed with both ledgers. Orders changed 37 (submitter IDs 20, formal IDs 4), rounds changed 64 (51 historical submitter references), attribution changed 1. Missing names 15, preserved alias orders 23, deleted orders 109 unchanged. No new approval rounds. Approved Lead/true-BPM exclusions retained.
- SQL verification: isolated MySQL prerequisite, preimage conflict, transaction/second-ledger injected failure rollback, first/repeat, partial-before recovery with ledgers, later-edit rejection, rollback rehearsal and empty target repeat passed. Full target-row/JSON scope comparisons passed. Local UTF-8 HEX, semantic verification and immediate zero-write replay passed.
- Java: Maven 21-module build succeeded; 11 tests, no failures/errors/skips; includes 37 actual MySQL snapshot projections through production reader. Both frontend personnel consumers inspected; no response/interface change.
- Evidence: ignored backups/mysql/order-actors-v312-20261012192247 and order-actors-v312-local-20261012192901; SQL SHA256 a01992ecff592c4960b5550a79a7b690051dac38c70c67233c8f02a7c031a7e8. Full 552-table checksums: expected writes plus concurrent Quartz heartbeat/business-audit activity; time-window audit confirms scheduler/heartbeat actions. Other protected tables unchanged.
- Implementation verification repairs: JSON typed image generation corrected after isolated rollback; BIT-byte test comparison corrected; private snapshot serialization corrected before local SQL execution. Post-apply protected-table strict check stopped on concurrent service activity; verified business result and classified external activity using saved evidence, without regenerating repair or rewriting source SQL.
- Changed files: owned source paths from registration; new tools include order_actor_repair_sql.py, order_actor_repair_verify.py, order_actor_repair_test.py, order_actor_repair_local.py. No baseline or previous applied migration edits.
- Remaining limitations: no service restart or authenticated UI acceptance; new personnel fallback requires updated backend runtime. Repository-wide migration check fails on pre-existing concurrent Lead appeal schema/baseline differences, not V312; full fresh production chain unverified. No production, permission, branch, commit or push actions. Seven old importer references in the explicitly retained alias group remain outside the confirmed 51-reference repair.
