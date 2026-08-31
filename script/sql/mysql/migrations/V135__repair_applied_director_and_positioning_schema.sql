-- Repairs schema drift caused by earlier applied revisions of V133 and V134.
-- Dependencies: V133 and V134. Execute only after both version markers exist.
-- Scope: adds two draft-version columns and six positioning-submission snapshot columns; backfills only
-- V133-owned compatibility submissions from their authoritative positioning-card source rows.
-- Repeatability: every ADD COLUMN is guarded, snapshot repair fills only NULL values, and markers are idempotent.
-- Rollback limitation: dropping the added columns would discard versions and immutable snapshot sections written later.

SET @v135_schema = DATABASE();

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_service_relation' AND column_name='director_precheck_draft_version')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_precheck_draft_version` int NOT NULL DEFAULT 0 COMMENT ''编导资料预审草稿版本'' AFTER `director_precheck_draft_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_service_relation' AND column_name='director_interview_draft_version')=0,
  'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_interview_draft_version` int NOT NULL DEFAULT 0 COMMENT ''编导学员采访草稿版本'' AFTER `director_interview_draft_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_positioning_card_submission' AND column_name='layer1_json')=0,
  'ALTER TABLE `zsjos_positioning_card_submission` ADD COLUMN `layer1_json` longtext DEFAULT NULL AFTER `dict_snapshot_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_positioning_card_submission' AND column_name='layer2_json')=0,
  'ALTER TABLE `zsjos_positioning_card_submission` ADD COLUMN `layer2_json` longtext DEFAULT NULL AFTER `layer1_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_positioning_card_submission' AND column_name='formula_json')=0,
  'ALTER TABLE `zsjos_positioning_card_submission` ADD COLUMN `formula_json` longtext DEFAULT NULL AFTER `layer2_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_positioning_card_submission' AND column_name='feasibility_json')=0,
  'ALTER TABLE `zsjos_positioning_card_submission` ADD COLUMN `feasibility_json` longtext DEFAULT NULL AFTER `formula_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_positioning_card_submission' AND column_name='content_form_json')=0,
  'ALTER TABLE `zsjos_positioning_card_submission` ADD COLUMN `content_form_json` longtext DEFAULT NULL AFTER `feasibility_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

SET @v135_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v135_schema
  AND table_name='zsjos_positioning_card_submission' AND column_name='compliance_json')=0,
  'ALTER TABLE `zsjos_positioning_card_submission` ADD COLUMN `compliance_json` longtext DEFAULT NULL AFTER `content_form_json`',
  'SELECT 1');
PREPARE v135_stmt FROM @v135_sql; EXECUTE v135_stmt; DEALLOCATE PREPARE v135_stmt;

UPDATE `zsjos_positioning_card_submission` submission
JOIN `zsjos_positioning_card` card
  ON card.id=submission.card_id AND card.tenant_id=submission.tenant_id AND card.deleted=b'0'
SET submission.layer1_json=COALESCE(submission.layer1_json,CAST(card.layer1_json AS CHAR)),
    submission.layer2_json=COALESCE(submission.layer2_json,CAST(card.layer2_json AS CHAR)),
    submission.formula_json=COALESCE(submission.formula_json,CAST(card.formula_json AS CHAR)),
    submission.feasibility_json=COALESCE(submission.feasibility_json,CAST(card.feasibility_json AS CHAR)),
    submission.content_form_json=COALESCE(submission.content_form_json,CAST(card.content_form_json AS CHAR)),
    submission.compliance_json=COALESCE(submission.compliance_json,CAST(card.compliance_json AS CHAR)),
    submission.updater='V135',submission.update_time=NOW()
WHERE submission.creator='V133' AND submission.deleted=b'0'
  AND (submission.layer1_json IS NULL OR submission.layer2_json IS NULL OR submission.formula_json IS NULL
    OR submission.feasibility_json IS NULL OR submission.content_form_json IS NULL OR submission.compliance_json IS NULL);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V135','repair applied director and positioning schema',
       SHA2('V135__repair_applied_director_and_positioning_schema.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V135');

INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V135','repair applied director and positioning schema',
       SHA2('V135__repair_applied_director_and_positioning_schema.sql',256),'baseline',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                  WHERE `module_code`='core' AND `version`='V135');
