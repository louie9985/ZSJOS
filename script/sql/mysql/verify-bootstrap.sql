-- Read-only verification. Every row should report PASS on a usable installation.
SET NAMES utf8mb4;

SELECT 'new_media_workflow_schema' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
           AND table_name IN ('zsjos_media_account','zsjos_content','zsjos_content_version','zsjos_production_ticket',
                              'zsjos_production_ticket_item','zsjos_positioning_card','zsjos_positioning_card_version',
                              'zsjos_positioning_exec_card','zsjos_interview_record',
                              'zsjos_cooperation_assessment','zsjos_review_report','zsjos_exception_ticket',
                              'zsjos_workbench_capacity','zsjos_partner_student_link'))=14
          AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V096'), 'PASS','FAIL') AS result;
SELECT 'new_media_version_and_signature_contract' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                  AND table_name='zsjos_content_version' AND index_name='uk_tenant_content_version_idempotency')
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                  AND table_name='zsjos_positioning_exec_card' AND column_name='signature_snapshot_json')
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                  AND table_name='zsjos_positioning_exec_card' AND column_name='effective_at'), 'PASS','FAIL') AS result;
SELECT 'new_media_workflow_permissions' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V097')
          AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission IN
             ('zsjos:content:advance','zsjos:production-ticket:transition','zsjos:positioning-card:advance') AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:content:acceptance-review' AND deleted=b'0'),
          'PASS','FAIL') AS result;
SELECT 'partner_student_active_unique_keys' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V098')
          AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
               AND table_name='zsjos_partner_student_link' AND non_unique=0
               AND index_name IN ('uk_tenant_active_partner','uk_tenant_active_student'))=2,
          'PASS','FAIL') AS result;
SELECT 'positioning_student_confirm_permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V099')
          AND EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:positioning-card:student-confirm' AND deleted=b'0'),
          'PASS','FAIL') AS result;
SELECT 'new_media_role_menu_permissions' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V103')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=7022 AND permission='zsjos:media-student:query-my' AND path='media-students' AND deleted=b'0')
          AND NOT EXISTS (SELECT 1 FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id WHERE r.code='content_director' AND rm.menu_id=73020 AND rm.deleted=b'0')
          AND NOT EXISTS (SELECT 1 FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id WHERE r.code='new_media_operator' AND rm.menu_id=7022 AND rm.deleted=b'0'), 'PASS','FAIL') AS result;
SELECT 'student_basic_info_permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V101')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V101')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=73427
                      AND permission='zsjos:student:update-basic-info' AND parent_id=73020
                      AND type=3 AND status=0 AND deleted=b'0'), 'PASS','FAIL') AS result;
SELECT 'student_delivery_stages' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V114')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V114')
          AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
               AND ((table_name='zsjos_service_relation' AND column_name IN ('delivery_stage','delivery_data_json'))
                 OR (table_name='zsjos_student_contact_record' AND column_name IN ('delivery_stage','delivery_data_json'))))=4
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name='zsjos_student_contact_record' AND column_name='task_id' AND is_nullable='YES')
          AND EXISTS (SELECT 1 FROM system_menu student_menu
               JOIN system_menu root_menu ON root_menu.id=student_menu.parent_id
                    AND root_menu.path='/zsjos' AND root_menu.parent_id=0
                    AND root_menu.status=0 AND root_menu.deleted=b'0'
               WHERE student_menu.id=73020 AND student_menu.permission='zsjos:student:query-my'
                    AND student_menu.path='my-students' AND student_menu.component='zsjos/my-students'
                    AND student_menu.component_name='ZsjosMyStudents' AND student_menu.type=2
                    AND student_menu.status=0 AND student_menu.visible=b'1' AND student_menu.deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=73428
               AND permission='zsjos:student-contact:delivery-stage-submit' AND parent_id=73020
               AND type=3 AND status=0 AND deleted=b'0')
          AND NOT EXISTS (SELECT 1 FROM system_role role_row
               WHERE role_row.code='study_planner' AND role_row.status=0 AND role_row.deleted=b'0'
                 AND (SELECT COUNT(DISTINCT grant_row.menu_id) FROM system_role_menu grant_row
                      WHERE grant_row.role_id=role_row.id AND grant_row.tenant_id=role_row.tenant_id
                        AND grant_row.menu_id IN (73020,73428) AND grant_row.deleted=b'0')<>2),
          'PASS','FAIL') AS result;
SELECT 'generic_work_order_schema' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V115')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V115')
          AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
               AND table_name IN ('zsjos_work_order_scene','zsjos_work_order','zsjos_work_order_history'))=3
          AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
               AND ((table_name='zsjos_work_order' AND column_name IN ('command_user_id','request_fingerprint'))
                 OR (table_name='zsjos_work_order_history' AND column_name IN ('operation','request_fingerprint'))))=4
          AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
               AND table_name='zsjos_work_order' AND index_name IN ('idx_source_user','idx_target_user'))=8,
          'PASS','FAIL') AS result;
SELECT 'student_delivery_stages_checksums' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V114' AND checksum='student-delivery-stages-v6')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V114'
                     AND checksum=SHA2('student-delivery-stages-v6',256)), 'PASS','FAIL') AS result;
SELECT 'generic_work_order_idempotency_contract' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                  AND table_name='zsjos_work_order' AND column_name='idempotency_key'
                  AND is_nullable='NO' AND collation_name='utf8mb4_bin')
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                  AND table_name='zsjos_work_order_history' AND column_name='idempotency_key'
                  AND is_nullable='NO' AND collation_name='utf8mb4_bin'), 'PASS','FAIL') AS result;
SELECT 'generic_work_order_checksums' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V115' AND checksum='V115__generic_work_order.sql')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V115'
                     AND checksum=SHA2('V115__generic_work_order.sql',256)), 'PASS','FAIL') AS result;
SELECT 'study_planner_repurchase_schema_gate' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V116'
                  AND checksum='study-planner-repurchase-permission-v5')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V116'
                     AND checksum=SHA2('study-planner-repurchase-permission-v5',256))
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                     AND table_name='zsjos_order' AND column_name='submission_request_fingerprint')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=6813
                     AND permission='zsjos:sales-order:query-own' AND path='sales-orders/my'
                     AND type=2 AND status=0 AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=73020
                     AND permission='zsjos:student:query-my' AND path='my-students'
                     AND type=2 AND status=0 AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=73440
                     AND permission='zsjos:sales-order:student-repurchase' AND parent_id=73020
                     AND type=3 AND status=0 AND deleted=b'0'), 'PASS','FAIL') AS result;
SELECT 'new_media_business_notifications' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V102')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V102')
          AND (SELECT COUNT(DISTINCT scene_code) FROM system_notify_template
               WHERE scene_code LIKE 'media.%' AND creator='migration-V102' AND deleted=b'0')=17
          AND NOT EXISTS (SELECT 1 FROM system_tenant tenant
               WHERE tenant.deleted=b'0' AND (SELECT COUNT(DISTINCT rule_row.scene_code)
                    FROM system_notify_rule rule_row
                    WHERE rule_row.tenant_id=tenant.id AND rule_row.scene_code LIKE 'media.%'
                      AND rule_row.deleted=b'0')<17), 'PASS','FAIL') AS result;

SELECT 'schema_version' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_schema_version'), 'PASS', 'FAIL') AS result;

SELECT 'V120_operator_media_student_grant' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V120'
                  AND checksum='restore-operator-media-student-menu-v1')
          AND EXISTS (
            SELECT 1 FROM system_role_menu rm
            JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
            JOIN system_menu m ON m.id=rm.menu_id
            WHERE r.code='new_media_operator' AND r.status=0 AND r.deleted=b'0'
              AND m.id=7022 AND m.permission='zsjos:media-student:query-my'
              AND rm.deleted=b'0' AND m.deleted=b'0'
          ), 'PASS', 'FAIL') AS result;
SELECT 'employee_birthday_care_migration' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V081'), 'PASS', 'FAIL') AS result;
SELECT 'registration_planner_notification_migration' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V082'), 'PASS', 'FAIL') AS result;
SELECT 'registration_routes_attachments_migration' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V083'), 'PASS', 'FAIL') AS result;
SELECT 'registration_route_tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
            AND table_name IN ('zsjos_registration_route_option','zsjos_registration_case_route','zsjos_registration_item_attachment'))=3,
          'PASS','FAIL') AS result;
SELECT 'registration_exact_default_routes' AS check_name,
       IF(NOT EXISTS (SELECT version_row.id FROM zsjos_registration_checklist_version version_row
            WHERE version_row.status='published' AND version_row.deleted=b'0'
              AND (SELECT COUNT(*) FROM zsjos_registration_route_option option_row
                   WHERE option_row.tenant_id=version_row.tenant_id AND option_row.version_id=version_row.id
                     AND option_row.option_key IN ('student_delivery','new_media') AND option_row.deleted=b'0')<>2),
          'PASS','FAIL') AS result;
SELECT 'content_director_my_students_menu' AS check_name,
       IF(NOT EXISTS (SELECT 1 FROM system_role role_row
            WHERE role_row.code='content_director' AND role_row.status=0 AND role_row.deleted=b'0'
              AND NOT EXISTS (SELECT 1 FROM system_role_menu relation_row
                   WHERE relation_row.role_id=role_row.id AND relation_row.menu_id=7022
                     AND relation_row.tenant_id=role_row.tenant_id AND relation_row.deleted=b'0'))
          AND NOT EXISTS (SELECT 1 FROM system_role role_row
            JOIN system_role_menu relation_row ON relation_row.role_id=role_row.id
                 AND relation_row.tenant_id=role_row.tenant_id AND relation_row.deleted=b'0'
            WHERE role_row.code='content_director' AND role_row.deleted=b'0'
              AND relation_row.menu_id=73020),
          'PASS','FAIL') AS result;
SELECT 'employee_birthday_care_job' AS check_name,
       IF((SELECT COUNT(*) FROM infra_job WHERE handler_name='employeeBirthdayCareJob' AND deleted=b'0')=1, 'PASS', 'FAIL') AS result;
