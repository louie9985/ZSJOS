-- UTF-8. Core V304; requires V303 in both ledgers. Tenant 1 only.
-- Reconciles the three approved historical order-import batches, never new business.
-- Preserves post-deal reminders, ownership, original activity/qualification timestamps,
-- order lifecycle/amounts, approval snapshots, financial and performance records.
-- Added opportunities represent the current final state; their creation time is NOW,
-- while won_at is the historical effective order time. No events/notifications replay.
-- Prerequisite/repair/postconditions/ledger writes share a failure-controlled procedure.
-- Backup-table/routine DDL precedes the transaction and is NOT claimed to roll back.
-- Named lock serializes the complete routine installation/execution/removal. Use a
-- fail-fast client (no --force); connection closure releases the lock on any failure.
-- Repeat execution preserves first before-images and changes no already repaired rows.
-- Recovery uses manifests even when Lead or version rows already say complete; do not
-- delete backups/ledger rows or automatically delete newly inserted opportunities.
-- See docs/operations/legacy-lead-deal-state-reconcile.md for guarded recovery.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @v304_lock_name = CONCAT('v304:', LEFT(SHA2(DATABASE(),256),48));
SET @v304_lock = GET_LOCK(@v304_lock_name,60);
-- Abort before routine DDL on a lock failure, including under a fail-fast runner.
SET @v304_assert_sql = IF(@v304_lock=1, 'DO 0',
    'SELECT * FROM information_schema.V304_LOCK_NOT_ACQUIRED');
PREPARE v304_assert FROM @v304_assert_sql;
EXECUTE v304_assert;
DEALLOCATE PREPARE v304_assert;

