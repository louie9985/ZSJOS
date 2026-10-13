-- =============================================================================
-- 史辉文 / 史政凯 服务关系补全
--   person 8150 史辉文 ; lead 6606 史政凯 ; order 8822 ; case 1019 ; relation 1016
--
-- 目标终态:
--   relation 1016 -> 正式班(班主任=400 周老师【梁颖】), owner=400,
--                    acceptance_status='accepted', delivery_stage='first_contact',
--                    content_director_user_id=47  (邓穆祺),
--                    operator_user_id=525        (余红红),
--                    + 首联待办 + 协作群 + 协作者分配日志
--
-- 写入口径对齐应用代码:
--   DeliveryClassServiceImpl.create()              -> 建班字段/编号
--   DeliveryClassServiceImpl.transferLocked()      -> class_id + owner_user_id + version
--   StudentContactServiceImpl.accept()             -> accepted_* + delivery_stage + 首联任务
--   StudentContactServiceImpl.assignCollaborator() -> 协作者字段 + assignment_log
--
-- 幂等: 全部以 id / idempotency_key 为守卫, 重复执行不产生新行.
-- 回滚: 见文末 ROLLBACK 段.
-- =============================================================================

-- 库表排序规则是 utf8mb4_unicode_ci, 而客户端连接默认 utf8mb4_0900_ai_ci.
-- 不做这一步, 所有 "字符串字面量 = unicode_ci 列" 的比较都会报
-- ERROR 1267 Illegal mix of collations. 必须在任何语句之前执行.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @tenant := 1;
SET @relation := 1016;
SET @person := 8150;
SET @order := 8822;
SET @item := 9323;
SET @case := 1019;
SET @homeroom := 400;                 -- 周老师【梁颖】 学习规划师 / 班主任 / 服务 owner
SET @director := 47;                  -- 邓穆祺       编导
SET @operator := 525;                 -- 余红红       运营
SET @category := 13;                  -- 技能提升
SET @exam_schedule := 30;             -- 郑州-人社 三级健康管理师等 考试时间 2026-10-31 (PUBLISHED 未结束)
SET @first_contact_minutes := 120;    -- config id=2 (published) 的 first_contact_timeout_minutes
SET @config_version := 2;

-- -----------------------------------------------------------------------------
-- 0. 前置校验: 不满足直接 SIGNAL 中断, 不做任何写入
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS zsjos_repair_1016_guard;
DELIMITER $$
CREATE PROCEDURE zsjos_repair_1016_guard()
BEGIN
  DECLARE g INT DEFAULT 0;
  DECLARE v INT DEFAULT 0;

  SELECT COUNT(*) INTO v FROM zsjos_service_relation
   WHERE id=@relation AND person_id=@person AND order_id=@order AND order_item_id=@item
     AND registration_case_id=@case AND status='active' AND deleted=b'0' AND tenant_id=@tenant;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_users
   WHERE id=@homeroom AND status=0 AND deleted=b'0' AND dept_id=1051;
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='班主任 400 不合法(需启用且在部门1051)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id
   WHERE ur.user_id=@homeroom AND r.code='study_planner';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='班主任 400 缺少 study_planner 角色'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_users WHERE id=@director AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='编导 47 不合法'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM system_users WHERE id=@operator AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='运营 525 不合法'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_user_relation
   WHERE scene='study_planner_content_director' AND source_user_id=@homeroom
     AND target_user_id=@director AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺协作关系 400->47 (study_planner_content_director)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_user_relation
   WHERE scene='content_director_operator' AND source_user_id=@director
     AND target_user_id=@operator AND status=0 AND deleted=b'0';
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺协作关系 47->525 (content_director_operator)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_exam_schedule
   WHERE id=@exam_schedule AND record_status='PUBLISHED' AND deleted=b'0'
     AND (CASE WHEN schedule_type='EXACT' THEN exact_date ELSE end_date END) >= CURDATE();
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='考试场次 30 不可用(需 PUBLISHED 且未结束)'; END IF;
  SET g := g + v;

  SELECT COUNT(*) INTO v FROM zsjos_student_contact_config_version
   WHERE id=@config_version AND status='published' AND deleted=b'0' AND tenant_id=@tenant;
  IF v = 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='学员联系配置无已发布版本'; END IF;
  SET g := g + v;

  IF g <> 9 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='前置校验未全部通过'; END IF;

  SELECT CONCAT('前置校验通过 (', g, '/9)') AS precheck;
END$$
DELIMITER ;

CALL zsjos_repair_1016_guard();
DROP PROCEDURE IF EXISTS zsjos_repair_1016_guard;


-- -----------------------------------------------------------------------------
-- 1. 建正式班 (班主任 = 400); 若 1016 已在正式班则复用, 不重复建
-- -----------------------------------------------------------------------------
SET @existing_class := (
  SELECT dc.id FROM zsjos_delivery_class dc
   WHERE dc.id = (SELECT class_id FROM zsjos_service_relation WHERE id=@relation)
     AND dc.system_class = b'0' AND dc.deleted = b'0'
);