SELECT 'employee_birthday_care_menu' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu
            WHERE id IN (602100,602101,602102) AND deleted=b'0')=3
          AND (SELECT COUNT(*) FROM system_menu
                WHERE parent_id=602100 AND id IN (602101,602102) AND deleted=b'0')=2,
          'PASS', 'FAIL') AS result;
SELECT 'employee_birthday_care_menu_repair_migration' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V084'), 'PASS', 'FAIL') AS result;
SELECT 'employee_birthday_care_super_admin_menu' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM system_role role_row
             WHERE role_row.code='super_admin' AND role_row.status=0 AND role_row.deleted=b'0'
               AND (SELECT COUNT(*) FROM system_role_menu relation_row
                     WHERE relation_row.role_id=role_row.id AND relation_row.tenant_id=role_row.tenant_id
                       AND relation_row.menu_id IN (602100,602101,602102) AND relation_row.deleted=b'0')<>3
          ), 'PASS', 'FAIL') AS result;

SELECT 'business_notification_customer_name_migration' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V085')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V085'), 'PASS', 'FAIL') AS result;
SELECT 'business_notification_identifier_repair_migration' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V087')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V087'), 'PASS', 'FAIL') AS result;
SELECT 'business_notification_templates_no_customer_name_variables' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM system_notify_template
             WHERE ((scene_code LIKE 'zsjos.lead.%' AND (title LIKE '%lead.name%' OR summary LIKE '%lead.name%' OR content LIKE '%lead.name%' OR params LIKE '%lead.name%'))
                 OR (scene_code LIKE 'zsjos.sales_order.%' AND (title LIKE '%order.studentName%' OR summary LIKE '%order.studentName%' OR content LIKE '%order.studentName%' OR params LIKE '%order.studentName%'))
                 OR (scene_code LIKE 'zsjos.registration.%' AND (title LIKE '%student.name%' OR summary LIKE '%student.name%' OR content LIKE '%student.name%' OR params LIKE '%student.name%')))
          ), 'PASS', 'FAIL') AS result;
SELECT 'business_notification_history_parameter_json_valid' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM system_notify_message
             WHERE (scene_code LIKE 'zsjos.lead.%' OR scene_code LIKE 'zsjos.sales_order.%'
                    OR scene_code LIKE 'zsjos.registration.%')
               AND (template_params IS NULL OR JSON_VALID(template_params)=0
                    OR JSON_TYPE(template_params)<>'OBJECT')
          ), 'PASS', 'FAIL') AS result;
SELECT 'business_notification_history_no_customer_name_parameters' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM system_notify_message
             WHERE JSON_VALID(template_params)
               AND ((scene_code LIKE 'zsjos.lead.%' AND JSON_CONTAINS_PATH(template_params,'one','$."lead.name"'))
                 OR (scene_code LIKE 'zsjos.sales_order.%' AND JSON_CONTAINS_PATH(template_params,'one','$."order.studentName"'))
                 OR (scene_code LIKE 'zsjos.registration.%' AND JSON_CONTAINS_PATH(template_params,'one','$."student.name"')))
          ), 'PASS', 'FAIL') AS result;
SELECT 'business_notification_template_params_unique' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM system_notify_template template_row
             WHERE (template_row.scene_code LIKE 'zsjos.lead.%'
                 OR template_row.scene_code LIKE 'zsjos.sales_order.%'
                 OR template_row.scene_code LIKE 'zsjos.registration.%')
               AND JSON_VALID(template_row.params)
               AND JSON_TYPE(template_row.params)='ARRAY'
               AND JSON_LENGTH(template_row.params)<>(
                 SELECT COUNT(DISTINCT param_row.param_value)
                 FROM JSON_TABLE(template_row.params,'$[*]'
                   COLUMNS(param_value varchar(255) PATH '$')) param_row)
          ), 'PASS', 'FAIL') AS result;

SELECT 'V072 partner identity schema' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_partner_account')
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_complaint' AND column_name='partner_id')
          AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='system_notify_message' AND index_name='uk_notify_rule_type_user_event')
          AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V072'), 'PASS', 'FAIL') AS result;
SELECT 'admin_user' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_users WHERE username='admin' AND deleted=b'0' AND tenant_id=1), 'PASS', 'FAIL') AS result;
SELECT 'admin_super_admin_role' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id WHERE ur.user_id=(SELECT id FROM system_users WHERE username='admin' AND tenant_id=1 AND deleted=b'0' LIMIT 1) AND r.code='super_admin' AND ur.deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'product_ref_nullable' AS check_name,
       IF((SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_intended_product' AND column_name='product_ref')='YES', 'PASS', 'FAIL') AS result;
SELECT 'lead_intended_product_active_key' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
           AND table_name='zsjos_lead_intended_product' AND column_name='active_product_ref'
           AND extra LIKE '%STORED GENERATED%')=1
          AND (SELECT CONCAT(MAX(non_unique), ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
           FROM information_schema.statistics WHERE table_schema=DATABASE()
           AND table_name='zsjos_lead_intended_product' AND index_name='uk_tenant_lead_active_product')
             = '0:tenant_id,lead_id,active_product_ref'
          AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
           AND table_name='zsjos_lead_intended_product' AND index_name='uk_tenant_lead_product'),
       'PASS', 'FAIL') AS result;
SELECT 'lead_category_empty' AS check_name,
       IF(NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_category' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'source_channel_empty' AS check_name,
       IF(NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_source_channel' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'default_assignment_rule' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_lead_assignment_rule WHERE tenant_id=1 AND code='default' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_management_menu' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6770 AND permission='zsjos:lead:query' AND parent_id=6735 AND visible=b'1' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_management_query_all_permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6771 AND permission='zsjos:lead:query-all' AND parent_id=6770 AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_management_v002' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V002'), 'PASS', 'FAIL') AS result;
SELECT 'claim_pool_menu' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6749 AND permission='' AND component='zsjos/leadClaimPool/index' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'claim_pool_action' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6772 AND permission='zsjos:lead:claim' AND parent_id=6749 AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'claim_pool_v003' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V003'), 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_schemes' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_lead_inbox_filter_scheme WHERE tenant_id=1 AND audience IN ('submitter','owner','reviewer') AND published_version=1 AND deleted=b'0')=3, 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_versions' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_lead_inbox_filter_version WHERE tenant_id=1 AND version_no=1 AND deleted=b'0')=3, 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_menu' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6773 AND permission='zsjos:lead-filter:query' AND component='zsjos/leadFilter/index' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_v005' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V005'), 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_keys_v032' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V032')
          AND NOT EXISTS (SELECT 1 FROM zsjos_lead_inbox_filter_scheme
            WHERE audience='reviewer' AND deleted=b'0'
              AND (draft_config_json LIKE '%\"key\":\"registrationReview\"%'
                OR draft_config_json LIKE '%\"key\": \"registrationReview\"%'
                OR draft_config_json LIKE '%\"key\":\"financeReview\"%'
                OR draft_config_json LIKE '%\"key\": \"financeReview\"%'
                OR published_config_json LIKE '%\"key\":\"registrationReview\"%'
                OR published_config_json LIKE '%\"key\": \"registrationReview\"%'
                OR published_config_json LIKE '%\"key\":\"financeReview\"%'
                OR published_config_json LIKE '%\"key\": \"financeReview\"%')),
           'PASS', 'FAIL') AS result;
SELECT 'lead_filter_status_v042' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V042')
          AND NOT EXISTS (SELECT 1 FROM zsjos_lead_inbox_filter_scheme
            WHERE audience IN ('submitter','owner') AND deleted=b'0'
              AND (JSON_SEARCH(draft_config_json,'one','converted') IS NOT NULL
                OR JSON_SEARCH(published_config_json,'one','converted') IS NOT NULL)),
          'PASS', 'FAIL') AS result;
SELECT 'order_lifecycle_review_v043' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V043')
          AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
               AND table_name IN ('zsjos_person_contact_claim','zsjos_order_command'))=2,
          'PASS','FAIL') AS result;
SELECT 'order_concurrency_objects_v043' AS check_name,
       IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_order' AND column_name='terminated_at')
          AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name='zsjos_order_approval_round'
               AND column_name IN ('registration_decision_idempotency_key','finance_decision_idempotency_key',
                                   'termination_idempotency_key','version'))=4
          AND (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE()
               AND ((table_name='zsjos_order' AND index_name='uk_tenant_active_repurchase')
                 OR (table_name='zsjos_order_approval_round' AND index_name IN
                    ('uk_tenant_registration_decision_key','uk_tenant_finance_decision_key','uk_tenant_termination_key'))))=4,
          'PASS','FAIL') AS result;
SELECT 'order_command_ledger_v043' AS check_name,
       IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='zsjos_order_command' AND index_name='uk_tenant_order_command_key' AND non_unique=0),
          'PASS','FAIL') AS result;
SELECT 'sales_order_supervisor_confirmation_v055' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V055')
          AND EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V061')
          AND EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                     AND table_name='zsjos_order_supervisor_confirmation')
          AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                     AND table_name='zsjos_order_approval_round' AND column_name='supervisor_confirmation_enabled')
          AND EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                     AND table_name='zsjos_order_supervisor_confirmation' AND index_name='uk_tenant_round_task')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6856
                     AND permission='zsjos:sales-order:supervisor-confirm' AND deleted=b'0'),
          'PASS','FAIL') AS result;
SELECT 'sales_order_unified_approval_entry_v076' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V076')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6810 AND name='成交订单审批'
                     AND permission='' AND path='sales-order-approvals' AND type=2 AND deleted=b'0')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=76000 AND parent_id=6810
                     AND permission='zsjos:sales-order:review' AND type=3 AND deleted=b'0')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6856 AND parent_id=6810
                     AND permission='zsjos:sales-order:supervisor-confirm' AND type=3
                     AND path='' AND component='' AND deleted=b'0')
          AND NOT EXISTS(
            SELECT 1 FROM system_role role
            WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
              AND (NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=role.id
                    AND rm.tenant_id=role.tenant_id AND rm.menu_id=6810 AND rm.deleted=b'0')
                OR NOT EXISTS(SELECT 1 FROM system_role_menu rm WHERE rm.role_id=role.id
                    AND rm.tenant_id=role.tenant_id AND rm.menu_id=6856 AND rm.deleted=b'0'))
          ), 'PASS','FAIL') AS result;
