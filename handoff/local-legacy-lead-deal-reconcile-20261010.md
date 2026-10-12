# Local legacy Lead deal reconciliation

## Registration — 2026-10-10

- Environment: local (ZSJOS_AGENT_ENV and environment marker absent).
- Workstream: local-legacy-lead-deal-reconcile-20261010; owner: Codex /root.
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Goal: implement the user-approved tenant-1 legacy deal reconciliation, including missing won opportunities and effective-order links, then verify and apply to local development.
- Non-goals: remote writes, permissions, business state-machine/API changes, notifications, financial or historical approval rewrites, reminder cancellation, commits or branch operations.
- Exclusive ownership: script/sql/mysql/migrations/V304__legacy_lead_deal_state_reconcile.sql; script/sql/mysql/tools/legacy_lead_deal_reconcile.py; script/sql/mysql/tools/test_legacy_lead_deal_reconcile.py; docs/operations/legacy-lead-deal-state-reconcile.md; this record.
- Dependencies: Core V303 in both ledgers; existing local yudao-mysql; three approved order import batches; current owner/person and intended-product snapshots. Existing unrelated changes remain owned by their workstreams.
- Verification: read-only preflight; isolated MySQL initial/repeat/conflict/failure/recovery/concurrency cases; protected rollback checks; local snapshot backup, source execution, whole-table scoped-diff and reminder preservation; authenticated UI if available.
- Target branch/integration order: None.
- Approved expected local changes: 40 Leads, 26 existing opportunities, 14 inserted opportunities, 40 order links; preserve 16 post-deal reminders. Counts are observations, not cross-environment constants.

## Delivery — 2026-10-10

- Implemented V304, the read-only preflight/recovery inspector, local backed-up apply tool, isolated MySQL test suite and operational documentation. No API or schema-baseline changes; only the established optional backup table can be created when missing.
- Frozen migration SHA-256 applied locally: e1eddf06c4f12aa993b0119d76ab785e883ec646610cd3501d0a0d5197124760. Prerequisite Core V303 present in both ledgers. V304 registered in both only after transactional postconditions succeeded.
- Actual local changes: 40 Lead updates; 26 existing opportunity updates; 14 won opportunities inserted with actual migration creation time and historical order won_at; 40 order links; 280 immutable repair image/manifest records. Sixteen post-deal pending reminders and all follow-up timestamps preserved. Remaining scoped anomalous Leads: 0. All 120 recorded after-images match current data.
- Backup: D:/ZSJ-OS/backups/mysql/v304-20261010-122446/{before.sql,before.json,verification.json}. Protected by existing Git ignore; contains business data, do not publish. Whole-table comparison of 15 involved/protected tables passed: only authorized fields/new opportunity records/repair records/V304 ledger entries changed, no deletions. Source before-state hash matches the verified isolated clone.
- Isolated real-data clone: v304_20261010122200_clone; result 40/40/26/14/40 (targets/Lead updates/existing opportunity updates/inserts/order links); second execution zero changes with identical full snapshot hash. Clone evidence stored under backups/mysql/v304_20261010122200_clone.
- Synthetic suite v304_20261010122210_* passed initial/repeat/Lead+opportunity+order partial recovery with existing markers, post-user-edit refusal, missing owner/person/time, customer mismatch, different order link, lost opportunity/conflicting won time, deleted/other-person opportunity uniqueness, multiple effective orders, pending approval, public sea, missing prerequisite, injected insert/second-ledger failure with successful recovery, concurrent execution, empty candidates, already-correct order link and inserted-row edit refusal. Earlier test attempts exposed MySQL temporary-table reuse restrictions; SQL was corrected before any development write.
- Extended verification v304_20261010123512_* covers deleted Lead/order exclusion, empty historical product summary, empty scope, correct existing links and inserted-row modification protection. UTF-8/HEX validated both synthetic Chinese product snapshots and a newly inserted real opportunity summary. Python compile and content checks passed.
- Runtime attempt: authenticated existing browser session at local Workbench; backend proxy target 192.168.2.17 is this host. Original sample LD202609010065 returned empty in both all-stage and won filters; another repaired sample also empty. Did not alter account permissions or claim the cause conclusively. Authenticated sample list/detail acceptance remains unverified. Database independently confirms LD202609010065, LD202608210043 and LD202609150053 are won with correct won_at and order references. No server restart, production/test writes or Git publication performed.
- Recovery is guarded inspection plus documented controlled steps; no automatic rollback/deletion of inserted opportunities. Full fresh/upgrade chain not run because this is a scoped data-only migration with no changed baseline/order; not claimed as release acceptance.
- Ownership released for all registered files; no unrelated changes staged or modified.
