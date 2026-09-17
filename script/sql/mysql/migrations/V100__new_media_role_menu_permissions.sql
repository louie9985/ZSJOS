-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V100: new-media role menu permissions and director-owned student page.
-- Draft only. Do not execute without separate authorization.
SET NAMES utf8mb4;
SET @v100_workbench_menu_id := (SELECT id FROM system_menu WHERE path='/zsjos' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);

INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(7022,'我的学员','zsjos:media-student:query-my',2,47,@v100_workbench_menu_id,'/zsjos/media-students','ep:user','zsjos-workbench','MediaStudentsPage',0,b'1',b'1',b'1','migration-V100',NOW(),'migration-V100',NOW(),b'0');

INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V100','New-media role menu permissions and director student page','new-media-role-menu-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V100','New-media role menu permissions and director student page',SHA2('new-media-role-menu-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
