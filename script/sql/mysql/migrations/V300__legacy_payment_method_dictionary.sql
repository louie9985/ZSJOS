-- UTF-8. Core V300; execute after V299 in both ledgers.
--
-- Scope: complete the order payment correction started in V299 by supplying the four missing
-- dictionary entries and backfilling the 18 imported orders that had no value to hold.
--
-- Background. V299 realigned the two crossed payment columns of 326 imported orders and preserved
-- each order's recorded historical label on zsjos_order. Four legacy 支付方式 values had no enabled
-- entry in zsjos_order_payment_method, so V299 left payment_method NULL and stored the recorded
-- label in payment_method_label_snapshot:
--   小鹅通线上支付 15, 中世健网校微信小店（视频号小店） 1, 复训 1, 畅学卡扣款（用于畅学卡学员开课） 1.
-- Adding dictionary entries is an administrator decision, which is now confirmed. The approved
-- entries are recorded in the separately reviewed, versioned file
-- script/sql/mysql/dictionary-data/order-payment-legacy-labels.sql, sourced below. Four options are
-- added to zsjos_order_payment_method (sorts 100/110/120/130, values
-- xiaoe_online / wechat_shop / retraining / learning_card_deduction); nothing existing is renamed,
-- disabled or deleted.
--
-- The 18 orders are then backfilled from the label V299 already stored, so the migration does not
-- depend on the imported legacy database, which production does not have. The predicate is
-- naturally idempotent: a row whose value has been set no longer matches payment_method IS NULL.
--
-- Repeatable: the dictionary seed carries a one-time marker and skips any value that already
-- exists; the backfill only matches a NULL value with one of the four recorded labels. Neither
-- overwrites an administrator's later rename, disable or delete.
--
-- Rollback: the pre-images under repair_key 'order-payment-legacy-label-backfill-20261010' restore
-- the value column; the label column was already populated by V299 and is not changed here. The
-- dictionary entries are retained on rollback, because deleting them would strand the recorded
-- selections. Exact statements are in docs/operations/legacy-order-payment-realign.md.
SET NAMES utf8mb4;
SOURCE script/sql/mysql/dictionary-data/order-payment-legacy-labels.sql;

DROP PROCEDURE IF EXISTS zsjos_v300_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v300_apply()
BEGIN
  DECLARE v_rows INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V299')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V299') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V300 requires Core V299 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_order_payment_method'
        AND deleted=b'0' AND status=0
        AND value IN ('xiaoe_online','wechat_shop','retraining','learning_card_deduction')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V300 requires the four approved historic payment-method entries';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_order' AND column_name='payment_method_label_snapshot') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V300 requires the V299 payment method label column';
  END IF;

  START TRANSACTION;

  DROP TEMPORARY TABLE IF EXISTS v300_label_map;
  CREATE TEMPORARY TABLE v300_label_map (
    label_snapshot varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    dict_value     varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    PRIMARY KEY (label_snapshot)
  ) ENGINE=InnoDB;
  INSERT INTO v300_label_map(label_snapshot,dict_value) VALUES
    ('小鹅通线上支付','xiaoe_online'),
    ('中世健网校微信小店（视频号小店）','wechat_shop'),
    ('复训','retraining'),
    ('畅学卡扣款（用于畅学卡学员开课）','learning_card_deduction');

  -- Pre-images, captured once per row and never overwritten on replay.
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-payment-legacy-label-backfill-20261010','zsjos_order',o.id,'payment_method',o.payment_method
    FROM zsjos_order o JOIN v300_label_map m ON m.label_snapshot=o.payment_method_label_snapshot
   WHERE o.deleted=b'0' AND o.payment_method IS NULL
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-payment-legacy-label-backfill-20261010'
                       AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='payment_method');

  -- Both sides of the assignment come from the joined map, never from the column being written.
  UPDATE zsjos_order o JOIN v300_label_map m ON m.label_snapshot=o.payment_method_label_snapshot
     SET o.payment_method=m.dict_value, o.updater='V300', o.update_time=NOW()
   WHERE o.deleted=b'0' AND o.payment_method IS NULL;
  SET v_rows = ROW_COUNT();

  IF EXISTS(SELECT 1 FROM zsjos_order o JOIN v300_label_map m
              ON m.label_snapshot=o.payment_method_label_snapshot
            WHERE o.deleted=b'0' AND o.payment_method IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V300 legacy payment-method backfill incomplete';
  END IF;
  -- The empty string is the one legacy row whose payment_method was recorded blank; V299 left
  -- it unchanged and it is out of scope here.
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE deleted=b'0' AND payment_method IS NOT NULL
              AND payment_method<>'' AND payment_method NOT IN
              ('prepaid_recharge','balance_cash','course_exchange','company_alipay','finance_wechat',
               'company_qr','learning_qr','80','nopay','xiaoe_online','wechat_shop','retraining',
               'learning_card_deduction')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V300 a payment_method value outside the dictionary remains';
  END IF;

  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V300','Legacy payment-method dictionary and backfill',SHA2('V300__legacy_payment_method_dictionary.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V300');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V300','Legacy payment-method dictionary and backfill',SHA2('V300__legacy_payment_method_dictionary.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V300');
  COMMIT;

  SELECT v_rows AS legacy_payment_rows_backfilled;
END$$
DELIMITER ;
CALL zsjos_v300_apply();
DROP PROCEDURE zsjos_v300_apply;
