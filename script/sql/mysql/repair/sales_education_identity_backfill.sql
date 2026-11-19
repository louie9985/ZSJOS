-- -*- coding: utf-8 -*-
-- ZSJ-OS 教务身份退役：客资相关的旧教务快照一次性回填为销售
--
-- 目的：
--   原教务团队已撤销，全员按销售业务规则处理。用户决定：现有客资中涉及旧教务快照的数据
--   全部调整为销售，代码不保留教务兼容分支（LeadConstants 已删除 education / education_self_sourced，
--   'education' 负责人身份在代码中视为未知身份并失败关闭）。本脚本把存量快照改为当前代码使用的销售等价值。
--
-- 前置条件（任何一项不满足都不得执行）：
--   1. 已在目标库完成并核对 V371__sales_delivery_merge.sql（教务团队/教务自拓菜单元数据已退役）。
--   2. 已部署删除教务分支的后端版本，或在部署窗口内暂停客资写入，避免旧版本在回填后再写入教务值。
--   3. 已对目标库做完整备份；本脚本须经单独授权后由管理员执行；开发/AI 不在任何真实或共享库执行。
--
-- 执行方式：把下方 @tenant_id 改为目标租户后，使用 UTF-8 客户端执行：
--   mysql --default-character-set=utf8mb4 <库名> < sales_education_identity_backfill.sql
--
-- 精确数据范围（仅 tenant_id=@tenant_id；含逻辑删除行，保证不残留教务值）：
--   * zsjos_lead：owner_identity / pending_owner_identity 'education' -> 'sales'；
--     source_type / submission_source_type 'education_self_sourced' -> 'sales_self_sourced'。只改这四列。
--   * zsjos_lead_duplicate_review.submission_source_type、zsjos_lead_activation.source_type：
--     'education_self_sourced' -> 'sales_self_sourced'。
--   * zsjos_lead_assignment_history.owner_identity_snapshot、zsjos_lead_follow_up_record.owner_identity_snapshot、
--     zsjos_opportunity_follow_up_record.owner_identity_snapshot、zsjos_lead_complaint.owner_identity_snapshot、
--     zsjos_order.formal_owner_identity、zsjos_user_relation.owner_identity：'education' -> 'sales'。
--   * zsjos_lead_assignment_history.reason：仅系统生成的两个精确值
--     '教务自拓直接归属' / '教务代录新媒体客资直接归属' 改为对应的 '销售…'；人工填写的原因不改。
--   * zsjos_business_event.related_object_refs、zsjos_business_task.payload：仅 JSON 顶层
--     $.generationSource='education_self_sourced_auto' 改为 'sales_self_sourced_auto'，其余键不改。
--     幂等键（idempotency_key，含 'education_self_sourced_auto:…'）是技术标识，不改。
--   * zsjos_lead_inbox_filter_scheme.draft_config_json / published_config_json、
--     zsjos_lead_inbox_filter_version.config_json：条件值数组中的 "education_self_sourced" 删除；
--     若它是数组唯一值则改为 "sales_self_sourced"（保持条件非空且仍可通过保存/回滚校验）。
--   * zsjos_home_metric.draft_json、zsjos_home_metric_version.spec_json：指标来源
--     "source":"education_team" -> "source":"subordinate_sales"，字段键 "educationTeam.X" -> "subordinate.X"，
--     路径 /zsjos/education-team -> /zsjos/subordinate-sales。原兼容层（已删除）的映射与此一致；
--     旧教务团队场景的 22 个字段在 subordinate 场景均有同名字段。
--   * zsjos_advanced_filter_template：scene='education_team' 的模板改为 scene/page_key='subordinate_sales'，
--     filter_json 字段键 "educationTeam.X" -> "subordinate.X"。
--   * 不改 zsjos_business_audit_log、zsjos_order_bak_* / zsjos_bak_* 备份表、通知消息、BPM 表单和
--     已发送内容；不新增/删除业务行，不授予或回收任何权限，不写 system_role_menu。
--
-- 备份：首次执行时创建 zsjos_bak_sei_<源表名>（按源表列结构建空表、id 为主键，生成列存为普通列），只插入「将被修改且尚未备份过」
--   的原始整行；重复执行不覆盖已有备份，备份始终保存每行首次被本脚本修改前的值。
--
-- 可重复性：所有更新都以「当前仍含教务值」为条件；第二次执行 0 行变化，也不新增备份行。
--   本脚本是修复脚本，不写 zsjos_schema_version / zsjos_module_schema_version 账本。
--
-- 失败控制：前置检查失败时 SIGNAL 报错且不写任何数据。备份表 DDL 会隐式提交（仅建空表）；
--   备份插入与全部更新在同一事务中，任一语句失败整体 ROLLBACK 并抛出原错误。
--   失败时辅助过程 zsjos_repair_sei_backfill 会残留；下次执行开头会先删除，也可手工 DROP PROCEDURE。
--
-- 恢复（须单独授权；按表把备份行中本脚本改过的列写回）示例：
--   UPDATE zsjos_lead l JOIN zsjos_bak_sei_zsjos_lead b ON b.id=l.id
--      SET l.owner_identity=b.owner_identity, l.pending_owner_identity=b.pending_owner_identity,
--          l.source_type=b.source_type, l.submission_source_type=b.submission_source_type
--    WHERE l.tenant_id=@tenant_id;
--   其他表同理，只恢复上方「精确数据范围」列出的列。
-- 回滚限制：恢复会同时撤销备份之后经业务操作写入的同列新值；恢复后的 'education' 值在新代码中
--   会被视为未知身份（相关主管操作失败关闭），因此只能与回退到旧后端版本同时进行。

