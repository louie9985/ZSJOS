-- UTF-8. Core V303; execute after V302 in both ledgers.
--
-- Scope: repoint every business reference that still targets one of the six retired employee
-- accounts onto the live account that inherited the person, in the Lead and order surfaces.
--
-- Background. The 2026-09-16 legacy merge and the 2026-09-21 increment imported Leads and orders
-- against employee accounts that were later retired into duplicate rows: 陈薇 232, 莫才巧 259,
-- 钟茂源 54, 汪雅琪 415, 梁颖 418, 汪雅琪2 524. Their live counterparts already exist, are active
-- (deleted=0, status=0) and already hold the ownership side of the same records: 20, 19, 27, 51,
-- 400, 30. The retired rows keep deleted=1, so every read path that resolves an employee name
-- through the live user map renders 未知账号 for them, and the employee-submitter filter and the
-- submitter-scoped order queries cannot match the person who actually submitted the record.
--
-- The 8 Leads whose source_user_id=54 and whose provider_owner_type='partner' are NOT in scope:
-- for those rows 54 is zsjos_partner_account.id 54 (partner_id 52, enabled), a numeric collision
-- with the retired employee, and three of them are new-system submissions created 2026-10-08/09.
-- The same reasoning excludes the 19 partner-provider rows whose provider_owner_id is 54, and the
-- zsjos_business_audit_log / zsjos_lead_assignment_history rows written by the partner portal.
--
-- Predicate surfaces and expected row counts:
--   zsjos_lead.source_user_id            244  (excludes provider_owner_type='partner')
--   zsjos_lead.provider_owner_id         188  (only where provider_owner_type='system_user')
--   zsjos_lead.source_provider_user_id    38
--   zsjos_order.submitter_user_id         26
--   zsjos_order.imported_actor_snapshot.submitter.orderUserId            26
--   zsjos_order_approval_round.order_snapshot.leadProfile.sourceSubjectId 9
--
-- The order snapshot keys are contract-coupled, not decorative. SalesOrderSnapshot.restoreActor
-- only fills a missing historical actor name when imported.orderUserId equals the order's current
-- submitter_user_id, and SalesOrderServiceImpl.buildLeadProfile derives leadProfile.sourceSubjectId
-- from the Lead's source_user_id. Repointing the plain columns without their snapshots would break
-- that gate and leave the frozen profile naming a retired account.
--
-- Deliberately NOT changed: the partner-side rows above; imported_actor_snapshot.submitter.
-- sourceEmployeeId, an explicitly typed LEGACY_EMPLOYEE id that the reader refuses to consume as
-- an ADMIN id; other numeric collisions carrying the same small values in unrelated fields
-- (legacyOrderId, sourceOrderId, followUpRecordId, personId, contentId, infraFileId,
-- legacyOrderItemId); the 125 zsjos_cashback.beneficiary_user_id rows, which are money and stay on
-- their disabled partner subjects pending a separate decision; notification outbox payloads
-- (queued history); system_user_role / system_user_post rows of the retired accounts, which are
-- authorization facts left to System administration.
--
-- Repeatable: every statement is bounded by the retired value it replaces, and no replacement id
-- equals a retired id (232/259/54/415/418/524 vs 20/19/27/51/400/30), so a second run changes 0
-- rows. Pre-images are captured once per (repair_key, table, row, column) and never overwritten.
--
-- Rollback: restore the six surfaces from repair_key 'retired-submitter-repoint-20261010'. The
-- exact statements are in docs/operations/legacy-retired-account-reference-repoint.md.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v303_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v303_apply()
apply_body: BEGIN
  DECLARE v_lead_source INT DEFAULT 0;
  DECLARE v_lead_provider INT DEFAULT 0;
  DECLARE v_lead_spuid INT DEFAULT 0;
  DECLARE v_order_submitter INT DEFAULT 0;
  DECLARE v_order_actor INT DEFAULT 0;
  DECLARE v_round_subject INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
