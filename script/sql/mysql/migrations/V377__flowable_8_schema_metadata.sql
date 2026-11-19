-- UTF-8. For verified StrongUuidGenerator only, set @zsjos_v377_id_generator='strong-uuid'
-- in the same connection before CALL. Missing/unknown mode never bypasses populated-engine protection.
-- UUID mode leaves an absent counter absent; existing counters are never rewritten.
-- Apply after V376 and before starting the Flowable 8 application.
-- Stop all application executors and back up the selected database first.
-- The Core baseline already carries Flowable 8 table structures but omits
-- engine version properties. The engine otherwise interprets it as 6.1.2.
-- Validate the native 8.0.0 MySQL structure before reconciling any old
-- metadata. Version properties and both Core ledgers are written atomically.
-- No Flowable definitions, tasks, process instances, histories or existing
-- ID counters are changed. Replay is safe. For rollback, restore the
-- pre-execution property snapshot with all application executors stopped.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v377_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v377_apply()
BEGIN
    DECLARE original_group_concat BIGINT UNSIGNED DEFAULT @@SESSION.group_concat_max_len;
    DECLARE shape_hash CHAR(64);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET SESSION group_concat_max_len=original_group_concat;
        RESIGNAL;
    END;

    START TRANSACTION;
    IF EXISTS(SELECT 1 FROM ACT_GE_PROPERTY WHERE NAME_ IN
          ('schema.version','common.schema.version','eventregistry.schema.version')
          AND (VALUE_ IS NULL OR VALUE_ NOT IN ('6.1.2.0','6.2.0.0','8.0.0.0')))
       OR EXISTS(SELECT 1 FROM ACT_ID_PROPERTY WHERE NAME_='schema.version'
          AND (VALUE_ IS NULL OR VALUE_ NOT IN ('6.1.2.0','6.2.0.0','8.0.0.0'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 found an unreviewed Flowable schema version';
    END IF;

    -- Hash the native 8.0.0 table, column, index and FK definitions in a
    -- stable order. Exclude string collations because V331 widens utf8mb3.
    SET SESSION group_concat_max_len=1048576;
    SELECT SHA2(GROUP_CONCAT(shape ORDER BY BINARY shape SEPARATOR '\n'),256) INTO shape_hash
    FROM (SELECT CONCAT_WS('|',UPPER(table_name),engine,row_format) AS shape
          FROM information_schema.tables WHERE table_schema=DATABASE()
            AND UPPER(table_name) REGEXP '^(ACT|FLW)_') shapes;
    IF NOT (shape_hash <=> '7c526d40f83a7c29ac358a2f412fcc6bbdd70906d1280380de5c17ffd92e9a69') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 Flowable table definitions differ from the reviewed 8.0.0 schema';
    END IF;

    SELECT SHA2(GROUP_CONCAT(shape ORDER BY BINARY shape SEPARATOR '\n'),256) INTO shape_hash
    FROM (SELECT CONCAT_WS('|',UPPER(table_name),UPPER(column_name),column_type,is_nullable,
                           COALESCE(column_default,'<NULL>'),extra,ordinal_position) AS shape
          FROM information_schema.columns WHERE table_schema=DATABASE()
            AND UPPER(table_name) REGEXP '^(ACT|FLW)_') shapes;
    IF NOT (shape_hash <=> 'd8a80d3beff6a985f754cd1dcbe77ea2c3f47bb32fdde9758da73a3544068e28') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 Flowable column definitions differ from the reviewed 8.0.0 schema';
    END IF;

    SELECT SHA2(GROUP_CONCAT(shape ORDER BY BINARY shape SEPARATOR '\n'),256) INTO shape_hash
    FROM (SELECT CONCAT_WS('|',UPPER(table_name),UPPER(index_name),seq_in_index,UPPER(column_name),
                           non_unique,COALESCE(sub_part,'<NULL>'),index_type,
                           COALESCE(collation,'<NULL>'),is_visible) AS shape
          FROM information_schema.statistics WHERE table_schema=DATABASE()
            AND UPPER(table_name) REGEXP '^(ACT|FLW)_') shapes;
    IF NOT (shape_hash <=> 'ffbb9f810b8797a4402d06bbeafb9e082567fcb132114ee919be18b63b3b7d00') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 Flowable index definitions differ from the reviewed 8.0.0 schema';
    END IF;

    SELECT SHA2(GROUP_CONCAT(shape ORDER BY BINARY shape SEPARATOR '\n'),256) INTO shape_hash
    FROM (SELECT CONCAT_WS('|',UPPER(k.table_name),UPPER(k.constraint_name),UPPER(k.column_name),
                           k.ordinal_position,UPPER(k.referenced_table_name),UPPER(k.referenced_column_name),
                           r.update_rule,r.delete_rule) AS shape
          FROM information_schema.key_column_usage k
          JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema
              AND r.table_name=k.table_name AND r.constraint_name=k.constraint_name
          WHERE k.table_schema=DATABASE() AND UPPER(k.table_name) REGEXP '^(ACT|FLW)_') shapes;
    IF NOT (shape_hash <=> '1f87ea5208c9b3fbe2e0fc54efdb753f110724f6939b018a5d1d21b70ab09ead') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 Flowable foreign keys differ from the reviewed 8.0.0 schema';
    END IF;

    -- SQL cannot infer a Java engine configuration from stored IDs. The operator
    -- must verify the deployed generator before explicitly selecting UUID mode.
    IF @zsjos_v377_id_generator IS NOT NULL
       AND @zsjos_v377_id_generator NOT IN ('strong-uuid','database') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 unknown ID generator mode';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid') THEN
        -- A blank engine starts at 1. Any engine data requires the
        -- original counter unless the operator has verified UUID generation.
        SELECT CONCAT('SELECT ',GROUP_CONCAT(
            CONCAT('EXISTS(SELECT 1 FROM `',REPLACE(table_name,'`','``'),'`)')
            ORDER BY table_name SEPARATOR ' OR '),' INTO @v377_engine_has_data')
        INTO @v377_engine_check
        FROM information_schema.tables
        WHERE table_schema=DATABASE() AND UPPER(table_name) REGEXP '^(ACT|FLW)_'
          AND UPPER(table_name) NOT IN ('ACT_GE_PROPERTY','ACT_ID_PROPERTY');
        PREPARE zsjos_v377_engine_check FROM @v377_engine_check;
        EXECUTE zsjos_v377_engine_check;
        DEALLOCATE PREPARE zsjos_v377_engine_check;
        IF @v377_engine_has_data AND NOT (COALESCE(@zsjos_v377_id_generator,'')='strong-uuid') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 requires the original Flowable ID counter for a populated engine';
        END IF;
        IF COALESCE(@zsjos_v377_id_generator,'')<>'strong-uuid' THEN
            INSERT INTO ACT_GE_PROPERTY(NAME_,VALUE_,REV_) VALUES ('next.dbid','1',1);
        END IF;
    END IF;
    INSERT INTO ACT_GE_PROPERTY(NAME_,VALUE_,REV_)
    VALUES ('schema.version','8.0.0.0',1),('common.schema.version','8.0.0.0',1),
           ('eventregistry.schema.version','8.0.0.0',1)
    ON DUPLICATE KEY UPDATE REV_=IF(VALUE_='8.0.0.0',REV_,COALESCE(REV_,0)+1),VALUE_='8.0.0.0';
    INSERT INTO ACT_ID_PROPERTY(NAME_,VALUE_,REV_) VALUES ('schema.version','8.0.0.0',1)
    ON DUPLICATE KEY UPDATE REV_=IF(VALUE_='8.0.0.0',REV_,COALESCE(REV_,0)+1),VALUE_='8.0.0.0';

    IF (SELECT COUNT(*) FROM ACT_GE_PROPERTY WHERE NAME_ IN
          ('schema.version','common.schema.version','eventregistry.schema.version') AND VALUE_='8.0.0.0')<>3
       OR NOT EXISTS(SELECT 1 FROM ACT_ID_PROPERTY WHERE NAME_='schema.version' AND VALUE_='8.0.0.0')
       OR (COALESCE(@zsjos_v377_id_generator,'')<>'strong-uuid'
           AND NOT EXISTS(SELECT 1 FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid' AND VALUE_ REGEXP '^[1-9][0-9]*$'))
       OR EXISTS(SELECT 1 FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid' AND (VALUE_ IS NULL OR VALUE_ NOT REGEXP '^[1-9][0-9]*$')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V377 Flowable metadata postcondition failed';
    END IF;

    INSERT INTO zsjos_schema_version(version,description,checksum)
    SELECT 'V377','Flowable 8 schema metadata',SHA2('V377__flowable_8_schema_metadata.sql',256)
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V377');
    INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
    SELECT 'core','V377','Flowable 8 schema metadata',SHA2('V377__flowable_8_schema_metadata.sql',256),'baseline'
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V377');
    SET SESSION group_concat_max_len=original_group_concat;
    COMMIT;
END$$
DELIMITER ;
CALL zsjos_v377_apply();
DROP PROCEDURE zsjos_v377_apply;
