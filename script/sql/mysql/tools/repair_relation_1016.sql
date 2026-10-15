-- UTF-8. Controlled repair of tenant 1 service relation 1016 only.
-- Requires existing InnoDB business tables and zsjos_data_repair_backup.old_value LONGTEXT.
-- Before first application: export scoped tables, review the full relation JSON produced by
-- the same ordered information_schema columns, then explicitly set @repair_1016_expected_sha
-- (SHA2(CAST(JSON_OBJECT(...) AS CHAR),256)) and @repair_1016_expected_version in this session.
-- No inferred before-state is accepted. Missing/mismatched approval fails without business writes.
-- Dependency order: class -> relation -> assignment logs -> first-contact task -> collaboration group.
-- Full before/after row JSON and newly created row IDs remain under repair_key relation-1016-v2.
-- Exact completed replay is zero-write; partial evidence and later edits fail closed.
-- No version ledger or role assignments. Procedure/temporary-table DDL is outside business DML.
-- Recovery: see docs/operations/review-fixes-20261014.md; never restore hardcoded previous values.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS zsjos_repair_1016_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_repair_1016_apply()
apply_body: BEGIN
 DECLARE g INT DEFAULT 0;
 DECLARE v INT DEFAULT 0;
 DECLARE locked_id BIGINT;
 DECLARE phase INT DEFAULT 0;
 DECLARE table_index INT;
 DECLARE target_table VARCHAR(64);
 DECLARE predicate_text TEXT;
 DECLARE columns_json LONGTEXT;
 DECLARE relation_image JSON;
 DECLARE saved_group_concat BIGINT DEFAULT @@session.group_concat_max_len;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN
  ROLLBACK;
  SET SESSION group_concat_max_len=saved_group_concat;
  DO RELEASE_LOCK('zsjos:repair:relation:1016');
  RESIGNAL;
 END;
 IF GET_LOCK('zsjos:repair:relation:1016',10)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Repair lock unavailable';
 END IF;
 SET SESSION group_concat_max_len=1048576;
