-- UTF-8. Requires V343 in both ledgers and the existing class-management menu.
-- Scope: empty class-group/membership-audit tables, nullable class group_id, indexes,
-- and one empty-group-delete button definition. No historical grouping or role grants.
-- Apply after V343, before backend and Workbench. Replay repairs missing additive DDL.
-- DDL commits implicitly; inspect structures and both ledgers after failure and replay.
-- Rollback: revert application first, retain columns/groups/audit and existing class data.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v344_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v344_apply()
BEGIN
 DECLARE page_id bigint;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V343')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V343') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V344 requires V343 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:query' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V344 requires one class-management page';
 END IF;
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:delivery-class:query' AND type=2 AND deleted=0;
 CREATE TABLE IF NOT EXISTS zsjos_delivery_class_group (
  id bigint NOT NULL AUTO_INCREMENT,name varchar(100) NOT NULL,
  dept_id bigint NOT NULL,dept_name_snapshot varchar(100) DEFAULT NULL,reference_date date DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',tenant_id bigint NOT NULL,
  PRIMARY KEY(id),KEY idx_delivery_group_scope(tenant_id,dept_id,deleted,id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付班级组';
 CREATE TABLE IF NOT EXISTS zsjos_delivery_class_membership_event (
  id bigint NOT NULL AUTO_INCREMENT,class_id bigint NOT NULL,
  source_group_id bigint DEFAULT NULL,target_group_id bigint DEFAULT NULL,operator_user_id bigint NOT NULL,
  creator varchar(64) DEFAULT '',create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',tenant_id bigint NOT NULL,
  PRIMARY KEY(id),KEY idx_delivery_membership_class(tenant_id,class_id,id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级归组历史';
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='group_id') THEN
  ALTER TABLE zsjos_delivery_class ADD COLUMN group_id bigint DEFAULT NULL COMMENT '普通班级所属父级';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='idx_delivery_class_group') THEN
  ALTER TABLE zsjos_delivery_class ADD KEY idx_delivery_class_group(tenant_id,group_id,deleted,id);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='group_id' AND data_type='bigint' AND is_nullable='YES')
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class_group'
 AND ((column_name='name' AND data_type='varchar' AND character_maximum_length=100 AND is_nullable='NO')
 OR (column_name='dept_id' AND data_type='bigint' AND is_nullable='NO') OR (column_name='reference_date' AND data_type='date')
 OR (column_name='version' AND data_type='int' AND is_nullable='NO') OR column_name IN ('id','tenant_id','deleted','dept_name_snapshot','creator','create_time','updater','update_time')))<>12
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class_membership_event')<>11
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class_membership_event'
 AND column_name IN ('class_id','source_group_id','target_group_id','operator_user_id','tenant_id') AND data_type='bigint')<>5
 OR (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_delivery_class_group','zsjos_delivery_class_membership_event')
 AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci')<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class_group' AND index_name='idx_delivery_group_scope'
 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='dept_id') OR (seq_in_index=3 AND column_name='deleted') OR (seq_in_index=4 AND column_name='id')))<>4
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class_membership_event' AND index_name='idx_delivery_membership_class'
 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='class_id') OR (seq_in_index=3 AND column_name='id')))<>3
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='idx_delivery_class_group'
 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='group_id') OR (seq_in_index=3 AND column_name='deleted') OR (seq_in_index=4 AND column_name='id')))<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V344 class-group postcondition failed';
 END IF;
 START TRANSACTION;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '删除空班级组','zsjos:delivery-class:group-delete',3,20,page_id,'','','',NULL,0,b'1',b'1',b'0','V344',NOW(),'V344',NOW(),b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:delivery-class:group-delete');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:group-delete' AND parent_id=page_id AND type=3 AND deleted=b'0')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V344 delete permission metadata conflict';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V344','班级父子分组',SHA2('V344__delivery_class_groups.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V344');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V344','班级父子分组',SHA2('V344__delivery_class_groups.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V344');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v344_apply();
DROP PROCEDURE zsjos_v344_apply;
