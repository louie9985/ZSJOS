-- UTF-8. V288: independent System announcement public sharing. Prerequisite: Core V287.
-- Add one empty table and one button metadata row under the existing notice page.
-- No role/tenant-package grants, business-row rewrite or historical share backfill.
-- Order: V287 -> V288 -> backend -> H5 -> management clients -> administrator permission assignment.
-- Repeatable; inspect actual table/index/ledger state after partial failure, then replay.
-- DDL commits implicitly. Wrong existing structures fail; do not mark success or overwrite ledgers.
-- Rollback: disable public entry points before reverting application, retain schema and records.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v288_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v288_apply()
BEGIN
  DECLARE notice_menu_id bigint;
  DECLARE share_menu_id bigint;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V287')
    OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 requires Core V287 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
      AND table_name IN ('system_notice','system_notice_attachment','system_menu','zsjos_schema_version','zsjos_module_schema_version')) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 requires notice, attachment, menu and transactional ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu q JOIN system_menu p ON p.id=q.parent_id
      WHERE q.permission='system:notice:query' AND q.type=3 AND q.deleted=b'0' AND p.type=2 AND p.deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 requires one existing System notice page';
  END IF;
  SELECT parent_id INTO notice_menu_id FROM system_menu WHERE permission='system:notice:query' AND type=3 AND deleted=b'0';
  CREATE TABLE IF NOT EXISTS system_notice_share (
    id bigint NOT NULL AUTO_INCREMENT,
    notice_id bigint NOT NULL COMMENT '公告编号',
    token varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '公开分享令牌',
    attachment_ids varchar(512) NOT NULL COMMENT '允许公开的附件文件编号JSON',
    active bit(1) NOT NULL DEFAULT b'0' COMMENT '是否开启',
    version bigint NOT NULL DEFAULT 0 COMMENT '分享版本',
    opened_by bigint NOT NULL COMMENT '开启人',
    opened_at datetime NOT NULL COMMENT '开启时间',
    closed_by bigint DEFAULT NULL COMMENT '关闭人',
    closed_at datetime DEFAULT NULL COMMENT '关闭时间',
    creator varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0',
    tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_notice_share_token (token),
    UNIQUE KEY uk_notice_share_tenant_notice (tenant_id,notice_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告对外分享';
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='system_notice_share'
      AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 share table engine or charset mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_share' AND (
    (column_name='id' AND data_type='bigint' AND is_nullable='NO')
    OR (column_name='notice_id' AND data_type='bigint' AND is_nullable='NO')
    OR (column_name='token' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=64)
    OR (column_name='attachment_ids' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=512)
    OR (column_name='active' AND data_type='bit' AND is_nullable='NO')
    OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    OR (column_name='opened_by' AND data_type='bigint' AND is_nullable='NO')
    OR (column_name='opened_at' AND data_type='datetime' AND is_nullable='NO')
    OR (column_name='closed_by' AND data_type='bigint' AND is_nullable='YES')
    OR (column_name='closed_at' AND data_type='datetime' AND is_nullable='YES')
    OR (column_name='creator' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=64)
    OR (column_name='create_time' AND data_type='datetime' AND is_nullable='NO')
    OR (column_name='updater' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=64)
    OR (column_name='update_time' AND data_type='datetime' AND is_nullable='NO')
    OR (column_name='deleted' AND data_type='bit' AND is_nullable='NO')
    OR (column_name='tenant_id' AND data_type='bigint' AND is_nullable='NO')
  )) <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 share column contract mismatch';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_share'
      AND column_name='token' AND collation_name='ascii_bin')
    OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_share'
      AND column_name='id' AND extra LIKE '%auto_increment%')
    OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice_share'
      AND column_name IN ('active','deleted') AND column_default='b''0''') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 token collation, identity or defaults mismatch';
  END IF;
  IF (SELECT COUNT(*) FROM (
      SELECT index_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='system_notice_share'
      AND non_unique=0 GROUP BY index_name
      HAVING (index_name='PRIMARY' AND GROUP_CONCAT(column_name ORDER BY seq_in_index)='id')
         OR (index_name='uk_notice_share_token' AND GROUP_CONCAT(column_name ORDER BY seq_in_index)='token')
         OR (index_name='uk_notice_share_tenant_notice' AND GROUP_CONCAT(column_name ORDER BY seq_in_index)='tenant_id,notice_id')
    ) required_keys) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 share unique indexes mismatch';
  END IF;
  START TRANSACTION;
  IF NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='system:notice:share') THEN
    SELECT COALESCE(MAX(id),0)+1 INTO share_menu_id FROM system_menu;
    INSERT INTO system_menu
      (id,name,permission,type,sort,parent_id,path,icon,component,component_name,workbench_render_mode,
       status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
    VALUES (share_menu_id,'公告对外分享','system:notice:share',3,8,notice_menu_id,'','','',NULL,'admin_only',
       0,b'1',b'1',b'1','V288',NOW(),'V288',NOW(),b'0');
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='system:notice:share') <> 1
    OR NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='system:notice:share' AND type=3
      AND parent_id=notice_menu_id AND deleted=b'0' AND status=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V288 share permission metadata conflict';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V288','System notice public sharing',SHA2('V288__notice_public_share.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V288');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V288','System notice public sharing',SHA2('V288__notice_public_share.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V288');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v288_apply();
DROP PROCEDURE zsjos_v288_apply;