SELECT 'wecom_user_id_uniqueness_v077' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V077')
          AND (SELECT CONCAT(MAX(non_unique), ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
               FROM information_schema.statistics WHERE table_schema=DATABASE()
                AND table_name='system_users' AND index_name='uk_tenant_wecom_user_id')
                = '0:tenant_id,unique_wecom_user_id'
          AND NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                         AND table_name='system_users' AND index_name='uk_system_users_tenant_wecom')
          AND NOT EXISTS(SELECT 1 FROM system_users
                         WHERE wecom_user_id IS NOT NULL AND TRIM(wecom_user_id)='')
          AND NOT EXISTS(SELECT 1 FROM system_users WHERE wecom_user_id IS NOT NULL
                         GROUP BY tenant_id,TRIM(wecom_user_id) HAVING COUNT(*)>1),
          'PASS','FAIL') AS result;
SELECT 'unified_lead_management_scope_v078' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V078')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6770 AND path='leads/manage'
                     AND component='zsjos/lead/index' AND visible=b'1' AND deleted=b'0')
          AND (SELECT COUNT(*) FROM system_menu WHERE id IN (6778,6779) AND parent_id=6770
               AND type=3 AND path='' AND component='' AND visible=b'1' AND deleted=b'0')=2
          AND NOT EXISTS(
            SELECT 1 FROM system_role_menu rm
            JOIN system_role role ON role.id=rm.role_id AND role.tenant_id=rm.tenant_id
            JOIN system_menu menu ON menu.id=rm.menu_id AND menu.permission='zsjos:lead:query-all'
            WHERE rm.deleted=b'0' AND role.deleted=b'0'
              AND role.code IN ('sales_manager','sales_specialist'))
          AND NOT EXISTS(
            SELECT 1 FROM system_role role
            WHERE role.code='sales_manager' AND role.status=0 AND role.deleted=b'0'
              AND NOT EXISTS(
                SELECT 1 FROM system_role_menu rm
                JOIN system_menu menu ON menu.id=rm.menu_id AND menu.permission='zsjos:lead-follow-up:query'
                WHERE rm.role_id=role.id AND rm.tenant_id=role.tenant_id
                  AND rm.deleted=b'0' AND menu.deleted=b'0')),
          'PASS','FAIL') AS result;
SELECT 'unified_lead_management_visibility_v079' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V079')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6770 AND path='leads/manage'
                     AND visible=b'1' AND deleted=b'0')
          AND NOT EXISTS(
            SELECT 1
            FROM system_role_menu source
            JOIN system_menu source_menu ON source_menu.id=source.menu_id
              AND source_menu.permission IN ('zsjos:lead:query','zsjos:lead:query-all',
                                              'zsjos:lead:query-submitted','zsjos:lead:query-owned')
              AND source_menu.deleted=b'0'
            LEFT JOIN system_role_menu page_grant
              ON page_grant.role_id=source.role_id AND page_grant.tenant_id=source.tenant_id
             AND page_grant.menu_id=6770 AND page_grant.deleted=b'0'
            WHERE source.deleted=b'0' AND page_grant.role_id IS NULL),
          'PASS','FAIL') AS result;
SELECT 'crm_lifecycle_confirmed_rules_v056' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V056')
          AND EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                     AND table_name='zsjos_order_no_daily_counter')
          AND EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                     AND table_name='system_notify_business_outbox')
          AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                     AND table_name='system_notify_business_outbox' AND column_name='claim_token'),
          'PASS','FAIL') AS result;
SELECT 'lead_runtime_settings_v057' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V057')
          AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                     AND table_name='zsjos_lead_follow_up_rule'
                     AND column_name='notification_popup_duration_minutes' AND column_default='5')
          AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                     AND table_name='zsjos_lead_follow_up_rule'
                     AND column_name='duplicate_auto_resolution_enabled'
                     AND column_default IN ('b''0''','0')),
          'PASS','FAIL') AS result;
SELECT 'account_unique_generated_columns_v056' AS check_name,
       IF((SELECT CONCAT(MAX(non_unique), ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
             FROM information_schema.statistics WHERE table_schema=DATABASE()
              AND table_name='system_users' AND index_name='uk_tenant_username_binary')
              = '0:tenant_id,unique_username'
          AND (SELECT collation_name FROM information_schema.columns WHERE table_schema=DATABASE()
                AND table_name='system_users' AND column_name='unique_username')='utf8mb4_bin'
          AND (SELECT CONCAT(MAX(non_unique), ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
             FROM information_schema.statistics WHERE table_schema=DATABASE()
              AND table_name='system_users' AND index_name='uk_tenant_mobile_active')
              = '0:tenant_id,unique_mobile',
          'PASS','FAIL') AS result;
SELECT 'account_uniqueness_conflicts_v056' AS check_name,
       IF(NOT EXISTS (SELECT 1 FROM system_users GROUP BY tenant_id,BINARY username HAVING COUNT(*)>1)
          AND NOT EXISTS (SELECT 1 FROM system_users WHERE mobile<>'' GROUP BY tenant_id,mobile HAVING COUNT(*)>1)
          AND NOT EXISTS (SELECT 1 FROM system_users username_user JOIN system_users mobile_user
               ON mobile_user.tenant_id=username_user.tenant_id AND mobile_user.id<>username_user.id
              AND mobile_user.mobile<>'' AND BINARY mobile_user.mobile=BINARY username_user.username),
          'PASS','FAIL') AS result;
SELECT 'person_contact_claim_completeness_v043' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM (
              SELECT tenant_id,id person_id,CONVERT(TRIM(mobile) USING utf8mb4) COLLATE utf8mb4_bin contact_value
                FROM zsjos_person WHERE deleted=b'0' AND mobile IS NOT NULL
              UNION
              SELECT tenant_id,id,CONVERT(TRIM(wechat_id) USING utf8mb4) COLLATE utf8mb4_bin
                FROM zsjos_person WHERE deleted=b'0' AND wechat_id IS NOT NULL
            ) expected LEFT JOIN zsjos_person_contact_claim claim
              ON claim.tenant_id=expected.tenant_id AND claim.contact_value=expected.contact_value
             AND claim.person_id=expected.person_id AND claim.deleted=b'0'
            WHERE claim.id IS NULL)
          AND NOT EXISTS (
            SELECT 1 FROM zsjos_person_contact_claim claim
            LEFT JOIN (
              SELECT tenant_id,id person_id,CONVERT(TRIM(mobile) USING utf8mb4) COLLATE utf8mb4_bin contact_value
                FROM zsjos_person WHERE deleted=b'0' AND mobile IS NOT NULL
              UNION
              SELECT tenant_id,id,CONVERT(TRIM(wechat_id) USING utf8mb4) COLLATE utf8mb4_bin
                FROM zsjos_person WHERE deleted=b'0' AND wechat_id IS NOT NULL
            ) expected ON expected.tenant_id=claim.tenant_id AND expected.contact_value=claim.contact_value
                      AND expected.person_id=claim.person_id
            WHERE claim.deleted=b'0' AND (claim.person_id IS NULL OR claim.reservation_key IS NOT NULL OR expected.person_id IS NULL)),
          'PASS','FAIL') AS result;
WITH filter_documents AS (
  SELECT id,draft_config_json document FROM zsjos_lead_inbox_filter_scheme WHERE audience IN('submitter','owner') AND deleted=b'0'
  UNION ALL
  SELECT id,published_config_json FROM zsjos_lead_inbox_filter_scheme WHERE audience IN('submitter','owner') AND deleted=b'0'
), legacy_status AS (
  SELECT documents.id
  FROM filter_documents documents
  JOIN JSON_TABLE(documents.document,'$.groups[*]' COLUMNS(group_doc JSON PATH '$')) groups_json
  JOIN JSON_TABLE(groups_json.group_doc,'$.conditions[*]' COLUMNS(condition_doc JSON PATH '$')) conditions_json
  JOIN JSON_TABLE(conditions_json.condition_doc,'$.values[*]' COLUMNS(condition_value varchar(64) PATH '$')) values_json
  WHERE JSON_UNQUOTE(JSON_EXTRACT(conditions_json.condition_doc,'$.field'))='status'
    AND values_json.condition_value='converted'
  UNION ALL
  SELECT documents.id
  FROM filter_documents documents
  JOIN JSON_TABLE(documents.document,'$.groups[*]' COLUMNS(group_doc JSON PATH '$')) groups_json
  JOIN JSON_TABLE(groups_json.group_doc,'$.options[*]' COLUMNS(option_doc JSON PATH '$')) options_json
  JOIN JSON_TABLE(options_json.option_doc,'$.conditions[*]' COLUMNS(condition_doc JSON PATH '$')) conditions_json
  JOIN JSON_TABLE(conditions_json.condition_doc,'$.values[*]' COLUMNS(condition_value varchar(64) PATH '$')) values_json
  WHERE JSON_UNQUOTE(JSON_EXTRACT(conditions_json.condition_doc,'$.field'))='status'
    AND values_json.condition_value='converted'
), legacy_option AS (
  SELECT documents.id
  FROM filter_documents documents
  JOIN JSON_TABLE(documents.document,'$.groups[*]' COLUMNS(group_doc JSON PATH '$')) groups_json
  JOIN JSON_TABLE(groups_json.group_doc,'$.options[*]' COLUMNS(option_doc JSON PATH '$')) options_json
  WHERE JSON_UNQUOTE(JSON_EXTRACT(options_json.option_doc,'$.key'))='converted'
    AND JSON_UNQUOTE(JSON_EXTRACT(options_json.option_doc,'$.label'))='已进入转化'
)
SELECT 'lead_filter_status_v043_structured' AS check_name,
       IF(NOT EXISTS(SELECT 1 FROM legacy_status) AND NOT EXISTS(SELECT 1 FROM legacy_option),'PASS','FAIL') AS result;
