-- UTF-8. Requires Core V304. Tenant 1; approved historical order repair.
-- Existing business databases MUST use order_history_repair.py with a private, reviewed
-- evidence package. A normal migration runner fails closed instead of inventing history.
-- Empty-order installations need no evidence and register normally.
-- Source data is never embedded in this tracked SQL. No role grants, BPM or event replay.
-- Run through a fail-fast client, never --force. The Python executor owns the business
-- transaction containing before/after images, mutations, postconditions and both ledgers.
-- Backup-column expansion is DDL and is not transactionally reversible. Keep LONGTEXT
-- on application rollback; guarded data recovery uses the private plan and before-images.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- BEGIN PREPARE
-- BEGIN V305 COMPLETION VERIFIER
DROP PROCEDURE IF EXISTS zsjos_v305_completed;
DELIMITER $$
CREATE PROCEDURE zsjos_v305_completed(OUT completed BOOLEAN)
check_completed: BEGIN
 DECLARE receipt JSON;
 DECLARE row_table VARCHAR(64);
 DECLARE record_id BIGINT;
 DECLARE before_doc JSON;
 DECLARE after_doc JSON;
 DECLARE expected_doc JSON;
 DECLARE field_names JSON;
 DECLARE field_path VARCHAR(200);
 DECLARE field_index INT;
 DECLARE done BOOLEAN DEFAULT FALSE;
 DECLARE later CURSOR FOR SELECT table_name,row_id,before_image,after_image FROM v305_later ORDER BY step,table_name,row_id;
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=TRUE;
 SET completed=FALSE;
 IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V305') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V305') ) THEN LEAVE check_completed; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completion evidence table missing';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305'
   GROUP BY table_name,row_id,column_name HAVING COUNT(*)<>1) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 duplicate completion evidence';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305' AND table_name='__plan__' AND row_id=0 AND column_name='manifest') THEN
   IF EXISTS(SELECT 1 FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND status='effective'
      AND COALESCE(creator,'') NOT IN ('seed-liudandan','seed-students','seed-llyilin','seed-batch4'))
      OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-history-v305') THEN
     SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completed ledger lacks reviewed plan receipt';
   END IF;
   SET completed=TRUE; LEAVE check_completed;
 END IF;
 SELECT CAST(old_value AS JSON) INTO receipt FROM zsjos_data_repair_backup
 WHERE repair_key='order-history-v305' AND table_name='__plan__' AND row_id=0 AND column_name='manifest';
 IF COALESCE(receipt->>'$.sha256','') NOT REGEXP '^[a-f0-9]{64}$'
   OR COALESCE(receipt->>'$.sourceSha','') NOT REGEXP '^[a-f0-9]{64}$'
   OR COALESCE(JSON_TYPE(receipt->'$.counts'),'')<>'OBJECT'
   OR JSON_LENGTH(receipt->'$.counts')=0 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 invalid reviewed plan receipt';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v305_completion;
 CREATE TEMPORARY TABLE v305_completion(table_name VARCHAR(64),row_id BIGINT,expected_image JSON,current_image JSON,
   PRIMARY KEY(table_name,row_id)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 INSERT INTO v305_completion(table_name,row_id,expected_image)
 SELECT table_name,row_id,CAST(old_value AS JSON) FROM zsjos_data_repair_backup
 WHERE repair_key='order-history-v305' AND column_name IN ('__after__','__inserted__');
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-history-v305' AND b.column_name='__before__'
   AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup a WHERE a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'))
 OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup a WHERE a.repair_key='order-history-v305' AND a.column_name='__after__'
   AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key=a.repair_key AND b.table_name=a.table_name AND b.row_id=a.row_id AND b.column_name='__before__')) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 incomplete before/after evidence';
 END IF;
 IF EXISTS(SELECT 1 FROM JSON_TABLE(JSON_KEYS(receipt->'$.counts'),'$[*]' COLUMNS(k VARCHAR(100) PATH '$')) counts
 WHERE COALESCE(JSON_EXTRACT(receipt,CONCAT('$.counts."',counts.k,'"')),-1)<>
   (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='order-history-v305'
    AND CONCAT(IF(b.column_name='__inserted__','insert:','update:'),b.table_name)=counts.k
    AND b.column_name IN ('__after__','__inserted__')))
 OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-history-v305' AND b.column_name IN ('__after__','__inserted__')
   AND JSON_CONTAINS_PATH(receipt,'one',CONCAT('$.counts."',IF(b.column_name='__inserted__','insert:','update:'),b.table_name,'"'))=0) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 evidence counts differ from reviewed receipt';
 END IF;
 UPDATE v305_completion c JOIN zsjos_lead x ON c.table_name='zsjos_lead' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image=JSON_OBJECT('sales_stage',x.`sales_stage`,'sales_stage_label_snapshot',x.`sales_stage_label_snapshot`,'id',x.`id`,'lead_no',x.`lead_no`,'person_id',x.`person_id`,'submitted_name',x.`submitted_name`,'submitted_mobile',x.`submitted_mobile`,'submitted_wechat_id',x.`submitted_wechat_id`,'source_type',x.`source_type`,'source_user_id',x.`source_user_id`,'source_provider_user_id',x.`source_provider_user_id`,'source_provider_recorded',CAST(x.`source_provider_recorded` AS UNSIGNED),'source_dept_id',x.`source_dept_id`,'partner_id',x.`partner_id`,'partner_owner_user_id_snapshot',x.`partner_owner_user_id_snapshot`,'partner_owner_name_snapshot',x.`partner_owner_name_snapshot`,'provider_owner_type',x.`provider_owner_type`,'provider_owner_id',x.`provider_owner_id`,'provider_owner_name_snapshot',x.`provider_owner_name_snapshot`,'contribution_user_id_snapshot',x.`contribution_user_id_snapshot`,'contribution_user_name_snapshot',x.`contribution_user_name_snapshot`,'contribution_supervisor_user_id_snapshot',x.`contribution_supervisor_user_id_snapshot`,'contribution_supervisor_name_snapshot',x.`contribution_supervisor_name_snapshot`,'contribution_dept_id_snapshot',x.`contribution_dept_id_snapshot`,'contribution_dept_name_snapshot',x.`contribution_dept_name_snapshot`,'source_channel_id',x.`source_channel_id`,'source_channel_label_snapshot',x.`source_channel_label_snapshot`,'status',x.`status`,'assignment_status',x.`assignment_status`,'owner_user_id',x.`owner_user_id`,'owner_identity',x.`owner_identity`,'ownership_started_at',IF(MICROSECOND(x.`ownership_started_at`)=0,DATE_FORMAT(x.`ownership_started_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`ownership_started_at` AS CHAR)),'recycle_source_owner_user_id',x.`recycle_source_owner_user_id`,'current_assignment_history_id',x.`current_assignment_history_id`,'current_assignment_first_follow_up_at',IF(MICROSECOND(x.`current_assignment_first_follow_up_at`)=0,DATE_FORMAT(x.`current_assignment_first_follow_up_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`current_assignment_first_follow_up_at` AS CHAR)),'current_assignment_first_follow_up_deadline_at',IF(MICROSECOND(x.`current_assignment_first_follow_up_deadline_at`)=0,DATE_FORMAT(x.`current_assignment_first_follow_up_deadline_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`current_assignment_first_follow_up_deadline_at` AS CHAR)),'last_follow_up_at',IF(MICROSECOND(x.`last_follow_up_at`)=0,DATE_FORMAT(x.`last_follow_up_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`last_follow_up_at` AS CHAR)),'last_follow_up_record_id',x.`last_follow_up_record_id`,'next_follow_up_at',IF(MICROSECOND(x.`next_follow_up_at`)=0,DATE_FORMAT(x.`next_follow_up_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`next_follow_up_at` AS CHAR)),'follow_up_count',x.`follow_up_count`,'no_progress_warned_at',IF(MICROSECOND(x.`no_progress_warned_at`)=0,DATE_FORMAT(x.`no_progress_warned_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`no_progress_warned_at` AS CHAR)),'qualification_round_no',x.`qualification_round_no`,'qualification_started_at',IF(MICROSECOND(x.`qualification_started_at`)=0,DATE_FORMAT(x.`qualification_started_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`qualification_started_at` AS CHAR)),'qualification_deadline_at',IF(MICROSECOND(x.`qualification_deadline_at`)=0,DATE_FORMAT(x.`qualification_deadline_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`qualification_deadline_at` AS CHAR)),'qualification_rule_snapshot',x.`qualification_rule_snapshot`,'suspended_at',IF(MICROSECOND(x.`suspended_at`)=0,DATE_FORMAT(x.`suspended_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`suspended_at` AS CHAR)),'qualified_by_user_id',x.`qualified_by_user_id`,'qualified_at',IF(MICROSECOND(x.`qualified_at`)=0,DATE_FORMAT(x.`qualified_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`qualified_at` AS CHAR)),'valid_description',x.`valid_description`,'submitted_at',IF(MICROSECOND(x.`submitted_at`)=0,DATE_FORMAT(x.`submitted_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`submitted_at` AS CHAR)),'counted_at',IF(MICROSECOND(x.`counted_at`)=0,DATE_FORMAT(x.`counted_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`counted_at` AS CHAR)),'last_activity_at',IF(MICROSECOND(x.`last_activity_at`)=0,DATE_FORMAT(x.`last_activity_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`last_activity_at` AS CHAR)),'converted_at',IF(MICROSECOND(x.`converted_at`)=0,DATE_FORMAT(x.`converted_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`converted_at` AS CHAR)),'invalid_reason',x.`invalid_reason`,'invalid_reason_label_snapshot',x.`invalid_reason_label_snapshot`,'invalid_description',x.`invalid_description`,'invalid_evidence_refs',x.`invalid_evidence_refs`,'appeal_deadline_at',IF(MICROSECOND(x.`appeal_deadline_at`)=0,DATE_FORMAT(x.`appeal_deadline_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`appeal_deadline_at` AS CHAR)),'closed_at',IF(MICROSECOND(x.`closed_at`)=0,DATE_FORMAT(x.`closed_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`closed_at` AS CHAR)),'close_reason',x.`close_reason`,'version',x.`version`,'creator',x.`creator`,'create_time',IF(MICROSECOND(x.`create_time`)=0,DATE_FORMAT(x.`create_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`create_time` AS CHAR)),'updater',x.`updater`,'update_time',IF(MICROSECOND(x.`update_time`)=0,DATE_FORMAT(x.`update_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`update_time` AS CHAR)),'deleted',CAST(x.`deleted` AS UNSIGNED),'tenant_id',x.`tenant_id`,'province_code',x.`province_code`,'province_name',x.`province_name`,'city_code',x.`city_code`,'city_name',x.`city_name`,'lead_category',x.`lead_category`,'lead_category_label_snapshot',x.`lead_category_label_snapshot`,'remark',x.`remark`,'dispatch_mode',x.`dispatch_mode`,'pending_assignee_user_id',x.`pending_assignee_user_id`,'pending_expires_at',IF(MICROSECOND(x.`pending_expires_at`)=0,DATE_FORMAT(x.`pending_expires_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`pending_expires_at` AS CHAR)),'assignment_attempt_count',x.`assignment_attempt_count`,'assignment_rule_snapshot',x.`assignment_rule_snapshot`,'public_pool_at',IF(MICROSECOND(x.`public_pool_at`)=0,DATE_FORMAT(x.`public_pool_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`public_pool_at` AS CHAR)),'submission_idempotency_key',x.`submission_idempotency_key`,'active_person_id',x.`active_person_id`,'pending_owner_identity',x.`pending_owner_identity`);
 UPDATE v305_completion c JOIN zsjos_opportunity x ON c.table_name='zsjos_opportunity' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image=JSON_OBJECT('id',x.`id`,'person_id',x.`person_id`,'type',x.`type`,'lead_id',x.`lead_id`,'source_service_relation_id',x.`source_service_relation_id`,'previous_order_id',x.`previous_order_id`,'status',x.`status`,'owner_user_id',x.`owner_user_id`,'expected_product_summary',x.`expected_product_summary`,'next_follow_up_at',IF(MICROSECOND(x.`next_follow_up_at`)=0,DATE_FORMAT(x.`next_follow_up_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`next_follow_up_at` AS CHAR)),'shelved_until',IF(MICROSECOND(x.`shelved_until`)=0,DATE_FORMAT(x.`shelved_until`,'%Y-%m-%d %H:%i:%s'),CAST(x.`shelved_until` AS CHAR)),'shelved_reason',x.`shelved_reason`,'won_at',IF(MICROSECOND(x.`won_at`)=0,DATE_FORMAT(x.`won_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`won_at` AS CHAR)),'lost_at',IF(MICROSECOND(x.`lost_at`)=0,DATE_FORMAT(x.`lost_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`lost_at` AS CHAR)),'lost_reason',x.`lost_reason`,'version',x.`version`,'creator',x.`creator`,'create_time',IF(MICROSECOND(x.`create_time`)=0,DATE_FORMAT(x.`create_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`create_time` AS CHAR)),'updater',x.`updater`,'update_time',IF(MICROSECOND(x.`update_time`)=0,DATE_FORMAT(x.`update_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`update_time` AS CHAR)),'deleted',CAST(x.`deleted` AS UNSIGNED),'tenant_id',x.`tenant_id`,'active_person_id',x.`active_person_id`);
 UPDATE v305_completion c JOIN zsjos_order x ON c.table_name='zsjos_order' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image=JSON_OBJECT('id',x.`id`,'order_no',x.`order_no`,'lead_id',x.`lead_id`,'source_payment_order_id',x.`source_payment_order_id`,'purchase_intent_id',x.`purchase_intent_id`,'opportunity_id',x.`opportunity_id`,'person_id',x.`person_id`,'order_type',x.`order_type`,'status',x.`status`,'submitter_user_id',x.`submitter_user_id`,'formal_sales_user_id',x.`formal_sales_user_id`,'imported_actor_snapshot',x.`imported_actor_snapshot`,'formal_owner_identity',x.`formal_owner_identity`,'submitter_center_type',x.`submitter_center_type`,'buyer_name',x.`buyer_name`,'student_name',x.`student_name`,'student_nature',x.`student_nature`,'student_mobile',x.`student_mobile`,'student_wechat_id',x.`student_wechat_id`,'province_code',x.`province_code`,'province_name',x.`province_name`,'city_code',x.`city_code`,'city_name',x.`city_name`,'agreed_exam_time',x.`agreed_exam_time`,'class_type',x.`class_type`,'service_period',x.`service_period`,'student_source',x.`student_source`,'total_amount',CAST(x.`total_amount` AS CHAR),'discount_amount',CAST(x.`discount_amount` AS CHAR),'payable_amount',CAST(x.`payable_amount` AS CHAR),'customer_paid_at',IF(MICROSECOND(x.`customer_paid_at`)=0,DATE_FORMAT(x.`customer_paid_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`customer_paid_at` AS CHAR)),'fee_mode',x.`fee_mode`,'payment_method',x.`payment_method`,'fee_mode_label_snapshot',x.`fee_mode_label_snapshot`,'payment_method_label_snapshot',x.`payment_method_label_snapshot`,'remark',x.`remark`,'student_special_requirements',x.`student_special_requirements`,'material_delivery_contact',x.`material_delivery_contact`,'gift_items',x.`gift_items`,'gift_shipping_address',x.`gift_shipping_address`,'payment_voucher_refs',x.`payment_voucher_refs`,'submission_idempotency_key',x.`submission_idempotency_key`,'submission_request_fingerprint',x.`submission_request_fingerprint`,'repurchase_reason',x.`repurchase_reason`,'termination_reason',x.`termination_reason`,'terminated_at',IF(MICROSECOND(x.`terminated_at`)=0,DATE_FORMAT(x.`terminated_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`terminated_at` AS CHAR)),'contract_refs',x.`contract_refs`,'current_approval_round_id',x.`current_approval_round_id`,'supersedes_order_id',x.`supersedes_order_id`,'superseded_by_order_id',x.`superseded_by_order_id`,'submitted_at',IF(MICROSECOND(x.`submitted_at`)=0,DATE_FORMAT(x.`submitted_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`submitted_at` AS CHAR)),'effective_at',IF(MICROSECOND(x.`effective_at`)=0,DATE_FORMAT(x.`effective_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`effective_at` AS CHAR)),'cancelled_at',IF(MICROSECOND(x.`cancelled_at`)=0,DATE_FORMAT(x.`cancelled_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`cancelled_at` AS CHAR)),'cancel_reason',x.`cancel_reason`,'zero_amount_reason',x.`zero_amount_reason`,'zero_amount_approved_by_user_id',x.`zero_amount_approved_by_user_id`,'version',x.`version`,'creator',x.`creator`,'create_time',IF(MICROSECOND(x.`create_time`)=0,DATE_FORMAT(x.`create_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`create_time` AS CHAR)),'updater',x.`updater`,'update_time',IF(MICROSECOND(x.`update_time`)=0,DATE_FORMAT(x.`update_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`update_time` AS CHAR)),'deleted',CAST(x.`deleted` AS UNSIGNED),'tenant_id',x.`tenant_id`,'active_lead_id',x.`active_lead_id`,'active_repurchase_person_id',x.`active_repurchase_person_id`);
 UPDATE v305_completion c JOIN zsjos_order_item x ON c.table_name='zsjos_order_item' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image=JSON_OBJECT('id',x.`id`,'order_id',x.`order_id`,'product_id',x.`product_id`,'sku_id',x.`sku_id`,'product_ref',x.`product_ref`,'sku_ref',x.`sku_ref`,'quantity',CAST(x.`quantity` AS CHAR),'unit_price',CAST(x.`unit_price` AS CHAR),'discount_amount',CAST(x.`discount_amount` AS CHAR),'payable_amount',CAST(x.`payable_amount` AS CHAR),'product_snapshot',x.`product_snapshot`,'product_rule_snapshot',x.`product_rule_snapshot`,'product_rule_version',x.`product_rule_version`,'creator',x.`creator`,'create_time',IF(MICROSECOND(x.`create_time`)=0,DATE_FORMAT(x.`create_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`create_time` AS CHAR)),'updater',x.`updater`,'update_time',IF(MICROSECOND(x.`update_time`)=0,DATE_FORMAT(x.`update_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`update_time` AS CHAR)),'deleted',CAST(x.`deleted` AS UNSIGNED),'tenant_id',x.`tenant_id`);
 UPDATE v305_completion c JOIN zsjos_order_approval_round x ON c.table_name='zsjos_order_approval_round' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image=JSON_OBJECT('id',x.`id`,'order_id',x.`order_id`,'round_no',x.`round_no`,'status',x.`status`,'order_snapshot',x.`order_snapshot`,'process_instance_id',x.`process_instance_id`,'process_definition_key',x.`process_definition_key`,'submitted_by_user_id',x.`submitted_by_user_id`,'submitted_at',IF(MICROSECOND(x.`submitted_at`)=0,DATE_FORMAT(x.`submitted_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`submitted_at` AS CHAR)),'completed_at',IF(MICROSECOND(x.`completed_at`)=0,DATE_FORMAT(x.`completed_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`completed_at` AS CHAR)),'rejected_bpm_task_id',x.`rejected_bpm_task_id`,'decision_reason',x.`decision_reason`,'submission_idempotency_key',x.`submission_idempotency_key`,'registration_decision_idempotency_key',x.`registration_decision_idempotency_key`,'finance_decision_idempotency_key',x.`finance_decision_idempotency_key`,'termination_idempotency_key',x.`termination_idempotency_key`,'supervisor_confirmation_enabled',CAST(x.`supervisor_confirmation_enabled` AS UNSIGNED),'version',x.`version`,'creator',x.`creator`,'create_time',IF(MICROSECOND(x.`create_time`)=0,DATE_FORMAT(x.`create_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`create_time` AS CHAR)),'updater',x.`updater`,'update_time',IF(MICROSECOND(x.`update_time`)=0,DATE_FORMAT(x.`update_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`update_time` AS CHAR)),'deleted',CAST(x.`deleted` AS UNSIGNED),'tenant_id',x.`tenant_id`);
 UPDATE v305_completion c JOIN zsjos_performance_attribution x ON c.table_name='zsjos_performance_attribution' AND c.row_id=x.id AND x.tenant_id=1 SET c.current_image=JSON_OBJECT('id',x.`id`,'outcome',x.`outcome`,'completed_at',IF(MICROSECOND(x.`completed_at`)=0,DATE_FORMAT(x.`completed_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`completed_at` AS CHAR)),'fact_type',x.`fact_type`,'fact_id',x.`fact_id`,'user_id',x.`user_id`,'user_name',x.`user_name`,'dept_id',x.`dept_id`,'dept_name',x.`dept_name`,'center_id',x.`center_id`,'center_name',x.`center_name`,'lead_id',x.`lead_id`,'assignment_id',x.`assignment_id`,'received_at',IF(MICROSECOND(x.`received_at`)=0,DATE_FORMAT(x.`received_at`,'%Y-%m-%d %H:%i:%s'),CAST(x.`received_at` AS CHAR)),'org_source',x.`org_source`,'source_group',x.`source_group`,'channel_code',x.`channel_code`,'channel_label',x.`channel_label`,'creator',x.`creator`,'create_time',IF(MICROSECOND(x.`create_time`)=0,DATE_FORMAT(x.`create_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`create_time` AS CHAR)),'updater',x.`updater`,'update_time',IF(MICROSECOND(x.`update_time`)=0,DATE_FORMAT(x.`update_time`,'%Y-%m-%d %H:%i:%s'),CAST(x.`update_time` AS CHAR)),'deleted',CAST(x.`deleted` AS UNSIGNED),'tenant_id',x.`tenant_id`);
 IF EXISTS(SELECT 1 FROM v305_completion WHERE current_image IS NULL OR NOT(expected_image->'$.id' <=> CAST(row_id AS JSON)) OR NOT(expected_image->'$.tenant_id' <=> CAST(1 AS JSON))) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completion target missing or wrong identity';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v305_later;
 CREATE TEMPORARY TABLE v305_later(step INT,table_name VARCHAR(64),row_id BIGINT,before_image JSON,after_image JSON,
   PRIMARY KEY(step,table_name,row_id)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='order-identity-v306') THEN
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V306') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V306')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair lacks ledgers or completion receipt: V306'; END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='order-identity-v306' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair evidence incomplete: V306';
  END IF;
  INSERT INTO v305_later SELECT 0,b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='order-identity-v306' AND b.column_name='__before__';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='lead-provider-v307') THEN
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V307') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V307')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair lacks ledgers or completion receipt: V307'; END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='lead-provider-v307' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair evidence incomplete: V307';
  END IF;
  INSERT INTO v305_later SELECT 1,b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='lead-provider-v307' AND b.column_name='__before__';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='confirmed-provider-v308') THEN
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V308') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V308')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair lacks ledgers or completion receipt: V308'; END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='confirmed-provider-v308' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair evidence incomplete: V308';
  END IF;
  INSERT INTO v305_later SELECT 2,b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='confirmed-provider-v308' AND b.column_name='__before__';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='media-assisted-source-v309') THEN
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair lacks ledgers or completion receipt: V309'; END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='media-assisted-source-v309' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair evidence incomplete: V309';
  END IF;
  INSERT INTO v305_later SELECT 3,b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='media-assisted-source-v309' AND b.column_name='__before__';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='lead-source-confirmed-v309-r2') THEN
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair lacks ledgers or completion receipt: V309'; END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='lead-source-confirmed-v309-r2' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair evidence incomplete: V309';
  END IF;
  INSERT INTO v305_later SELECT 4,b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='lead-source-confirmed-v309-r2' AND b.column_name='__before__';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id WHERE b.repair_key='provider-identity-20261011') THEN
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V308') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V308') AND (SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='provider-identity-20261011')=97 AND EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='provider-identity-20261011' AND table_name='__repair__' AND row_id=0 AND column_name='__manifest__' AND old_value='5383cbc288ca5eb3c790bf23ce52541cac3e8392fa8c5234b079d2fbe1d7284f')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair lacks ledgers or completion receipt: V308'; END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
    WHERE b.repair_key='provider-identity-20261011' GROUP BY b.table_name,b.row_id HAVING SUM(b.column_name='__before__')<>1 OR SUM(b.column_name='__after__')<>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair evidence incomplete: V308';
  END IF;
  INSERT INTO v305_later SELECT 5,b.table_name,b.row_id,CAST(b.old_value AS JSON),CAST(a.old_value AS JSON)
  FROM zsjos_data_repair_backup b JOIN v305_completion c ON c.table_name=b.table_name AND c.row_id=b.row_id
  JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
  WHERE b.repair_key='provider-identity-20261011' AND b.column_name='__before__';
 END IF;
 UPDATE v305_later SET before_image=JSON_OBJECT('formal_owner_identity',before_image->'$.identity'),after_image=JSON_OBJECT('formal_owner_identity',after_image->'$.identity') WHERE step=0 AND table_name='zsjos_order';
 UPDATE v305_later SET before_image=JSON_OBJECT('order_snapshot',before_image->'$.snapshot'),after_image=JSON_OBJECT('order_snapshot',after_image->'$.snapshot') WHERE step=0 AND table_name='zsjos_order_approval_round';
 OPEN later;
 replay_chain: LOOP
  FETCH later INTO row_table,record_id,before_doc,after_doc;
  IF done THEN LEAVE replay_chain; END IF;
  SELECT expected_image INTO expected_doc FROM v305_completion WHERE table_name=row_table AND row_id=record_id;
  SET field_names=JSON_KEYS(after_doc),field_index=0;
  WHILE field_index<JSON_LENGTH(field_names) DO
   SET field_path=CONCAT('$.',JSON_QUOTE(JSON_UNQUOTE(JSON_EXTRACT(field_names,CONCAT('$[',field_index,']')))));
   IF JSON_CONTAINS_PATH(expected_doc,'one',field_path)=1
     AND JSON_UNQUOTE(JSON_EXTRACT(field_names,CONCAT('$[',field_index,']'))) NOT IN ('version','updater','update_time')
     AND NOT(JSON_EXTRACT(before_doc,field_path) <=> JSON_EXTRACT(after_doc,field_path)) THEN
    IF JSON_CONTAINS_PATH(expected_doc,'one',field_path)=1
       AND NOT(JSON_EXTRACT(expected_doc,field_path) <=> JSON_EXTRACT(before_doc,field_path)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 later repair chain does not match original evidence';
    END IF;
    SET expected_doc=JSON_SET(expected_doc,field_path,JSON_EXTRACT(after_doc,field_path));
   END IF;
   SET field_index=field_index+1;
  END WHILE;
  UPDATE v305_completion SET expected_image=expected_doc WHERE table_name=row_table AND row_id=record_id;
 END LOOP;
 CLOSE later;
 IF EXISTS(SELECT 1 FROM v305_completion c
   JOIN JSON_TABLE(JSON_KEYS(c.expected_image),'$[*]' COLUMNS(k VARCHAR(100) PATH '$')) fields
   WHERE fields.k NOT IN ('version','updater','update_time') AND NOT(
     JSON_EXTRACT(c.current_image,CONCAT('$.',JSON_QUOTE(fields.k))) <=>
     JSON_EXTRACT(c.expected_image,CONCAT('$.',JSON_QUOTE(fields.k))))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 completed business fields conflict with repair evidence';
 END IF;
 DROP TEMPORARY TABLE v305_later;
 DROP TEMPORARY TABLE v305_completion;
 SET completed=TRUE;
END$$
DELIMITER ;
-- END V305 COMPLETION VERIFIER
DROP PROCEDURE IF EXISTS zsjos_v305_prepare;
DELIMITER $$
CREATE PROCEDURE zsjos_v305_prepare()
prepare_body: BEGIN
  DECLARE completed BOOLEAN DEFAULT FALSE;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V304')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V304') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 requires V304 in both ledgers';
  END IF;
-- BEGIN V305 PREPARE REPLAY
  CALL zsjos_v305_completed(completed);
  IF completed THEN LEAVE prepare_body; END IF;
-- END V305 PREPARE REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND status='effective'
       AND COALESCE(creator,'') NOT IN ('seed-liudandan','seed-students','seed-llyilin','seed-batch4'))
     AND COALESCE(@v305_executor,0)<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 requires reviewed private evidence; use order_history_repair.py';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type<>'longtext') THEN
    ALTER TABLE zsjos_data_repair_backup MODIFY COLUMN old_value LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
       AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 requires full-length repair backups';
  END IF;
