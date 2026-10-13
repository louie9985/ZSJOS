# Legacy order payment column realignment (Core V299)

## Scope and diagnosis

The parttimecrm import wrote the two order payment columns of 326 orders with the legacy *labels*
instead of dictionary values, and it **crossed the two legacy columns**:

| Legacy column | Legacy meaning | Was written into | Should be |
|---|---|---|---|
| `orders_order.payment_method` | 缴费方式 (零售缴费 / 底价缴费 / 预付款扣费) | `zsjos_order.payment_method` | `zsjos_order.fee_mode` |
| `orders_order.payment_channel` | 支付方式 (学校二维码 / 公司二维码 / 充值预扣 / 对公户 / …) | `zsjos_order.fee_mode` | `zsjos_order.payment_method` |

Both dictionary value columns were therefore wrong in both directions. Before V299:

| Column | Empty | Holding a label from the other dictionary |
|---|---:|---:|
| `zsjos_order.fee_mode` | 261 | 65 |
| `zsjos_order.payment_method` | 219 | 91 |

A second defect made the values invisible even where they existed. The only label store is
`zsjos_order_approval_round.order_snapshot.orderLabels`, and it is empty for these orders (a legacy
round carries only `{"legacyStatus","legacyOrderId"}`). `SalesOrderSnapshot.apply` marks
`feeMode`/`paymentMethod` as `history_not_recorded`, and the order detail card and the finance export
render `历史未记录`. The recorded selection was never persisted in a form the current projection
could read.

## What V299 changes

`script/sql/mysql/migrations/V299__order_payment_column_realign.sql`, applied after Core V298 in both
ledgers, does three things inside one failure-controlled procedure:

1. Adds two nullable columns to `zsjos_order` — `fee_mode_label_snapshot` and
   `payment_method_label_snapshot`, both `varchar(64)` `utf8mb4_unicode_ci`.
2. Captures the pre-images of both value columns into `zsjos_data_repair_backup` under
   `repair_key = 'order-payment-column-realign-20261009'` (326 rows for `fee_mode`, 310 for
   `payment_method`; a column already equal to its target is not captured).
3. Realigns both value columns from a frozen 326-row map and fills the two label columns from the
   legacy label text.

```sql
-- 缴费方式
UPDATE zsjos_order o JOIN v299_order_map m ON m.order_no=o.order_no
   SET o.fee_mode=m.fee_code, o.fee_mode_label_snapshot=m.fee_label, ...
-- 支付方式
UPDATE zsjos_order o JOIN v299_order_map m ON m.order_no=o.order_no
   SET o.payment_method=CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END,
       o.payment_method_label_snapshot=m.pay_label, ...
```

The stored label is the text selected at the time, **not** a re-resolution of today's dictionary. The
six 对公户 orders keep the label `对公户` while the current dictionary label for value `80` is
`对公汇款`.

### Mapping authority

The legacy parttimecrm database (`orders_order.payment_method` / `payment_channel`) is the source.
Production has no copy of it, so the 326 order-level targets are **frozen into the migration as
explicit data** keyed by `order_no`, rather than re-derived at apply time. The map is total for the
affected set: every target order carries both a fee-mode code and a payment-method code (or an
explicit "leave NULL").

| Legacy 缴费方式 | fee_mode value | rows |
|---|---|---:|
| 零售缴费 | `retail` | 309 |
| 底价缴费 | `floor_price` | 9 |
| 预付款扣费 | `prepaid_deduction` | 8 |

| Legacy 支付方式 | payment_method value | rows |
|---|---|---:|
| 学校二维码 | `learning_qr` | 250 |
| 公司二维码 | `company_qr` | 40 |
| 充值预扣 | `prepaid_recharge` | 7 |
| 对公户 | `80` | 6 |
| 等价/退差价换课 | `course_exchange` | 5 |

### The 18 orders with no dictionary entry (resolved by V300)

Four legacy 支付方式 labels had no enabled entry in `zsjos_order_payment_method`. V299 left the value
`NULL` and stored the recorded legacy label in `payment_method_label_snapshot`:

| Legacy label | orders | approved value (V300) |
|---|---:|---|
| 小鹅通线上支付 | 15 | `xiaoe_online` |
| 中世健网校微信小店（视频号小店） | 1 | `wechat_shop` |
| 复训 | 1 | `retraining` |
| 畅学卡扣款（用于畅学卡学员开课） | 1 | `learning_card_deduction` |

The administrator decision was confirmed, so **V300** adds those four options (sorts 100/110/120/130),
seeded by the separately reviewed, versioned file
`script/sql/mysql/dictionary-data/order-payment-legacy-labels.sql`, and backfills the 18 orders from
the label already stored by V299. For 复训 and 畅学卡扣款 the crossed 缴费方式 value that previously
sat in `payment_method` was cleared by V299; their `fee_mode` is correctly `retail`.

