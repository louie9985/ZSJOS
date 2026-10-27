# Single-person merge trial

## Registration — 2026-10-27 (Asia/Shanghai)

- Environment: local (ZSJOS_AGENT_ENV unset; marker absent).
- Owner/workstream: Codex root / person-merge-trial-20261027.
- Goal: select one of the five workbook-confirmed duplicate identities and test a scoped merge in a retained independent MySQL verification schema.
- Non-goals: application/production writes, imports, frontend changes, general merge capability, permissions or source workbook edits.
- Branch: main; worktree: D:/ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Ownership: this record; private trial script and evidence under C:/Users/EDY/.codex/tmp/person-merge-trial-20261027/ (no personal data in repository).
- Dependencies: current local MySQL snapshot, existing read-only connection helper, supplied workbook confirmation annotations. User requested one selected test; no shared database merge authorized.
- Verification: source read-only transaction; clone relevant identity-reference tables; retain original snapshots; test merge rollback, commit, replay, preserved unrelated rows and business snapshots, contact uniqueness, import identity matching, Chinese HEX and source selected-row digest stability.
- Integration target/order: None. Only database-level verification; no API/browser acceptance claim.

## Delivery — 2026-10-27 13:56 (Asia/Shanghai)

- Status: one isolated database trial passed; ownership released. Branch/worktree/HEAD unchanged from registration.
- Selected the workbook-confirmed candidate with only one Lead across both identities and no media-account migration. Kept the existing student master and its contact claim, moved one Lead, one activation and one order, logically retired the duplicate only in the verification schema, and appended one PersonMergeEvent. No physical deletion.
- Retained verification schema: historical_student_verify_20261027135451846832. Private script, manifest and before/after/result evidence: C:/Users/EDY/.codex/tmp/person-merge-trial-20261027/ (latest run directory). No source-workbook edits or teaching imports.
- Verification: exact cloned table row hashes, injected-failure transaction rollback, committed merge, replay with zero further changes, all other cloned rows unchanged, three orders and existing service preserved, contact uniqueness and existing importer identity lookup, Chinese reason UTF-8 HEX, selected application-source rows unchanged before/after. Application connection used a read-only transaction; all writes used the independent schema.
- Initial clone verification failed before merge because filtering EXTRA by GENERATED also excluded ordinary DEFAULT_GENERATED timestamp columns. Corrected only the private trial copier to use an empty generation_expression; recreated an independent fixture and all checks passed. Initial fixture retained. Existing importer files were not edited.
- Changed repository files: this handoff record only. Private trial script is deliberately not a general-purpose or application-write merge tool. Existing business snapshots and backup tables were preserved.
- Dependency/integration impact: None. No deployment, migration ledger, role grant, live-service reconfiguration or shared database mutation.
- Limits/remaining: no application API/browser or effective object-access verification against merged data. A live merge requires scoped review/authorization and those checks; candidates with multiple active Leads/Opportunities require separate business conflict resolution and cannot reuse this trial blindly.

## Registration update — 2026-10-27, remaining confirmed candidates

- User requested completing the other four candidates. Same owner, local environment, branch, worktree, private evidence root and repository-file scope as above; ownership reopened for this record.
- Scope: independently verify the other four current identity pairs; merge compatible pairs in a new verification schema and exercise rejection/rollback for conflicting active Lead/Opportunity pairs. Source data remains read-only. No application writes or generic lifecycle changes.
- Findings: two pairs have one active Lead and at most one Opportunity; two have both following and won Leads/Opportunities with cashback/performance references, and one has different owners. Those lifecycle decisions are pending user clarification; they cannot be silently resolved by marking a business record deleted.
- Verification extends to exact media/service preservation, all non-generated Person-reference columns, scoped source stability, conflict rejection and fixture rollback. Prior API/browser limitation remains.

## Delivery / direction update — 2026-10-27 14:08 (Asia/Shanghai)

