-- Adds personal all-message and unread-message entries under the existing message center.
-- Dependencies: system_menu id 2739 and its current system_role_menu grants must exist.
-- Execution order: add stable child menus, inherit grants from the parent, then record V010.
-- Repeatability: stable menu IDs and active-grant NOT EXISTS guards make reruns idempotent.
-- Data scope: menu metadata and role-menu grants only; no message, account, or role rows change.
-- Recovery: forward-only. Hide the child menus or remove grants through system permissions if needed.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6783,'全部消息','',2,5,2739,'all','ep:message','system/notify/my/all/index','MyNotifyMessageAll',0,b'1',b'1',b'1','migration-V010',NOW(),'migration-V010',NOW(),b'0'),
(6784,'未读消息','',2,6,2739,'unread','ep:message-box','system/notify/my/unread/index','MyNotifyMessageUnread',0,b'1',b'1',b'1','migration-V010',NOW(),'migration-V010',NOW(),b'0');

INSERT INTO `system_role_menu`
(`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source.role_id, target.menu_id, 'migration-V010', NOW(), 'migration-V010', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
CROSS JOIN (SELECT 6783 AS menu_id UNION ALL SELECT 6784) target
WHERE source.menu_id=2739 AND source.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id
      AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0'
  );

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V010','Add personal all and unread message-center menus','personal-message-center-v1');
