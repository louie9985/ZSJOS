# Migration renumbering - 2026-10-15

## Registration
- ID/owner: local-migration-renumber-20261015 / Codex root; environment local (environment variable unset, environment file absent).
- Branch: main; worktree: D:/ZSJ-OS; base: 665fe9bf93c267788be7c8091ff14e93a36dedcc; target branch/integration: None.
- Goal: user-requested source renumbering V320 exam export -> V319, V321 delivery domain -> V320.
- Non-goals: business SQL changes, live ledger reconciliation, deployment, service operations, Git commit/push; preserve historical handoff evidence and unrelated changes.
- Ownership: the two renamed SQL migrations; migrations/README.md; tools/test_exam_export_permission.py and test_delivery_performance_domain.py under script/sql/mysql; docs/api/exam-calendar.md and delivery-performance.md; this record.
- Dependencies: existing V318; historical local execution under old numbers is recorded in the exam-export and delivery-manager handoffs. Existing databases require separately reviewed ledger mapping before migration execution.
- Verification: source numbering and reference checks, existing numbering tests and static database check; compare SQL to HEAD after substituting only version identifiers. Full fresh/upgrade database acceptance is not established by these checks.

## Delivery
- Renamed both migrations and synchronized SQL version literals, procedure names, ledger references, checksum filename inputs, fixture references and current API/migration documentation. Historical handoffs remain unchanged.
- PASS: zsjos_db.py check; migration numbering unittest (4 tests); unique contiguous core sequence through V320; both SQL bodies match HEAD exactly after version substitution and newline normalization; fixture Python syntax; scoped git diff --check.
- No database writes or ledger reconciliation performed. Controlled MySQL execution and complete fresh/upgrade verification under the renamed versions remain unverified; this is source-numbering delivery only, not database/release acceptance. Existing old-number databases require ledger/checksum review before running migrations.
- Unrelated pre-existing changes preserved. No commit/push. Ownership released.
