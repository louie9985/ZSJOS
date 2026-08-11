-- V029: add the configurable approval-person view to the shared inbox filter scheme.
-- Non-destructive and repeatable: only inserts missing reviewer rows and their first version.
SET @reviewer_filter = '{"groups":[{"key":"todo","label":"待处理","sort":10,"enabled":true,"sectionLabel":"审批环节","conditions":[{"field":"handled","values":["todo"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"registrationReview","label":"教务审批","sort":10,"enabled":true,"conditions":[{"field":"task_definition_key","values":["registrationReview"]}]},{"key":"financeReview","label":"财务审批","sort":20,"enabled":true,"conditions":[{"field":"task_definition_key","values":["financeReview"]}]}]},{"key":"done","label":"已处理","sort":20,"enabled":true,"sectionLabel":"审批环节","conditions":[{"field":"handled","values":["done"]}],"options":[{"key":"all","label":"全部","sort":0,"enabled":true,"conditions":[]},{"key":"registrationReview","label":"教务审批","sort":10,"enabled":true,"conditions":[{"field":"task_definition_key","values":["registrationReview"]}]},{"key":"financeReview","label":"财务审批","sort":20,"enabled":true,"conditions":[{"field":"task_definition_key","values":["financeReview"]}]}]}]}';

INSERT INTO `zsjos_lead_inbox_filter_scheme`
(`audience`,`name`,`draft_config_json`,`published_config_json`,`published_version`,`published_by`,`published_at`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'reviewer', '审批人视角', @reviewer_filter, @reviewer_filter, 1, 1, NOW(), 0, '1', NOW(), '1', NOW(), b'0', t.id
FROM `system_tenant` t
WHERE t.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_scheme` s
    WHERE s.tenant_id = t.id AND s.audience = 'reviewer' AND s.deleted = b'0'
  );

INSERT INTO `zsjos_lead_inbox_filter_version`
(`scheme_id`,`version_no`,`config_json`,`published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT s.id, 1, s.published_config_json, 1, s.published_at, '1', NOW(), '1', NOW(), b'0', s.tenant_id
FROM `zsjos_lead_inbox_filter_scheme` s
WHERE s.audience = 'reviewer' AND s.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `zsjos_lead_inbox_filter_version` v
    WHERE v.scheme_id = s.id AND v.version_no = 1 AND v.deleted = b'0'
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V029','Add sales-order approval reviewer filter scheme',NULL)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
