-- V259: 修正通用/拍摄外勤工单模板的发起与接收资格，使模板重新可发起。
-- 背景：V206 把两个模板的 source/target_qualification_mode 写成 'PERMISSION'，
--       而资格校验只认 ROLE/DEPARTMENT/ROLE_AND_DEPARTMENT（新增 ALL），导致 catalog 对所有人返回空，无法发起。
-- 口径（业务确认）：
--   通用工单 media_design_edit   发起=总公司及子部门；接收=剪辑师角色(3005)，拒单进角色池。
--   拍摄外勤 filming_field_work  发起=总公司及子部门；接收=全员任选，拒单工单失效。
-- 部门范围在建档时展开为“含全部子部门”的快照，运行期用精确 dept_id 匹配即可覆盖全公司成员。
-- 可重复：按 scene_id + 新版本号 + NOT EXISTS 守卫，重复执行不会新增版本。
-- 回滚限制：只新增版本行并重指向 published_version_id，不改历史版本；如需回滚重指旧版本即可。
SET NAMES utf8mb4;

-- 总公司【中世健】+ 全部子部门（12 中心 + 各自下级部），label 取自 system_dept。
SET @dept_scopes := JSON_ARRAY(
  JSON_OBJECT('id', 1001, 'label', '中世健【总公司】'),
  JSON_OBJECT('id', 1010, 'label', '新媒体与客资中心'),
  JSON_OBJECT('id', 1011, 'label', '新媒体一部'),
  JSON_OBJECT('id', 1012, 'label', '新媒体二部'),
  JSON_OBJECT('id', 1013, 'label', '新媒体三部'),
  JSON_OBJECT('id', 1020, 'label', '销售转化中心'),
  JSON_OBJECT('id', 1021, 'label', '销售转化一部'),
  JSON_OBJECT('id', 1122, 'label', '销售转换二部'),
  JSON_OBJECT('id', 1030, 'label', '报名履约中心'),
  JSON_OBJECT('id', 1031, 'label', '报名履约一部'),
  JSON_OBJECT('id', 1040, 'label', '财务结算中心'),
  JSON_OBJECT('id', 1041, 'label', '财务结算一部'),
  JSON_OBJECT('id', 1050, 'label', '学生服务与交付中心'),
  JSON_OBJECT('id', 1051, 'label', '学生服务与交付一部'),
  JSON_OBJECT('id', 1060, 'label', '考务中心'),
  JSON_OBJECT('id', 1061, 'label', '考务一部'),
  JSON_OBJECT('id', 1070, 'label', '就业指导事业部'),
  JSON_OBJECT('id', 1071, 'label', '就业指导一部'),
  JSON_OBJECT('id', 1080, 'label', 'IP师资与产品研发中心'),
  JSON_OBJECT('id', 1081, 'label', 'IP师资与产品研发一部'),
  JSON_OBJECT('id', 1090, 'label', '服务质控中心'),
  JSON_OBJECT('id', 1091, 'label', '服务质控一部'),
  JSON_OBJECT('id', 1100, 'label', '人力资源中心'),
  JSON_OBJECT('id', 1101, 'label', '人力资源一部'),
  JSON_OBJECT('id', 1110, 'label', '行政综合事务部'),
  JSON_OBJECT('id', 1111, 'label', '行政综合事务一部'),
  JSON_OBJECT('id', 1120, 'label', 'AI应用开发部'),
  JSON_OBJECT('id', 1121, 'label', 'AI应用开发一部')
);

