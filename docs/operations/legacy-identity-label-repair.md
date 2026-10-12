# Legacy Lead identity and label repair (Core V298)

## Scope and diagnosis

Two historical defects were introduced by the parttimecrm business merge and by V273, and both were
visible to users before this repair.

1. **Academic self-sourced Leads displayed as "新媒体提交".** `V273__academic_planner_lead_ownership.sql`
   established `owner_identity='education'` for the 177 Leads in its explicit `academic_customer`
   map, but it deliberately did not touch `source_type` and did not backfill 归属以外的字段. The
   imported rows therefore kept `source_type='internal_new_media'`, which the management list renders
   as "新媒体提交" while the owner column renders "教务". 142 of the 177 rows were affected; the other
   35 were 30 rows already typed `education_self_sourced` and 5 rows typed `partner`.

2. **"历史未记录" was displayed for a large number of fields.** The literal string is never persisted:
   it is a front-end fallback. The underlying gaps, ranked by volume, were:

   | Surface | Rows | Cause |
   |---|---:|---|
   | `zsjos_order.payment_method` value | 479 | 498 Orders have no approval round and 73 rounds carry no `orderLabels` |
   | `zsjos_lead.invalid_reason_label_snapshot` | 474 | legacy rows kept the English reason code while the dictionary holds Chinese values |
   | `zsjos_lead.sales_stage` | 250 | V275 bounded initialization to `status<>'won'`; no legacy sales stage exists |
   | `zsjos_order.imported_actor_snapshot` | 246 | normal Orders legitimately have no import evidence |
   | `zsjos_lead.source_channel_label_snapshot` | 50 | 49 rows recorded only the enabled dictionary value 其他; 1 row recorded nothing |
   | `zsjos_lead.lead_category_label_snapshot` | 41 | the legacy `customer_level` value is empty |

## What V298 changes

`script/sql/mysql/migrations/V298__legacy_identity_and_label_repair.sql`, applied after Core V297 in
both ledgers, performs three bounded corrections inside one failure-controlled procedure:

| Section | Correction | Rows |
|---|---|---:|
| 1 | `source_type` `internal_new_media` → `education_self_sourced` where `owner_identity='education'` | 142 |
| 2 | legacy English `invalid_reason` code → enabled dictionary value, and the label snapshot set to the same value | 528 |
| 3 | `source_channel_label_snapshot` backfilled to `其他` where that is the only recorded value | 49 |

Section 2 covers 528 rows, not only the 474 invalid ones: 44 valid, 5 submitted, 4 suspended and 1 won
row still carry the reason recorded before a later restoration, and the current code clears it in
`LeadValidityRestoration`. Normalizing the value keeps one convention in the column. Writing a label
for those 54 rows changes no rendering, because both display paths are gated on `invalid_description`
or on `status='invalid'`.

The ten English codes map one-to-one onto the ten enabled entries of `zsjos_lead_invalid_reason`:
`wechat_add_failed`→微信号添加不上, `other`→其他, `duplicate`→重复客资, `no_answer`→电话多次未接通,
`no_need`→无相关需求, `refused`→客户明确拒绝, `empty_number`→空号/停机, `not_target`→非目标客户,
`fake_info`→信息虚假, `age_ineligible`→年龄不符合报考条件.

## Evaluation-order defect found during authoring

The first draft of section 2 wrote both columns with two `CASE invalid_reason ... END` expressions in a
single `UPDATE`. MySQL evaluates a single-table `UPDATE` SET list left to right, so the second expression
read the already-rewritten Chinese value, matched no `WHEN` branch and stored `NULL`. The row counts and
the reason postcondition both passed, because neither inspected the label. The resulting 474 invalid
Leads showed a reason with no label.

Section 2 therefore maps through a temporary table joined on the legacy code and assigns both columns
from `m.dict_value`, so neither assignment reads the column it writes. A postcondition was added that
fails when a normalized reason has an empty label snapshot.

When correcting a field whose label lives in a second column of the same row, always take both values
from the source side of a join rather than from the row being updated.

## Deliberately out of scope

- **Order field values and label snapshots.** Of 704 Orders, 498 have no approval round and 73 of the
  206 rounds carry no `orderLabels`; the only label store is `order_snapshot.orderLabels`, so no
  authoritative source exists. The legacy columns also cross two meanings: legacy `payment_method`
  holds 缴费方式 labels (零售缴费/底价缴费/预付款扣费) while legacy `payment_channel` holds 支付方式
  labels (学校二维码/公司二维码/对公户). Aligning them requires administrator dictionary decisions for
  five legacy labels with no enabled entry, so it is a separate, explicitly authorized change.
