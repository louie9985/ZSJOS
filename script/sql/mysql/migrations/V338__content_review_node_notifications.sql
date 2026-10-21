-- UTF-8. Content approval node-result default templates, not administrator configuration.
-- Order: after V337; prerequisites: V269 in both ledgers and existing InnoDB notification templates.
-- Scope: at most eight unmodified V269 templates (four scenes, in_app/wecom); no rules,
-- recipients, role grants, historical messages or business records are changed or replayed.
-- Repeatability: compare every default presentation field before updating; retain custom templates
-- and existing ledger checksums. Recovery: replay after failure; all DML/ledgers roll back together.
-- Rollback: retain source-compatible extra variables, or restore backed-up default template fields.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v338_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v338_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  DROP TEMPORARY TABLE IF EXISTS zsjos_v338_targets;
  DROP TEMPORARY TABLE IF EXISTS zsjos_v338_defaults;
  RESIGNAL;
 END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V269')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V269') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V338 requires V269 in both ledgers';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('system_notify_template','zsjos_schema_version','zsjos_module_schema_version')
 AND engine='InnoDB') <> 3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V338 requires InnoDB templates and ledgers';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS zsjos_v338_defaults;
 DROP TEMPORARY TABLE IF EXISTS zsjos_v338_targets;
 CREATE TEMPORARY TABLE zsjos_v338_defaults (
  suffix VARCHAR(64) PRIMARY KEY, scene_code VARCHAR(128), old_title VARCHAR(128),
  new_title VARCHAR(128), new_summary VARCHAR(512)
 ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 INSERT INTO zsjos_v338_defaults VALUES
 ('DIRECTOR_COMPLETED','zsjos.content_review.director_completed','编导审核通过','内容审批 · 编导审核通过',
  '批次 {{batchNo}}，共 {{itemCount}} 条作品，编导已全部通过，待终审。'),
 ('DIRECTOR_REJECTED','zsjos.content_review.director_rejected','编导审核驳回','内容审批 · 编导审核未通过',
  '批次 {{batchNo}}，共 {{itemCount}} 条作品，其中 {{returnedCount}} 条未通过，已退回修改。'),
 ('FINAL_COMPLETED','zsjos.content_review.final_completed','终审通过','内容审批 · 终审通过',
  '批次 {{batchNo}}，共 {{itemCount}} 条作品，已全部通过，待发布。'),
 ('FINAL_REJECTED','zsjos.content_review.final_rejected','终审驳回','内容审批 · 终审未通过',
  '批次 {{batchNo}}，共 {{itemCount}} 条作品，其中 {{returnedCount}} 条未通过，已退回修改。');
 CREATE TEMPORARY TABLE zsjos_v338_targets (id BIGINT PRIMARY KEY, suffix VARCHAR(64))
 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 START TRANSACTION;
 INSERT INTO zsjos_v338_targets
 SELECT t.id,d.suffix FROM system_notify_template t JOIN zsjos_v338_defaults d
 ON BINARY t.scene_code=BINARY d.scene_code
 AND ((t.channel_code='in_app' AND BINARY t.code=BINARY CONCAT('ZSJ_N269_ZSJOS_CONTENT_REVIEW_',d.suffix))
 OR (t.channel_code='wecom' AND BINARY t.code=BINARY CONCAT('ZSJ_N269_ZSJOS_CONTENT_REVIEW_',d.suffix,'_WECOM')))
 WHERE t.deleted=b'0' AND t.type=2 AND t.remark LIKE 'V269 notification defaults%'
 AND BINARY t.name=BINARY IF(t.channel_code='wecom',CONCAT(d.old_title,'（企微）'),d.old_title)
 AND BINARY t.nickname=BINARY '中仕健消息中心'
 AND BINARY t.title=BINARY d.old_title AND BINARY t.summary=BINARY d.old_title
 AND BINARY t.content=BINARY CONCAT('内容审核批次{{batchNo}}：',d.old_title,'。')
 AND CAST(t.params AS JSON)=JSON_ARRAY('batchNo');
 UPDATE system_notify_template t JOIN zsjos_v338_targets x ON x.id=t.id
 JOIN zsjos_v338_defaults d ON d.suffix=x.suffix
 SET t.name=CONCAT(REPLACE(d.new_title,'内容审批 · ',''),IF(t.channel_code='wecom','（企微）','')),
 t.nickname='中世健消息中心',
 t.title=d.new_title,t.summary=d.new_summary,t.content=CONCAT(d.new_summary,' 请打开本轮内容审核查看逐条结论及意见。'),
 t.params=JSON_ARRAY('batchNo','itemCount','returnedCount'),
 t.updater='migration-V338',t.update_time=NOW();
 IF EXISTS(SELECT 1 FROM zsjos_v338_targets x JOIN system_notify_template t ON t.id=x.id
 JOIN zsjos_v338_defaults d ON d.suffix=x.suffix
 WHERE BINARY t.title<>BINARY d.new_title OR BINARY t.summary<>BINARY d.new_summary
 OR BINARY t.nickname<>BINARY '中世健消息中心'
 OR BINARY t.content<>BINARY CONCAT(d.new_summary,' 请打开本轮内容审核查看逐条结论及意见。')
 OR NOT (CAST(t.params AS JSON)=JSON_ARRAY('batchNo','itemCount','returnedCount'))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V338 notification postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V338','内容审批节点结果通知',SHA2('V338__content_review_node_notifications.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V338');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V338','内容审批节点结果通知',SHA2('V338__content_review_node_notifications.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V338');
 COMMIT;
 DROP TEMPORARY TABLE zsjos_v338_targets;
 DROP TEMPORARY TABLE zsjos_v338_defaults;
END$$
DELIMITER ;
CALL zsjos_v338_apply();
DROP PROCEDURE zsjos_v338_apply;