- User rejected stopping or archiving ongoing business. Subsequent discussion proposes assigning the imported teaching record to one existing archive without physically merging the existing Person records. This is a proposal under discussion, not an application-write authorization or implemented importer policy.
- Completed two more isolated merge trials: historical_student_verify_20261027140354704564 and historical_student_verify_20261027140530620077. Respectively preserved four and three orders and one existing service each, plus all media records and unrelated cloned rows. Existing contact values were retained, including copying the retiring record's identical numeric WeChat value into the retained record's empty WeChat field in the fixture only.
- Both passed exact clone hashes, all-row diff allowlists, injected full transaction rollback, commit, replay/no further changes, unique contact/import matching, UTF-8 HEX, and selected source-row stability. No source application data writes, teaching imports, or spreadsheet updates.
- Two conflicting candidates were independently tested against both Lead and Opportunity unique constraints; all four attempted fixture updates rejected with duplicate-key errors and were rolled back. Existing ongoing business was preserved. Private evidence: active-business-conflicts-result.json under the registered private root.
- One earlier fixture committed but its full after-check ran out of memory; it is not accepted as a verified result. Revised the private checker to use per-table disk snapshots and limited numerical-library threads, then completed the two verified runs above. Evidence retained; no automatic deletion.
- Repository change: this handoff only. Private trial script extended for the two additional exact candidates and a conflict test added. HEAD, branch and worktree unchanged. Dependency/integration impact: None.
- Remaining: discuss and, if requested, implement an exact reviewed source-row-to-existing-Person assignment for import, with full candidate-set/evidence validation and course/service idempotency. This would leave original People, contact claims, Leads, Opportunities, owners, cashback/performance and orders unchanged. No arbitrary global multi-match bypass. All trials remain database-level; API/browser/authorization verification was not completed. Physical-merge trial workstream closed; do not apply those fixture changes to the application database.

## Registration update — 2026-10-27 14:13 (Asia/Shanghai)

- User authorized trying targeted teaching import to assess smallest change and existing-business impact. Reopened this record; same owner/environment/branch/worktree. Private scope extends to C:/Users/EDY/.codex/tmp/targeted-student-import-20261027/.
- Implement only an independent-schema prototype adapter around current import prepare/apply functions for the five reviewed rows. Do not alter repository importer policy, expand the entire eight-sheet batch, write application data, or update source Excel.
- Proposed exception binds exact workbook digest, row contents, target business archive number, complete candidate/claim state and same-person review. Preflight and transaction-time checks share it. Preserve all existing Person/contact/Lead/Opportunity/order/media/service rows except the existing importer may need to promote a non-student target's identity_status; measure and report this explicitly instead of claiming zero effect.
- Verification: controlled clone, baseline rejection vs targeted preflight, tampered/missing decisions rejected, target/candidate/claim drift rejection, cross-candidate existing-service guard, injected rollback, commit/replay, exact old-row diff allowlist, UTF-8, selected source-row stability. API/browser/visibility changes remain unverified unless tested; do not present SQL evidence as that acceptance.

## Delivery — 2026-10-27 14:19 (Asia/Shanghai), targeted-import prototype

- Outcome: five-row targeted import succeeded in independent schema historical_student_verify_20261027141613197123. Original policy rejected all five as multi-archive contact conflicts; reviewed target selection produced four new teaching services and one existing/no-op row. No new classes needed. No application-data or source-workbook writes.
- Existing/no-op: registration nutrition source row 186 already has matching person/class/SKU/name/owner service. Created services for that sheet's rows 99, 164, 175 and human-resources public-nutrition row 91, using separate existing targets and preserving both originals in each pair.
- Exact fixture changes: four service relations, four empty collaboration groups, eight historical contact/plan records, four import audit events; one existing target Person promoted from active to student with audit/version fields. No Person creation/deletion/merge, no contact-claim writes, no changes to existing Lead/Opportunity/order/cashback/performance/media/service records. No existing business ownership or stage changed.
- Tests passed: exact cloned row hashes, original-policy rejection, missing/modified row decision rejection, tampered target rejection, Person-version and contact-reservation drift rejection, full injected rollback, commit, exact existing-row/addition allowlist, replay with no further writes, service-structure/UTF-8 checks, selected source People/claims/Leads/Opportunities/orders/services unchanged. Cross-candidate same-course guard included; no artificial duplicate-service injection was exercised.
- Private prototype/evidence: C:/Users/EDY/.codex/tmp/targeted-student-import-20261027/prototype.py and run-20261027141613196123/{manifest.json,preflight-private.json,result.json,decisions-private.json}; full per-table before/after snapshots retained outside repository. Prototype wraps existing prepare/apply functions in memory and refuses databases other than its generated independent schema. It does not change the repository importer or claim the eight-sheet batch is supported.
- Impact assessment: smaller than physical merges or an identity-association subsystem; change boundary is reviewed import mapping, shared preflight/commit resolution, contact-claim preservation and existing-service guard. Normal student promotion changes identity-based filters; adding services changes teaching counts and grants the new service owner the existing student-read/repurchase eligibility checks subject to configured permissions. MyStudentService filters visible course relations by their own scopes. No real-account/API/browser validation performed, so do not claim zero behavioral impact or complete permission acceptance.
- Changed repository files: this record only. HEAD remains 8279212fb97129ba6b006dbb0452ad843dfde4b5; branch/worktree unchanged. Dependencies/integration: None. No new runtime libraries, schema migrations or application implementation.
- Remaining: productionizing the approved target-map flow and full current-workbook source validation, reviewing actual target choices and identity-promotion impact, and real-account verification are separate from this feasibility trial. Existing ambiguous identities remain ambiguous for normal unrelated commands. Workstream closed; no automatic application of fixtures.

