-- V242: Payment subject management menus, permissions and finance-manager grants.
-- UTF-8. Scope: system_menu and system_role_menu rows for payment subject configuration only.
-- Prerequisites: workbench root menu 6735 (path='/zsjos'); V239/V241 payment tables.
-- Replaces the V242-V249 series. Those files kept retrying the same fixed-ID insert
-- (601960/601961, 6850/6851, 602180/602181, 602190/602191, 602200/602210) without asserting
-- ownership of the slot, so the guarded INSERT silently became a no-op each time: V058 already
-- owns 601960/601961 and V048 already owns 6850/6851.
-- Repeatability: ownership before position. Each slot is asserted free-or-ours, the workbench root
-- is resolved by path, permission codes are asserted globally unique, and every write re-applies
-- the intended values on rerun.
-- Rollback: soft-disable the six menu rows (deleted=b'1', visible=b'0') and revoke their
-- role-menu grants through System permission administration; retain the rows because
-- administrator-created grants reference them.
-- Do not execute without separate environment approval.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v242_assert_slot`;
DROP PROCEDURE IF EXISTS `zsjos_v242_apply`;

DELIMITER $$
-- Parameter collations are pinned to the tables'. system_menu/system_role_menu are
-- utf8mb4_unicode_ci (V058), but this schema's default is utf8mb4_0900_ai_ci, so an unqualified
-- VARCHAR parameter inherits 0900 from the connection and every comparison against those columns
-- dies with ERROR 1267 (illegal mix of collations). V227 was bitten by the same mismatch.
CREATE PROCEDURE `zsjos_v242_assert_slot`(
  IN p_id BIGINT,
  IN p_name VARCHAR(50) CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
  IN p_permission VARCHAR(100) CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci,
  IN p_component VARCHAR(255) CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci
)
BEGIN
  -- The slot must be free, ours, or a soft-deleted row we are reviving. Ours means the same menu
  -- name and component; the page nodes carry an empty permission, so name+component is the key.
  IF EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = p_id
      AND `deleted` = b'0'
      AND NOT (`name` = p_name AND `permission` = p_permission AND `component` = p_component)
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V242 blocked: system_menu ID is owned by another menu';
  END IF;

  -- The permission code must not already live on a different live row.
  IF p_permission <> '' AND EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = p_permission
      AND `deleted` = b'0'
      AND `id` <> p_id
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V242 blocked: permission code already uses another menu ID';
  END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE PROCEDURE `zsjos_v242_apply`()
BEGIN
  DECLARE workbench_root_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  -- Resolve the workbench root by path rather than trusting a hard-coded parent ID. These pages
  -- are workbench features, not FMS ones: the workbench root is 6735 (path='/zsjos').
  SELECT `id` INTO workbench_root_id FROM `system_menu`
    WHERE `id` = 6735 AND `path` = '/zsjos' AND `parent_id` = 0 AND `deleted` = b'0';
  IF workbench_root_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V242 requires the workbench root menu (6735 path=/zsjos)';
  END IF;

  START TRANSACTION;

  CALL `zsjos_v242_assert_slot`(602200, '支付主体管理', '', 'zsjos/payment/subject/index');
  CALL `zsjos_v242_assert_slot`(602201, '支付主体查询', 'zsjos:payment-subject:query', '');
  CALL `zsjos_v242_assert_slot`(602202, '支付主体创建', 'zsjos:payment-subject:create', '');
  CALL `zsjos_v242_assert_slot`(602203, '支付主体修改', 'zsjos:payment-subject:update', '');
  CALL `zsjos_v242_assert_slot`(602204, '支付主体删除', 'zsjos:payment-subject:delete', '');
  CALL `zsjos_v242_assert_slot`(602210, '产品支付主体配置', '', 'zsjos/payment/productSubject/index');
  CALL `zsjos_v242_assert_slot`(602211, '产品支付配置查询', 'zsjos:product-payment-subject:query', '');
  CALL `zsjos_v242_assert_slot`(602212, '产品支付配置修改', 'zsjos:product-payment-subject:configure', '');

  -- 支付主体管理 page, under the workbench root.
  -- path 必须是相对值：工作台的 buildMenuTree/resolveMenuPath 与后端
  -- WorkbenchLayoutResolver.resolvePublicPath 都把子路径拼在父路径之后。
  -- 父级 6735 path='/zsjos'，因此最终路径为 /zsjos/payment-subject。
  INSERT INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
  VALUES
    (602200,'支付主体管理','',2,66,workbench_root_id,'payment-subject','ep:bank-card',
     'zsjos/payment/subject/index','PaymentSubject',
     'admin_embed',0,b'1',b'1',b'1','V242','V242',b'0')
  ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),
    `icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),
    `workbench_render_mode`=VALUES(`workbench_render_mode`),
    `status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),
    `always_show`=VALUES(`always_show`),`updater`='V242',`update_time`=NOW(),`deleted`=b'0';

  -- 产品支付主体配置 page, also under the workbench root.
  INSERT INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
  VALUES
    (602210,'产品支付主体配置','',2,67,workbench_root_id,'product-payment-subject','ep:connection',
     'zsjos/payment/productSubject/index','ProductPaymentSubject',
     'admin_embed',0,b'1',b'1',b'1','V242','V242',b'0')
  ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),
    `icon`=VALUES(`icon`),`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),
    `workbench_render_mode`=VALUES(`workbench_render_mode`),
    `status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),
    `always_show`=VALUES(`always_show`),`updater`='V242',`update_time`=NOW(),`deleted`=b'0';

  -- Action permissions. The product configuration code is :configure, which is what
  -- ProductPaymentSubjectController enforces; the :update/:batch-update/:export codes the retired
  -- V245-V249 files published are disabled below.
  INSERT INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`status`,
     `visible`,`keep_alive`,`always_show`,`creator`,`updater`,`deleted`)
  VALUES
    (602201,'支付主体查询','zsjos:payment-subject:query',3,1,602200,'',0,b'1',b'1',b'1','V242','V242',b'0'),
    (602202,'支付主体创建','zsjos:payment-subject:create',3,2,602200,'',0,b'1',b'1',b'1','V242','V242',b'0'),
    (602203,'支付主体修改','zsjos:payment-subject:update',3,3,602200,'',0,b'1',b'1',b'1','V242','V242',b'0'),
    (602204,'支付主体删除','zsjos:payment-subject:delete',3,4,602200,'',0,b'1',b'1',b'1','V242','V242',b'0'),
    (602211,'产品支付配置查询','zsjos:product-payment-subject:query',3,1,602210,'',0,b'1',b'1',b'1','V242','V242',b'0'),
    (602212,'产品支付配置修改','zsjos:product-payment-subject:configure',3,2,602210,'',0,b'1',b'1',b'1','V242','V242',b'0')
  ON DUPLICATE KEY UPDATE
    `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
    `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),
    `status`=VALUES(`status`),`visible`=VALUES(`visible`),`keep_alive`=VALUES(`keep_alive`),
    `always_show`=VALUES(`always_show`),`updater`='V242',`update_time`=NOW(),`deleted`=b'0';

  -- Retire the leftover permission codes the V242-V249 series published. Soft-disable only;
  -- these rows may carry administrator grants, and the bare :query codes duplicated a page node.
  UPDATE `system_menu`
    SET `deleted` = b'1', `visible` = b'0', `updater` = 'V242', `update_time` = NOW()
    WHERE `deleted` = b'0'
      AND `id` NOT IN (602200, 602201, 602202, 602203, 602204, 602210, 602211, 602212)
      AND `permission` IN (
        'zsjos:product-payment-subject:update',
        'zsjos:product-payment-subject:batch-update',
        'zsjos:product-payment-subject:export'
      );

  -- Soft-disabling a menu does not remove its grants, and the admin role tree lists
  -- system_role_menu rows regardless of menu.deleted, so those stale grants would still show up.
  -- Retire grants pointing at dead payment menus: either a retired permission code, or the
  -- fixed IDs the earlier V242-V249 attempts used (page nodes carry an empty permission).
  UPDATE `system_role_menu` grant_row
  INNER JOIN `system_menu` menu ON menu.`id` = grant_row.`menu_id`
    SET grant_row.`deleted` = b'1', grant_row.`updater` = 'V242', grant_row.`update_time` = NOW()
    WHERE grant_row.`deleted` = b'0' AND menu.`deleted` = b'1'
      AND (
        menu.`permission` LIKE 'zsjos:payment-subject:%'
        OR menu.`permission` LIKE 'zsjos:product-payment-subject:%'
        OR menu.`id` IN (601960, 601961, 602180, 602181, 602190, 602191, 6850, 6851)
      );

  -- Grants go to the tenant that owns the role. finance_manager is seeded per tenant.
  INSERT INTO `system_role_menu`
    (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role.`id`, menu.`id`, 'V242', NOW(), 'V242', NOW(), b'0', role.`tenant_id`
  FROM `system_role` role
  CROSS JOIN `system_menu` menu
  WHERE role.`code` = 'finance_manager' AND role.`deleted` = b'0'
    AND menu.`deleted` = b'0'
    AND menu.`id` IN (602200, 602201, 602202, 602203, 602204, 602210, 602211, 602212)
    AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` existing
      WHERE existing.`role_id` = role.`id` AND existing.`menu_id` = menu.`id`
        AND existing.`tenant_id` = role.`tenant_id` AND existing.`deleted` = b'0'
    );

  INSERT INTO `system_role_menu`
    (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role.`id`, menu.`id`, 'V242', NOW(), 'V242', NOW(), b'0', role.`tenant_id`
  FROM `system_role` role
  CROSS JOIN `system_menu` menu
  WHERE role.`code` = 'super_admin' AND role.`deleted` = b'0'
    AND menu.`deleted` = b'0'
    AND menu.`id` IN (602200, 602201, 602202, 602203, 602204, 602210, 602211, 602212)
    AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` existing
      WHERE existing.`role_id` = role.`id` AND existing.`menu_id` = menu.`id`
        AND existing.`tenant_id` = role.`tenant_id` AND existing.`deleted` = b'0'
    );

  -- Drop the stale registry rows the retired V242-V249 files wrote. Their checksums were literal
  -- placeholder strings rather than file hashes, so the runner would report "applied migration
  -- checksum changed" instead of reapplying. The runner records this file's real checksum after
  -- commit; nothing is inserted here.
  DELETE FROM `zsjos_module_schema_version`
    WHERE `version` IN ('V242','V243','V244','V245','V246','V247','V248','V249');
  DELETE FROM `zsjos_schema_version`
    WHERE `version` IN ('V242','V243','V244','V245','V246','V247','V248','V249');

  COMMIT;
END$$
DELIMITER ;

CALL `zsjos_v242_apply`();
DROP PROCEDURE `zsjos_v242_apply`;
DROP PROCEDURE `zsjos_v242_assert_slot`;
