-- UTF-8. Core V373 prerequisite. Stop writers before applying. Tenant 1.
-- Transactional business/menu migration. Recover partial DDL by replay. Restore pre-cutover backup for data rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v374_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v374_apply()
BEGIN
 DECLARE pool_id BIGINT;
 DECLARE collection_id BIGINT;
 DECLARE my_id BIGINT;
 DECLARE checklist BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V373')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V373') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 requires Core V373';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
     AND table_name='zsjos_registration_case' AND column_name='owner_user_id') THEN
  ALTER TABLE zsjos_registration_case ADD COLUMN owner_user_id BIGINT NULL;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
     AND table_name='zsjos_registration_case' AND index_name='idx_tenant_owner_status') THEN
  ALTER TABLE zsjos_registration_case ADD INDEX idx_tenant_owner_status (tenant_id,owner_user_id,status);
 END IF;
 IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='zsjos_registration_case' AND index_name='idx_tenant_owner_status')
       <> 'tenant_id,owner_user_id,status' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 owner index mismatch';
 END IF;
 START TRANSACTION;
 SELECT id INTO pool_id FROM system_menu WHERE permission='zsjos:registration:query-pool' AND type=2;
 SELECT id INTO collection_id FROM system_menu WHERE permission='zsjos:student-info-task:query' AND type=2;
 IF pool_id IS NULL OR collection_id IS NULL THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 missing historical menu';
 END IF;
 BEGIN
  UPDATE zsjos_registration_case c JOIN zsjos_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
  SET c.status='cancelled',c.cancelled_at=COALESCE(c.cancelled_at,o.update_time),c.version=c.version+1,c.updater='V374'
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing')
    AND o.status IN ('revision_required','superseded','terminated');

  IF EXISTS(SELECT 1 FROM zsjos_registration_case c
       WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing') AND c.order_item_id IS NULL
       AND (SELECT COUNT(*) FROM zsjos_order_item i WHERE i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0)<>1) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 ambiguous unfinished legacy case';
  END IF;
  UPDATE zsjos_registration_case c JOIN zsjos_order_item i ON i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0
  SET c.order_item_id=i.id,c.updater='V374'
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing') AND c.order_item_id IS NULL;

  UPDATE zsjos_registration_case c LEFT JOIN zsjos_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
  SET c.owner_user_id=o.formal_sales_user_id,c.updater='V374'
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.owner_user_id IS NULL AND o.formal_sales_user_id IS NOT NULL;

  IF EXISTS(SELECT 1 FROM zsjos_order o JOIN zsjos_order_item i ON i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0
     WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='pending_approval'
     AND NOT EXISTS(SELECT 1 FROM zsjos_registration_case c WHERE c.tenant_id=i.tenant_id AND c.order_item_id=i.id AND c.deleted=0)) THEN
   SELECT published_version_id INTO checklist FROM zsjos_registration_checklist_template WHERE tenant_id=1 AND deleted=0 ORDER BY id LIMIT 1;
   IF checklist IS NULL OR NOT EXISTS(SELECT 1 FROM zsjos_registration_checklist_template_item
     WHERE tenant_id=1 AND version_id=checklist AND deleted=0 AND enabled=1 AND item_type<>'study_planner') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 missing published registration checklist';
   END IF;
   INSERT INTO zsjos_registration_case(order_id,order_item_id,owner_user_id,status,assignment_mode,checklist_version_id,
          registration_approved_at,version,tenant_id,creator,updater)
   SELECT o.id,i.id,o.formal_sales_user_id,'pending',IF(o.agreed_exam_month IS NULL,'sales_owner','auto_class'),
          checklist,o.create_time,0,1,'V374','V374'
   FROM zsjos_order o JOIN zsjos_order_item i ON i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0
   WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='pending_approval'
   AND NOT EXISTS(SELECT 1 FROM zsjos_registration_case c WHERE c.tenant_id=i.tenant_id AND c.order_item_id=i.id AND c.deleted=0);

   INSERT INTO zsjos_registration_case_checklist_item(registration_case_id,template_item_id,item_key,item_type,
          title_snapshot,sort,attachment_required,checked,version,tenant_id,creator,updater)
   SELECT c.id,t.id,t.item_key,t.item_type,t.title,t.sort,t.attachment_required,0,0,1,'V374','V374'
   FROM zsjos_registration_case c JOIN zsjos_registration_checklist_template_item t
     ON t.tenant_id=c.tenant_id AND t.version_id=c.checklist_version_id AND t.enabled=1 AND t.deleted=0 AND t.item_type<>'study_planner'
   WHERE c.tenant_id=1 AND c.deleted=0 AND c.creator='V374'
     AND NOT EXISTS(SELECT 1 FROM zsjos_registration_case_checklist_item x WHERE x.registration_case_id=c.id AND x.template_item_id=t.id);

   INSERT INTO zsjos_registration_case_route(registration_case_id,route_option_id,option_key,department_id,
          department_name_snapshot,assignee_type,selected,sort,version,tenant_id,creator,updater)
   SELECT c.id,r.id,r.option_key,r.department_id,r.department_name_snapshot,r.assignee_type,0,r.sort,0,1,'V374','V374'
   FROM zsjos_registration_case c JOIN zsjos_registration_route_option r
     ON r.tenant_id=c.tenant_id AND r.version_id=c.checklist_version_id AND r.enabled=1 AND r.deleted=0 AND r.assignee_type<>'study_planner'
   WHERE c.tenant_id=1 AND c.deleted=0 AND c.creator='V374'
     AND NOT EXISTS(SELECT 1 FROM zsjos_registration_case_route x WHERE x.registration_case_id=c.id AND x.route_option_id=r.id);
  END IF;

  UPDATE zsjos_student_info_form f JOIN zsjos_registration_case c
    ON c.id=f.registration_case_id AND c.tenant_id=f.tenant_id AND c.deleted=0 AND c.status IN ('pending','processing')
  SET f.order_item_id=c.order_item_id,f.updater='V374'
  WHERE f.tenant_id=1 AND f.deleted=0 AND f.order_item_id IS NULL AND c.order_item_id IS NOT NULL;
  IF EXISTS(SELECT 1 FROM zsjos_student_info_form f WHERE f.tenant_id=1 AND f.deleted=0 AND f.order_item_id IS NOT NULL
       AND EXISTS(SELECT 1 FROM zsjos_registration_case c WHERE c.tenant_id=f.tenant_id AND c.order_item_id=f.order_item_id
          AND c.deleted=0 AND c.status IN ('pending','processing'))
       AND NOT EXISTS(SELECT 1 FROM zsjos_student_info_task t WHERE t.tenant_id=f.tenant_id AND t.order_item_id=f.order_item_id AND t.deleted=0 AND t.form_id IS NOT NULL)
       GROUP BY f.order_item_id HAVING COUNT(*)>1) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 ambiguous student info form binding';
  END IF;
  INSERT INTO zsjos_student_info_task(order_id,order_item_id,person_id,form_id,tenant_id,creator,updater)
  SELECT c.order_id,c.order_item_id,o.person_id,
         (SELECT f.id FROM zsjos_student_info_form f WHERE f.tenant_id=1 AND f.deleted=0 AND f.order_item_id=c.order_item_id
             AND f.person_id=o.person_id LIMIT 1),1,'V374','V374'
  FROM zsjos_registration_case c JOIN zsjos_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
  WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing') AND c.order_item_id IS NOT NULL AND o.person_id IS NOT NULL
     AND NOT EXISTS(SELECT 1 FROM zsjos_student_info_task t WHERE t.tenant_id=1 AND t.order_item_id=c.order_item_id);
  UPDATE zsjos_student_info_task t JOIN zsjos_student_info_form f
    ON f.tenant_id=t.tenant_id AND f.order_item_id=t.order_item_id AND f.person_id=t.person_id AND f.deleted=0
  JOIN zsjos_registration_case c ON c.tenant_id=t.tenant_id AND c.order_item_id=t.order_item_id AND c.deleted=0
    AND c.status IN ('pending','processing')
  SET t.form_id=f.id,t.updater='V374'
  WHERE t.tenant_id=1 AND t.deleted=0 AND t.form_id IS NULL;
 END;

 IF EXISTS(SELECT 1 FROM zsjos_order o JOIN zsjos_order_item i ON i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0
    WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='pending_approval'
      AND NOT EXISTS(SELECT 1 FROM zsjos_registration_case c WHERE c.tenant_id=i.tenant_id AND c.order_item_id=i.id AND c.deleted=0)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 pending approval item lacks fulfillment';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_registration_case c
   LEFT JOIN zsjos_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id AND o.deleted=0
   LEFT JOIN zsjos_order_item i ON i.id=c.order_item_id AND i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0
   LEFT JOIN zsjos_student_info_task t ON t.order_item_id=i.id AND t.order_id=o.id AND t.tenant_id=c.tenant_id AND t.person_id=o.person_id AND t.deleted=0
   LEFT JOIN zsjos_student_info_form f ON f.id=t.form_id AND f.order_item_id=i.id AND f.person_id=o.person_id AND f.tenant_id=c.tenant_id AND f.deleted=0
   WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing')
     AND (o.id IS NULL OR i.id IS NULL OR t.id IS NULL OR (t.form_id IS NOT NULL AND f.id IS NULL))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 unfinished fulfillment binding mismatch';
 END IF;

 UPDATE system_menu SET status=1,visible=0,deleted=1,updater='V374' WHERE id IN (pool_id,collection_id) AND deleted=0;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,workbench_render_mode,
                         status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '我的报名履约','zsjos:registration:query-my',2,sort,parent_id,'registration-pool',icon,component,component_name,workbench_render_mode,
        0,1,keep_alive,always_show,'V374','V374',0
 FROM system_menu WHERE id=pool_id AND NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:registration:query-my');
 SELECT id INTO my_id FROM system_menu WHERE permission='zsjos:registration:query-my' AND type=2 AND deleted=0 AND status=0 AND path='registration-pool';
 IF my_id IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 my fulfillment menu mismatch'; END IF;
 UPDATE system_menu SET parent_id=my_id,updater='V374' WHERE parent_id IN(pool_id,collection_id) AND type=3 AND deleted=0;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator,updater,deleted)
 SELECT '转交报名履约','zsjos:registration:transfer',3,99,my_id,'',0,1,1,0,'V374','V374',0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:registration:transfer');
 IF (SELECT COUNT(*) FROM system_menu WHERE parent_id=my_id AND permission='zsjos:registration:transfer'
     AND type=3 AND status=0 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V374 transfer action menu mismatch';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V374','销售报名履约归属',SHA2('V374__sales_owned_registration.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V374');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V374','销售报名履约归属',SHA2('V374__sales_owned_registration.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V374');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v374_apply();
DROP PROCEDURE zsjos_v374_apply;


