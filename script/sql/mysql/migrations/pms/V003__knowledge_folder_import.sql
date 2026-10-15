-- UTF-8. PMS V003 folder import tasks; requires PMS V002 and Core module ledger.
-- Scope: two new empty tables only. No historical rows or permission assignments changed.
-- Apply after V002 and before the feature deployment. Repeat/partial recovery supported.
-- DDL commits implicitly. A failed schema check cannot register success. Preserve tables on rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS pms_v003_import_apply;
DELIMITER $$
CREATE PROCEDURE pms_v003_import_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_module_schema_version') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 requires module ledger';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V002') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 requires V002';
  END IF;
  CREATE TABLE IF NOT EXISTS pms_knowledge_import_task (
    id bigint NOT NULL AUTO_INCREMENT,
    owner_id bigint NOT NULL,
    library_id bigint NOT NULL,
    folder_id bigint DEFAULT NULL,
    root_name varchar(255) NOT NULL,
    request_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mode varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    tenant_id bigint NOT NULL DEFAULT 1,
    creator varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_request (tenant_id,owner_id,request_key)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='owner_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.owner_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='library_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.library_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='folder_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.folder_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='root_name' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.root_name';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='request_key' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.request_key';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='manifest_hash' AND data_type='char') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.manifest_hash';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='mode' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.mode';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='status' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.status';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='tenant_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.tenant_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='creator' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.creator';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='create_time' AND data_type='datetime') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.create_time';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='updater' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.updater';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='update_time' AND data_type='datetime') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.update_time';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='deleted' AND data_type='bit') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Task.deleted';
  END IF;
  IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND index_name='uk_import_request' AND non_unique=0) <> 'tenant_id,owner_id,request_key' OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND index_name='uk_import_request') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid idempotency index';
  END IF;
  CREATE TABLE IF NOT EXISTS pms_knowledge_import_item (
    id bigint NOT NULL AUTO_INCREMENT,
    task_id bigint NOT NULL,
    relative_path varchar(2048) NOT NULL,
    path_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title varchar(255) NOT NULL,
    file_type varchar(255) NOT NULL,
    file_size bigint NOT NULL,
    last_modified bigint NOT NULL,
    digest char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    status varchar(255) NOT NULL,
    reason varchar(500) DEFAULT NULL,
    folder_id bigint DEFAULT NULL,
    document_id bigint DEFAULT NULL,
    file_id bigint DEFAULT NULL,
    file_url varchar(2048) DEFAULT NULL,
    lease_token varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    lease_until datetime DEFAULT NULL,
    version int NOT NULL,
    tenant_id bigint NOT NULL DEFAULT 1,
    creator varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_path (tenant_id,task_id,path_hash)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='task_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.task_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='relative_path' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.relative_path';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='path_hash' AND data_type='char') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.path_hash';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='title' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.title';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='file_type' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.file_type';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='file_size' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.file_size';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='last_modified' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.last_modified';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='digest' AND data_type='char') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.digest';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='status' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.status';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='reason' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.reason';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='folder_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.folder_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='document_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.document_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='file_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.file_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='file_url' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.file_url';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='lease_token' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.lease_token';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='lease_until' AND data_type='datetime') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.lease_until';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='version' AND data_type='int') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.version';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='tenant_id' AND data_type='bigint') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.tenant_id';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='creator' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.creator';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='create_time' AND data_type='datetime') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.create_time';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='updater' AND data_type='varchar') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.updater';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='update_time' AND data_type='datetime') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.update_time';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='deleted' AND data_type='bit') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid Item.deleted';
  END IF;
  IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND index_name='uk_import_path' AND non_unique=0) <> 'tenant_id,task_id,path_hash' OR NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND index_name='uk_import_path') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid idempotency index';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND engine='InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 requires InnoDB';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='root_name' AND character_maximum_length=255) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid root_name capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='request_key' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid request_key capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='manifest_hash' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid manifest_hash capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='mode' AND character_maximum_length=255) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid mode capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='status' AND character_maximum_length=255) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid status capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='creator' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid creator capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_task' AND column_name='updater' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid updater capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND engine='InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 requires InnoDB';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='relative_path' AND character_maximum_length=2048) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid relative_path capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='path_hash' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid path_hash capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='title' AND character_maximum_length=255) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid title capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='file_type' AND character_maximum_length=255) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid file_type capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='digest' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid digest capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='status' AND character_maximum_length=255) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid status capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='reason' AND character_maximum_length=500) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid reason capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='file_url' AND character_maximum_length=2048) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid file_url capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='lease_token' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid lease_token capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='creator' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid creator capacity';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_knowledge_import_item' AND column_name='updater' AND character_maximum_length=64) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='PMS V003 invalid updater capacity';
  END IF;
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
    SELECT 'pms','V003','Knowledge folder import tasks',SHA2('V003__knowledge_folder_import.sql',256),'baseline'
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='pms' AND version='V003');
END$$
DELIMITER ;
CALL pms_v003_import_apply();
DROP PROCEDURE pms_v003_import_apply;
