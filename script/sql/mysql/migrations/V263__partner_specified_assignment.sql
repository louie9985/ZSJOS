-- UTF-8. V263 follows V262; requires user relation scene/relation, Lead and version tables.
-- Scope: additive source type and assignment identity snapshots; one empty Partner scene per existing scene tenant.
-- No real relationships, business rows or role grants are seeded. Existing employee scenes retain system_user.
-- Apply before application upgrade. Repeatable existence guards; existing scene configuration is preserved.
-- Rollback: revert application and disable the new scene administratively; retain columns/snapshots (no destructive rollback).
SET NAMES utf8mb4;
SET @partner_assignment_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_user_relation_scene' AND column_name='source_type')=0, 'ALTER TABLE zsjos_user_relation_scene ADD COLUMN source_type varchar(32) NOT NULL DEFAULT ''system_user'' COMMENT ''来源主体类型''', 'SELECT 1');
PREPARE partner_assignment_stmt FROM @partner_assignment_ddl;
EXECUTE partner_assignment_stmt;
DEALLOCATE PREPARE partner_assignment_stmt;
SET @partner_assignment_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_user_relation' AND column_name='owner_identity')=0, 'ALTER TABLE zsjos_user_relation ADD COLUMN owner_identity varchar(32) NULL COMMENT ''承接身份''', 'SELECT 1');
PREPARE partner_assignment_stmt FROM @partner_assignment_ddl;
EXECUTE partner_assignment_stmt;
DEALLOCATE PREPARE partner_assignment_stmt;
SET @partner_assignment_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='pending_owner_identity')=0, 'ALTER TABLE zsjos_lead ADD COLUMN pending_owner_identity varchar(32) NULL COMMENT ''指定承接身份快照''', 'SELECT 1');
PREPARE partner_assignment_stmt FROM @partner_assignment_ddl;
EXECUTE partner_assignment_stmt;
DEALLOCATE PREPARE partner_assignment_stmt;
ALTER TABLE zsjos_user_relation_scene MODIFY COLUMN source_post_code varchar(64) COLLATE utf8mb4_unicode_ci NULL COMMENT '来源岗位编码';
INSERT INTO zsjos_user_relation_scene(name,code,source_label,target_label,source_type,source_post_code,target_post_code,target_eligibility_type,target_permission_code,status,remark,tenant_id)
SELECT '兼职客资指定分配','partner_lead_specified_assignment','兼职','接单人员','partner',NULL,NULL,'permission','zsjos:lead:accept',0,'每个兼职配置一个接单人，并明确销售或教务承接身份',t.tenant_id
FROM (SELECT DISTINCT tenant_id FROM zsjos_user_relation_scene WHERE deleted=0) t
WHERE NOT EXISTS (SELECT 1 FROM zsjos_user_relation_scene s WHERE s.tenant_id=t.tenant_id AND s.code='partner_lead_specified_assignment');
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
VALUES('V263','兼职指定分配与教务接单',SHA2('V263__partner_specified_assignment.sql',256));
INSERT IGNORE INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES('core','V263','兼职指定分配与教务接单',SHA2('V263__partner_specified_assignment.sql',256),'baseline',NOW());
