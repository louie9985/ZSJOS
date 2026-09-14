SET NAMES utf8mb4;
-- V211 intentionally removes all legacy media-account operation records.
-- Scope: rows linked to existing zsjos_media_account records only. Student/person,
-- service relations, partner accounts and partner metrics are never deleted.
-- This migration is irreversible; export the listed tables before execution.
-- Child rows are removed first so foreign-key deployments remain safe.
DROP PROCEDURE IF EXISTS zsjos_V211_reset_media_account_operation_data;
DELIMITER $$
CREATE PROCEDURE zsjos_V211_reset_media_account_operation_data()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V211') THEN
    START TRANSACTION;
    CREATE TEMPORARY TABLE tmp_v211_account_ids (id BIGINT PRIMARY KEY);
    INSERT INTO tmp_v211_account_ids SELECT id FROM zsjos_media_account WHERE deleted=b'0';

    DELETE e FROM zsjos_media_account_profile_entry AS e JOIN tmp_v211_account_ids AS a ON a.id=e.account_id;
    DELETE x FROM zsjos_production_ticket_command AS x JOIN tmp_v211_account_ids AS a ON a.id=x.account_id;
    DELETE x FROM zsjos_production_ticket_item AS x JOIN zsjos_production_ticket AS t ON t.id=x.ticket_id JOIN tmp_v211_account_ids AS a ON a.id=t.account_id;
    DELETE x FROM zsjos_production_ticket AS x JOIN tmp_v211_account_ids AS a ON a.id=x.account_id;
    DELETE x FROM zsjos_content_version_file AS x JOIN zsjos_content_version AS v ON v.id=x.content_version_id JOIN zsjos_content AS c ON c.id=v.content_id JOIN tmp_v211_account_ids AS a ON a.id=c.account_id;
    DELETE x FROM zsjos_content_version AS x JOIN zsjos_content AS c ON c.id=x.content_id JOIN tmp_v211_account_ids AS a ON a.id=c.account_id;
    DELETE x FROM zsjos_content AS x JOIN tmp_v211_account_ids AS a ON a.id=x.account_id;
    DELETE x FROM zsjos_positioning_confirmation_link AS x JOIN zsjos_positioning_card AS c ON c.id=x.card_id JOIN tmp_v211_account_ids AS a ON a.id=c.account_id;
    DELETE x FROM zsjos_positioning_card_submission AS x JOIN tmp_v211_account_ids AS a ON a.id=x.account_id;
    DELETE x FROM zsjos_positioning_card_version AS x JOIN zsjos_positioning_card AS c ON c.id=x.positioning_card_id JOIN tmp_v211_account_ids AS a ON a.id=c.account_id;
    DELETE x FROM zsjos_positioning_exec_card AS x JOIN tmp_v211_account_ids AS a ON a.id=x.account_id;
    DELETE x FROM zsjos_positioning_card AS x JOIN tmp_v211_account_ids AS a ON a.id=x.account_id;
    DROP TEMPORARY TABLE tmp_v211_account_ids;
    INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
      VALUES('V211','reset media account operation data',SHA2('V211__reset_media_account_operation_data.sql',256),NOW());
    INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
      VALUES('core','V211','reset media account operation data',SHA2('V211__reset_media_account_operation_data.sql',256),'baseline',NOW());
    COMMIT;
  END IF;
END$$
DELIMITER ;
CALL zsjos_V211_reset_media_account_operation_data();
DROP PROCEDURE IF EXISTS zsjos_V211_reset_media_account_operation_data;