SET NAMES utf8mb4;

-- 必填：执行前改为目标租户 ID。保持 NULL 时前置检查失败，不写任何数据。
SET @tenant_id := NULL;

DROP PROCEDURE IF EXISTS zsjos_repair_sei_backfill;

DELIMITER $$
CREATE PROCEDURE zsjos_repair_sei_backfill(IN p_tenant BIGINT)
BEGIN
  DECLARE v_lead INT DEFAULT 0;
  DECLARE v_duplicate INT DEFAULT 0;
  DECLARE v_activation INT DEFAULT 0;
  DECLARE v_history INT DEFAULT 0;
  DECLARE v_follow_up INT DEFAULT 0;
  DECLARE v_opportunity_follow_up INT DEFAULT 0;
  DECLARE v_complaint INT DEFAULT 0;
  DECLARE v_order INT DEFAULT 0;
  DECLARE v_relation INT DEFAULT 0;
  DECLARE v_event INT DEFAULT 0;
  DECLARE v_task INT DEFAULT 0;
  DECLARE v_inbox_scheme INT DEFAULT 0;
  DECLARE v_inbox_version INT DEFAULT 0;
  DECLARE v_metric INT DEFAULT 0;
  DECLARE v_metric_version INT DEFAULT 0;
  DECLARE v_template INT DEFAULT 0;
  DECLARE v_backup INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  -- ---------------------------------------------------------------- 前置检查（只读）
  IF p_tenant IS NULL OR NOT EXISTS (SELECT 1 FROM system_tenant WHERE id = p_tenant AND deleted = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sales_education_identity_backfill: @tenant_id is not an existing tenant';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                  AND table_name = 'zsjos_lead' AND column_name = 'submission_source_type')
     OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                  AND table_name = 'zsjos_lead' AND column_name = 'pending_owner_identity')
     OR NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                  AND table_name = 'zsjos_home_metric_version') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sales_education_identity_backfill: current lead/home-metric schema is required';
  END IF;

  -- ---------------------------------------------------------------- 备份表（DDL 隐式提交，仅建空表）
  -- 用 CREATE ... SELECT 而非 LIKE：zsjos_lead/zsjos_order 等含生成列，LIKE 出的备份表不允许写入生成列值。
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead (PRIMARY KEY (id)) SELECT * FROM zsjos_lead WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_duplicate_review (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_duplicate_review WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_activation (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_activation WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_assignment_history (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_assignment_history WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_follow_up_record (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_follow_up_record WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_opportunity_follow_up_record (PRIMARY KEY (id)) SELECT * FROM zsjos_opportunity_follow_up_record WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_complaint (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_complaint WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_order (PRIMARY KEY (id)) SELECT * FROM zsjos_order WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_user_relation (PRIMARY KEY (id)) SELECT * FROM zsjos_user_relation WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_business_event (PRIMARY KEY (id)) SELECT * FROM zsjos_business_event WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_business_task (PRIMARY KEY (id)) SELECT * FROM zsjos_business_task WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_inbox_filter_scheme (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_inbox_filter_scheme WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_lead_inbox_filter_version (PRIMARY KEY (id)) SELECT * FROM zsjos_lead_inbox_filter_version WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_home_metric (PRIMARY KEY (id)) SELECT * FROM zsjos_home_metric WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_home_metric_version (PRIMARY KEY (id)) SELECT * FROM zsjos_home_metric_version WHERE 1 = 0;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sei_zsjos_advanced_filter_template (PRIMARY KEY (id)) SELECT * FROM zsjos_advanced_filter_template WHERE 1 = 0;

  -- ---------------------------------------------------------------- 备份 + 更新（单事务）
  -- 每张表：先备份命中条件且尚未备份的整行，再按同一条件更新，二次执行条件不再命中。
  START TRANSACTION;

  -- zsjos_lead
  INSERT INTO zsjos_bak_sei_zsjos_lead SELECT s.* FROM zsjos_lead s
   WHERE s.tenant_id = p_tenant
     AND (s.owner_identity = 'education' OR s.pending_owner_identity = 'education'
       OR s.source_type = 'education_self_sourced' OR s.submission_source_type = 'education_self_sourced')
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead
     SET owner_identity = IF(owner_identity = 'education', 'sales', owner_identity),
         pending_owner_identity = IF(pending_owner_identity = 'education', 'sales', pending_owner_identity),
         source_type = IF(source_type = 'education_self_sourced', 'sales_self_sourced', source_type),
         submission_source_type = IF(submission_source_type = 'education_self_sourced', 'sales_self_sourced', submission_source_type)
   WHERE tenant_id = p_tenant
     AND (owner_identity = 'education' OR pending_owner_identity = 'education'
       OR source_type = 'education_self_sourced' OR submission_source_type = 'education_self_sourced');
  SET v_lead = ROW_COUNT();

  -- zsjos_lead_duplicate_review
  INSERT INTO zsjos_bak_sei_zsjos_lead_duplicate_review SELECT s.* FROM zsjos_lead_duplicate_review s
   WHERE s.tenant_id = p_tenant AND s.submission_source_type = 'education_self_sourced'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_duplicate_review b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_duplicate_review SET submission_source_type = 'sales_self_sourced'
   WHERE tenant_id = p_tenant AND submission_source_type = 'education_self_sourced';
  SET v_duplicate = ROW_COUNT();

  -- zsjos_lead_activation
  INSERT INTO zsjos_bak_sei_zsjos_lead_activation SELECT s.* FROM zsjos_lead_activation s
   WHERE s.tenant_id = p_tenant AND s.source_type = 'education_self_sourced'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_activation b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_activation SET source_type = 'sales_self_sourced'
   WHERE tenant_id = p_tenant AND source_type = 'education_self_sourced';
  SET v_activation = ROW_COUNT();

  -- zsjos_lead_assignment_history：身份快照及系统生成的直接归属原因
  INSERT INTO zsjos_bak_sei_zsjos_lead_assignment_history SELECT s.* FROM zsjos_lead_assignment_history s
   WHERE s.tenant_id = p_tenant
     AND (s.owner_identity_snapshot = 'education' OR s.reason IN ('教务自拓直接归属', '教务代录新媒体客资直接归属'))
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_assignment_history b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_assignment_history
     SET owner_identity_snapshot = IF(owner_identity_snapshot = 'education', 'sales', owner_identity_snapshot),
         reason = CASE reason WHEN '教务自拓直接归属' THEN '销售自拓直接归属'
                              WHEN '教务代录新媒体客资直接归属' THEN '销售代录新媒体客资直接归属'
                              ELSE reason END
   WHERE tenant_id = p_tenant
     AND (owner_identity_snapshot = 'education' OR reason IN ('教务自拓直接归属', '教务代录新媒体客资直接归属'));
  SET v_history = ROW_COUNT();

  -- 跟进、商机跟进、投诉身份快照
  INSERT INTO zsjos_bak_sei_zsjos_lead_follow_up_record SELECT s.* FROM zsjos_lead_follow_up_record s
   WHERE s.tenant_id = p_tenant AND s.owner_identity_snapshot = 'education'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_follow_up_record b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_follow_up_record SET owner_identity_snapshot = 'sales'
   WHERE tenant_id = p_tenant AND owner_identity_snapshot = 'education';
  SET v_follow_up = ROW_COUNT();

  INSERT INTO zsjos_bak_sei_zsjos_opportunity_follow_up_record SELECT s.* FROM zsjos_opportunity_follow_up_record s
   WHERE s.tenant_id = p_tenant AND s.owner_identity_snapshot = 'education'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_opportunity_follow_up_record b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_opportunity_follow_up_record SET owner_identity_snapshot = 'sales'
   WHERE tenant_id = p_tenant AND owner_identity_snapshot = 'education';
  SET v_opportunity_follow_up = ROW_COUNT();

  INSERT INTO zsjos_bak_sei_zsjos_lead_complaint SELECT s.* FROM zsjos_lead_complaint s
   WHERE s.tenant_id = p_tenant AND s.owner_identity_snapshot = 'education'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_complaint b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_complaint SET owner_identity_snapshot = 'sales'
   WHERE tenant_id = p_tenant AND owner_identity_snapshot = 'education';
  SET v_complaint = ROW_COUNT();

  -- zsjos_order 成交归属身份快照
  INSERT INTO zsjos_bak_sei_zsjos_order SELECT s.* FROM zsjos_order s
   WHERE s.tenant_id = p_tenant AND s.formal_owner_identity = 'education'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_order b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_order SET formal_owner_identity = 'sales'
   WHERE tenant_id = p_tenant AND formal_owner_identity = 'education';
  SET v_order = ROW_COUNT();

  -- zsjos_user_relation 兼职指定承接身份
  INSERT INTO zsjos_bak_sei_zsjos_user_relation SELECT s.* FROM zsjos_user_relation s
   WHERE s.tenant_id = p_tenant AND s.owner_identity = 'education'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_user_relation b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_user_relation SET owner_identity = 'sales'
   WHERE tenant_id = p_tenant AND owner_identity = 'education';
  SET v_relation = ROW_COUNT();

  -- 自动生成来源标记（JSON 顶层 generationSource）
  INSERT INTO zsjos_bak_sei_zsjos_business_event SELECT s.* FROM zsjos_business_event s
   WHERE s.tenant_id = p_tenant
     AND JSON_UNQUOTE(JSON_EXTRACT(s.related_object_refs, '$.generationSource')) = 'education_self_sourced_auto'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_business_event b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_business_event
     SET related_object_refs = JSON_SET(related_object_refs, '$.generationSource', 'sales_self_sourced_auto')
   WHERE tenant_id = p_tenant
     AND JSON_UNQUOTE(JSON_EXTRACT(related_object_refs, '$.generationSource')) = 'education_self_sourced_auto';
  SET v_event = ROW_COUNT();

  INSERT INTO zsjos_bak_sei_zsjos_business_task SELECT s.* FROM zsjos_business_task s
   WHERE s.tenant_id = p_tenant
     AND JSON_UNQUOTE(JSON_EXTRACT(s.payload, '$.generationSource')) = 'education_self_sourced_auto'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_business_task b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_business_task
     SET payload = JSON_SET(payload, '$.generationSource', 'sales_self_sourced_auto')
   WHERE tenant_id = p_tenant
     AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.generationSource')) = 'education_self_sourced_auto';
  SET v_task = ROW_COUNT();

  -- 客资收件箱筛选方案：MySQL JSON 规范化输出以 ", " 分隔数组元素
  INSERT INTO zsjos_bak_sei_zsjos_lead_inbox_filter_scheme SELECT s.* FROM zsjos_lead_inbox_filter_scheme s
   WHERE s.tenant_id = p_tenant
     AND (CAST(s.draft_config_json AS CHAR) LIKE '%"education\\_self\\_sourced"%'
       OR CAST(s.published_config_json AS CHAR) LIKE '%"education\\_self\\_sourced"%')
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_inbox_filter_scheme b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_inbox_filter_scheme
     SET draft_config_json = CAST(REPLACE(REPLACE(REPLACE(CAST(draft_config_json AS CHAR),
           ', "education_self_sourced"', ''), '"education_self_sourced", ', ''),
           '"education_self_sourced"', '"sales_self_sourced"') AS JSON),
         published_config_json = IF(published_config_json IS NULL, NULL,
           CAST(REPLACE(REPLACE(REPLACE(CAST(published_config_json AS CHAR),
           ', "education_self_sourced"', ''), '"education_self_sourced", ', ''),
           '"education_self_sourced"', '"sales_self_sourced"') AS JSON))
   WHERE tenant_id = p_tenant
     AND (CAST(draft_config_json AS CHAR) LIKE '%"education\\_self\\_sourced"%'
       OR CAST(published_config_json AS CHAR) LIKE '%"education\\_self\\_sourced"%');
  SET v_inbox_scheme = ROW_COUNT();

  INSERT INTO zsjos_bak_sei_zsjos_lead_inbox_filter_version SELECT s.* FROM zsjos_lead_inbox_filter_version s
   WHERE s.tenant_id = p_tenant AND CAST(s.config_json AS CHAR) LIKE '%"education\\_self\\_sourced"%'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_lead_inbox_filter_version b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_lead_inbox_filter_version
     SET config_json = CAST(REPLACE(REPLACE(REPLACE(CAST(config_json AS CHAR),
           ', "education_self_sourced"', ''), '"education_self_sourced", ', ''),
           '"education_self_sourced"', '"sales_self_sourced"') AS JSON)
   WHERE tenant_id = p_tenant AND CAST(config_json AS CHAR) LIKE '%"education\\_self\\_sourced"%';
  SET v_inbox_version = ROW_COUNT();

  -- 首页指标草稿及发布快照（LONGTEXT，由 Jackson 紧凑序列化，无空格）
  INSERT INTO zsjos_bak_sei_zsjos_home_metric SELECT s.* FROM zsjos_home_metric s
   WHERE s.tenant_id = p_tenant
     AND (s.draft_json LIKE '%"source":"education\\_team"%' OR s.draft_json LIKE '%"educationTeam.%'
       OR s.draft_json LIKE '%/zsjos/education-team%')
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_home_metric b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_home_metric
     SET draft_json = REPLACE(REPLACE(REPLACE(draft_json,
           '"source":"education_team"', '"source":"subordinate_sales"'),
           '"educationTeam.', '"subordinate.'), '/zsjos/education-team', '/zsjos/subordinate-sales')
   WHERE tenant_id = p_tenant
     AND (draft_json LIKE '%"source":"education\\_team"%' OR draft_json LIKE '%"educationTeam.%'
       OR draft_json LIKE '%/zsjos/education-team%');
  SET v_metric = ROW_COUNT();

  INSERT INTO zsjos_bak_sei_zsjos_home_metric_version SELECT s.* FROM zsjos_home_metric_version s
   WHERE s.tenant_id = p_tenant
     AND (s.spec_json LIKE '%"source":"education\\_team"%' OR s.spec_json LIKE '%"educationTeam.%'
       OR s.spec_json LIKE '%/zsjos/education-team%')
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_home_metric_version b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_home_metric_version
     SET spec_json = REPLACE(REPLACE(REPLACE(spec_json,
           '"source":"education_team"', '"source":"subordinate_sales"'),
           '"educationTeam.', '"subordinate.'), '/zsjos/education-team', '/zsjos/subordinate-sales')
   WHERE tenant_id = p_tenant
     AND (spec_json LIKE '%"source":"education\\_team"%' OR spec_json LIKE '%"educationTeam.%'
       OR spec_json LIKE '%/zsjos/education-team%');
  SET v_metric_version = ROW_COUNT();

  -- 教务团队高级筛选模板并入下属销售场景
  INSERT INTO zsjos_bak_sei_zsjos_advanced_filter_template SELECT s.* FROM zsjos_advanced_filter_template s
   WHERE s.tenant_id = p_tenant AND s.scene = 'education_team'
     AND NOT EXISTS (SELECT 1 FROM zsjos_bak_sei_zsjos_advanced_filter_template b WHERE b.id = s.id);
  SET v_backup = v_backup + ROW_COUNT();
  UPDATE zsjos_advanced_filter_template
     SET scene = 'subordinate_sales',
         page_key = IF(page_key = 'education_team', 'subordinate_sales', page_key),
         filter_json = CAST(REPLACE(CAST(filter_json AS CHAR), '"educationTeam.', '"subordinate.') AS JSON)
   WHERE tenant_id = p_tenant AND scene = 'education_team';
  SET v_template = ROW_COUNT();

  COMMIT;

  SELECT 'summary' AS report, p_tenant AS tenant_id, v_lead AS lead_updated, v_duplicate AS duplicate_review_updated,
         v_activation AS activation_updated, v_history AS assignment_history_updated,
         v_follow_up AS follow_up_updated, v_opportunity_follow_up AS opportunity_follow_up_updated,
         v_complaint AS complaint_updated, v_order AS order_updated, v_relation AS user_relation_updated,
         v_event AS business_event_updated, v_task AS business_task_updated,
         v_inbox_scheme AS inbox_scheme_updated, v_inbox_version AS inbox_version_updated,
         v_metric AS home_metric_updated, v_metric_version AS home_metric_version_updated,
         v_template AS filter_template_updated, v_backup AS rows_newly_backed_up;

  -- 只读核对：以下 JSON/文本中仍含教务字样的行未被精确规则覆盖，需人工确认（不修改）。
  SELECT 'residual_review' AS report, 'zsjos_business_event' AS table_name, COUNT(*) AS row_count
    FROM zsjos_business_event WHERE tenant_id = p_tenant
     AND (CAST(related_object_refs AS CHAR) LIKE '%education%' OR reason LIKE '%教务%')
  UNION ALL
  SELECT 'residual_review', 'zsjos_business_task', COUNT(*) FROM zsjos_business_task WHERE tenant_id = p_tenant
     AND (CAST(payload AS CHAR) LIKE '%education%' OR title_snapshot LIKE '%教务%' OR summary_snapshot LIKE '%教务%')
  UNION ALL
  SELECT 'residual_review', 'zsjos_lead_assignment_history.reason', COUNT(*) FROM zsjos_lead_assignment_history
   WHERE tenant_id = p_tenant AND reason LIKE '%教务%'
  UNION ALL
  SELECT 'residual_review', 'zsjos_lead_inbox_filter_scheme', COUNT(*) FROM zsjos_lead_inbox_filter_scheme
   WHERE tenant_id = p_tenant AND (CAST(draft_config_json AS CHAR) LIKE '%教务%' OR CAST(published_config_json AS CHAR) LIKE '%教务%')
  UNION ALL
  SELECT 'residual_review', 'zsjos_home_metric', COUNT(*) FROM zsjos_home_metric
   WHERE tenant_id = p_tenant AND (draft_json LIKE '%education%' OR draft_json LIKE '%教务%');
END$$
DELIMITER ;

CALL zsjos_repair_sei_backfill(@tenant_id);
DROP PROCEDURE IF EXISTS zsjos_repair_sei_backfill;
