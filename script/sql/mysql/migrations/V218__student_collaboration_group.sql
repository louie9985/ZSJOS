-- V218: explicit, immutable one-service-relation collaboration boundary.
-- Migration-Owner: ai
-- Development-only new foundation, after V217 (service operator field from V128).
-- Corrects the never-executed V215 collaboration draft; the unrelated V215 gift migration is unchanged.
-- Scope: create groups for non-deleted service relations, including pending/closed history;
-- retain null members and original relation IDs, never merge by current member identity.
-- Repeated runs preserve existing group members, status and bindings. No rows are deleted.
-- Source relations remain required for new groups. A mismatching existing binding is not overwritten.
-- Run verify/student-collaboration-foundation.sql; any mismatch blocks delivery.
-- Rollback: additive only; retain groups and bindings after consumers start using them.
-- Core manifest discovers V218 automatically after bootstrap; module checksums are recorded by zsjos-db.
SET NAMES utf8mb4;
SET @schema_name := DATABASE();
CREATE TABLE IF NOT EXISTS `zsjos_collaboration_group` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL,
  `source_service_relation_id` bigint NOT NULL, `student_person_id` bigint NOT NULL,
  `director_user_id` bigint DEFAULT NULL, `operator_user_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'active', `creator` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `version` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_collab_source_relation` (`tenant_id`,`source_service_relation_id`,`deleted`),
  KEY `idx_collab_student` (`tenant_id`,`student_person_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员编导运营协作组';
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='zsjos_service_relation' AND column_name='collaboration_group_id')=0,
 'ALTER TABLE `zsjos_service_relation` ADD COLUMN `collaboration_group_id` bigint DEFAULT NULL COMMENT ''编导运营协作组'' AFTER `id`', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
INSERT INTO `zsjos_collaboration_group` (`tenant_id`,`source_service_relation_id`,`student_person_id`,`director_user_id`,`operator_user_id`,`status`,`creator`,`updater`)
SELECT sr.tenant_id,sr.id,sr.person_id,sr.content_director_user_id,sr.operator_user_id,IF(sr.status IN ('active','paused'),'active','closed'),'V218','V218'
FROM `zsjos_service_relation` sr WHERE sr.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_collaboration_group` cg WHERE cg.tenant_id=sr.tenant_id AND cg.source_service_relation_id=sr.id AND cg.deleted=b'0');
UPDATE `zsjos_service_relation` sr JOIN `zsjos_collaboration_group` cg ON cg.tenant_id=sr.tenant_id AND cg.source_service_relation_id=sr.id AND cg.deleted=b'0'
SET sr.collaboration_group_id=cg.id WHERE sr.collaboration_group_id IS NULL AND sr.deleted=b'0';
INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V218','Student collaboration group boundary','student-collaboration-group-v2');
