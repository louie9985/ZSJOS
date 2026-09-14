-- 2026 教育产品目录只读校验。使用 utf8mb4 客户端执行。
SET NAMES utf8mb4;
SELECT 'visible_tenant_product_count' AS check_name, COUNT(*) AS actual, 26 AS expected
FROM zsjos_product WHERE product_ref REGEXP '^edu2026_[0-9]+$'
  AND tenant_id=1 AND status=0 AND deleted=0;
SELECT 'visible_tenant_sku_count' AS check_name, COUNT(*) AS actual, 96 AS expected
FROM zsjos_product_sku WHERE sku_ref REGEXP '^edu2026_[0-9]+_sku_[0-9]+$'
  AND tenant_id=1 AND deleted=0;
SELECT 'catalog_root_categories' AS check_name, COUNT(DISTINCT c.id) AS actual, 6 AS expected
FROM zsjos_product p JOIN zsjos_product_category c ON c.id=p.category_id
WHERE p.product_ref REGEXP '^edu2026_[0-9]+$' AND p.tenant_id=1 AND p.deleted=0
  AND c.tenant_id=1 AND c.parent_id=0 AND c.level=1 AND c.status=0 AND c.deleted=0;
SELECT 'active_synthetic_root' AS check_name, COUNT(*) AS actual, 0 AS expected
FROM zsjos_product_category WHERE tenant_id=1 AND name='2026教育产品' AND status=0 AND deleted=0;
SELECT 'sku_tenant_mismatch' AS check_name, COUNT(*) AS actual, 0 AS expected
FROM zsjos_product_sku s JOIN zsjos_product p ON p.id=s.spu_id
WHERE p.product_ref REGEXP '^edu2026_[0-9]+$' AND p.tenant_id=1 AND p.deleted=0
  AND s.deleted=0 AND s.tenant_id<>p.tenant_id;
SELECT name, HEX(name) AS name_utf8_hex FROM zsjos_product
WHERE tenant_id=1 AND product_ref='edu2026_1' AND deleted=0;
SELECT 'product_count' AS check_name, COUNT(*) AS actual, 26 AS expected
FROM zsjos_product WHERE product_ref LIKE 'edu2026_%' AND deleted = b'0';
SELECT 'sku_count' AS check_name, COUNT(*) AS actual, 96 AS expected
FROM zsjos_product_sku WHERE sku_ref LIKE 'edu2026_%' AND deleted = b'0';
SELECT 'enabled_sku_count' AS check_name, SUM(status = 0) AS actual, 94 AS expected
FROM zsjos_product_sku WHERE sku_ref LIKE 'edu2026_%' AND deleted = b'0';
SELECT 'disabled_legacy_sku_count' AS check_name, COUNT(*) AS actual, 115 AS expected
FROM zsjos_product_sku WHERE sku_ref NOT LIKE 'edu2026_%' AND deleted = b'0' AND status = 1;
SELECT 'subject_discount_rule' AS check_name, COUNT(*) AS actual, 1 AS expected
FROM zsjos_product_sku WHERE sku_name LIKE '%无忧实验班｜单科｜2980元/科%'
  AND price_unit = 'SUBJECT' AND min_deal_type = 'DISCOUNT_RATE' AND min_deal_rate = 0.8;
SELECT 'anhui_fixed_rule' AS check_name, COUNT(*) AS actual, 1 AS expected
FROM zsjos_product_sku WHERE sku_name LIKE '%安徽拜师跟诊%'
  AND min_deal_type = 'FIXED' AND min_deal_price = 40800;
SELECT 'deferred_issuer_skus' AS check_name, COUNT(*) AS actual, 0 AS expected
FROM zsjos_product WHERE name IN ('中医健康管理师','中医食疗调理师','中医预防保健调理师')
  AND EXISTS (SELECT 1 FROM zsjos_product_sku s WHERE s.spu_id = zsjos_product.id AND s.deleted = b'0');

