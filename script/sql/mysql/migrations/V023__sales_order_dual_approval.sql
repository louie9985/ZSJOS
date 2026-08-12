-- V023 direct sales-order entry and dual-center BPM approval.
-- Dependencies: V021 and V022 must be integrated first; System departments/users/roles, Infra files, ZSJOS Lead/Product, BPM.
-- Data scope: additive order metadata, approval configuration, dictionaries, menus and permission grants. No business rows are deleted.
-- Repeatability: every DDL/DML statement is guarded; dictionary/menu/config seeds use stable keys.
-- Rollback limitation: disable menus and the BPM definition; retain order, item and approval-round audit data.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='buyer_name'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `buyer_name` varchar(100) DEFAULT NULL COMMENT ''购买方快照'' AFTER `submitter_center_type`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='student_name'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `student_name` varchar(100) DEFAULT NULL COMMENT ''学员姓名快照'' AFTER `buyer_name`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='student_nature'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `student_nature` varchar(64) DEFAULT NULL COMMENT ''学员性质字典值'' AFTER `student_name`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='student_mobile'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `student_mobile` varchar(32) DEFAULT NULL COMMENT ''学员手机号快照'' AFTER `student_nature`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='student_wechat_id'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `student_wechat_id` varchar(64) DEFAULT NULL COMMENT ''学员微信号快照'' AFTER `student_mobile`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='province_code'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `province_code` varchar(32) DEFAULT NULL COMMENT ''省编码快照'' AFTER `student_wechat_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='province_name'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `province_name` varchar(100) DEFAULT NULL COMMENT ''省名称快照'' AFTER `province_code`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='city_code'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `city_code` varchar(32) DEFAULT NULL COMMENT ''市编码快照'' AFTER `province_name`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='city_name'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `city_name` varchar(100) DEFAULT NULL COMMENT ''市名称快照'' AFTER `city_code`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='agreed_exam_time'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `agreed_exam_time` varchar(100) DEFAULT NULL COMMENT ''商定考试时间文本'' AFTER `city_name`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='class_type'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `class_type` varchar(100) DEFAULT NULL COMMENT ''开通班种文本'' AFTER `agreed_exam_time`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='service_period'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `service_period` varchar(64) DEFAULT NULL COMMENT ''服务周期字典值'' AFTER `class_type`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='student_source'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `student_source` varchar(64) DEFAULT NULL COMMENT ''学生来源字典值'' AFTER `service_period`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='customer_paid_at'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `customer_paid_at` datetime DEFAULT NULL COMMENT ''客户实际付款时间'' AFTER `payable_amount`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='fee_mode'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `fee_mode` varchar(64) DEFAULT NULL COMMENT ''缴费方式字典值'' AFTER `customer_paid_at`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='payment_method'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `payment_method` varchar(64) DEFAULT NULL COMMENT ''支付方式字典值'' AFTER `fee_mode`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='remark'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `remark` varchar(1000) DEFAULT NULL COMMENT ''订单备注'' AFTER `payment_method`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='student_special_requirements'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `student_special_requirements` varchar(1000) DEFAULT NULL COMMENT ''学生特殊要求'' AFTER `remark`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='material_delivery_contact'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `material_delivery_contact` varchar(1000) DEFAULT NULL COMMENT ''教材邮递联系'' AFTER `student_special_requirements`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='payment_voucher_refs'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `payment_voucher_refs` json DEFAULT NULL COMMENT ''缴费凭证文件快照'' AFTER `material_delivery_contact`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='submission_idempotency_key'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `submission_idempotency_key` varchar(128) DEFAULT NULL COMMENT ''首次提交幂等键'' AFTER `payment_voucher_refs`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='active_lead_id'),'SELECT 1','ALTER TABLE `zsjos_order` ADD COLUMN `active_lead_id` bigint GENERATED ALWAYS AS (CASE WHEN (`deleted` = b''0'' AND `status` IN (''pending_approval'',''revision_required'')) THEN `lead_id` ELSE NULL END) STORED COMMENT ''活动成交单客资唯一键'' AFTER `submission_idempotency_key`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND index_name='uk_tenant_order_submit_key'),'SELECT 1','ALTER TABLE `zsjos_order` ADD UNIQUE KEY `uk_tenant_order_submit_key` (`tenant_id`,`submission_idempotency_key`)')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND index_name='uk_tenant_active_lead_order'),'SELECT 1','ALTER TABLE `zsjos_order` ADD UNIQUE KEY `uk_tenant_active_lead_order` (`tenant_id`,`active_lead_id`)')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_item' AND column_name='product_ref'),'SELECT 1','ALTER TABLE `zsjos_order_item` ADD COLUMN `product_ref` varchar(64) DEFAULT NULL COMMENT ''产品稳定编号快照'' AFTER `sku_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_item' AND column_name='sku_ref'),'SELECT 1','ALTER TABLE `zsjos_order_item` ADD COLUMN `sku_ref` varchar(64) DEFAULT NULL COMMENT ''SKU 稳定编号快照'' AFTER `product_ref`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND column_name='submission_idempotency_key'),'SELECT 1','ALTER TABLE `zsjos_order_approval_round` ADD COLUMN `submission_idempotency_key` varchar(128) DEFAULT NULL COMMENT ''本轮提交幂等键'' AFTER `rejected_bpm_task_id`')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_order_approval_round' AND index_name='uk_tenant_order_round_submit_key'),'SELECT 1','ALTER TABLE `zsjos_order_approval_round` ADD UNIQUE KEY `uk_tenant_order_round_submit_key` (`tenant_id`,`submission_idempotency_key`)')); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_order_approval_config` (
  `id` bigint NOT NULL AUTO_INCREMENT, `registration_dept_id` bigint NOT NULL COMMENT '报名履约中心根部门',
  `finance_dept_id` bigint NOT NULL COMMENT '财务结算中心根部门', `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_order_approval_config` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成交订单审批部门配置';

