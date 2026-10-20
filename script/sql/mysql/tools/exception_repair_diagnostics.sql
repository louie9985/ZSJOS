-- UTF-8. MySQL 8 read-only diagnostics; select the intended database before execution.
-- Scope: tenant 1; infra_file is a system table without tenant_id.
-- Requires infra_file, system_social_user and bpm_form from the existing baseline.
-- No values, URLs, identities, form bodies, tokens or configuration secrets are returned.
-- Repeatable SELECT-only inspection; no rollback or migration markers are needed.
SET NAMES utf8mb4;
START TRANSACTION READ ONLY;

SELECT table_name, column_name, column_type, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND ((table_name='infra_file' AND column_name IN ('path','url'))
    OR (table_name='system_social_user' AND column_name='openid')
    OR (table_name='bpm_form' AND column_name='fields'))
ORDER BY table_name,column_name;

SELECT COUNT(*) AS files, MAX(CHAR_LENGTH(url)) AS max_url_characters,
       SUM(CHAR_LENGTH(url)>=1024) AS urls_at_or_above_baseline_limit,
       SUM(LOCATE('?',url)>0) AS urls_with_query
FROM infra_file WHERE deleted=0;

SELECT type, COUNT(*) AS identities, MAX(CHAR_LENGTH(openid)) AS max_identity_characters,
       SUM(CHAR_LENGTH(openid)>=32) AS identities_at_or_above_baseline_limit
FROM system_social_user WHERE tenant_id=1 AND deleted=0 GROUP BY type;

SELECT CASE WHEN NOT JSON_VALID(fields) THEN 'INVALID'
            ELSE JSON_TYPE(IF(JSON_VALID(fields),fields,'null')) END AS document_type,
       COUNT(*) AS forms
FROM bpm_form WHERE tenant_id=1 AND deleted=0 GROUP BY document_type;

WITH parsed AS (
  SELECT IF(JSON_VALID(fields),fields,'null') AS doc
  FROM bpm_form WHERE tenant_id=1 AND deleted=0
)
SELECT JSON_TYPE(JSON_EXTRACT(doc,CONCAT('$[',j.ordinal-1,']'))) AS element_type,
       COUNT(*) AS elements
FROM parsed
JOIN JSON_TABLE(
  CASE WHEN JSON_TYPE(doc)='ARRAY' THEN doc ELSE JSON_ARRAY() END,
  '$[*]' COLUMNS(ordinal FOR ORDINALITY)
) AS j ON TRUE
GROUP BY element_type;
COMMIT;
