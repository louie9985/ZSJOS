-- -*- coding: utf-8 -*-
-- ZSJ-OS 销售与学生交付合并：业绩归属快照及个人目标一次性按新组织回填
--
-- 目的：
--   业绩事实及目标按「快照」统计（zsjos_performance_attribution 冻结的部门/中心、
--   zsjos_performance_target 已存的 dept_id/center_id），读取时不按员工当前部门重算。
--   用户决定：组织调整（现销售人员归销售一部、现交付人员归销售二部）完成后，把全部既有快照
--   一次性改为每名员工「执行本脚本时」的当前部门及中心，此后继续按快照统计。
--
-- 前置条件（任何一项不满足都不得执行）：
--   1. 已在目标库完成并核对 V371__sales_delivery_merge.sql（两本迁移账本均已登记）。
--   2. 真实组织调整、人员调部已由管理员在 System 中完成；zsjos_performance_org 已为新部门
--      配置中心关联，并已明确标记销售业务边界根（sales_boundary_root=1）。
--   3. 已对目标库做完整备份；执行窗口内暂停业绩目标写入。
--   4. 本脚本须经单独授权后由管理员执行；开发/AI 不在任何真实或共享库执行。
--
-- 执行方式：把下方 @tenant_id 改为目标租户后，使用 UTF-8 客户端执行：
--   mysql --default-character-set=utf8mb4 <库名> < sales_delivery_org_backfill.sql
--
-- 精确数据范围（仅 tenant_id=@tenant_id）：
--   * zsjos_performance_attribution：deleted=0、user_id 对应未删除账号、该账号 dept_id 指向
--     本租户未删除部门的行；只改 dept_id/dept_name/center_id/center_name 四列。
--     dept_name 取 system_dept.name；center_id 取新部门在 zsjos_performance_org 的未删除映射，
--     无映射则置 NULL（与 PerformanceSnapshotService 写入口径一致）；center_name 取中心部门名称。
--   * zsjos_performance_target：deleted=0、scope_type='USER'、business_domain IN ('sales','delivery')、
--     scope_id 对应同上条件账号的行；只改 dept_id/center_id。DEPT/CENTER 目标行不改。
--   * 不改 org_source、business_domain、user_name、金额、版本、修订、订单及其他业务数据；
--     不新增/删除任何业务行，不授予或回收任何权限。
--   * org_source 保持原值：明细/报表 SQL（PerformanceDetailSql.organizationEvidence）只把
--     org_source='frozen' 当作部门归属证据。原为 frozen 的行改写后仍须计入新部门统计；
--     原为 current/NULL 的行不是可证明的组织证据，本脚本不把它们升级为 frozen，避免虚构证据。
--   * 无当前有效部门的账号（账号不存在/已删除/未设部门/部门已删除）对应行不改，结果集列出供核对。
--   * 当前部门不在任何销售边界根内的账号仍按其当前部门回填（用户决定“全部快照按新安排”），
--     但会单独列出，执行前请确认这些人员的组织安排正确。
--
-- 备份：首次执行时创建 zsjos_bak_sdm_perf_attribution / zsjos_bak_sdm_perf_target
--   （CREATE TABLE ... LIKE 源表，无时间戳），只插入「将被修改且尚未备份过」的原始整行。
--   重复执行不会覆盖已有备份，因此备份始终保存每行首次被本脚本修改前的值。
--   备份表沿用源表唯一键；极端情况下冲突会使脚本整体报错回滚，不产生部分更新。
--
-- 可重复性：所有更新均以「当前值与目标值不同」为条件；组织未再变化时第二次执行 0 行变化。
--   若之后组织再次调整并重跑，快照会再次跟随当时的当前部门（备份仍保留最初值）。
--   本脚本是修复脚本，不写 zsjos_schema_version / zsjos_module_schema_version 账本。
--
-- 失败控制：前置检查失败时 SIGNAL 报错且不写任何数据。备份表 DDL 会隐式提交（仅建空表）；
--   备份插入与两表更新在同一事务中，任一语句失败整体 ROLLBACK 并抛出原错误。
--   失败时客户端停止于 CALL，辅助过程 zsjos_repair_sdm_org_backfill 会残留；下次执行开头会先删除，
--   也可手工 DROP PROCEDURE。
--
-- 恢复（仅恢复本脚本改过的列，须单独授权）：
--   UPDATE zsjos_performance_attribution a JOIN zsjos_bak_sdm_perf_attribution b ON b.id=a.id
--      SET a.dept_id=b.dept_id, a.dept_name=b.dept_name, a.center_id=b.center_id, a.center_name=b.center_name
--    WHERE a.tenant_id=@tenant_id;
--   UPDATE zsjos_performance_target t JOIN zsjos_bak_sdm_perf_target b ON b.id=t.id
--      SET t.dept_id=b.dept_id, t.center_id=b.center_id
--    WHERE t.tenant_id=@tenant_id;
-- 回滚限制：恢复会同时撤销备份之后经业务操作（例如保存个人目标）写入的同列新值；
--   备份之后新建的快照/目标行不在备份内，不受恢复影响。确认恢复前须比对业务变化。

