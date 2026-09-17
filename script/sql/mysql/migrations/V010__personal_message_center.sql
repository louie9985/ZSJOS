-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
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

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V010','Add personal all and unread message-center menus','personal-message-center-v1');