SELECT 'default_employee_avatar_v044' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V044')
          AND (SELECT COUNT(*) FROM infra_config
               WHERE config_key='zsjos.user.default-avatar' AND type=1 AND visible=b'1' AND deleted=b'0')=1,
          'PASS', 'FAIL') AS result;
SELECT 'default_follow_up_rule' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_lead_follow_up_rule WHERE tenant_id=1 AND code='default' AND first_follow_up_timeout_minutes=1440 AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'sales_accept_permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code='sales_specialist' AND m.permission='zsjos:lead:accept' AND rm.deleted=b'0' AND r.deleted=b'0' AND m.deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_follow_up_rule_v006' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V006'), 'PASS', 'FAIL') AS result;
SELECT 'lead_unified_management_scopes' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6770 AND path='leads/manage' AND visible=b'1' AND deleted=b'0')
          AND (SELECT COUNT(*) FROM system_menu WHERE id IN (6778,6779) AND type=3 AND parent_id=6770
                 AND permission IN ('zsjos:lead:query-submitted','zsjos:lead:query-owned')
                 AND visible=b'1' AND path='' AND component='' AND deleted=b'0')=2, 'PASS', 'FAIL') AS result;
SELECT 'lead_submitted_inbox_grant' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_role_menu source JOIN system_menu source_menu ON source_menu.id=source.menu_id AND source_menu.permission='zsjos:lead:submit' JOIN system_role_menu target ON target.role_id=source.role_id AND target.tenant_id=source.tenant_id JOIN system_menu target_menu ON target_menu.id=target.menu_id AND target_menu.permission='zsjos:lead:query-submitted' WHERE source.deleted=b'0' AND target.deleted=b'0' AND source_menu.deleted=b'0' AND target_menu.deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_owned_inbox_grant' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_role_menu source JOIN system_menu source_menu ON source_menu.id=source.menu_id AND source_menu.permission IN ('zsjos:lead:claim','zsjos:lead:accept') JOIN system_role_menu target ON target.role_id=source.role_id AND target.tenant_id=source.tenant_id JOIN system_menu target_menu ON target_menu.id=target.menu_id AND target_menu.permission='zsjos:lead:query-owned' WHERE source.deleted=b'0' AND target.deleted=b'0' AND source_menu.deleted=b'0' AND target_menu.deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_inbox_v007' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V007'), 'PASS', 'FAIL') AS result;
SELECT 'lead_follow_up_schema' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead_follow_up_record','zsjos_lead_follow_up_image'))=2, 'PASS', 'FAIL') AS result;
SELECT 'lead_follow_up_dicts' AS check_name,
       IF((SELECT COUNT(*) FROM system_dict_type WHERE type IN ('zsjos_lead_follow_up_method','zsjos_lead_follow_up_result','zsjos_lead_follow_up_quick_note') AND deleted=b'0')=3, 'PASS', 'FAIL') AS result;
SELECT 'lead_invalid_remark_template_dict' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_dict_type WHERE type='zsjos_lead_invalid_remark_template' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'today_tasks_menu' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6780 AND permission='zsjos:business-task:query' AND visible=b'1' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_follow_up_v008' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V008'), 'PASS', 'FAIL') AS result;
SELECT 'business_notify_rule_schema' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='system_notify_rule'), 'PASS', 'FAIL') AS result;
SELECT 'business_notify_snapshot_columns' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notify_message' AND column_name IN ('template_title','template_summary','notify_rule_id','scene_code','source_event_key','action_type','biz_type','biz_id'))=8, 'PASS', 'FAIL') AS result;
SELECT 'business_notify_lead_templates' AS check_name,
       IF((SELECT COUNT(*) FROM (
         SELECT 'ZSJOS_LEAD_CREATED' code,'zsjos.lead.created' scene_code
         UNION ALL SELECT 'ZSJOS_LEAD_ACTIVATED','zsjos.lead.activated'
         UNION ALL SELECT 'ZSJOS_LEAD_PENDING_ASSIGNMENT','zsjos.lead.assigned'
         UNION ALL SELECT 'ZSJOS_LEAD_REASSIGNED','zsjos.lead.reassigned'
         UNION ALL SELECT 'ZSJOS_LEAD_ACCEPTED','zsjos.lead.accepted'
         UNION ALL SELECT 'ZSJOS_LEAD_REJECTED','zsjos.lead.rejected'
         UNION ALL SELECT 'ZSJOS_LEAD_EXPIRED','zsjos.lead.expired'
         UNION ALL SELECT 'ZSJOS_LEAD_PUBLIC_POOL','zsjos.lead.public_pool'
         UNION ALL SELECT 'ZSJOS_LEAD_CLAIMED','zsjos.lead.claimed'
         UNION ALL SELECT 'ZSJOS_LEAD_TRANSFERRED','zsjos.lead.transferred'
         UNION ALL SELECT 'ZSJOS_LEAD_FOLLOW_UP_RECORDED','zsjos.lead.follow_up_recorded'
         UNION ALL SELECT 'ZSJOS_LEAD_CATEGORY_CHANGED','zsjos.lead.category_changed'
         UNION ALL SELECT 'ZSJOS_LEAD_QUALIFICATION_SUSPENDED','zsjos.lead.qualification_suspended'
         UNION ALL SELECT 'ZSJOS_LEAD_QUALIFICATION_RESTORED','zsjos.lead.qualification_restored'
         UNION ALL SELECT 'ZSJOS_LEAD_QUALIFICATION_TRANSFERRED','zsjos.lead.qualification_transferred'
         UNION ALL SELECT 'ZSJOS_LEAD_QUALIFICATION_RECYCLED','zsjos.lead.qualification_recycled'
         UNION ALL SELECT 'ZSJOS_LEAD_QUALIFICATION_RELEASED','zsjos.lead.qualification_released'
         UNION ALL SELECT 'ZSJOS_LEAD_APPEAL_SUBMITTED','zsjos.lead.appeal_submitted'
         UNION ALL SELECT 'ZSJOS_LEAD_APPEAL_OVERTURNED','zsjos.lead.appeal_overturned'
         UNION ALL SELECT 'ZSJOS_LEAD_APPEAL_UPHELD','zsjos.lead.appeal_upheld'
       ) expected JOIN system_notify_template template
         ON template.code=expected.code AND template.scene_code=expected.scene_code AND template.deleted=b'0')=20,
       'PASS', 'FAIL') AS result;
SELECT 'business_notify_v011' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V011'), 'PASS', 'FAIL') AS result;
SELECT 'business_notify_templates_v016' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V016'), 'PASS', 'FAIL') AS result;
SELECT 'lead_number_user_visible_contract_v067' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V067'), 'PASS', 'FAIL') AS result;
SELECT 'lead_default_notify_templates_use_lead_no' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_notify_template
         WHERE deleted=b'0' AND scene_code LIKE 'zsjos.lead.%'
           AND ((creator=updater
                 AND creator IN ('quick-init','migration-V011','migration-V016','migration-V031',
                                 'migration-V040','migration-V056','migration-V066'))
                OR updater='migration-V067')
           AND (title LIKE '%{{lead.id}}%' OR summary LIKE '%{{lead.id}}%'
                OR content LIKE '%{{lead.id}}%' OR params LIKE '%"lead.id"%')
       ), 'PASS', 'FAIL') AS result;
SELECT 'lead_invalid_remark_v017' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V017'), 'PASS', 'FAIL') AS result;
SELECT 'lead_actions_v018' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V018'), 'PASS', 'FAIL') AS result;
SELECT 'historical_valid_leads_v019' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V019'), 'PASS', 'FAIL') AS result;
SELECT 'unified_schema_migration_v020' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V020'), 'PASS', 'FAIL') AS result;
SELECT 'workbench_foundation_v022' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V022'), 'PASS', 'FAIL') AS result;
SELECT 'work_plan_query_permission_v033' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V033'), 'PASS', 'FAIL') AS result;
SELECT 'business_task_workbench_columns' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
           AND table_name='zsjos_business_task'
           AND column_name IN ('title_snapshot','summary_snapshot','action_code','remind_at'))=4, 'PASS', 'FAIL') AS result;
SELECT 'work_plan_tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
           AND table_name IN ('zsjos_work_plan','zsjos_work_task','zsjos_work_report','zsjos_work_plan_summary',
                              'zsjos_work_attachment','zsjos_work_plan_field_definition','zsjos_work_field_value','zsjos_work_change'))=8, 'PASS', 'FAIL') AS result;
SELECT 'work_plan_config_tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
           AND table_name IN ('zsjos_work_plan_type','zsjos_work_plan_template','zsjos_work_plan_template_version',
                              'zsjos_work_plan_template_field','zsjos_work_plan_template_scope','zsjos_work_plan_template_task'))=6, 'PASS', 'FAIL') AS result;
SELECT 'work_plan_menu_permissions' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 6900 AND 6917 AND deleted=b'0'
           AND permission IN ('zsjos:work-plan:query','zsjos:work-plan:create','zsjos:work-plan:update',
                              'zsjos:work-plan:publish','zsjos:work-plan:assign','zsjos:work-plan:complete',
                              'zsjos:work-plan:review','zsjos:work-plan:cancel','zsjos:work-plan:decompose','zsjos:work-plan:close',
                              'zsjos:work-plan-config:query','zsjos:work-plan-config:create','zsjos:work-plan-config:update',
                              'zsjos:work-plan-config:publish','zsjos:work-plan-config:disable','zsjos:work-plan:export'))=16, 'PASS', 'FAIL') AS result;
SELECT 'work_plan_query_permission_split' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6900 AND name='工作计划' AND permission='' AND type=2 AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=6908 AND parent_id=6900 AND name='查看工作计划'
                      AND permission='zsjos:work-plan:query' AND type=3 AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_intended_product_active_key_v021' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V021'), 'PASS', 'FAIL') AS result;
