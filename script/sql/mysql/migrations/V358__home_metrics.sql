-- UTF-8. V358: configurable home metrics; upgrade/fresh execution after V357.
-- Deployment: apply before the matching backend and Admin/Workbench release.
-- Prerequisites: V357 in both core ledgers and a unique active System menu root.
-- Application rollback retains configuration/history tables, menus and version records.
-- Scope: two empty tenant-owned tables, one Admin page and two permission buttons.
-- No business records, dictionaries or role grants are seeded. No destructive rollback.
-- DDL auto-commits: inspect partial tables after failure; rerun repairs missing tables/menus.
-- Conflicting existing structure fails before version records. Never use --force.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v358_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v358_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V357')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V357') THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics requires V357 in both core ledgers'; END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/system' AND type=1 AND deleted=0)<>1 THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics requires exactly one System root'; END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/system' AND type=1 AND deleted=0;
 CREATE TABLE IF NOT EXISTS zsjos_home_metric (
 id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(64) NOT NULL, draft_json LONGTEXT NOT NULL,
 version INT NOT NULL DEFAULT 0, published_version INT NULL, enabled BIT(1) NOT NULL DEFAULT b'0',
 creator VARCHAR(64) NOT NULL DEFAULT '',create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) NOT NULL DEFAULT '',update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT(1) NOT NULL DEFAULT b'0',tenant_id BIGINT NOT NULL DEFAULT 1,
 PRIMARY KEY(id),UNIQUE KEY uk_home_metric_code(tenant_id,code,deleted)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页指标配置';
 CREATE TABLE IF NOT EXISTS zsjos_home_metric_version (
 id BIGINT NOT NULL AUTO_INCREMENT,metric_id BIGINT NOT NULL,version INT NOT NULL,spec_json LONGTEXT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) NOT NULL DEFAULT '',update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT(1) NOT NULL DEFAULT b'0',tenant_id BIGINT NOT NULL DEFAULT 1,
 PRIMARY KEY(id),UNIQUE KEY uk_home_metric_version(tenant_id,metric_id,version)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页指标发布快照';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_home_metric' AND column_name IN ('id','code','draft_json','version','published_version','enabled','creator','create_time','updater','update_time','deleted','tenant_id'))<>12
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_home_metric_version' AND column_name IN ('id','metric_id','version','spec_json','creator','create_time','updater','update_time','deleted','tenant_id'))<>10
 OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_home_metric' AND index_name='uk_home_metric_code' AND non_unique=0 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='tenant_id,code,deleted')
 OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_home_metric_version' AND index_name='uk_home_metric_version' AND non_unique=0 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='tenant_id,metric_id,version') THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics schema conflict'; END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_home_metric','zsjos_home_metric_version') AND (
 (column_name IN ('id','tenant_id','metric_id') AND data_type<>'bigint') OR
 (column_name='id' AND extra NOT LIKE '%auto_increment%') OR
 (column_name IN ('version','published_version') AND data_type<>'int') OR
 (column_name='published_version' AND is_nullable<>'YES') OR
 (column_name IN ('draft_json','spec_json') AND (data_type<>'longtext' OR character_set_name<>'utf8mb4')) OR
 (column_name='code' AND (data_type<>'varchar' OR character_maximum_length<>64 OR character_set_name<>'utf8mb4')) OR
 (column_name IN ('creator','updater') AND (data_type<>'varchar' OR character_maximum_length<>64 OR character_set_name<>'utf8mb4')) OR
 (column_name IN ('enabled','deleted') AND column_type<>'bit(1)') OR
 (column_name IN ('create_time','update_time') AND data_type<>'datetime') OR
 (column_name NOT IN ('published_version') AND is_nullable<>'NO')))
 OR (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_home_metric','zsjos_home_metric_version') AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci')<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_home_metric','zsjos_home_metric_version') AND index_name='PRIMARY' AND column_name='id' AND seq_in_index=1)<>2
 OR EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_home_metric','zsjos_home_metric_version') AND index_name='PRIMARY' AND seq_in_index>1) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics column type or engine conflict'; END IF;
 START TRANSACTION;
 IF EXISTS(SELECT permission FROM system_menu WHERE permission IN ('zsjos:home-metric:query','zsjos:home-metric:update','zsjos:home-metric:publish') GROUP BY permission HAVING COUNT(*)>1)
 OR EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:home-metric:query' AND (parent_id<>root_id OR type<>2 OR deleted<>0 OR path<>'home-metric' OR component<>'zsjos/homeMetric/index' OR workbench_render_mode<>'admin_only')) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics menu conflict'; END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '首页指标配置','zsjos:home-metric:query',2,90,root_id,'home-metric','ep:data-analysis','zsjos/homeMetric/index','HomeMetricConfig','admin_only',0,b'1',b'1',b'0','V358','V358',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:home-metric:query');
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:home-metric:query';
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission IN ('zsjos:home-metric:update','zsjos:home-metric:publish') AND (parent_id<>page_id OR type<>3 OR deleted<>0)) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics button conflict'; END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '编辑指标','zsjos:home-metric:update',3,1,page_id,'','','',NULL,0,b'1',b'1',b'0','V358','V358',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:home-metric:update');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '发布指标','zsjos:home-metric:publish',3,2,page_id,'','','',NULL,0,b'1',b'1',b'0','V358','V358',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:home-metric:publish');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:home-metric:query','zsjos:home-metric:update','zsjos:home-metric:publish') AND deleted=0)<>3
 OR NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:home-metric:query' AND path='home-metric' AND component='zsjos/homeMetric/index' AND workbench_render_mode='admin_only' AND parent_id=root_id AND type=2 AND deleted=0) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Home metrics postcondition failed'; END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V358','首页动态指标',SHA2('V358__home_metrics.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V358');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V358','首页动态指标',SHA2('V358__home_metrics.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V358');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v358_apply();
DROP PROCEDURE zsjos_v358_apply;
