-- UTF-8. V339: apply after V338 in both ledgers, before application rollout.
-- Additive teaching state/history, two system class kinds and permission metadata only.
-- No historical services are closed/moved; no role assignments or media/order data change.
-- Existing system pending class is classified explicitly; exception classes are created by
-- the application using System public qualification APIs, never guessed in SQL.
-- Replay preserves business data and installed ledger records. DDL commits implicitly;
-- inspect actual structure and both ledgers after failure, then replay. Application rollback
-- retains all columns/history; removing populated structures requires a separate recovery plan.
-- Cross-version replay preserves V343's PENDING-only guard, including partial V343 DDL.
-- Source compatibility correction only: do not rewrite installed checksums; deployed
-- environments require a reviewed rollout before using the changed migration source.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v339_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v339_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V338')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V338') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 requires V338 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_service_relation','zsjos_delivery_class','system_menu') AND engine='InnoDB')<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 requires InnoDB service, class and menu tables';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 requires one student page menu';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='teaching_closed') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN teaching_closed bit(1) NOT NULL DEFAULT b'0' COMMENT '教学服务是否已关闭';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='teaching_previous_class_id') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN teaching_previous_class_id bigint DEFAULT NULL COMMENT '关闭前班级';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='teaching_closed_at') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN teaching_closed_at datetime DEFAULT NULL COMMENT '最近教学关闭时间';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='teaching_restored') THEN
  ALTER TABLE zsjos_service_relation ADD COLUMN teaching_restored bit(1) NOT NULL DEFAULT b'0' COMMENT '曾恢复教学服务';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='class_kind') THEN
  ALTER TABLE zsjos_delivery_class ADD COLUMN class_kind varchar(24) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/PENDING/EXCEPTION';
 END IF;
 -- V343 allows one UNSCHEDULED system class per planner. Restoring the old
 -- tenant-wide system-class guard would collide with those valid existing rows.
 -- Check actual DDL as well as either ledger to cover interrupted V343 application.
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_delivery_class' AND column_name='unscheduled_guard')
 OR EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V343')
 OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V343') THEN
  ALTER TABLE zsjos_delivery_class MODIFY COLUMN pending_guard bigint GENERATED ALWAYS AS
  (CASE WHEN (class_kind='PENDING' AND deleted=b'0') THEN tenant_id ELSE NULL END) STORED;
 ELSE
  ALTER TABLE zsjos_delivery_class MODIFY COLUMN pending_guard bigint GENERATED ALWAYS AS
  (CASE WHEN (system_class=b'1' AND class_kind<>'EXCEPTION' AND deleted=b'0') THEN tenant_id ELSE NULL END) STORED;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='exception_guard') THEN
  ALTER TABLE zsjos_delivery_class ADD COLUMN exception_guard bigint GENERATED ALWAYS AS
  (CASE WHEN (class_kind='EXCEPTION' AND deleted=b'0') THEN homeroom_user_id ELSE NULL END) STORED;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_exception_guard') THEN
  ALTER TABLE zsjos_delivery_class ADD UNIQUE KEY uk_delivery_exception_guard(tenant_id,exception_guard);
 END IF;
CREATE TABLE IF NOT EXISTS zsjos_teaching_service_event (
 id bigint NOT NULL AUTO_INCREMENT, service_relation_id bigint NOT NULL,
 action varchar(16) NOT NULL, reason varchar(500) NOT NULL,
 operator_user_id bigint NOT NULL, operator_name varchar(100) DEFAULT NULL,
 from_class_id bigint DEFAULT NULL, target_class_id bigint NOT NULL, target_owner_user_id bigint NOT NULL,
 target_class_name varchar(100) DEFAULT NULL, target_owner_name varchar(100) DEFAULT NULL,
 idempotency_key varchar(100) NOT NULL, fingerprint varchar(64) NOT NULL, occurred_at datetime NOT NULL,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_teaching_event_key(tenant_id,idempotency_key,deleted),
 KEY idx_teaching_event_service(tenant_id,service_relation_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学服务关闭恢复历史';
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation'
 AND ((column_name IN ('teaching_closed','teaching_restored') AND column_type='bit(1)' AND is_nullable='NO' AND column_default="b'0'")
 OR (column_name='teaching_previous_class_id' AND data_type='bigint' AND is_nullable='YES')
 OR (column_name='teaching_closed_at' AND data_type='datetime' AND is_nullable='YES')))<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 teaching state postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class'
 AND column_name='class_kind' AND column_type='varchar(24)' AND is_nullable='NO' AND column_default='NORMAL')
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class'
 AND column_name IN ('pending_guard','exception_guard') AND extra LIKE '%STORED GENERATED%')<>2
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class'
 AND index_name='uk_delivery_exception_guard' AND non_unique=0 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='exception_guard')))<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 system class structure postcondition failed';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_teaching_service_event' AND engine='InnoDB')
 OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_teaching_service_event'
 AND column_name IN ('service_relation_id','action','reason','operator_user_id','target_class_id','target_owner_user_id','idempotency_key','fingerprint','occurred_at','tenant_id') AND is_nullable='NO')<>10
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_teaching_service_event'
 AND index_name='uk_teaching_event_key' AND non_unique=0 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='idempotency_key') OR (seq_in_index=3 AND column_name='deleted')))<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 history structure postcondition failed';
 END IF;
 START TRANSACTION;
 UPDATE zsjos_delivery_class SET class_kind='PENDING',update_time=update_time
 WHERE tenant_id=1 AND system_class=1 AND class_kind='NORMAL';
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater)
 SELECT '关闭服务','zsjos:student-service:close',3,90,id,'','','',0,b'1',b'1',b'1','migration-V339','migration-V339'
 FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0
 AND NOT EXISTS(SELECT 1 FROM (SELECT id FROM system_menu WHERE permission='zsjos:student-service:close' AND deleted=0) existing);
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater)
 SELECT '恢复服务','zsjos:student-service:restore',3,91,id,'','','',0,b'1',b'1',b'1','migration-V339','migration-V339'
 FROM system_menu WHERE permission='zsjos:student:query-my' AND type=2 AND deleted=0
 AND NOT EXISTS(SELECT 1 FROM (SELECT id FROM system_menu WHERE permission='zsjos:student-service:restore' AND deleted=0) existing);
 IF (SELECT COUNT(DISTINCT permission) FROM system_menu WHERE permission IN ('zsjos:student-service:close','zsjos:student-service:restore') AND type=3 AND deleted=0)<>2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V339 permission postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V339','教学服务关闭恢复',SHA2('V339__teaching_service_close_restore.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V339');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V339','教学服务关闭恢复',SHA2('V339__teaching_service_close_restore.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V339');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v339_apply();
DROP PROCEDURE zsjos_v339_apply;