SET @tenant := 1;
SET @relation := 1016;
SET @person := 8150;
SET @order := 8822;
SET @item := 9323;
SET @case := 1019;
SET @homeroom := 400;
SET @director := 47;
SET @operator := 525;
SET @category := 13;
SET @exam_schedule := 30;
SET @first_contact_minutes := 120;
SET @config_version := 2;

 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
 AND table_name IN ('zsjos_service_relation','zsjos_delivery_class','zsjos_student_collaborator_assignment_log',
 'zsjos_business_task','zsjos_collaboration_group','zsjos_data_repair_backup'))<>6
 OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Repair requires transactional tables and LONGTEXT evidence';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS repair_1016_images;
 CREATE TEMPORARY TABLE repair_1016_images(table_name VARCHAR(64),row_id BIGINT,image JSON,
 PRIMARY KEY(table_name,row_id)) ENGINE=InnoDB;
 START TRANSACTION;
 SELECT id INTO locked_id FROM zsjos_service_relation WHERE tenant_id=1 AND id=1016 AND deleted=0 FOR UPDATE;
 IF locked_id IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Relation missing'; END IF;

 SET table_index=0;
 WHILE table_index<5 DO
  SET target_table=ELT(table_index+1,'zsjos_service_relation','zsjos_delivery_class',
    'zsjos_student_collaborator_assignment_log','zsjos_business_task','zsjos_collaboration_group');
  SET predicate_text=ELT(table_index+1,'tenant_id=1 AND id=1016',
    'tenant_id=1 AND id=(SELECT class_id FROM zsjos_service_relation WHERE tenant_id=1 AND id=1016) AND creator=\'repair-1016\'',
    'tenant_id=1 AND service_relation_id=1016 AND idempotency_key IN (\'repair-1016-director-1016\',\'repair-1016-operator-1016\')',
    'tenant_id=1 AND idempotency_key=\'student-contact:accept:1016\'',
    'tenant_id=1 AND source_service_relation_id=1016 AND deleted=0');
  SELECT GROUP_CONCAT(CONCAT(QUOTE(column_name),',',
     IF(data_type='bit',CONCAT('CAST(`',column_name,'` AS UNSIGNED)'),CONCAT('`',column_name,'`')))
     ORDER BY ordinal_position SEPARATOR ',') INTO columns_json
   FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=target_table;
  SET @snapshot_sql=CONCAT('INSERT INTO repair_1016_images SELECT ',QUOTE(target_table),',id,JSON_OBJECT(',columns_json,') FROM `',target_table,'` WHERE ',predicate_text,' FOR UPDATE');
  PREPARE snapshot_statement FROM @snapshot_sql; EXECUTE snapshot_statement; DEALLOCATE PREPARE snapshot_statement;
  SET table_index=table_index+1;
 END WHILE;

 SELECT COUNT(*) INTO v FROM repair_1016_images;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='relation-1016-v2') THEN
  IF (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='relation-1016-v2' AND column_name='__before__')<>1
   OR (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='relation-1016-v2' AND column_name='__after__')<>6
   OR v<>6
   OR EXISTS(SELECT 1 FROM repair_1016_images i LEFT JOIN zsjos_data_repair_backup b
     ON b.repair_key='relation-1016-v2' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__after__'
     WHERE b.id IS NULL OR NOT (CAST(b.old_value AS JSON)=CAST(CAST(i.image AS CHAR) AS JSON))) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partial evidence or later edit: reviewed recovery required';
  END IF;
  ROLLBACK; SET SESSION group_concat_max_len=saved_group_concat;
  DO RELEASE_LOCK('zsjos:repair:relation:1016');
  SELECT 'Verified completed repair; no changes' AS result;
  LEAVE apply_body;
 END IF;
 SELECT image INTO relation_image FROM repair_1016_images WHERE table_name='zsjos_service_relation';
 IF EXISTS(SELECT 1 FROM zsjos_delivery_class c JOIN zsjos_service_relation r ON r.class_id=c.id
 WHERE r.id=1016 AND r.tenant_id=1 AND c.system_class=0) OR @repair_1016_expected_sha IS NULL OR @repair_1016_expected_version IS NULL
  OR NOT (SHA2(CAST(relation_image AS CHAR),256)=@repair_1016_expected_sha)
  OR NOT (JSON_EXTRACT(relation_image,'$.version')=@repair_1016_expected_version)
  OR NOT (JSON_UNQUOTE(JSON_EXTRACT(relation_image,'$.acceptance_status'))='pending')
  OR JSON_TYPE(JSON_EXTRACT(relation_image,'$.owner_user_id'))<>'NULL'
  OR JSON_TYPE(JSON_EXTRACT(relation_image,'$.content_director_user_id'))<>'NULL'
  OR JSON_TYPE(JSON_EXTRACT(relation_image,'$.operator_user_id'))<>'NULL'
  OR JSON_TYPE(JSON_EXTRACT(relation_image,'$.collaboration_group_id'))<>'NULL'
  OR (SELECT COUNT(*) FROM repair_1016_images)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approved preimage/version required; unexpected existing side effects';
 END IF;
  SELECT COUNT(*) INTO v FROM zsjos_service_relation
   WHERE id=@relation AND person_id=@person AND order_id=@order AND order_item_id=@item
     AND registration_case_id=@case AND status='active' AND deleted=b'0' AND tenant_id=@tenant;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_users
   WHERE tenant_id=1 AND id=@homeroom AND status=0 AND deleted=b'0' AND dept_id=1051;
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='班主任 400 不合法(需启用且在部门1051)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id
   WHERE ur.user_id=@homeroom AND r.code='study_planner';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='班主任 400 缺少 study_planner 角色'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_users WHERE tenant_id=1 AND id=@director AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='编导 47 不合法'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_users WHERE tenant_id=1 AND id=@operator AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='运营 525 不合法'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_user_relation
   WHERE tenant_id=1 AND scene='study_planner_content_director' AND source_user_id=@homeroom
     AND target_user_id=@director AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺协作关系 400->47 (study_planner_content_director)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_user_relation
   WHERE tenant_id=1 AND scene='content_director_operator' AND source_user_id=@director
     AND target_user_id=@operator AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺协作关系 47->525 (content_director_operator)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_exam_schedule
   WHERE tenant_id=1 AND id=@exam_schedule AND record_status='PUBLISHED' AND deleted=b'0'
     AND (CASE WHEN schedule_type='EXACT' THEN exact_date ELSE end_date END) >= CURDATE();
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='考试场次 30 不可用(需 PUBLISHED 且未结束)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_student_contact_config_version
   WHERE id=@config_version AND status='published' AND deleted=b'0' AND tenant_id=@tenant;
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='学员联系配置无已发布版本'; END IF;
  SET g := g + v;

  IF g <> 9 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='前置校验未全部通过'; END IF;


 SELECT first_contact_timeout_minutes INTO @first_contact_minutes FROM zsjos_student_contact_config_version
 WHERE id=@config_version AND tenant_id=1 AND deleted=0 AND status='published';
 IF @first_contact_minutes IS NULL OR @first_contact_minutes<=0 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid contact deadline configuration';
 END IF;
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
 SELECT 'relation-1016-v2',table_name,row_id,'__before__',CAST(image AS CHAR) FROM repair_1016_images;
