-- 快速检查关键数据

-- 1. 支付主体数据（重点看是否有2条）
SELECT '【1. 支付主体数据】' as '';
SELECT id, subject_code, subject_name, cusid, is_default 
FROM zsjos_payment_subject;

-- 2. 菜单数量（6 条权限：支付主体 4 + 产品支付配置 2）
SELECT '【2. 菜单数量】' as '';
SELECT COUNT(*) as menu_count FROM system_menu
WHERE permission LIKE 'zsjos:payment-subject%' AND deleted = b'0';

-- 3. 财务主管角色权限数（应该是 6）
SELECT '【3. 财务主管权限数】' as '';
SELECT COUNT(*) as permission_count
FROM system_role r
JOIN system_role_menu rm ON r.id = rm.role_id AND rm.deleted = b'0'
JOIN system_menu m ON rm.menu_id = m.id AND m.deleted = b'0'
WHERE r.code = 'finance_manager' AND r.deleted = b'0'
  AND m.permission LIKE 'zsjos:payment-subject%';

-- 4. 版本记录（应该是 4 条：V239-V242）
SELECT '【4. 版本记录】' as '';
SELECT COUNT(*) as version_count FROM zsjos_schema_version
WHERE version IN ('V239', 'V240', 'V241', 'V242');

-- 5. 产品数量
SELECT '【5. 产品数量】' as '';
SELECT COUNT(*) as product_count FROM zsjos_product WHERE deleted = b'0';

-- 6. 父菜单是否存在
SELECT '【6. 父菜单】' as '';
SELECT id, name FROM system_menu WHERE id = 601951;

-- 7. 财务主管角色是否存在
SELECT '【7. 财务主管角色】' as '';
SELECT id, name, code FROM system_role WHERE code = 'finance_manager';
