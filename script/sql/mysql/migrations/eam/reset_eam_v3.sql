-- EAM V3 local-development reset only.
-- Scope: EAM-owned rows and EAM category field metadata. It does not drop a database,
-- touch System users/departments/roles/menus/dictionaries, or touch other modules.
-- Execution order: run against the selected local database after a read-only row-count check.
-- Recovery: restore the EAM table backup; this operation is intentionally destructive.

SET @eam_tenant_id = 1;
SET @eam_tenant_exists = (SELECT COUNT(*) FROM system_tenant WHERE id = @eam_tenant_id AND deleted = b'0');
SET @eam_tenant_guard = IF(@eam_tenant_exists = 1, 'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''eam_tenant_id does not exist''');
PREPARE tenant_guard FROM @eam_tenant_guard; EXECUTE tenant_guard; DEALLOCATE PREPARE tenant_guard;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM eam_asset_import_row;
DELETE FROM eam_asset_import_batch;
DELETE FROM eam_asset_handover;
DELETE FROM eam_asset_verification;
DELETE FROM eam_asset_change_log;
DELETE FROM eam_repair;
DELETE FROM eam_scrap;
DELETE FROM eam_transfer;
DELETE FROM eam_inventory_detail;
DELETE FROM eam_inventory;
DELETE FROM eam_asset;
DELETE FROM eam_category_field;
DELETE FROM eam_category;
DELETE FROM eam_code_rule;

SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE eam_category_field ADD COLUMN option_source varchar(30) DEFAULT NULL COMMENT ''下拉选项来源''',
  'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'eam_category_field' AND column_name = 'option_source');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE eam_asset ADD COLUMN source_label_snapshot varchar(100) DEFAULT NULL COMMENT ''来源标签快照''',
  'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'eam_asset' AND column_name = 'source_label_snapshot');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE eam_asset ADD COLUMN ext_field_labels json DEFAULT NULL COMMENT ''自定义下拉字段标签快照''',
  'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'eam_asset' AND column_name = 'ext_field_labels');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE eam_asset ADD COLUMN ext_field_dict_types json DEFAULT NULL COMMENT ''自定义下拉字段字典类型快照''',
  'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'eam_asset' AND column_name = 'ext_field_dict_types');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE eam_category_field ADD COLUMN dict_type varchar(100) DEFAULT NULL COMMENT ''System字典类型''',
  'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'eam_category_field' AND column_name = 'dict_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,remark,creator,updater,tenant_id) VALUES
(0,'IT硬件设备','IT',1,0,1,'个','V3','eam-v3','eam-v3',1),
(0,'数字资产','DIGITAL',2,0,1,'项','V3','eam-v3','eam-v3',1),
(0,'办公家具及设备','FURNITURE',3,0,1,'件','V3','eam-v3','eam-v3',1),
(0,'办公用品和耗材','SUPPLIES',4,0,2,'个','V3','eam-v3','eam-v3',1),
(0,'专业书籍','BOOK',5,0,2,'册','V3','eam-v3','eam-v3',1),
(0,'教学用具','TEACHING',6,0,2,'件','V3','eam-v3','eam-v3',1),
(0,'其他','OTHER',7,0,1,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'电脑','IT-COMPUTER',1,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'移动终端','IT-MOBILE',2,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'显示器','IT-DISPLAY',3,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'打印/投影设备','IT-PRINT-PROJECT',4,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'网络及通讯设备','IT-NETWORK',5,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'音视频设备','IT-AUDIO-VIDEO',6,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'外设耗材','IT-PERIPHERAL',7,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='IT'),'其他IT硬件设备','IT-OTHER',8,0,1,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='DIGITAL'),'平台账号','DIGITAL-PLATFORM',1,0,1,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='DIGITAL'),'订阅服务','DIGITAL-SUBSCRIPTION',2,0,1,'项','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='DIGITAL'),'企业手机号','DIGITAL-MOBILE',3,0,1,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='DIGITAL'),'其他数字资产','DIGITAL-OTHER',4,0,1,'项','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='FURNITURE'),'办公桌椅','FURNITURE-DESK-CHAIR',1,0,1,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='FURNITURE'),'会客家具','FURNITURE-RECEPTION',2,0,1,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='FURNITURE'),'文件柜','FURNITURE-CABINET',3,0,1,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='FURNITURE'),'饮水机','FURNITURE-WATER',4,0,1,'台','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='FURNITURE'),'其他办公家具及设备','FURNITURE-OTHER',5,0,1,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'文具','SUPPLIES-STATIONERY',1,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'胶粘用品','SUPPLIES-ADHESIVE',2,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'电池','SUPPLIES-BATTERY',3,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'档案及证书物料','SUPPLIES-ARCHIVE',4,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'印刷物料','SUPPLIES-PRINT',5,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'日杂用品','SUPPLIES-DAILY',6,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='SUPPLIES'),'其他办公用品和耗材','SUPPLIES-OTHER',7,0,2,'个','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='TEACHING'),'厨房秤','TEACHING-KITCHEN-SCALE',1,0,2,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='TEACHING'),'沙盘','TEACHING-SANDBOX',2,0,2,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='TEACHING'),'膳食宝塔','TEACHING-PAGODA',3,0,2,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='TEACHING'),'膳食餐盘','TEACHING-PLATE',4,0,2,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='TEACHING'),'食品真相揭秘箱','TEACHING-FOOD-BOX',5,0,2,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='TEACHING'),'其他教学用具','TEACHING-OTHER',6,0,2,'件','V3','eam-v3','eam-v3',1),
((SELECT id FROM eam_category WHERE code='OTHER'),'其他资产','OTHER-ASSET',1,0,1,'个','V3','eam-v3','eam-v3',1);

INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,option_source,dict_type,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
SELECT id,'device_model','机型',5,'SYSTEM_DICT','eam_device_model',b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'cpu','CPU',1,NULL,NULL,b'0',b'1',b'1',b'0',20,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'memory','内存',1,NULL,NULL,b'0',b'1',b'1',b'0',30,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'disk','硬盘',1,NULL,NULL,b'0',b'1',b'1',b'0',40,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'power_adapter','电源适配器',5,'SYSTEM_DICT','eam_yes_no',b'0',b'1',b'1',b'0',50,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'cables','配套线材',1,NULL,NULL,b'0',b'1',b'1',b'0',60,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'original_box','原装包装盒',5,'SYSTEM_DICT','eam_yes_no',b'0',b'1',b'1',b'0',70,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-COMPUTER'
UNION ALL SELECT id,'device_type','设备类型',5,'SYSTEM_DICT','eam_mobile_device_type',b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-MOBILE'
UNION ALL SELECT id,'mobile_system','手机系统',5,'SYSTEM_DICT','eam_mobile_system',b'0',b'1',b'1',b'0',20,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-MOBILE'
UNION ALL SELECT id,'storage_capacity','存储容量',1,NULL,NULL,b'0',b'1',b'1',b'0',30,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-MOBILE'
UNION ALL SELECT id,'color','外观颜色',1,NULL,NULL,b'0',b'1',b'1',b'0',40,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-MOBILE'
UNION ALL SELECT id,'purpose','用途',5,'SYSTEM_DICT','eam_asset_purpose',b'0',b'1',b'1',b'0',50,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-MOBILE'
UNION ALL SELECT id,'display_size_resolution','尺寸/分辨率',1,NULL,NULL,b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-DISPLAY'
UNION ALL SELECT id,'device_type','设备类型',5,'SYSTEM_DICT','eam_print_project_type',b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-PRINT-PROJECT'
UNION ALL SELECT id,'device_type','设备类型',5,'SYSTEM_DICT','eam_network_device_type',b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-NETWORK'
UNION ALL SELECT id,'device_type','设备类型',5,'SYSTEM_DICT','eam_audio_video_type',b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='IT-AUDIO-VIDEO'
UNION ALL SELECT id,'specification','规格/型号',1,NULL,NULL,b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('IT-PERIPHERAL','SUPPLIES-STATIONERY','SUPPLIES-ADHESIVE','SUPPLIES-BATTERY','SUPPLIES-ARCHIVE','SUPPLIES-PRINT','SUPPLIES-DAILY','SUPPLIES-OTHER')
UNION ALL SELECT id,'login_id','登录账号/ID',1,NULL,NULL,b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-PLATFORM','DIGITAL-SUBSCRIPTION','DIGITAL-MOBILE','DIGITAL-OTHER')
UNION ALL SELECT id,'account_name','账号名称',1,NULL,NULL,b'0',b'1',b'1',b'0',20,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-PLATFORM','DIGITAL-SUBSCRIPTION','DIGITAL-MOBILE','DIGITAL-OTHER')
UNION ALL SELECT id,'bound_mobile','绑定手机号',1,NULL,NULL,b'0',b'1',b'1',b'0',30,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-PLATFORM','DIGITAL-SUBSCRIPTION','DIGITAL-MOBILE','DIGITAL-OTHER')
UNION ALL SELECT id,'password_custody','密码保管方式',5,'SYSTEM_DICT','eam_password_custody',b'0',b'1',b'1',b'0',40,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-PLATFORM','DIGITAL-SUBSCRIPTION','DIGITAL-MOBILE','DIGITAL-OTHER')
UNION ALL SELECT id,'platform','所属平台',5,'SYSTEM_DICT','eam_digital_platform',b'0',b'1',b'1',b'0',50,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-PLATFORM'
UNION ALL SELECT id,'account_nature','账号性质',5,'SYSTEM_DICT','eam_account_nature',b'0',b'1',b'1',b'0',60,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-PLATFORM'
UNION ALL SELECT id,'real_name_person','实名认证人',1,NULL,NULL,b'0',b'1',b'1',b'0',70,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-PLATFORM','DIGITAL-MOBILE')
UNION ALL SELECT id,'bound_wechat_qq','绑定微信/QQ',1,NULL,NULL,b'0',b'1',b'1',b'0',80,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-PLATFORM'
UNION ALL SELECT id,'contact_count','联系人数',3,NULL,NULL,b'0',b'1',b'1',b'0',90,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-PLATFORM'
UNION ALL SELECT id,'account_screenshot','账号截图',6,NULL,NULL,b'0',b'1',b'1',b'0',100,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-PLATFORM'
UNION ALL SELECT id,'service_type','服务类型',5,'SYSTEM_DICT','eam_service_type',b'0',b'1',b'1',b'0',50,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-SUBSCRIPTION'
UNION ALL SELECT id,'service_name','服务名称',1,NULL,NULL,b'0',b'1',b'1',b'0',60,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-SUBSCRIPTION'
UNION ALL SELECT id,'membership_level','会员/套餐等级',1,NULL,NULL,b'0',b'1',b'1',b'0',70,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-SUBSCRIPTION'
UNION ALL SELECT id,'package_expiry','套餐到期日',4,NULL,NULL,b'0',b'1',b'1',b'0',80,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-SUBSCRIPTION','DIGITAL-MOBILE')
UNION ALL SELECT id,'auto_renewal','是否自动续费',5,'SYSTEM_DICT','eam_yes_no',b'0',b'1',b'1',b'0',90,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-SUBSCRIPTION'
UNION ALL SELECT id,'payment_method','付费方式',1,NULL,NULL,b'0',b'1',b'1',b'0',100,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('DIGITAL-SUBSCRIPTION','DIGITAL-MOBILE')
UNION ALL SELECT id,'account_balance','账户当前余额',3,NULL,NULL,b'0',b'1',b'1',b'0',110,'eam-v3','eam-v3',1 FROM eam_category WHERE code='DIGITAL-SUBSCRIPTION'
UNION ALL SELECT id,'material','材质',1,NULL,NULL,b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('FURNITURE-DESK-CHAIR','FURNITURE-RECEPTION','FURNITURE-CABINET')
UNION ALL SELECT id,'furniture_type','类型',5,'SYSTEM_DICT','eam_furniture_type',b'0',b'1',b'1',b'0',20,'eam-v3','eam-v3',1 FROM eam_category WHERE code IN ('FURNITURE-DESK-CHAIR','FURNITURE-RECEPTION')
UNION ALL SELECT id,'subject','所属科目',5,'SYSTEM_DICT','eam_book_subject',b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='BOOK'
UNION ALL SELECT id,'publisher','出版社',1,NULL,NULL,b'0',b'1',b'1',b'0',20,'eam-v3','eam-v3',1 FROM eam_category WHERE code='BOOK'
UNION ALL SELECT id,'author','作者',1,NULL,NULL,b'0',b'1',b'1',b'0',30,'eam-v3','eam-v3',1 FROM eam_category WHERE code='BOOK'
UNION ALL SELECT id,'isbn','ISBN',1,NULL,NULL,b'0',b'1',b'1',b'0',40,'eam-v3','eam-v3',1 FROM eam_category WHERE code='BOOK'
UNION ALL SELECT id,'edition_year','版次/年份',1,NULL,NULL,b'0',b'1',b'1',b'0',50,'eam-v3','eam-v3',1 FROM eam_category WHERE code='BOOK'
UNION ALL SELECT id,'price','定价（元）',3,NULL,NULL,b'0',b'1',b'1',b'0',60,'eam-v3','eam-v3',1 FROM eam_category WHERE code='BOOK'
UNION ALL SELECT id,'size','规格尺寸',1,NULL,NULL,b'0',b'1',b'1',b'0',10,'eam-v3','eam-v3',1 FROM eam_category WHERE code='TEACHING'
UNION ALL SELECT id,'material','材质',1,NULL,NULL,b'0',b'1',b'1',b'0',20,'eam-v3','eam-v3',1 FROM eam_category WHERE code='TEACHING';

INSERT INTO eam_code_rule(category_id,prefix,use_category_code,date_format,serial_length,separator,current_serial,creator,updater,tenant_id)
VALUES (NULL,'EAM',b'1','yyyyMMdd',4,'-',0,'eam-v3','eam-v3',@eam_tenant_id);

SET FOREIGN_KEY_CHECKS = 1;
