SET NAMES utf8mb4;
SET @db := DATABASE();
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@db AND table_name='zsjos_content_version' AND column_name='reference_work_url'),'SELECT 1','ALTER TABLE zsjos_content_version ADD COLUMN reference_work_url varchar(1024) DEFAULT NULL COMMENT ''参考作品链接'' AFTER reference_content_version_id'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
