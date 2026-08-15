-- Read-only verification for V058 HRM/FMS data repair and metadata synchronization.
-- Run after V058 against the selected database. Every result row should report PASS.

WITH hrm_target_rows AS (
  SELECT COUNT(*) count_value FROM hrm_performance_assessment_quota_score WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment_action_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment_appeal_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment_quota WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment_dimension WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment_stage WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_plan WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_result_template WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_performance_assessment_template WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_insurance_month_employee_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_insurance_month_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_insurance_employee_info WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_insurance_scheme_project WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_insurance_scheme WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_slip WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_slip_send_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_month_employee_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_month_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_change_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_employee_info WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_group WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_tax_rule WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_option WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_config WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_change_template WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_personal_note WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_file WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_salary_card WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_quit_info WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_change_record WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_contract WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_contact WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_certificate WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_training_experience WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_work_experience WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee_education_experience WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_attendance_leave WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_attendance_clock WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_employee WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_attendance_group WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_attendance_holiday WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_recruit_interview WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_recruit_candidate WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_recruit_post WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_recruit_channel WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_recruit_post_type WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_config WHERE tenant_id IN (0, 1, 121)
  UNION ALL SELECT COUNT(*) FROM hrm_salary_slip_template
    WHERE tenant_id IN (1, 121) OR (tenant_id = 0 AND id <> 1)
)
SELECT 'hrm_target_rows_remaining' check_name, 0 expected, SUM(count_value) actual,
       IF(SUM(count_value) = 0, 'PASS', 'FAIL') result
FROM hrm_target_rows;

WITH fms_target_rows AS (
  SELECT COUNT(*) count_value FROM fms_closing_voucher WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_closing WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_closing_period WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_closing_template WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_cash_flow_extend_data WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_cash_flow_extend_config WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_cash_flow_statement_report WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_cash_flow_statement_config WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_income_statement_report WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_income_statement_config WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_balance_sheet_report WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_balance_sheet_config WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_voucher_entry WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_voucher WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_voucher_template WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_voucher_template_category WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_digest WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_finance_indicator WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_voucher_word WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_initial_balance WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_assist_combination WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_auxiliary_item WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_auxiliary_type WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_subject WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_currency WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_finance_parameter WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_account_user WHERE tenant_id IN (1, 121)
  UNION ALL SELECT COUNT(*) FROM fms_account_set WHERE tenant_id IN (1, 121)
)
SELECT 'fms_target_rows_remaining' check_name, 0 expected, SUM(count_value) actual,
       IF(SUM(count_value) = 0, 'PASS', 'FAIL') result
FROM fms_target_rows;

SELECT 'fms_subject_templates_preserved' check_name, 190 expected, COUNT(*) actual,
       IF(COUNT(*) = 190, 'PASS', 'FAIL') result FROM fms_subject_template
UNION ALL
SELECT 'fms_report_templates_preserved', 135, COUNT(*), IF(COUNT(*) = 135, 'PASS', 'FAIL') FROM fms_report_template
UNION ALL
SELECT 'hrm_salary_option_templates_preserved', 84, COUNT(*), IF(COUNT(*) = 84, 'PASS', 'FAIL') FROM hrm_salary_option_template
UNION ALL
SELECT 'hrm_global_salary_slip_template_preserved', 1, COUNT(*), IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM hrm_salary_slip_template WHERE id = 1 AND tenant_id = 0 AND deleted = b'0';

SELECT 'v058_menus' check_name, 294 expected, COUNT(*) actual,
       IF(COUNT(*) = 294, 'PASS', 'FAIL') result
FROM system_menu WHERE creator = 'v058' AND deleted = b'0'
UNION ALL
SELECT 'v058_hrm_permissions', 146, COUNT(*), IF(COUNT(*) = 146, 'PASS', 'FAIL')
FROM system_menu WHERE creator = 'v058' AND permission LIKE 'hrm:%' AND deleted = b'0'
UNION ALL
SELECT 'v058_fms_permissions', 101, COUNT(*), IF(COUNT(*) = 101, 'PASS', 'FAIL')
FROM system_menu WHERE creator = 'v058' AND permission LIKE 'fms:%' AND deleted = b'0'
UNION ALL
SELECT 'v058_menu_roots', 2, COUNT(*), IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM system_menu WHERE id IN (601476, 601894) AND parent_id = 0 AND creator = 'v058' AND deleted = b'0';

SELECT 'v058_menu_orphans' check_name, 0 expected, COUNT(*) actual,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') result
FROM system_menu child
LEFT JOIN system_menu parent ON parent.id = child.parent_id AND parent.deleted = b'0'
WHERE child.creator = 'v058' AND child.deleted = b'0' AND child.parent_id <> 0 AND parent.id IS NULL;

SELECT 'hrm_fms_dict_types' check_name, 60 expected, COUNT(*) actual,
       IF(COUNT(*) = 60, 'PASS', 'FAIL') result
FROM system_dict_type WHERE (type LIKE 'hrm\_%' OR type LIKE 'fms\_%') AND deleted = b'0'
UNION ALL
SELECT 'hrm_fms_dict_data', 244, COUNT(*), IF(COUNT(*) = 244, 'PASS', 'FAIL')
FROM system_dict_data WHERE (dict_type LIKE 'hrm\_%' OR dict_type LIKE 'fms\_%') AND deleted = b'0';

SELECT 'tenant_1_super_admin_menu_grants' check_name, 294 expected, COUNT(*) actual,
       IF(COUNT(*) = 294, 'PASS', 'FAIL') result
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id AND r.tenant_id = 1 AND r.code = 'super_admin' AND r.deleted = b'0'
JOIN system_menu m ON m.id = rm.menu_id AND m.creator = 'v058' AND m.deleted = b'0'
WHERE rm.tenant_id = 1 AND rm.deleted = b'0';

SELECT 'unauthorized_v058_menu_grants' check_name, 0 expected, COUNT(*) actual,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') result
FROM system_role_menu rm
JOIN system_menu m ON m.id = rm.menu_id AND m.creator = 'v058' AND m.deleted = b'0'
LEFT JOIN system_role r ON r.id = rm.role_id AND r.deleted = b'0'
WHERE rm.deleted = b'0'
  AND NOT (rm.tenant_id = 1 AND r.tenant_id = 1 AND r.code = 'super_admin');

SELECT 'zsjos_schema_version_v058' check_name, 1 expected, COUNT(*) actual,
       IF(COUNT(*) = 1, 'PASS', 'FAIL') result
FROM zsjos_schema_version WHERE version = 'V058'
UNION ALL
SELECT 'zsjos_module_schema_version_v058', 1, COUNT(*), IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM zsjos_module_schema_version WHERE module_code = 'core' AND version = 'V058';