One further order, `OR202606040001`, records an empty-string `payment_method` (`creator='migration'`).
It is not one of these four labels and is left unchanged by V299 and V300; it is a separate
pre-existing gap, reported and not corrected.

## Deliberately out of scope

- **The 133 orders that already hold a valid dictionary value.** Each is new-system and has no legacy
  row, so nothing is overwritten. 120 `retail`, 9 `floor_price`, 4 `nomoney` on the fee side; 105
  `learning_qr`, 18 `company_qr`, 6 `80`, 2 `course_exchange`, 2 `nopay` on the payment side.
- **The 242 orders with no legacy row** and the **9 legacy orders with no counterpart** in this
  system (OR202609160016, OR202609170002, OR202609170016, OR202609180001, OR202609180004,
  OR202609180005, OR202609180025, OR202609200028, OR202609200029).
- Order amounts, statuses, approvals, ownership, `zsjos_order_approval_round.order_snapshot`, the
  lead-side corrections (V298), menus and permissions. V299 adds no dictionary data; the four
  entries the 18 orders need are added by V300, listed under "The 18 orders with no dictionary
  entry" above, and no existing dictionary entry is renamed, disabled or deleted.

## V300 — legacy payment-method dictionary and backfill

`script/sql/mysql/migrations/V300__legacy_payment_method_dictionary.sql`, applied after V299 in both
ledgers. It sources the approved dictionary file and then, inside one failure-controlled procedure,
captures pre-images into `zsjos_data_repair_backup` under `repair_key =
'order-payment-legacy-label-backfill-20261010'` and fills `payment_method` on the 18 orders from the
label V299 stored. The predicate is `payment_method IS NULL` plus one of the four labels, so it is
naturally idempotent and never depends on the imported legacy database.

```
legacy_payment_rows_backfilled = 18
```

## V301 — performance source-group recompute

`script/sql/mysql/migrations/V301__performance_source_group_recompute.sql`, applied after V300 in both
ledgers. V298 corrected 142 academic self-sourced Leads from `internal_new_media` to
`education_self_sourced`, but `zsjos_performance_attribution.source_group` had been written by offline
backfills while those Leads still read `internal_new_media`, so 331 rows carried the wrong population.
V301 recomputes `source_group` for exactly those rows using the canonical rule, bounded to rows that
are **not** fact-time:

| Rule | value |
|---|---|
| `LeadConstants.isSelfSourced(source_type)` | `self` |
| `internal_new_media` / `partner` | `inbound` |
| ORDER fact with `order_type='repurchase'` | `repurchase` (checked first, unchanged) |
| otherwise | `unknown` |

Rows with `org_source='frozen'` are the fact-time snapshot by contract and are **never** rewritten;
rows whose Lead no longer exists are not matched. Pre-images go to `repair_key =
'performance-source-group-20261010'`.

```
source_group_rows_recomputed = 331
```

Recomputing changes a business metric, which is why it was held for an explicit decision rather than
folded into V299.

## Apply on a fresh or upgrade environment

The migration is a numbered Core migration and is discovered by the repository runner. On the
production host, apply it with the same path used for every other Core migration (see
`docs/operations/production-deployment.md`):

```bash
cd /opt/zsjos
ZSJOS_DB_MODULES=core ./zsjos-db migrate      # uses the db-migrator image and records both ledgers
```

Do not export `ZSJOS_DB_SKIP_CHECKSUM`. The file registers its own checksum as
`SHA2('V299__order_payment_column_realign.sql',256)` with `release_version='baseline'`, matching the
V298/V295 precedent, and the runner exempts `legacy`/`baseline` rows from file-byte comparison.

For a host without the runner, the file is self-contained and can be executed directly through a
utf8mb4 client:

```bash
mysql --default-character-set=utf8mb4 -h "$HOST" -u "$USER" -p "$DATABASE" \
  < script/sql/mysql/migrations/V299__order_payment_column_realign.sql
```

The map is embedded, so no legacy database is needed on the target. Foreign key checks may be left
enabled: V299 never inserts or deletes rows.

## Verification

Run after applying, on the target database:

```sql
-- residuals: both must be 0
SELECT COUNT(*) FROM zsjos_order WHERE deleted=b'0' AND fee_mode IS NOT NULL
  AND fee_mode NOT IN ('retail','floor_price','prepaid_deduction','nomoney');
SELECT COUNT(*) FROM zsjos_order WHERE deleted=b'0' AND payment_method IS NOT NULL
  AND payment_method<>'' AND payment_method NOT IN
  ('prepaid_recharge','balance_cash','course_exchange','company_alipay','finance_wechat',
   'company_qr','learning_qr','80','nopay');
-- pre-images captured: 326 and 310
SELECT column_name, COUNT(*) FROM zsjos_data_repair_backup
 WHERE repair_key='order-payment-column-realign-20261009' GROUP BY column_name;
-- UTF-8: the HEX must decode as UTF-8, not as mojibake
SELECT fee_mode_label_snapshot, HEX(fee_mode_label_snapshot) FROM zsjos_order
 WHERE order_no='OR202609200023';   -- 零售缴费 = E99BB6E594AEE7BCB4E8B4B9
-- both ledgers must carry V299
SELECT version FROM zsjos_schema_version WHERE version='V299';
SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V299';
```

