-- UTF-8. V340 follows V339 in both ledgers; run before the new application.
-- Scope: gift purchase nullable order linkage, binary workflow flag, manual salesperson
-- snapshots, empty append-only operation table and three button metadata entries.
-- Existing records start unpurchased; no historical actor names are invented.
-- No order, role-menu, dictionary or historical timestamp changes. Replay is additive.
-- DDL commits implicitly; retain data on rollback, inspect actual schema and both ledgers
-- after partial failure and replay. Reverting application code does not remove the schema.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v340_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v340_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V339')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V339') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V340 requires V339 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_gift_purchase','system_menu') AND engine='InnoDB')<>2
 OR (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:gift-purchase:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V340 requires gift table and one purchase page menu';
 END IF;
 ALTER TABLE zsjos_gift_purchase MODIFY COLUMN order_id bigint DEFAULT NULL,
  MODIFY COLUMN order_no varchar(64) DEFAULT NULL;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase' AND column_name='purchased') THEN
  ALTER TABLE zsjos_gift_purchase ADD COLUMN purchased bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已采购';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase' AND column_name='submitter_user_id') THEN
  ALTER TABLE zsjos_gift_purchase ADD COLUMN submitter_user_id bigint DEFAULT NULL COMMENT '手工采购录单销售';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase' AND column_name='submitter_user_name') THEN
  ALTER TABLE zsjos_gift_purchase ADD COLUMN submitter_user_name varchar(100) DEFAULT NULL COMMENT '手工采购录单销售姓名快照';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_gift_purchase_operation (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL,
  purchase_id bigint NOT NULL, action varchar(24) NOT NULL,
  operator_user_id bigint DEFAULT NULL, operator_name varchar(100) DEFAULT NULL,
  occurred_at datetime NOT NULL,
  creator varchar(64) DEFAULT '',create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',PRIMARY KEY(id),KEY idx_purchase_operation(tenant_id,purchase_id,id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='礼品采购操作记录';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase'
 AND ((column_name IN ('order_id','order_no','submitter_user_id','submitter_user_name') AND is_nullable='YES')
 OR (column_name='purchased' AND column_type='bit(1)' AND is_nullable='NO' AND column_default="b'0'")))<>5
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase_operation'
 AND column_name IN ('id','tenant_id','purchase_id','action','occurred_at') AND is_nullable='NO')<>5
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase_operation'
 AND index_name='idx_purchase_operation' AND ((seq_in_index=1 AND column_name='tenant_id')
 OR (seq_in_index=2 AND column_name='purchase_id') OR (seq_in_index=3 AND column_name='id')))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V340 gift purchase structure postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater)
 SELECT buttons.name,buttons.permission,3,buttons.sort,p.id,'','','',0,b'1',b'1',b'1','migration-V340','migration-V340'
 FROM system_menu p CROSS JOIN (
  SELECT '新增采购' name,'zsjos:gift-purchase:create' permission,2 sort UNION ALL
  SELECT '编辑采购及状态','zsjos:gift-purchase:update',3 UNION ALL
  SELECT '删除采购','zsjos:gift-purchase:delete',4
 ) buttons
 WHERE p.permission='zsjos:gift-purchase:query' AND p.type=2 AND p.deleted=0
 AND NOT EXISTS(SELECT 1 FROM (SELECT permission FROM system_menu WHERE deleted=0) existing WHERE existing.permission=buttons.permission);
 IF (SELECT COUNT(DISTINCT permission) FROM system_menu WHERE permission IN
 ('zsjos:gift-purchase:create','zsjos:gift-purchase:update','zsjos:gift-purchase:delete') AND type=3 AND deleted=0
 AND parent_id=(SELECT id FROM system_menu WHERE permission='zsjos:gift-purchase:query' AND type=2 AND deleted=0))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V340 permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V340','礼品采购管理',SHA2('V340__gift_purchase_management.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V340');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V340','礼品采购管理',SHA2('V340__gift_purchase_management.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V340');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v340_apply();
DROP PROCEDURE zsjos_v340_apply;
