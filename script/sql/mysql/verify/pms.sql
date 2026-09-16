-- PMS module verification. Read-only: this file only SELECTs.
-- Run after installing the pms module to confirm the schema and seed metadata exist.
-- Every check returns a row whose `result` column is either 'OK' or a FAIL description.

SELECT 'pms tables' AS check_name,
       CASE WHEN COUNT(*) = 33 THEN 'OK'
            ELSE CONCAT('FAIL: expected 33 PMS tables, found ', COUNT(*)) END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('pms_iteration',
                     'pms_knowledge_content_permission',
                     'pms_knowledge_content_permission_member',
                     'pms_knowledge_document',
                     'pms_knowledge_document_comment',
                     'pms_knowledge_document_label',
                     'pms_knowledge_document_like',
                     'pms_knowledge_document_share',
                     'pms_knowledge_favorite',
                     'pms_knowledge_folder',
                     'pms_knowledge_group',
                     'pms_knowledge_group_relation',
                     'pms_knowledge_library',
                     'pms_knowledge_library_member',
                     'pms_knowledge_library_template',
                     'pms_knowledge_recycle_record',
                     'pms_knowledge_view_record',
                     'pms_project',
                     'pms_project_announcement',
                     'pms_project_favorite',
                     'pms_project_group',
                     'pms_project_group_relation',
                     'pms_project_member',
                     'pms_project_template',
                     'pms_work_item',
                     'pms_work_item_activity',
                     'pms_work_item_board',
                     'pms_work_item_comment',
                     'pms_work_item_label',
                     'pms_work_item_member',
                     'pms_work_item_status',
                     'pms_work_item_user_sort',
                     'pms_work_item_work_log');

SELECT 'pms knowledge document columns' AS check_name,
       CASE WHEN COUNT(*) = 4 THEN 'OK'
            ELSE 'FAIL: pms_knowledge_document permission/label/lifecycle columns are missing' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'pms_knowledge_document'
  AND column_name IN ('permission_id','label_ids','delete_user_id','delete_time');

SELECT 'pms work item columns' AS check_name,
       CASE WHEN COUNT(*) = 4 THEN 'OK'
            ELSE 'FAIL: pms_work_item lifecycle columns are missing' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'pms_work_item'
  AND column_name IN ('assignee_user_id','iteration_id','label_ids','file_urls');

SELECT 'pms menus' AS check_name,
       CASE WHEN COUNT(*) = 48 THEN 'OK'
            ELSE CONCAT('FAIL: expected 48 PMS menu and permission rows (8000-8104), found ',
                        COUNT(*)) END AS result
FROM `system_menu`
WHERE (`id` BETWEEN 8000 AND 8104) AND `deleted` = b'0';

SELECT 'pms dictionary types' AS check_name,
       CASE WHEN COUNT(*) = 19 THEN 'OK'
            ELSE CONCAT('FAIL: expected 19 PMS dictionary types, found ', COUNT(*)) END AS result
FROM `system_dict_type`
WHERE `type` LIKE 'pms\_%' AND `deleted` = b'0';

SELECT 'pms dictionary options' AS check_name,
       CASE WHEN COUNT(*) >= 20 THEN 'OK'
            ELSE CONCAT('FAIL: expected at least 20 PMS dictionary options, found ', COUNT(*)) END AS result
FROM `system_dict_data`
WHERE `dict_type` LIKE 'pms\_%' AND `deleted` = b'0';
