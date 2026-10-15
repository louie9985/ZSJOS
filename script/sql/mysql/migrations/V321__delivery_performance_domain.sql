-- UTF-8. V321: independent delivery performance domain and menu metadata.
-- Requires V276 tables, canonical sales performance menus, and both ledgers.
-- Reviewed configuration scope: tenant 1 organization 1050 and department 1051
-- previously configured through the business API by the delivery-manager workstream.
-- Other existing organizations/targets retain sales; targets follow their stored dept_id,
-- never current employee membership. No facts, amounts, revisions or role grants change.
-- Repeatable and partial-DDL recovery supported. DDL implicitly commits; failure does
-- not register success. Rollback requires reviewed restoration of domain/index metadata.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v321_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v321_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales-performance:query' AND deleted=0)
 OR NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:sales-performance-target:query' AND deleted=0)
 OR NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V276') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V321 requires V276 tables and canonical menus';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_org' AND column_name='business_domain') THEN
  ALTER TABLE zsjos_performance_org ADD COLUMN business_domain varchar(16) NOT NULL DEFAULT 'sales' COMMENT '业绩业务域';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND column_name='business_domain') THEN
  ALTER TABLE zsjos_performance_target ADD COLUMN business_domain varchar(16) NOT NULL DEFAULT 'sales' COMMENT '业绩业务域';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND index_name='uk_target'
  AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND index_name='uk_target' AND column_name='business_domain')) THEN
  ALTER TABLE zsjos_performance_target DROP INDEX uk_target;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND index_name='uk_target') THEN
  ALTER TABLE zsjos_performance_target ADD UNIQUE KEY uk_target(tenant_id,business_domain,scope_type,scope_id,period_type,period_start,deleted);
 END IF;
 START TRANSACTION;
 UPDATE zsjos_performance_org SET business_domain='delivery',updater='V321'
 WHERE tenant_id=1 AND dept_id IN (1050,1051) AND center_id=1050 AND business_domain='sales';
 UPDATE zsjos_performance_target t JOIN zsjos_performance_org o ON o.tenant_id=t.tenant_id AND o.dept_id=t.dept_id AND o.deleted=0
 SET t.business_domain='delivery',t.updater='V321' WHERE t.tenant_id=1 AND o.business_domain='delivery' AND t.business_domain='sales';
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '交付业绩统计','zsjos:delivery-performance:query',2,96,parent_id,'delivery-performance',icon,'zsjos/deliveryPerformance/index','DeliveryPerformance',status,visible,keep_alive,always_show,'V321','V321',0
 FROM system_menu WHERE permission='zsjos:sales-performance:query' AND deleted=0
 AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:delivery-performance:query' AND deleted=0);
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '交付业绩设置','zsjos:delivery-performance-target:query',2,97,parent_id,'delivery-performance-target',icon,'zsjos/deliveryPerformanceTarget/index','DeliveryPerformanceTarget',status,visible,keep_alive,always_show,'V321','V321',0
 FROM system_menu WHERE permission='zsjos:sales-performance-target:query' AND deleted=0
 AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:delivery-performance-target:query' AND deleted=0);
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT REPLACE(s.name,'销售','交付'),REPLACE(s.permission,'zsjos:sales-performance','zsjos:delivery-performance'),3,s.sort,d.id,'','','',s.status,s.visible,s.keep_alive,s.always_show,'V321','V321',0
 FROM system_menu s JOIN system_menu p ON p.id=s.parent_id AND p.deleted=0
 JOIN system_menu d ON d.permission=REPLACE(p.permission,'zsjos:sales-performance','zsjos:delivery-performance') AND d.deleted=0
 WHERE s.type=3 AND s.deleted=0 AND s.permission LIKE 'zsjos:sales-performance%'
 AND NOT EXISTS(SELECT 1 FROM system_menu x WHERE x.permission=REPLACE(s.permission,'zsjos:sales-performance','zsjos:delivery-performance') AND x.deleted=0);
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission LIKE 'zsjos:delivery-performance%')<>8
 OR EXISTS(SELECT 1 FROM zsjos_performance_org WHERE business_domain NOT IN ('sales','delivery'))
 OR EXISTS(SELECT 1 FROM zsjos_performance_target WHERE business_domain NOT IN ('sales','delivery')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V321 domain/menu postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V321','独立交付业绩域',SHA2('V321__delivery_performance_domain.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V321');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V321','独立交付业绩域',SHA2('V321__delivery_performance_domain.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V321');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v321_apply();
DROP PROCEDURE zsjos_v321_apply;
