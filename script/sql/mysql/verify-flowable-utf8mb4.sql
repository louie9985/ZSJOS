-- UTF-8. Read-only V331 acceptance; use the intended database and an utf8mb4 client.
SET NAMES utf8mb4;
WITH targets AS (
    SELECT expected_name FROM JSON_TABLE(
      '["ACT_EVT_LOG","ACT_GE_BYTEARRAY","ACT_GE_PROPERTY","ACT_HI_ACTINST","ACT_HI_ATTACHMENT","ACT_HI_COMMENT","ACT_HI_DETAIL","ACT_HI_ENTITYLINK","ACT_HI_IDENTITYLINK","ACT_HI_PROCINST","ACT_HI_TASKINST","ACT_HI_TSK_LOG","ACT_HI_VARINST","ACT_ID_BYTEARRAY","ACT_ID_GROUP","ACT_ID_INFO","ACT_ID_MEMBERSHIP","ACT_ID_PRIV","ACT_ID_PRIV_MAPPING","ACT_ID_PROPERTY","ACT_ID_TOKEN","ACT_ID_USER","ACT_PROCDEF_INFO","ACT_RE_DEPLOYMENT","ACT_RE_MODEL","ACT_RE_PROCDEF","ACT_RU_ACTINST","ACT_RU_DEADLETTER_JOB","ACT_RU_ENTITYLINK","ACT_RU_EVENT_SUBSCR","ACT_RU_EXECUTION","ACT_RU_EXTERNAL_JOB","ACT_RU_HISTORY_JOB","ACT_RU_IDENTITYLINK","ACT_RU_JOB","ACT_RU_SUSPENDED_JOB","ACT_RU_TASK","ACT_RU_TIMER_JOB","ACT_RU_VARIABLE","FLW_RU_BATCH","FLW_RU_BATCH_PART"]',
      '$[*]' COLUMNS(expected_name VARCHAR(64) PATH '$')) AS names
)
SELECT x.expected_name AS flowable_table,
       COUNT(c.column_name) AS total_columns,
       SUM(c.character_set_name IS NOT NULL) AS character_columns,
       IF(COUNT(DISTINCT t.table_name)=1 AND MIN(t.table_collation)='utf8mb4_bin'
          AND COUNT(c.column_name)>0
          AND SUM(c.character_set_name IS NOT NULL AND c.collation_name<>'utf8mb4_bin')=0,
          'PASS','FAIL') AS result
FROM targets x
LEFT JOIN information_schema.tables t ON t.table_schema=DATABASE() AND UPPER(t.table_name)=x.expected_name
LEFT JOIN information_schema.columns c ON c.table_schema=t.table_schema AND c.table_name=t.table_name
GROUP BY x.expected_name ORDER BY x.expected_name;

SELECT 'flowable_utf8mb4_V331_ledgers' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V331')=1
          AND (SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V331')=1,
          'PASS','MISSING') AS result;

-- Explicitly report other remaining utf8mb3 columns rather than converting an unreviewed scope.
SELECT table_name,column_name,character_set_name,collation_name
FROM information_schema.columns WHERE table_schema=DATABASE() AND character_set_name IN ('utf8','utf8mb3')
ORDER BY table_name,ordinal_position;
