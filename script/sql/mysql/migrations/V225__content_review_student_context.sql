SET NAMES utf8mb4;
ALTER TABLE `zsjos_content_review_batch`
  ADD COLUMN `student_person_id` bigint DEFAULT NULL COMMENT '学员主体 ID' AFTER `account_id`,
  ADD COLUMN `account_ids_json` json DEFAULT NULL COMMENT '本批次选择的账号 ID 集合快照' AFTER `student_person_id`;
