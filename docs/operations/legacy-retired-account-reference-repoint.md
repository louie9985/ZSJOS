# Retired account reference repoint (Core V303)

## Scope and diagnosis

The 2026-09-16 legacy merge and the 2026-09-21 increment imported Leads and orders against
employee accounts that were afterwards retired into duplicate rows. Six of those rows still carry
business references while the live account that inherited the person sits unused:

| Retired `system_users.id` | Name | Live account | Evidence |
|---|---:|---:|---|
| 232 | 陈薇 | 20 | V255 migration body states 232 → 20 |
| 259 | 莫才巧 | 19 | V255 migration body states 259 → 19 |
| 54 | 钟茂源 | 27 | V273 map 163 → 27; 1051 dept; legacy phone 15560121487 |
| 415 | 汪雅琪 | 51 | V273 map 156 → 51; the 30 legacy lead numbers are identical to 415's |
| 418 | 梁颖 | 400 | V273 map 159 → 400 |
| 524 | 汪雅琪2 | 30 | V273 map 160 → 30; legacy phone 15551182137 identical |

All six retired rows keep `deleted=1`, so `AdminUserApiImpl.getUserList` never returns them and
`LeadIdentityMaskingService.employeeName` renders **未知账号** for every reference. The employee
submitter advanced filter and the submitter-scoped order queries could not match the person who
actually submitted the record either, because they compare against the stored id.

The V273 mapping table is the authority used here. It is the same source the earlier ownership
repair consumed, and it is why this migration does not match accounts by nickname: the live
accounts are stored under bracket aliases such as `小江老师【钟茂源】`, so an exact-name match
finds nothing.

## What V303 changes

`script/sql/mysql/migrations/V303__retired_submitter_reference_repoint.sql`, applied after Core
V302 in both ledgers, repoints six surfaces in one failure-controlled procedure.

| Surface | Rows | Per retired id |
|---|---:|---|
| `zsjos_lead.source_user_id` | 244 | 232→105, 259→34, 54→57, 415→30, 418→14, 524→4 |
| `zsjos_lead.provider_owner_id` (where `provider_owner_type='system_user'`) | 188 | 232→105, 259→34, 54→31, 415→11, 418→3, 524→4 |
| `zsjos_lead.source_provider_user_id` | 38 | 54→31, 418→3, 524→4 |
| `zsjos_order.submitter_user_id` | 26 | 54→19, 415→2, 418→1, 524→4 |
| `zsjos_order.imported_actor_snapshot.submitter.orderUserId` | 26 | same 26 orders |
| `zsjos_order_approval_round.order_snapshot.leadProfile.sourceSubjectId` | 9 | 415→5, 54→3, 418→1 |

That is 496 repointed column values over 244 distinct Leads and 26 distinct orders.

### Why the two snapshot surfaces are part of the migration

They are contract-coupled, not decorative:

- `SalesOrderSnapshot.restoreActor` fills a missing historical actor name only when
  `imported.orderUserId` equals the order's current `submitter_user_id`. Repointing the column
  alone would make that gate fail and turn the historical submitter name into **未知账号** on all
  26 orders. The migration asserts the equality again after the write.
- `SalesOrderServiceImpl.buildLeadProfile` derives `leadProfile.sourceSubjectId` from the Lead's
  `source_user_id` for an employee source. Five of the nine frozen rounds carried `415`, three
  carried `54` and one carried `418`, so a frozen profile and a freshly built one disagreed before
  this correction. The other profile keys are re-checked as still present.

### Why `source_provider_user_id` moves with `provider_owner_id`

`LeadNotifySceneProvider` refuses to send the new-media provider notification when
`source_provider_user_id` no longer equals `provider_owner_id` (`LEAD_SOURCE_ATTRIBUTION_MISMATCH`),
and `LeadFollowUpServiceImpl.createSelfSourcedAutomatic` treats a non-null
`source_provider_user_id` as "provider already selected". Both columns therefore change together
and the equality is asserted afterwards.

## Boundaries

Rows deliberately **not** changed:

- **The partner side.** Eight Leads carry `source_user_id=54` with `provider_owner_type='partner'`;
  there, `54` is `zsjos_partner_account.id 54` (partner_id 52, enabled), a numeric collision with
  the retired employee, and three of them are new-system `KZ2026...` submissions created
  2026-10-08/09. Nineteen more Leads carry `provider_owner_id=54` with a partner provider. The
  migration asserts none of them received `updater='V303'`.
- **`imported_actor_snapshot.submitter.sourceEmployeeId`** (values 156/159/160/163). This is an
  explicitly typed `LEGACY_EMPLOYEE` id and `SalesOrderSnapshot.restoreActor` must never consume it
  as an ADMIN id. It stays byte-identical.
- **Other numeric collisions.** The same small values appear in unrelated fields —
  `legacyOrderId` (415, 418), `sourceOrderId`, `followUpRecordId`, `roundKey`, `personId`,
  `contentId`, `contentVersionId`, `infraFileId`, `legacyOrderItemId`, `partnerId`. None is a user
  reference and none is matched.
- **The 125 `zsjos_cashback.beneficiary_user_id` rows** (232 × 106, 259 × 19). This is money, held
  on the disabled partner subjects 8 (陈薇) and 9 (莫才巧). Repointing the beneficiary would not
  make it withdrawable, because the withdrawal path reads the cashback's own `partner_id` and both
  subjects are `disabled`. It is left for a separate, explicitly authorized decision.
- **Queued notifications** (`system_notify_business_outbox.payload`, 941 rows). These are the
  payloads as they were generated; they must not be edited as live history.
