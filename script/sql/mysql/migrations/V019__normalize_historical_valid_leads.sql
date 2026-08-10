-- Normalizes historical valid leads that predate V018 opportunity creation.
-- Dependencies: V018 and existing lead/product/opportunity tables.
-- Scope: non-deleted tenant leads with status=valid and no initial_conversion opportunity.
-- Repeatability: unique opportunity key plus NOT EXISTS makes this safe to rerun.
-- Rollback limitation: inserted opportunity rows are business history and are not deleted automatically.

INSERT INTO `zsjos_opportunity`
(`person_id`,`type`,`lead_id`,`status`,`owner_user_id`,`expected_product_summary`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT l.`person_id`, 'initial_conversion', l.`id`, 'open', l.`owner_user_id`,
       COALESCE((
         SELECT JSON_ARRAYAGG(JSON_OBJECT(
           'spuRef', COALESCE(p.`spu_ref`, ''),
           'spuName', COALESCE(p.`spu_name_snapshot`, ''),
           'skuRef', COALESCE(p.`sku_ref`, ''),
           'skuName', COALESCE(p.`sku_name_snapshot`, ''),
           'primary', p.`is_primary`
         ))
         FROM `zsjos_lead_intended_product` p
         WHERE p.`tenant_id`=l.`tenant_id` AND p.`lead_id`=l.`id` AND p.`deleted`=b'0'
       ), JSON_ARRAY()),
       'migration-V019', NOW(), 'migration-V019', NOW(), b'0', l.`tenant_id`
FROM `zsjos_lead` l
WHERE l.`status`='valid' AND l.`deleted`=b'0' AND l.`owner_user_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_opportunity` o
    WHERE o.`tenant_id`=l.`tenant_id` AND o.`type`='initial_conversion'
      AND o.`lead_id`=l.`id` AND o.`deleted`=b'0'
  );

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V019','Normalize historical valid leads with initial opportunities','normalize-historical-valid-leads-v1');
