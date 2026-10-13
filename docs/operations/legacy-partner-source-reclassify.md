# Legacy partner source reclassification (Core V302)

## Scope and diagnosis

The 2026-09-16 legacy merge mapped the old system's single source column straight onto
`zsjos_lead.source_type`. The old column carried only 兼职 and 新媒体 meanings, so every Lead it
carried arrived as `source_type='partner'` — including 4112 Leads whose recorded provider is an
internal employee (`provider_owner_type='system_user'`, `provider_owner_id` resolving to a live
`system_users` row, `partner_id` NULL). The management list renders `source_type` as the 来源 column,
so those rows displayed **兼职提交** beside an employee name.

It was a display and classification defect, not a data-loss one. Every downstream path already
treated these rows as employee submissions:

| Path | Why it was already correct |
|---|---|
| Cashback eligibility | `CashbackServiceImpl.eligibleLead` requires `provider_owner_type='partner'`, so these rows never qualified |
| Withdrawal | reads the cashback row's own `partner_id`, not the Lead |
| Media screen | `LeadMapper` maps `provider_owner_type='partner'` to `part_time`; these rows were counted `direct` |
| Employee submitter filter | `AdvancedFilterRelations` already excludes a Partner subject and falls through to `source_user_id` |
| Performance grouping | `partner` and `internal_new_media` both map to `inbound` |

The old system's own 兼职 name field confirms it never identified a distinct submitter: on 4721
`part_time` rows it equals the submitter's own name 4041 times. Of the 4112 affected Leads, 24
distinct employees are involved, 12 of them former part-timers promoted on 2026-07-04 and the other
12 never present in the partner roster at all.

The business decision, confirmed 2026-10-10, is that these people are new-media employees today:
the earlier system simply had no separate new-media role. The recorded source is therefore corrected
to match the recorded provider.

## What V302 changes

`script/sql/mysql/migrations/V302__legacy_partner_source_reclassify.sql`, applied after Core V301 in
both ledgers, does two things in one failure-controlled procedure.

**Section 1 — `zsjos_lead.source_type`.** 4112 rows changed from `partner` to `internal_new_media`:

```sql
WHERE deleted=b'0' AND source_type='partner'
  AND provider_owner_type='system_user' AND partner_id IS NULL
```

| `source_type` | before | after |
|---|---:|---:|
| `internal_new_media` | 285 | 4397 |
| `partner` | 4821 | 709 |
| `education_self_sourced` | 242 | 242 |
| `sales_self_sourced` | 122 | 122 |
| `others` | 1 | 1 |

Every affected row is a legacy import (`lead_no LIKE 'LD%'`); no new-system Lead is in the set.

**Section 2 — the frozen order profile.** Nine approval rounds carry
`order_snapshot.leadProfile` with `sourceType='partner'`, `sourceLabel='兼职提交'`,
`sourceSubjectType='PARTNER'` and a JSON-null `sourceSubjectId`. Those four keys are rewritten so
the order detail card stops showing 兼职提交 beside an employee name:

| Key | before | after |
|---|---|---|
| `sourceType` | `partner` | `internal_new_media` |
| `sourceLabel` | `兼职提交` | `新媒体提交` |
| `sourceSubjectType` | `PARTNER` | `ADMIN` |
| `sourceSubjectId` | `null` | the Lead's `source_user_id` |

`sourceUserName` already holds the employee name and is left alone. The target shape matches what
`SalesOrderServiceImpl.buildLeadProfile` produces today, so the frozen profile and a freshly built
one now agree. Rounds 121 and 140 belong to genuine partner Leads and are explicitly not matched.

### Why the snapshot rewrite is safe here

- `partner` occurred exactly once in each of the nine snapshots, so a nested `JSON_SET` cannot
  disturb an unrelated key. `order`, `selections`, `orderLabels`, `submitter` and `formalSales` were
  re-inspected after the write and are unchanged.
- No Flowable variable stores the source type: `ACT_HI_VARINST` holds no `NAME_` matching
  `%source%`, and the pending process instance's variables carry only owner identity, persons and
  assignees.
- The write takes both sides from the joined Lead, never from the JSON it is replacing, so it does
  not depend on evaluation order. This is the same trap recorded in the V298 operations document.

## Boundaries

- **The 709 genuine partner Leads keep `source_type='partner'`.** They carry
  `provider_owner_type='partner'` and a live `partner_id`. A postcondition fails the migration if any
  Partner-provider row is left with a different source.
- **Provider and ownership columns are untouched**: `provider_owner_type`, `provider_owner_id`,
  `partner_id`, `source_user_id`, `counted_at`, `owner_user_id`, `owner_identity` and every
  contribution snapshot keep their values. Only the classification and the four snapshot keys move.
- **`zsjos_lead_activation.source_type` is not changed.** Its 33 `partner` rows all carry a non-null
  `partner_id`, so the diagnostic predicate
  (`source_type='partner' AND partner_id IS NULL`) matches zero of them. The column is an activation
  audit record of the identity that triggered the activation, and the 14 rows sharing a reclassified
  Lead were written by partner accounts (ids 39, 56, 74, 93, 123) — keeping them is the honest
  record.
- **`zsjos_lead_duplicate_review` is not changed.** Its single `partner` row references
  `submission_partner_id=3` (吴燕) and is unrelated to the reclassified set.
