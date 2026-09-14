-- UTF-8; read-only checks after V203. Does not expose learner data.
SET NAMES utf8mb4;
SELECT table_name, table_collation FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_student_positioning_interview%';
SELECT t.tenant_id,t.scene,v.version_no,JSON_LENGTH(v.fields_json) AS field_count,
 HEX(t.name) AS template_name_utf8,
 HEX(JSON_UNQUOTE(JSON_EXTRACT(v.fields_json,'$[1].title'))) AS family_title_utf8,
 JSON_UNQUOTE(JSON_EXTRACT(v.fields_json,'$[51].key')) AS collection_key
FROM zsjos_director_form_template t JOIN zsjos_director_form_template_version v
 ON v.id=t.published_version_id AND v.tenant_id=t.tenant_id
WHERE t.scene='director_positioning_interview' AND t.deleted=b'0';
SELECT tenant_id,student_person_id,COUNT(*) AS invalid_duplicate_drafts
FROM zsjos_student_positioning_interview WHERE status='draft' AND deleted=b'0'
GROUP BY tenant_id,student_person_id HAVING COUNT(*)>1;
SELECT permission FROM system_menu WHERE deleted=b'0' AND permission IN
 ('zsjos:student:positioning-interview','zsjos:student:positioning-interview-query','zsjos:student:positioning-interview-complete');
SELECT version FROM zsjos_schema_version WHERE version='V203';
