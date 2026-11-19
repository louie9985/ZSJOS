-- UTF-8. Run after V378, before the matching backend. Only an empty note table,
-- two system buttons and both Core ledger entries are created. Replay retains
-- notes and administrator settings. DDL commits independently; fix conflicts
-- then rerun. To retire, disable the buttons and retain recorded notes.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v379_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v379_apply()
BEGIN
 DECLARE parent BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V378') OR
    NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V378') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V379 requires V378';
 END IF;
 CREATE TABLE IF NOT EXISTS zsjos_contact_note (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 subject_type VARCHAR(16) NOT NULL,
 subject_id BIGINT NOT NULL,
 author_id BIGINT NOT NULL,
 author_name VARCHAR(128) NOT NULL,
 content VARCHAR(2000) NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 creator VARCHAR(64) DEFAULT '', updater VARCHAR(64) DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_note_request (tenant_id,subject_type,subject_id,author_id,request_key),
 KEY idx_note_subject (tenant_id,subject_type,subject_id,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_contact_note'
 AND column_name IN ('id','tenant_id','subject_type','subject_id','author_id','author_name','content','request_key','creator','updater','create_time','update_time','deleted'))<>13 OR
 NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_contact_note' AND index_name='uk_note_request' AND non_unique=0 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='tenant_id,subject_type,subject_id,author_id,request_key') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V379 note schema mismatch';
 END IF;
 START TRANSACTION;
 SET parent=(SELECT id FROM system_menu WHERE type=2 AND permission='zsjos:lead:query' AND deleted=0);
 IF parent IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V379 missing lead:query'; END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '追加客资跟进记录','zsjos:lead-follow-up:append',3,20,parent,'','','',0,1,1,1,'V379',NOW(),'V379',NOW(),0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:lead-follow-up:append');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:lead-follow-up:append')<>1
 OR NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:lead-follow-up:append' AND type=3 AND parent_id=parent AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V379 lead-follow-up:append conflict';
 END IF;
 SET parent=(SELECT id FROM system_menu WHERE type=2 AND permission='zsjos:student:query-my' AND deleted=0);
 IF parent IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V379 missing student:query-my'; END IF;
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
 SELECT '追加学员跟进记录','zsjos:student-contact:append',3,20,parent,'','','',0,1,1,1,'V379',NOW(),'V379',NOW(),0
 WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-contact:append');
 IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-contact:append')<>1
 OR NOT EXISTS(SELECT 1 FROM system_menu WHERE permission='zsjos:student-contact:append' AND type=3 AND parent_id=parent AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V379 student-contact:append conflict';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V379','追加沟通记录',SHA2('V379__contact_notes.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V379');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V379','追加沟通记录',SHA2('V379__contact_notes.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V379');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v379_apply();
DROP PROCEDURE zsjos_v379_apply;
