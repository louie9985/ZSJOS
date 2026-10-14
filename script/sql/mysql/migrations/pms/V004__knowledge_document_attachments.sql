-- UTF-8. PMS V004 requires V003. Creates one empty attachment table; no grants or historical backfill.
-- Apply V001 through V003 first. Repeat/partial application is additive; incompatible tables fail.
-- DDL commits implicitly. Rollback application code while retaining table/data and ledger.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS pms_v004_attachment_apply;
DELIMITER $$
CREATE PROCEDURE pms_v004_attachment_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V003') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 requires V003';
 END IF;
CREATE TABLE IF NOT EXISTS pms_knowledge_attachment (
 id bigint NOT NULL AUTO_INCREMENT,
 document_id bigint NOT NULL,
 file_id bigint NOT NULL,
 file_name varchar(512) NOT NULL,
 mime_type varchar(255) DEFAULT NULL,
 file_size bigint NOT NULL,
 sort int NOT NULL DEFAULT 0,
 shared bit(1) NOT NULL DEFAULT b'0',
 tenant_id bigint NOT NULL DEFAULT 1,
 creator varchar(64) DEFAULT '',
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '',
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY (id),
 KEY idx_attachment_document (tenant_id, document_id, deleted, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档附件';
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='id' AND data_type='bigint') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid id'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='document_id' AND data_type='bigint') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid document_id'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='file_id' AND data_type='bigint') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid file_id'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='file_name' AND data_type='varchar') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid file_name'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='mime_type' AND data_type='varchar') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid mime_type'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='file_size' AND data_type='bigint') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid file_size'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='sort' AND data_type='int') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid sort'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='shared' AND data_type='bit') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid shared'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='tenant_id' AND data_type='bigint') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid tenant_id'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='creator' AND data_type='varchar') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid creator'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='create_time' AND data_type='datetime') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid create_time'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='updater' AND data_type='varchar') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid updater'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='update_time' AND data_type='datetime') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid update_time'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND column_name='deleted' AND data_type='bit') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid deleted'; END IF;
 IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND index_name='idx_attachment_document') <> 'tenant_id,document_id,deleted,sort' OR NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pms_knowledge_attachment' AND index_name='idx_attachment_document') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V004 invalid index'; END IF;
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'pms','V004','Knowledge document attachments',SHA2('V004__knowledge_document_attachments.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V004');
END$$
DELIMITER ;
CALL pms_v004_attachment_apply();
DROP PROCEDURE pms_v004_attachment_apply;