## Registration update — 2026-10-27, confirmed psychology SKU correction

- User authorized replacing the exact psychology level-three VIP shorthand with its verified full system SKU name ending in 4980. Local environment, branch/worktree/base and owner unchanged.
- Ownership: this record and the two user-supplied desktop workbooks; private backup, authoring and verification files under C:/Users/EDY/.codex/tmp/psychology-sku-fix-20261027/.
- Scope: exact 29 source specifications and matching exception entries only. Preserve other anomalies and manual opinions; record specification resolved/pending full recheck rather than claiming import completion. No database, importer or full-batch policy changes.
- Verification: authoritative enabled SKU/product exact match, Artifact Tool authoring, source-package preservation, all-cell diff allowlist, unchanged workbook objects/styles and other sheets, retained original backup. Changed source hash invalidates prior import plans; any future write requires fresh preflight.

## Delivery — 2026-10-27, confirmed psychology SKU correction

- Updated both supplied desktop files in place after verification: 29 exact source specification cells; corresponding 29 exception specification cells plus reason/pending text (87 exception cells). Preserved 125 exception rows, all other reasons and manual opinions. Specification-only cases marked matched/pending recheck; no claim of successful import.
- Read-only local DB verified exactly one matching full SKU and enabled SKU/product. No database writes, code/policy changes or role changes.
- Artifact Tool authored values and rendered representative before/after ranges; visual checks passed. Node returned nonzero after producing all exports/previews without a diagnostic; independent ZIP and openpyxl read-only checks verified exported values. Native package writeback copied only verified target cells, preserving all other ZIP parts and all original cell styles/values. Fixed namespace/ZipInfo-copy issues before replacing either user file; final all-cell/ZIP checks passed.
- Private original backups, plan, authored intermediates, previews and verification: C:/Users/EDY/.codex/tmp/psychology-sku-fix-20261027/. Source workbook new SHA-256: 43c29eae0ac473f95ee965f011a9c525240e6cd346701157bb2f496c90b3dc6c. Prior source-bound trial/import plans cannot be reused; regenerate them against this revised source before any import.
- Repository changed: this handoff only. Branch/worktree/HEAD unchanged from registration. Dependency/integration impact: None. Remaining: other exceptions and full import preflight; no new service imported. Ownership released.

## Registration update — 2026-10-27, other numeric-suffix specifications

- User authorized analogous exact specification corrections where only a terminal separator plus number is missing. Same local environment, owner, branch/worktree/base and two desktop workbook targets; private scope C:/Users/EDY/.codex/tmp/psychology-sku-suffix-fix-20261027/.
- Read-only scan of all eight teaching sheets against current enabled SKU/product catalog found one additional unique exact-prefix mapping: psychology level-four VIP ending in 3980, 22 rows. No match for the other unresolved specifications. No fuzzy matches, renamed courses or dictionary/catalog writes.
- Reuse verified Artifact Tool authoring/native preservation workflow. Backup current files, change only 22 source specification cells and corresponding exception specification/reason/pending cells, preserve manual opinions and all other workbook contents, visually inspect and verify exact diffs. No imports.

## Delivery — 2026-10-27, other numeric-suffix specifications

- Completed the sole additional verified mapping: 22 level-four VIP source rows now use the full enabled SKU ending in 3980. Updated corresponding 66 exception cells (specification/reason/pending text); retained all 125 rows, other anomalies and manual opinions. Previous level-three correction preserved.
- All eight source teaching sheets were compared against the live enabled catalog. Other unmatched specifications do not have a unique exact-name-plus-numeric-suffix candidate and remain unchanged.
- Verification passed: authored-cell checks, representative before/after visual inspection, ZIP integrity, exact all-cell diff allowlist, identical styles and all other package parts. Artifact process again returned nonzero after successful exports/renders; final artifacts independently validated. Fixed preview script path before authoring; original files replaced only after verification.
- Private backup/evidence: C:/Users/EDY/.codex/tmp/psychology-sku-suffix-fix-20261027/. New source SHA-256 f8bdd5388b6497b2f38055e888bff1870085f868ee15c0643853e2570bf5eb4b; future imports must regenerate source-bound plans.
- Repository change: this record only. Same branch/worktree/HEAD; integration/dependencies None. No database writes or imports. Ownership released; remaining unmatched specifications require other business clarification.

