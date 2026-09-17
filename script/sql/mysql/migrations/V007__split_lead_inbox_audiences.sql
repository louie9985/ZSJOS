-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- Splits the employee lead inbox into fixed submitter and owner routes.
-- Dependencies: V006, workbench menu 6735, lead query menu 6770, and current role-menu grants.
-- Execution order: hide the legacy mixed route, add fixed routes, derive grants from existing
-- submit/claim/accept/query-all permissions, then record V007.
-- Repeatability: the menu update is idempotent and inserts use stable IDs plus NOT EXISTS guards.
-- Data scope: menu metadata and role-menu grants only; no lead, account, or filter-scheme rows change.
-- Recovery: forward-only. Restore route visibility and manage grants through system permissions if needed.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

UPDATE `system_menu`
SET `visible`=b'0', `updater`='migration-V007', `update_time`=NOW()
WHERE `id`=6770 AND `permission`='zsjos:lead:query' AND `deleted`=b'0';

INSERT IGNORE INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(6778,'我提交的','zsjos:lead:query-submitted',2,15,6735,'leads/submitted','ep:upload-filled','zsjos-workbench','LeadSubmittedInboxPage',0,b'1',b'1',b'1','migration-V007',NOW(),'migration-V007',NOW(),b'0'),
(6779,'我负责的','zsjos:lead:query-owned',2,16,6735,'leads/owned','ep:user-filled','zsjos-workbench','LeadOwnedInboxPage',0,b'1',b'1',b'1','migration-V007',NOW(),'migration-V007',NOW(),b'0');

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V007','Split lead inbox into fixed submitter and owner routes','lead-inbox-fixed-audiences-v1');