SELECT 'sales_order_dual_approval_v023' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V023'), 'PASS', 'FAIL') AS result;
SELECT 'zsjos_bpm_readonly_forms_v024' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V024'), 'PASS', 'FAIL') AS result;
SELECT 'sales_order_workbench_views_v025' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V025'), 'PASS', 'FAIL') AS result;
SELECT 'sales_order_v025_reason_and_index' AS check_name,
       IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
            AND table_name='zsjos_order_approval_round' AND column_name='decision_reason')
          AND EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
            AND table_name='zsjos_order' AND index_name='idx_tenant_submitter_status_submitted'), 'PASS', 'FAIL') AS result;
SELECT 'sales_order_v025_menu' AS check_name,
       IF(EXISTS(SELECT 1 FROM system_menu WHERE id=6813 AND permission='zsjos:sales-order:query-own'
            AND path='sales-orders/my' AND sort=17 AND deleted=b'0')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6810 AND sort=18 AND deleted=b'0')
          AND EXISTS(SELECT 1 FROM system_menu WHERE id=6804 AND sort=19 AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'zsjos_bpm_readonly_forms' AS check_name,
       IF(NOT EXISTS (
         SELECT 1
         FROM system_tenant tenant
         CROSS JOIN (
           SELECT 'zsjos-system-form:lead-appeal-review' marker,4 expected_fields
           UNION ALL
           SELECT 'zsjos-system-form:sales-order-dual-approval',3
         ) expected
         LEFT JOIN bpm_form form
           ON form.tenant_id=tenant.id AND form.remark=expected.marker AND form.deleted=b'0'
         WHERE tenant.deleted=b'0' AND tenant.status=0
         GROUP BY tenant.id,expected.marker,expected.expected_fields
         HAVING COUNT(form.id)<>1
            OR MIN(form.status)<>0
            OR MIN(JSON_VALID(form.conf))<>1
            OR MIN(JSON_VALID(form.fields))<>1
            OR MIN(JSON_LENGTH(form.fields))<>expected.expected_fields
            OR COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[0]')),'$.props.disabled'))='true'),0)<>1
            OR COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[0]')),'$.props.readonly'))='true'),0)<>1
            OR COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[1]')),'$.props.disabled'))='true'),0)<>1
            OR COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[1]')),'$.props.readonly'))='true'),0)<>1
            OR COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[2]')),'$.props.disabled'))='true'),0)<>1
            OR COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[2]')),'$.props.readonly'))='true'),0)<>1
            OR (expected.expected_fields=4 AND COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[3]')),'$.props.disabled'))='true'),0)<>1)
            OR (expected.expected_fields=4 AND COALESCE(MIN(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(form.fields,'$[3]')),'$.props.readonly'))='true'),0)<>1)
       ),'PASS','FAIL') AS result;
SELECT 'zsjos_default_bpm_forms_use_lead_no' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM bpm_form
         WHERE deleted=b'0'
           AND remark IN ('zsjos-system-form:lead-appeal-review',
                          'zsjos-system-form:sales-order-dual-approval')
           AND ((creator=updater AND creator IN ('quick-init','migration-V024'))
                OR updater='migration-V067')
           AND (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(fields,'$[1]')),'$.field')),'')<>'leadNo'
                OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(fields,'$[1]')),'$.title')),'')<>'客资编号')
       ),'PASS','FAIL') AS result;
SELECT 'sales_order_v023_columns' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order'
           AND column_name IN ('buyer_name','student_name','student_nature','student_mobile','student_wechat_id',
             'province_code','province_name','city_code','city_name','agreed_exam_time','class_type','service_period',
             'student_source','customer_paid_at','fee_mode','payment_method','remark','student_special_requirements',
             'material_delivery_contact','payment_voucher_refs','submission_idempotency_key','active_lead_id'))=22,
          'PASS','FAIL') AS result;
SELECT 'sales_order_v023_approval_config' AS check_name,
       IF(EXISTS(SELECT 1 FROM zsjos_order_approval_config WHERE tenant_id=1 AND registration_dept_id=1030 AND finance_dept_id=1040 AND deleted=b'0'),
          'PASS','FAIL') AS result;
SELECT 'sales_order_v023_dictionaries' AS check_name,
       IF((SELECT COUNT(DISTINCT type) FROM system_dict_type WHERE type IN ('zsjos_order_student_nature','zsjos_order_service_period',
           'zsjos_order_student_source','zsjos_order_fee_mode','zsjos_order_payment_method') AND deleted=b'0')=5,
          'PASS','FAIL') AS result;
SELECT 'student_contact_chain' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
             AND table_name='zsjos_service_relation'
             AND column_name IN ('acceptance_status','accepted_by_user_id','accepted_at','content_director_user_id','career_planner_user_id'))=5
           AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
              AND table_name IN ('zsjos_student_contact_config_version','zsjos_student_contact_record',
                                 'zsjos_student_contact_config_command','zsjos_student_contact_extension',
                                 'zsjos_student_collaborator_assignment_log'))=5
           AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
              AND table_name='zsjos_student_contact_extension' AND column_name='withdrawal_idempotency_key')
           AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
              AND ((table_name='zsjos_student_contact_config_command' AND column_name='request_fingerprint')
                OR (table_name='zsjos_student_contact_record' AND column_name='request_fingerprint')))=2
           AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
              AND table_name='zsjos_business_task_notify_stage' AND column_name='task_version')
          AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V094')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V094')
          AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V095')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V095')
          AND NOT EXISTS (
            SELECT 1 FROM system_tenant tenant
            LEFT JOIN bpm_form form ON form.tenant_id=tenant.id
              AND form.remark='zsjos-system-form:student-contact-extension' AND form.deleted=b'0'
            WHERE tenant.deleted=b'0' AND tenant.status=0
            GROUP BY tenant.id HAVING COUNT(form.id)<>1
          )
          AND (SELECT COUNT(*) FROM system_dict_type WHERE type IN
             ('zsjos_student_contact_unsuccessful_reason','zsjos_student_contact_extension_reason') AND deleted=b'0')=2
          AND (SELECT COUNT(DISTINCT code) FROM zsjos_user_relation_scene WHERE code IN
             ('registration_manager_study_planner','registration_specialist_study_planner',
              'study_planner_content_director','study_planner_career_planner') AND deleted=b'0')=4,
          'PASS','FAIL') AS result
UNION ALL
SELECT 'module_schema_versions' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core'
            AND version IN ('V001','V017','V018','V019','V020','V021','V022','V023','V024','V025','V026','V033','V034','V035','V036','V037','V038','V039','V040','V041','V042','V043','V044','V045'))=24,
          'PASS', 'FAIL') AS result;
SELECT 'enabled_crm_schema' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
           AND table_name IN ('crm_owner_record','crm_performance_config'))=2, 'PASS', 'FAIL') AS result;
SELECT 'system_area_v013' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V013'), 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_v015' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V015'), 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_columns' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
           AND table_name='zsjos_lead_appeal' AND column_name IN ('round_no','review_stage','status','evidence_refs',
           'invalid_evidence_refs_snapshot','process_instance_id','decision_evidence_refs',
           'submission_idempotency_key','decision_idempotency_key','owner_user_id_snapshot',
           'owner_dept_id_snapshot','reviewer_dept_id_snapshot','reviewer_user_ids_snapshot'))=13, 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_reviewer_snapshots' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM zsjos_lead_appeal
         WHERE deleted=b'0' AND status IN ('sales_manager_reviewing','quality_reviewing','chairman_reviewing')
           AND reviewer_user_ids_snapshot IS NOT NULL
           AND (JSON_VALID(reviewer_user_ids_snapshot)<>1
                OR JSON_TYPE(reviewer_user_ids_snapshot)<>'ARRAY'
                OR JSON_LENGTH(reviewer_user_ids_snapshot)=0)
       ), 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_indexes' AS check_name,
       IF((SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE()
           AND table_name='zsjos_lead_appeal' AND index_name IN ('uk_tenant_lead_round',
           'uk_tenant_appeal_submit_key','uk_tenant_appeal_decision_key','idx_tenant_process'))=4, 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_menu' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:lead:appeal:query' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_boss_role' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_role WHERE code='boss' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_appeal_status_dict' AS check_name,
       IF((SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_lead_appeal_status' AND deleted=b'0')=6, 'PASS', 'FAIL') AS result;
SELECT 'dual_frontend_workbench_menu_components' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu
           WHERE parent_id=6735 AND type=2 AND deleted=b'0'
             AND ((id=6736 AND component='zsjos/leadSubmission/index')
               OR (id=6770 AND component='zsjos/lead/index')
               OR (id=6780 AND component='zsjos/todayTask/index')
               OR (id=6840 AND component='zsjos/leadDuplicateReview/index')
               OR (id=6844 AND component='zsjos/leadSelfSourced/index')
               OR (id=6850 AND component='zsjos/personnel/index')
               OR (id=6852 AND component='zsjos/partner/index')
               OR (id=6848 AND component='zsjos/leadComplaint/index')
               OR (id=6849 AND component='zsjos/externalRepurchase/index')))=9, 'PASS', 'FAIL') AS result;
SELECT 'account_personnel_partner_permissions' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND id IN (6850,6851,6852,6853,6854,6855)
             AND permission IN ('zsjos:personnel:query','zsjos:personnel:update-state','zsjos:partner:query',
                                'zsjos:partner:create','zsjos:partner:update-state','zsjos:partner:convert'))=6,
          'PASS', 'FAIL') AS result;
SELECT 'maintenance_mode_config' AS check_name,
       IF((SELECT COUNT(*) FROM infra_config WHERE config_key='zsjos.system.maintenance-enabled'
             AND type=1 AND deleted=b'0')=1, 'PASS', 'FAIL') AS result;
SELECT 'maintenance_mode_menu' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND
             ((id=6860 AND component='system/maintenance/index')
               OR (id=6861 AND permission='system:maintenance:update')))=2, 'PASS', 'FAIL') AS result;
