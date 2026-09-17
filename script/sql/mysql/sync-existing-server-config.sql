-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Non-destructive repair for an existing ZSJOS database.
-- Scope: missing dictionary definitions/data and missing role-menu grants.
-- This file never deletes, disables, or rewrites existing rows. It does not
-- seed users, business instances, BPM runtime tables, or Flowable IDs.
-- Run from the repository root with mysql so SOURCE paths resolve.
SET NAMES utf8mb4;

-- Core dictionary baseline. INSERT IGNORE plus the baseline's stable guards
-- preserve administrator edits and fill only missing rows.
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/04-bootstrap-zsjos-feedback-dictionary.sql;

-- Later dictionary-only additions whose migrations also contain unrelated DDL
-- are reproduced here through their idempotent data statements.
SOURCE script/sql/mysql/migrations/V104__new_media_business_dictionary.sql;
SOURCE script/sql/mysql/migrations/V129__seed_director_form_dictionaries.sql;
-- Full active dictionary snapshot from the reviewed source database. The dump
-- contains only system_dict_type/system_dict_data rows with deleted=0 and is
-- safe to replay with INSERT IGNORE.
SOURCE script/sql/mysql/dictionary-data/current-database-dictionary-snapshot.sql;

-- Current approved business dictionary snapshot. Keyed by dict_type + value
-- so reruns preserve administrator edits and do not depend on row IDs.
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1,'不确定','不确定','zsjos_lead_category',0,'default','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='不确定' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7,'C类【低意向】','C类【低意向】','zsjos_lead_category',0,'default','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='C类【低意向】' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 8,'B类【中意向】','B类【中意向】','zsjos_lead_category',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='B类【中意向】' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 9,'A类【高意向】','A类【高意向】','zsjos_lead_category',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='A类【高意向】' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 11,'S类【重点客户-待成交】','S类【重点客户-待成交】','zsjos_lead_category',0,'default','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_category' AND `value`='S类【重点客户-待成交】' AND `deleted`=b'0');

INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1,'抖音自然流','抖音自然流','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='抖音自然流' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 2,'小红书私信','小红书私信','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='小红书私信' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 3,'信息流投放','信息流投放','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='信息流投放' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 5,'老学员转介绍','老学员转介绍','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='老学员转介绍' AND `deleted`=b'0');
INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 0,'其他','其他','zsjos_lead_source_channel',0,'info','','','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_lead_source_channel' AND `value`='其他' AND `deleted`=b'0');

INSERT INTO `system_dict_data`
  (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 99,'未知','unknown','zsjos_certificate_practice',0,'default','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_certificate_practice' AND `value`='unknown' AND `deleted`=b'0');
INSERT INTO `system_dict_data`
  (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 99,'部分掌握','partial','zsjos_video_skill',0,'default','sync-existing-server',NOW(),'sync-existing-server',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='zsjos_video_skill' AND `value`='partial' AND `deleted`=b'0');

-- Role assignments are maintained through System role management.
SOURCE script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql;

-- BPM assets are deliberately not SQL. Validate and import them through the
-- BPM management UI using script/bpm/manifest.json and the procedure documented
-- in docs/operations/zsjos-existing-db-initialization.md.
