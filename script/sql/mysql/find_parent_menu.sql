-- 查找合适的父菜单位置
SET NAMES utf8mb4;

-- 1. 查找所有一级菜单（parent_id = 0）
SELECT '=== 1. 所有一级菜单 ===' as '';
SELECT id, name, path, icon, sort
FROM system_menu
WHERE parent_id = 0 AND deleted = b'0' AND type = 1
ORDER BY sort;

-- 2. 查找 zsjos 模块的菜单结构
SELECT '=== 2. zsjos 模块的菜单结构 ===' as '';
SELECT id, name, parent_id, type, path, sort
FROM system_menu
WHERE (path LIKE 'zsjos%' OR component LIKE 'zsjos%')
  AND deleted = b'0'
ORDER BY parent_id, sort;

-- 3. 查找财务相关的菜单
SELECT '=== 3. 财务相关的菜单 ===' as '';
SELECT id, name, parent_id, type, path, sort
FROM system_menu
WHERE name LIKE '%财务%' AND deleted = b'0'
ORDER BY parent_id, sort;

-- 4. 查找 FMS 模块的菜单
SELECT '=== 4. FMS 模块的菜单 ===' as '';
SELECT id, name, parent_id, type, path, sort
FROM system_menu
WHERE (name LIKE '%FMS%' OR path LIKE 'fms%') AND deleted = b'0'
ORDER BY parent_id, sort;

-- 5. 查找 ID 范围 601800-602000 的所有菜单
SELECT '=== 5. ID 601800-602000 的菜单 ===' as '';
SELECT id, name, parent_id, type, path, deleted
FROM system_menu
WHERE id BETWEEN 601800 AND 602000
ORDER BY id;
