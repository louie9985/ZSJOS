-- V006: baseline categories and non-credential custom fields.
-- Depends on V005. Inserts only missing rows; repeatable and non-destructive.
-- The complete Excel-derived leaf list is delivered by eam-category-config-template.xlsx
-- and can be imported through /eam/category/import/preview then /commit.
DROP PROCEDURE IF EXISTS `eam_v006_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v006_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  SELECT GET_LOCK('eam:migration:V006', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V006 lock unavailable'; END IF;
  START TRANSACTION;
  INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,creator,updater,tenant_id)
  SELECT 0,'IT硬件设备','IT',1,0,1,'个','migration-eam-V006','migration-eam-V006',1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM eam_category WHERE parent_id=0 AND code='IT' AND deleted=b'0');
  INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,creator,updater,tenant_id)
  SELECT 0,'数字资产','DIGITAL',2,0,2,'项','migration-eam-V006','migration-eam-V006',1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM eam_category WHERE parent_id=0 AND code='DIGITAL' AND deleted=b'0');
  INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,creator,updater,tenant_id)
  SELECT 0,'办公家具','FURNITURE',3,0,1,'件','migration-eam-V006','migration-eam-V006',1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM eam_category WHERE parent_id=0 AND code='FURNITURE' AND deleted=b'0');
  INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,creator,updater,tenant_id)
  SELECT 0,'办公用品和耗材','SUPPLIES',4,0,2,'个','migration-eam-V006','migration-eam-V006',1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM eam_category WHERE parent_id=0 AND code='SUPPLIES' AND deleted=b'0');
  INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,creator,updater,tenant_id)
  SELECT 0,'专业书籍及教具','BOOKS',5,0,2,'本','migration-eam-V006','migration-eam-V006',1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM eam_category WHERE parent_id=0 AND code='BOOKS' AND deleted=b'0');
  INSERT INTO eam_category(parent_id,name,code,sort,status,management_mode,unit,creator,updater,tenant_id)
  SELECT 0,'其他','OTHER',6,0,1,'个','migration-eam-V006','migration-eam-V006',1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM eam_category WHERE parent_id=0 AND code='OTHER' AND deleted=b'0');

  INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,options,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
  SELECT c.id,'device_color','设备外观颜色',1,NULL,b'0',b'1',b'1',b'0',10,'migration-eam-V006','migration-eam-V006',1 FROM eam_category c WHERE c.code='IT' AND c.parent_id=0 AND c.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM eam_category_field f WHERE f.category_id=c.id AND f.field_key='device_color' AND f.deleted=b'0');
  INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,options,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
  SELECT c.id,'password_custody','密码保管方式',1,NULL,b'0',b'1',b'1',b'0',80,'migration-eam-V006','migration-eam-V006',1 FROM eam_category c WHERE c.code='DIGITAL' AND c.parent_id=0 AND c.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM eam_category_field f WHERE f.category_id=c.id AND f.field_key='password_custody' AND f.deleted=b'0');
  INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,options,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
  SELECT c.id,'material','材质',1,NULL,b'0',b'1',b'1',b'0',10,'migration-eam-V006','migration-eam-V006',1 FROM eam_category c WHERE c.code='FURNITURE' AND c.parent_id=0 AND c.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM eam_category_field f WHERE f.category_id=c.id AND f.field_key='material' AND f.deleted=b'0');
  INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,options,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
  SELECT c.id,'specification_detail','规格',1,NULL,b'0',b'1',b'1',b'0',10,'migration-eam-V006','migration-eam-V006',1 FROM eam_category c WHERE c.code='SUPPLIES' AND c.parent_id=0 AND c.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM eam_category_field f WHERE f.category_id=c.id AND f.field_key='specification_detail' AND f.deleted=b'0');
  INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,options,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
  SELECT c.id,'isbn','ISBN',1,NULL,b'0',b'1',b'1',b'0',10,'migration-eam-V006','migration-eam-V006',1 FROM eam_category c WHERE c.code='BOOKS' AND c.parent_id=0 AND c.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM eam_category_field f WHERE f.category_id=c.id AND f.field_key='isbn' AND f.deleted=b'0');
  INSERT INTO eam_category_field(category_id,field_key,field_name,field_type,options,required,admin_visible,collection_visible,collection_required,sort,creator,updater,tenant_id)
  SELECT c.id,'other_description','其他说明',2,NULL,b'0',b'1',b'1',b'0',10,'migration-eam-V006','migration-eam-V006',1 FROM eam_category c WHERE c.code='OTHER' AND c.parent_id=0 AND c.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM eam_category_field f WHERE f.category_id=c.id AND f.field_key='other_description' AND f.deleted=b'0');
  COMMIT;
  DO RELEASE_LOCK('eam:migration:V006');
END$$
DELIMITER ;
CALL `eam_v006_apply`();
DROP PROCEDURE IF EXISTS `eam_v006_apply`;
