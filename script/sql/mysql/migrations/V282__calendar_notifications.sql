-- UTF-8. Migration-Owner: ai
-- Scope: active-development V282 correction; run after Core V281 in both ledgers.
-- Adds version columns and notification batch/recipient/snapshot/state/preview/intent tables; no business deletes or grants.
-- Replay: inspect actual schema even when V282 was recorded by an earlier partial run.
-- Existing rows, calendar versions and ledger checksums are preserved.
-- Deployed copies/checksum differences require a separately reviewed rollout.
-- Rollback: retain additive schema and records; DDL is not transactionally reversible.
SET NAMES utf8mb4;
-- Active-development completion: four missing notification buttons under V187/V191 pages.
-- Replay preserves administrator names/status and existing ledger checksums; no role/package grants.
-- Deployed checksum-enforced environments need a reviewed rollout, not ledger rewriting.
-- Rollback retains metadata; disable through System management if necessary.

DROP PROCEDURE IF EXISTS zsjos_v282_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v282_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V281')
      OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V281') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 requires Core V281 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_course_calendar_event','zsjos_exam_schedule') AND table_type='BASE TABLE') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 requires course and exam calendar tables';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_course_calendar_event' AND column_name='calendar_version') THEN
    ALTER TABLE zsjos_course_calendar_event ADD COLUMN calendar_version int NOT NULL DEFAULT 1 COMMENT '通知内容版本';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='calendar_version') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN calendar_version int NOT NULL DEFAULT 1 COMMENT '通知内容版本';
  END IF;

CREATE TABLE IF NOT EXISTS `zsjos_calendar_notify_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `calendar_type` varchar(16) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `calendar_version` int NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `scope` varchar(16) NOT NULL,
  `title_snapshot` text NOT NULL,
  `time_snapshot` varchar(256) DEFAULT NULL,
  `remark_snapshot` varchar(2000) DEFAULT NULL,
  `source_event_key` varchar(128) NOT NULL,
  `resend` bit(1) NOT NULL DEFAULT b'0',
  `status` varchar(16) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_calendar_notify_event` (`tenant_id`,`source_event_key`),
  KEY `idx_calendar_notify_business` (`tenant_id`,`calendar_type`,`calendar_id`,`calendar_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS日历通知批次';

CREATE TABLE IF NOT EXISTS `zsjos_calendar_notify_recipient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0, `batch_id` bigint NOT NULL,
  `user_id` bigint NOT NULL, `user_type` int NOT NULL DEFAULT 2,
  `nickname_snapshot` varchar(128) DEFAULT NULL, `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `skip_reason` varchar(128) DEFAULT NULL, `message_id` bigint DEFAULT NULL, `completed_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_calendar_notify_recipient` (`tenant_id`,`batch_id`,`user_id`,`user_type`),
  KEY `idx_calendar_notify_recipient_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS日历通知接收人';

  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_course_calendar_event','zsjos_exam_schedule')
      AND column_name='calendar_version' AND column_type='int' AND is_nullable='NO' AND column_default='1') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 calendar version columns do not match the contract';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_batch' AND column_name IN
      ('id','tenant_id','calendar_type','calendar_id','calendar_version','event_type','scope','title_snapshot',
       'time_snapshot','remark_snapshot','source_event_key','resend','status','creator','create_time','updater','update_time','deleted')) <> 18
      OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_recipient' AND column_name IN
      ('id','tenant_id','batch_id','user_id','user_type','nickname_snapshot','status','skip_reason','message_id',
       'completed_time','creator','create_time','updater','update_time','deleted')) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 notification columns are incomplete';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_batch' AND index_name='uk_calendar_notify_event'
      AND non_unique=0 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='source_event_key'))) <> 2
      OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_recipient' AND index_name='uk_calendar_notify_recipient'
      AND non_unique=0 AND ((seq_in_index=1 AND column_name='tenant_id') OR (seq_in_index=2 AND column_name='batch_id')
        OR (seq_in_index=3 AND column_name='user_id') OR (seq_in_index=4 AND column_name='user_type'))) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 notification idempotency indexes do not match the contract';
  END IF;

