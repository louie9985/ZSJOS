-- UTF-8. V306; requires V305 in both ledgers and LONGTEXT repair storage.
-- User-confirmed tenant-1 order manifest: 2 education, 284 sales. No role inference.
-- Only empty order identities/current-round identity JSON paths and modification audit change.
-- No Lead/user/permission/financial/BPM/attribution writes. Departed and seed orders excluded.
-- First before/after images preserved. Repeat/partial recovery checks actual state even with ledgers.
-- Later identity/version/audit edits reject the batch; SQL client must stop on error (no --force).
-- Recovery rollback is rehearsed by the companion tool; physical restore needs separate review.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @v306_lock=GET_LOCK(CONCAT('v306:',DATABASE()),10);
SET @v306_assert=IF(@v306_lock=1,'DO 0','SELECT * FROM information_schema.V306_LOCK_FAILED');
PREPARE v306_assert FROM @v306_assert;
EXECUTE v306_assert;
DEALLOCATE PREPARE v306_assert;
DROP PROCEDURE IF EXISTS zsjos_v306_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v306_apply()
apply_body: BEGIN
 DECLARE v_now DATETIME DEFAULT NOW();
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V305') OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V305') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 requires V305';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_data_repair_backup' AND column_name='old_value' AND data_type='longtext') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 requires LONGTEXT backup';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v306_manifest;
 CREATE TEMPORARY TABLE v306_manifest(order_no VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, identity_value VARCHAR(32) NOT NULL) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 INSERT INTO v306_manifest VALUES
