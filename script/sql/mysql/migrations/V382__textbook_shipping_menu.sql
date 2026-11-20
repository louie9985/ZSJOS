-- UTF-8. Apply after core V381. Defines one independent page under the /zsjos root.
-- Adds optional carrier/tracking and an optimistic version to order items; DDL can commit independently.
-- After failure inspect all three columns and both ledgers, correct conflicts and replay. No historical backfill.
-- Replay preserves administrator settings and grants. Roll back visibility via menu management.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v382_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v382_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V381') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V381') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V382 requires core V381';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_item' AND column_name='textbook_carrier') THEN
  ALTER TABLE zsjos_order_item ADD COLUMN textbook_carrier VARCHAR(100) NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_item' AND column_name='textbook_tracking_no') THEN
  ALTER TABLE zsjos_order_item ADD COLUMN textbook_tracking_no VARCHAR(100) NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_item' AND column_name='textbook_shipping_version') THEN
  ALTER TABLE zsjos_order_item ADD COLUMN textbook_shipping_version INT NOT NULL DEFAULT 0;
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_item' AND
   ((column_name IN ('textbook_carrier','textbook_tracking_no') AND data_type='varchar' AND character_maximum_length=100 AND is_nullable='YES') OR
    (column_name='textbook_shipping_version' AND column_type='int' AND is_nullable='NO' AND column_default='0'))) <> 3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V382 shipping column conflict';
 END IF;
 START TRANSACTION;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0 FOR UPDATE;
 IF root_id IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V382 requires ZSJOS root'; END IF;
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:textbook-shipping:query' OR (parent_id=root_id AND path='textbook-shipping' AND deleted=0)) THEN
  INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,workbench_render_mode)
  VALUES('教材邮寄','zsjos:textbook-shipping:query',2,28,root_id,'textbook-shipping','ep:reading','zsjos/textbook-shipping/index','ZsjosTextbookShipping',0,1,1,1,'admin_embed');
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND (permission='zsjos:textbook-shipping:query' OR (parent_id=root_id AND path='textbook-shipping'))) <> 1 OR NOT EXISTS(SELECT 1 FROM system_menu WHERE deleted=0 AND type=2 AND parent_id=root_id AND path='textbook-shipping' AND permission='zsjos:textbook-shipping:query' AND component='zsjos/textbook-shipping/index' AND component_name='ZsjosTextbookShipping' AND workbench_render_mode='admin_embed') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V382 textbook shipping menu conflict';
 END IF;
 SELECT id INTO page_id FROM system_menu WHERE deleted=0 AND permission='zsjos:textbook-shipping:query';
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show)
 SELECT action.name,action.permission,3,action.sort,page_id,'','',0,1,1,1 FROM (
   SELECT '查看所有邮寄信息' AS name,'zsjos:textbook-shipping:view-all' AS permission,1 AS sort
   UNION ALL SELECT '填写邮寄信息','zsjos:textbook-shipping:update',2
 ) action WHERE NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission=action.permission);
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:textbook-shipping:view-all','zsjos:textbook-shipping:update')) <> 2 OR
    (SELECT COUNT(DISTINCT permission) FROM system_menu WHERE parent_id=page_id AND type=3 AND deleted=0 AND permission IN ('zsjos:textbook-shipping:view-all','zsjos:textbook-shipping:update')) <> 2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V382 shipping permission conflict';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V382','教材邮寄独立菜单',SHA2('V382__textbook_shipping_menu.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V382');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V382','教材邮寄独立菜单',SHA2('V382__textbook_shipping_menu.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V382');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v382_apply();
DROP PROCEDURE zsjos_v382_apply;
