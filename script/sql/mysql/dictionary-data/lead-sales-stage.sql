-- UTF-8. Approved five initial sales-stage business options (2026-09-22).
-- Prerequisites: System dictionary tables and zsjos_schema_version; run from repository root.
-- Scope: this dictionary only, global System dictionary metadata (no tenant column).
-- One-time seed marker preserves administrator renames, disables and deletions on replay.
-- No role grants, business rows or historical snapshots. Rollback: retain the dictionary;
-- deleting it would affect later selections and is not part of rollback.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO system_dict_type(name,type,status,remark,creator,updater)
SELECT '当前销售阶段','zsjos_lead_sales_stage',0,'销售推进阶段，与有效性和成交状态独立','migration-V275','migration-V275'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='lead_sales_stage_dictionary_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='zsjos_lead_sales_stage');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 10,'待触达','pending_contact','zsjos_lead_sales_stage',0,'migration-V275','migration-V275'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='lead_sales_stage_dictionary_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='pending_contact');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 20,'已触达','contacted','zsjos_lead_sales_stage',0,'migration-V275','migration-V275'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='lead_sales_stage_dictionary_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='contacted');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 30,'有效沟通','effective_communication','zsjos_lead_sales_stage',0,'migration-V275','migration-V275'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='lead_sales_stage_dictionary_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='effective_communication');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 40,'明确需求','needs_identified','zsjos_lead_sales_stage',0,'migration-V275','migration-V275'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='lead_sales_stage_dictionary_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='needs_identified');
INSERT INTO system_dict_data(sort,label,value,dict_type,status,creator,updater)
SELECT 50,'意向客户','intent_customer','zsjos_lead_sales_stage',0,'migration-V275','migration-V275'
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='lead_sales_stage_dictionary_v1')
AND NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='intent_customer');
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
VALUES ('lead_sales_stage_dictionary_v1','Approved five initial sales stage options','lead-sales-stage-dictionary-v1');
COMMIT;