- **`zsjos_performance_attribution.source_group` (353 rows read `inbound`).** It is derived from
  `source_type`, but rows carrying `org_source='frozen'` are the fact-time organization snapshot by
  contract. Recomputing the grouping changes a business metric, not a data error, and needs approval.
- **`sales_stage` for the 250 won Leads**, the 41 empty `lead_category_label_snapshot` rows,
  `counted_at`, ownership, assignment history, business status, amounts, dictionaries, menus,
  permissions and roles.

## Apply on a fresh or upgrade environment

The migration is a numbered Core migration and is discovered by the repository runner. On the
production host, apply it with the same path used for every other Core migration (see
`docs/operations/production-deployment.md`):

```bash
cd /opt/zsjos
ZSJOS_DB_MODULES=core ./zsjos-db migrate      # uses the db-migrator image and records both ledgers
```

Do not export `ZSJOS_DB_SKIP_CHECKSUM`. The file registers its own checksum as
`SHA2('V298__legacy_identity_and_label_repair.sql',256)` with `release_version='baseline'`, matching
the V295/V297 precedent, and the runner exempts `legacy`/`baseline` rows from file-byte comparison.

For a host without the runner, the file is self-contained and can be executed directly through an
utf8mb4 client. It rewrites nothing else and is safe to replay:

```bash
mysql --default-character-set=utf8mb4 -h "$HOST" -u "$USER" -p "$DATABASE" \
  < script/sql/mysql/migrations/V298__legacy_identity_and_label_repair.sql
```

## Verification

Run after applying, on the target database:

```sql
-- section 1 residual: 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0'
  AND source_type='internal_new_media' AND owner_identity='education';
-- section 2 residual: 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND invalid_reason IN
  ('wechat_add_failed','other','duplicate','no_answer','no_need',
   'refused','empty_number','not_target','fake_info','age_ineligible');
-- section 3 residual: 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0'
  AND source_channel_label_snapshot IS NULL AND source_channel_id='其他';
-- UTF-8: the HEX must decode as UTF-8, not as mojibake
SELECT invalid_reason, invalid_reason_label_snapshot,
       HEX(invalid_reason_label_snapshot) FROM zsjos_lead
 WHERE deleted=b'0' AND invalid_reason='微信号添加不上' LIMIT 1;
-- both ledgers must carry V298
SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V298';
SELECT version FROM zsjos_schema_version WHERE version='V298';
```

Re-running the migration must report `0 / 0 / 0` and add no new ledger row.

## Rollback

There is no automatic rollback. Each section is reversed by the predicate it corrected:

```sql
-- section 3
UPDATE zsjos_lead SET source_channel_label_snapshot=NULL
 WHERE deleted=b'0' AND source_channel_id='其他' AND source_channel_label_snapshot='其他'
   AND updater='V298';
-- section 1
UPDATE zsjos_lead SET source_type='internal_new_media'
 WHERE deleted=b'0' AND source_type='education_self_sourced'
   AND owner_identity='education' AND submission_idempotency_key LIKE 'legacy-%';
-- section 2: reverse the ten pairs
UPDATE zsjos_lead SET invalid_reason = CASE invalid_reason
    WHEN '微信号添加不上' THEN 'wechat_add_failed'
    WHEN '其他' THEN 'other'
    WHEN '重复客资' THEN 'duplicate'
    WHEN '电话多次未接通' THEN 'no_answer'
    WHEN '无相关需求' THEN 'no_need'
    WHEN '客户明确拒绝' THEN 'refused'
    WHEN '空号/停机' THEN 'empty_number'
    WHEN '非目标客户' THEN 'not_target'
    WHEN '信息虚假' THEN 'fake_info'
    WHEN '年龄不符合报考条件' THEN 'age_ineligible' END
 WHERE deleted=b'0' AND updater='V298' AND invalid_reason IN
   ('微信号添加不上','其他','重复客资','电话多次未接通','无相关需求',
    '客户明确拒绝','空号/停机','非目标客户','信息虚假','年龄不符合报考条件');
```

Section 2's reverse is limited to rows this migration wrote, identified by `updater='V298'`. Section 1's
reverse is limited by the legacy submission marker, because the 30 rows that already read
`education_self_sourced` before this migration were not written by it.


## 2026-10-11 客户端重复执行修订

V298 在 V309 之后，仅在双版本记录齐全、旧标签后验通过，且保留的新媒体来源有 V309 修复证据支持时零写入返回；不会重新按负责人身份覆盖来源。

仅面向本地未发布开发脚本；保留原台账、备份、业务数据和部署校验值审查边界。此节更新前文对“重复执行一律检查整行”的约定，实际写入恢复约束不变。生成方式、执行顺序与隔离验证见 [数据库迁移操作](database-migrations.md#replaying-v296v310-from-a-gui-client)。
