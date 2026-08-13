-- V047: split the default submitted/owned inbox option into first-follow and qualification stages.
-- Dependencies/order: apply after V046; V005 filter tables and V014 qualification deadlines must exist.
-- Data scope: only active submitter/owner scheme JSON that is semantically equal to the system default is changed.
-- Custom draft or published JSON is preserved independently. Published upgrades append one immutable version snapshot.
-- Repeatability: after replacement the JSON no longer equals the old default, so reruns neither update nor version again.
-- Rollback limitation: publish a replacement configuration; immutable version history is intentionally retained.
-- This file must not be executed without separate environment approval.

SET @old_submitter_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["submitted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"unassigned","label":"待分配","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["unassigned"]}]},{"key":"pending_acceptance","label":"待接单","sort":20,"enabled":true,"conditions":[{"field":"assignment_status","values":["pending_acceptance"]}]},{"key":"public_pool","label":"抢单池","sort":30,"enabled":true,"conditions":[{"field":"assignment_status","values":["public_pool"]}]},{"key":"owned","label":"已归属","sort":40,"enabled":true,"conditions":[{"field":"assignment_status","values":["owned"]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["valid","won"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"valid","label":"已判有效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["valid"]}]},{"key":"won","label":"已成交","sort":20,"enabled":true,"conditions":[{"field":"status","values":["won"]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["invalid"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["closed"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]}]}';
SET @old_owner_filter = '{"groups":[{"key":"all","label":"全部客资","sort":0,"enabled":true,"sectionLabel":null,"conditions":[],"options":[]},{"key":"pending_qualification","label":"待判定客资","sort":10,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["submitted"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"owned","label":"已接单","sort":10,"enabled":true,"conditions":[{"field":"assignment_status","values":["owned"]}]}]},{"key":"valid","label":"有效客资","sort":20,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["valid","won"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"valid","label":"已判有效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["valid"]}]},{"key":"won","label":"已成交","sort":20,"enabled":true,"conditions":[{"field":"status","values":["won"]}]}]},{"key":"invalid","label":"无效客资","sort":30,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["invalid"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"invalid","label":"已判无效","sort":10,"enabled":true,"conditions":[{"field":"status","values":["invalid"]}]}]},{"key":"closed","label":"已关闭客资","sort":40,"enabled":true,"sectionLabel":"当前环节","conditions":[{"field":"status","values":["closed"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"closed","label":"已关闭","sort":10,"enabled":true,"conditions":[{"field":"status","values":["closed"]}]}]}]}';

SET @submitter_stage_options = JSON_ARRAY(
  JSON_OBJECT('key','all','label','全部','sort',0,'enabled',TRUE,'conditions',JSON_ARRAY()),
  JSON_OBJECT('key','unassigned','label','待分配','sort',10,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','assignment_status','values',JSON_ARRAY('unassigned')))),
  JSON_OBJECT('key','pending_acceptance','label','待接单','sort',20,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','assignment_status','values',JSON_ARRAY('pending_acceptance')))),
  JSON_OBJECT('key','public_pool','label','抢单池','sort',30,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','assignment_status','values',JSON_ARRAY('public_pool')))),
  JSON_OBJECT('key','first_follow_pending','label','待首跟','sort',40,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','handling_stage','values',JSON_ARRAY('first_follow_pending')))),
  JSON_OBJECT('key','qualification_pending','label','待判定','sort',50,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','handling_stage','values',JSON_ARRAY('qualification_pending'))))
);
SET @owner_stage_options = JSON_ARRAY(
  JSON_OBJECT('key','all','label','全部','sort',0,'enabled',TRUE,'conditions',JSON_ARRAY()),
  JSON_OBJECT('key','first_follow_pending','label','待首跟','sort',10,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','handling_stage','values',JSON_ARRAY('first_follow_pending')))),
  JSON_OBJECT('key','qualification_pending','label','待判定','sort',20,'enabled',TRUE,'conditions',JSON_ARRAY(JSON_OBJECT('field','handling_stage','values',JSON_ARRAY('qualification_pending'))))
);

INSERT INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`,`version_no`,`config_json`,`published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT s.id,s.published_version+1,
       JSON_SET(s.published_config_json,'$.groups[1].options',
                IF(s.audience='submitter',@submitter_stage_options,@owner_stage_options)),
       COALESCE(s.published_by,0),NOW(),'migration-V047',NOW(),'migration-V047',NOW(),b'0',s.tenant_id
FROM `zsjos_lead_inbox_filter_scheme` s
WHERE s.deleted=b'0' AND s.audience IN ('submitter','owner')
  AND ((s.audience='submitter' AND s.published_config_json=CAST(@old_submitter_filter AS JSON))
    OR (s.audience='owner' AND s.published_config_json=CAST(@old_owner_filter AS JSON)))
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_version` v
    WHERE v.tenant_id=s.tenant_id AND v.scheme_id=s.id
      AND v.version_no=s.published_version+1 AND v.deleted=b'0');

UPDATE `zsjos_lead_inbox_filter_scheme`
SET `published_config_json`=JSON_SET(`published_config_json`,'$.groups[1].options',
        IF(`audience`='submitter',@submitter_stage_options,@owner_stage_options)),
    `published_version`=`published_version`+1,`published_at`=NOW(),
    `updater`='migration-V047',`update_time`=NOW()
WHERE `deleted`=b'0' AND `audience` IN ('submitter','owner')
  AND ((`audience`='submitter' AND `published_config_json`=CAST(@old_submitter_filter AS JSON))
    OR (`audience`='owner' AND `published_config_json`=CAST(@old_owner_filter AS JSON)));

UPDATE `zsjos_lead_inbox_filter_scheme`
SET `draft_config_json`=JSON_SET(`draft_config_json`,'$.groups[1].options',
        IF(`audience`='submitter',@submitter_stage_options,@owner_stage_options)),
    `updater`='migration-V047',`update_time`=NOW()
WHERE `deleted`=b'0' AND `audience` IN ('submitter','owner')
  AND ((`audience`='submitter' AND `draft_config_json`=CAST(@old_submitter_filter AS JSON))
    OR (`audience`='owner' AND `draft_config_json`=CAST(@old_owner_filter AS JSON)));

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V047','Split lead pending handling stages','lead-pending-handling-stages-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V047','Split lead pending handling stages',
        SHA2('lead-pending-handling-stages-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
