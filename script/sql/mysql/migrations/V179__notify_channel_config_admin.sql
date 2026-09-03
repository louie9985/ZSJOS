-- V179: expose tenant notification-channel enablement in Admin.
-- Additive and repeatable. Does not seed credentials or enable any tenant.
SET NAMES utf8mb4;
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '通知渠道', '', 2, 30, p.id, 'notify-channel', 'ep:connection', 'system/notify/channel/index',
       'SystemNotifyChannel', 0, b'1', b'1', b'1', 'migration-V179', NOW(), 'migration-V179', NOW(), b'0'
FROM system_menu p
WHERE p.id=2144 AND p.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.path='notify-channel' AND x.deleted=b'0');

INSERT INTO system_menu (name, permission, type, sort, parent_id, status, visible, keep_alive, always_show,
                         creator, create_time, updater, update_time, deleted)
SELECT '通知渠道查询', 'system:notify-channel:query', 3, 1, p.id, 0, b'1', b'1', b'1',
       'migration-V179', NOW(), 'migration-V179', NOW(), b'0'
FROM system_menu p WHERE p.path='notify-channel' AND p.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission='system:notify-channel:query' AND x.deleted=b'0');

INSERT INTO system_menu (name, permission, type, sort, parent_id, status, visible, keep_alive, always_show,
                         creator, create_time, updater, update_time, deleted)
SELECT '通知渠道更新', 'system:notify-channel:update', 3, 2, p.id, 0, b'1', b'1', b'1',
       'migration-V179', NOW(), 'migration-V179', NOW(), b'0'
FROM system_menu p WHERE p.path='notify-channel' AND p.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.permission='system:notify-channel:update' AND x.deleted=b'0');

INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, 'migration-V179', NOW(), 'migration-V179', NOW(), b'0', 0
FROM system_role r JOIN system_menu m ON m.permission IN ('system:notify-channel:query','system:notify-channel:update')
WHERE r.code='system_administrator' AND r.deleted=b'0' AND m.deleted=b'0';

INSERT IGNORE INTO zsjos_schema_version (version, description, checksum)
VALUES ('V179','Notification channel administration','notify-channel-admin-v1');