SET @class_no := CONCAT('BJ', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), LPAD(FLOOR(RAND()*10000), 4, '0'));

START TRANSACTION;

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
   AND acceptance_status = 'pending'
   AND deleted = b'0'
   AND tenant_id = @tenant;

SET @rel_rows := ROW_COUNT();


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
                    WHERE l.idempotency_key = CONCAT('repair-1016-director-', @relation));

INSERT INTO zsjos_student_collaborator_assignment_log
  (service_relation_id, collaborator_type, previous_user_id, assigned_user_id,
   operator_user_id, reason, idempotency_key,
   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @relation, 'operator', NULL, @operator, @director,
       '补录：编导指定运营', CONCAT('repair-1016-operator-', @relation),
       'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_student_collaborator_assignment_log l
                    WHERE l.idempotency_key = CONCAT('repair-1016-operator-', @relation));


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
                    WHERE t.idempotency_key = CONCAT('student-contact:accept:', @relation));


-- -----------------------------------------------------------------------------
-- 5. 协作群 + 回填 relation.collaboration_group_id
-- -----------------------------------------------------------------------------
INSERT INTO zsjos_collaboration_group
  (source_service_relation_id, student_person_id, director_user_id, operator_user_id,
   status, version, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @relation, @person, @director, @operator, 'active', 0,
       'repair-1016', NOW(), 'repair-1016', NOW(), b'0', @tenant
 WHERE NOT EXISTS (SELECT 1 FROM zsjos_collaboration_group cg
                    WHERE cg.source_service_relation_id=@relation AND cg.deleted=b'0');

SET @group_id := (SELECT id FROM zsjos_collaboration_group
                   WHERE source_service_relation_id=@relation AND deleted=b'0' LIMIT 1);

UPDATE zsjos_service_relation
   SET collaboration_group_id = @group_id
 WHERE id = @relation AND collaboration_group_id IS NULL;

COMMIT;

SELECT CONCAT('事务提交完成; relation更新行数=', @rel_rows, ', class_id=', @class_id) AS result;


-- =============================================================================
-- 执行后核对
-- =============================================================================
SELECT '--- 服务关系 ---' AS section;
SELECT id, person_id, order_id, class_id, status, acceptance_status, accepted_by_user_id, accepted_at,
       owner_user_id, content_director_user_id, operator_user_id,
       delivery_stage, director_stage, collaboration_group_id, version
  FROM zsjos_service_relation WHERE id=@relation\G

SELECT '--- 正式班 ---' AS section;
SELECT id, class_no, class_name, system_class, homeroom_user_id, homeroom_user_name_snapshot,
       dept_id, status, category_id, exam_schedule_id, exam_schedule_snapshot
  FROM zsjos_delivery_class WHERE id=@class_id;

SELECT '--- 首联任务 ---' AS section;
SELECT id, task_type, biz_type, biz_id, status, assignee_id, due_at, idempotency_key
  FROM zsjos_business_task WHERE idempotency_key=CONCAT('student-contact:accept:', @relation);

SELECT '--- 协作群 ---' AS section;
SELECT id, source_service_relation_id, student_person_id, director_user_id, operator_user_id, status
  FROM zsjos_collaboration_group WHERE source_service_relation_id=@relation;

SELECT '--- 分配日志 ---' AS section;
SELECT id, collaborator_type, previous_user_id, assigned_user_id, operator_user_id, idempotency_key
  FROM zsjos_student_collaborator_assignment_log WHERE service_relation_id=@relation;


-- =============================================================================
-- ROLLBACK 段 (需要时单独执行, 默认不执行)
-- =============================================================================
-- START TRANSACTION;
-- SET @relation := 1016;
-- DELETE FROM zsjos_student_collaborator_assignment_log
--  WHERE service_relation_id=@relation AND creator='repair-1016';
-- DELETE FROM zsjos_business_task
--  WHERE idempotency_key=CONCAT('student-contact:accept:', @relation);
-- UPDATE zsjos_service_relation SET collaboration_group_id=NULL WHERE id=@relation;
-- DELETE FROM zsjos_collaboration_group
--  WHERE source_service_relation_id=@relation AND creator='repair-1016';
-- UPDATE zsjos_service_relation
--    SET class_id=1, owner_user_id=NULL, acceptance_status='pending',
--        accepted_by_user_id=NULL, accepted_at=NULL, delivery_stage=NULL,
--        content_director_user_id=NULL, operator_user_id=NULL, collaboration_group_id=NULL,
--        version=0, updater='repair-1016', update_time=NOW()
--  WHERE id=@relation;
-- DELETE FROM zsjos_delivery_class WHERE creator='repair-1016' AND system_class=b'0';
-- COMMIT;
