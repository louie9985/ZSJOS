-- UTF-8. Core V301; execute after V300 in both ledgers.
--
-- Scope: recompute zsjos_performance_attribution.source_group for the Leads whose source_type was
-- corrected by V298, so the performance report's 引流/非引流 populations match the canonical rule.
--
-- Background. source_group is written when the business fact happens (PerformanceSnapshotService)
-- from lead.source_type via sourceGroup(): self-sourced -> self, internal_new_media/partner ->
-- inbound, otherwise unknown; an ORDER fact whose order_type is repurchase is repurchase. The
-- offline backfills that wrote the historical rows ran while 142 academic self-sourced Leads still
-- read internal_new_media, so they stored inbound. V298 corrected those Leads; this migration brings
-- the derived grouping in line with the same rule, using the current Lead value.
--
-- Bounded to non-fact-time rows. Rows carrying org_source='frozen' are the organization snapshot
-- taken when the fact occurred and are protected by contract; they are never rewritten here, only
-- the rows a backfill wrote (org_source='current') or left unset. Rows whose Lead no longer exists
-- are not matched, because the source cannot be established and inventing one is not a correction.
--
-- Repeatable: the assignment is bounded by source_group='inbound', so a second execution changes
-- no row and registers no new version.
--
-- Rollback: the pre-images under repair_key 'performance-source-group-20261010' restore the prior
-- value. Exact statements are in docs/operations/legacy-order-payment-realign.md.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v301_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v301_apply()
apply_body: BEGIN
  DECLARE v_rows INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
-- BEGIN V301 COMPLETED REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309')
     OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309') THEN
    IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V301') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V301')) OR NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Older source repair lacks complete ledgers; reviewed recovery required';
    END IF;
  IF EXISTS(SELECT 1 FROM zsjos_performance_attribution p
              JOIN zsjos_lead l ON l.id=p.lead_id AND l.deleted=b'0'
            WHERE IFNULL(p.org_source,'') <> 'frozen'
              AND p.source_group='inbound'
              AND l.source_type IN ('sales_self_sourced','education_self_sourced')
              AND NOT (p.fact_type='ORDER' AND EXISTS(SELECT 1 FROM zsjos_order o
                         WHERE o.id=p.fact_id AND o.deleted=b'0' AND o.order_type='repurchase'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V301 attribution source-group recompute incomplete';
  END IF;
  -- Fact-time rows must be untouched by this migration.
  IF EXISTS(SELECT 1 FROM zsjos_performance_attribution
            WHERE org_source='frozen' AND updater='V301') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V301 must not rewrite a fact-time attribution row';
  END IF;


    SELECT 'V301' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
-- END V301 COMPLETED REPLAY



  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V300')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V300') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V301 requires Core V300 in both ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_performance_attribution' AND column_name='source_group') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V301 requires zsjos_performance_attribution.source_group';
  END IF;

  START TRANSACTION;

  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'performance-source-group-20261010','zsjos_performance_attribution',p.id,'source_group',p.source_group
    FROM zsjos_performance_attribution p
    JOIN zsjos_lead l ON l.id=p.lead_id AND l.deleted=b'0'
   WHERE IFNULL(p.org_source,'') <> 'frozen'
     AND p.source_group='inbound'
     AND l.source_type IN ('sales_self_sourced','education_self_sourced')
     AND NOT (p.fact_type='ORDER' AND EXISTS(SELECT 1 FROM zsjos_order o
                WHERE o.id=p.fact_id AND o.deleted=b'0' AND o.order_type='repurchase'))
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='performance-source-group-20261010'
                       AND b.table_name='zsjos_performance_attribution' AND b.row_id=p.id
                       AND b.column_name='source_group');

  UPDATE zsjos_performance_attribution p
    JOIN zsjos_lead l ON l.id=p.lead_id AND l.deleted=b'0'
     SET p.source_group='self', p.updater='V301', p.update_time=NOW()
   WHERE IFNULL(p.org_source,'') <> 'frozen'
     AND p.source_group='inbound'
     AND l.source_type IN ('sales_self_sourced','education_self_sourced')
     AND NOT (p.fact_type='ORDER' AND EXISTS(SELECT 1 FROM zsjos_order o
                WHERE o.id=p.fact_id AND o.deleted=b'0' AND o.order_type='repurchase'));
  SET v_rows = ROW_COUNT();

  IF EXISTS(SELECT 1 FROM zsjos_performance_attribution p
              JOIN zsjos_lead l ON l.id=p.lead_id AND l.deleted=b'0'
            WHERE IFNULL(p.org_source,'') <> 'frozen'
              AND p.source_group='inbound'
              AND l.source_type IN ('sales_self_sourced','education_self_sourced')
              AND NOT (p.fact_type='ORDER' AND EXISTS(SELECT 1 FROM zsjos_order o
                         WHERE o.id=p.fact_id AND o.deleted=b'0' AND o.order_type='repurchase'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V301 attribution source-group recompute incomplete';
  END IF;
  -- Fact-time rows must be untouched by this migration.
  IF EXISTS(SELECT 1 FROM zsjos_performance_attribution
            WHERE org_source='frozen' AND updater='V301') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V301 must not rewrite a fact-time attribution row';
  END IF;

  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V301','Performance source group recompute',SHA2('V301__performance_source_group_recompute.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V301');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V301','Performance source group recompute',SHA2('V301__performance_source_group_recompute.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V301');
  COMMIT;

  SELECT v_rows AS source_group_rows_recomputed;
END$$
DELIMITER ;
CALL zsjos_v301_apply();
DROP PROCEDURE zsjos_v301_apply;
