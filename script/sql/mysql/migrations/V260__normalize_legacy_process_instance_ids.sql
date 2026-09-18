-- V260: 规整历史迁移遗留的 BPM 流程实例号，让「无实例」可被唯一键表达。
-- 背景：从遗留系统迁移进来的审批数据把业务单号写进了 process_instance_id（如 OD2026081617240100001、
--       ZSJ20260827172555SR00001、AP2026081314220400001），而真正的 Flowable 实例号是 UUID。
--       这些值在引擎中一律查不到，属于「假装有实例」的脏数据。
-- 影响面（2026-09-17 实测，脚本只处理「已软删」的行）：
--       zsjos_order_approval_round 15 条（均已随 KZ 清理软删，订单侧同样已软删）
--       zsjos_order_command        28 条（全部已软删）
--       zsjos_lead_appeal           5 条（全部已软删）
--       zsjos_feedback            2 条（未删除且停在 APPROVING，见下方状态修正）
--       zsjos_feedback_round      2 条（同上）
-- 明确不处理：zsjos_order_approval_round.id=15（order 18，订单号 OD202608231715100003）。
--       该行未删除、订单停在 pending_approval，但引擎里从无对应实例，是一条走不下去的死单。
--       本轮只做实例号规整，不替业务决定订单终态：改掉它的实例号会让「有订单、无轮次状态」更难排查，
--       留待业务确认后单独处理（终止或补审）。同批的 id=20（order 23）订单已软删，故一并规整。
-- 关键约束：zsjos_order_approval_round 的 process_instance_id 是 NOT NULL，
--       且带唯一键 uk_tenant_process_instance(tenant_id, process_instance_id)。
--       15 条若统一置空串会撞唯一键；MySQL 唯一索引允许多个 NULL，因此必须先放开为可空，
--       再用 NULL 表达「本轮没有对应的流程实例」。zsjos_withdrawal 已是同一模式，本脚本与其对齐。
-- 可重复：结构变更走 information_schema 守卫；数据修正限定在「已软删 + 值不是 UUID + 引擎中确实不存在」的行。
-- 回滚限制：数据修正不可逆（原始假实例号只存在于备份与审计日志）；结构放开为可空可保留。
-- 依赖：zsjos_order_approval_round / zsjos_order_command 由 V023、V055 建立。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v260_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v260_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  -- 1. 放开为可空，使「无实例」能存在多行而不违反唯一键
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_order_approval_round' AND column_name='process_instance_id'
      AND is_nullable='NO') THEN
    ALTER TABLE `zsjos_order_approval_round`
      MODIFY COLUMN `process_instance_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        DEFAULT NULL COMMENT 'BPM 流程实例编号；历史迁移订单为空表示未走本系统审批';
  END IF;

  -- 2. 规整轮次：只处理「已软删 + 引擎中确实不存在 + 值不是 UUID」的行。
  --    刻意排除未删除的 id=15（order 18）——那是条死单，留待业务单独确认终态。
  UPDATE `zsjos_order_approval_round` r
  SET r.`process_instance_id` = NULL
  WHERE r.`deleted` = b'1'
    AND r.`process_instance_id` IS NOT NULL
    AND r.`process_instance_id` <> ''
    AND NOT REGEXP_LIKE(r.`process_instance_id`, '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-')
    AND NOT EXISTS (SELECT 1 FROM `ACT_HI_PROCINST` p WHERE p.`ID_` = r.`process_instance_id`);

  -- 3. 规整命令表（该列仅参与幂等判等，且这些行已软删、查询走逻辑删除过滤）
  UPDATE `zsjos_order_command` c
  SET c.`process_instance_id` = ''
  WHERE c.`deleted` = b'1'
    AND c.`process_instance_id` <> ''
    AND NOT REGEXP_LIKE(c.`process_instance_id`, '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-')
    AND NOT EXISTS (SELECT 1 FROM `ACT_HI_PROCINST` p WHERE p.`ID_` = c.`process_instance_id`);

  -- 4. 规整申诉：只碰已软删且引擎无实例的行
  UPDATE `zsjos_lead_appeal` a
  SET a.`process_instance_id` = NULL
  WHERE a.`deleted` = b'1'
    AND a.`process_instance_id` IS NOT NULL
    AND a.`process_instance_id` <> ''
    AND NOT REGEXP_LIKE(a.`process_instance_id`, '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-')
    AND NOT EXISTS (SELECT 1 FROM `ACT_HI_PROCINST` p WHERE p.`ID_` = a.`process_instance_id`);

  -- 5. 反馈：这些需求单停在 APPROVING，但对应实例从未创建，用户既看不到也无法处理。
  --    修正为 WAITING（可受理）让它们重新进入处理流程；同时清空假实例号。
  UPDATE `zsjos_feedback` f
  SET f.`process_instance_id` = NULL,
      f.`status` = 'WAITING'
  WHERE f.`status` = 'APPROVING'
    AND f.`process_instance_id` IS NOT NULL
    AND f.`process_instance_id` <> ''
    AND NOT REGEXP_LIKE(f.`process_instance_id`, '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-')
    AND NOT EXISTS (SELECT 1 FROM `ACT_HI_PROCINST` p WHERE p.`ID_` = f.`process_instance_id`);

  UPDATE `zsjos_feedback_round` fr
  SET fr.`process_instance_id` = NULL,
      fr.`status` = 'WAITING'
  WHERE fr.`status` = 'APPROVING'
    AND fr.`process_instance_id` IS NOT NULL
    AND fr.`process_instance_id` <> ''
    AND NOT REGEXP_LIKE(fr.`process_instance_id`, '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-')
    AND NOT EXISTS (SELECT 1 FROM `ACT_HI_PROCINST` p WHERE p.`ID_` = fr.`process_instance_id`);

  -- 6. 反馈表统一用 NULL 表达无实例
  UPDATE `zsjos_feedback` SET `process_instance_id` = NULL WHERE `process_instance_id` = '';
  UPDATE `zsjos_feedback_round` SET `process_instance_id` = NULL WHERE `process_instance_id` = '';

  -- 版本登记
  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V260','Normalize legacy BPM process instance ids','V260__normalize_legacy_process_instance_ids.sql',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V260','Normalize legacy BPM process instance ids',
          SHA2('V260__normalize_legacy_process_instance_ids.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v260_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v260_apply`;
