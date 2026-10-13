-- UTF-8. Core V299; execute after V298 in both ledgers.
--
-- Scope: two coupled corrections of historical imported order facts.
--
--   1) The two payment columns of 326 imported orders hold the legacy *labels* instead of
--      dictionary values, and the two legacy columns were crossed on import:
--        legacy orders_order.payment_method  (缴费方式: 零售缴费/底价缴费/预付款扣费)
--          was written into zsjos_order.payment_method
--        legacy orders_order.payment_channel (支付方式: 学校二维码/公司二维码/充值预扣/对公户/...)
--          was written into zsjos_order.fee_mode
--      The dictionary value columns are therefore wrong in both directions: 261 orders have an
--      empty fee_mode with a value recoverable from the legacy 缴费方式, 65 orders hold a 支付方式
--      label inside fee_mode, 219 orders have an empty payment_method with a value recoverable
--      from the legacy 支付方式, and 91 orders hold a 缴费方式 label inside payment_method. Both
--      value columns are normalized to the enabled dictionary values of zsjos_order_fee_mode and
--      zsjos_order_payment_method.
--
--   2) The recorded historical labels are preserved. zsjos_order_approval_round.order_snapshot
--      .orderLabels is the only label store and is empty for these orders, so the order detail
--      card and the finance export fall back to "历史未记录" even though the legacy system recorded
--      the selection. Two nullable label columns are added to zsjos_order and filled from the
--      legacy label text, giving the historical projection a recorded snapshot to display. The
--      stored label is the text selected at the time, not a re-resolution of today's dictionary:
--      the five 对公户 orders keep 对公户 while the current dictionary label for value 80 is
--      对公汇款.
--
-- Out of scope and intentionally unchanged:
--   * 18 orders whose legacy 支付方式 has no enabled dictionary entry (小鹅通线上支付 15,
--     中世健网校微信小店（视频号小店） 1, 复训 1, 畅学卡扣款（用于畅学卡学员开课） 1). Adding
--     dictionary entries is an administrator decision, so their payment_method value is left NULL
--     and the recorded legacy label is stored in the label column. For 复训 and 畅学卡扣款 the
--     crossed 缴费方式 value that previously sat in payment_method is cleared.
--   * The 133 orders that already hold a valid dictionary value: each is new-system and has no
--     legacy row, so nothing is overwritten.
--   * The 242 orders with no legacy row, the 9 legacy orders with no counterpart in this system,
--     order amounts, statuses, approvals, ownership, dictionaries, menus and permissions.
--
-- Source of the mapping: the legacy parttimecrm database (orders_order.payment_method /
-- payment_channel). Production has no copy of that database, so the 326 order-level targets are
-- frozen into this migration as explicit data instead of being re-derived at apply time.
--
-- Repeatable: every assignment is bounded by a null-safe inequality against its target, so a
-- second execution changes 0 rows and registers no new version. Pre-images are written only for a
-- (repair_key, table, row, column) with no earlier capture, so replay never overwrites the
-- original state used for rollback.
--
-- Rollback: the pre-images under repair_key 'order-payment-column-realign-20261009' restore both
-- value columns; the two label columns were empty before this migration. The exact statements are
-- in docs/operations/legacy-order-payment-realign.md.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v299_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v299_apply()
apply_body: BEGIN
  DECLARE v_map INT DEFAULT 0;
  DECLARE v_fee INT DEFAULT 0;
  DECLARE v_pay INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V298')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V298') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 requires Core V298 in both ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name='zsjos_order' AND engine='InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 requires the zsjos_order table';
  END IF;
  -- Fresh installs have no repair pre-images. Create the evidence facility before its first use;
  -- a populated installation missing prior evidence remains blocked.
  IF NOT EXISTS(SELECT 1 FROM zsjos_order) AND NOT EXISTS(SELECT 1 FROM zsjos_lead)
     AND NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                    AND table_name='zsjos_data_repair_backup') THEN
    CREATE TABLE zsjos_data_repair_backup (
      id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
      repair_key varchar(100) NOT NULL, table_name varchar(64) NOT NULL,
      row_id bigint NOT NULL, column_name varchar(64) NOT NULL,
      old_value varchar(512) NULL, backed_up_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
      KEY idx_mark_row (repair_key,table_name,row_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name='zsjos_data_repair_backup') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 requires zsjos_data_repair_backup for pre-images';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE deleted=b'0') AND
     (SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_order_fee_mode'
        AND deleted=b'0' AND status=0
        AND value IN ('retail','floor_price','prepaid_deduction','nomoney')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 requires the four enabled fee-mode dictionary entries';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE deleted=b'0') AND
     (SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_order_payment_method'
        AND deleted=b'0' AND status=0
        AND value IN ('prepaid_recharge','balance_cash','course_exchange','company_alipay',
                      'finance_wechat','company_qr','learning_qr','80','nopay')) <> 9 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 requires the nine enabled payment-method dictionary entries';
  END IF;

  -- MySQL DDL commits implicitly, so each column is added only when absent and the structure is
  -- verified afterwards; an existing version marker is never treated as proof of completion.
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_order' AND column_name='fee_mode_label_snapshot') THEN
    ALTER TABLE zsjos_order ADD COLUMN fee_mode_label_snapshot varchar(64)
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
      COMMENT '缴费方式历史标签快照';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_order' AND column_name='payment_method_label_snapshot') THEN
    ALTER TABLE zsjos_order ADD COLUMN payment_method_label_snapshot varchar(64)
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
      COMMENT '支付方式历史标签快照';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_order'
        AND column_name IN ('fee_mode_label_snapshot','payment_method_label_snapshot')
        AND data_type='varchar' AND character_maximum_length=64 AND is_nullable='YES'
        AND character_set_name='utf8mb4' AND collation_name='utf8mb4_unicode_ci') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 label snapshot column contract mismatch';
  END IF;

  START TRANSACTION;

  DROP TEMPORARY TABLE IF EXISTS v299_order_map;
  CREATE TEMPORARY TABLE v299_order_map (
    order_no  varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    fee_code  varchar(64) COLLATE utf8mb4_unicode_ci NULL,
    fee_label varchar(64) COLLATE utf8mb4_unicode_ci NULL,
    pay_code  varchar(64) COLLATE utf8mb4_unicode_ci NULL,
    pay_null  tinyint NOT NULL DEFAULT 0,
    pay_label varchar(64) COLLATE utf8mb4_unicode_ci NULL,
    PRIMARY KEY (order_no)
  ) ENGINE=InnoDB;

  INSERT INTO v299_order_map(order_no,fee_code,fee_label,pay_code,pay_null,pay_label) VALUES
    ('OR202608100001','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608100002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608100003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608100004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608110001','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608110002','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608110003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608110004','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608110005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608110006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608120001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608120002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608120003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608120004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608130001','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608140001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608140002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608140003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608140004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608140005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608160001','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608170001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608170002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608170003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608170004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608180001','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608180002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608200001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608200002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608200003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608200004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608210001','retail','零售缴费',NULL,1,'中世健网校微信小店（视频号小店）'),
    ('OR202608210002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608210003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608210004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608220001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240007','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608240008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608240011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608250001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608250002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608250003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608250004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608250005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608250006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608250007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260001','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608260002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608260009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608270001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608270002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608270003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608270004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608270005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608270006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608280001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280005','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608280006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280007','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608280008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280010','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608280011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608280012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608290001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608290002','retail','零售缴费','course_exchange',0,'等价/退差价换课'),
    ('OR202608290003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608290004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300002','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608300003','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608300004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300008','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608300009','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608300010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300014','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300015','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608300016','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608310001','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608310002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608310003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608310004','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608310005','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608310006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608310007','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202608310008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608310009','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608310010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608310011','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202608310012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202608310013','floor_price','底价缴费','company_qr',0,'公司二维码'),
    ('OR202608310014','floor_price','底价缴费','company_qr',0,'公司二维码'),
    ('OR202609010001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609010008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609020001','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609020002','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609020003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609020004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609020005','retail','零售缴费','course_exchange',0,'等价/退差价换课'),
    ('OR202609020006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609020007','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609030001','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609030002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609030003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609030004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040001','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609040002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040003','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609040004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040005','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609040006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609040007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040009','retail','零售缴费','course_exchange',0,'等价/退差价换课'),
    ('OR202609040010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609040014','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609050001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609050002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609050003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609050004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609050005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609050006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609060001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609060002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609070001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609070002','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609070003','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609070004','floor_price','底价缴费','learning_qr',0,'学校二维码'),
    ('OR202609070005','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609070006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609070007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609070008','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609070009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609070010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609070011','retail','零售缴费','course_exchange',0,'等价/退差价换课'),
    ('OR202609070012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609070013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609080001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609080002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609080003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609090007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090008','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609090009','prepaid_deduction','预付款扣费','learning_qr',0,'学校二维码'),
    ('OR202609090010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609090013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609100001','floor_price','底价缴费','learning_qr',0,'学校二维码'),
    ('OR202609100002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609100003','floor_price','底价缴费','learning_qr',0,'学校二维码'),
    ('OR202609100004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609100005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110001','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202609110002','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202609110003','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202609110004','retail','零售缴费',NULL,1,'小鹅通线上支付'),
    ('OR202609110005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110007','floor_price','底价缴费','learning_qr',0,'学校二维码'),
    ('OR202609110008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110009','floor_price','底价缴费','learning_qr',0,'学校二维码'),
    ('OR202609110010','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609110011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609110014','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609110015','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609110016','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609110017','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609110018','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609120001','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609120002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609120003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609130001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609130002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609130003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140001','floor_price','底价缴费','learning_qr',0,'学校二维码'),
    ('OR202609140002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140005','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609140006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609140010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150006','floor_price','底价缴费','80',0,'对公户'),
    ('OR202609150007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609150013','retail','零售缴费','80',0,'对公户'),
    ('OR202609150014','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160006','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609160007','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609160008','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609160009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160014','retail','零售缴费','course_exchange',0,'等价/退差价换课'),
    ('OR202609160015','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160017','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609160018','retail','零售缴费',NULL,1,'复训'),
    ('OR202609160019','retail','零售缴费',NULL,1,'畅学卡扣款（用于畅学卡学员开课）'),
    ('OR202609170001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170010','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609170011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170014','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170015','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170017','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609170018','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180012','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180013','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180014','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180015','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180016','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180017','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180018','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180019','prepaid_deduction','预付款扣费','prepaid_recharge',0,'充值预扣'),
    ('OR202609180020','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180021','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180022','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180023','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180024','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609180026','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609190001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609190002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609190003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609190004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200001','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200002','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200003','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200004','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200005','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200006','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200007','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200008','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200009','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200010','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200011','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200012','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609200013','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609200014','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200015','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200016','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200017','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200018','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200019','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200020','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200021','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609200022','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('OR202609200023','retail','零售缴费','80',0,'对公户'),
    ('OR202609200024','retail','零售缴费','80',0,'对公户'),
    ('OR202609200025','retail','零售缴费','company_qr',0,'公司二维码'),
    ('OR202609200026','retail','零售缴费','80',0,'对公户'),
    ('OR202609200027','retail','零售缴费','80',0,'对公户'),
    ('OR202609200030','retail','零售缴费','learning_qr',0,'学校二维码'),
    ('',NULL,NULL,NULL,0,NULL);

  SET v_map = (SELECT COUNT(*) FROM v299_order_map WHERE order_no<>'');
  IF v_map <> 326 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 order map must contain 326 target rows';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order) AND
     EXISTS(SELECT 1 FROM v299_order_map m LEFT JOIN zsjos_order o
              ON o.order_no=m.order_no AND o.deleted=b'0'
            WHERE m.order_no<>'' AND o.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 order map references an order that does not exist';
  END IF;

-- BEGIN V299 COMPLETED REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V300')
    OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V300') THEN
    IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V299') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V299')) OR NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V300') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V300')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 completed payment repair requires both V299/V300 ledgers';
    END IF;
    DROP TEMPORARY TABLE IF EXISTS v299_completed_payment_map;
    CREATE TEMPORARY TABLE v299_completed_payment_map(label_snapshot VARCHAR(64) PRIMARY KEY,dict_value VARCHAR(64))
      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    INSERT INTO v299_completed_payment_map VALUES 
    ('小鹅通线上支付','xiaoe_online'),
    ('中世健网校微信小店（视频号小店）','wechat_shop'),
    ('复训','retraining'),
    ('畅学卡扣款（用于畅学卡学员开课）','learning_card_deduction');
    IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no AND o.tenant_id=1
      JOIN v299_completed_payment_map p ON p.label_snapshot=m.pay_label WHERE m.pay_null=1 AND m.order_no<>''
      AND (o.deleted<>0 OR NOT(o.payment_method <=> p.dict_value)
        OR (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='order-payment-legacy-label-backfill-20261010'
            AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='payment_method' AND b.old_value IS NULL)<>1)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 later V300 payment result or evidence conflicts';
    END IF;
    UPDATE v299_order_map m JOIN v299_completed_payment_map p ON p.label_snapshot=m.pay_label
      SET m.pay_code=p.dict_value,m.pay_null=0 WHERE m.pay_null=1;
    IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no AND o.tenant_id=1
      WHERE m.order_no<>'' AND (o.deleted<>0 OR NOT(o.fee_mode <=> m.fee_code)
        OR NOT(o.fee_mode_label_snapshot <=> m.fee_label)
        OR NOT(o.payment_method <=> CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END)
        OR NOT(o.payment_method_label_snapshot <=> m.pay_label))) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 completed payment fields conflict; later changes preserved';
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE v299_completed_payment_map;
    DROP TEMPORARY TABLE v299_order_map;
    SELECT 'V299' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