Re-running the migration must report `326 / 0 / 0` and add no new ledger row or pre-image.

## Rollback

There is no automatic rollback. The pre-images restore both value columns to their exact pre-V299
state, including NULLs:

```sql
UPDATE zsjos_order o
  JOIN zsjos_data_repair_backup b
    ON b.repair_key='order-payment-column-realign-20261009'
   AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='fee_mode'
   SET o.fee_mode=b.old_value;
UPDATE zsjos_order o
  JOIN zsjos_data_repair_backup b
    ON b.repair_key='order-payment-column-realign-20261009'
   AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='payment_method'
   SET o.payment_method=b.old_value;
```

The two label columns were empty before V299, so clearing them is optional:

```sql
UPDATE zsjos_order SET fee_mode_label_snapshot=NULL, payment_method_label_snapshot=NULL
 WHERE updater='V299';
```

### Rollback for V300 and V301

```sql
-- V300: restore the value column (the label column is left as V299 recorded it)
UPDATE zsjos_order o
  JOIN zsjos_data_repair_backup b
    ON b.repair_key='order-payment-legacy-label-backfill-20261010'
   AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='payment_method'
   SET o.payment_method=b.old_value;

-- V301: restore the prior source_group
UPDATE zsjos_performance_attribution p
  JOIN zsjos_data_repair_backup b
    ON b.repair_key='performance-source-group-20261010'
   AND b.table_name='zsjos_performance_attribution' AND b.row_id=p.id AND b.column_name='source_group'
   SET p.source_group=b.old_value;
```

The four dictionary entries added by V300 are retained on rollback: deleting them would strand the 18
recorded selections. After a rollback, remove the pre-images:

```sql
DELETE FROM zsjos_data_repair_backup WHERE repair_key IN
  ('order-payment-legacy-label-backfill-20261010','performance-source-group-20261010');
```

After a rollback, remove the pre-images so a later replay cannot restore a stale state:

```sql
DELETE FROM zsjos_data_repair_backup WHERE repair_key='order-payment-column-realign-20261009';
```

Dropping the two columns is not required to roll back the data. If the code is reverted without the
columns, `SalesOrderServiceImpl` reads them through MyBatis-Plus and would fail, so revert the code
and the schema together.

## Projection fallback (backend, same change)

`SalesOrderServiceImpl` now falls back to the recorded order label when the round snapshot has none,
and clears the corresponding `history_not_recorded` marker so the flag matches the value returned:

- order detail `convert(...)`: `applyRecordedFeeModeLabel` / `applyRecordedPaymentMethodLabel`
- management list `convertListItem(...)`: `setRecordedPaymentLabels`
- finance export: `paymentMethod` falls back to `payment_method_label_snapshot`

The round snapshot stays the primary source; the order columns are only read when it is empty, so
new-system orders are unaffected.


## 2026-10-11 客户端重复执行修订

V299 识别 V300 已完成的历史支付方式补齐，按原静态历史映射及逐单备份核验后零写入返回，避免先清空 18 笔支付方式再由 V300 填回。V301 在 V309 后验证原业务后验及双台账后零写入返回。

仅面向本地未发布开发脚本；保留原台账、备份、业务数据和部署校验值审查边界。此节更新前文对“重复执行一律检查整行”的约定，实际写入恢复约束不变。生成方式、执行顺序与隔离验证见 [数据库迁移操作](database-migrations.md#replaying-v296v310-from-a-gui-client)。


## 2026-10-13 初始化兼容修订

V298/V299/V302/V303 的历史字典、订单和账号前置条件仅对存在相关业务记录的数据库强制执行；
严格空库不预造历史数据，有业务数据和部分修复状态仍保留原保护。V299 在空 Lead/Order 库中
建立与既有修复工具一致的空证据表；有业务订单却缺少证据表仍拒绝执行。V303 重试前清理
本连接临时映射表；V298/V304/V309 临时表显式使用 utf8mb4_unicode_ci，兼容 MySQL 8
数据库默认 utf8mb4_0900_ai_ci。编号不变，已应用文件的 checksum 不自动改写。

本次范围为隔离副本与源码修复，实际测试库保持只读。部署前必须审查已部署文件版本和
对应 checksum；不能用批量 reconcile 冒充迁移完成。完整恢复边界、验证和清理规则见
[测试库迁移恢复](test-migration-recovery-20261013.md)。
