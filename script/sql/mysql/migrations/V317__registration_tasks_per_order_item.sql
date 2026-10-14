-- UTF-8. V317: one registration task per order item, tenant 1.
-- Requires Core V316 in both ledgers. Run during a registration-write maintenance window,
-- then deploy the matching backend and both frontend consumers together. Old backend is incompatible.
-- Scope: add nullable item identity and replace order uniqueness with item uniqueness;
-- split only pending/processing aggregate tasks, preserving their checklist/attachment/route
-- snapshots and moving each saved class assignment to its item task. First item retains old case ID.
-- Terminal historical tasks, service relations, orders, BPM, files and grants are untouched.
-- Repeated execution checks actual schema and remaining rows, not just version markers.
-- DDL implicitly commits; DML and both ledger writes share one transaction. Re-run after a partial
-- DDL failure. Back up the five affected registration tables and both ledgers before upgrading.
-- No automatic rollback: after new tasks are used, restore only with reviewed per-record reconciliation.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v317_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v317_apply()
BEGIN
 DECLARE finished BOOLEAN DEFAULT FALSE;
 DECLARE source_id BIGINT;
 DECLARE item_id BIGINT;
 DECLARE first_item_id BIGINT;
 DECLARE target_id BIGINT;
 DECLARE split_rows CURSOR FOR
  SELECT c.id,i.id,(SELECT MIN(i2.id) FROM zsjos_order_item i2
   WHERE i2.order_id=c.order_id AND i2.tenant_id=c.tenant_id AND i2.deleted=0)
  FROM zsjos_registration_case c JOIN zsjos_order_item i
   ON i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing')
   AND c.order_item_id IS NULL ORDER BY c.id,i.id;
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V316')
  OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V316') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 requires Core V316';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
  AND table_name IN ('zsjos_registration_case','zsjos_order','zsjos_order_item',
   'zsjos_registration_case_checklist_item','zsjos_registration_case_route',
   'zsjos_registration_item_attachment','zsjos_registration_class_assignment',
   'zsjos_service_relation','zsjos_schema_version','zsjos_module_schema_version'))<>10 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 requires transactional registration tables and ledgers';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND column_name='order_item_id') THEN
  ALTER TABLE zsjos_registration_case ADD COLUMN order_item_id BIGINT NULL COMMENT '履约订单商品；历史整单任务为空' AFTER order_id;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND column_name='order_item_id' AND data_type='bigint' AND is_nullable='YES') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 incompatible order_item_id column';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND index_name='uk_registration_case_item') THEN
  ALTER TABLE zsjos_registration_case ADD UNIQUE KEY uk_registration_case_item(tenant_id,order_item_id,deleted);
 END IF;
 IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='zsjos_registration_case' AND index_name='uk_registration_case_item' AND non_unique=0)
  <> 'tenant_id,order_item_id,deleted' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 item uniqueness index mismatch';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND index_name='idx_registration_case_order') THEN
  ALTER TABLE zsjos_registration_case ADD KEY idx_registration_case_order(tenant_id,order_id,deleted);
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND index_name='uk_registration_case_order') THEN
  ALTER TABLE zsjos_registration_case DROP INDEX uk_registration_case_order;
 END IF;
 -- Fresh baselines used a different name for the same order-level uniqueness.
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_registration_case' AND index_name='uk_tenant_order') THEN
  ALTER TABLE zsjos_registration_case DROP INDEX uk_tenant_order;
 END IF;

 START TRANSACTION;
 IF EXISTS(SELECT 1 FROM zsjos_registration_case c WHERE c.tenant_id=1 AND c.deleted=0
  AND c.status IN ('pending','processing') AND c.order_item_id IS NULL
  AND (NOT EXISTS(SELECT 1 FROM zsjos_order o WHERE o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0)
   OR NOT EXISTS(SELECT 1 FROM zsjos_order_item i WHERE i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0)
   OR EXISTS(SELECT 1 FROM zsjos_service_relation sr WHERE sr.registration_case_id=c.id AND sr.tenant_id=c.tenant_id AND sr.deleted=0))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 unresolved source task: missing order/items or existing service';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_registration_class_assignment a JOIN zsjos_registration_case c
  ON c.id=a.registration_case_id AND c.tenant_id=a.tenant_id
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing') AND a.deleted=0
   AND NOT EXISTS(SELECT 1 FROM zsjos_order_item i WHERE i.id=a.order_item_id
     AND i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 source class assignment belongs to another/missing item';
 END IF;
 OPEN split_rows;
 split_loop: LOOP
  FETCH split_rows INTO source_id,item_id,first_item_id;
  IF finished THEN LEAVE split_loop; END IF;
  IF item_id=first_item_id THEN
   SET target_id=source_id;
   UPDATE zsjos_registration_case SET order_item_id=item_id,version=version+1,updater='V317'
    WHERE id=source_id AND tenant_id=1 AND deleted=0 AND order_item_id IS NULL;
   IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 source task changed during split'; END IF;
  ELSE
   INSERT INTO zsjos_registration_case(order_id,order_item_id,status,checklist_version_id,assignment_mode,
    study_planner_user_id,registration_approved_at,version,creator,create_time,updater,tenant_id)
   SELECT order_id,item_id,status,checklist_version_id,assignment_mode,study_planner_user_id,
    registration_approved_at,0,creator,create_time,'V317',tenant_id
    FROM zsjos_registration_case WHERE id=source_id AND tenant_id=1 AND deleted=0;
   IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 missing split source'; END IF;
   SET target_id=LAST_INSERT_ID();
   INSERT INTO zsjos_registration_case_checklist_item(registration_case_id,template_item_id,item_key,item_type,
    title_snapshot,sort,checked,attachment_required,checked_by_user_id,checked_at,version,creator,create_time,updater,tenant_id)
   SELECT target_id,template_item_id,item_key,item_type,title_snapshot,sort,checked,attachment_required,
    checked_by_user_id,checked_at,0,creator,create_time,'V317',tenant_id
    FROM zsjos_registration_case_checklist_item WHERE registration_case_id=source_id AND tenant_id=1 AND deleted=0;
   INSERT INTO zsjos_registration_item_attachment(registration_case_id,checklist_item_id,infra_file_id,file_url,
    original_name,content_type,file_size,uploaded_by_user_id,uploaded_at,creator,create_time,updater,tenant_id)
   SELECT target_id,dst.id,a.infra_file_id,a.file_url,a.original_name,a.content_type,a.file_size,
    a.uploaded_by_user_id,a.uploaded_at,a.creator,a.create_time,'V317',a.tenant_id
    FROM zsjos_registration_item_attachment a JOIN zsjos_registration_case_checklist_item src
     ON src.id=a.checklist_item_id AND src.registration_case_id=source_id AND src.tenant_id=a.tenant_id AND src.deleted=0
    JOIN zsjos_registration_case_checklist_item dst ON dst.registration_case_id=target_id
     AND dst.template_item_id=src.template_item_id AND dst.tenant_id=src.tenant_id AND dst.deleted=0
    WHERE a.registration_case_id=source_id AND a.tenant_id=1 AND a.deleted=0;
   INSERT INTO zsjos_registration_case_route(registration_case_id,route_option_id,option_key,department_id,
    department_name_snapshot,assignee_type,selected,assignee_user_id,assignee_name_snapshot,sort,version,
    creator,create_time,updater,tenant_id)
   SELECT target_id,route_option_id,option_key,department_id,department_name_snapshot,assignee_type,
    selected,assignee_user_id,assignee_name_snapshot,sort,0,creator,create_time,'V317',tenant_id
    FROM zsjos_registration_case_route WHERE registration_case_id=source_id AND tenant_id=1 AND deleted=0;
   UPDATE zsjos_registration_class_assignment SET registration_case_id=target_id,version=version+1,updater='V317'
    WHERE registration_case_id=source_id AND order_item_id=item_id AND tenant_id=1 AND deleted=0;
  END IF;
 END LOOP;
 CLOSE split_rows;
 IF EXISTS(SELECT 1 FROM zsjos_registration_case WHERE tenant_id=1 AND deleted=0
  AND status IN ('pending','processing') AND order_item_id IS NULL)
  OR EXISTS(SELECT 1 FROM zsjos_registration_case c WHERE c.tenant_id=1 AND c.deleted=0 AND c.order_item_id IS NOT NULL
   AND NOT EXISTS(SELECT 1 FROM zsjos_order_item i WHERE i.id=c.order_item_id AND i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0))
  OR EXISTS(SELECT 1 FROM zsjos_registration_class_assignment a JOIN zsjos_registration_case c
   ON c.id=a.registration_case_id AND c.tenant_id=a.tenant_id WHERE c.tenant_id=1 AND c.deleted=0 AND a.deleted=0
    AND c.order_item_id IS NOT NULL AND a.order_item_id<>c.order_item_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V317 item task postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V317','报名履约按订单商品拆分',SHA2('V317__registration_tasks_per_order_item.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V317');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V317','报名履约按订单商品拆分',SHA2('V317__registration_tasks_per_order_item.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V317');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v317_apply();
DROP PROCEDURE zsjos_v317_apply;
