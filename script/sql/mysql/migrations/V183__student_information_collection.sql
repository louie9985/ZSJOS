-- UTF-8. V183, corrected unreleased development baseline, after V182 and before V184.
-- Scope: four empty tables and System menu definitions only. No business data, dictionary options or role grants.
-- Repeatable CREATE/guarded inserts preserve administrator configuration.
-- Rollback: disable feature permissions; retain submissions, template versions and encryption keys.
-- Prerequisites: System menus and Core version registries; verify from bootstrap and from V182.
SET NAMES utf8mb4;

-- UTF-8. Student information collection schema; empty business tables only.
CREATE TABLE IF NOT EXISTS zsjos_student_info_form (
 id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, lead_id bigint NOT NULL,
 sales_user_id bigint NOT NULL, config_version_id bigint NOT NULL,
 token_hash char(64) NOT NULL, token_ciphertext varchar(512) NOT NULL,
 status varchar(16) NOT NULL, expires_at datetime NOT NULL, revoked_at datetime DEFAULT NULL,
 submitted_at datetime DEFAULT NULL, submit_source varchar(16) DEFAULT NULL,
 active_lead_id bigint GENERATED ALWAYS AS (IF(status='DRAFT' AND deleted=b'0',lead_id,NULL)) STORED,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id), UNIQUE KEY uk_student_info_token(token_hash),
 UNIQUE KEY uk_student_info_active(tenant_id,active_lead_id),
 KEY idx_student_info_lead(tenant_id,lead_id,id), KEY idx_student_info_expiry(tenant_id,status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员信息收集表';

CREATE TABLE IF NOT EXISTS zsjos_student_info_form_value (
 id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, form_id bigint NOT NULL,
 field_key varchar(64) NOT NULL, field_type varchar(32) NOT NULL, value_text text,
 dict_type varchar(100) DEFAULT NULL, value_code varchar(200) DEFAULT NULL,
 value_label_snapshot varchar(512) DEFAULT NULL, area_code_path varchar(128) DEFAULT NULL,
 area_label_snapshot varchar(512) DEFAULT NULL, `sensitive` bit(1) NOT NULL DEFAULT b'0',
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id), UNIQUE KEY uk_student_info_field(tenant_id,form_id,field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员信息字段快照';

CREATE TABLE IF NOT EXISTS zsjos_student_info_form_config (
 id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, version_no int NOT NULL,
 revision int NOT NULL DEFAULT 0, status varchar(16) NOT NULL, fields_json json NOT NULL,
 published_at datetime DEFAULT NULL,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id), UNIQUE KEY uk_student_info_config_version(tenant_id,version_no),
 KEY idx_student_info_config_state(tenant_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员信息收集表配置';

CREATE TABLE IF NOT EXISTS zsjos_student_info_config_lock (
 tenant_id bigint NOT NULL, PRIMARY KEY(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收集表配置租户锁';

-- Configuration belongs to Admin; menu path is relative to its existing ZSJOS root.
INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '学员信息收集表配置','zsjos:student-info-form:config:query',2,90,parent.parent_id,
 'student-info-form-config','ep:document','zsjos/studentInfoFormConfig/index','ZsjosStudentInfoFormConfig',
 0,b'1',b'1',b'0','migration-V183','migration-V183',b'0'
FROM (SELECT parent_id FROM system_menu WHERE component='zsjos/mediaAccountFieldConfig/index' AND deleted=b'0' ORDER BY id LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:student-info-form:config:query' AND deleted=b'0');

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT seed.name,seed.permission,3,seed.sort,parent.id,'','','',NULL,0,b'1',b'1',b'0','migration-V183','migration-V183',b'0'
FROM (SELECT MIN(id) id FROM system_menu WHERE permission='zsjos:student-info-form:config:query' AND deleted=b'0') parent
JOIN (
 SELECT '编辑收集表配置' name,'zsjos:student-info-form:config:update' permission,1 sort
 UNION ALL SELECT '发布收集表配置','zsjos:student-info-form:config:publish',2
) seed
WHERE parent.id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission=seed.permission AND m.deleted=b'0');

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT seed.name,seed.permission,3,seed.sort,parent.id,'','','',NULL,0,b'1',b'1',b'0','migration-V183','migration-V183',b'0'
FROM (SELECT MIN(id) id FROM system_menu WHERE permission='zsjos:lead:query' AND type=2 AND deleted=b'0') parent
JOIN (
 SELECT '生成信息收集表' name,'zsjos:student-info-form:create' permission,40 sort
 UNION ALL SELECT '查看收集链接','zsjos:student-info-form:link-read',41
 UNION ALL SELECT '重新生成收集链接','zsjos:student-info-form:regenerate',42
 UNION ALL SELECT '撤销收集链接','zsjos:student-info-form:revoke',43
 UNION ALL SELECT '查看学员信息','zsjos:student-info-form:read',44
 UNION ALL SELECT '查看完整敏感字段','zsjos:student-info-form:sensitive-read',45
 UNION ALL SELECT '导出学员信息','zsjos:student-info-form:export',46
) seed
WHERE parent.id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission=seed.permission AND m.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES('V183','Lead student information collection','V183__student_information_collection.sql',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES('core','V183','Lead student information collection',SHA2('V183__student_information_collection.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
