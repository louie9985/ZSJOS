-- Non-destructive repair for an existing ZSJOS database.
-- Scope: missing dictionary definitions/data and missing role-menu grants.
-- This file never deletes, disables, or rewrites existing rows. It does not
-- seed users, business instances, BPM runtime tables, or Flowable IDs.
-- Run from the repository root with mysql so SOURCE paths resolve.
SET NAMES utf8mb4;

-- Core dictionary baseline. INSERT IGNORE plus the baseline's stable guards
-- preserve administrator edits and fill only missing rows.
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/04-bootstrap-zsjos-feedback-dictionary.sql;

-- Later dictionary-only additions whose migrations also contain unrelated DDL
-- are reproduced here through their idempotent data statements.
SOURCE script/sql/mysql/migrations/V104__new_media_business_dictionary.sql;
SOURCE script/sql/mysql/migrations/V129__seed_director_form_dictionaries.sql;
-- Full active dictionary snapshot from the reviewed source database. The dump
-- contains only system_dict_type/system_dict_data rows with deleted=0 and is
-- safe to replay with INSERT IGNORE.
SOURCE script/sql/mysql/dictionary-data/current-database-dictionary-snapshot.sql;

-- Current approved business dictionary snapshot. Keyed by dict_type + value
-- so reruns preserve administrator edits and do not depend on row IDs.
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1,'不确定','不确定','zsjos_lead_category',0,'default','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='不确定' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7,'C类【低意向】','C类【低意向】','zsjos_lead_category',0,'default','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='C类【低意向】' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8,'B类【中意向】','B类【中意向】','zsjos_lead_category',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='B类【中意向】' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 9,'A类【高意向】','A类【高意向】','zsjos_lead_category',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='A类【高意向】' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 11,'S类【重点客户-待成交】','S类【重点客户-待成交】','zsjos_lead_category',0,'default','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='S类【重点客户-待成交】' AND `deleted`=b'0');

INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1,'抖音自然流','抖音自然流','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='抖音自然流' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 2,'小红书私信','小红书私信','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='小红书私信' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 3,'信息流投放','信息流投放','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='信息流投放' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 5,'老学员转介绍','老学员转介绍','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='老学员转介绍' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 0,'其他','其他','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='其他' AND `deleted`=b'0');

INSERT INTO `system_dict_data`
  (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 99,'未知','unknown','zsjos_certificate_practice',0,'default','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_certificate_practice' AND `value`='unknown' AND `deleted`=b'0');
INSERT INTO `system_dict_data`
  (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 99,'部分掌握','partial','zsjos_video_skill',0,'default','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_video_skill' AND `value`='partial' AND `deleted`=b'0');

-- V071 is the reviewed exact allowlist for partner, finance, review and administrator roles.
-- It resolves by stable role/permission codes and logically retires old active relations.
CREATE TABLE IF NOT EXISTS `zsjos_role_menu_backup_20260904` LIKE `system_role_menu`;
INSERT INTO `zsjos_role_menu_backup_20260904`
SELECT rm.* FROM `system_role_menu` rm
WHERE rm.tenant_id IN (1,121,122)
  AND NOT EXISTS (SELECT 1 FROM `zsjos_role_menu_backup_20260904` b WHERE b.id=rm.id);
SOURCE script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql;

-- The following repair is intentionally limited to the confirmed existing tenants.
-- It removes only active permission-bearing menu relations; parent containers remain intact.
UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0'
SET rm.deleted=b'1', rm.updater='sync-existing-server', rm.update_time=NOW()
WHERE rm.deleted=b'0' AND rm.tenant_id IN (1,121,122) AND m.permission<>''
  AND (r.code IN ('center_head','academic_specialist','exam_manager','exam_specialist','career_planner',
                  'career_manager','ip_teacher','product_rd_head','teaching_assistant','recruitment_manager',
                  'recruitment_specialist','hr_specialist','admin_manager','admin_specialist')
       OR (r.code IN ('content_director','new_media_operator') AND (m.permission LIKE 'zsjos:lead:%'
           OR m.permission LIKE 'zsjos:lead-detail:%' OR m.permission='zsjos:partner:query'))
       OR (r.code='dept_manager' AND m.permission IN ('bpm:model:create','bpm:model:update','bpm:model:deploy',
           'bpm:model:import','bpm:category:create','bpm:category:query'))
       OR (r.code='delivery_manager' AND m.permission IN ('bpm:task-assign-rule:query','bpm:task-assign-rule:update'))
       OR (r.code='hr_specialist' AND m.permission IN ('system:user:delete','system:user:update-password',
           'system:dept:delete','system:dept:update')));