## Registration update — 2026-10-27 14:39 (Asia/Shanghai), resolved specification cleanup

- User authorized removing resolved specification-only exceptions and verifying corrections in the import source. Same environment/owner/branch/worktree/base. Scope: desktop exception workbook, source read verification, this record, private C:/Users/EDY/.codex/tmp/resolved-spec-cleanup-20261027/.
- Delete exactly 45 specification-only rows; retain six corrected-specification rows with independent conflicts and all other rows/opinions. Verify all 51 corrections against source coordinates. Backup original, preserve retained styles/order via Artifact Tool authoring and native row remapping; verify 80 remaining records and source hash unchanged. No database writes/imports.

## Delivery — 2026-10-27, resolved specification cleanup

- Removed exactly 45 resolved-specification-only rows from the supplied desktop exception workbook; 80 remain. Six rows with corrected specifications retain independent identity/contact/specification-choice issues. All retained values, opinions, order, cell styles and row heights match their original rows; untouched XLSX package components match exactly.
- Verified all 51 full specification names against import-table source coordinates, names and contacts (trim compatibility for two pre-existing whitespace differences); source already contains all corrections and was left byte-identical. No database writes/imports.
- Artifact authoring, independent ZIP/content verification and final native-workbook visual inspection passed. Artifact runtime returned nonzero after producing exports/previews; outputs independently verified as before. Replacement initially blocked by an open file; user closed it, unchanged-source guard passed and in-place replacement succeeded.
- Original backup, authoring plan, retained-row map and result: C:/Users/EDY/.codex/tmp/resolved-spec-cleanup-20261027/. Source and exception filenames remain unchanged; no alternate desktop version delivered.
- Repository changed: this record only. Same local environment/branch/worktree/HEAD; dependency/integration None. Remaining: 80 listed records, including the five target-selection trials not yet applied to the business database. Ownership released.

## Registration update — 2026-10-27, unscheduled-class assignment

- User superseded the unexecuted missing-SKU creation proposal: move the cited no-level psychology records to each planner's unscheduled class and summarize other missing SKUs. No catalog creation was performed.
- Same local environment/owner/branch/worktree/base. Scope: both supplied desktop workbooks, this record, private C:/Users/EDY/.codex/tmp/unscheduled-psychology-20261027/.
- Read-only DB verified exact enabled planner matches and their existing SERVING/UNSCHEDULED classes. Source scope: 24 rows with the exact cited no-level SKU; planner distribution 6/16/2. Preserve planners, original SKU and independent anomalies. Change class names only plus corresponding exception explanation; no application enrollment changes.
- Verify source/exception row mapping, exact diffs/styles/package preservation, backups and visuals. Summarize four other missing specifications (10 rows) without inventing SKU values or prices. Existing importer NORMAL-only class and enabled-SKU prerequisites remain; this is workbook preparation, not import acceptance.

## Delivery — 2026-10-27, unscheduled-class assignment

- Completed workbook preparation for the exact no-level psychology SKU: 24 source class cells now say 未确定考期, planner fields unchanged. Corresponding exception class and explanation cells updated (48 total); all 80 exceptions retained because class assignment does not resolve missing SKU/other conflicts.
- Verified enabled planner identity and unique existing SERVING/UNSCHEDULED class for all three planners; 6/16/2 source records. No new class, SKU, attribute, service or other DB write. Prior missing-SKU creation/price proposal cancelled and never executed.
- Other missing specifications summarized from current source/live catalog: psychology initial 7, psychology assistant 1, public-nutrition human-resources level-one premium 1, Chinese pharmacist exam-prediction single-subject 1. Exact names and source row coordinates delivered in chat.
- Original backups/plan/authored values/ZIP and all-cell verification/final previews retained under C:/Users/EDY/.codex/tmp/unscheduled-psychology-20261027/. Initial full Artifact import hit host memory exhaustion; sparse-cell authoring and disabled optimization succeeded. Nonzero Artifact exit after export/render independently checked. Native-package transfer preserved all non-target cells/styles and other ZIP parts. User closed occupied files; original hashes rechecked, both original desktop files replaced successfully and final native renders inspected.
- Source new SHA-256 9847996cbfd166d763b9d2d5091f7b5f59ef2bcd2edf77c1599a8a47ccf6df77. Old source-bound plans remain unusable. Existing importer still requires NORMAL classes and enabled matched SKU; no claim that these 24 rows can yet be imported.
- Changed repository file: this record only. Environment/branch/worktree/HEAD unchanged. Integration/dependencies None. Remaining: SKU business mapping and eventual import adapter/preflight; no live enrollment change. Ownership released.