('OD202609211435200019',60,'sales'),
('OD202609212049560024',528,'education'),
('OD202609221356140005',61,'sales'),
('OD202609221356340006',400,'education'),
('OD202609221748560009',61,'sales'),
('OD202609231811290011',41,'sales'),
('OD202609240833480001',61,'sales'),
('OD202609241423370005',41,'sales'),
('OD202609260901440003',61,'sales'),
('OD202609272121210003',61,'sales'),
('OD202609281653090012',291,'sales'),
('OD202609282047360014',41,'sales'),
('OD202609291557070003',61,'sales'),
('OD202609301142080008',17,'sales'),
('OD202609301646250013',61,'sales'),
('OD202609301917450016',60,'sales'),
('OD202609302306590021',61,'sales'),
('OD202610081849390016',291,'sales'),
('OR202606080001',22,'sales'),
('OR202606110001',61,'sales'),
('OR202606140001',61,'sales'),
('OR202606220002',23,'sales'),
('OR202606240003',61,'sales'),
('OR202606240004',60,'sales'),
('OR202606240005',41,'sales'),
('OR202606240008',294,'sales'),
('OR202606240009',294,'sales'),
('OR202606240011',61,'sales'),
('OR202606240012',22,'sales'),
('OR202606250002',294,'sales'),
('OR202606250004',294,'sales'),
('OR202606250005',294,'sales'),
('OR202606260006',61,'sales'),
('OR202606280001',61,'sales'),
('OR202606290001',41,'sales'),
('OR202606300001',294,'sales'),
('OR202606300002',311,'sales'),
('OR202606300003',294,'sales'),
('OR202606300005',310,'sales'),
('OR202606300006',308,'sales'),
('OR202606300007',308,'sales'),
('OR202606300008',310,'sales'),
('OR202607010002',41,'sales'),
('OR202607010003',61,'sales'),
('OR202607060001',17,'sales'),
('OR202607070001',60,'sales'),
('OR202607080001',309,'sales'),
('OR202607090001',60,'sales'),
('OR202607100001',60,'sales'),
('OR202607100004',61,'sales'),
('OR202607100005',61,'sales'),
('OR202607100006',61,'sales'),
('OR202607100007',61,'sales'),
('OR202607100008',61,'sales'),
('OR202607120002',23,'sales'),
('OR202607120003',22,'sales'),
('OR202607120004',22,'sales'),
('OR202607120005',61,'sales'),
('OR202607120006',309,'sales'),
('OR202607130001',23,'sales'),
('OR202607140001',309,'sales'),
('OR202607150001',60,'sales'),
('OR202607150002',389,'sales'),
('OR202607150003',308,'sales'),
('OR202607160002',308,'sales'),
('OR202607160004',41,'sales'),
('OR202607160005',60,'sales'),
('OR202607170001',22,'sales'),
('OR202607170002',22,'sales'),
('OR202607170003',23,'sales'),
('OR202607180001',41,'sales'),
('OR202607210001',308,'sales'),
('OR202607210002',60,'sales'),
('OR202607210003',60,'sales'),
('OR202607210005',61,'sales'),
('OR202607220001',60,'sales'),
('OR202607220002',23,'sales'),
('OR202607220003',23,'sales'),
('OR202607240002',60,'sales'),
('OR202607260001',294,'sales'),
('OR202607260003',60,'sales'),
('OR202607260004',41,'sales'),
('OR202607260005',41,'sales'),
('OR202607260006',60,'sales'),
('OR202607260007',294,'sales'),
('OR202607270001',60,'sales'),
('OR202607270003',61,'sales'),
('OR202607270004',61,'sales'),
('OR202607270005',61,'sales'),
('OR202607270006',61,'sales'),
('OR202607270007',61,'sales'),
('OR202607280001',61,'sales'),
('OR202607280002',61,'sales'),
('OR202607280003',60,'sales'),
('OR202607290001',60,'sales'),
('OR202607290002',61,'sales'),
('OR202607290003',61,'sales'),
('OR202607300002',60,'sales'),
('OR202607310001',309,'sales'),
('OR202607310002',60,'sales'),
('OR202607310003',41,'sales'),
('OR202607310004',61,'sales'),
('OR202607310005',61,'sales'),
('OR202608020001',22,'sales'),
('OR202608020002',22,'sales'),
('OR202608020003',60,'sales'),
('OR202608020004',41,'sales'),
('OR202608020008',61,'sales'),
('OR202608020009',61,'sales'),
('OR202608020010',61,'sales'),
('OR202608040001',23,'sales'),
('OR202608050001',60,'sales'),
('OR202608060001',60,'sales'),
('OR202608070001',60,'sales'),
('OR202608100004',41,'sales'),
('OR202608110004',60,'sales'),
('OR202608110005',60,'sales'),
('OR202608110006',60,'sales'),
('OR202608120002',60,'sales'),
('OR202608120003',60,'sales'),
('OR202608120004',60,'sales'),
('OR202608130001',60,'sales'),
('OR202608140002',41,'sales'),
('OR202608140003',41,'sales'),
('OR202608140004',61,'sales'),
('OR202608160001',41,'sales'),
('OR202608170003',61,'sales'),
('OR202608170004',294,'sales'),
('OR202608180001',60,'sales'),
('OR202608180002',41,'sales'),
('OR202608200001',61,'sales'),
('OR202608200002',294,'sales'),
('OR202608200003',60,'sales'),
('OR202608200004',60,'sales'),
('OR202608210002',41,'sales'),
('OR202608210003',294,'sales'),
('OR202608210004',41,'sales'),
('OR202608240001',61,'sales'),
('OR202608240002',61,'sales'),
('OR202608240005',294,'sales'),
('OR202608240006',294,'sales'),
('OR202608240007',60,'sales'),
('OR202608240008',291,'sales'),
('OR202608240011',291,'sales'),
('OR202608250001',294,'sales'),
('OR202608250002',60,'sales'),
('OR202608250004',61,'sales'),
('OR202608250007',291,'sales'),
('OR202608260001',17,'sales'),
('OR202608260005',23,'sales'),
('OR202608260006',23,'sales'),
('OR202608260008',23,'sales'),
('OR202608260009',23,'sales'),
('OR202608270001',294,'sales'),
('OR202608270002',41,'sales'),
('OR202608270003',22,'sales'),
('OR202608270004',17,'sales'),
('OR202608270005',17,'sales'),
('OR202608270006',17,'sales'),
('OR202608280001',294,'sales'),
('OR202608280004',23,'sales'),
('OR202608280005',17,'sales'),
('OR202608280006',60,'sales'),
('OR202608280007',60,'sales'),
('OR202608280010',22,'sales'),
('OR202608280012',23,'sales'),
('OR202608290003',61,'sales'),
('OR202608300001',61,'sales'),
('OR202608300003',60,'sales'),
('OR202608300004',60,'sales'),
('OR202608300005',60,'sales'),
('OR202608300007',61,'sales'),
('OR202608300009',17,'sales'),
('OR202608300013',61,'sales'),
('OR202608300014',61,'sales'),
('OR202608300016',294,'sales'),
('OR202608310001',17,'sales'),
('OR202608310003',60,'sales'),
('OR202608310004',17,'sales'),
('OR202608310006',17,'sales'),
('OR202608310007',291,'sales'),
('OR202608310008',23,'sales'),
('OR202608310009',17,'sales'),
('OR202608310010',60,'sales'),
('OR202608310011',17,'sales'),
('OR202608310012',22,'sales'),
('OR202608310014',291,'sales'),
('OR202609010003',23,'sales'),
('OR202609010004',61,'sales'),
('OR202609010007',61,'sales'),
('OR202609020002',17,'sales'),
('OR202609020003',61,'sales'),
('OR202609020004',22,'sales'),
('OR202609030001',17,'sales'),
('OR202609030003',23,'sales'),
('OR202609040002',23,'sales'),
('OR202609040004',60,'sales'),
('OR202609040006',17,'sales'),
('OR202609040007',61,'sales'),
('OR202609040011',57,'sales'),
('OR202609040014',23,'sales'),
('OR202609050002',61,'sales'),
('OR202609050005',61,'sales'),
('OR202609050006',61,'sales'),
('OR202609060001',22,'sales'),
('OR202609060002',291,'sales'),
('OR202609070001',60,'sales'),
('OR202609070003',17,'sales'),
('OR202609070004',291,'sales'),
('OR202609070005',17,'sales'),
('OR202609070008',291,'sales'),
('OR202609070010',61,'sales'),
('OR202609070012',23,'sales'),
('OR202609070013',61,'sales'),
('OR202609080003',61,'sales'),
('OR202609090001',22,'sales'),
('OR202609090002',60,'sales'),
('OR202609090004',60,'sales'),
('OR202609090005',60,'sales'),
('OR202609090006',17,'sales'),
('OR202609090007',60,'sales'),
('OR202609090008',17,'sales'),
('OR202609090011',22,'sales'),
('OR202609090012',61,'sales'),
('OR202609100002',291,'sales'),
('OR202609100005',61,'sales'),
('OR202609110001',60,'sales'),
('OR202609110002',60,'sales'),
('OR202609110003',60,'sales'),
('OR202609110004',60,'sales'),
('OR202609110006',22,'sales'),
('OR202609110008',60,'sales'),
('OR202609110011',60,'sales'),
('OR202609110017',17,'sales'),
('OR202609120001',17,'sales'),
('OR202609120003',22,'sales'),
('OR202609130002',61,'sales'),
('OR202609140001',291,'sales'),
('OR202609140003',61,'sales'),
('OR202609140004',294,'sales'),
('OR202609140006',22,'sales'),
('OR202609140007',291,'sales'),
('OR202609140008',57,'sales'),
('OR202609140009',291,'sales'),
('OR202609140010',61,'sales'),
('OR202609150001',60,'sales'),
('OR202609150006',291,'sales'),
('OR202609150008',60,'sales'),
('OR202609150009',61,'sales'),
('OR202609150010',61,'sales'),
('OR202609150012',61,'sales'),
('OR202609160001',23,'sales'),
('OR202609160002',61,'sales'),
('OR202609160005',41,'sales'),
('OR202609160006',17,'sales'),
('OR202609160012',60,'sales'),
('OR202609160013',60,'sales'),
('OR202609170001',61,'sales'),
('OR202609170008',41,'sales'),
('OR202609170014',23,'sales'),
('OR202609170015',23,'sales'),
('OR202609180009',41,'sales'),
('OR202609180011',41,'sales'),
('OR202609180012',41,'sales'),
('OR202609180013',41,'sales'),
('OR202609180014',41,'sales'),
('OR202609180015',41,'sales'),
('OR202609180016',60,'sales'),
('OR202609180020',291,'sales'),
('OR202609180021',61,'sales'),
('OR202609180023',291,'sales'),
('OR202609180024',291,'sales'),
('OR202609180026',41,'sales'),
('OR202609190001',22,'sales'),
('OR202609200005',23,'sales'),
('OR202609200013',17,'sales'),
('OR202609200016',23,'sales'),
('OR202609200017',23,'sales'),
('OR202609200019',61,'sales'),
('OR202609200020',61,'sales'),
('OR202609200021',17,'sales'),
('OR202609200022',23,'sales'),
('OR202609200025',17,'sales'),
('OR202609200026',17,'sales'),
('OR202609200027',17,'sales'),
('OR202609200030',23,'sales');
 IF (SELECT COUNT(*) FROM v306_manifest) <> 286
  OR EXISTS(SELECT 1 FROM v306_manifest m
      LEFT JOIN zsjos_order o ON o.order_no=m.order_no AND o.tenant_id=1 AND o.deleted=0
      LEFT JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id
          AND r.order_id=o.id AND r.tenant_id=1 AND r.deleted=0
      WHERE o.id IS NULL OR r.id IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 manifest target missing or incomplete';
 END IF;
 SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
 START TRANSACTION;
 -- Lock current target orders and rounds before creating candidate images.
 SELECT COUNT(*) INTO @v306_order_locks FROM zsjos_order o JOIN v306_manifest m ON m.order_no=o.order_no WHERE o.tenant_id=1 FOR UPDATE;
 SELECT COUNT(*) INTO @v306_round_locks FROM zsjos_order_approval_round r JOIN zsjos_order o ON o.current_approval_round_id=r.id JOIN v306_manifest m ON m.order_no=o.order_no WHERE o.tenant_id=1 FOR UPDATE;
 IF EXISTS(SELECT 1 FROM zsjos_order o JOIN v306_manifest m ON m.order_no=o.order_no LEFT JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id AND r.order_id=o.id AND r.tenant_id=o.tenant_id AND r.deleted=0
  WHERE o.tenant_id=1 AND (o.deleted<>0 OR o.status<>'effective' OR NOT(o.formal_sales_user_id <=> m.user_id) OR r.id IS NULL OR r.status<>'approved' OR COALESCE(r.order_snapshot->>'$.snapshotVersion','')<>'2'
  OR r.round_no<>(SELECT MAX(x.round_no) FROM zsjos_order_approval_round x WHERE x.order_id=o.id AND x.tenant_id=1 AND x.deleted=0))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 target status/user/round changed';
 END IF;
 IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup WHERE repair_key='order-identity-v306' GROUP BY table_name,row_id,column_name HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 duplicate backup';
 END IF;
 DROP TEMPORARY TABLE IF EXISTS v306_images;
 CREATE TEMPORARY TABLE v306_images(table_name VARCHAR(64),row_id BIGINT,before_image JSON,after_image JSON,current_image JSON,PRIMARY KEY(table_name,row_id)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 INSERT INTO v306_images
 SELECT 'zsjos_order',o.id,
 JSON_OBJECT('identity',o.formal_owner_identity,'version',o.version,'updater',o.updater,'update_time',DATE_FORMAT(o.update_time,'%Y-%m-%d %H:%i:%s')),
 JSON_OBJECT('identity',m.identity_value,'version',o.version+1,'updater','order-identity-v306','update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')),
 JSON_OBJECT('identity',o.formal_owner_identity,'version',o.version,'updater',o.updater,'update_time',DATE_FORMAT(o.update_time,'%Y-%m-%d %H:%i:%s'))
 FROM zsjos_order o JOIN v306_manifest m ON m.order_no=o.order_no WHERE o.tenant_id=1;
 INSERT INTO v306_images
 SELECT 'zsjos_order_approval_round',r.id,
 JSON_OBJECT('snapshot',r.order_snapshot,'version',r.version,'updater',r.updater,'update_time',DATE_FORMAT(r.update_time,'%Y-%m-%d %H:%i:%s')),
 JSON_OBJECT('snapshot',CASE WHEN JSON_TYPE(r.order_snapshot->'$.formalSales')='OBJECT' THEN
 JSON_SET(r.order_snapshot,'$.order.formalOwnerIdentity',m.identity_value,'$.formalSales.identity',m.identity_value)
 ELSE JSON_SET(r.order_snapshot,'$.order.formalOwnerIdentity',m.identity_value) END,
 'version',r.version+1,'updater','order-identity-v306','update_time',DATE_FORMAT(v_now,'%Y-%m-%d %H:%i:%s')),
 JSON_OBJECT('snapshot',r.order_snapshot,'version',r.version,'updater',r.updater,'update_time',DATE_FORMAT(r.update_time,'%Y-%m-%d %H:%i:%s'))
 FROM zsjos_order o JOIN v306_manifest m ON m.order_no=o.order_no JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id WHERE o.tenant_id=1;
 -- Both halves must exist together; never manufacture a new baseline from partial backups.
 IF EXISTS(SELECT 1 FROM v306_images i JOIN zsjos_data_repair_backup b ON b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id
  WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup z WHERE z.repair_key=b.repair_key AND z.table_name=b.table_name AND z.row_id=b.row_id AND z.column_name='__before__')
     OR NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup z WHERE z.repair_key=b.repair_key AND z.table_name=b.table_name AND z.row_id=b.row_id AND z.column_name='__after__')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 incomplete backup pair';
 END IF;
 UPDATE v306_images i JOIN zsjos_data_repair_backup b ON b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__before__'
 JOIN zsjos_data_repair_backup a ON a.repair_key=b.repair_key AND a.table_name=b.table_name AND a.row_id=b.row_id AND a.column_name='__after__'
 SET i.before_image=CAST(b.old_value AS JSON),i.after_image=CAST(a.old_value AS JSON);
-- BEGIN V306 COMPLETED REPLAY
 IF EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V306') AND EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V306') THEN
  IF EXISTS(SELECT 1 FROM v306_images i WHERE (SELECT COUNT(*) FROM zsjos_data_repair_backup b
    WHERE b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name IN ('__before__','__after__'))<>2) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 completed repair evidence incomplete';
  END IF;
  IF EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-identity-v306'
    AND NOT EXISTS(SELECT 1 FROM v306_images i WHERE i.table_name=b.table_name AND i.row_id=b.row_id)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 completed repair evidence incomplete';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM v306_images WHERE current_image<>after_image) THEN
    COMMIT;
    DROP TEMPORARY TABLE v306_images;
    SELECT 'V306' AS migration,'already_completed_verified' AS replay_status;
    LEAVE apply_body;
  END IF;
 END IF;