-- END V299 COMPLETED REPLAY
  -- Pre-images of the original state, captured once per column and never overwritten on replay.
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-payment-column-realign-20261009','zsjos_order',o.id,'fee_mode',o.fee_mode
    FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no AND o.deleted=b'0'
   WHERE m.order_no<>'' AND NOT (o.fee_mode <=> m.fee_code)
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-payment-column-realign-20261009'
                       AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='fee_mode');
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-payment-column-realign-20261009','zsjos_order',o.id,'payment_method',o.payment_method
    FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no AND o.deleted=b'0'
   WHERE m.order_no<>''
     AND NOT (o.payment_method <=> CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END)
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-payment-column-realign-20261009'
                       AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='payment_method');

  -- 缴费方式: legacy payment_method -> fee_mode plus its recorded label
  UPDATE zsjos_order o JOIN v299_order_map m ON m.order_no=o.order_no
     SET o.fee_mode=m.fee_code, o.fee_mode_label_snapshot=m.fee_label,
         o.updater='V299', o.update_time=NOW()
   WHERE o.deleted=b'0' AND m.order_no<>''
     AND (NOT (o.fee_mode <=> m.fee_code) OR NOT (o.fee_mode_label_snapshot <=> m.fee_label));
  SET v_fee = ROW_COUNT();

  -- 支付方式: legacy payment_channel -> payment_method plus its recorded label
  UPDATE zsjos_order o JOIN v299_order_map m ON m.order_no=o.order_no
     SET o.payment_method=CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END,
         o.payment_method_label_snapshot=m.pay_label,
         o.updater='V299', o.update_time=NOW()
   WHERE o.deleted=b'0' AND m.order_no<>''
     AND (NOT (o.payment_method <=> CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END)
          OR NOT (o.payment_method_label_snapshot <=> m.pay_label));
  SET v_pay = ROW_COUNT();

  IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no
            WHERE m.order_no<>'' AND NOT (o.fee_mode <=> m.fee_code)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 fee mode realignment incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no
            WHERE m.order_no<>'' AND o.fee_mode_label_snapshot IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 fee mode label snapshot incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no
            WHERE m.order_no<>'' AND NOT (o.payment_method <=>
                  CASE WHEN m.pay_null=1 THEN NULL ELSE m.pay_code END)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 payment method realignment incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM v299_order_map m JOIN zsjos_order o ON o.order_no=m.order_no
            WHERE m.order_no<>'' AND o.payment_method_label_snapshot IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 payment method label snapshot incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE deleted=b'0' AND fee_mode IS NOT NULL
              AND fee_mode NOT IN ('retail','floor_price','prepaid_deduction','nomoney')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 a fee_mode value outside the dictionary remains';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE deleted=b'0' AND payment_method IS NOT NULL
              AND payment_method<>'' AND payment_method NOT IN
              ('prepaid_recharge','balance_cash','course_exchange','company_alipay',
               'finance_wechat','company_qr','learning_qr','80','nopay')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V299 a payment_method value outside the dictionary remains';
  END IF;

  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V299','Order payment column realignment',SHA2('V299__order_payment_column_realign.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V299');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V299','Order payment column realignment',SHA2('V299__order_payment_column_realign.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V299');
  COMMIT;

  SELECT v_map AS map_rows, v_fee AS fee_rows_written, v_pay AS payment_rows_written;
END$$
DELIMITER ;
CALL zsjos_v299_apply();
DROP PROCEDURE zsjos_v299_apply;