- **No other table copies the source.** `zsjos_business_audit_log.source_type` holds SYSTEM/ADMIN/
  PARTNER/EXPLICIT/PUBLIC_CALLBACK operation origins and `zsjos_personal_calendar_event.source_type`
  holds MANUAL; neither is a Lead source.
- Order amounts, statuses, approvals, ownership, the Lead status machine and all dictionaries are
  unchanged.

## Apply on a fresh or upgrade environment

The migration is a numbered Core migration and is discovered by the repository runner:

```bash
cd /opt/zsjos
ZSJOS_DB_MODULES=core ./zsjos-db migrate      # uses the db-migrator image and records both ledgers
```

Do not export `ZSJOS_DB_SKIP_CHECKSUM`. The file registers its own checksum as
`SHA2('V302__legacy_partner_source_reclassify.sql',256)` with `release_version='baseline'`, matching
the V298–V301 precedent.

For a host without the runner the file is self-contained:

```bash
mysql --default-character-set=utf8mb4 -h "$HOST" -u "$USER" -p "$DATABASE" \
  < script/sql/mysql/migrations/V302__legacy_partner_source_reclassify.sql
```

It reads only this database; the imported legacy database is not needed.

## Verification

```sql
-- residuals: both must be 0
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND source_type='partner'
  AND provider_owner_type='system_user' AND partner_id IS NULL;
SELECT COUNT(*) FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
  JOIN zsjos_lead l ON l.id=o.lead_id
 WHERE o.deleted=b'0' AND l.deleted=b'0' AND l.provider_owner_type='system_user'
   AND l.partner_id IS NULL AND r.order_snapshot->>'$.leadProfile.sourceType'='partner';
-- the genuine partner population must still be 709
SELECT COUNT(*) FROM zsjos_lead WHERE deleted=b'0' AND source_type='partner'
  AND provider_owner_type='partner' AND partner_id IS NOT NULL;
-- pre-images
SELECT repair_key, column_name, COUNT(*) FROM zsjos_data_repair_backup
 WHERE repair_key IN ('lead-partner-source-reclassify-20261010','order-lead-profile-source-20261010')
 GROUP BY 1,2;
-- UTF-8: 新媒体提交 = E696B0E5AA92E4BD93E68F90E4BAA4
SELECT HEX(r.order_snapshot->>'$.leadProfile.sourceLabel') FROM zsjos_order_approval_round r
 WHERE r.updater='V302' LIMIT 1;
-- both ledgers
SELECT version FROM zsjos_schema_version WHERE version='V302';
SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V302';
```

A replay must report `0 / 0` and add no ledger row and no pre-image.

## Rollback

Pre-images restore both surfaces. The Lead section is a plain column restore:

```sql
UPDATE zsjos_lead l
  JOIN zsjos_data_repair_backup b
    ON b.repair_key='lead-partner-source-reclassify-20261010'
   AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='source_type'
   SET l.source_type=b.old_value;
```

The snapshot keys are stored one pre-image row each, so restore them key by key:

```sql
UPDATE zsjos_order_approval_round r JOIN zsjos_data_repair_backup b
    ON b.repair_key='order-lead-profile-source-20261010'
   AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
   SET r.order_snapshot=JSON_SET(r.order_snapshot,
     '$.leadProfile.sourceType',
       CASE WHEN b.column_name='order_snapshot.leadProfile.sourceType'
            THEN b.old_value ELSE r.order_snapshot->>'$.leadProfile.sourceType' END)
 WHERE b.column_name='order_snapshot.leadProfile.sourceType';
```

Repeat that statement for `sourceLabel`, `sourceSubjectType` and `sourceSubjectId`. For
`sourceSubjectId` the pre-image of a JSON null is recorded as the literal `<json-null>`; restore it
with `JSON_EXTRACT('null','$')`, not with a quoted string. `JSON_SET(col, path, NULL)` stores a JSON
null, so a bare SQL `NULL` must never be passed for that key.

After a rollback, remove the pre-images:

```sql
DELETE FROM zsjos_data_repair_backup
 WHERE repair_key IN ('lead-partner-source-reclassify-20261010','order-lead-profile-source-20261010');
```

Rolling back the data does not require reverting any code: the projection reads whatever the row and
the frozen snapshot hold.


## 2026-10-13 初始化兼容修订

V298/V299/V302/V303 的历史字典、订单和账号前置条件仅对存在相关业务记录的数据库强制执行；
严格空库不预造历史数据，有业务数据和部分修复状态仍保留原保护。V299 在空 Lead/Order 库中
建立与既有修复工具一致的空证据表；有业务订单却缺少证据表仍拒绝执行。V303 重试前清理
本连接临时映射表；V298/V304/V309 临时表显式使用 utf8mb4_unicode_ci，兼容 MySQL 8
数据库默认 utf8mb4_0900_ai_ci。编号不变，已应用文件的 checksum 不自动改写。

本次范围为隔离副本与源码修复，实际测试库保持只读。部署前必须审查已部署文件版本和
对应 checksum；不能用批量 reconcile 冒充迁移完成。完整恢复边界、验证和清理规则见
[测试库迁移恢复](test-migration-recovery-20261013.md)。
