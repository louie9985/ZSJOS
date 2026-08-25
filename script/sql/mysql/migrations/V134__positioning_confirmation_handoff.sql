-- Positioning-card immutable submission history and public student confirmation handoff.
-- Scope: creates two ZSJOS-owned tables, adds one operator button permission, and converts only
-- active positioning cards waiting on the retired Partner-H5 confirmation entry.
-- Repeatability: CREATE TABLE IF NOT EXISTS, guarded menu/grant inserts, and an idempotent status update.
-- Rollback limitation: generated/consumed public links and decisions are not translated back into Partner-H5 state.

CREATE TABLE IF NOT EXISTS `zsjos_positioning_card_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `card_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `student_person_id` bigint NOT NULL,
  `service_relation_id` bigint NOT NULL,
  `submission_no` int NOT NULL,
  `director_user_id` bigint NOT NULL,
  `operator_user_id` bigint NOT NULL,
  `template_id` bigint DEFAULT NULL,
  `template_version_id` bigint DEFAULT NULL,
  `fields_snapshot_json` longtext,
  `values_snapshot_json` longtext,
  `dict_snapshot_json` longtext,
  `layer1_json` longtext,
  `layer2_json` longtext,
  `formula_json` longtext,
  `feasibility_json` longtext,
  `content_form_json` longtext,
  `compliance_json` longtext,
  `trial_end_date` date DEFAULT NULL,
  `professional_risk` bit(1) NOT NULL DEFAULT b'0',
  `status` varchar(32) NOT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `operator_reviewed_by_user_id` bigint DEFAULT NULL,
  `operator_reviewed_at` datetime DEFAULT NULL,
  `operator_review_comment` varchar(500) DEFAULT NULL,
  `student_decision` varchar(32) DEFAULT NULL,
  `student_decision_comment` varchar(500) DEFAULT NULL,
  `student_decided_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_card_submission` (`tenant_id`,`card_id`,`submission_no`,`deleted`),
  KEY `idx_tenant_student_account_submitted` (`tenant_id`,`student_person_id`,`account_id`,`submitted_at`),
  KEY `idx_tenant_card_submitted` (`tenant_id`,`card_id`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定位卡不可变提交快照';

CREATE TABLE IF NOT EXISTS `zsjos_positioning_confirmation_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `card_id` bigint NOT NULL,
  `submission_id` bigint NOT NULL,
  `token_hash` char(64) NOT NULL,
  `status` varchar(16) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `used_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`,`deleted`),
  KEY `idx_tenant_submission_status` (`tenant_id`,`submission_id`,`status`),
  KEY `idx_tenant_card_status` (`tenant_id`,`card_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定位卡学员确认链接';

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73477,'生成学员确认链接','zsjos:positioning-card:student-link-generate',3,77,7022,'','','','',0,b'1',b'1',b'1','V134',NOW(),'V134',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE permission='zsjos:positioning-card:student-link-generate' AND deleted=b'0')
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE id=73477);

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73477),`updater`='V134',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73477', '$');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`tenant_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT r.id,m.id,r.tenant_id,'V134',NOW(),'V134',NOW(),b'0'
FROM `system_role` r JOIN `system_menu` m
  ON m.permission='zsjos:positioning-card:student-link-generate' AND m.deleted=b'0'
WHERE r.code='new_media_operator' AND r.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=m.id
    AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

-- Only in-flight legacy cards require a compatibility snapshot. The old schema did not retain an exact
-- submission timestamp, so submitted_at remains NULL instead of inventing historical time.
INSERT INTO `zsjos_positioning_card_submission`
(`card_id`,`account_id`,`student_person_id`,`service_relation_id`,`submission_no`,`director_user_id`,`operator_user_id`,
 `template_id`,`template_version_id`,`fields_snapshot_json`,`values_snapshot_json`,`dict_snapshot_json`,`layer1_json`,`layer2_json`,
 `formula_json`,`feasibility_json`,`content_form_json`,`compliance_json`,`trial_end_date`,
 `professional_risk`,`status`,`submitted_at`,`operator_reviewed_by_user_id`,`operator_reviewed_at`,
 `operator_review_comment`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT c.id,c.account_id,c.student_person_id,c.service_relation_id,1,c.director_user_id,c.operator_user_id,
       c.template_id,c.template_version_id,c.fields_snapshot_json,c.values_snapshot_json,c.dict_snapshot_json,
       c.layer1_json,c.layer2_json,c.formula_json,c.feasibility_json,c.content_form_json,c.compliance_json,
       c.trial_end_date,c.professional_risk,'student_link_pending',NULL,c.operator_reviewed_by_user_id,
       c.operator_reviewed_at,c.operator_review_comment,0,'V134',NOW(),'V134',NOW(),b'0',c.tenant_id
FROM `zsjos_positioning_card` c
WHERE c.status='student_confirm' AND c.deleted=b'0'
  AND c.service_relation_id IS NOT NULL AND c.student_person_id IS NOT NULL AND c.operator_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `zsjos_positioning_card_submission` s
                  WHERE s.tenant_id=c.tenant_id AND s.card_id=c.id AND s.deleted=b'0');

UPDATE `zsjos_positioning_card`
SET `status`='student_link_pending',`version`=`version`+1,`updater`='V134',`update_time`=NOW()
WHERE `status`='student_confirm' AND `deleted`=b'0'
  AND EXISTS (SELECT 1 FROM `zsjos_positioning_card_submission` s
              WHERE s.tenant_id=zsjos_positioning_card.tenant_id
                AND s.card_id=zsjos_positioning_card.id AND s.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V134','positioning confirmation handoff',SHA2('V134__positioning_confirmation_handoff.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V134');
