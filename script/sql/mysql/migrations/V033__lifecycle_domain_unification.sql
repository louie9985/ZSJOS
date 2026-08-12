-- V033: unify Customer, Lead, Opportunity, and first-purchase Order lifecycle invariants.
-- Dependencies: V032, zsjos_lead, zsjos_opportunity, zsjos_order, and System dictionaries.
-- Data scope: normalize lifecycle states and official owners; add active Customer uniqueness metadata.
-- Execution order: assert Customer relations, normalize states/owners/config, add generated keys/indexes, record V033.
-- Repeatability: updates are idempotent and every DDL statement is guarded by information_schema.
-- Rollback limitation: forward-only. Historical state/owner normalization is retained; do not remove unique keys after new writes rely on them.
-- Recovery: run the documented read-only duplicate queries, resolve conflicts manually, then rerun. This migration never merges or deletes business rows.

DROP PROCEDURE IF EXISTS `zsjos_v033_assert_customer_relations`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v033_assert_customer_relations`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `zsjos_lead` WHERE `deleted`=b'0'
    GROUP BY `tenant_id`, `person_id` HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V033 blocked: one customer has multiple active leads';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM `zsjos_opportunity` o
    LEFT JOIN `zsjos_lead` l
      ON l.`tenant_id`=o.`tenant_id` AND l.`id`=o.`lead_id` AND l.`deleted`=b'0'
    WHERE o.`deleted`=b'0'
    GROUP BY o.`tenant_id`, COALESCE(l.`person_id`, o.`person_id`)
    HAVING COUNT(*) > 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V033 blocked: one customer has multiple active opportunities';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v033_assert_customer_relations`();
DROP PROCEDURE `zsjos_v033_assert_customer_relations`;

UPDATE `zsjos_lead`
SET `status`='valid', `assignment_status`='owned', `updater`='migration-V033', `update_time`=NOW()
WHERE `deleted`=b'0' AND `status`='converted';

UPDATE `zsjos_lead` l
JOIN `zsjos_order` o
  ON o.`tenant_id`=l.`tenant_id` AND o.`lead_id`=l.`id` AND o.`person_id`=l.`person_id`
 AND o.`status`='effective' AND o.`deleted`=b'0'
SET l.`status`='won', l.`assignment_status`='owned', l.`updater`='migration-V033', l.`update_time`=NOW()
WHERE l.`deleted`=b'0' AND l.`status` IN ('valid','converted');

UPDATE `zsjos_opportunity` o
JOIN `zsjos_lead` l
  ON l.`tenant_id`=o.`tenant_id` AND l.`id`=o.`lead_id` AND l.`deleted`=b'0'
SET o.`person_id`=l.`person_id`, o.`owner_user_id`=l.`owner_user_id`,
    o.`updater`='migration-V033', o.`update_time`=NOW()
WHERE o.`deleted`=b'0' AND l.`owner_user_id` IS NOT NULL
  AND (o.`person_id`<>l.`person_id` OR o.`owner_user_id`<>l.`owner_user_id`);

UPDATE `system_dict_data`
SET `status`=1, `updater`='migration-V033', `update_time`=NOW()
WHERE `dict_type`='zsjos_lead_status' AND `value`='converted' AND `deleted`=b'0';

INSERT INTO `system_dict_data`
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 70,'已成交','won','zsjos_lead_status',0,'success','migration-V033',NOW(),'migration-V033',NOW(),b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_dict_data`
  WHERE `dict_type`='zsjos_lead_status' AND `value`='won' AND `deleted`=b'0'
);

UPDATE `zsjos_lead_inbox_filter_scheme`
SET `draft_config_json`=REPLACE(REPLACE(`draft_config_json`, '["valid","converted"]', '["valid","won"]'),
                                '"key":"converted","label":"已进入转化","sort":20,"enabled":true,"conditions":[{"field":"status","values":["converted"]}]',
                                '"key":"won","label":"已成交","sort":20,"enabled":true,"conditions":[{"field":"status","values":["won"]}]'),
    `published_config_json`=REPLACE(REPLACE(`published_config_json`, '["valid","converted"]', '["valid","won"]'),
                                    '"key":"converted","label":"已进入转化","sort":20,"enabled":true,"conditions":[{"field":"status","values":["converted"]}]',
                                    '"key":"won","label":"已成交","sort":20,"enabled":true,"conditions":[{"field":"status","values":["won"]}]'),
    `updater`='migration-V033', `update_time`=NOW()
WHERE `deleted`=b'0' AND (`draft_config_json` LIKE '%converted%' OR `published_config_json` LIKE '%converted%');

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='active_person_id'),
  'SELECT 1','ALTER TABLE `zsjos_lead` ADD COLUMN `active_person_id` bigint GENERATED ALWAYS AS (CASE WHEN (`deleted` = b''0'') THEN `person_id` ELSE NULL END) STORED COMMENT ''活动客户主客资唯一键'' AFTER `submission_idempotency_key`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND index_name='uk_tenant_active_person'),
  'SELECT 1','ALTER TABLE `zsjos_lead` ADD UNIQUE KEY `uk_tenant_active_person` (`tenant_id`,`active_person_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity' AND column_name='active_person_id'),
  'SELECT 1','ALTER TABLE `zsjos_opportunity` ADD COLUMN `active_person_id` bigint GENERATED ALWAYS AS (CASE WHEN (`deleted` = b''0'') THEN `person_id` ELSE NULL END) STORED COMMENT ''活动客户商机唯一键'' AFTER `tenant_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity' AND index_name='uk_tenant_active_person'),
  'SELECT 1','ALTER TABLE `zsjos_opportunity` ADD UNIQUE KEY `uk_tenant_active_person` (`tenant_id`,`active_person_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V033','Unify customer lead opportunity and first-purchase lifecycle','lifecycle-domain-unification-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V033','Unify customer lead opportunity and first-purchase lifecycle',
        SHA2('lifecycle-domain-unification-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`), `checksum`=VALUES(`checksum`);
