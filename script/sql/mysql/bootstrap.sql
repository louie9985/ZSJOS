-- Run with the MySQL client from the repository root:
--   mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/bootstrap.sql
-- This is a fresh-environment bootstrap. It never drops a database or table.
DROP PROCEDURE IF EXISTS `zsjos_assert_empty_database`;
DELIMITER $$
CREATE PROCEDURE `zsjos_assert_empty_database`()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Bootstrap requires an empty database; use a versioned migration for existing environments';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_assert_empty_database`();
DROP PROCEDURE `zsjos_assert_empty_database`;

SOURCE script/sql/mysql/00-bootstrap-schema.sql;
SOURCE script/sql/mysql/01-bootstrap-system-seed.sql;
SOURCE script/sql/mysql/02-bootstrap-zsjos-seed.sql;
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/migrations/V031__timed_business_notifications.sql;
SOURCE script/sql/mysql/migrations/V032__normalize_lead_inbox_filter_keys.sql;
SOURCE script/sql/mysql/migrations/V034__lead_aging_collaboration_pool.sql;
SOURCE script/sql/mysql/migrations/V035__cancel_invalid_lead_pending_tasks.sql;
SOURCE script/sql/mysql/migrations/V036__subordinate_sales_management.sql;
SOURCE script/sql/mysql/migrations/V037__lifecycle_domain_unification.sql;
SOURCE script/sql/mysql/migrations/V038__duplicate_lead_review.sql;
SOURCE script/sql/mysql/migrations/V039__lead_pools_and_claim_limit.sql;
SOURCE script/sql/mysql/migrations/V040__submitter_actions_and_complaints.sql;
SOURCE script/sql/mysql/migrations/V041__order_repurchase_and_concurrency.sql;
SOURCE script/sql/mysql/migrations/V042__normalize_legacy_lead_filter_status.sql;
SOURCE script/sql/mysql/migrations/V043__order_lifecycle_review_fixes.sql;
SOURCE script/sql/mysql/migrations/V044__default_employee_avatar.sql;
SOURCE script/sql/mysql/migrations/V045__dual_frontend_workbench_menu_components.sql;
SOURCE script/sql/mysql/migrations/V046__customer_order_advanced_filter_indexes.sql;
SOURCE script/sql/mysql/migrations/V047__split_lead_pending_handling_stages.sql;
SOURCE script/sql/mysql/migrations/V048__account_personnel_partner_lifecycle.sql;
SOURCE script/sql/mysql/migrations/V049__maintenance_mode_and_scheduler_guard.sql;
SOURCE script/sql/mysql/migrations/V050__readonly_impersonation_and_audit_catalog.sql;
SOURCE script/sql/mysql/migrations/V051__cashback_domain.sql;
SOURCE script/sql/mysql/migrations/V052__withdrawal_and_offline_payout.sql;
SOURCE script/sql/mysql/migrations/V053__withdrawal_finance_query_permission.sql;
SOURCE script/sql/mysql/migrations/V054__lead_business_number.sql;
SOURCE script/sql/mysql/migrations/V055__sales_order_supervisor_confirmation.sql;
SOURCE script/sql/mysql/migrations/V056__crm_lifecycle_confirmed_rules.sql;
SOURCE script/sql/mysql/migrations/V057__lead_runtime_settings.sql;
SOURCE script/sql/mysql/migrations/V062__finance_order_export_permission.sql;
SOURCE script/sql/mysql/migrations/V063__partner_portal_app_api_role.sql;
SOURCE script/sql/mysql/migrations/V064__bpm_model_import_permission.sql;
SOURCE script/sql/mysql/migrations/V065__inbox_cursor_activity_ordering.sql;
SOURCE script/sql/mysql/migrations/V066__readable_timed_reminder_templates.sql;
SOURCE script/sql/mysql/migrations/V067__lead_number_user_visible_contract.sql;
SOURCE script/sql/mysql/migrations/V068__repair_partner_permissions.sql;
SOURCE script/sql/mysql/migrations/V069__remove_invalid_partner_admin_route.sql;
SOURCE script/sql/mysql/migrations/V070__repair_bpm_model_import_permission.sql;
SOURCE script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql;
SOURCE script/sql/mysql/migrations/V072__independent_partner_identity.sql;
SOURCE script/sql/mysql/migrations/V073__registration_fulfillment_students.sql;
SOURCE script/sql/mysql/migrations/V074__registration_task_notifications.sql;
SOURCE script/sql/mysql/migrations/V075__lead_created_default_notification.sql;
SOURCE script/sql/mysql/migrations/V076__unify_sales_order_approval_entry.sql;
SOURCE script/sql/mysql/migrations/V077__normalize_wecom_user_id_uniqueness.sql;
SOURCE script/sql/mysql/migrations/V078__unified_lead_management_scope.sql;
SOURCE script/sql/mysql/migrations/V079__repair_lead_management_visibility.sql;
SOURCE script/sql/mysql/migrations/V080__lead_source_provider_notification.sql;
SOURCE script/sql/mysql/migrations/V081__employee_birthday_care.sql;
SOURCE script/sql/mysql/migrations/V082__registration_planner_notifications.sql;
SOURCE script/sql/mysql/migrations/V083__registration_routes_and_attachments.sql;
SOURCE script/sql/mysql/migrations/V084__repair_employee_birthday_care_menu.sql;
SOURCE script/sql/mysql/migrations/V085__remove_customer_names_from_business_notifications.sql;
SOURCE script/sql/mysql/migrations/V086__lead_detail_tab_permissions.sql;
SOURCE script/sql/mysql/migrations/V087__repair_business_notification_identifiers.sql;
SOURCE script/sql/mysql/migrations/V088__lead_source_provider_identity.sql;
SOURCE script/sql/mysql/migrations/V089__registration_attachment_idempotency_result.sql;
SOURCE script/sql/mysql/migrations/V090__lead_complaint_result_notifications.sql;
SOURCE script/sql/mysql/migrations/V091__lead_flow_history_permission.sql;
SOURCE script/sql/mysql/migrations/V092__subordinate_sales_pause_all_permission.sql;
SOURCE script/sql/mysql/migrations/V093__sales_order_supervisor_notifications.sql;
SOURCE script/sql/mysql/migrations/V094__student_contact_chain.sql;
SOURCE script/sql/mysql/migrations/V095__student_contact_extension_bpm_form.sql;
SOURCE script/sql/mysql/migrations/V096__new_media_content_workflow_schema.sql;
SOURCE script/sql/mysql/migrations/V097__new_media_workflow_menu_permissions.sql;
SOURCE script/sql/mysql/migrations/V098__partner_student_identity_link.sql;
SOURCE script/sql/mysql/migrations/V099__positioning_student_confirm_permission.sql;
SOURCE script/sql/mysql/migrations/V100__new_media_role_menu_permissions.sql;
SOURCE script/sql/mysql/migrations/V101__student_basic_info_permission.sql;
SOURCE script/sql/mysql/migrations/V102__new_media_business_notifications.sql;
SOURCE script/sql/mysql/migrations/V103__repair_new_media_operator_director_menu.sql;
SOURCE script/sql/mysql/migrations/V012__system_area_management.sql;
SOURCE script/sql/mysql/migrations/V013__configurable_area_other_nodes.sql;
SOURCE script/sql/mysql/migrations/V112__repair_registration_planner_notification_template.sql;
SOURCE script/sql/mysql/migrations/V113__media_student_center_consolidation.sql;
SOURCE script/sql/mysql/migrations/V114__student_delivery_stages.sql;
SOURCE script/sql/mysql/migrations/V115__generic_work_order.sql;
SOURCE script/sql/mysql/migrations/V116__study_planner_repurchase_permissions.sql;
SOURCE script/sql/mysql/migrations/V117__lead_category_label_snapshot.sql;
SOURCE script/sql/mysql/migrations/V118__independent_role_permission_boundaries.sql;
SOURCE script/sql/mysql/migrations/V119__workbench_relative_child_paths.sql;
SOURCE script/sql/mysql/migrations/V120__restore_operator_media_student_menu.sql;
SOURCE script/sql/mysql/migrations/V121__retire_lead_qualification_exception_menu.sql;
SOURCE script/sql/mysql/migrations/V122__repair_partner_lead_source_and_cashback_defaults.sql;
SOURCE script/sql/mysql/migrations/V123__retire_student_group_handoff_stage.sql;
SOURCE script/sql/mysql/migrations/V124__repair_registration_planner_student_notification_template.sql;
SOURCE script/sql/mysql/migrations/V125__student_business_number.sql;
SOURCE script/sql/mysql/migrations/V126__student_service_forms_exam_date_and_menu.sql;
SOURCE script/sql/mysql/migrations/V127__repair_student_business_form_config_menu.sql;
SOURCE script/sql/mysql/migrations/V128__media_director_student_flow.sql;
SOURCE script/sql/mysql/migrations/V129__seed_director_form_dictionaries.sql;
SOURCE script/sql/mysql/migrations/V130__director_configurable_forms_and_menus.sql;
SOURCE script/sql/mysql/migrations/V131__repair_director_operator_action_permissions.sql;
SOURCE script/sql/mysql/migrations/V132__workbench_menu_render_mode.sql;
SOURCE script/sql/mysql/migrations/V133__director_interview_form_presentation.sql;
SOURCE script/sql/mysql/migrations/V134__positioning_confirmation_handoff.sql;
SOURCE script/sql/mysql/migrations/V135__repair_applied_director_and_positioning_schema.sql;
SOURCE script/sql/mysql/migrations/V136__sales_order_team_management.sql;
SOURCE script/sql/mysql/migrations/V137__repair_workbench_menu_render_mode_version_collision.sql;
SOURCE script/sql/mysql/migrations/V138__hrm_fms_eam_workbench_admin_embed.sql;
SOURCE script/sql/mysql/migrations/V139__lead_supervisor_actions_and_public_sea_route.sql;

INSERT IGNORE INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core', `version`, `description`, SHA2(COALESCE(`checksum`, `version`), 256), 'baseline', `installed_at`
FROM `zsjos_schema_version`;