CREATE TABLE IF NOT EXISTS zsjos_data_repair_backup (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  repair_key VARCHAR(100) NOT NULL,
  table_name VARCHAR(64) NOT NULL,
  row_id BIGINT NOT NULL,
  column_name VARCHAR(64) NOT NULL,
  old_value VARCHAR(512) NULL,
  backed_up_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mark_row (repair_key,table_name,row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS zsjos_v304_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v304_apply()
apply_body: BEGIN
  DECLARE v_now DATETIME DEFAULT NOW();
  DECLARE v_id BIGINT;
  DECLARE v_done BOOLEAN DEFAULT FALSE;
  DECLARE v_leads INT DEFAULT 0;
  DECLARE v_existing INT DEFAULT 0;
  DECLARE v_inserted INT DEFAULT 0;
  DECLARE v_orders INT DEFAULT 0;
  DECLARE v_targets INT DEFAULT 0;
  DECLARE c_orders CURSOR FOR SELECT o.id FROM zsjos_order o
    JOIN zsjos_v304_target t ON t.lead_id=o.lead_id WHERE o.tenant_id=1 ORDER BY o.id FOR UPDATE;
  DECLARE c_leads CURSOR FOR SELECT l.id FROM zsjos_lead l
    JOIN zsjos_v304_target t ON t.lead_id=l.id ORDER BY l.id FOR UPDATE;
  DECLARE c_people CURSOR FOR SELECT p.id FROM zsjos_person p
    JOIN zsjos_v304_target t ON t.person_id=p.id WHERE p.tenant_id=1 ORDER BY p.id FOR UPDATE;
  DECLARE c_opps CURSOR FOR SELECT p.id FROM zsjos_opportunity p
    JOIN zsjos_v304_target t ON t.person_id=p.person_id WHERE p.tenant_id=1 ORDER BY p.id FOR UPDATE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done=TRUE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  IF COALESCE(@v304_lock,0)<>1 OR IS_USED_LOCK(@v304_lock_name)<>CONNECTION_ID() THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 migration lock required';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V303') OR
     NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V303') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 requires Core V303 in both ledgers';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('zsjos_lead','zsjos_order','zsjos_opportunity','zsjos_person',
      'zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version') AND engine<>'InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 requires transactional InnoDB tables';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='legacy-deal-state-v304'
    GROUP BY table_name,row_id,column_name HAVING COUNT(*)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 duplicate repair images require review';
  END IF;

  SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
  START TRANSACTION;
  CREATE TEMPORARY TABLE zsjos_v304_target (
    lead_id BIGINT PRIMARY KEY, person_id BIGINT, order_id BIGINT, effective_at DATETIME,
    opportunity_id BIGINT NULL, imported_batch VARCHAR(64), original_status VARCHAR(32)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO zsjos_v304_target
-- BEGIN V304 TARGET SELECT
  SELECT l.id AS lead_id,l.person_id,MIN(o.id) AS order_id,MIN(o.effective_at) AS effective_at,
    (SELECT MIN(p.id) FROM zsjos_opportunity p WHERE p.tenant_id=1
      AND p.lead_id=l.id AND p.type='initial_conversion' AND p.deleted=b'0') AS opportunity_id,
    MIN(o.creator) AS imported_batch,l.status AS original_status
  FROM zsjos_lead l JOIN zsjos_order o ON o.tenant_id=l.tenant_id AND o.lead_id=l.id
  WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.status IN ('valid','converted')
    AND o.deleted=b'0' AND o.status='effective' AND o.order_type='first_purchase'
    AND o.creator IN ('legacy-migration','legacy-increment-20260917','legacy-increment-20260921')
    AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
      AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='__target__')
  GROUP BY l.id,l.person_id,l.status
  UNION ALL
  SELECT b.row_id,CAST(JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.personId')) AS UNSIGNED),
    CAST(JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.orderId')) AS UNSIGNED),
    CAST(JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.effectiveAt')) AS DATETIME),
    (SELECT MIN(p.id) FROM zsjos_opportunity p WHERE p.tenant_id=1
      AND p.lead_id=b.row_id AND p.type='initial_conversion' AND p.deleted=b'0'),
    JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.batch')),
    JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.originalStatus'))
  FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
    AND b.table_name='zsjos_lead' AND b.column_name='__target__'
-- END V304 TARGET SELECT
  ;
  OPEN c_orders;
  order_locks: LOOP FETCH c_orders INTO v_id; IF v_done THEN LEAVE order_locks; END IF; END LOOP;
  CLOSE c_orders; SET v_done=FALSE;
  OPEN c_leads;
  lead_locks: LOOP FETCH c_leads INTO v_id; IF v_done THEN LEAVE lead_locks; END IF; END LOOP;
  CLOSE c_leads; SET v_done=FALSE;
  OPEN c_people;
  person_locks: LOOP FETCH c_people INTO v_id; IF v_done THEN LEAVE person_locks; END IF; END LOOP;
  CLOSE c_people; SET v_done=FALSE;
  OPEN c_opps;
  opp_locks: LOOP FETCH c_opps INTO v_id; IF v_done THEN LEAVE opp_locks; END IF; END LOOP;
  CLOSE c_opps;

  CREATE TEMPORARY TABLE zsjos_v304_conflicts (lead_id BIGINT,reason VARCHAR(80)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO zsjos_v304_conflicts
-- BEGIN V304 CONFLICT SELECT
  SELECT t.lead_id,'lead_or_person_changed' reason FROM zsjos_v304_target t
    LEFT JOIN zsjos_lead l ON l.id=t.lead_id AND l.tenant_id=1 AND l.deleted=b'0'
    LEFT JOIN zsjos_person p ON p.id=t.person_id AND p.tenant_id=1 AND p.deleted=b'0'
    WHERE l.id IS NULL OR p.id IS NULL OR NOT(l.person_id<=>t.person_id)
      OR l.status NOT IN ('valid','converted','won') OR l.assignment_status<>'owned'
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'order_conflict' FROM zsjos_v304_target t
    LEFT JOIN zsjos_order o ON o.id=t.order_id
    WHERE o.id IS NULL OR o.tenant_id<>1 OR o.deleted<>b'0' OR o.status<>'effective'
      OR o.order_type<>'first_purchase' OR NOT(o.lead_id<=>t.lead_id) OR NOT(o.person_id<=>t.person_id)
      OR o.effective_at IS NULL OR NOT(o.effective_at<=>t.effective_at)
      OR o.creator NOT IN ('legacy-migration','legacy-increment-20260917','legacy-increment-20260921')
      OR o.superseded_by_order_id IS NOT NULL
      OR (o.opportunity_id IS NOT NULL AND NOT(o.opportunity_id<=>t.opportunity_id))
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'multiple_effective_first_orders' FROM zsjos_v304_target t
    WHERE (SELECT COUNT(*) FROM zsjos_order o WHERE o.tenant_id=1 AND o.lead_id=t.lead_id
      AND o.deleted=b'0' AND o.status='effective' AND o.order_type='first_purchase')<>1
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'active_approval_or_public_sea' FROM zsjos_v304_target t
    WHERE EXISTS(SELECT 1 FROM zsjos_order o WHERE o.tenant_id=1 AND o.lead_id=t.lead_id
      AND o.deleted=b'0' AND o.status IN ('pending_approval','revision_required'))
    OR EXISTS(SELECT 1 FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.id=r.order_id
      AND o.tenant_id=r.tenant_id WHERE r.tenant_id=1 AND r.deleted=b'0' AND r.status='pending'
      AND o.lead_id=t.lead_id AND o.deleted=b'0')
    OR EXISTS(SELECT 1 FROM zsjos_lead_public_sea_record s WHERE s.tenant_id=1
      AND s.lead_id=t.lead_id AND s.deleted=b'0')
    OR EXISTS(SELECT 1 FROM zsjos_lead_aging_pool_cycle c WHERE c.tenant_id=1
      AND c.lead_id=t.lead_id AND c.deleted=b'0' AND c.status IN ('waiting_assignment','assigned','deal_pending'))
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'opportunity_identity_or_unique_conflict' FROM zsjos_v304_target t
    WHERE EXISTS(SELECT 1 FROM zsjos_opportunity p WHERE p.tenant_id=1
      AND ((p.lead_id=t.lead_id) OR (p.person_id=t.person_id AND p.deleted=b'0'))
      AND (NOT(p.id<=>t.opportunity_id) OR p.type<>'initial_conversion' OR p.deleted<>b'0'
        OR NOT(p.person_id<=>t.person_id)))
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'missing_opportunity_owner' FROM zsjos_v304_target t JOIN zsjos_lead l ON l.id=t.lead_id
    WHERE t.opportunity_id IS NULL AND l.owner_user_id IS NULL
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'opportunity_state_conflict' FROM zsjos_v304_target t
    JOIN zsjos_opportunity p ON p.id=t.opportunity_id
    WHERE NOT ((p.status IN ('open','following') AND p.won_at IS NULL)
      OR (p.status='won' AND p.won_at=t.effective_at AND EXISTS(
        SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
          AND b.table_name='zsjos_opportunity' AND b.row_id=p.id AND b.column_name='__after__')))
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'duplicate_person_in_repair' FROM zsjos_v304_target t
    WHERE EXISTS(SELECT 1 FROM zsjos_lead l2 JOIN zsjos_order o2 ON o2.lead_id=l2.id AND o2.tenant_id=l2.tenant_id
      WHERE l2.tenant_id=1 AND l2.deleted=b'0' AND l2.person_id=t.person_id AND l2.id<>t.lead_id
        AND l2.status IN ('valid','converted') AND o2.deleted=b'0' AND o2.status='effective'
        AND o2.order_type='first_purchase' AND o2.creator IN
          ('legacy-migration','legacy-increment-20260917','legacy-increment-20260921'))
  ;
  INSERT INTO zsjos_v304_conflicts
  SELECT t.lead_id,'recorded_opportunity_missing_or_replaced' FROM zsjos_v304_target t
    JOIN zsjos_data_repair_backup b ON b.repair_key='legacy-deal-state-v304'
      AND b.table_name='zsjos_lead' AND b.row_id=t.lead_id AND b.column_name='__target__'
    WHERE (JSON_TYPE(JSON_EXTRACT(b.old_value,'$.originalOpportunityId'))<>'NULL'
      AND NOT(CAST(JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.originalOpportunityId')) AS UNSIGNED)<=>t.opportunity_id))
    OR EXISTS(SELECT 1 FROM zsjos_data_repair_backup n WHERE n.repair_key=b.repair_key
      AND n.table_name='zsjos_opportunity' AND n.column_name='__inserted__'
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(n.old_value,'$.leadId')) AS UNSIGNED)=t.lead_id
      AND NOT(n.row_id<=>t.opportunity_id))
-- END V304 CONFLICT SELECT
  ;
  IF EXISTS(SELECT 1 FROM zsjos_v304_conflicts) THEN
    SELECT l.lead_no,c.reason FROM zsjos_v304_conflicts c LEFT JOIN zsjos_lead l ON l.id=c.lead_id;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 preflight conflicts; no business data changed';
  END IF;

  -- Compact images fit the established 512-character backup field; unchanged business
  -- fields are never rewritten. Version and timestamp guard post-repair user activity.
  CREATE TEMPORARY TABLE zsjos_v304_images (
    table_name VARCHAR(64),row_id BIGINT,before_image VARCHAR(512),after_image VARCHAR(512),
    PRIMARY KEY(table_name,row_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  INSERT INTO zsjos_v304_images
  SELECT 'zsjos_lead',l.id,
    JSON_OBJECT('status',l.status,'version',l.version,'updater',l.updater,'updateTime',l.update_time),
    JSON_OBJECT('status','won','version',l.version+1,'updater','V304','updateTime',v_now)
  FROM zsjos_lead l JOIN zsjos_v304_target t ON t.lead_id=l.id
  ;
  INSERT INTO zsjos_v304_images
  SELECT 'zsjos_opportunity',p.id,
    JSON_OBJECT('status',p.status,'wonAt',p.won_at,'version',p.version,'updater',p.updater,'updateTime',p.update_time),
    JSON_OBJECT('status','won','wonAt',t.effective_at,'version',p.version+1,'updater','V304','updateTime',v_now)
  FROM zsjos_opportunity p JOIN zsjos_v304_target t ON t.opportunity_id=p.id
  ;
  INSERT INTO zsjos_v304_images
  SELECT 'zsjos_order',o.id,
    JSON_OBJECT('opportunityId',o.opportunity_id,'version',o.version,'updater',o.updater,'updateTime',o.update_time),
    NULL
  FROM zsjos_order o JOIN zsjos_v304_target t ON t.order_id=o.id;

-- BEGIN V304 COMPLETED REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V304') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V304') THEN
    IF EXISTS(SELECT 1 FROM zsjos_v304_images i WHERE
      (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
       AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__after__')<>1
      OR (SELECT COUNT(*) FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
       AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name IN ('__before__','__inserted__'))<>1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 completed repair evidence incomplete';
    END IF;
    IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304' AND b.column_name='__after__'
      AND NOT EXISTS(SELECT 1 FROM zsjos_v304_images i WHERE i.table_name=b.table_name AND i.row_id=b.row_id)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 completed repair evidence incomplete';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM zsjos_v304_images i JOIN zsjos_data_repair_backup a
      ON a.repair_key='legacy-deal-state-v304' AND a.table_name=i.table_name AND a.row_id=i.row_id AND a.column_name='__after__'
      WHERE NOT(JSON_REMOVE(CAST(i.before_image AS JSON),'$.version','$.updater','$.updateTime') <=>
        JSON_REMOVE(CAST(a.old_value AS JSON),'$.version','$.updater','$.updateTime'))) THEN
      COMMIT;
      DROP TEMPORARY TABLE zsjos_v304_images;
      DROP TEMPORARY TABLE zsjos_v304_target;
      SELECT 'V304' AS migration,'already_completed_verified' AS replay_status;
      LEAVE apply_body;
    END IF;
  END IF;
-- END V304 COMPLETED REPLAY
  IF EXISTS(SELECT 1 FROM zsjos_v304_images i JOIN zsjos_data_repair_backup b
      ON b.repair_key='legacy-deal-state-v304' AND b.table_name=i.table_name
      AND b.row_id=i.row_id AND b.column_name='__before__'
    LEFT JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name
      AND a.row_id=b.row_id AND a.column_name='__after__'
    WHERE CAST(i.before_image AS JSON)<>CAST(b.old_value AS JSON)
      AND (a.id IS NULL OR CAST(i.before_image AS JSON)<>CAST(a.old_value AS JSON))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 recorded row changed after repair; manual review required';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_v304_images i JOIN zsjos_data_repair_backup b
      ON b.repair_key='legacy-deal-state-v304' AND b.table_name=i.table_name
      AND b.row_id=i.row_id AND b.column_name='__inserted__'
    LEFT JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name
      AND a.row_id=b.row_id AND a.column_name='__after__'
    WHERE a.id IS NULL OR CAST(i.before_image AS JSON)<>CAST(a.old_value AS JSON)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 inserted row changed after repair; manual review required';
  END IF;

  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'legacy-deal-state-v304','zsjos_lead',t.lead_id,'__target__',
    JSON_OBJECT('personId',t.person_id,'orderId',t.order_id,'effectiveAt',t.effective_at,
      'batch',t.imported_batch,'originalStatus',t.original_status,'originalOpportunityId',t.opportunity_id)
  FROM zsjos_v304_target t WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
    WHERE b.repair_key='legacy-deal-state-v304' AND b.table_name='zsjos_lead'
      AND b.row_id=t.lead_id AND b.column_name='__target__');

  INSERT INTO zsjos_opportunity(person_id,type,lead_id,status,owner_user_id,expected_product_summary,
    next_follow_up_at,won_at,version,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT l.person_id,'initial_conversion',l.id,'won',l.owner_user_id,
    COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('spuRef',COALESCE(ip.spu_ref,''),
      'spuName',COALESCE(ip.spu_name_snapshot,''),'skuRef',COALESCE(ip.sku_ref,''),
      'skuName',COALESCE(ip.sku_name_snapshot,''),'primary',ip.is_primary))
      FROM zsjos_lead_intended_product ip WHERE ip.tenant_id=1 AND ip.lead_id=l.id AND ip.deleted=b'0'),JSON_ARRAY()),
    l.next_follow_up_at,t.effective_at,0,'V304-legacy-deal-backfill',v_now,'V304',v_now,b'0',1
  FROM zsjos_lead l JOIN zsjos_v304_target t ON t.lead_id=l.id WHERE t.opportunity_id IS NULL;
  SET v_inserted=ROW_COUNT();
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'legacy-deal-state-v304','zsjos_opportunity',p.id,'__inserted__',
    JSON_OBJECT('leadId',t.lead_id,'orderId',t.order_id,'creator',p.creator,'createdAt',p.create_time)
  FROM zsjos_v304_target t JOIN zsjos_opportunity p ON p.tenant_id=1 AND p.lead_id=t.lead_id
    AND p.type='initial_conversion' AND p.deleted=b'0' WHERE t.opportunity_id IS NULL;
  -- Inserted rows have no fictitious before-image. Their after-image supports repeat
  -- and recovery guards, while the insertion manifest retains the original provenance.
  INSERT INTO zsjos_v304_images
  SELECT 'zsjos_opportunity',p.id,NULL,
    JSON_OBJECT('status',p.status,'wonAt',p.won_at,'version',p.version,'updater',p.updater,'updateTime',p.update_time)
  FROM zsjos_opportunity p JOIN zsjos_v304_target t ON t.lead_id=p.lead_id
    AND p.tenant_id=1 AND p.type='initial_conversion' AND p.deleted=b'0'
  WHERE t.opportunity_id IS NULL;
  UPDATE zsjos_v304_target t JOIN zsjos_opportunity p ON p.tenant_id=1 AND p.lead_id=t.lead_id
    AND p.type='initial_conversion' AND p.deleted=b'0' SET t.opportunity_id=p.id;

  UPDATE zsjos_v304_images i JOIN zsjos_v304_target t ON i.table_name='zsjos_order' AND t.order_id=i.row_id
    JOIN zsjos_order o ON o.id=i.row_id
    SET i.after_image=IF(o.opportunity_id=t.opportunity_id,i.before_image,
      JSON_OBJECT('opportunityId',t.opportunity_id,'version',o.version+1,'updater','V304','updateTime',v_now));

  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'legacy-deal-state-v304',i.table_name,i.row_id,'__before__',i.before_image
  FROM zsjos_v304_images i WHERE i.before_image IS NOT NULL
    AND NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='legacy-deal-state-v304'
      AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name IN ('__before__','__inserted__'));
  INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
  SELECT 'legacy-deal-state-v304',i.table_name,i.row_id,'__after__',i.after_image
  FROM zsjos_v304_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b
    WHERE b.repair_key='legacy-deal-state-v304' AND b.table_name=i.table_name
      AND b.row_id=i.row_id AND b.column_name='__after__');

  UPDATE zsjos_opportunity p JOIN zsjos_v304_target t ON t.opportunity_id=p.id
    JOIN zsjos_data_repair_backup a ON a.repair_key='legacy-deal-state-v304'
      AND a.table_name='zsjos_opportunity' AND a.row_id=p.id AND a.column_name='__after__'
  SET p.status='won',p.won_at=t.effective_at,p.version=JSON_EXTRACT(a.old_value,'$.version'),
    p.updater='V304',p.update_time=CAST(JSON_UNQUOTE(JSON_EXTRACT(a.old_value,'$.updateTime')) AS DATETIME)
  WHERE p.status IN ('open','following');
  SET v_existing=ROW_COUNT();
  UPDATE zsjos_order o JOIN zsjos_v304_target t ON t.order_id=o.id
    JOIN zsjos_data_repair_backup a ON a.repair_key='legacy-deal-state-v304'
      AND a.table_name='zsjos_order' AND a.row_id=o.id AND a.column_name='__after__'
  SET o.opportunity_id=t.opportunity_id,o.version=JSON_EXTRACT(a.old_value,'$.version'),
    o.updater='V304',o.update_time=CAST(JSON_UNQUOTE(JSON_EXTRACT(a.old_value,'$.updateTime')) AS DATETIME)
  WHERE o.opportunity_id IS NULL;
  SET v_orders=ROW_COUNT();
  UPDATE zsjos_lead l JOIN zsjos_v304_target t ON t.lead_id=l.id
    JOIN zsjos_data_repair_backup a ON a.repair_key='legacy-deal-state-v304'
      AND a.table_name='zsjos_lead' AND a.row_id=l.id AND a.column_name='__after__'
  SET l.status='won',l.version=JSON_EXTRACT(a.old_value,'$.version'),l.updater='V304',
    l.update_time=CAST(JSON_UNQUOTE(JSON_EXTRACT(a.old_value,'$.updateTime')) AS DATETIME)
  WHERE l.status IN ('valid','converted');
  SET v_leads=ROW_COUNT();

  IF EXISTS(SELECT 1 FROM zsjos_v304_target t LEFT JOIN zsjos_lead l ON l.id=t.lead_id
    LEFT JOIN zsjos_order o ON o.id=t.order_id LEFT JOIN zsjos_opportunity p ON p.id=t.opportunity_id
    WHERE l.status<>'won' OR p.id IS NULL OR p.status<>'won' OR NOT(p.won_at<=>t.effective_at)
      OR NOT(o.opportunity_id<=>p.id) OR p.tenant_id<>1 OR p.deleted<>b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V304 reconciliation postcondition failed';
  END IF;
  SELECT COUNT(*) INTO v_targets FROM zsjos_v304_target;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V304','Legacy Lead deal state reconciliation',SHA2('V304__legacy_lead_deal_state_reconcile.sql',256)
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V304');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V304','Legacy Lead deal state reconciliation',
    SHA2('V304__legacy_lead_deal_state_reconcile.sql',256),'baseline'
    WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V304');
  COMMIT;
  SELECT v_targets AS targets,v_leads AS leads_updated,v_existing AS opportunities_updated,
    v_inserted AS opportunities_inserted,v_orders AS orders_linked;
  DROP TEMPORARY TABLE zsjos_v304_images;
  DROP TEMPORARY TABLE zsjos_v304_conflicts;
  DROP TEMPORARY TABLE zsjos_v304_target;
END$$
DELIMITER ;
CALL zsjos_v304_apply();
DROP PROCEDURE zsjos_v304_apply;
DO RELEASE_LOCK(@v304_lock_name);
