-- V104: configure the confirmed new-media platform and content-class dictionaries.
-- Scope: system dictionary metadata and options only; no business rows are changed.
-- Platform values and content-class labels follow the new-media product document.
-- Repeatability: inserts are guarded by dictionary type/value; existing administrator data is preserved.
-- Recovery: disable or revise the entries through the System dictionary administrator; historical snapshots remain unchanged.
SET NAMES utf8mb4;

INSERT INTO system_dict_type
(`name`,`type`,`status`,`remark`,`creator`,`updater`,`deleted`)
SELECT '新媒体账号平台','zsjos_account_platform',0,'新媒体账号所属平台','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_type WHERE `type`='zsjos_account_platform' AND `deleted`=b'0'
);

INSERT INTO system_dict_type
(`name`,`type`,`status`,`remark`,`creator`,`updater`,`deleted`)
SELECT '新媒体内容分类','zsjos_content_class',0,'新媒体内容生产分类','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_type WHERE `type`='zsjos_content_class' AND `deleted`=b'0'
);

INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 1,'抖音','douyin','zsjos_account_platform',0,'primary','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_account_platform' AND `value`='douyin' AND `deleted`=b'0'
);
INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 2,'小红书','xiaohongshu','zsjos_account_platform',0,'danger','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_account_platform' AND `value`='xiaohongshu' AND `deleted`=b'0'
);
INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 3,'视频号','shipinhao','zsjos_account_platform',0,'success','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_account_platform' AND `value`='shipinhao' AND `deleted`=b'0'
);

INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 1,'首批','first_batch','zsjos_content_class',0,'primary','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_content_class' AND `value`='first_batch' AND `deleted`=b'0'
);
INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 2,'重点','priority','zsjos_content_class',0,'warning','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_content_class' AND `value`='priority' AND `deleted`=b'0'
);
INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 3,'异常','exception','zsjos_content_class',0,'danger','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_content_class' AND `value`='exception' AND `deleted`=b'0'
);
INSERT INTO system_dict_data
(`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`updater`,`deleted`)
SELECT 4,'日常','daily','zsjos_content_class',0,'info','','','system','system',b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data WHERE `dict_type`='zsjos_content_class' AND `value`='daily' AND `deleted`=b'0'
);

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V104','Configure new-media platform and content-class dictionaries','new-media-business-dictionary-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);

INSERT INTO zsjos_module_schema_version
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V104','Configure new-media platform and content-class dictionaries',
        SHA2('new-media-business-dictionary-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
