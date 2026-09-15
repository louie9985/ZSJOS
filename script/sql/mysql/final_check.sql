-- 最终验证检查

SELECT '=== 1. 菜单数量（应该是6个）===' as '检查项';
SELECT COUNT(*) as '菜单数量' 
FROM system_menu 
WHERE permission LIKE 'zsjos:payment-subject%' 
  AND deleted = b'0';

SELECT '=== 2. 菜单详情（不应有重复权限）===' as '检查项';
SELECT 
  id as '菜单ID',
  name as '菜单名称',
  permission as '权限标识',
  parent_id as '父菜单ID',
  deleted as '是否删除'
FROM system_menu 
WHERE permission LIKE 'zsjos:payment-subject%' 
  AND deleted = b'0'
ORDER BY permission;

SELECT '=== 3. 财务主管权限数（应该是6个）===' as '检查项';
SELECT COUNT(*) as '权限数量'
FROM system_role r
JOIN system_role_menu rm ON r.id = rm.role_id AND rm.deleted = b'0'
JOIN system_menu m ON rm.menu_id = m.id AND m.deleted = b'0'
WHERE r.code = 'finance_manager' 
  AND r.deleted = b'0'
  AND (m.permission LIKE 'zsjos:payment-subject%' 
       OR m.permission LIKE 'zsjos:product-payment-subject%');

SELECT '=== 4. 财务主管权限详情 ===' as '检查项';
SELECT 
  m.id as '菜单ID',
  m.name as '菜单名称',
  m.permission as '权限标识'
FROM system_role r
JOIN system_role_menu rm ON r.id = rm.role_id AND rm.deleted = b'0'
JOIN system_menu m ON rm.menu_id = m.id AND m.deleted = b'0'
WHERE r.code = 'finance_manager' 
  AND r.deleted = b'0'
  AND (m.permission LIKE 'zsjos:payment-subject%' 
       OR m.permission LIKE 'zsjos:product-payment-subject%')
ORDER BY m.permission;

SELECT '=== 5. 检查是否还有重复权限 ===' as '检查项';
SELECT 
  permission as '权限标识',
  COUNT(*) as '出现次数'
FROM system_menu 
WHERE permission LIKE 'zsjos:payment-subject%' 
  AND deleted = b'0'
GROUP BY permission
HAVING COUNT(*) > 1;
-- 应该返回空结果（0行）

SELECT '=== ✅ 如果以上全部正常，说明修复成功！===' as '结论';
