-- UTF-8. Core V298; execute after V297 in both ledgers.
--
-- Scope: two scoped corrections of historical imported business facts, plus one label backfill.
--   1) 142 legacy academic Leads were imported with source_type='internal_new_media'. V273
--      established owner_identity='education' for them from the parttimecrm `academic_customer`
--      map (177 Leads) but deliberately never touched source_type, so the management list showed
--      "新媒体提交" for Leads whose owner business identity is 教务. source_type is the fact.
--   2) 528 historical Leads still hold a legacy English reason code (`wechat_add_failed`, ...).
--      Of these, 474 are invalid, and 54 are valid/submitted/suspended/won rows that kept the
--      reason recorded before they were restored or re-qualified; LeadValidityRestoration clears
--      the reason in the current code, but these rows predate that path and were never rewritten.
--      Every user-editable selector persists an enabled dictionary value, and all enabled entries
--      of zsjos_lead_invalid_reason are Chinese, so the stored code and its label snapshot are
--      both normalized to the enabled dictionary value. Rows already holding a Chinese value are
--      not matched by the predicate and stay untouched. The 474 invalid rows are the ones whose
--      label was actually missing; writing a label for the remaining 54 changes no rendering
--      because both display paths are gated on invalid_description or on status='invalid'.
--   3) 49 Leads record the enabled dictionary value 其他 as their only channel value while the
--      label snapshot is empty; the label is backfilled from that same value. One further Lead
--      records no channel value at all and is left as unrecorded.
--
-- Not in scope, and intentionally not changed:
--   * zsjos_performance_attribution.source_group. It is a projection of source_type, but rows
--     carrying org_source='frozen' are the fact-time organization snapshot by contract, so
--     rewriting the grouping is a business decision, not a data correction. Report only.
--   * Order field values and their label snapshots. The order label snapshot lives only in
--     zsjos_order_approval_round.order_snapshot.orderLabels; 498 Orders have no approval round and
--     73 further rounds carry no orderLabels, so no authoritative source exists to restore.
--   * sales_stage for Leads already marked won. V275 bounded its initialization to status<>'won'
--     and no historical sales stage exists in the legacy system.
--   * Leads whose lead_category or source_channel value is empty: no source value exists.
--   * counted_at (V273 decided not to backfill it), ownership, assignment history, business
--     status, amounts, orders, dictionaries, menus, permissions and roles.
--
-- Repeatable: every statement is bounded by the value predicate it corrects, so a second execution
-- changes no row and registers no new version. Preconditions, all corrections, the postconditions
-- and both ledger writes share one failure-controlled procedure; an error rolls back and re-signals.
--
-- The English-code mapping in section 2 is established by the legacy source itself: the legacy
-- system kept the code and its display text side by side, and the ten legacy codes correspond
-- one-to-one with the ten enabled dictionary entries of zsjos_lead_invalid_reason.
--
-- Rollback: section 1 restores source_type='internal_new_media' for Leads whose owner_identity is
-- 'education' and whose submission marker is 'legacy-%'; section 3 clears the label it wrote;
-- section 2 restores the English codes from the same mapping in reverse. The exact statements are
-- recorded in docs/operations/legacy-identity-label-repair.md. Reverting the code does not require
-- reverting this data.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v298_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v298_apply()
apply_body: BEGIN
  DECLARE v_leads INT DEFAULT 0;
  DECLARE v_invalid INT DEFAULT 0;
  DECLARE v_channel INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
-- BEGIN V298 COMPLETED REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309')
     OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309') THEN
    IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V298') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V298')) OR NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Older source repair lacks complete ledgers; reviewed recovery required';
    END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'
              AND source_type='internal_new_media' AND owner_identity='education'
              AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                WHERE b.repair_key IN ('media-assisted-source-v309','lead-source-confirmed-v309-r2')
                AND b.table_name='zsjos_lead' AND b.row_id=zsjos_lead.id AND b.column_name='__after__'
                AND JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.source_type'))=zsjos_lead.source_type
                AND JSON_EXTRACT(b.old_value,'$.source_user_id') <=> CAST(zsjos_lead.source_user_id AS JSON))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 education source correction incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0' AND invalid_reason IN
              ('wechat_add_failed','other','duplicate','no_answer','no_need',
               'refused','empty_number','not_target','fake_info','age_ineligible')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 invalid reason normalization incomplete';
  END IF;
  -- A normalized reason must carry its label snapshot; checking only the code would pass even if a
  -- left-to-right SET evaluation wrote the label as NULL.
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0' AND invalid_reason IN
              ('微信号添加不上','其他','重复客资','电话多次未接通','无相关需求',
               '客户明确拒绝','空号/停机','非目标客户','信息虚假','年龄不符合报考条件')
              AND (invalid_reason_label_snapshot IS NULL OR invalid_reason_label_snapshot='')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 invalid reason label snapshot incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'
              AND source_channel_label_snapshot IS NULL AND source_channel_id='其他') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 channel label backfill incomplete';
  END IF;


    SELECT 'V298' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
