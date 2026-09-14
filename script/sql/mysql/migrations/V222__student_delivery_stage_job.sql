SET NAMES utf8mb4;
INSERT INTO `infra_job`
(`name`,`status`,`handler_name`,`handler_param`,`cron_expression`,`retry_count`,`retry_interval`,`monitor_timeout`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '学员S0-S6交付阶段任务','1','studentDeliveryStageJob',NULL,'0 0/5 * * * ?',1,60,0,'migration-V222',NOW(),'migration-V222',NOW(),b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `infra_job` WHERE `handler_name`='studentDeliveryStageJob' AND `deleted`=b'0');
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V222','Register student delivery stage job','student-delivery-stage-job-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