- **`system_user_role` (4 rows) and `system_user_post` (2 rows)** of the retired accounts. These
  are authorization facts; per the configurable permission contract they belong to System
  administration, not to a data migration.
- **`provider_owner_name_snapshot`** (333 rows carrying one of these names). It participates in
  display only when `provider_owner_type='partner'`; employee-side names are resolved live, and
  these six names did not change.
- Audit trails — `zsjos_business_event` (14), `zsjos_business_audit_log` (10),
  `zsjos_lead_assignment_history` (3). They record what an account did at the time; the three
  assignment rows and the portal log rows are partner-side anyway.
- `system_users`, `system_role_menu` and all permission configuration. The retired rows stay
  soft-deleted.

## Apply on a fresh or upgrade environment

The migration is a numbered Core migration and is discovered by the repository runner:

```bash
cd /opt/zsjos
ZSJOS_DB_MODULES=core ./zsjos-db migrate
```

Do not export `ZSJOS_DB_SKIP_CHECKSUM`. The file registers its own checksum as
`SHA2('V303__retired_submitter_reference_repoint.sql',256)` with `release_version='baseline'`,
matching the V298–V302 precedent.

For a host without the runner the file is self-contained (it needs no `SOURCE`):

```bash
mysql --default-character-set=utf8mb4 -h "$HOST" -u "$USER" -p "$DATABASE" \
  < script/sql/mysql/migrations/V303__retired_submitter_reference_repoint.sql
```

## Verification

```sql
-- residuals: all six must be 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND IFNULL(provider_owner_type,'')<>'partner'
  AND source_user_id IN (232,259,54,415,418,524);
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND provider_owner_type='system_user'
  AND provider_owner_id IN (232,259,54,415,418,524);
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0'
  AND source_provider_user_id IN (232,259,54,415,418,524);
SELECT COUNT(*) FROM zsjos_order WHERE deleted=b'0' AND submitter_user_id IN (232,259,54,415,418,524);
SELECT COUNT(*) FROM zsjos_order WHERE deleted=b'0'
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
      IN (232,259,54,415,418,524);
SELECT COUNT(*) FROM zsjos_order_approval_round
 WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(order_snapshot,'$.leadProfile.sourceSubjectId')) AS UNSIGNED)
       IN (232,259,54,415,418,524);

-- the partner side must be untouched
SELECT COUNT(*) FROM zsjos_lead WHERE updater='V303' AND provider_owner_type='partner';   -- 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND provider_owner_type='partner'
  AND source_user_id=54;                                                                  -- 8

-- the coupled invariants
SELECT COUNT(*) FROM zsjos_order o WHERE o.deleted=b'0' AND o.updater='V303'
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
      <> o.submitter_user_id;                                                             -- 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND updater='V303'
  AND source_provider_user_id IS NOT NULL
  AND source_provider_user_id<>provider_owner_id;                                         -- 0

-- pre-images and ledgers
SELECT table_name, column_name, COUNT(*) FROM zsjos_data_repair_backup
 WHERE repair_key='retired-submitter-repoint-20261010' GROUP BY 1,2;
SELECT version FROM zsjos_schema_version WHERE version='V303';
SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V303';
```

A replay reports 0 on all six counters and adds no ledger row and no pre-image.

## Rollback

The pre-images restore all six surfaces. The four plain columns are direct restores:

```sql
UPDATE zsjos_lead l JOIN zsjos_data_repair_backup b
   ON b.repair_key='retired-submitter-repoint-20261010' AND b.table_name='zsjos_lead'
  AND b.row_id=l.id AND b.column_name='source_user_id'
   SET l.source_user_id=CAST(b.old_value AS UNSIGNED);
```

Repeat for `provider_owner_id`, `source_provider_user_id` (both `zsjos_lead`) and `submitter_user_id`
(`zsjos_order`).

The two snapshot surfaces are keyed restores:

```sql
UPDATE zsjos_order o JOIN zsjos_data_repair_backup b
   ON b.repair_key='retired-submitter-repoint-20261010' AND b.table_name='zsjos_order'
  AND b.row_id=o.id AND b.column_name='imported_actor_snapshot.submitter.orderUserId'
   SET o.imported_actor_snapshot=JSON_SET(o.imported_actor_snapshot,
         '$.submitter.orderUserId', CAST(b.old_value AS UNSIGNED));

UPDATE zsjos_order_approval_round r JOIN zsjos_data_repair_backup b
   ON b.repair_key='retired-submitter-repoint-20261010'
  AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
  AND b.column_name='order_snapshot.leadProfile.sourceSubjectId'
   SET r.order_snapshot=JSON_SET(r.order_snapshot,
         '$.leadProfile.sourceSubjectId', CAST(b.old_value AS UNSIGNED));
```

Neither key can be a JSON null here: all 35 pre-images hold a concrete integer, so
`CAST(old_value AS UNSIGNED)` is the correct restore and the `<json-null>` rule from V302 does not
apply.

After a rollback, remove the pre-images:

```sql
DELETE FROM zsjos_data_repair_backup WHERE repair_key='retired-submitter-repoint-20261010';
```

Rolling back the data does not require reverting any code: the name resolution, the filter and the
frozen profile all read whatever the row holds.


## 2026-10-11 客户端重复执行修订

V303 在 V309 后验证自身及 V309 双台账和原有六类主体引用后验后零写入返回；仍拒绝残留旧主体和域冲突，不重新映射后续修订。

仅面向本地未发布开发脚本；保留原台账、备份、业务数据和部署校验值审查边界。此节更新前文对“重复执行一律检查整行”的约定，实际写入恢复约束不变。生成方式、执行顺序与隔离验证见 [数据库迁移操作](database-migrations.md#replaying-v296v310-from-a-gui-client)。
