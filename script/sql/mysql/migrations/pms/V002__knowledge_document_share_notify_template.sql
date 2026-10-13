-- UTF-8. PMS V002: missing knowledge document share in-app template.
-- Requires PMS V001 and the current Core System notification template schema.
-- Scope: one global System template code (System templates have no tenant_id),
-- consumed by PMS for ADMIN recipients in the sole business tenant 1.
-- No rules, grants, members, business rows or historical messages are changed.
-- Apply after Core migrations and PMS V001, before testing a new member share.
-- Repeat/recovery: insert only when absent; preserve existing valid customization,
-- including disabled status; upgrade only the exact original development default. Conflicting/deleted templates fail for manual review.
-- Template + PMS module ledger writes are transactional; no Core ledger write.
-- Retain a scoped preflight backup. Rollback by disabling this template through
-- System administration; retain message history and the module ledger.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS pms_v002_share_notify_apply;
DELIMITER $$
CREATE PROCEDURE pms_v002_share_notify_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema=DATABASE() AND engine='InnoDB'
      AND table_name IN ('system_notify_template','zsjos_module_schema_version')) <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V002 requires transactional System template and module ledger tables';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V001') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V002 requires PMS V001';
  END IF;

  START TRANSACTION;
  IF (SELECT COUNT(*) FROM system_notify_template WHERE code='pms_knowledge_document_shared') > 1
    OR EXISTS (SELECT 1 FROM system_notify_template WHERE code='pms_knowledge_document_shared'
      AND (deleted<>b'0' OR channel_code<>'in_app' OR type<>2 OR status NOT IN (0,1)
           OR scene_code IS NOT NULL OR params IS NULL OR NOT JSON_VALID(params))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V002 existing template conflicts with direct in-app sharing contract';
  END IF;
  IF EXISTS (SELECT 1 FROM system_notify_template WHERE code='pms_knowledge_document_shared'
      AND (JSON_TYPE(params)<>'ARRAY'
        OR NOT JSON_CONTAINS(JSON_ARRAY('documentTitle','route','operatorName','publicUrl','documentRoute'), CAST(params AS JSON)))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V002 existing template requires unsupported parameters';
  END IF;

  INSERT INTO system_notify_template
    (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater)
  SELECT '知识库文档分享','pms_knowledge_document_shared','中世健消息中心',NULL,'in_app',
    '收到知识库文档分享','{{operatorName}}向您分享了《{{documentTitle}}》。',
    '{{operatorName}}向您分享了知识库文档《{{documentTitle}}》。',
    2,'["operatorName","documentTitle"]',0,'PMS 知识库内部成员分享站内信；仅通知新增成员。','migration-pms-V002','migration-pms-V002'
  WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='pms_knowledge_document_shared');

  -- Upgrade only our original local-development default, never administrator wording.
  UPDATE system_notify_template
  SET summary='{{operatorName}}向您分享了《{{documentTitle}}》。',
      content='{{operatorName}}向您分享了知识库文档《{{documentTitle}}》。',
      params='["operatorName","documentTitle"]', updater='migration-pms-V002'
  WHERE code='pms_knowledge_document_shared' AND deleted=b'0'
    AND creator='migration-pms-V002' AND updater='migration-pms-V002'
    AND title='收到知识库文档分享'
    AND summary='有人向您分享了《{{documentTitle}}》。'
    AND content='有人向您分享了知识库文档《{{documentTitle}}》。查看地址：{{route}}'
    AND JSON_LENGTH(params)=2
    AND JSON_CONTAINS(params, JSON_ARRAY('documentTitle','route'));

  IF (SELECT COUNT(*) FROM system_notify_template
      WHERE code='pms_knowledge_document_shared' AND deleted=b'0' AND channel_code='in_app' AND type=2) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V002 template postcondition failed';
  END IF;
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'pms','V002','Knowledge document share notification template',
    SHA2('V002__knowledge_document_share_notify_template.sql',256),'baseline'
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V002');
  COMMIT;
END$$
DELIMITER ;
CALL pms_v002_share_notify_apply();
DROP PROCEDURE pms_v002_share_notify_apply;