END$$
DELIMITER ;
CALL zsjos_v305_prepare();
DROP PROCEDURE zsjos_v305_prepare;
-- END PREPARE

-- BEGIN FINALIZE
DROP PROCEDURE IF EXISTS zsjos_v305_finalize;
DELIMITER $$
CREATE PROCEDURE zsjos_v305_finalize()
finalize_body: BEGIN
  DECLARE completed BOOLEAN DEFAULT FALSE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
-- BEGIN V305 FINALIZE REPLAY
  IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V304') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V304')) OR NOT EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 finalize requires verified preparation prerequisites';
  END IF;
  CALL zsjos_v305_completed(completed);
  IF completed THEN LEAVE finalize_body; END IF;
-- END V305 FINALIZE REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND status='effective'
       AND COALESCE(creator,'') NOT IN ('seed-liudandan','seed-students','seed-llyilin','seed-batch4'))
     AND (COALESCE(@v305_verified,0)<>1 OR COALESCE(@v305_plan_sha,'') NOT REGEXP '^[a-f0-9]{64}$') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V305 postconditions not verified; no success marker';
  END IF;
  INSERT INTO zsjos_schema_version(version,description,checksum)
    SELECT 'V305','Source-backed order history repair',SHA2('V305__order_history_repair.sql',256)
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V305');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
    SELECT 'core','V305','Source-backed order history repair',SHA2('V305__order_history_repair.sql',256),'baseline'
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V305');
END$$
DELIMITER ;
-- The executor installs the routine before beginning its transaction and invokes it
-- only after verifying all operations. The direct empty-database path uses this call.
START TRANSACTION;
CALL zsjos_v305_finalize();
COMMIT;
DROP PROCEDURE zsjos_v305_finalize;
-- END FINALIZE

DROP PROCEDURE IF EXISTS zsjos_v305_completed;
