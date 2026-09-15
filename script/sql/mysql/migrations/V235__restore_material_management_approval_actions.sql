-- V235: restore management entry and hidden approval action permissions after V234.
-- 审批按钮权限必须挂在素材库目录（80010）下，不能挂在素材管理页（80012）下：
-- 80012 是 admin_embed 页，Vue Admin 会把子菜单当目录子路由，而审批页是 React 专有页面、
-- 在 Vue 侧解析不出组件，结果 manage 退化成空 ParentLayout，嵌入页白屏。
SET NAMES utf8mb4;
UPDATE system_menu SET deleted=b'0',visible=b'1',workbench_render_mode='admin_embed',component='zsjos/material/index',updater='V235',update_time=NOW() WHERE permission='zsjos:material:manage' AND id=80012;
UPDATE system_menu SET deleted=b'0',visible=b'0',parent_id=80010,workbench_render_mode='admin_only',updater='V235',update_time=NOW() WHERE permission IN ('zsjos:material-approval:query','zsjos:material-approval:approve','zsjos:material-approval:reject');
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V235','Restore material management approval actions',SHA2('V235__restore_material_management_approval_actions.sql',256),NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V235','Restore material management approval actions',SHA2('V235__restore_material_management_approval_actions.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
