-- V225: content-review batch student context.
-- UTF-8. Adds two nullable columns to zsjos_content_review_batch and nothing else.
-- Repeatability: both ADD COLUMN statements are guarded through information_schema, so the
-- migration is a no-op when the columns already exist. This matters because the reviewed fresh
-- baseline already carries both columns while the baseline version list does not register V225;
-- an unguarded ALTER aborted `zsjos-db migrate` on an empty database with ERROR 1060.
-- Historical rows retain NULL: the batch predates student scoping and no trustworthy source
-- exists to reconstruct it.
-- Rollback limitation: dropping the columns would lose the recorded student context of new
-- batches, so rollback is forward-only.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v225_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v225_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='zsjos_content_review_batch'
        AND column_name='student_person_id') THEN
    ALTER TABLE `zsjos_content_review_batch`
      ADD COLUMN `student_person_id` bigint DEFAULT NULL COMMENT '学员主体 ID' AFTER `account_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='zsjos_content_review_batch'
        AND column_name='account_ids_json') THEN
    ALTER TABLE `zsjos_content_review_batch`
      ADD COLUMN `account_ids_json` json DEFAULT NULL COMMENT '本批次选择的账号 ID 集合快照'
      AFTER `student_person_id`;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v225_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v225_apply`;