SET NAMES utf8mb4;

-- 必填：执行前改为目标租户 ID。保持 NULL 时前置检查失败，不写任何数据。
SET @tenant_id := NULL;

DROP PROCEDURE IF EXISTS zsjos_repair_sdm_org_backfill;

DELIMITER $$
CREATE PROCEDURE zsjos_repair_sdm_org_backfill(IN p_tenant BIGINT)
BEGIN
  DECLARE v_attr_backup INT DEFAULT 0;
  DECLARE v_target_backup INT DEFAULT 0;
  DECLARE v_attr_updated INT DEFAULT 0;
  DECLARE v_target_updated INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS tmp_sdm_attr_plan;
    DROP TEMPORARY TABLE IF EXISTS tmp_sdm_target_plan;
    DROP TEMPORARY TABLE IF EXISTS tmp_sdm_root_depts;
    RESIGNAL;
  END;

  -- ---------------------------------------------------------------- 前置检查（只读）
  IF p_tenant IS NULL OR NOT EXISTS (SELECT 1 FROM system_tenant WHERE id = p_tenant AND deleted = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sales_delivery_org_backfill: @tenant_id is not an existing tenant';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                  AND table_name = 'zsjos_performance_org' AND column_name = 'sales_boundary_root')
     OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                  AND table_name = 'zsjos_performance_target' AND column_name = 'merged_delivery_target_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sales_delivery_org_backfill: V371 schema is required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM zsjos_performance_org
                  WHERE tenant_id = p_tenant AND deleted = b'0' AND sales_boundary_root = b'1') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sales_delivery_org_backfill: sales boundary roots are not configured';
  END IF;

  -- 报告：有归属快照或个人目标、但当前无有效部门的人员（不修改，需人工核对）。
  SELECT 'no_current_department' AS report, x.user_id, MAX(u.nickname) AS nickname, MAX(u.dept_id) AS dept_id,
         SUM(x.attribution_rows) AS attribution_rows, SUM(x.target_rows) AS target_rows
    FROM (SELECT a.user_id, COUNT(*) AS attribution_rows, 0 AS target_rows
            FROM zsjos_performance_attribution a
           WHERE a.tenant_id = p_tenant AND a.deleted = b'0' AND a.user_id IS NOT NULL
           GROUP BY a.user_id
          UNION ALL
          SELECT t.scope_id, 0, COUNT(*)
            FROM zsjos_performance_target t
           WHERE t.tenant_id = p_tenant AND t.deleted = b'0' AND t.scope_type = 'USER'
             AND t.business_domain IN ('sales','delivery')
           GROUP BY t.scope_id) x
    LEFT JOIN system_users u ON u.id = x.user_id AND u.tenant_id = p_tenant AND u.deleted = b'0'
    LEFT JOIN system_dept d ON d.id = u.dept_id AND d.tenant_id = p_tenant AND d.deleted = b'0'
   WHERE d.id IS NULL
   GROUP BY x.user_id
   ORDER BY x.user_id;

  -- ---------------------------------------------------------------- 计划（临时表，不提交）
  DROP TEMPORARY TABLE IF EXISTS tmp_sdm_attr_plan;
  DROP TEMPORARY TABLE IF EXISTS tmp_sdm_target_plan;
  CREATE TEMPORARY TABLE tmp_sdm_attr_plan (
    id bigint NOT NULL PRIMARY KEY, user_id bigint NOT NULL,
    dept_id bigint NOT NULL, dept_name varchar(100) NULL, center_id bigint NULL, center_name varchar(100) NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  CREATE TEMPORARY TABLE tmp_sdm_target_plan (
    id bigint NOT NULL PRIMARY KEY, user_id bigint NOT NULL, dept_id bigint NOT NULL, center_id bigint NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  -- 名称按二进制比较，大小写或全半角变化也视为需要更新；NULL 安全比较保证重复执行 0 行。
  INSERT INTO tmp_sdm_attr_plan (id, user_id, dept_id, dept_name, center_id, center_name)
  SELECT a.id, a.user_id, u.dept_id, d.name, m.center_id, cd.name
    FROM zsjos_performance_attribution a
    JOIN system_users u ON u.id = a.user_id AND u.tenant_id = a.tenant_id AND u.deleted = b'0'
    JOIN system_dept d ON d.id = u.dept_id AND d.tenant_id = a.tenant_id AND d.deleted = b'0'
    LEFT JOIN zsjos_performance_org m ON m.tenant_id = a.tenant_id AND m.dept_id = u.dept_id AND m.deleted = b'0'
    LEFT JOIN system_dept cd ON cd.id = m.center_id AND cd.tenant_id = a.tenant_id AND cd.deleted = b'0'
   WHERE a.tenant_id = p_tenant AND a.deleted = b'0'
     AND (NOT (a.dept_id <=> u.dept_id)
       OR NOT (CAST(a.dept_name AS BINARY) <=> CAST(d.name AS BINARY))
       OR NOT (a.center_id <=> m.center_id)
       OR NOT (CAST(a.center_name AS BINARY) <=> CAST(cd.name AS BINARY)));

  INSERT INTO tmp_sdm_target_plan (id, user_id, dept_id, center_id)
  SELECT t.id, t.scope_id, u.dept_id, m.center_id
    FROM zsjos_performance_target t
    JOIN system_users u ON u.id = t.scope_id AND u.tenant_id = t.tenant_id AND u.deleted = b'0'
    JOIN system_dept d ON d.id = u.dept_id AND d.tenant_id = t.tenant_id AND d.deleted = b'0'
    LEFT JOIN zsjos_performance_org m ON m.tenant_id = t.tenant_id AND m.dept_id = u.dept_id AND m.deleted = b'0'
   WHERE t.tenant_id = p_tenant AND t.deleted = b'0' AND t.scope_type = 'USER'
     AND t.business_domain IN ('sales','delivery')
     AND (NOT (t.dept_id <=> u.dept_id) OR NOT (t.center_id <=> m.center_id));

  -- 报告：当前部门不在任何已配置销售边界根之下、但将被回填的人员（请确认组织安排）。
  DROP TEMPORARY TABLE IF EXISTS tmp_sdm_root_depts;
  CREATE TEMPORARY TABLE tmp_sdm_root_depts (id bigint NOT NULL PRIMARY KEY) ENGINE=InnoDB;
  INSERT INTO tmp_sdm_root_depts (id)
  WITH RECURSIVE branch (id, depth) AS (
    SELECT r.dept_id, 0 FROM zsjos_performance_org r
     WHERE r.tenant_id = p_tenant AND r.deleted = b'0' AND r.sales_boundary_root = b'1'
    UNION ALL
    SELECT d.id, b.depth + 1 FROM branch b
      JOIN system_dept d ON d.parent_id = b.id AND d.tenant_id = p_tenant AND d.deleted = b'0'
     WHERE b.depth < 64
  )
  SELECT DISTINCT id FROM branch;
  SELECT 'outside_sales_roots' AS report, x.user_id, x.dept_id,
         SUM(x.attribution_rows) AS attribution_rows, SUM(x.target_rows) AS target_rows
    FROM (SELECT user_id, dept_id, COUNT(*) AS attribution_rows, 0 AS target_rows FROM tmp_sdm_attr_plan GROUP BY user_id, dept_id
          UNION ALL
          SELECT user_id, dept_id, 0, COUNT(*) FROM tmp_sdm_target_plan GROUP BY user_id, dept_id) x
   WHERE NOT EXISTS (SELECT 1 FROM tmp_sdm_root_depts r WHERE r.id = x.dept_id)
   GROUP BY x.user_id, x.dept_id
   ORDER BY x.user_id;
  DROP TEMPORARY TABLE IF EXISTS tmp_sdm_root_depts;

  -- ---------------------------------------------------------------- 备份表（DDL 隐式提交，仅建空表）
  CREATE TABLE IF NOT EXISTS zsjos_bak_sdm_perf_attribution LIKE zsjos_performance_attribution;
  CREATE TABLE IF NOT EXISTS zsjos_bak_sdm_perf_target LIKE zsjos_performance_target;

  -- ---------------------------------------------------------------- 备份 + 更新（单事务）
  START TRANSACTION;

  INSERT INTO zsjos_bak_sdm_perf_attribution
  SELECT a.* FROM zsjos_performance_attribution a JOIN tmp_sdm_attr_plan p ON p.id = a.id
   WHERE NOT EXISTS (SELECT 1 FROM zsjos_bak_sdm_perf_attribution b WHERE b.id = a.id);
  SET v_attr_backup = ROW_COUNT();

  INSERT INTO zsjos_bak_sdm_perf_target
  SELECT t.* FROM zsjos_performance_target t JOIN tmp_sdm_target_plan p ON p.id = t.id
   WHERE NOT EXISTS (SELECT 1 FROM zsjos_bak_sdm_perf_target b WHERE b.id = t.id);
  SET v_target_backup = ROW_COUNT();

  UPDATE zsjos_performance_attribution a JOIN tmp_sdm_attr_plan p ON p.id = a.id
     SET a.dept_id = p.dept_id, a.dept_name = p.dept_name, a.center_id = p.center_id, a.center_name = p.center_name
   WHERE a.tenant_id = p_tenant;
  SET v_attr_updated = ROW_COUNT();

  UPDATE zsjos_performance_target t JOIN tmp_sdm_target_plan p ON p.id = t.id
     SET t.dept_id = p.dept_id, t.center_id = p.center_id
   WHERE t.tenant_id = p_tenant;
  SET v_target_updated = ROW_COUNT();

  COMMIT;

  DROP TEMPORARY TABLE IF EXISTS tmp_sdm_attr_plan;
  DROP TEMPORARY TABLE IF EXISTS tmp_sdm_target_plan;

  SELECT 'summary' AS report, p_tenant AS tenant_id,
         v_attr_updated AS attribution_updated, v_attr_backup AS attribution_newly_backed_up,
         v_target_updated AS target_updated, v_target_backup AS target_newly_backed_up;
END$$
DELIMITER ;

CALL zsjos_repair_sdm_org_backfill(@tenant_id);
DROP PROCEDURE IF EXISTS zsjos_repair_sdm_org_backfill;