DROP PROCEDURE IF EXISTS `zsjos_v257_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v257_apply`()
BEGIN
  DECLARE v_scene_id BIGINT;
  DECLARE v_tenant_id BIGINT;
  DECLARE v_new_no INT;

  -- 通用工单：发起=总公司整树；接收=剪辑师角色，拒单进角色池
  SELECT id, tenant_id INTO v_scene_id, v_tenant_id
    FROM `zsjos_work_order_scene` WHERE code='media_design_edit' AND deleted=b'0' LIMIT 1;
  IF v_scene_id IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM `zsjos_work_order_scene_version`
        WHERE tenant_id=v_tenant_id AND scene_id=v_scene_id AND version_no=2 AND deleted=b'0') THEN
      INSERT INTO `zsjos_work_order_scene_version`
        (`tenant_id`,`scene_id`,`version_no`,`code`,`name`,`remark`,`category_value`,`category_label_snapshot`,
         `icon`,`sort`,`processor_type`,`allowed_assignment_types_json`,`source_qualification_mode`,`source_role_scopes_json`,
         `source_dept_scopes_json`,`target_qualification_mode`,`target_role_scopes_json`,`target_dept_scopes_json`,
         `rejection_strategy`,`number_prefix`,`number_reset_period`,`number_sequence_width`,`fields_json`,
         `published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
      SELECT `tenant_id`,`scene_id`,2,`code`,`name`,`remark`,`category_value`,`category_label_snapshot`,
             `icon`,`sort`,`processor_type`,'["PERSON"]','DEPARTMENT','[]',
             @dept_scopes,'ROLE',JSON_ARRAY(JSON_OBJECT('id', 3005, 'label', '剪拍专员')),'[]',
             'ROLE_POOL',`number_prefix`,`number_reset_period`,`number_sequence_width`,`fields_json`,
             1,NOW(),'V259',NOW(),'V259',NOW(),b'0'
        FROM `zsjos_work_order_scene_version`
       WHERE tenant_id=v_tenant_id AND scene_id=v_scene_id AND id=(
             SELECT published_version_id FROM `zsjos_work_order_scene` WHERE id=v_scene_id);
    END IF;
    UPDATE `zsjos_work_order_scene` s
       JOIN `zsjos_work_order_scene_version` v
         ON v.tenant_id=s.tenant_id AND v.scene_id=s.id AND v.version_no=2 AND v.deleted=b'0'
       SET s.status=1, s.lifecycle_status='PUBLISHED', s.published_version_id=v.id, s.published_version_no=2,
           s.updater='V259', s.update_time=NOW()
     WHERE s.id=v_scene_id;
  END IF;

  -- 拍摄外勤工单：发起=总公司整树；接收=全员任选，拒单工单失效
  SELECT id, tenant_id INTO v_scene_id, v_tenant_id
    FROM `zsjos_work_order_scene` WHERE code='filming_field_work' AND deleted=b'0' LIMIT 1;
  IF v_scene_id IS NOT NULL THEN
    SELECT COALESCE(MAX(version_no), 0) + 1 INTO v_new_no
      FROM `zsjos_work_order_scene_version` WHERE tenant_id=v_tenant_id AND scene_id=v_scene_id AND deleted=b'0';
    IF NOT EXISTS (SELECT 1 FROM `zsjos_work_order_scene_version`
        WHERE tenant_id=v_tenant_id AND scene_id=v_scene_id AND version_no=v_new_no AND deleted=b'0') THEN
      INSERT INTO `zsjos_work_order_scene_version`
        (`tenant_id`,`scene_id`,`version_no`,`code`,`name`,`remark`,`category_value`,`category_label_snapshot`,
         `icon`,`sort`,`processor_type`,`allowed_assignment_types_json`,`source_qualification_mode`,`source_role_scopes_json`,
         `source_dept_scopes_json`,`target_qualification_mode`,`target_role_scopes_json`,`target_dept_scopes_json`,
         `rejection_strategy`,`number_prefix`,`number_reset_period`,`number_sequence_width`,`fields_json`,
         `published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
      SELECT `tenant_id`,`scene_id`,v_new_no,`code`,`name`,`remark`,`category_value`,`category_label_snapshot`,
             `icon`,`sort`,`processor_type`,'["PERSON","DEPARTMENT"]','DEPARTMENT','[]',
             @dept_scopes,'ALL','[]','[]',
             'INVALID',`number_prefix`,`number_reset_period`,`number_sequence_width`,`fields_json`,
             1,NOW(),'V259',NOW(),'V259',NOW(),b'0'
        FROM `zsjos_work_order_scene_version`
       WHERE tenant_id=v_tenant_id AND scene_id=v_scene_id AND id=(
             SELECT published_version_id FROM `zsjos_work_order_scene` WHERE id=v_scene_id);
    END IF;
    UPDATE `zsjos_work_order_scene` s
       JOIN `zsjos_work_order_scene_version` v
         ON v.tenant_id=s.tenant_id AND v.scene_id=s.id AND v.version_no=v_new_no AND v.deleted=b'0'
       SET s.status=1, s.lifecycle_status='PUBLISHED', s.published_version_id=v.id, s.published_version_no=v_new_no,
           s.updater='V259', s.update_time=NOW()
     WHERE s.id=v_scene_id;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v257_apply`();
DROP PROCEDURE `zsjos_v257_apply`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V259','Work order template qualification fix',SHA2('V259__work_order_template_qualification_fix.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V259','Work order template qualification fix',SHA2('V259__work_order_template_qualification_fix.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
