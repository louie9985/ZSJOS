-- UTF-8. V261 positioning evidence; prerequisite: V258 positioning submissions/application schema.
-- New business capability: scoped additive columns and button metadata only. No role grants or business row rewrites.
-- Execute before updated backend. Repeatable; existing submissions retain evidence_required=0.
-- Rollback: revert application code while retaining columns and receipts; never delete confirmation history.
SET NAMES utf8mb4;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_positioning_card_submission' AND column_name='evidence_required')=0, 'ALTER TABLE zsjos_positioning_card_submission ADD COLUMN evidence_required bit(1) NOT NULL DEFAULT b''0'' COMMENT ''本轮是否要求确认凭证''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_positioning_card_submission' AND column_name='evidence_json')=0, 'ALTER TABLE zsjos_positioning_card_submission ADD COLUMN evidence_json longtext NULL COMMENT ''运营确认凭证及上传记录''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '上传定位卡确认凭证','zsjos:positioning-card:evidence',3,91,parent_id,'','','',0,b'1',b'1',b'1','1','1',b'0'
FROM system_menu WHERE permission='zsjos:positioning-card:create' AND deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.permission='zsjos:positioning-card:evidence' AND existing.deleted=0)
ORDER BY id LIMIT 1;
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum) VALUES('V261','定位卡确认凭证','positioning-evidence-v1');