SELECT 'readonly_impersonation_tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
             AND table_name IN ('zsjos_impersonation_session','zsjos_impersonation_request_log',
                                'zsjos_business_audit_log','zsjos_export_task'))=4,
          'PASS', 'FAIL') AS result;
SELECT 'readonly_impersonation_permissions' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND
             ((id=6870 AND permission='zsjos:impersonation:query')
               OR (id=6871 AND permission='zsjos:impersonation:start')))=2, 'PASS', 'FAIL') AS result;
SELECT 'async_export_permissions' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND
             ((id=6872 AND permission='zsjos:export:query')
               OR (id=6873 AND permission='zsjos:export:lead')
               OR (id=6874 AND permission='zsjos:export:order')
               OR (id=6875 AND permission='zsjos:export:cashback')
               OR (id=6876 AND permission='zsjos:export:withdrawal')
               OR (id=6879 AND permission='zsjos:export:finance-order')))=6,
          'PASS', 'FAIL') AS result;
SELECT 'business_audit_permissions' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND
             ((id=6877 AND permission='zsjos:audit:query')
               OR (id=6878 AND permission='zsjos:audit:query-impersonation')))=2,
          'PASS', 'FAIL') AS result;
SELECT 'system_area_official_count' AS check_name,
       IF((SELECT COUNT(*) FROM system_area WHERE selection_code<>'OTHER' AND deleted=b'0')=3879, 'PASS', 'FAIL') AS result;
SELECT 'system_area_other_count' AS check_name,
       IF((SELECT COUNT(*) FROM system_area WHERE selection_code='OTHER' AND deleted=b'0')=34, 'PASS', 'FAIL') AS result;
SELECT 'system_area_parent_integrity' AS check_name,
       IF(NOT EXISTS (
         SELECT 1
         FROM system_area child
         LEFT JOIN system_area parent ON parent.id=child.parent_id AND parent.deleted=b'0'
         WHERE child.parent_id<>0 AND child.deleted=b'0' AND parent.id IS NULL
       ), 'PASS', 'FAIL') AS result;
SELECT 'system_area_direct_province_leaves' AS check_name,
       IF((SELECT COUNT(*) FROM system_area WHERE id IN (810000,820000) AND type=2
             AND leaf_selectable=b'1' AND status=0 AND deleted=b'0')=2, 'PASS', 'FAIL') AS result;
SELECT 'system_area_initial_pinyin_order' AS check_name,
       IF(NOT EXISTS (
         SELECT 1
         FROM system_area earlier
         JOIN system_area later ON later.parent_id=earlier.parent_id
           AND later.selection_code<>'OTHER' AND later.deleted=b'0'
         WHERE earlier.selection_code<>'OTHER' AND earlier.deleted=b'0'
           AND (CONVERT(earlier.name USING gbk) < CONVERT(later.name USING gbk)
             OR (CONVERT(earlier.name USING gbk) = CONVERT(later.name USING gbk)
               AND earlier.id < later.id))
           AND earlier.sort >= later.sort
       ), 'PASS', 'FAIL') AS result;
SELECT 'system_area_other_last' AS check_name,
       IF(NOT EXISTS (
         SELECT 1
         FROM system_area other_area
         JOIN system_area sibling ON sibling.parent_id=other_area.parent_id AND sibling.deleted=b'0'
         WHERE other_area.selection_code='OTHER' AND other_area.deleted=b'0'
           AND (sibling.sort>other_area.sort OR (sibling.sort=other_area.sort AND sibling.id>other_area.id))
       ), 'PASS', 'FAIL') AS result;

SELECT expected.table_name,
       IF(actual.table_name IS NULL, 'MISSING', 'PRESENT') AS result
FROM (
  SELECT 'system_users' table_name UNION ALL SELECT 'system_dept' UNION ALL SELECT 'system_post'
  UNION ALL SELECT 'system_role' UNION ALL SELECT 'system_menu' UNION ALL SELECT 'bpm_category'
  UNION ALL SELECT 'pay_app' UNION ALL SELECT 'crm_customer' UNION ALL SELECT 'ai_model'
  UNION ALL SELECT 'zsjos_lead' UNION ALL SELECT 'zsjos_lead_no_daily_counter'
  UNION ALL SELECT 'zsjos_product' UNION ALL SELECT 'zsjos_product_sku'
  UNION ALL SELECT 'zsjos_lead_inbox_filter_scheme' UNION ALL SELECT 'zsjos_lead_inbox_filter_version'
  UNION ALL SELECT 'zsjos_lead_follow_up_rule' UNION ALL SELECT 'zsjos_business_task'
  UNION ALL SELECT 'zsjos_business_task_notify_stage'
  UNION ALL SELECT 'zsjos_lead_follow_up_record' UNION ALL SELECT 'zsjos_lead_follow_up_image'
  UNION ALL SELECT 'zsjos_lead_aging_pool_cycle' UNION ALL SELECT 'zsjos_lead_aging_pool_event'
  UNION ALL SELECT 'zsjos_lead_aging_pool_notify_stage'
  UNION ALL SELECT 'system_notify_rule' UNION ALL SELECT 'system_area'
  UNION ALL SELECT 'crm_owner_record' UNION ALL SELECT 'crm_performance_config'
  UNION ALL SELECT 'zsjos_work_plan' UNION ALL SELECT 'zsjos_work_task'
  UNION ALL SELECT 'zsjos_work_report' UNION ALL SELECT 'zsjos_work_plan_summary'
  UNION ALL SELECT 'zsjos_work_attachment' UNION ALL SELECT 'zsjos_work_plan_field_definition'
  UNION ALL SELECT 'zsjos_work_field_value' UNION ALL SELECT 'zsjos_work_change'
  UNION ALL SELECT 'zsjos_module_schema_version'
  UNION ALL SELECT 'zsjos_cashback'
  UNION ALL SELECT 'zsjos_order_no_daily_counter'
  UNION ALL SELECT 'system_notify_business_outbox'
) expected
LEFT JOIN information_schema.tables actual
  ON actual.table_schema=DATABASE() AND actual.table_name=expected.table_name;

SELECT 'cashback_rule_columns' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
             AND ((table_name='zsjos_product' AND column_name IN ('valid_cashback_amount','deal_cashback_rate'))
               OR (table_name='zsjos_product_category' AND column_name IN ('default_valid_cashback_amount','default_deal_cashback_rate'))))=4,
          'PASS','FAIL') AS result;
SELECT 'cashback_permissions_ungranted' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND id IN (6880,6881))=2,'PASS','FAIL') AS result;
SELECT 'V052 withdrawal tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_partner_bank_card','zsjos_withdrawal','zsjos_withdrawal_item'))=3,'PASS','FAIL') AS result;
SELECT 'V052 withdrawal active uniqueness' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_withdrawal_item' AND index_name='uk_active_cashback')>0,'PASS','FAIL') AS result;
SELECT 'V052 withdrawal menus' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND id BETWEEN 6890 AND 6895)=6,'PASS','FAIL') AS result;
SELECT 'V053 withdrawal finance query permission' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND id=6896
             AND permission='zsjos:withdrawal:finance-query')=1,'PASS','FAIL') AS result;
SELECT 'V053 withdrawal finance query permission unique' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0'
             AND permission='zsjos:withdrawal:finance-query')=1,'PASS','FAIL') AS result;
SELECT 'V054 Lead business number schema' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
             AND table_name='zsjos_lead' AND column_name='lead_no' AND is_nullable='NO')=1
          AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
             AND table_name='zsjos_lead_no_daily_counter')=1
          AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
             AND table_name='zsjos_lead' AND index_name='uk_tenant_lead_no')>0,
          'PASS','FAIL') AS result;
SELECT 'V054 Lead business number data' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_lead WHERE lead_no IS NULL OR lead_no NOT REGEXP '^KZ[0-9]{18}$')=0
          AND (SELECT COUNT(*) FROM (SELECT tenant_id,lead_no FROM zsjos_lead
               GROUP BY tenant_id,lead_no HAVING COUNT(*)>1) duplicates)=0,
          'PASS','FAIL') AS result;
SELECT 'V054 Lead business number counters' AS check_name,
       IF(NOT EXISTS (
         SELECT 1
         FROM zsjos_lead_no_daily_counter counter_row
         LEFT JOIN zsjos_lead latest ON latest.tenant_id=counter_row.tenant_id
           AND DATE(latest.submitted_at)=counter_row.sequence_date
         LEFT JOIN zsjos_lead later ON later.tenant_id=latest.tenant_id
           AND DATE(later.submitted_at)=DATE(latest.submitted_at)
           AND (later.submitted_at>latest.submitted_at
             OR (later.submitted_at=latest.submitted_at AND later.id>latest.id))
         WHERE later.id IS NULL AND (latest.id IS NULL
           OR counter_row.current_value<>CAST(SUBSTRING(latest.lead_no,17) AS UNSIGNED))
       ) AND NOT EXISTS (
         SELECT 1
         FROM (
           SELECT tenant_id, DATE(submitted_at) sequence_date
           FROM zsjos_lead
           GROUP BY tenant_id, DATE(submitted_at)
         ) allocated
         LEFT JOIN zsjos_lead_no_daily_counter counter_row
           ON counter_row.tenant_id=allocated.tenant_id
          AND counter_row.sequence_date=allocated.sequence_date
         WHERE counter_row.id IS NULL
       ), 'PASS','FAIL') AS result;
SELECT 'V063 partner role' AS check_name,
       IF((SELECT COUNT(*) FROM system_role WHERE code='part_time_partner' AND deleted=b'0')>0,'PASS','FAIL') AS result;
SELECT 'V069 invalid partner admin route retired' AS check_name,
       IF(NOT EXISTS (SELECT 1 FROM system_menu
                      WHERE path='partner-portal' AND component_name='ZsjosPartnerPortal' AND deleted=b'0'),
          'PASS','FAIL') AS result;
