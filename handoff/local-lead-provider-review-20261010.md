# Workstream Registration — 2026-10-10

- ID: local-lead-provider-review-20261010; environment: local; owner: Codex /root.
- Goal: restore management provider display, annotate verified provider identity, investigate missing snapshots for user review before data repair.
- Non-goals: no database writes, inferred historical attribution, role changes, service restart, commits or branch operations.
- Branch: main; worktree: D:\ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; integration: None.
- Ownership: LeadManagementRespVO.java; LeadManagementServiceImpl.java (provider projection only); new LeadProviderDisplay.java and test; Workbench LeadManagementPage.tsx, LeadDetailOverview.tsx, LeadDetailOverview.source.test.ts, services/api.ts (provider label only); new provider browser fixture files; docs/api/lead-provider-review.md; this record.
- Dependencies: established provider attribution, source-provider recording flag, identity masking and existing test/browser tools. Preserve concurrent edits in shared API file.
- Verification: focused Java and Workbench tests, typecheck, browser desktop/mobile fixture, readonly tenant-1 source audit. Admin consumer compatibility inspection for additive response field. Migration scope awaits historical-source confirmation.
- Decision: current user instruction supersedes the old management-only provider hiding request.

## Scope update

- User confirmed 492 missing Partner snapshots may use current names with recorded repair provenance; four identified employee-provider rows are actually unassisted self-sourced Leads.
- Add ownership: V307__lead_provider_snapshot_repair.sql; tools/lead_provider_snapshot_repair.py; tools/test_lead_provider_snapshot_repair.py.
- Prepare repeatable exact-manifest migration with before/after backup and provenance, dual-ledger failure control; verify on isolated MySQL copies. Shared local DB application not yet performed; exact-target approval is a final step after reviewable verification.

## Authorization resolution and delivery — 2026-10-10

- User's reply explicitly permits the stated 492-row current-name migration and confirms the four exact Leads as self-sourced, in the established local-Docker target context. This covers local application; no repeat permission request required. Applied V307 after controlled verification.
- UI: management provider hiding removed; shared overview consumes optional backend identity label. Ambiguous histories remain pending verification. Names continue through existing backend masking. Old backend response falls back to identity pending review, never inferred roles.
- Verification: Maven reactor focused LeadProviderDisplayTest passed 2 tests; Workbench source tests 4/4, typecheck passed; real Chromium synthetic fixture covered 6 states at 1440 and 390, screenshots inspected and wrapping corrected. Admin additive-field compatibility inspected (typed GET accepts additional response fields); no Admin UI change.
- SQL final controlled verification: v307_test_20261010174708_bf1759 and empty v307_test_20261010174715_0e9942. Passed prerequisites, second-ledger failure rollback, 496-row scoped diff, all 492 UTF-8 HEX checks, repeat, partial recovery despite ledgers, missing evidence rejection, later-edit rejection, guarded rollback rehearsal, empty repeat. Initial test-clone generated-column issue corrected by excluding generated columns.
- Local application: 5,471 active Leads, 5,189 named, 282 unnamed; 1,488 before/after/provenance records; both V307 ledgers present. Actual 496 postimages and all 492 UTF-8 names verified; immediate repeat leaves all Lead rows and backups unchanged.
- Remaining: 112 unnamed are confirmed unassisted self-sourcing (normal); 170 unresolved (42 sales, 111 education, 16 new-media with missing source-user ID, 1 other). No speculative repair. Backend not restarted; authenticated live UI acceptance remains outstanding. No production operation, permissions grant, commit, push or branch changes. Test databases retained for review.
- Ownership released for delivered changes; follow-up uses this record. SQL and implementation remain uncommitted with unrelated existing work preserved.

## V308 scope — 2026-10-10

- User explicitly requests migration and local repair of confirmed 153 unassisted self-sourced Leads and 16 media providers (13 Cheng Xu, 2 Wang Wenxin, 1 Chen Wei). Remaining other-source Lead excluded.
- Own V308__confirmed_lead_provider_repair.sql, confirmed_lead_provider_repair.py and its controlled test; this record and lead-provider-review.md. Current main/worktree/base/environment unchanged.
- Provider repair only: preserve existing source_type/source_user_id, owner, department/counting snapshots, orders and statistical attribution. Legacy source-type inconsistency is documented separately; fixing that cross-statistics contract is outside this provider-field correction.
- Verify fixed manifest, transaction/dual ledger failures, repeat, partial recovery, later-edit rejection, UTF-8 and scoped before/after diff on isolated MySQL; then authorized local application and repeat verification. No service restart/production/branch/commit operations.

## V308 delivery — 2026-10-10

- Created and applied V308__confirmed_lead_provider_repair.sql on authorized local Docker ruoyi-vue-pro. 153 self-sourced flags and 16 media provider associations repaired; fixed manifest prevents scope expansion. Three-way provider counts verified 13/2/1; two user-confirmed exceptions exact-checked.
- Final isolated MySQL verification: v308_test_20261010182514_52a491 and empty v308_test_20261010182520_2dee47. Initial/repeat, prerequisite failure, second-ledger rollback, missing evidence, later edits, partial before-image recovery with ledgers, rollback rehearsal and empty repeat passed. Scoped full-row comparison and UTF-8 HEX passed.
- Actual local apply: exactly 169 rows changed, only provider/recording/version/modification audit fields. All 169 expected images and 16 HEX names verified; immediate repeat leaves all Lead rows and all V308 backups unchanged. 507 evidence rows and both version ledgers present.
- Current active tenant-1 totals: 5,471; named 5,205; unnamed 266 = 82 sales self + 183 education self + 1 unresolved other. Employee type 4,496; partner 709. Other-source LD202605270007 preserved.
- Legacy source-type inconsistency and missing original submitter for 16 rows remain explicitly documented, not fabricated from provider identity. No new historical department/counting facts. No production, service restart, code rebuild, permission grant, branch or commit. Ownership released.
