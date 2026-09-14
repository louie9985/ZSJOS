-- 学员概览可先建立协作组级定位卡草稿；正式提交前必须绑定账号。
SET NAMES utf8mb4;
SET @schema_name = DATABASE();
SET @sql = IF((SELECT IS_NULLABLE FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='zsjos_positioning_card' AND column_name='account_id')='NO',
  'ALTER TABLE `zsjos_positioning_card` MODIFY COLUMN `account_id` bigint DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

