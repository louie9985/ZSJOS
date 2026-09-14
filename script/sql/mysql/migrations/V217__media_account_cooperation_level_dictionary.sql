SET NAMES utf8mb4;
INSERT INTO system_dict_type (name,type,status,remark,creator,create_time,updater,update_time,deleted)
SELECT '学员配合等级','zsjos_media_account_cooperation_level',0,'账号周期诊断学员配合等级；管理员可配置','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='zsjos_media_account_cooperation_level' AND deleted=b'0');
INSERT INTO system_dict_data (sort,label,value,dict_type,status,remark,creator,create_time,updater,update_time,deleted)
SELECT s.sort,s.label,s.value,'zsjos_media_account_cooperation_level',0,'账号周期诊断预置配合等级','1',NOW(),'1',NOW(),b'0'
FROM (SELECT 1 sort,'高' label,'high' value UNION ALL SELECT 2,'中','medium' UNION ALL SELECT 3,'低','low') s
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data d WHERE d.dict_type='zsjos_media_account_cooperation_level' AND d.value=s.value AND d.deleted=b'0');
INSERT INTO zsjos_schema_version (`version`,`description`,`checksum`) VALUES ('V217','Add media account cooperation level dictionary','media-account-cooperation-level-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);