SELECT 'V071 exact partner permissions' AS check_name,
       IF(NOT EXISTS (
         SELECT r.id FROM system_role r
         LEFT JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
         LEFT JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.permission LIKE 'zsjos:%'
         WHERE r.code='part_time_partner' AND r.deleted=b'0'
         GROUP BY r.id
         HAVING COUNT(DISTINCT m.permission)<>10
            OR COUNT(DISTINCT CASE WHEN m.permission IN
              ('zsjos:partner:self-query','zsjos:lead:submit','zsjos:lead:query-submitted',
               'zsjos:lead:submitter-supplement','zsjos:lead:urge','zsjos:lead-complaint:create',
               'zsjos:lead:appeal:create','zsjos:cashback:my-query',
               'zsjos:withdrawal:my-query','zsjos:withdrawal:apply') THEN m.permission END)<>10
       ), 'PASS','FAIL') AS result;
SELECT 'V071 exact finance permissions' AS check_name,
       IF(NOT EXISTS (
         SELECT r.id FROM system_role r
         LEFT JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
         LEFT JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.permission LIKE 'zsjos:%'
         WHERE r.code IN ('finance_manager','finance_specialist') AND r.deleted=b'0'
         GROUP BY r.id
         HAVING COUNT(DISTINCT m.permission)<>11
            OR COUNT(DISTINCT CASE WHEN m.permission IN
              ('zsjos:sales-order:query','zsjos:sales-order:review','zsjos:cashback:finance-query',
               'zsjos:withdrawal:finance-query','zsjos:withdrawal:review','zsjos:withdrawal:payout',
               'zsjos:export:query','zsjos:export:order','zsjos:export:finance-order',
               'zsjos:export:cashback','zsjos:export:withdrawal') THEN m.permission END)<>11
            OR SUM(CASE WHEN m.permission='zsjos:export:lead' THEN 1 ELSE 0 END)>0
       ), 'PASS','FAIL') AS result;
SELECT 'V071 administrator finance separation' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_role r
         JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
         JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0'
         WHERE r.code='system_administrator' AND r.deleted=b'0'
           AND m.permission IN ('zsjos:sales-order:review','zsjos:cashback:finance-query',
                                'zsjos:withdrawal:finance-query','zsjos:withdrawal:review',
                                'zsjos:withdrawal:payout','zsjos:export:order',
                                'zsjos:export:finance-order','zsjos:export:cashback',
                                'zsjos:export:withdrawal')
       ) AND NOT EXISTS (
         SELECT r.id FROM system_role r
         LEFT JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
         LEFT JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0'
         WHERE r.code='system_administrator' AND r.deleted=b'0'
         GROUP BY r.id
         HAVING SUM(CASE WHEN m.permission='zsjos:withdrawal:admin-query' THEN 1 ELSE 0 END)<>1
            OR SUM(CASE WHEN m.permission='zsjos:export:lead' THEN 1 ELSE 0 END)<>1
       ), 'PASS','FAIL') AS result;
SELECT 'V071 zero-ZSJOS roles' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_role r
         JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
         JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.permission LIKE 'zsjos:%'
         WHERE r.deleted=b'0' AND r.code IN
           ('center_head','content_director','filming_editor','study_planner','academic_specialist',
            'delivery_manager','exam_manager','exam_specialist','career_planner','career_manager',
            'ip_teacher','product_rd_head','teaching_assistant','recruitment_manager',
            'recruitment_specialist','hr_specialist','admin_manager','admin_specialist')
       ), 'PASS','FAIL') AS result;
SELECT 'V071 no duplicate role permissions' AS check_name,
       IF(NOT EXISTS (
         SELECT rm.role_id,rm.tenant_id,m.permission
         FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0'
         WHERE rm.deleted=b'0' AND m.permission<>''
         GROUP BY rm.role_id,rm.tenant_id,m.permission HAVING COUNT(*)>1
       ), 'PASS','FAIL') AS result;
SELECT 'V071 app-only permission placement' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:partner:self-query'
                  AND parent_id=0 AND type=3 AND path='' AND component='' AND deleted=b'0')
          AND NOT EXISTS (
            SELECT 1 FROM system_menu child
            LEFT JOIN system_menu parent ON parent.id=child.parent_id AND parent.deleted=b'0'
            WHERE child.deleted=b'0' AND child.permission IN
              ('zsjos:partner:self-query','zsjos:lead:submit','zsjos:lead:query-submitted',
               'zsjos:lead:submitter-supplement','zsjos:lead:urge','zsjos:lead-complaint:create',
               'zsjos:lead:appeal:create','zsjos:cashback:my-query',
               'zsjos:withdrawal:my-query','zsjos:withdrawal:apply')
              AND child.parent_id<>0 AND parent.id IS NULL
          ),'PASS','FAIL') AS result;
SELECT 'V071 active menu parent integrity' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_menu child
         LEFT JOIN system_menu parent ON parent.id=child.parent_id AND parent.deleted=b'0'
         WHERE child.deleted=b'0' AND child.parent_id<>0 AND parent.id IS NULL
          ),'PASS','FAIL') AS result;
SELECT 'V073 registration fulfillment tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
             AND table_name IN ('zsjos_registration_case','zsjos_registration_case_checklist_item',
               'zsjos_registration_checklist_template','zsjos_registration_checklist_version',
               'zsjos_registration_checklist_template_item','zsjos_registration_item',
               'zsjos_service_relation','zsjos_registration_command'))=8,'PASS','FAIL') AS result;
SELECT 'V073 fixed planner checklist item' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_registration_checklist_template_item
             WHERE item_key='study_planner' AND item_type='study_planner'
               AND enabled=b'1' AND system_required=b'1' AND deleted=b'0') >=
          (SELECT COUNT(*) FROM system_tenant WHERE status=0 AND deleted=b'0'),'PASS','FAIL') AS result;
SELECT 'V073 registration menus' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE id IN (73000,73010,73020)
             AND deleted=b'0')=3,'PASS','FAIL') AS result;
SELECT 'V073 registration menus under workbench' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu child
             JOIN system_menu parent ON parent.id=child.parent_id AND parent.deleted=b'0'
            WHERE child.id IN (73000,73010,73020) AND child.deleted=b'0'
              AND parent.path='/zsjos' AND parent.parent_id=0)=3,'PASS','FAIL') AS result;
SELECT 'V074 registration notification template' AS check_name,
       IF((SELECT COUNT(*) FROM system_notify_template
             WHERE code='ZSJOS_REGISTRATION_TASK_CREATED'
               AND scene_code='zsjos.registration.task_created' AND deleted=b'0')=1,'PASS','FAIL') AS result;
SELECT 'V074 registration notification rules' AS check_name,
       IF((SELECT COUNT(*) FROM system_notify_rule
             WHERE scene_code='zsjos.registration.task_created'
               AND creator='migration-V074' AND deleted=b'0')=
           (SELECT COUNT(*) FROM system_tenant WHERE deleted=b'0'),'PASS','FAIL') AS result;
SELECT 'V082 planner notification template' AS check_name,
       IF((SELECT COUNT(*) FROM system_notify_template
             WHERE code='ZSJOS_REGISTRATION_PLANNER_ASSIGNED'
               AND scene_code='zsjos.registration.planner_assigned' AND deleted=b'0')=1,'PASS','FAIL') AS result;
SELECT 'V082 planner notification rules' AS check_name,
       IF((SELECT COUNT(*) FROM system_notify_rule
             WHERE scene_code='zsjos.registration.planner_assigned'
               AND creator='migration-V082' AND deleted=b'0')=
          (SELECT COUNT(*) FROM system_tenant WHERE deleted=b'0'),'PASS','FAIL') AS result;
SELECT 'V112 planner template lead.no contract' AS check_name,
       IF((SELECT COUNT(*) FROM system_notify_template
             WHERE code='ZSJOS_REGISTRATION_PLANNER_ASSIGNED'
               AND scene_code='zsjos.registration.planner_assigned'
               AND creator='migration-V082' AND deleted=b'0'
               AND params='["registration.caseId","lead.no"]'
               AND content='客资{{lead.no}}已分配给你。')=1,'PASS','FAIL') AS result;
SELECT 'V113 media student center version' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V113'
                    AND checksum='media-student-center-v3'),'PASS','FAIL') AS result;
SELECT 'V113 media account field and talk tables' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
             AND table_name IN ('zsjos_media_account_field_config','zsjos_media_student_talk_record'))=2,
          'PASS','FAIL') AS result;
SELECT 'V113 media account detail snapshot columns' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
             AND table_name='zsjos_media_account'
             AND column_name IN ('detail_config_version_id','detail_values_json','detail_snapshot_json'))=3,
          'PASS','FAIL') AS result;
SELECT 'V113 retired standalone media menus' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE id IN (6970,6974,6980) AND deleted=b'0')=0,
          'PASS','FAIL') AS result;
SELECT 'V113 account field configuration menus' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND permission IN (
             'zsjos:media-account-field-config:query','zsjos:media-account-field-config:update',
             'zsjos:media-account-field-config:publish'))=3,'PASS','FAIL') AS result;
SELECT 'V113 operator media student menu grant' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_role role_row
          WHERE role_row.code='new_media_operator' AND role_row.status=0 AND role_row.deleted=b'0'
            AND NOT EXISTS (
              SELECT 1 FROM system_role_menu rm JOIN system_menu menu_row ON menu_row.id=rm.menu_id
               WHERE rm.role_id=role_row.id AND rm.tenant_id=role_row.tenant_id AND rm.deleted=b'0'
                 AND menu_row.permission='zsjos:media-student:query-my' AND menu_row.deleted=b'0'
            )
       ),'PASS','FAIL') AS result;
SELECT 'V075 Lead-created notification coverage' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_tenant tenant
          WHERE tenant.deleted=b'0'
            AND NOT EXISTS (
              SELECT 1 FROM system_notify_rule rule_row
               WHERE rule_row.tenant_id=tenant.id
                 AND rule_row.scene_code='zsjos.lead.created'
                 AND rule_row.deleted=b'0'
            )
       ),'PASS','FAIL') AS result;
