-- V195: unify personal/team sales-order menus, permissions and filter templates.
-- Data scope: menu/role-menu metadata and advanced-filter templates only; no order rows.
-- Repeatability: all writes are guarded by stable permission/page keys.
-- Rollback limitation: restore the pre-migration menu, role-menu and template rows from backup.
SET NAMES utf8mb4;

INSERT INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 73511,'订单管理','zsjos:sales-order:query-management',2,17,6735,'sales-orders','ep:tickets','zsjos/mySalesOrder/index','ZsjosSalesOrderManagement',0,b'1',b'1',b'1','migration-V195',NOW(),'migration-V195',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:sales-order:query-management' AND deleted=b'0');

INSERT INTO system_role_menu
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.role_id, menu.id, 'migration-V195', NOW(), 'migration-V195', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
JOIN system_menu old_menu ON old_menu.id=rm.menu_id
JOIN system_menu menu ON menu.permission='zsjos:sales-order:query-management' AND menu.deleted=b'0'
WHERE old_menu.permission IN ('zsjos:sales-order:query-own','zsjos:sales-order:query-team')
  AND old_menu.deleted=b'0' AND rm.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
                  WHERE existing.role_id=rm.role_id AND existing.menu_id=menu.id
                    AND existing.tenant_id=rm.tenant_id AND existing.deleted=b'0');

UPDATE system_role_menu rm JOIN system_menu old_menu ON old_menu.id=rm.menu_id
SET rm.deleted=b'1', rm.updater='migration-V195', rm.update_time=NOW()
WHERE old_menu.permission IN ('zsjos:sales-order:query-own','zsjos:sales-order:query-team')
  AND rm.deleted=b'0';

UPDATE system_menu
SET deleted=b'1', status=1, visible=b'0', updater='migration-V195', update_time=NOW()
WHERE permission IN ('zsjos:sales-order:query-own','zsjos:sales-order:query-team') AND deleted=b'0';

CREATE TEMPORARY TABLE tmp_zsjos_order_templates AS
SELECT source.id, source.page_key source_page, source.name original_name,
       EXISTS (SELECT 1 FROM zsjos_advanced_filter_template peer
               WHERE peer.scene=source.scene AND peer.page_key IN ('sales_order_my','sales_order_team')
                 AND peer.page_key<>source.page_key AND peer.scope=source.scope
                 AND COALESCE(peer.owner_user_id,0)=COALESCE(source.owner_user_id,0)
                 AND peer.name=source.name AND peer.deleted=b'0') conflict_name
FROM zsjos_advanced_filter_template source
WHERE source.scene='order' AND source.page_key IN ('sales_order_my','sales_order_team') AND source.deleted=b'0';
UPDATE zsjos_advanced_filter_template target
JOIN tmp_zsjos_order_templates snapshot ON snapshot.id=target.id
SET target.name=CASE WHEN snapshot.conflict_name THEN
                    CONCAT(LEFT(snapshot.original_name, 22),
                           CASE snapshot.source_page WHEN 'sales_order_my' THEN '（我的订单）' ELSE '（团队订单）' END)
                    ELSE snapshot.original_name END,
    target.page_key='sales_order_management';
UPDATE zsjos_advanced_filter_template SET page_key='sales_order_management'
WHERE scene='order' AND page_key IN ('sales_order_my','sales_order_team');

UPDATE zsjos_advanced_filter_template SET default_template=b'0'
WHERE scene='order' AND page_key='sales_order_management' AND deleted=b'0';
UPDATE zsjos_advanced_filter_template t
JOIN (
    SELECT latest.id
    FROM (
        SELECT candidate.id
        FROM zsjos_advanced_filter_template candidate
        WHERE candidate.scene='order' AND candidate.page_key='sales_order_management' AND candidate.deleted=b'0'
          AND NOT EXISTS (
              SELECT 1 FROM zsjos_advanced_filter_template newer
              WHERE newer.scene=candidate.scene AND newer.page_key=candidate.page_key
                AND newer.scope=candidate.scope
                AND COALESCE(newer.owner_user_id,0)=COALESCE(candidate.owner_user_id,0)
                AND newer.deleted=b'0'
                AND (newer.update_time > candidate.update_time
                     OR (newer.update_time=candidate.update_time AND newer.id > candidate.id)))
    ) latest
) chosen ON chosen.id=t.id
SET t.default_template=b'1'
WHERE t.scene='order' AND t.page_key='sales_order_management' AND t.deleted=b'0'
;

INSERT INTO zsjos_schema_version (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V195','Unify sales order management menu and templates',SHA2('V195__unify_sales_order_management.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V195','Unify sales order management menu and templates',SHA2('V195__unify_sales_order_management.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);

SELECT 'sales_order_v193_menu' check_name,
       IF(EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:sales-order:query-management'
                  AND path='sales-orders' AND deleted=b'0'),'PASS','FAIL') result;