-- BEGIN V303 COMPLETED REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309')
     OR EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309') THEN
    IF NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V303') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V303')) OR NOT (EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V309') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V309')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Older source repair lacks complete ledgers; reviewed recovery required';
    END IF;
  -- Postconditions: no residual reference in any of the six surfaces.
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0'
              AND IFNULL(l.provider_owner_type,'')<>'partner'
              AND l.source_user_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lead.source_user_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.provider_owner_type='system_user'
              AND l.provider_owner_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lead.provider_owner_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0'
              AND l.source_provider_user_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lead.source_provider_user_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0'
              AND o.submitter_user_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 order.submitter_user_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0'
              AND CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
                  IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 imported actor snapshot repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r
             WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId')) AS UNSIGNED)
                   IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 frozen profile subject repoint incomplete';
  END IF;

  -- The partner side must be untouched: 54 there is a partner account, not an employee.
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.provider_owner_type='partner'
              AND l.updater='V303') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 rewrote a partner-provider Lead';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead) AND NOT EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.provider_owner_type='partner'
                 AND l.source_user_id=54) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lost the partner-account source rows';
  END IF;

  -- The order snapshot gate must hold row by row after the write.
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0' AND o.updater='V303'
              AND CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
                  <> o.submitter_user_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 order actor snapshot and submitter disagree';
  END IF;
  -- The provider-notification invariant must hold after the write.
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.updater='V303'
              AND l.source_provider_user_id IS NOT NULL
              AND l.source_provider_user_id<>l.provider_owner_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 provider selection no longer matches attribution';
  END IF;
  -- The legacy employee id must still be present on every rewritten order.
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0' AND o.updater='V303'
              AND JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.sourceEmployeeId') IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 dropped a legacy employee id';
  END IF;

  -- Frozen profile keys other than the subject must be intact on every rewritten round.
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r WHERE r.updater='V303'
              AND (JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceType') IS NULL
                OR JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceLabel') IS NULL
                OR JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectType') IS NULL)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 dropped a frozen profile key';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r WHERE r.updater='V303' AND JSON_VALID(r.order_snapshot)=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 produced an invalid order snapshot';
  END IF;


    SELECT 'V303' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
-- END V303 COMPLETED REPLAY



  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V302')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V302') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 requires Core V302 in both ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_lead' AND column_name='source_provider_user_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 requires zsjos_lead.source_provider_user_id';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='zsjos_order' AND column_name='imported_actor_snapshot') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 requires zsjos_order.imported_actor_snapshot';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name='zsjos_data_repair_backup') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 requires zsjos_data_repair_backup for pre-images';
  END IF;
  -- Empty installs have no retired-account history; populated/recovery paths keep every identity guard.
  IF EXISTS(SELECT 1 FROM zsjos_lead) OR EXISTS(SELECT 1 FROM zsjos_order) OR EXISTS(SELECT 1 FROM zsjos_order_approval_round) OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='retired-submitter-repoint-20261010') THEN
  -- Every replacement must be a live employee account before anything is rewritten.
  IF EXISTS(SELECT 1 FROM (SELECT 20 AS id UNION ALL SELECT 19 UNION ALL SELECT 27
                            UNION ALL SELECT 51 UNION ALL SELECT 400 UNION ALL SELECT 30) t
             WHERE NOT EXISTS(SELECT 1 FROM system_users u
                               WHERE u.id=t.id AND u.deleted=b'0' AND u.status=0)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 requires all six replacement accounts to be active';
  END IF;
  -- The retired rows must still be present and still deleted, not revived accounts.
  IF EXISTS(SELECT 1 FROM (SELECT 232 AS id UNION ALL SELECT 259 UNION ALL SELECT 54
                            UNION ALL SELECT 415 UNION ALL SELECT 418 UNION ALL SELECT 524) t
             WHERE NOT EXISTS(SELECT 1 FROM system_users u
                               WHERE u.id=t.id AND u.deleted=b'1')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 expected six retired duplicate accounts';
  END IF;
  -- The partner-account collision must still be a partner account, not an employee row.
  IF NOT EXISTS(SELECT 1 FROM zsjos_partner_account pa WHERE pa.id=54 AND pa.partner_id=52) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 partner-account collision check failed';
  END IF;

  END IF;

  DROP TEMPORARY TABLE IF EXISTS zsjos_v303_map;
  CREATE TEMPORARY TABLE zsjos_v303_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
  INSERT INTO zsjos_v303_map(old_id,new_id) VALUES
    (232,20),(259,19),(54,27),(415,51),(418,400),(524,30);

  START TRANSACTION;

  -- 1) zsjos_lead.source_user_id (submitter fact)
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'retired-submitter-repoint-20261010','zsjos_lead',l.id,'source_user_id',CAST(l.source_user_id AS CHAR)
    FROM zsjos_lead l JOIN zsjos_v303_map m ON m.old_id=l.source_user_id
   WHERE l.deleted=b'0' AND IFNULL(l.provider_owner_type,'')<>'partner'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='retired-submitter-repoint-20261010'
                       AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='source_user_id');
  UPDATE zsjos_lead l JOIN zsjos_v303_map m ON m.old_id=l.source_user_id
     SET l.source_user_id=m.new_id, l.updater='V303', l.update_time=l.update_time
   WHERE l.deleted=b'0' AND IFNULL(l.provider_owner_type,'')<>'partner';
  SET v_lead_source = ROW_COUNT();

  -- 2) zsjos_lead.provider_owner_id (provider attribution, employee side only)
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'retired-submitter-repoint-20261010','zsjos_lead',l.id,'provider_owner_id',CAST(l.provider_owner_id AS CHAR)
    FROM zsjos_lead l JOIN zsjos_v303_map m ON m.old_id=l.provider_owner_id
   WHERE l.deleted=b'0' AND l.provider_owner_type='system_user'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='retired-submitter-repoint-20261010'
                       AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='provider_owner_id');
  UPDATE zsjos_lead l JOIN zsjos_v303_map m ON m.old_id=l.provider_owner_id
     SET l.provider_owner_id=m.new_id, l.updater='V303', l.update_time=l.update_time
   WHERE l.deleted=b'0' AND l.provider_owner_type='system_user';
  SET v_lead_provider = ROW_COUNT();

  -- 3) zsjos_lead.source_provider_user_id. This column must keep equalling provider_owner_id,
  --    which LeadNotifySceneProvider asserts before sending the provider notification.
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'retired-submitter-repoint-20261010','zsjos_lead',l.id,'source_provider_user_id',CAST(l.source_provider_user_id AS CHAR)
    FROM zsjos_lead l JOIN zsjos_v303_map m ON m.old_id=l.source_provider_user_id
   WHERE l.deleted=b'0'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='retired-submitter-repoint-20261010'
                       AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='source_provider_user_id');
  UPDATE zsjos_lead l JOIN zsjos_v303_map m ON m.old_id=l.source_provider_user_id
     SET l.source_provider_user_id=m.new_id, l.updater='V303', l.update_time=l.update_time
   WHERE l.deleted=b'0';
  SET v_lead_spuid = ROW_COUNT();

  -- 4) zsjos_order.submitter_user_id
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'retired-submitter-repoint-20261010','zsjos_order',o.id,'submitter_user_id',CAST(o.submitter_user_id AS CHAR)
    FROM zsjos_order o JOIN zsjos_v303_map m ON m.old_id=o.submitter_user_id
   WHERE o.deleted=b'0'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='retired-submitter-repoint-20261010'
                       AND b.table_name='zsjos_order' AND b.row_id=o.id AND b.column_name='submitter_user_id');
  UPDATE zsjos_order o JOIN zsjos_v303_map m ON m.old_id=o.submitter_user_id
     SET o.submitter_user_id=m.new_id, o.updater='V303', o.update_time=o.update_time
   WHERE o.deleted=b'0';
  SET v_order_submitter = ROW_COUNT();

  -- 5) imported_actor_snapshot.submitter.orderUserId must stay equal to submitter_user_id, or
  --    SalesOrderSnapshot.restoreActor stops restoring the historical actor name. The legacy
  --    sourceEmployeeId is a different identity space and is never rewritten.
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'retired-submitter-repoint-20261010','zsjos_order',o.id,
         'imported_actor_snapshot.submitter.orderUserId',
         JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId'))
    FROM zsjos_order o JOIN zsjos_v303_map m
      ON m.old_id=CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
   WHERE o.deleted=b'0'
     AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='retired-submitter-repoint-20261010'
                       AND b.table_name='zsjos_order' AND b.row_id=o.id
                       AND b.column_name='imported_actor_snapshot.submitter.orderUserId');
  UPDATE zsjos_order o JOIN zsjos_v303_map m
      ON m.old_id=CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
     SET o.imported_actor_snapshot=JSON_SET(o.imported_actor_snapshot,'$.submitter.orderUserId',m.new_id),
         o.updater='V303', o.update_time=o.update_time
   WHERE o.deleted=b'0';
  SET v_order_actor = ROW_COUNT();

  -- 6) the frozen order profile subject, so a frozen profile and a freshly built one agree. The
  --    replacement comes from the map, never read back out of the JSON being replaced.
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'retired-submitter-repoint-20261010','zsjos_order_approval_round',r.id,
         'order_snapshot.leadProfile.sourceSubjectId',
         JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId'))
    FROM zsjos_order_approval_round r JOIN zsjos_v303_map m
      ON m.old_id=CAST(JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId')) AS UNSIGNED)
   WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
                     WHERE b.repair_key='retired-submitter-repoint-20261010'
                       AND b.table_name='zsjos_order_approval_round' AND b.row_id=r.id
                       AND b.column_name='order_snapshot.leadProfile.sourceSubjectId');
  UPDATE zsjos_order_approval_round r JOIN zsjos_v303_map m
      ON m.old_id=CAST(JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId')) AS UNSIGNED)
     SET r.order_snapshot=JSON_SET(r.order_snapshot,'$.leadProfile.sourceSubjectId',m.new_id),
         r.updater='V303', r.update_time=r.update_time;
  SET v_round_subject = ROW_COUNT();

  -- Postconditions: no residual reference in any of the six surfaces.
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0'
              AND IFNULL(l.provider_owner_type,'')<>'partner'
              AND l.source_user_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lead.source_user_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.provider_owner_type='system_user'
              AND l.provider_owner_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lead.provider_owner_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0'
              AND l.source_provider_user_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lead.source_provider_user_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0'
              AND o.submitter_user_id IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 order.submitter_user_id repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0'
              AND CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
                  IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 imported actor snapshot repoint incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r
             WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectId')) AS UNSIGNED)
                   IN (232,259,54,415,418,524)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 frozen profile subject repoint incomplete';
  END IF;

  -- The partner side must be untouched: 54 there is a partner account, not an employee.
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.provider_owner_type='partner'
              AND l.updater='V303') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 rewrote a partner-provider Lead';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_lead) AND NOT EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.provider_owner_type='partner'
                 AND l.source_user_id=54) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 lost the partner-account source rows';
  END IF;

  -- The order snapshot gate must hold row by row after the write.
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0' AND o.updater='V303'
              AND CAST(JSON_UNQUOTE(JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.orderUserId')) AS UNSIGNED)
                  <> o.submitter_user_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 order actor snapshot and submitter disagree';
  END IF;
  -- The provider-notification invariant must hold after the write.
  IF EXISTS(SELECT 1 FROM zsjos_lead l WHERE l.deleted=b'0' AND l.updater='V303'
              AND l.source_provider_user_id IS NOT NULL
              AND l.source_provider_user_id<>l.provider_owner_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 provider selection no longer matches attribution';
  END IF;
  -- The legacy employee id must still be present on every rewritten order.
  IF EXISTS(SELECT 1 FROM zsjos_order o WHERE o.deleted=b'0' AND o.updater='V303'
              AND JSON_EXTRACT(o.imported_actor_snapshot,'$.submitter.sourceEmployeeId') IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 dropped a legacy employee id';
  END IF;

  -- Frozen profile keys other than the subject must be intact on every rewritten round.
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r WHERE r.updater='V303'
              AND (JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceType') IS NULL
                OR JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceLabel') IS NULL
                OR JSON_EXTRACT(r.order_snapshot,'$.leadProfile.sourceSubjectType') IS NULL)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 dropped a frozen profile key';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_order_approval_round r WHERE r.updater='V303' AND JSON_VALID(r.order_snapshot)=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V303 produced an invalid order snapshot';
  END IF;

  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V303','Retired submitter reference repoint',SHA2('V303__retired_submitter_reference_repoint.sql',256)
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V303');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V303','Retired submitter reference repoint',
         SHA2('V303__retired_submitter_reference_repoint.sql',256),'baseline'
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V303');
  COMMIT;

  SELECT v_lead_source AS lead_source_user_id,
         v_lead_provider AS lead_provider_owner_id,
         v_lead_spuid AS lead_source_provider_user_id,
         v_order_submitter AS order_submitter_user_id,
         v_order_actor AS order_imported_actor,
         v_round_subject AS round_profile_subject;
  DROP TEMPORARY TABLE zsjos_v303_map;
END$$
DELIMITER ;
CALL zsjos_v303_apply();
DROP PROCEDURE zsjos_v303_apply;
