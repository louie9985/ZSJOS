-- V122 repairs partner Lead source-account provenance and normalizes missing root cashback defaults.
-- Non-destructive, tenant-scoped, repeatable. Apply after V121.
-- Only rows with exactly one non-deleted partner account for the same partner are repaired.
-- Ambiguous or invalid rows are intentionally left unchanged for manual review.

UPDATE `zsjos_lead` l
JOIN (
    SELECT `tenant_id`, `partner_id`, MIN(`id`) AS `account_id`
    FROM `zsjos_partner_account`
    WHERE `deleted`=b'0'
      AND `status`=0
    GROUP BY `tenant_id`, `partner_id`
    HAVING COUNT(*)=1
) a ON a.`tenant_id`=l.`tenant_id` AND a.`partner_id`=l.`partner_id`
SET l.`source_user_id`=a.`account_id`
WHERE l.`deleted`=b'0'
  AND l.`source_type`='partner'
  AND l.`source_user_id` IS NULL;

UPDATE `zsjos_product_category`
SET `default_valid_cashback_amount`=COALESCE(`default_valid_cashback_amount`,10.00),
    `default_deal_cashback_rate`=COALESCE(`default_deal_cashback_rate`,0.1000)
WHERE `deleted`=b'0'
  AND `parent_id`=0;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V122','Repair partner Lead source account and cashback defaults','V122__repair_partner_lead_source_and_cashback_defaults.sql')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V122','Repair partner Lead source account and cashback defaults',SHA2('V122__repair_partner_lead_source_and_cashback_defaults.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`),`installed_at`=VALUES(`installed_at`);