CREATE TABLE IF NOT EXISTS `zsjos_calendar_notify_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `calendar_type` varchar(16) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `calendar_version` int NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `record_status` varchar(16) NOT NULL,
  `title_snapshot` text NOT NULL,
  `time_snapshot` varchar(256) DEFAULT NULL,
  `remark_snapshot` varchar(2000) DEFAULT NULL,
  `details_json` longtext NOT NULL,
  `content_hash` char(64) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calendar_snapshot_version` (`tenant_id`,`calendar_type`,`calendar_id`,`calendar_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日历不可变通知快照';

CREATE TABLE IF NOT EXISTS `zsjos_calendar_notify_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `calendar_type` varchar(16) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `calendar_version` int NOT NULL,
  `current_snapshot_id` bigint DEFAULT NULL,
  `record_status` varchar(16) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calendar_notify_state` (`tenant_id`,`calendar_type`,`calendar_id`),
  KEY `idx_calendar_state_snapshot` (`tenant_id`,`current_snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日历通知当前状态';

  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_snapshot' AND column_name IN
      ('id','tenant_id','calendar_type','calendar_id','calendar_version','event_type','record_status',
       'title_snapshot','time_snapshot','remark_snapshot','details_json','content_hash','creator','create_time','updater','update_time','deleted')) <> 17
      OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_state' AND column_name IN
      ('id','tenant_id','calendar_type','calendar_id','calendar_version','current_snapshot_id','record_status','creator','create_time','updater','update_time','deleted')) <> 12 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 calendar snapshot/state columns are incomplete';
  END IF;
  IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_snapshot'
      AND index_name='uk_calendar_snapshot_version' AND non_unique=0) IS NULL
      OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_snapshot'
      AND index_name='uk_calendar_snapshot_version' AND non_unique=0) <> 'tenant_id,calendar_type,calendar_id,calendar_version'
      OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_state'
      AND index_name='uk_calendar_notify_state' AND non_unique=0) IS NULL
      OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_state'
      AND index_name='uk_calendar_notify_state' AND non_unique=0) <> 'tenant_id,calendar_type,calendar_id' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 calendar snapshot/state unique keys are invalid';
  END IF;