INSERT INTO `zsjos_order_approval_config` (`registration_dept_id`,`finance_dept_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 1030,1040,'migration-V023',NOW(),'migration-V023',NOW(),b'0',1
WHERE EXISTS(SELECT 1 FROM `system_dept` WHERE id=1030 AND tenant_id=1 AND deleted=b'0')
  AND EXISTS(SELECT 1 FROM `system_dept` WHERE id=1040 AND tenant_id=1 AND deleted=b'0')
  AND NOT EXISTS(SELECT 1 FROM `zsjos_order_approval_config` WHERE tenant_id=1 AND deleted=b'0');

INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.type,0,'成交订单字段字典','migration-V023',NOW(),'migration-V023',NOW(),b'0' FROM (
 SELECT '学员性质' name,'zsjos_order_student_nature' type UNION ALL SELECT '服务周期','zsjos_order_service_period' UNION ALL
 SELECT '学生来源','zsjos_order_student_source' UNION ALL SELECT '缴费方式','zsjos_order_fee_mode' UNION ALL SELECT '支付方式','zsjos_order_payment_method'
) seed WHERE NOT EXISTS(SELECT 1 FROM `system_dict_type` d WHERE d.type=seed.type AND d.deleted=b'0');

INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.sort,seed.label,seed.value,seed.dict_type,0,'default','migration-V023',NOW(),'migration-V023',NOW(),b'0' FROM (
 SELECT 50 sort,'畅学卡' label,'learning_card' value,'zsjos_order_student_nature' dict_type UNION ALL SELECT 40,'老学员','existing_student','zsjos_order_student_nature' UNION ALL SELECT 30,'新学员','new_student','zsjos_order_student_nature' UNION ALL SELECT 20,'老带新','existing_referral','zsjos_order_student_nature' UNION ALL SELECT 10,'大客户代理','key_account_agent','zsjos_order_student_nature' UNION ALL
 SELECT 80,'1年','one_year','zsjos_order_service_period' UNION ALL SELECT 70,'2年','two_year','zsjos_order_service_period' UNION ALL SELECT 60,'3年','three_year','zsjos_order_service_period' UNION ALL SELECT 50,'4年','four_year','zsjos_order_service_period' UNION ALL SELECT 40,'5年','five_year','zsjos_order_service_period' UNION ALL SELECT 30,'仅限当期','current_term_only','zsjos_order_service_period' UNION ALL SELECT 20,'当期有效（可免费复训一期）','current_term_plus_retrain','zsjos_order_service_period' UNION ALL SELECT 10,'长期有效','long_term','zsjos_order_service_period' UNION ALL
 SELECT 40,'直接招生','direct_enrollment','zsjos_order_student_source' UNION ALL SELECT 30,'代理推荐','agent_referral','zsjos_order_student_source' UNION ALL SELECT 20,'合作伙伴','partner','zsjos_order_student_source' UNION ALL SELECT 10,'大客户低价','key_account_low_price','zsjos_order_student_source' UNION ALL
 SELECT 30,'零售缴费','retail','zsjos_order_fee_mode' UNION ALL SELECT 20,'预付款扣费','prepaid_deduction','zsjos_order_fee_mode' UNION ALL SELECT 10,'底价缴费','floor_price','zsjos_order_fee_mode' UNION ALL
 SELECT 70,'学习二维码','learning_qr','zsjos_order_payment_method' UNION ALL SELECT 60,'公司二维码','company_qr','zsjos_order_payment_method' UNION ALL SELECT 50,'财务微信','finance_wechat','zsjos_order_payment_method' UNION ALL SELECT 40,'公司支付宝','company_alipay','zsjos_order_payment_method' UNION ALL SELECT 30,'等价/退差价换课','course_exchange','zsjos_order_payment_method' UNION ALL SELECT 20,'余额抵扣现金','balance_cash','zsjos_order_payment_method' UNION ALL SELECT 10,'充值预扣','prepaid_recharge','zsjos_order_payment_method'
) seed WHERE NOT EXISTS(SELECT 1 FROM `system_dict_data` d WHERE d.dict_type=seed.dict_type AND d.value=seed.value AND d.deleted=b'0');

INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6810,'成交审批','zsjos:sales-order:review',2,17,6735,'sales-order-approvals','ep:finished','zsjos/salesOrderApproval/index','ZsjosSalesOrderApproval',0,b'1',b'1',b'1','migration-V023',NOW(),'migration-V023',NOW(),b'0'),
(6811,'录入成交','zsjos:sales-order:create',3,15,6770,'','','',NULL,0,b'1',b'1',b'1','migration-V023',NOW(),'migration-V023',NOW(),b'0'),
(6812,'查询成交订单','zsjos:sales-order:query',3,1,6810,'','','',NULL,0,b'1',b'1',b'1','migration-V023',NOW(),'migration-V023',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id,6811,'migration-V023',NOW(),'migration-V023',NOW(),b'0',source.tenant_id
FROM `system_role_menu` source JOIN `system_menu` m ON m.id=source.menu_id AND m.permission='zsjos:lead:qualify' AND m.deleted=b'0'
WHERE source.deleted=b'0' AND NOT EXISTS(SELECT 1 FROM `system_role_menu` x WHERE x.role_id=source.role_id AND x.menu_id=6811 AND x.tenant_id=source.tenant_id AND x.deleted=b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V023','Add direct sales-order entry and dual-center approval','sales-order-dual-approval-v1');
