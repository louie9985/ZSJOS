-- UTF-8. V322: reviewed performance attribution domain, no historical backfill or role grants.
-- Prerequisite: canonical V320 delivery schema or legacy applied V321 delivery schema.
-- Does not rewrite V319/V320/V321 ledger entries or checksums. Resolve renumber ledger drift separately.
-- Exact scope: nullable attribution column and this version's two ledger records only.
-- Repeatable; existing incompatible columns/menu structure fail without registering success.
-- DDL implicitly commits; partial column creation is retained and safely revalidated on replay.
-- Rollback: preserve the nullable column and existing snapshots; code rollback needs compatibility review.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v322_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v322_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V322')
      XOR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V322')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 ledger marker is one-sided';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V322'
      AND (description<>'业绩归属快照与结构复核' OR checksum<>SHA2('V322__performance_attribution_domain_review.sql',256)))
    OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V322'
      AND (description<>'业绩归属快照与结构复核' OR checksum<>SHA2('V322__performance_attribution_domain_review.sql',256)
           OR release_version<>'baseline')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 ledger identity conflict';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version IN ('V320','V321') AND description='独立交付业绩域') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 requires applied delivery domain schema';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND path='/zsjos' AND parent_id=0 AND type=1)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 canonical workbench root conflict';
 END IF;
 IF (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission LIKE 'zsjos:delivery-performance%')<>8 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery permission set conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu root ON root.id=m.parent_id
 WHERE m.deleted=0 AND root.deleted=0 AND root.path='/zsjos' AND root.parent_id=0 AND root.type=1
 AND m.permission='zsjos:sales-performance:query' AND m.type=2 AND m.path='sales-performance' AND m.component='zsjos/salesPerformance/index')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance:query')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance page contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:sales-performance:self'
 AND p.permission='zsjos:sales-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance:self')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance:self button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:sales-performance:department'
 AND p.permission='zsjos:sales-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance:department')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance:department button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:sales-performance:center'
 AND p.permission='zsjos:sales-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance:center')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance:center button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:sales-performance:detail'
 AND p.permission='zsjos:sales-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance:detail')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance:detail button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu root ON root.id=m.parent_id
 WHERE m.deleted=0 AND root.deleted=0 AND root.path='/zsjos' AND root.parent_id=0 AND root.type=1
 AND m.permission='zsjos:sales-performance-target:query' AND m.type=2 AND m.path='sales-performance-target' AND m.component='zsjos/salesPerformanceTarget/index')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance-target:query')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance-target page contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:sales-performance-target:update'
 AND p.permission='zsjos:sales-performance-target:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance-target:update')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance-target:update button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:sales-performance-target:configure'
 AND p.permission='zsjos:sales-performance-target:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:sales-performance-target:configure')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 sales-performance-target:configure button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu root ON root.id=m.parent_id
 WHERE m.deleted=0 AND root.deleted=0 AND root.path='/zsjos' AND root.parent_id=0 AND root.type=1
 AND m.permission='zsjos:delivery-performance:query' AND m.type=2 AND m.path='delivery-performance' AND m.component='zsjos/deliveryPerformance/index')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance:query')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance page contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:delivery-performance:self'
 AND p.permission='zsjos:delivery-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance:self')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance:self button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:delivery-performance:department'
 AND p.permission='zsjos:delivery-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance:department')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance:department button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:delivery-performance:center'
 AND p.permission='zsjos:delivery-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance:center')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance:center button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:delivery-performance:detail'
 AND p.permission='zsjos:delivery-performance:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance:detail')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance:detail button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu root ON root.id=m.parent_id
 WHERE m.deleted=0 AND root.deleted=0 AND root.path='/zsjos' AND root.parent_id=0 AND root.type=1
 AND m.permission='zsjos:delivery-performance-target:query' AND m.type=2 AND m.path='delivery-performance-target' AND m.component='zsjos/deliveryPerformanceTarget/index')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance-target:query')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance-target page contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:delivery-performance-target:update'
 AND p.permission='zsjos:delivery-performance-target:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance-target:update')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance-target:update button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
 WHERE m.deleted=0 AND p.deleted=0 AND m.type=3 AND m.permission='zsjos:delivery-performance-target:configure'
 AND p.permission='zsjos:delivery-performance-target:query')<>1
 OR (SELECT COUNT(*) FROM system_menu WHERE deleted=0 AND permission='zsjos:delivery-performance-target:configure')<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 delivery-performance-target:configure button contract conflict';
 END IF;

 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_org'
 AND column_name='business_domain' AND column_type='varchar(16)' AND is_nullable='NO' AND column_default='sales')<>1
 OR EXISTS(SELECT 1 FROM zsjos_performance_org WHERE business_domain NOT IN ('sales','delivery')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 zsjos_performance_org domain structure conflict';
 END IF;

 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target'
 AND column_name='business_domain' AND column_type='varchar(16)' AND is_nullable='NO' AND column_default='sales')<>1
 OR EXISTS(SELECT 1 FROM zsjos_performance_target WHERE business_domain NOT IN ('sales','delivery')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 zsjos_performance_target domain structure conflict';
 END IF;

 IF (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND index_name='uk_target')
 <> 'tenant_id,business_domain,scope_type,scope_id,period_type,period_start,deleted'
 OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target'
 AND index_name='uk_target' AND non_unique=0 AND sub_part IS NULL)<>7 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 target unique index conflict';
 END IF;

 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_performance_attribution' AND column_name='business_domain') THEN
  ALTER TABLE zsjos_performance_attribution ADD COLUMN business_domain varchar(16) NULL DEFAULT NULL COMMENT '事实发生时业务域，未知不推定';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='zsjos_performance_attribution' AND column_name='business_domain' AND column_type='varchar(16)'
 AND is_nullable='YES' AND column_default IS NULL)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 attribution domain structure conflict';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_performance_attribution WHERE business_domain IS NOT NULL AND business_domain NOT IN ('sales','delivery')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V322 attribution domain value conflict';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V322','业绩归属快照与结构复核',SHA2('V322__performance_attribution_domain_review.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V322');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V322','业绩归属快照与结构复核',SHA2('V322__performance_attribution_domain_review.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V322');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v322_apply();
DROP PROCEDURE zsjos_v322_apply;