CREATE TABLE IF NOT EXISTS `zsjos_calendar_notify_preview` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `token_hash` char(64) NOT NULL,
  `operator_user_id` bigint NOT NULL,
  `request_hash` char(64) NOT NULL,
  `content_hash` char(64) NOT NULL,
  `roster_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calendar_preview_token` (`tenant_id`,`token_hash`),
  KEY `idx_calendar_preview_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日历通知预览凭证';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='snapshot_id') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `snapshot_id` bigint DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='operator_user_id') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `operator_user_id` bigint DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='idempotency_key') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `idempotency_key` varchar(128) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='request_hash') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `request_hash` char(64) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='requested_count') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `requested_count` int NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='accepted_count') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `accepted_count` int NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='skipped_count') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD COLUMN `skipped_count` int NOT NULL DEFAULT 0;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name IN ('snapshot_id','operator_user_id','idempotency_key','request_hash','requested_count','accepted_count','skipped_count')) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 acceptance columns missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND column_name='accepted') THEN
    ALTER TABLE `zsjos_calendar_notify_recipient` ADD COLUMN `accepted` bit(1) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND column_name='dedup_key') THEN
    ALTER TABLE `zsjos_calendar_notify_recipient` ADD COLUMN `dedup_key` char(64) DEFAULT NULL;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND column_name IN ('accepted','dedup_key')) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 acceptance columns missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND index_name='uk_calendar_request') THEN
    ALTER TABLE `zsjos_calendar_notify_batch` ADD UNIQUE KEY `uk_calendar_request` (tenant_id,idempotency_key);
  END IF;
  IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND index_name='uk_calendar_request' AND non_unique=0) IS NULL OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND index_name='uk_calendar_request' AND non_unique=0) <> 'tenant_id,idempotency_key' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 acceptance unique key invalid';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND index_name='uk_calendar_recipient_dedup') THEN
    ALTER TABLE `zsjos_calendar_notify_recipient` ADD UNIQUE KEY `uk_calendar_recipient_dedup` (tenant_id,dedup_key);
  END IF;
  IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND index_name='uk_calendar_recipient_dedup' AND non_unique=0) IS NULL OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND index_name='uk_calendar_recipient_dedup' AND non_unique=0) <> 'tenant_id,dedup_key' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 acceptance unique key invalid';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_preview' AND column_name IN ('id','tenant_id','token_hash','operator_user_id','request_hash','content_hash','roster_hash','expires_at','creator','create_time','updater','update_time','deleted')) <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 preview columns incomplete';
  END IF;


  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='title_snapshot' AND data_type='varchar') THEN
    ALTER TABLE zsjos_calendar_notify_batch MODIFY COLUMN title_snapshot text NOT NULL;
  END IF;
  ALTER TABLE zsjos_calendar_notify_recipient ALTER COLUMN user_type SET DEFAULT 2;
  IF (SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_batch' AND column_name='title_snapshot') <> 'text'
      OR (SELECT column_default FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_recipient' AND column_name='user_type') <> '2' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 title or employee default invalid';
  END IF;
  IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_preview' AND index_name='uk_calendar_preview_token' AND non_unique=0) IS NULL
      OR (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_preview' AND index_name='uk_calendar_preview_token' AND non_unique=0) <> 'tenant_id,token_hash' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 preview unique key invalid';
  END IF;

CREATE TABLE IF NOT EXISTS `zsjos_calendar_notify_intent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `operation_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `acceptance_key` varchar(128) NOT NULL,
  `operator_user_id` bigint NOT NULL,
  `calendar_type` varchar(16) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `snapshot_id` bigint NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `scope` varchar(16) NOT NULL,
  `recipients_json` longtext NOT NULL,
  `resend` bit(1) NOT NULL DEFAULT b'0',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `batch_id` bigint DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime NOT NULL,
  `last_error_code` varchar(64) DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calendar_intent_operation` (`tenant_id`,`operation_key`),
  UNIQUE KEY `uk_calendar_intent_acceptance` (`tenant_id`,`acceptance_key`),
  KEY `idx_calendar_intent_due` (`tenant_id`,`status`,`next_attempt_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日历维护通知意图';

  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_calendar_notify_intent' AND column_name IN
      ('id','tenant_id','operation_key','request_hash','acceptance_key','operator_user_id','calendar_type','calendar_id',
       'snapshot_id','event_type','scope','recipients_json','resend','status','batch_id','attempt_count','next_attempt_at',
       'last_error_code','completed_time','creator','create_time','updater','update_time','deleted')) <> 24 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 notification intent columns incomplete';
  END IF;
  IF COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_intent'
      AND index_name='uk_calendar_intent_operation' AND non_unique=0),'') <> 'tenant_id,operation_key'
      OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_intent'
      AND index_name='uk_calendar_intent_acceptance' AND non_unique=0),'') <> 'tenant_id,acceptance_key'
      OR COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_notify_intent'
      AND index_name='idx_calendar_intent_due'),'') <> 'tenant_id,status,next_attempt_at,id' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 notification intent indexes invalid';
  END IF;

-- All DDL commits implicitly; menu metadata and both ledger writes share one transaction.
START TRANSACTION;
-- BEGIN CALENDAR NOTIFICATION PERMISSIONS
-- Existing V187/V191 page identities are prerequisites; never infer a parent from its display name.
IF (SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND type=2
    AND ((id=73610 AND permission='zsjos:exam-calendar:query')
      OR (id=73630 AND permission='zsjos:course-calendar:query'))) <> 2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 requires existing exam and course calendar page metadata';
END IF;
IF EXISTS (SELECT 1 FROM system_menu WHERE
    (id=73613 AND (permission<>'zsjos:exam-calendar:notify' OR parent_id<>73610 OR type<>3 OR deleted<>b'0'))
 OR (id=73614 AND (permission<>'zsjos:exam-calendar:notify-all' OR parent_id<>73610 OR type<>3 OR deleted<>b'0'))
 OR (id=73633 AND (permission<>'zsjos:course-calendar:notify' OR parent_id<>73630 OR type<>3 OR deleted<>b'0'))
 OR (id=73634 AND (permission<>'zsjos:course-calendar:notify-all' OR parent_id<>73630 OR type<>3 OR deleted<>b'0'))
 OR (permission='zsjos:exam-calendar:notify' AND id<>73613)
 OR (permission='zsjos:exam-calendar:notify-all' AND id<>73614)
 OR (permission='zsjos:course-calendar:notify' AND id<>73633)
 OR (permission='zsjos:course-calendar:notify-all' AND id<>73634)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 calendar notification permission identity conflict';
END IF;

INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
  workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT proposed.id,proposed.name,proposed.permission,3,proposed.sort,proposed.parent_id,'','','',NULL,
       'native',0,b'1',b'1',b'0','V282','V282',b'0'
FROM (
 SELECT 73613 AS id,'发送通知' AS name,'zsjos:exam-calendar:notify' AS permission,3 AS sort,73610 AS parent_id
 UNION ALL SELECT 73614,'全员通知','zsjos:exam-calendar:notify-all',4,73610
 UNION ALL SELECT 73633,'发送通知','zsjos:course-calendar:notify',3,73630
 UNION ALL SELECT 73634,'全员通知','zsjos:course-calendar:notify-all',4,73630
) AS proposed
WHERE NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.id=proposed.id);

-- Do not reset administrator labels, disabled state, ordering or any role/package assignment on replay.
IF (SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND type=3 AND
    ((id=73613 AND parent_id=73610 AND permission='zsjos:exam-calendar:notify')
  OR (id=73614 AND parent_id=73610 AND permission='zsjos:exam-calendar:notify-all')
  OR (id=73633 AND parent_id=73630 AND permission='zsjos:course-calendar:notify')
  OR (id=73634 AND parent_id=73630 AND permission='zsjos:course-calendar:notify-all'))) <> 4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 calendar notification permission postcondition failed';
END IF;
-- END CALENDAR NOTIFICATION PERMISSIONS
INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V282','Calendar notifications',SHA2('V282__calendar_notifications.sql',256))
ON DUPLICATE KEY UPDATE version=VALUES(version);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES ('core','V282','Calendar notifications',SHA2('V282__calendar_notifications.sql',256),'baseline')
ON DUPLICATE KEY UPDATE version=VALUES(version);
COMMIT;
END$$
DELIMITER ;
CALL zsjos_v282_apply();
DROP PROCEDURE zsjos_v282_apply;
