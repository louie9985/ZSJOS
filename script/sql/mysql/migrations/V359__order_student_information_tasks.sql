-- UTF-8. Requires V358 and V342 in both core ledgers. Run before matching backend.
-- Adds order-item collection tasks and Workbench menu metadata only. Existing
-- collection forms/answers/files and role assignments are preserved. Existing
-- forms receive task references. Also seed empty tasks for pending/processing
-- fulfillment cases on processable orders (tenant 1), using verified order/item/Person
-- bindings. Never reopen terminal cases, mark sent, create answers or grant roles.
-- Repeatable; inspect actual table/indexes after partial DDL. DDL implicitly commits.
-- Rollback retains tasks and collected data; do not drop tables to revert code.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v359_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v359_apply()
BEGIN
 DECLARE root_id BIGINT;
 DECLARE page_id BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF (SELECT COUNT(*) FROM zsjos_schema_version WHERE version IN ('V358','V342'))<>2
 OR (SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version IN ('V358','V342'))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V359 requires V358 and V342 in both core ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V359 requires unique Workbench root';
 END IF;
 SELECT id INTO root_id FROM system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;
 CREATE TABLE IF NOT EXISTS zsjos_student_info_task (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL, order_item_id BIGINT NOT NULL, person_id BIGINT NOT NULL,
  form_id BIGINT NULL, source_form_id BIGINT NULL, source_revision INT NULL,
  version INT NOT NULL DEFAULT 0, last_command_key VARCHAR(64) NULL,
  last_command_fingerprint VARCHAR(64) NULL, operator_user_id BIGINT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0', tenant_id BIGINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_tenant_item (tenant_id,order_item_id), KEY idx_person (tenant_id,person_id)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单报名资料收集任务';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task'
  AND column_name IN ('id','order_id','order_item_id','person_id','form_id','source_form_id','source_revision','version','last_command_key','last_command_fingerprint','operator_user_id','creator','create_time','updater','update_time','deleted','tenant_id'))<>17
 OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task' AND index_name='uk_tenant_item' AND non_unique=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V359 task structure postcondition failed';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT 1 FROM zsjos_student_info_form f WHERE f.deleted=0 AND f.order_item_id IS NOT NULL
   GROUP BY f.tenant_id,f.order_item_id HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V359 requires unambiguous existing form per order item';
 END IF;
 INSERT INTO zsjos_student_info_task(order_id,order_item_id,person_id,form_id,tenant_id,creator,updater)
 SELECT i.order_id,i.id,f.person_id,f.id,f.tenant_id,'V359','V359'
 FROM zsjos_student_info_form f JOIN zsjos_order_item i ON i.id=f.order_item_id AND i.tenant_id=f.tenant_id AND i.deleted=0
 WHERE f.deleted=0 AND f.person_id IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM zsjos_student_info_task t WHERE t.tenant_id=f.tenant_id AND t.order_item_id=i.id);
 -- The old fulfillment generation entry is retired, so unfinished legacy cases
 -- without a form must receive an actionable task before that entry is removed.
 INSERT INTO zsjos_student_info_task(order_id,order_item_id,person_id,tenant_id,creator,updater)
 SELECT DISTINCT o.id,i.id,o.person_id,o.tenant_id,'V359','V359'
 FROM zsjos_registration_case c
 JOIN zsjos_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
 JOIN zsjos_order_item i ON i.id=c.order_item_id AND i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0
 WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing')
  AND o.status IN ('pending_approval','revision_required','effective') AND o.person_id IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM zsjos_student_info_form f WHERE f.tenant_id=o.tenant_id AND f.deleted=0
    AND (f.order_item_id=i.id OR f.registration_case_id=c.id))
  AND NOT EXISTS(SELECT 1 FROM zsjos_student_info_task t WHERE t.tenant_id=o.tenant_id AND t.order_item_id=i.id);
 IF EXISTS(
  SELECT 1 FROM zsjos_registration_case c
  JOIN zsjos_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
  JOIN zsjos_order_item i ON i.id=c.order_item_id AND i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing')
   AND o.status IN ('pending_approval','revision_required','effective') AND o.person_id IS NOT NULL
   AND NOT EXISTS(SELECT 1 FROM zsjos_student_info_form f WHERE f.tenant_id=o.tenant_id AND f.deleted=0
     AND (f.order_item_id=i.id OR f.registration_case_id=c.id))
   AND NOT EXISTS(SELECT 1 FROM zsjos_student_info_task t WHERE t.tenant_id=o.tenant_id AND t.order_item_id=i.id
     AND t.order_id=o.id AND t.person_id=o.person_id AND t.deleted=0)
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V359 unfinished fulfillment collection task binding conflict';
 END IF;
 IF EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-info-task:query'
   AND (deleted<>0 OR type<>2 OR parent_id<>root_id OR path<>'student-info-tasks' OR component<>'zsjos/studentInfoTasks/index'))
 OR EXISTS(SELECT 1 FROM system_menu WHERE parent_id=root_id AND path='student-info-tasks' AND permission<>'zsjos:student-info-task:query' AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V359 menu identity conflict';
 END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '报名资料收集','zsjos:student-info-task:query',2,42,root_id,'student-info-tasks','ep:document','zsjos/studentInfoTasks/index','native',0,b'1',b'1',b'1','V359','V359',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-info-task:query');
 SELECT id INTO page_id FROM system_menu WHERE permission='zsjos:student-info-task:query';
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '复用已有报名资料','zsjos:student-info-task:reuse',3,1,page_id,'',0,b'1',b'1',b'1','V359','V359',b'0'
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-info-task:reuse');
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V359','订单报名资料收集任务',SHA2('V359__order_student_information_tasks.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V359');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V359','订单报名资料收集任务',SHA2('V359__order_student_information_tasks.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V359');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v359_apply();
DROP PROCEDURE zsjos_v359_apply;
