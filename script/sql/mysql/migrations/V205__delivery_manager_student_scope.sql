-- UTF-8. V205: grant the delivery manager role department-scoped class and student read access.
-- Dependencies/order: apply after V204. Requires the V192 unified class menu (73620/73628) and the
-- V193/V195 student-management route repair (73020).
-- Data scope: system_role.data_scope for the delivery_manager role plus role-menu read grants only.
-- No class, service-relation, student, order or BPM business row is created, changed or deleted.
-- Read-only grants: page 73620, hidden managed-scope capability 73628 and student page 73020.
-- Write capabilities (73621 create, 73622 update, 73623 complete, 73625 direct-transfer) and student
-- write buttons (73427, 73428, 73440) are intentionally NOT granted; managed read never implies write.
-- Repeatability: every grant is guarded by NOT EXISTS and the data_scope update is idempotent.
-- Recovery: forward-only. A later reviewed migration must retire grants; do not rewrite V205.
-- NOTE: this migration deliberately removes delivery_manager from the V071 zero-ZSJOS role set.
-- verify-bootstrap.sql `V071 zero-ZSJOS roles` is updated in the same change; the two must stay in sync.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v205_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v205_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V204')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V204') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V205 requires V204 in both schema-version registries';
  END IF;

  -- The unified class page and the hidden managed-scope capability must both exist before granting.
  IF NOT EXISTS (SELECT 1 FROM `system_menu`
                 WHERE `id`=73620 AND `permission`='zsjos:delivery-class:query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V205 requires the V192 unified class page 73620';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu`
                 WHERE `id`=73628 AND `permission`='zsjos:delivery-class:query-managed' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V205 requires the V192 managed-scope capability 73628';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu`
                 WHERE `id`=73020 AND `permission`='zsjos:student:query-my' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V205 requires the student-management page 73020';
  END IF;

  START TRANSACTION;

  -- Department subtree scope. DataScopeEnum: 1=ALL, 2=DEPT_CUSTOM, 3=DEPT_ONLY, 4=DEPT_AND_CHILD, 5=SELF.
  -- Only the role's own scope is raised from DEPT_ONLY to DEPT_AND_CHILD; custom dept ids stay untouched.
  UPDATE `system_role`
  SET `data_scope`=4, `updater`='V205', `update_time`=NOW()
  WHERE `code`='delivery_manager' AND `deleted`=b'0' AND `data_scope`=3;

  -- Read-only grants, one active relation per role and tenant.
  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role_row.id, target.menu_id, 'V205', NOW(), 'V205', NOW(), b'0', role_row.tenant_id
  FROM `system_role` role_row
  JOIN (SELECT 73620 AS menu_id UNION ALL SELECT 73628 UNION ALL SELECT 73020) target
  WHERE role_row.code='delivery_manager' AND role_row.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                    WHERE existing.role_id=role_row.id AND existing.menu_id=target.menu_id
                      AND existing.tenant_id=role_row.tenant_id AND existing.deleted=b'0');

  -- Tenant packages must expose the granted menus, otherwise the route guard rejects navigation.
  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73620), `updater`='V205', `update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73020','$') AND NOT JSON_CONTAINS(`menu_ids`,'73620','$');
  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73628), `updater`='V205', `update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73620','$') AND NOT JSON_CONTAINS(`menu_ids`,'73628','$');
  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73020), `updater`='V205', `update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73620','$') AND NOT JSON_CONTAINS(`menu_ids`,'73020','$');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V205','Delivery manager department student scope',
          SHA2('V205__delivery_manager_student_scope.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V205','Delivery manager department student scope',
          SHA2('V205__delivery_manager_student_scope.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v205_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v205_apply`;
