-- Read-only verification. Every row should report PASS on a usable installation.
SET NAMES utf8mb4;

SELECT 'schema_version' AS check_name,
       IF(EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_schema_version'), 'PASS', 'FAIL') AS result;
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
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6770 AND permission='zsjos:lead:query' AND parent_id=6735 AND visible=b'0' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
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
       IF((SELECT COUNT(*) FROM zsjos_lead_inbox_filter_version WHERE tenant_id=1 AND version_no=1 AND deleted=b'0')=2, 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_menu' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE id=6773 AND permission='zsjos:lead-filter:query' AND component='zsjos/leadFilter/index' AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_filter_v005' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V005'), 'PASS', 'FAIL') AS result;
SELECT 'default_follow_up_rule' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_lead_follow_up_rule WHERE tenant_id=1 AND code='default' AND first_follow_up_timeout_minutes=1440 AND deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'sales_accept_permission' AS check_name,
       IF(EXISTS (SELECT 1 FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code='sales_specialist' AND m.permission='zsjos:lead:accept' AND rm.deleted=b'0' AND r.deleted=b'0' AND m.deleted=b'0'), 'PASS', 'FAIL') AS result;
SELECT 'lead_follow_up_rule_v006' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V006'), 'PASS', 'FAIL') AS result;
SELECT 'lead_fixed_inbox_routes' AS check_name,
       IF((SELECT COUNT(*) FROM system_menu WHERE id IN (6778,6779) AND permission IN ('zsjos:lead:query-submitted','zsjos:lead:query-owned') AND visible=b'1' AND deleted=b'0')=2, 'PASS', 'FAIL') AS result;
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
SELECT 'lead_invalid_remark_v017' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V017'), 'PASS', 'FAIL') AS result;
SELECT 'lead_actions_v018' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V018'), 'PASS', 'FAIL') AS result;
SELECT 'historical_valid_leads_v019' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V019'), 'PASS', 'FAIL') AS result;
SELECT 'unified_schema_migration_v020' AS check_name,
       IF(EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V020'), 'PASS', 'FAIL') AS result;
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
SELECT 'module_schema_versions' AS check_name,
       IF((SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version IN ('V001','V017','V018','V019','V020','V021','V023','V024','V025','V026'))=10, 'PASS', 'FAIL') AS result;
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
  UNION ALL SELECT 'zsjos_lead' UNION ALL SELECT 'zsjos_product' UNION ALL SELECT 'zsjos_product_sku'
  UNION ALL SELECT 'zsjos_lead_inbox_filter_scheme' UNION ALL SELECT 'zsjos_lead_inbox_filter_version'
  UNION ALL SELECT 'zsjos_lead_follow_up_rule' UNION ALL SELECT 'zsjos_business_task'
  UNION ALL SELECT 'zsjos_lead_follow_up_record' UNION ALL SELECT 'zsjos_lead_follow_up_image'
  UNION ALL SELECT 'system_notify_rule' UNION ALL SELECT 'system_area'
  UNION ALL SELECT 'crm_owner_record' UNION ALL SELECT 'crm_performance_config'
  UNION ALL SELECT 'zsjos_module_schema_version'
) expected
LEFT JOIN information_schema.tables actual
  ON actual.table_schema=DATABASE() AND actual.table_name=expected.table_name;