## Registration update — 2026-10-27 16:04 (Asia/Shanghai), four disabled historical SKUs

- User explicitly authorized creating the four recently compared missing SKUs, keeping them disabled, and using zero as a price placeholder with a historical/unverified/no-sale note. Excludes the no-level psychology SKU. Target: local yudao-mysql / ruoyi-vue-pro, tenant 1 only.
- Ownership: this record and private scripts/SQL/evidence under C:/Users/EDY/.codex/tmp/disabled-historical-skus-20261027/. Same owner/local environment/branch/worktree/base. No API/code/schema/migration-ledger/workbook changes.
- Preserve exact source SKU names and owning courses. Reuse existing sales_spec attributes; add only absent supporting values and four disabled SKU rows, no existing row updates. Regular createSku forces enabled status, so use one guarded local data-maintenance transaction to avoid an enabled interval; do not relax endpoint behavior.
- Verify preconditions, exact catalog-row preservation, failure rollback and repeat execution in an independent schema before scoped local application; preserve before-state evidence, verify status/price/UTF-8 HEX/canonical attribute hashes and absence from enabled catalog. No deletion recovery; newly created configuration remains retained and disabled.
- Stopped-SKU policy inspection: new Lead/purchase intent/new order selection validates enabled SKU; order historical item snapshots may be reused without reselecting current catalog. History is not cancelled by disable. Real-account UI/API acceptance not yet performed.

### Scope correction before execution

- User broadened the request to all remaining missing specifications. Include the no-level psychology SKU: five new disabled SKUs / 34 source records total, same confirmed zero-placeholder policy. Preserve the prior unscheduled-class workbook assignments. This supersedes the four-SKU exclusion above; no application writes had run before this correction.

## Delivery — 2026-10-27 16:15 (Asia/Shanghai), disabled historical catalog entries

- Completed authorized local tenant-1 data maintenance: five new SKU rows and five supporting sales_spec values. SKU status=1 (disabled); price/retail_price=0 as expressly approved placeholders; pricing note/remark says historical, unverified price, no sale. Source SKU labels preserved, owner products resolved exactly, existing catalog rows unchanged. Includes all three psychology missing variants, public-nutrition level-one premium and Chinese-pharmacist prediction single-subject. Prior workbook class assignments unchanged.
- Target: local yudao-mysql / ruoyi-vue-pro only. No production operation, student import, workbook edit, role grant, migration-ledger change or existing business-row update. Transaction directly inserts disabled rows to avoid createSku API's forced-enabled interval.
- Private executable SQL/runner, original catalog snapshot, decisions and after/result evidence: C:/Users/EDY/.codex/tmp/disabled-historical-skus-20261027/. Accepted isolated fixture: historical_student_verify_20261027161438720054. Initial fixtures failed before application due to literal/column collation mismatch; explicit utf8mb4_unicode_ci literals corrected it. Docker became unavailable while user restarted it; waited as requested and continued after restoration, without restarting services ourselves.
- Verification passed: clone row equality, injected transaction failure rollback, initial insert and repeat execution in fixture, pre-application source-drift guard, application initial/repeat execution, exact preservation of every prior catalog row, five disabled zero-price SKUs, Chinese HEX, expected added attribute values only. No deletion or recovery cleanup; fixtures and evidence retained. Client aborts on failed CHECK and connection closure rolls back; all DML/postcondition checks precede COMMIT, no success ledger involved.
- Behavior evidence: enabled SKU catalog queries exclude new rows. Existing validateLeadProduct rejects disabled selection for new Lead submissions, purchase intents and new order items. Existing order item snapshots may be reused for historical revisions; disabling is not cancellation or deletion of historical services/orders. API/browser verification was not performed; this is code-path plus database evidence, not a complete end-to-end acceptance claim. Current historical importer also rejects disabled SKUs; no bypass added and no exceptions removed.
- Repository change: this handoff only. Branch/worktree/HEAD unchanged from registration. Dependency/integration impact None. Requested catalog operation complete; historical enrollment remains separate. Ownership released.
