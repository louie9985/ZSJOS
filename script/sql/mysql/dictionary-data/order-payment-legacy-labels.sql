-- UTF-8. Approved four historical payment-method options (2026-10-10, Core V300).
-- Prerequisites: System dictionary tables and zsjos_schema_version; run from the repository root.
-- Scope: this dictionary only, global System dictionary metadata (no tenant column).
--
-- Source: the legacy parttimecrm orders_order.payment_channel values that 18 imported orders
-- recorded as their payment channel and that had no enabled entry in zsjos_order_payment_method.
-- V299 preserved the recorded label on those orders and left the value NULL because inventing a
-- dictionary entry is an administrator decision; this file is that decision, and V300 backfills
-- the 18 orders from their stored label.
--
-- One-time seed marker preserves administrator renames, disables and deletions on replay.
-- No role grants, business rows or historical snapshots. Rollback: retain the dictionary;
-- deleting it would strand the 18 recorded selections and is not part of rollback.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 100,'小鹅通线上支付','xiaoe_online','zsjos_order_payment_method',0,'migration-V300','migration-V300'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='order_payment_legacy_labels_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_order_payment_method' AND value='xiaoe_online');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 110,'中世健网校微信小店（视频号小店）','wechat_shop','zsjos_order_payment_method',0,'migration-V300','migration-V300'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='order_payment_legacy_labels_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_order_payment_method' AND value='wechat_shop');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 120,'复训','retraining','zsjos_order_payment_method',0,'migration-V300','migration-V300'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='order_payment_legacy_labels_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_order_payment_method' AND value='retraining');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 130,'畅学卡扣款（用于畅学卡学员开课）','learning_card_deduction','zsjos_order_payment_method',0,'migration-V300','migration-V300'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='order_payment_legacy_labels_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_order_payment_method' AND value='learning_card_deduction');
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
VALUES ('order_payment_legacy_labels_v1','Approved four historical payment-method options','order-payment-legacy-labels-v1');
COMMIT;
