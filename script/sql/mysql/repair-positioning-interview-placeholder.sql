-- UTF-8. Controlled development correction ONLY for the earlier V203 21-field clone.
-- Run V203 in this same MySQL session first (it sets @v203_fields).
-- Preserves the prior published fields and all business records. Creates a new version,
-- updates only untouched V203-created defaults, and is a no-op on rerun/custom templates.
-- Rollback requires explicitly restoring the previous published pointer; never delete history.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO zsjos_director_form_template_version
 (template_id,version_no,status,fields_json,published_at,version,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT t.id,2,'published',@v203_fields,NOW(),0,'V203-complete-outline',NOW(),'V203-complete-outline',NOW(),b'0',t.tenant_id
FROM zsjos_director_form_template t JOIN zsjos_director_form_template_version v ON v.id=t.published_version_id
WHERE t.scene='director_positioning_interview' AND t.template_code='default_positioning_interview'
 AND t.creator='V203' AND t.version=0 AND t.deleted=b'0'
 AND v.creator='V203' AND v.version_no=1 AND v.version=0 AND JSON_LENGTH(v.fields_json)=21
 AND JSON_LENGTH(@v203_fields)=52 AND NOT EXISTS
 (SELECT 1 FROM zsjos_director_form_template_version other WHERE other.template_id=t.id AND other.version_no>1);
UPDATE zsjos_director_form_template t JOIN zsjos_director_form_template_version v
 ON v.template_id=t.id AND v.tenant_id=t.tenant_id AND v.version_no=2 AND v.creator='V203-complete-outline'
JOIN zsjos_director_form_template_version old ON old.id=t.published_version_id AND old.version_no=1 AND old.creator='V203'
SET t.published_version_id=v.id,t.version=t.version+1,t.updater='V203-complete-outline',old.status='superseded'
WHERE t.scene='director_positioning_interview' AND t.creator='V203' AND t.version=0
 AND t.deleted=b'0' AND JSON_LENGTH(old.fields_json)=21;
COMMIT;