-- END V306 COMPLETED REPLAY
 IF EXISTS(SELECT 1 FROM v306_images WHERE NOT(current_image=before_image OR current_image=after_image)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 before/after conflict; later changes preserved';
 END IF;
 IF EXISTS(SELECT 1 FROM v306_images i WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id)
 AND ((i.table_name='zsjos_order' AND COALESCE(i.before_image->>'$.identity','null') NOT IN ('null',''))
 OR (i.table_name='zsjos_order_approval_round' AND (COALESCE(i.before_image->>'$.snapshot.order.formalOwnerIdentity','null') NOT IN ('null','') OR COALESCE(i.before_image->>'$.snapshot.formalSales.identity','null') NOT IN ('null',''))))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 refuses existing recorded identity';
 END IF;
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
 SELECT 'order-identity-v306',i.table_name,i.row_id,'__before__',CAST(i.before_image AS CHAR CHARACTER SET utf8mb4) FROM v306_images i
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__before__');
 INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value)
 SELECT 'order-identity-v306',i.table_name,i.row_id,'__after__',CAST(i.after_image AS CHAR CHARACTER SET utf8mb4) FROM v306_images i
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_data_repair_backup b WHERE b.repair_key='order-identity-v306' AND b.table_name=i.table_name AND b.row_id=i.row_id AND b.column_name='__after__');
 UPDATE zsjos_order o JOIN v306_images i ON i.table_name='zsjos_order' AND i.row_id=o.id
 SET o.formal_owner_identity=i.after_image->>'$.identity',o.version=CAST(i.after_image->>'$.version' AS UNSIGNED),o.updater=i.after_image->>'$.updater',o.update_time=i.after_image->>'$.update_time'
 WHERE i.current_image<>i.after_image;
 UPDATE zsjos_order_approval_round r JOIN v306_images i ON i.table_name='zsjos_order_approval_round' AND i.row_id=r.id
 SET r.order_snapshot=i.after_image->'$.snapshot',r.version=CAST(i.after_image->>'$.version' AS UNSIGNED),r.updater=i.after_image->>'$.updater',r.update_time=i.after_image->>'$.update_time'
 WHERE i.current_image<>i.after_image;
 IF EXISTS(SELECT 1 FROM v306_manifest m JOIN zsjos_order o ON o.order_no=m.order_no AND o.tenant_id=1 JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id
 WHERE NOT(o.formal_owner_identity <=> m.identity_value) OR NOT(r.order_snapshot->>'$.order.formalOwnerIdentity' <=> m.identity_value)
 OR (JSON_TYPE(r.order_snapshot->'$.formalSales')='OBJECT' AND NOT(r.order_snapshot->>'$.formalSales.identity' <=> m.identity_value))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V306 postcondition failed';
 END IF;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V306','Complete confirmed order owner identities',SHA2('V306__order_owner_identity_completion.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V306');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V306','Complete confirmed order owner identities',SHA2('V306__order_owner_identity_completion.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V306');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v306_apply();
DROP PROCEDURE zsjos_v306_apply;
SELECT RELEASE_LOCK(CONCAT('v306:',DATABASE()));