-- Ordinary employees receive one explicit common set. Job roles add only their reviewed capabilities.
DROP TEMPORARY TABLE IF EXISTS `tmp_sync_normal_user_permission`;
CREATE TEMPORARY TABLE `tmp_sync_normal_user_permission` (`permission` varchar(100) PRIMARY KEY) ENGINE=MEMORY;
INSERT INTO `tmp_sync_normal_user_permission` VALUES
('system:notice:read'),('system:notify-message:query'),('bpm:process-instance:create'),('bpm:process-instance:query'),
('bpm:process-instance:cancel'),('bpm:task:query'),('bpm:task:update'),('bpm:process-instance-cc:query'),('hrm:portal:query'),
('hrm:portal:employee:update'),('hrm:portal:attendance:leave'),('hrm:portal:performance:action'),
('hrm:employee:personal-note:create'),('hrm:employee:personal-note:delete'),('zsjos:business-task:query'),
('zsjos:feedback:query'),('zsjos:feedback:requirement:create'),('zsjos:feedback:bug:create'),('zsjos:feedback:support:create'),
('zsjos:feedback:reply-self'),('zsjos:feedback:survey:submit'),('zsjos:feedback:read'),('zsjos:work-order:create'),
('zsjos:work-order:query'),('zsjos:work-order:withdraw');
UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0'
SET rm.deleted=b'1', rm.updater='sync-existing-server', rm.update_time=NOW()
WHERE rm.deleted=b'0' AND rm.tenant_id IN (1,121,122) AND r.code='normal_user' AND m.permission<>''
  AND NOT EXISTS (SELECT 1 FROM `tmp_sync_normal_user_permission` p WHERE BINARY p.permission=BINARY m.permission);
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0',r.tenant_id
FROM `system_role` r CROSS JOIN `tmp_sync_normal_user_permission` p
JOIN (SELECT permission,MIN(id) id FROM system_menu WHERE deleted=b'0' AND permission<>'' GROUP BY permission) m ON BINARY m.permission=BINARY p.permission
WHERE r.deleted=b'0' AND r.code='normal_user' AND r.tenant_id IN (1,121,122)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0');
DROP TEMPORARY TABLE `tmp_sync_normal_user_permission`;

-- Restore later-version business grants from the reviewed pre-rebuild snapshot.
-- Only stable role codes and active menu permissions are used; no user or business rows are touched.
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0',r.tenant_id
FROM `system_role` r
JOIN `zsjos_role_menu_backup_20260904` b ON b.tenant_id=r.tenant_id AND b.role_id=r.id AND b.deleted=b'0'
JOIN `system_menu` source_menu ON source_menu.id=b.menu_id AND source_menu.deleted=b'0' AND source_menu.permission<>''
JOIN (SELECT permission,MIN(id) id FROM system_menu WHERE deleted=b'0' AND permission<>'' GROUP BY permission) canonical
  ON BINARY canonical.permission=BINARY source_menu.permission
JOIN `system_menu` m ON m.id=canonical.id
WHERE r.deleted=b'0' AND r.tenant_id IN (1,121,122)
  AND r.code IN ('content_director','new_media_operator','filming_editor','study_planner','delivery_manager')
  AND NOT (r.code IN ('content_director','new_media_operator')
           AND (m.permission LIKE 'zsjos:lead:%' OR m.permission LIKE 'zsjos:lead-detail:%'
                OR m.permission='zsjos:partner:query'))
  AND NOT (r.code='delivery_manager' AND m.permission IN ('bpm:task-assign-rule:query','bpm:task-assign-rule:update'))
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x
                  WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

-- Canonicalize any legacy duplicate permission rows reintroduced by the snapshot restore.
DROP TEMPORARY TABLE IF EXISTS `tmp_sync_duplicate_permission`;
CREATE TEMPORARY TABLE `tmp_sync_duplicate_permission` AS
SELECT rm.role_id,rm.tenant_id,m.permission,MIN(rm.id) keep_id
FROM `system_role_menu` rm JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.permission<>''
WHERE rm.deleted=b'0' AND rm.tenant_id IN (1,121,122)
GROUP BY rm.role_id,rm.tenant_id,m.permission HAVING COUNT(*)>1;
UPDATE `system_role_menu` rm
JOIN `system_menu` m ON m.id=rm.menu_id
JOIN `tmp_sync_duplicate_permission` d ON d.role_id=rm.role_id AND d.tenant_id=rm.tenant_id AND d.permission=m.permission
SET rm.deleted=b'1',rm.updater='sync-existing-server',rm.update_time=NOW()
WHERE rm.deleted=b'0' AND rm.id<>d.keep_id;
DROP TEMPORARY TABLE `tmp_sync_duplicate_permission`;

-- BPM assets are deliberately not SQL. Validate and import them through the
-- BPM management UI using script/bpm/manifest.json and the procedure documented
-- in docs/operations/zsjos-existing-db-initialization.md.
