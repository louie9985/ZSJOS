# Sales-only performance organization configuration

## Registration - 2026-10-15
- Environment: local (environment variable/file absent). Owner: current chat /root.
- Branch: main; worktree: D:/ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184; integration: None.
- Goal: user authorized removing student delivery from sales performance configuration.
- Ownership: this handoff; sales-only scope paragraph in docs/api/sales-performance.md; script/sql/mysql/repairs/remove_delivery_performance_org_20261015.py; tenant 1 zsjos_performance_org rows 4/5 (departments 1050/1051). Preserve existing documentation edits.
- Non-goals: System departments, permissions, historical facts/targets, weekly targets, frontend changes, deployment and Git publication.
- Dependencies: configured application-local master database; existing logical-delete filtering. Remote write explicitly authorized by user follow-up to the exact-target diagnosis.
- Verification: scoped backup outside repository; temporary MySQL execution/repeat/rollback; guarded transaction, exact affected count, remaining mappings and enabled sales membership; scoped documentation diff. No migration ledgers affected.

## Delivery - 2026-10-15 17:57 Asia/Shanghai
- Branch/worktree/base unchanged from registration; current HEAD 315efd729f2e049fc2639194567d86077ae91184.
- Result: configured application-local master transaction committed exactly two logical deletions (tenant 1, org ids 4/5, departments 1050/1051); version advanced 0 to 1. Independent fresh connection confirmed deleted=1 and only sales department 1021 remains active under center 1020.
- Preserved: sales mapping byte-for-value comparison passed; System departments 1021/1050/1051 still exist with correct UTF-8/HEX names; all ten enabled sales-post users remain in department 1021. No System, permission, target or historical business rows were updated.
- Backup: D:/ZSJ-OS-backups/performance-org/20261015-175622-122742-delivery-org.json (scoped configuration preimage; restore requires current-state review and authorization).
- Changed files: repair script and this record; four-line sales-only scope addition in docs/api/sales-performance.md, preserving pre-existing edits.
- Evidence: temporary MySQL table exercised first application, zero-row repeat and rollback recovery; guarded source execution affected two rows and repeat affected zero; post-commit independent read passed; scoped documentation diff check passed.
- Integration/dependencies: None; no migration ledger, service restart, build, commit or push required/performed. Existing server tree reads active configurations on request.
- Limits: authenticated browser/API acceptance was not performed; reload page to fetch updated organization tree. Weekly targets and missing-target presentation remain outside this request. Ownership released.