SET @existing_class := (
  SELECT dc.id FROM zsjos_delivery_class dc
   WHERE dc.id = (SELECT class_id FROM zsjos_service_relation WHERE id=@relation)
     AND dc.system_class = b'0' AND dc.deleted = b'0'
);

SET @class_no := CONCAT('BJ', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), LPAD(FLOOR(RAND()*10000), 4, '0'));



INSERT INTO zsjos_delivery_class
  (class_no, class_name, system_class, category_id, category_name_snapshot, category_path_snapshot,
   exam_schedule_id, exam_schedule_snapshot,
   homeroom_user_id, homeroom_user_name_snapshot, dept_id, dept_name_snapshot,
   status, version, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @class_no,
       '技能提升·大健康轻创 2026秋（班主任：梁颖）',
       b'0', @category, '技能提升',
       JSON_ARRAY(JSON_OBJECT('id', @category, 'name', '技能提升')),
       @exam_schedule,
       CONCAT(COALESCE(es.schedule_name, ''), ' · ',
              CASE WHEN es.schedule_type='EXACT' THEN DATE_FORMAT(es.exact_date, '%Y-%m-%d')
                   ELSE CONCAT(DATE_FORMAT(es.start_date, '%Y-%m-%d'), '~', DATE_FORMAT(es.end_date, '%Y-%m-%d')) END),
       @homeroom, u.nickname, 1051,
       (SELECT name FROM system_dept WHERE id=1051 AND deleted=b'0'),
       'SERVING', 0, 'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
  FROM zsjos_exam_schedule es
  JOIN system_users u ON u.id=@homeroom
 WHERE es.id=@exam_schedule
   AND @existing_class IS NULL;

SET @class_id := IFNULL(@existing_class, NULLIF(LAST_INSERT_ID(), 0));
SET @class_id := IFNULL(@class_id, (SELECT class_id FROM zsjos_service_relation WHERE id=@relation));


-- -----------------------------------------------------------------------------
-- 2. 服务关系: 落班 + owner + 接收 + 两个协作者 (version 0 -> 1)
-- -----------------------------------------------------------------------------
UPDATE zsjos_service_relation
   SET class_id = @class_id,
       owner_user_id = @homeroom,
       acceptance_status = 'accepted',
       accepted_by_user_id = @homeroom,
       accepted_at = NOW(),
       delivery_stage = 'first_contact',
       director_stage = 'precheck',
       content_director_user_id = @director,
       operator_user_id = @operator,
       version = version + 1,
       updater = 'repair-1016',
       update_time = NOW()
 WHERE id = @relation
   AND acceptance_status = 'pending' AND version=@repair_1016_expected_version
   AND deleted = b'0'
   AND tenant_id = @tenant;

SET @rel_rows := ROW_COUNT();
IF @rel_rows <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Relation transition failed'; END IF;


-- -----------------------------------------------------------------------------
-- 3. 协作者分配日志 (对齐 writeAssignmentLog, 幂等键唯一)
-- -----------------------------------------------------------------------------
INSERT INTO zsjos_student_collaborator_assignment_log
  (service_relation_id, collaborator_type, previous_user_id, assigned_user_id,
   operator_user_id, reason, idempotency_key,
   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @relation, 'content_director', NULL, @director, @homeroom,
       '补录：报名完成后分配编导', CONCAT('repair-1016-director-', @relation),
       'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_student_collaborator_assignment_log l
                    WHERE l.tenant_id=1 AND l.idempotency_key = CONCAT('repair-1016-director-', @relation));

INSERT INTO zsjos_student_collaborator_assignment_log
  (service_relation_id, collaborator_type, previous_user_id, assigned_user_id,
   operator_user_id, reason, idempotency_key,
   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @relation, 'operator', NULL, @operator, @director,
       '补录：编导指定运营', CONCAT('repair-1016-operator-', @relation),
       'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_student_collaborator_assignment_log l
                    WHERE l.tenant_id=1 AND l.idempotency_key = CONCAT('repair-1016-operator-', @relation));


-- -----------------------------------------------------------------------------
-- 4. 首联待办 (对齐 accept() 内 createTask)
-- -----------------------------------------------------------------------------
INSERT INTO zsjos_business_task
  (task_type, biz_type, biz_id, status, assignee_type, assignee_id,
   title_snapshot, summary_snapshot, action_code, due_at, remind_at,
   payload, idempotency_key, version,
   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'student_first_contact', 'student_service', @relation, 'pending', 'user', @homeroom,
       CONCAT('首次联系 · ', o.order_no),
       '请在截止时间前完成本次学员联系',
       'OPEN_STUDENT_FIRST_CONTACT',
       NOW() + INTERVAL @first_contact_minutes MINUTE,
       NOW() + INTERVAL @first_contact_minutes MINUTE,
       JSON_OBJECT('serviceRelationId', @relation, 'configVersionId', @config_version),
       CONCAT('student-contact:accept:', @relation), 0,
       'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
  FROM zsjos_order o
 WHERE o.id=@order
   AND NOT EXISTS (SELECT 1 FROM zsjos_business_task t
                    WHERE t.tenant_id=1 AND t.idempotency_key = CONCAT('student-contact:accept:', @relation));


-- -----------------------------------------------------------------------------
-- 5. 协作群 + 回填 relation.collaboration_group_id
-- -----------------------------------------------------------------------------
INSERT INTO zsjos_collaboration_group
  (source_service_relation_id, student_person_id, director_user_id, operator_user_id,
   status, version, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @relation, @person, @director, @operator, 'active', 0,
       'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_collaboration_group cg
                    WHERE cg.tenant_id=1 AND cg.source_service_relation_id=@relation AND cg.deleted=b'0');

SET @group_id := (SELECT id FROM zsjos_collaboration_group
                   WHERE tenant_id=1 AND source_service_relation_id=@relation AND deleted=b'0' LIMIT 1);

UPDATE zsjos_service_relation
   SET collaboration_group_id = @group_id
 WHERE id = @relation AND tenant_id=1 AND collaboration_group_id IS NULL;


 DELETE FROM repair_1016_images;

 SET table_index=0;
 WHILE table_index<5 DO
  SET target_table=ELT(table_index+1,'zsjos_service_relation','zsjos_delivery_class',
    'zsjos_student_collaborator_assignment_log','zsjos_business_task','zsjos_collaboration_group');
  SET predicate_text=ELT(table_index+1,'tenant_id=1 AND id=1016',
    'tenant_id=1 AND id=(SELECT class_id FROM zsjos_service_relation WHERE tenant_id=1 AND id=1016) AND creator=\'repair-1016\'',
    'tenant_id=1 AND service_relation_id=1016 AND idempotency_key IN (\'repair-1016-director-1016\',\'repair-1016-operator-1016\')',
    'tenant_id=1 AND idempotency_key=\'student-contact:accept:1016\'',
    'tenant_id=1 AND source_service_relation_id=1016 AND deleted=0');
  SELECT GROUP_CONCAT(CONCAT(QUOTE(column_name),',',
     IF(data_type='bit',CONCAT('CAST(`',column_name,'` AS UNSIGNED)'),CONCAT('`',column_name,'`')))
     ORDER BY ordinal_position SEPARATOR ',') INTO columns_json
   FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=target_table;
  SET @snapshot_sql=CONCAT('INSERT INTO repair_1016_images SELECT ',QUOTE(target_table),',id,JSON_OBJECT(',columns_json,') FROM `',target_table,'` WHERE ',predicate_text,' FOR UPDATE');
  PREPARE snapshot_statement FROM @snapshot_sql; EXECUTE snapshot_statement; DEALLOCATE PREPARE snapshot_statement;
  SET table_index=table_index+1;
 END WHILE;

 IF (SELECT COUNT(*) FROM repair_1016_images)<>6 OR NOT EXISTS(
 SELECT 1 FROM zsjos_service_relation r JOIN zsjos_delivery_class c ON c.id=r.class_id AND c.tenant_id=1
 JOIN zsjos_collaboration_group g ON g.id=r.collaboration_group_id AND g.tenant_id=1
 WHERE r.id=1016 AND r.tenant_id=1 AND r.acceptance_status='accepted' AND r.owner_user_id=400
 AND r.content_director_user_id=47 AND r.operator_user_id=525 AND r.version=@repair_1016_expected_version+1
 AND c.homeroom_user_id=400 AND g.student_person_id=8150 AND g.director_user_id=47 AND g.operator_user_id=525) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Repair postcondition failed';
 END IF;
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
 SELECT 'relation-1016-v2',table_name,row_id,'__after__',CAST(image AS CHAR) FROM repair_1016_images;
 COMMIT;
 SET SESSION group_concat_max_len=saved_group_concat;
 DO RELEASE_LOCK('zsjos:repair:relation:1016');
 SELECT 'Repair complete; evidence saved' AS result;
END$$
DELIMITER ;
CALL zsjos_repair_1016_apply();
DROP PROCEDURE zsjos_repair_1016_apply;
