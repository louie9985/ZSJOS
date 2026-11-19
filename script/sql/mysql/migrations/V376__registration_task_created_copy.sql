-- UTF-8. Apply after V375 with the task-created template and both ledgers present.
-- Updates the one built-in template used for new notifications, preserving rendered
-- history, rule routing and administrator edits outside the old default text.
-- Replay completes any missing ledger entry and does not change corrected text.
-- Rollback is a template edit in notification management. Do not restore the old
-- approval claim while finance review and fulfillment can run concurrently.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v376_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v376_apply()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    IF (SELECT COUNT(*) FROM system_notify_template WHERE code='ZSJOS_REGISTRATION_TASK_CREATED'
        AND scene_code='zsjos.registration.task_created' AND deleted=b'0')<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V376 requires exactly one active registration task-created template';
    END IF;
    UPDATE system_notify_template
    SET summary=CASE WHEN BINARY summary='订单{{order.no}}已通过报名审核，请及时处理'
                     THEN '订单{{order.no}}新增报名履约任务，请及时处理' ELSE summary END,
        content=CASE WHEN BINARY content='学员{{order.no}}的订单{{order.no}}已通过报名审核，请进入报名履约公共池完成清单。'
                     THEN '订单{{order.no}}新增报名履约任务，请进入我的报名履约完成清单。' ELSE content END,
        updater='migration-V376',update_time=NOW()
    WHERE code='ZSJOS_REGISTRATION_TASK_CREATED' AND scene_code='zsjos.registration.task_created' AND deleted=b'0'
      AND (BINARY summary='订单{{order.no}}已通过报名审核，请及时处理'
           OR BINARY content='学员{{order.no}}的订单{{order.no}}已通过报名审核，请进入报名履约公共池完成清单。');
    IF EXISTS(SELECT 1 FROM system_notify_template
        WHERE code='ZSJOS_REGISTRATION_TASK_CREATED' AND scene_code='zsjos.registration.task_created' AND deleted=b'0'
          AND (BINARY summary='订单{{order.no}}已通过报名审核，请及时处理'
               OR BINARY content='学员{{order.no}}的订单{{order.no}}已通过报名审核，请进入报名履约公共池完成清单。')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V376 old default template text remains';
    END IF;
    INSERT INTO zsjos_schema_version(version,description,checksum)
    SELECT 'V376','报名履约任务通知文案',SHA2('V376__registration_task_created_copy.sql',256)
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V376');
    INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
    SELECT 'core','V376','报名履约任务通知文案',SHA2('V376__registration_task_created_copy.sql',256),'baseline'
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V376');
    COMMIT;
END$$
DELIMITER ;
CALL zsjos_v376_apply();
DROP PROCEDURE zsjos_v376_apply;
