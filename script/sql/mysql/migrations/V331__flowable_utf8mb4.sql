-- UTF-8. MySQL 8+, InnoDB DYNAMIC, innodb_page_size >= 16384.
-- Scope: the 41 explicitly named Flowable tables; no business DML, grants or ASCII-field changes.
-- Normal order: after V330, through zsjos_db.py/deploy-production.sh. A scoped maintenance
-- execution may run independently once the Flowable schema and both version ledgers exist.
-- Stop ALL application/Flowable executors first; back up the database. The named lock excludes
-- other copies of this migration, not application writes. Run with an utf8mb4 client, without --force.
-- Foreign keys remain installed. Only this session disables checks during related-column DDL;
-- full FK validation runs after every table is converted, before success can be recorded.
-- DDL commits implicitly. On failure/connection loss keep applications stopped and replay this
-- file: actual metadata, never a version marker, decides what still needs conversion.
-- No automatic downgrade: after supplementary characters are stored, utf8mb3 may lose data.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v331_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v331_apply()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table VARCHAR(64);
    DECLARE v_parent VARCHAR(64);
    DECLARE v_constraint VARCHAR(64);
    DECLARE v_predicate TEXT;
    DECLARE v_nonnull TEXT;
    DECLARE v_message VARCHAR(128);
    DECLARE v_lock VARCHAR(64);
    DECLARE v_locked INT DEFAULT 0;
    DECLARE v_old_checks INT DEFAULT @@SESSION.foreign_key_checks;
    DECLARE v_old_wait BIGINT DEFAULT @@SESSION.lock_wait_timeout;
    DECLARE tables_cursor CURSOR FOR
        SELECT actual_name FROM tmp_v331_targets ORDER BY expected_name;
    DECLARE fks_cursor CURSOR FOR
        SELECT table_name, constraint_name, referenced_table_name,
               GROUP_CONCAT(CONCAT('c.`', REPLACE(column_name,'`','``'), '`=p.`',
                                   REPLACE(referenced_column_name,'`','``'), '`')
                            ORDER BY ordinal_position SEPARATOR ' AND '),
               GROUP_CONCAT(CONCAT('c.`', REPLACE(column_name,'`','``'), '` IS NOT NULL')
                            ORDER BY ordinal_position SEPARATOR ' AND ')
        FROM tmp_v331_fks
        GROUP BY table_name, constraint_name, referenced_table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done=1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET SESSION foreign_key_checks=v_old_checks;
        SET SESSION lock_wait_timeout=v_old_wait;
        IF v_locked=1 THEN DO RELEASE_LOCK(v_lock); END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR VERSION() LIKE '%MariaDB%'
       OR CAST(SUBSTRING_INDEX(VERSION(),'.',1) AS UNSIGNED)<8
       OR @@innodb_page_size<16384 OR v_old_checks<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 requires MySQL 8+, 16KiB+ pages, selected database and foreign_key_checks=1';
    END IF;
    SET v_lock=CONCAT('zsjos:v331:',LEFT(SHA2(DATABASE(),256),40));
    SELECT GET_LOCK(v_lock,0) INTO v_locked;
    IF COALESCE(v_locked,0)<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 is already running';
    END IF;
    SET SESSION lock_wait_timeout=60;
    IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ('zsjos_schema_version','zsjos_module_schema_version') AND table_type='BASE TABLE')<>2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 requires both existing schema version ledgers';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_v331_targets;
    CREATE TEMPORARY TABLE tmp_v331_targets (
        expected_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
        actual_name VARCHAR(64), before_rows BIGINT UNSIGNED
    );
    INSERT INTO tmp_v331_targets(expected_name) VALUES
        ('ACT_EVT_LOG'),('ACT_GE_BYTEARRAY'),('ACT_GE_PROPERTY'),
        ('ACT_HI_ACTINST'),('ACT_HI_ATTACHMENT'),('ACT_HI_COMMENT'),('ACT_HI_DETAIL'),
        ('ACT_HI_ENTITYLINK'),('ACT_HI_IDENTITYLINK'),('ACT_HI_PROCINST'),('ACT_HI_TASKINST'),
        ('ACT_HI_TSK_LOG'),('ACT_HI_VARINST'),('ACT_ID_BYTEARRAY'),('ACT_ID_GROUP'),
        ('ACT_ID_INFO'),('ACT_ID_MEMBERSHIP'),('ACT_ID_PRIV'),('ACT_ID_PRIV_MAPPING'),
        ('ACT_ID_PROPERTY'),('ACT_ID_TOKEN'),('ACT_ID_USER'),('ACT_PROCDEF_INFO'),
        ('ACT_RE_DEPLOYMENT'),('ACT_RE_MODEL'),('ACT_RE_PROCDEF'),('ACT_RU_ACTINST'),
        ('ACT_RU_DEADLETTER_JOB'),('ACT_RU_ENTITYLINK'),('ACT_RU_EVENT_SUBSCR'),
        ('ACT_RU_EXECUTION'),('ACT_RU_EXTERNAL_JOB'),('ACT_RU_HISTORY_JOB'),
        ('ACT_RU_IDENTITYLINK'),('ACT_RU_JOB'),('ACT_RU_SUSPENDED_JOB'),('ACT_RU_TASK'),
        ('ACT_RU_TIMER_JOB'),('ACT_RU_VARIABLE'),('FLW_RU_BATCH'),('FLW_RU_BATCH_PART');
    UPDATE tmp_v331_targets x JOIN information_schema.tables t
      ON t.table_schema=DATABASE() AND UPPER(t.table_name)=x.expected_name
      SET x.actual_name=t.table_name;
    IF EXISTS(SELECT 1 FROM tmp_v331_targets WHERE actual_name IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 requires all 41 Flowable tables; engine schema differs';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.tables t JOIN tmp_v331_targets x
        ON UPPER(t.table_name)=x.expected_name WHERE t.table_schema=DATABASE())<>41 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 requires all 41 unambiguous Flowable tables; engine schema differs';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.tables t JOIN tmp_v331_targets x
              ON t.table_name=x.actual_name WHERE t.table_schema=DATABASE()
              AND (t.table_type<>'BASE TABLE' OR t.engine<>'InnoDB' OR t.row_format<>'Dynamic'
                   OR t.table_collation NOT IN ('utf8mb3_bin','utf8mb4_bin'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 requires InnoDB DYNAMIC tables with utf8mb3_bin or utf8mb4_bin defaults';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.columns c JOIN tmp_v331_targets x
              ON c.table_name=x.actual_name WHERE c.table_schema=DATABASE()
              AND c.character_set_name IS NOT NULL
              AND c.collation_name NOT IN ('utf8mb3_bin','utf8mb4_bin')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 found an unreviewed column collation; no conversion performed';
    END IF;
    -- Separate copies avoid MySQL's temporary-table reopening restriction.
    DROP TEMPORARY TABLE IF EXISTS tmp_v331_target_copy;
    CREATE TEMPORARY TABLE tmp_v331_target_copy AS SELECT * FROM tmp_v331_targets;
    IF EXISTS(
        SELECT 1 FROM information_schema.key_column_usage k
        LEFT JOIN tmp_v331_targets c ON k.table_schema=DATABASE() AND k.table_name=c.actual_name
        LEFT JOIN tmp_v331_target_copy p ON k.referenced_table_schema=DATABASE() AND k.referenced_table_name=p.actual_name
        WHERE k.referenced_table_name IS NOT NULL
          AND (c.actual_name IS NOT NULL OR p.actual_name IS NOT NULL)
          AND (c.actual_name IS NULL OR p.actual_name IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 found a foreign key crossing the reviewed table scope';
    END IF;
    IF EXISTS(
        SELECT 1 FROM information_schema.statistics s
        JOIN information_schema.columns c ON c.table_schema=s.table_schema
          AND c.table_name=s.table_name AND c.column_name=s.column_name
        JOIN tmp_v331_targets x ON s.table_name=x.actual_name
        WHERE s.table_schema=DATABASE()
        GROUP BY s.table_name,s.index_name
        HAVING SUM(CASE WHEN c.character_set_name IS NOT NULL THEN
                    LEAST(COALESCE(s.sub_part,c.character_maximum_length),c.character_maximum_length)*4
                    WHEN c.data_type='bigint' THEN 8 WHEN c.data_type='int' THEN 4
                    WHEN c.data_type='smallint' THEN 2 WHEN c.data_type='tinyint' THEN 1
                    ELSE COALESCE(LEAST(s.sub_part,c.character_octet_length),c.character_octet_length,16) END)>3072
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 utf8mb4 index estimate exceeds 3072 bytes; review before conversion';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_v331_columns;
    CREATE TEMPORARY TABLE tmp_v331_columns AS
      SELECT c.table_name,c.column_name,c.ordinal_position,c.column_type,c.is_nullable,
             c.column_default,c.extra,c.column_comment,c.generation_expression,c.character_set_name
      FROM information_schema.columns c JOIN tmp_v331_targets x ON c.table_name=x.actual_name
      WHERE c.table_schema=DATABASE();
    DROP TEMPORARY TABLE IF EXISTS tmp_v331_indexes;
    CREATE TEMPORARY TABLE tmp_v331_indexes AS
      SELECT s.table_name,s.index_name,s.seq_in_index,s.column_name,s.sub_part,s.non_unique,
             s.index_type,s.is_visible,s.expression,s.collation
      FROM information_schema.statistics s JOIN tmp_v331_targets x ON s.table_name=x.actual_name
      WHERE s.table_schema=DATABASE();
    DROP TEMPORARY TABLE IF EXISTS tmp_v331_fks;
    CREATE TEMPORARY TABLE tmp_v331_fks AS
      SELECT k.table_name,k.constraint_name,k.ordinal_position,k.column_name,
             k.referenced_table_schema,k.referenced_table_name,k.referenced_column_name,
             r.update_rule,r.delete_rule
      FROM information_schema.key_column_usage k
      JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema
        AND r.table_name=k.table_name AND r.constraint_name=k.constraint_name
      JOIN tmp_v331_targets x ON k.table_name=x.actual_name
      WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL;

    OPEN tables_cursor;
    count_loop: LOOP
        FETCH tables_cursor INTO v_table;
        IF v_done=1 THEN LEAVE count_loop; END IF;
        SET @v331_sql=CONCAT('SELECT COUNT(*) INTO @v331_rows FROM `',REPLACE(v_table,'`','``'),'`');
        PREPARE v331_stmt FROM @v331_sql; EXECUTE v331_stmt; DEALLOCATE PREPARE v331_stmt;
        UPDATE tmp_v331_targets SET before_rows=@v331_rows WHERE actual_name=v_table;
    END LOOP;
    CLOSE tables_cursor;

    SET SESSION foreign_key_checks=0;
    SET v_done=0;
    OPEN tables_cursor;
    convert_loop: LOOP
        FETCH tables_cursor INTO v_table;
        IF v_done=1 THEN LEAVE convert_loop; END IF;
        IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                  AND table_name=v_table AND character_set_name='utf8mb3')
           OR EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                     AND table_name=v_table AND table_collation<>'utf8mb4_bin') THEN
            SET @v331_sql=CONCAT('ALTER TABLE `',REPLACE(v_table,'`','``'),
                                '` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin');
            PREPARE v331_stmt FROM @v331_sql; EXECUTE v331_stmt; DEALLOCATE PREPARE v331_stmt;
        END IF;
    END LOOP;
    CLOSE tables_cursor;
    SET SESSION foreign_key_checks=1;

    IF EXISTS(SELECT 1 FROM information_schema.columns c JOIN tmp_v331_targets x ON c.table_name=x.actual_name
              WHERE c.table_schema=DATABASE() AND c.character_set_name IS NOT NULL AND c.collation_name<>'utf8mb4_bin') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 column charset postcondition failed';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.tables t JOIN tmp_v331_targets x ON t.table_name=x.actual_name
        WHERE t.table_schema=DATABASE() AND t.table_collation='utf8mb4_bin')<>41 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 charset postcondition failed';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns c JOIN tmp_v331_targets x
        ON c.table_name=x.actual_name WHERE c.table_schema=DATABASE())<>(SELECT COUNT(*) FROM tmp_v331_columns) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 column count postcondition failed';
    END IF;
    IF EXISTS(
        SELECT 1 FROM tmp_v331_columns b LEFT JOIN information_schema.columns c
          ON c.table_schema=DATABASE() AND c.table_name=b.table_name AND c.column_name=b.column_name
        WHERE c.column_name IS NULL OR c.ordinal_position<>b.ordinal_position OR BINARY c.column_type<>BINARY b.column_type
          OR c.is_nullable<>b.is_nullable OR NOT(BINARY c.column_default <=> BINARY b.column_default)
          OR BINARY c.extra<>BINARY b.extra OR BINARY c.column_comment<>BINARY b.column_comment
          OR BINARY c.generation_expression<>BINARY b.generation_expression
          OR (b.character_set_name IS NULL AND c.character_set_name IS NOT NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 changed a column definition beyond its charset';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.statistics s JOIN tmp_v331_targets x
        ON s.table_name=x.actual_name WHERE s.table_schema=DATABASE())<>(SELECT COUNT(*) FROM tmp_v331_indexes) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 index count postcondition failed';
    END IF;
    IF EXISTS(
        SELECT 1 FROM tmp_v331_indexes b LEFT JOIN information_schema.statistics s
          ON s.table_schema=DATABASE() AND s.table_name=b.table_name AND s.index_name=b.index_name AND s.seq_in_index=b.seq_in_index
        WHERE s.index_name IS NULL OR NOT(s.column_name <=> b.column_name) OR NOT(s.sub_part <=> b.sub_part)
          OR s.non_unique<>b.non_unique OR s.index_type<>b.index_type OR s.is_visible<>b.is_visible
          OR NOT(s.expression <=> b.expression) OR NOT(s.collation <=> b.collation)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 index postcondition failed';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.key_column_usage k JOIN tmp_v331_targets x
        ON k.table_name=x.actual_name WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL)
          <>(SELECT COUNT(*) FROM tmp_v331_fks) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 foreign key count postcondition failed';
    END IF;
    IF EXISTS(
        SELECT 1 FROM tmp_v331_fks b LEFT JOIN information_schema.key_column_usage k
          ON k.table_schema=DATABASE() AND k.table_name=b.table_name AND k.constraint_name=b.constraint_name
          AND k.ordinal_position=b.ordinal_position
        LEFT JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema
          AND r.table_name=k.table_name AND r.constraint_name=k.constraint_name
        WHERE k.column_name IS NULL OR k.column_name<>b.column_name
          OR k.referenced_table_schema<>b.referenced_table_schema OR k.referenced_table_name<>b.referenced_table_name
          OR k.referenced_column_name<>b.referenced_column_name OR r.update_rule<>b.update_rule OR r.delete_rule<>b.delete_rule
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 foreign key definition postcondition failed';
    END IF;
    SET v_done=0;
    OPEN fks_cursor;
    orphan_loop: LOOP
        FETCH fks_cursor INTO v_table,v_constraint,v_parent,v_predicate,v_nonnull;
        IF v_done=1 THEN LEAVE orphan_loop; END IF;
        SET @v331_sql=CONCAT('SELECT EXISTS(SELECT 1 FROM `',REPLACE(v_table,'`','``'),
          '` c WHERE ',v_nonnull,' AND NOT EXISTS(SELECT 1 FROM `',REPLACE(v_parent,'`','``'),
          '` p WHERE ',v_predicate,')) INTO @v331_orphan');
        PREPARE v331_stmt FROM @v331_sql; EXECUTE v331_stmt; DEALLOCATE PREPARE v331_stmt;
        IF @v331_orphan<>0 THEN
            SET v_message=CONCAT('V331 orphaned foreign key: ',v_constraint);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_message;
        END IF;
    END LOOP;
    CLOSE fks_cursor;
    SET v_done=0;
    OPEN tables_cursor;
    verify_count_loop: LOOP
        FETCH tables_cursor INTO v_table;
        IF v_done=1 THEN LEAVE verify_count_loop; END IF;
        SET @v331_sql=CONCAT('SELECT COUNT(*) INTO @v331_rows FROM `',REPLACE(v_table,'`','``'),'`');
        PREPARE v331_stmt FROM @v331_sql; EXECUTE v331_stmt; DEALLOCATE PREPARE v331_stmt;
        IF EXISTS(SELECT 1 FROM tmp_v331_targets WHERE actual_name=v_table AND before_rows<>@v331_rows) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V331 row count changed; keep all writers stopped and investigate';
        END IF;
    END LOOP;
    CLOSE tables_cursor;

    START TRANSACTION;
    INSERT INTO zsjos_schema_version(version,description,checksum)
      SELECT 'V331','Flowable UTF-8 四字节字符支持',SHA2('V331__flowable_utf8mb4.sql',256)
      WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V331');
    INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
      SELECT 'core','V331','Flowable UTF-8 四字节字符支持',SHA2('V331__flowable_utf8mb4.sql',256),'baseline'
      WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V331');
    COMMIT;
    SET SESSION lock_wait_timeout=v_old_wait;
    DO RELEASE_LOCK(v_lock);
    SET v_locked=0;
END$$
DELIMITER ;
CALL zsjos_v331_apply();
DROP PROCEDURE zsjos_v331_apply;
