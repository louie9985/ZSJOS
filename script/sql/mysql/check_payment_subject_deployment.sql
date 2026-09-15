-- ============================================================
-- 支付主体功能部署完整性检查脚本
-- 生成时间: 2026-09-15
-- 用途: 验证 V239-V242 迁移脚本执行结果
-- ============================================================

-- 1. 检查数据表是否创建
SELECT '=== 1. 检查数据表是否创建 ===' as '检查步骤';
SELECT
  TABLE_NAME as '表名',
  TABLE_COMMENT as '表说明',
  CREATE_TIME as '创建时间'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'zsjos_payment_subject',
    'zsjos_product_payment_subject'
  )
ORDER BY TABLE_NAME;
-- 预期结果: 2 行记录

SELECT '' as '';
SELECT '=== 2. 检查支付主体数据 ===' as '检查步骤';
SELECT
  id as 'ID',
  subject_code as '主体编码',
  subject_name as '主体名称',
  cusid as '商户号',
  appid as '应用ID',
  LEFT(merchant_private_key, 30) as '私钥前30字符',
  is_default as '是否默认',
  deleted as '是否删除'
FROM zsjos_payment_subject
ORDER BY id;
-- 预期结果: 2 行记录（school 学校主体, company 公司主体）

SELECT '' as '';
SELECT '=== 3. 检查菜单是否创建 ===' as '检查步骤';
SELECT
  id as '菜单ID',
  name as '菜单名称',
  permission as '权限标识',
  parent_id as '父菜单ID',
  sort as '排序',
  visible as '是否显示',
  deleted as '是否删除'
FROM system_menu
WHERE permission IN (
  'zsjos:payment-subject:query',
  'zsjos:payment-subject:create',
  'zsjos:payment-subject:update',
  'zsjos:payment-subject:delete',
  'zsjos:product-payment-subject:query',
  'zsjos:product-payment-subject:configure'
)
ORDER BY permission;
-- 预期结果: 6 行记录，deleted 都为 b'0'（:update 已统一为 :configure）

SELECT '' as '';
SELECT '=== 4. 检查财务主管角色权限 ===' as '检查步骤';
SELECT
  r.id as '角色ID',
  r.name as '角色名称',
  r.code as '角色编码',
  r.status as '状态',
  COUNT(rm.id) as '已授权菜单数'
FROM system_role r
LEFT JOIN system_role_menu rm ON r.id = rm.role_id AND rm.deleted = b'0'
LEFT JOIN system_menu m ON rm.menu_id = m.id AND m.deleted = b'0'
WHERE r.code = 'finance_manager' AND r.deleted = b'0'
  AND m.permission LIKE 'zsjos:payment-subject%'
GROUP BY r.id, r.name, r.code, r.status;
-- 预期结果: 1 行，已授权权限数 = 6（另有 2 个页面节点不带权限码，不在此口径内）

SELECT '' as '';
SELECT '=== 5. 检查版本记录 ===' as '检查步骤';
SELECT
  version as '版本号',
  description as '说明',
  installed_at as '安装时间'
FROM zsjos_schema_version
WHERE version IN ('V239', 'V240', 'V241', 'V242')
ORDER BY version;
-- 预期结果: 4 行记录（V243-V249 已删除并合并进 V242）

SELECT '' as '';
SELECT '=== 6. 检查是否有重复菜单 ===' as '检查步骤';
SELECT
  permission as '权限标识',
  COUNT(*) as '数量',
  GROUP_CONCAT(id) as '菜单ID列表'
FROM system_menu
WHERE permission LIKE 'zsjos:payment-subject%'
  AND deleted = b'0'
GROUP BY permission
HAVING COUNT(*) > 1;
-- 预期结果: 0 行（没有重复）

SELECT '' as '';
SELECT '=== 7. 检查父菜单是否存在 ===' as '检查步骤';
SELECT
  id as '菜单ID',
  name as '菜单名称',
  path as '路径',
  deleted as '是否删除'
FROM system_menu
WHERE id = 601951;
-- 预期结果: 1 行记录（设置管理菜单）

SELECT '' as '';
SELECT '=== 8. 检查产品表和数据 ===' as '检查步骤';
SELECT
  COUNT(*) as '产品总数'
FROM zsjos_product
WHERE deleted = b'0';
-- 预期结果: 大于 0

SELECT '' as '';
SELECT '=== 9. 检查财务主管角色是否存在 ===' as '检查步骤';
SELECT
  id as '角色ID',
  name as '角色名称',
  code as '角色编码',
  status as '状态',
  deleted as '是否删除'
FROM system_role
WHERE code = 'finance_manager';
-- 预期结果: 1 行记录

SELECT '' as '';
SELECT '=== 检查完成 ===' as '状态';
SELECT
  '如果以上所有检查都符合预期，说明支付主体功能已正确部署！' as '结论',
  '下一步请重启后端和前端服务，然后进行功能测试。' as '建议';
