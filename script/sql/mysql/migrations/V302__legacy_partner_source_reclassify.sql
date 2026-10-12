-- UTF-8. Core V302; execute after V301 in both ledgers.
--
-- Scope: reclassify the imported Leads whose recorded source does not match their recorded
-- provider identity, and align the order approval snapshots that froze the same wrong label.
--
-- Background. The 2026-09-16 legacy merge mapped the old system's single source column to
-- zsjos_lead.source_type. That column had only 兼职/新媒体 meanings, so every Lead it carried
-- arrived as source_type='partner' even when the recorded provider was an internal employee:
-- provider_owner_type='system_user' with provider_owner_id pointing at a live system_users row,
-- and partner_id NULL. The old system's own 兼职 name field equals the submitter name on 4041 of
-- its 4721 part_time rows, so it never identified a distinct third-party submitter. The业务
-- decision is that these people are new-media employees today, so the recorded source is corrected
-- to match the recorded provider.
--
-- Predicate. source_type='partner' AND provider_owner_type='system_user' AND partner_id IS NULL.
-- That is 4112 Leads, all of them legacy imports (lead_no LIKE 'LD%'). It excludes:
--   * the 709 genuine partner Leads, which carry provider_owner_type='partner' and a partner_id;
--   * any future row whose provider is a Partner subject.
-- The companion columns keep their meaning: provider_owner_type, provider_owner_id, partner_id,
-- source_user_id, counted_at and every contribution snapshot are not changed.
--
-- Downstream paths that already treat these rows as employee submissions, and are therefore
-- unaffected: cashback eligibility (CashbackServiceImpl reads provider_owner_type, and these rows
-- never qualified), withdrawal (reads the cashback's own partner_id), the media screen
-- (LeadMapper maps provider_owner_type='partner' to part_time, so these rows stay 'direct'),
-- the employee submitter advanced filter (AdvancedFilterRelations already excludes a Partner
-- subject and falls through to source_user_id), and the performance source grouping (partner and
-- internal_new_media both map to inbound).
--
-- Section 2 rewrites four keys inside zsjos_order_approval_round.order_snapshot.leadProfile for the
-- rounds whose Lead is reclassified, so the frozen order detail no longer shows 兼职提交 beside an
-- employee name. sourceSubjectType becomes ADMIN and sourceSubjectId becomes the Lead's
-- source_user_id, matching how the current projection builds the profile
-- (SalesOrderServiceImpl.buildLeadProfile). sourceUserName already holds the employee name and is
-- left alone. Rounds belonging to genuine partner Leads are not matched.
--
-- Repeatable: each statement is bounded by the value it corrects, so a second execution changes
-- nothing and registers no new version. Pre-images are captured once per (repair_key, table, row,
-- column) and never overwritten, so a replay cannot clobber the rollback state.
--
-- Rollback: restore zsjos_lead.source_type from repair_key 'lead-partner-source-reclassify-20261010'
-- and the four snapshot keys from 'order-lead-profile-source-20261010'. The exact statements are in
-- docs/operations/legacy-partner-source-reclassify.md. Section 2's pre-image rows record each key
-- separately, including the JSON null that sourceSubjectId originally held.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v302_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v302_apply()
BEGIN
  DECLARE v_leads INT DEFAULT 0;
  DECLARE v_rounds INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V301')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V301') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 requires Core V301 in both ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_lead' AND column_name='provider_owner_type') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 requires zsjos_lead.provider_owner_type';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name='zsjos_data_repair_backup') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 requires zsjos_data_repair_backup for pre-images';
  END IF;
  -- The genuine partner population must be intact before this migration runs.
  IF NOT EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'
        AND source_type='partner' AND provider_owner_type='partner' AND partner_id IS NOT NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 requires the genuine partner population to be present';
  END IF;

  START TRANSACTION;

  -- 1) imported employee-submitted Leads recorded as 兼职提交
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'lead-partner-source-reclassify-20261010','zsjos_lead',l.id,'source_type',l.source_type
    FROM zsjos_lead l
   WHERE l.deleted=b'0' AND l.source_type='partner'
     AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='lead-partner-source-reclassify-20261010'
                       AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='source_type');

  UPDATE zsjos_lead
     SET source_type='internal_new_media', updater='V302', update_time=NOW()
   WHERE deleted=b'0' AND source_type='partner'
     AND provider_owner_type='system_user' AND partner_id IS NULL;
  SET v_leads = ROW_COUNT();

  -- 2) the four frozen profile keys on the rounds of those Leads
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-lead-profile-source-20261010','zsjos_order_approval_round',r.id,
         'order_snapshot.leadProfile.sourceType', r.order_snapshot->>'$.leadProfile.sourceType'
    FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
    JOIN zsjos_lead l ON l.id=o.lead_id
   WHERE o.deleted=b'0' AND l.deleted=b'0'
     AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
     AND r.order_snapshot->>'$.leadProfile.sourceType'='partner'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-lead-profile-source-20261010'
                       AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
                       AND b.column_name='order_snapshot.leadProfile.sourceType');
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-lead-profile-source-20261010','zsjos_order_approval_round',r.id,
         'order_snapshot.leadProfile.sourceLabel', r.order_snapshot->>'$.leadProfile.sourceLabel'
    FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
    JOIN zsjos_lead l ON l.id=o.lead_id
   WHERE o.deleted=b'0' AND l.deleted=b'0'
     AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
     AND r.order_snapshot->>'$.leadProfile.sourceType'='partner'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-lead-profile-source-20261010'
                       AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
                       AND b.column_name='order_snapshot.leadProfile.sourceLabel');
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-lead-profile-source-20261010','zsjos_order_approval_round',r.id,
         'order_snapshot.leadProfile.sourceSubjectType', r.order_snapshot->>'$.leadProfile.sourceSubjectType'
    FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
    JOIN zsjos_lead l ON l.id=o.lead_id
   WHERE o.deleted=b'0' AND l.deleted=b'0'
     AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
     AND r.order_snapshot->>'$.leadProfile.sourceType'='partner'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-lead-profile-source-20261010'
                       AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
                       AND b.column_name='order_snapshot.leadProfile.sourceSubjectType');
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'order-lead-profile-source-20261010','zsjos_order_approval_round',r.id,
         'order_snapshot.leadProfile.sourceSubjectId',
         CASE WHEN JSON_TYPE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId'))='NULL'
                   OR JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId') IS NULL
              THEN '<json-null>' ELSE r.order_snapshot->>'$.leadProfile.sourceSubjectId' END
    FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
    JOIN zsjos_lead l ON l.id=o.lead_id
   WHERE o.deleted=b'0' AND l.deleted=b'0'
     AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
     AND r.order_snapshot->>'$.leadProfile.sourceType'='partner'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='order-lead-profile-source-20261010'
                       AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
                       AND b.column_name='order_snapshot.leadProfile.sourceSubjectId');

  -- Both sides of every assignment come from the joined Lead, never from the JSON already written,
  -- so a single nested JSON_SET cannot read a value it has just replaced.
  UPDATE zsjos_order_approval_round r
    JOIN zsjos_order o ON o.id=r.order_id
    JOIN zsjos_lead l ON l.id=o.lead_id
     SET r.order_snapshot=JSON_SET(r.order_snapshot,
           '$.leadProfile.sourceType','internal_new_media',
           '$.leadProfile.sourceLabel','新媒体提交',
           '$.leadProfile.sourceSubjectType','ADMIN',
           '$.leadProfile.sourceSubjectId',l.source_user_id),
         r.updater='V302', r.update_time=NOW()
   WHERE o.deleted=b'0' AND l.deleted=b'0'
     AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
     AND r.order_snapshot->>'$.leadProfile.sourceType'='partner';
  SET v_rounds = ROW_COUNT();

  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0' AND source_type='partner'
              AND provider_owner_type='system_user' AND partner_id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 lead source reclassification incomplete';
  END IF;
  -- The genuine partner population must be untouched: every Partner-provider row keeps its source.
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'
              AND provider_owner_type='partner' AND source_type<>'partner') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 changed a genuine partner Lead';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
            JOIN zsjos_lead l ON l.id=o.lead_id
            WHERE o.deleted=b'0' AND l.deleted=b'0'
              AND l.provider_owner_type='system_user' AND l.partner_id IS NULL
              AND r.order_snapshot->>'$.leadProfile.sourceType'='partner') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 order snapshot relabel incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r
            WHERE r.updater='V302'
              AND (JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceType'))<>'internal_new_media'
                OR JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceLabel'))<>'新媒体提交'
                OR JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectType'))<>'ADMIN'
                OR JSON_TYPE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId'))<>'INTEGER')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V302 wrote an unexpected snapshot profile value';
  END IF;

  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V302','Legacy partner source reclassification',SHA2('V302__legacy_partner_source_reclassify.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V302');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V302','Legacy partner source reclassification',SHA2('V302__legacy_partner_source_reclassify.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V302');
  COMMIT;

  SELECT v_leads AS leads_reclassified, v_rounds AS order_rounds_relabelled;
END$$
DELIMITER ;
CALL zsjos_v302_apply();
DROP PROCEDURE zsjos_v302_apply;
