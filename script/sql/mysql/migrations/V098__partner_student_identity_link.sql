-- V098: optional one-to-one link between a partner identity and a student Person.
-- Data scope: additive empty relation table only. No relationship is inferred or seeded.
-- Repeatability: CREATE TABLE IF NOT EXISTS. Apply after V097.
-- Recovery: forward-only; end a link instead of deleting audit history.
CREATE TABLE IF NOT EXISTS `zsjos_partner_student_link` (
 `id` bigint NOT NULL AUTO_INCREMENT, `partner_id` bigint NOT NULL, `student_person_id` bigint NOT NULL,
 `status` varchar(24) NOT NULL, `started_at` datetime NOT NULL, `ended_at` datetime DEFAULT NULL,
 `operated_by_user_id` bigint NOT NULL, `reason` varchar(500) DEFAULT NULL,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
 `active_partner_id` bigint GENERATED ALWAYS AS (CASE WHEN `status`='active' AND `deleted`=b'0' THEN `partner_id` ELSE NULL END) STORED,
 `active_student_person_id` bigint GENERATED ALWAYS AS (CASE WHEN `status`='active' AND `deleted`=b'0' THEN `student_person_id` ELSE NULL END) STORED,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_active_partner` (`tenant_id`,`active_partner_id`),
 UNIQUE KEY `uk_tenant_active_student` (`tenant_id`,`active_student_person_id`),
 KEY `idx_tenant_partner_status` (`tenant_id`,`partner_id`,`status`),
 KEY `idx_tenant_student_status` (`tenant_id`,`student_person_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='兼职身份与学员身份绑定历史';
INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V098','Partner student identity link','partner-student-link-v2') ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V098','Partner student identity link',SHA2('partner-student-link-v2',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