-- END V298 COMPLETED REPLAY



  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V297')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V297') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 requires Core V297 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_lead'
        AND column_name IN ('source_type','owner_identity','invalid_reason',
                            'invalid_reason_label_snapshot','source_channel_id',
                            'source_channel_label_snapshot')) <> 6 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 requires the zsjos_lead repair columns';
  END IF;
  IF (SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_lead_invalid_reason'
        AND deleted=b'0' AND status=0
        AND value IN ('微信号添加不上','其他','重复客资','电话多次未接通','无相关需求',
                      '客户明确拒绝','空号/停机','非目标客户','信息虚假','年龄不符合报考条件')) <> 10 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 requires the ten enabled invalid-reason dictionary entries';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_source_channel'
        AND deleted=b'0' AND value='其他') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 requires the enabled 其他 source-channel entry';
  END IF;

  START TRANSACTION;

  -- 1) academic self-sourced Leads stored as internal new-media
  UPDATE zsjos_lead
  SET source_type='education_self_sourced', updater='V298', update_time=NOW()
  WHERE deleted=b'0' AND source_type='internal_new_media' AND owner_identity='education';
  SET v_leads = ROW_COUNT();

  -- 2) legacy English invalid-reason codes -> enabled dictionary value and label snapshot.
  --    Both assignments read the mapping from a temporary table, never from the column being
  --    updated. Single-table UPDATE evaluates its SET list left to right, and a reference to a
  --    column already assigned earlier in that list yields the new value; a CASE over the column
  --    would therefore miss every branch and write NULL. Mapping through a joined table avoids
  --    that dependence on evaluation order.
  DROP TEMPORARY TABLE IF EXISTS v298_invalid_map;
  CREATE TEMPORARY TABLE v298_invalid_map (
    legacy_code varchar(32) NOT NULL PRIMARY KEY,
    dict_value  varchar(64) NOT NULL
  ) ENGINE=InnoDB;
  INSERT INTO v298_invalid_map(legacy_code, dict_value) VALUES
    ('wechat_add_failed','微信号添加不上'),
    ('other','其他'),
    ('duplicate','重复客资'),
    ('no_answer','电话多次未接通'),
    ('no_need','无相关需求'),
    ('refused','客户明确拒绝'),
    ('empty_number','空号/停机'),
    ('not_target','非目标客户'),
    ('fake_info','信息虚假'),
    ('age_ineligible','年龄不符合报考条件');
  UPDATE zsjos_lead l
  JOIN v298_invalid_map m ON m.legacy_code = l.invalid_reason
  SET l.invalid_reason = m.dict_value,
      l.invalid_reason_label_snapshot = m.dict_value,
      l.updater='V298', l.update_time=NOW()
  WHERE l.deleted=b'0';
  SET v_invalid = ROW_COUNT();
  DROP TEMPORARY TABLE IF EXISTS v298_invalid_map;

  -- 3) channel label snapshot for rows whose only recorded value is the enabled dictionary value 其他
  UPDATE zsjos_lead
  SET source_channel_label_snapshot='其他', updater='V298', update_time=NOW()
  WHERE deleted=b'0' AND source_channel_label_snapshot IS NULL AND source_channel_id='其他';
  SET v_channel = ROW_COUNT();

  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'
              AND source_type='internal_new_media' AND owner_identity='education') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 education source correction incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0' AND invalid_reason IN
              ('wechat_add_failed','other','duplicate','no_answer','no_need',
               'refused','empty_number','not_target','fake_info','age_ineligible')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 invalid reason normalization incomplete';
  END IF;
  -- A normalized reason must carry its label snapshot; checking only the code would pass even if a
  -- left-to-right SET evaluation wrote the label as NULL.
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0' AND invalid_reason IN
              ('微信号添加不上','其他','重复客资','电话多次未接通','无相关需求',
               '客户明确拒绝','空号/停机','非目标客户','信息虚假','年龄不符合报考条件')
              AND (invalid_reason_label_snapshot IS NULL OR invalid_reason_label_snapshot='')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 invalid reason label snapshot incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead WHERE deleted=b'0'
              AND source_channel_label_snapshot IS NULL AND source_channel_id='其他') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V298 channel label backfill incomplete';
  END IF;

  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V298','Legacy identity and label repair',SHA2('V298__legacy_identity_and_label_repair.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V298');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V298','Legacy identity and label repair',SHA2('V298__legacy_identity_and_label_repair.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V298');
  COMMIT;

  SELECT v_leads AS leads_corrected, v_invalid AS invalid_reasons_normalized,
         v_channel AS channel_labels_backfilled;
END$$
DELIMITER ;
CALL zsjos_v298_apply();
DROP PROCEDURE zsjos_v298_apply;