SELECT 'V080 migrated Lead-created sales rule contract' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_notify_rule rule_row
          WHERE rule_row.creator='migration-V075' AND rule_row.updater='migration-V080'
            AND rule_row.deleted=b'0'
            AND (rule_row.scene_code<>'zsjos.lead.created'
              OR rule_row.channel_code<>'in_app' OR rule_row.action_type<>'business_detail'
              OR rule_row.status<>0
              OR JSON_LENGTH(rule_row.recipient_roles)<>1
              OR NOT JSON_CONTAINS(rule_row.recipient_roles, JSON_QUOTE('operator')))
       ),'PASS','FAIL') AS result;
SELECT 'V075 Lead-created notification version' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V075'),'PASS','FAIL') AS result;
SELECT 'V080 Lead-source provider template contract' AS check_name,
       IF((SELECT COUNT(*) FROM system_notify_template
            WHERE code='ZSJOS_LEAD_SOURCE_LINKED'
              AND scene_code='zsjos.lead.created'
              AND title='新客资来源关联'
              AND summary='{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。'
              AND content='{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。'
              AND JSON_LENGTH(params)=2
              AND JSON_CONTAINS(params,JSON_QUOTE('operator.name'))
              AND JSON_CONTAINS(params,JSON_QUOTE('lead.no'))
              AND status=0 AND deleted=b'0')=1,'PASS','FAIL') AS result;
SELECT 'V080 Lead-source provider rule contract' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_notify_rule rule_row
          JOIN system_notify_template template ON template.id=rule_row.template_id
          WHERE rule_row.creator='migration-V080'
            AND (rule_row.scene_code<>'zsjos.lead.created'
              OR rule_row.channel_code<>'in_app'
              OR template.code<>'ZSJOS_LEAD_SOURCE_LINKED'
              OR rule_row.action_type<>'business_detail' OR rule_row.status<>0
              OR JSON_LENGTH(rule_row.recipient_roles)<>1
              OR NOT JSON_CONTAINS(rule_row.recipient_roles,JSON_QUOTE('new_media_provider')))
       ),'PASS','FAIL') AS result;
SELECT 'V080 migrated tenant provider-rule coverage' AS check_name,
       IF(NOT EXISTS (
         SELECT 1 FROM system_notify_rule sales_rule
          WHERE sales_rule.creator='migration-V075'
            AND sales_rule.updater='migration-V080' AND sales_rule.deleted=b'0'
            AND NOT EXISTS (
              SELECT 1 FROM system_notify_rule provider_rule
               JOIN system_notify_template provider_template
                 ON provider_template.id=provider_rule.template_id
              WHERE provider_rule.tenant_id=sales_rule.tenant_id
                AND provider_rule.creator='migration-V080'
                AND provider_rule.scene_code='zsjos.lead.created'
                AND provider_rule.deleted=b'0'
                AND provider_template.code='ZSJOS_LEAD_SOURCE_LINKED'
            )
       ),'PASS','FAIL') AS result;
SELECT 'V080 Lead-source provider notification version' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V080')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V080'),'PASS','FAIL') AS result;

SELECT 'V088 Lead source provider identity columns' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
                    AND column_name='source_provider_user_id')
          AND EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
                    AND column_name='source_provider_recorded')
          AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V088'),
          'PASS','FAIL') AS result;
SELECT 'V089 registration attachment idempotency result' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema=DATABASE() AND table_name='zsjos_registration_command'
                    AND column_name='result_attachment_id')
          AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V089'),
          'PASS','FAIL') AS result;
SELECT 'V090 Lead complaint result notifications' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V090')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V090')
          AND (SELECT COUNT(*) FROM system_notify_template
               WHERE code IN ('ZSJOS_LEAD_COMPLAINT_RESULT_FOUNDED',
                              'ZSJOS_LEAD_COMPLAINT_RESULT_UNFOUNDED')
                 AND deleted=b'0')=2
          AND NOT EXISTS (
            SELECT tenant.id FROM system_tenant tenant
            WHERE tenant.deleted=b'0' AND EXISTS (
              SELECT scene.scene_code FROM (
                SELECT 'zsjos.lead.complaint_founded' scene_code UNION ALL
                SELECT 'zsjos.lead.complaint_unfounded'
              ) scene
              WHERE NOT EXISTS (
                SELECT 1 FROM system_notify_rule rule_row
                WHERE rule_row.tenant_id=tenant.id
                  AND rule_row.scene_code=scene.scene_code
                  AND rule_row.channel_code='in_app'
                  AND rule_row.action_type='business_detail'
                  AND rule_row.deleted=b'0'
                  AND JSON_CONTAINS(rule_row.recipient_roles,JSON_QUOTE('complainant'))
              )
            )
          ),'PASS','FAIL') AS result;
SELECT 'V091 Lead flow history permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V091')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V091')
          AND (SELECT COUNT(*) FROM system_menu
               WHERE id=6924 AND permission='zsjos:lead-detail:flow-read'
                 AND parent_id=6770 AND type=3 AND deleted=b'0')=1,
           'PASS','FAIL') AS result;
SELECT 'V092 subordinate sales pause-all permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V092')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V092')
          AND (SELECT COUNT(*) FROM system_menu
               WHERE id=6819 AND permission='zsjos:subordinate-sales:pause-all'
                 AND parent_id=6814 AND type=3 AND deleted=b'0')=1,
          'PASS','FAIL') AS result;
SELECT 'collaboration pools mutually exclusive' AS check_name,
       IF(NOT EXISTS (
            SELECT 1 FROM zsjos_lead_aging_pool_cycle cycle
            JOIN zsjos_lead_public_sea_record manual
              ON manual.tenant_id=cycle.tenant_id AND manual.lead_id=cycle.lead_id
             AND manual.deleted=b'0'
            WHERE cycle.deleted=b'0'
              AND cycle.status IN ('waiting_assignment','assigned','deal_pending')
          ),'PASS','FAIL') AS result;
SELECT 'V063 cashback defaults' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_product_category WHERE parent_id=0 AND deleted=b'0'
             AND (default_valid_cashback_amount IS NULL OR default_deal_cashback_rate IS NULL))=0,'PASS','FAIL') AS result;
SELECT 'V070 BPM model import permission repair' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu
           WHERE permission='bpm:model:import' AND parent_id=1193
             AND type=3 AND status=0 AND deleted=b'0')=1,'PASS','FAIL') AS result;
SELECT 'V065 lead activity cursor ordering' AS check_name,
       IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
             AND table_name='zsjos_lead' AND column_name='last_activity_at')=1
          AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
             AND table_name='zsjos_lead' AND index_name='idx_tenant_last_activity')>0,
          'PASS','FAIL') AS result;
SELECT 'V086 schema version' AS check_item,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V086')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V086'), 'PASS', 'FAIL') AS result;
SELECT 'V086 Lead detail tab permissions' AS check_item,
       IF((SELECT COUNT(*) FROM system_menu
           WHERE id BETWEEN 6920 AND 6923 AND deleted=b'0'
             AND permission IN ('zsjos:lead-detail:follow-up-read','zsjos:lead-detail:appeal-read',
                                'zsjos:lead-detail:complaint-read','zsjos:lead-detail:order-read'))=4,
          'PASS','FAIL') AS result;
SELECT 'study_planner_repurchase_permissions' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V116'
            AND checksum='study-planner-repurchase-permission-v5')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
            WHERE module_code='core' AND version='V116'
              AND checksum=SHA2('study-planner-repurchase-permission-v5',256))
          AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
            AND table_name='zsjos_order' AND column_name='submission_request_fingerprint')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=6813
            AND permission='zsjos:sales-order:query-own' AND path='sales-orders/my'
            AND type=2 AND status=0 AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=73020
            AND permission='zsjos:student:query-my' AND path='my-students'
            AND type=2 AND status=0 AND deleted=b'0')
          AND EXISTS (SELECT 1 FROM system_menu WHERE id=73440 AND parent_id=73020
            AND permission='zsjos:sales-order:student-repurchase' AND type=3 AND status=0 AND deleted=b'0')
          AND NOT EXISTS (SELECT 1 FROM system_role role_row
            WHERE role_row.code='study_planner' AND role_row.status=0 AND role_row.deleted=b'0'
              AND (SELECT COUNT(DISTINCT relation_row.menu_id) FROM system_role_menu relation_row
                   WHERE relation_row.role_id=role_row.id AND relation_row.tenant_id=role_row.tenant_id
                     AND relation_row.menu_id IN (73440,6813) AND relation_row.deleted=b'0')<>2)
          AND NOT EXISTS (SELECT 1 FROM system_role_menu relation_row
            WHERE relation_row.menu_id=6849 AND relation_row.creator='migration-V116'),
          'PASS','FAIL') AS result;
SELECT 'lead_category_label_snapshot' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version
                   WHERE version='V117' AND checksum='lead-category-label-snapshot-v1')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V117'
                         AND checksum=SHA2('lead-category-label-snapshot-v1',256))
          AND EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
                         AND column_name='lead_category_label_snapshot')
          AND EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema=DATABASE() AND table_name='zsjos_lead_duplicate_review'
                         AND column_name='lead_category_label_snapshot'),
          'PASS','FAIL') AS result;
SELECT 'workbench_relative_child_paths' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version
                   WHERE version='V119' AND checksum='workbench-relative-child-paths-v1')
          AND EXISTS (SELECT 1 FROM zsjos_module_schema_version
                       WHERE module_code='core' AND version='V119'
                         AND checksum=SHA2('workbench-relative-child-paths-v1',256))
          AND NOT EXISTS (SELECT 1 FROM system_menu child_menu
              JOIN system_menu root_menu ON root_menu.id=child_menu.parent_id
                AND root_menu.path='/zsjos' AND root_menu.parent_id=0
                AND root_menu.status=0 AND root_menu.deleted=b'0'
              WHERE child_menu.type=2 AND child_menu.deleted=b'0'
                AND child_menu.path LIKE '/zsjos/%'),
          'PASS','FAIL') AS result;
