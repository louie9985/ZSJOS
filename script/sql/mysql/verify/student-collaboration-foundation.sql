SET NAMES utf8mb4;
-- Read-only: every count below except collaboration_groups must be zero.
-- A FAIL row blocks delivery; this script never repairs an existing binding implicitly.
SELECT COUNT(*) AS collaboration_groups FROM zsjos_collaboration_group WHERE deleted=b'0';
SELECT COUNT(*) AS unbound_service_relations FROM zsjos_service_relation WHERE deleted=b'0' AND collaboration_group_id IS NULL;
SELECT COUNT(*) AS duplicate_sources FROM (SELECT tenant_id,source_service_relation_id,COUNT(*) c FROM zsjos_collaboration_group WHERE deleted=b'0' GROUP BY tenant_id,source_service_relation_id HAVING c>1) x;
SELECT COUNT(*) AS mismatched_bindings FROM zsjos_service_relation sr
LEFT JOIN zsjos_collaboration_group cg ON cg.id=sr.collaboration_group_id AND cg.tenant_id=sr.tenant_id AND cg.deleted=b'0'
WHERE sr.deleted=b'0' AND (cg.id IS NULL OR cg.source_service_relation_id<>sr.id OR cg.student_person_id<>sr.person_id);
SELECT table_comment,HEX(table_comment) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_collaboration_group';
SELECT column_comment,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='collaboration_group_id';
SELECT version,description FROM zsjos_schema_version WHERE version='V218';
SELECT 'collaboration_binding_integrity' AS check_name,
IF(EXISTS(SELECT 1 FROM zsjos_service_relation sr
LEFT JOIN zsjos_collaboration_group cg ON cg.id=sr.collaboration_group_id AND cg.tenant_id=sr.tenant_id AND cg.deleted=b'0'
WHERE sr.deleted=b'0' AND (cg.id IS NULL OR cg.source_service_relation_id<>sr.id OR cg.student_person_id<>sr.person_id)), 'FAIL','PASS') AS result;
