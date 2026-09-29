-- UTF-8. V279: 归属快照组织来源标注 + 用户 400 双身份拆分。
-- Migration-Owner: ai
-- Prerequisites: current Core baseline through V278.
-- Scope: 一处缺失式加列、一次快照来源回填、一处既有角色绑定移除。不改业务事实、金额、状态。
-- Replay: 列用 information_schema 保护；来源回填只写 org_source IS NULL 的行；
--         角色移除为逻辑删除且按 (user_id, role_code) 幂等。重复执行零额外影响。
-- Recovery: 保留新列（可空、只增不减）；角色如需恢复须重新授权，本迁移不自动回滚。
SET NAMES utf8mb4;

INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
VALUES('V279','Attribution org provenance and dual-identity split',SHA2('V279__attribution_org_provenance_and_identity_split.sql',256));

-- ===========================================================================
-- 1) 归属快照组织来源标注
-- ===========================================================================
-- 背景：绩效快照的 dept_id/center_id 由 PerformanceSnapshotService.base() 按「写入当时的
-- 用户归属」填充，并非「业务事实发生当时」的组织。对历史补写的快照，该值等于今天的组织，
-- 不能当作历史组织证据。旧库也没有组织主数据（org_department 仅 4 行、无层级），
-- 因此无法还原真实历史组织。
--
-- 该列让报表区分两类快照：
--   frozen  = 事实发生时即写入（写入时间与 received_at 同步），可作为当时组织
--   current = 事后补写（补写路径），组织为补写时点的当前归属，不是历史证据
-- 不修改任何既有 dept_id/center_id 值，只增加来源判定。

SET @v279_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='zsjos_performance_attribution' AND column_name='org_source'),
   'SELECT 1','ALTER TABLE zsjos_performance_attribution ADD COLUMN org_source varchar(16) NULL COMMENT ''组织快照来源：frozen=事实发生时写入；current=事后按当前组织补写，非历史证据'''));
PREPARE v279_stmt FROM @v279_ddl; EXECUTE v279_stmt; DEALLOCATE PREPARE v279_stmt;

-- 判定规则（只回填未标注的行，可重复执行）：
--   写入时间与 received_at 相差在 1 小时内的，视为事实发生时即写入 -> frozen
--   其余（含批量补写）一律 -> current
-- 接收时间为空的事实（如 DISPATCH）按 create_time 无法判定归属时点，保守记为 current。
UPDATE zsjos_performance_attribution
SET org_source = CASE
      WHEN received_at IS NOT NULL
       AND create_time IS NOT NULL
       AND timestampdiff(minute, received_at, create_time) BETWEEN 0 AND 60
      THEN 'frozen'
      ELSE 'current'
    END
WHERE org_source IS NULL;

-- ===========================================================================
-- 2) 用户 400「周老师【梁颖】」双身份拆分
-- ===========================================================================
-- 背景：旧库把「学习规划师」与「新媒体运营」两种身份集成在同一个账号。现已拆分为两个账号：
--   400 XiaoZhou   周老师【梁颖】 dept 1051  学习规划师（education）业务线
--   527 LiangYing  梁颖          dept 1011  新媒体运营业务线
-- 依据：
--   * V273 已确认旧库 academic_customer.owner_id=159(梁颖) 映射到 400，并为其补 study_planner。
--   * 400 名下的 20 条客资全部 owner_identity='education'，无一条销售/新媒体归属。
--   * 400 的新媒体账号、内容、拍剪工单、定位卡记录数为 0；527 持有 2 个新媒体账号。
--   * 400 的 new_media_operator 是 2026-09-28 16:35 由 39 追加的重复授权。
-- 因此移除 400 上的 new_media_operator，保留 study_planner / normal_user。
-- 不动 527，不删除任何账号，不改部门，不改业务数据。

UPDATE `system_user_role` ur
JOIN `system_role` r ON r.`id` = ur.`role_id`
SET ur.`deleted` = b'1', ur.`updater` = 'V279', ur.`update_time` = NOW()
WHERE ur.`user_id` = 400
  AND r.`code` = 'new_media_operator'
  AND ur.`deleted` = b'0';

INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES('core','V279','Attribution org provenance and dual-identity split',SHA2('V279__attribution_org_provenance_and_identity_split.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
