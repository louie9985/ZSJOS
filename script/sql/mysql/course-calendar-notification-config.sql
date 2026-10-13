-- UTF-8. Explicitly approved tenant-1 course calendar notification configuration.
-- Prerequisites: existing System notification schema and V282 calendar feature.
-- Standalone administrator provisioning, not a numbered schema migration or bootstrap seed.
-- Scope: two global course-scene templates and two tenant-1 rules only.
-- Order: templates, then rules, then checked commit. No role grants, channel changes,
-- employee preferences, business data, history replay or version-ledger changes.
-- Repeatable for this exact configuration; any conflicting/edited configuration fails
-- and rolls back all DML. Run with mysql --default-character-set=utf8mb4, without --force.
-- Save a scoped preflight export before application. Recovery: after review disable only
-- these newly created rules; delivered messages cannot be recalled. Procedure DDL is not
-- transactional, but all configuration DML and postconditions share one transaction.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_configure_course_notify;
DELIMITER $$
CREATE PROCEDURE zsjos_configure_course_notify()
BEGIN
  DECLARE locked INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF locked = 1 THEN DO RELEASE_LOCK('zsjos:course-notify-config:tenant1'); END IF;
    RESIGNAL;
  END;
  SELECT GET_LOCK('zsjos:course-notify-config:tenant1', 10) INTO locked;
  IF locked <> 1 OR locked IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Course notification configuration is busy';
  END IF;
  START TRANSACTION;
  IF (SELECT COUNT(*) FROM system_tenant WHERE id=1 AND deleted=0 AND status=0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An enabled tenant 1 is required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V282') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V282 is required';
  END IF;

  INSERT INTO system_notify_template
    (name,code,nickname,scene_code,channel_code,wecom_message_type,title,summary,
     content,type,params,status,creator,updater)
  SELECT CONCAT('课程日历通知（',c.label,'）'),c.code,'中世健消息中心',
    'zsjos.calendar.course',c.channel,IF(c.channel='wecom','text',NULL),
    '课程通知：{{calendar.title}}','课程时间：{{calendar.time}}',
    CONCAT('课程：{{calendar.title}}',CHAR(10),'时间：{{calendar.time}}',CHAR(10),'备注：{{calendar.remark}}'),
    2,JSON_ARRAY('calendar.title','calendar.time','calendar.remark'),0,
    'course-notify-config','course-notify-config'
  FROM (
    SELECT 'in_app' AS channel,'站内信' AS label,'ZSJOS_CALENDAR_COURSE_IN_APP' AS code
    UNION ALL SELECT 'wecom','企业微信','ZSJOS_CALENDAR_COURSE_WECOM'
  ) c
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template t WHERE t.code=c.code);

  INSERT INTO system_notify_rule
    (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,
     action_type,status,creator,updater,tenant_id)
  SELECT CONCAT('课程日历通知（',IF(t.channel_code='in_app','站内信','企业微信'),'）'),
    'zsjos.calendar.course',t.channel_code,t.id,JSON_ARRAY('recipient'),JSON_ARRAY(),
    'business_detail',0,'course-notify-config','course-notify-config',1
  FROM system_notify_template t
  WHERE t.deleted=0 AND t.scene_code='zsjos.calendar.course'
    AND t.code IN ('ZSJOS_CALENDAR_COURSE_IN_APP','ZSJOS_CALENDAR_COURSE_WECOM')
    AND NOT EXISTS (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=1
      AND r.scene_code='zsjos.calendar.course' AND r.channel_code=t.channel_code);

  -- Upgrade only our explicitly provisioned local defaults; preserve administrator choices.
  UPDATE system_notify_rule r JOIN system_notify_template t ON t.id=r.template_id
  SET r.action_type='business_detail',r.updater='course-notify-config'
  WHERE r.tenant_id=1 AND r.scene_code='zsjos.calendar.course' AND r.deleted=0 AND r.status=0
    AND r.creator='course-notify-config' AND r.updater='course-notify-config' AND r.action_type='none'
    AND t.code IN ('ZSJOS_CALENDAR_COURSE_IN_APP','ZSJOS_CALENDAR_COURSE_WECOM');

  -- Fail closed on duplicates, disabled/deleted defaults, channel mismatch or edited text.
  IF (SELECT COUNT(*) FROM system_notify_template
      WHERE scene_code='zsjos.calendar.course'
         OR code IN ('ZSJOS_CALENDAR_COURSE_IN_APP','ZSJOS_CALENDAR_COURSE_WECOM')) <> 2
    OR (SELECT COUNT(*) FROM system_notify_rule
      WHERE tenant_id=1 AND scene_code='zsjos.calendar.course') <> 2
    OR (SELECT COUNT(DISTINCT r.channel_code)
      FROM system_notify_rule r JOIN system_notify_template t ON t.id=r.template_id
      WHERE r.tenant_id=1 AND r.scene_code='zsjos.calendar.course'
        AND r.deleted=0 AND r.status=0 AND r.channel_code=t.channel_code
        AND t.scene_code=r.scene_code AND t.deleted=0 AND t.status=0 AND t.type=2
        AND ((t.channel_code='in_app' AND t.code='ZSJOS_CALENDAR_COURSE_IN_APP' AND t.wecom_message_type IS NULL)
          OR (t.channel_code='wecom' AND t.code='ZSJOS_CALENDAR_COURSE_WECOM' AND t.wecom_message_type='text'))
        AND t.nickname='中世健消息中心'
        AND t.title='课程通知：{{calendar.title}}' AND t.summary='课程时间：{{calendar.time}}'
        AND t.content=CONCAT('课程：{{calendar.title}}',CHAR(10),'时间：{{calendar.time}}',CHAR(10),'备注：{{calendar.remark}}')
        AND CAST(t.params AS JSON)=JSON_ARRAY('calendar.title','calendar.time','calendar.remark')
        AND CAST(r.recipient_roles AS JSON)=JSON_ARRAY('recipient')
        AND CAST(r.specified_user_ids AS JSON)=JSON_ARRAY()
        AND r.action_type='business_detail' AND r.timing_stage IS NULL AND r.timing_offset_minutes IS NULL) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Course notification config conflicts with expected defaults; existing config preserved';
  END IF;
  COMMIT;
  DO RELEASE_LOCK('zsjos:course-notify-config:tenant1');
END$$
DELIMITER ;
CALL zsjos_configure_course_notify();
DROP PROCEDURE zsjos_configure_course_notify;
