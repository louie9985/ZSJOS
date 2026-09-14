-- Depends on V211. Creates idempotent tenant/order gift purchase projection and menu permissions. Repeatable: table/menu inserts are guarded; no existing business rows are deleted.\nSET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS zsjos_gift_purchase (id bigint NOT NULL AUTO_INCREMENT,tenant_id bigint NOT NULL DEFAULT 0,order_id bigint NOT NULL,order_no varchar(64) NOT NULL,student_name varchar(100) NOT NULL,student_mobile varchar(32),student_wechat_id varchar(64),gift_items_json json NOT NULL,shipping_address varchar(1000),generated_at datetime NOT NULL,creator varchar(64),create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,updater varchar(64),update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,deleted bit NOT NULL DEFAULT 0,PRIMARY KEY(id),UNIQUE KEY uk_tenant_order(tenant_id,order_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Gift menus depend on the existing ZSJOS root menu 6735. Re-runnable by permission key.
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8900,'礼品配置','zsjos:gift-config:query',2,26,6735,'gift','ep:present','zsjos/gift/index','ZsjosGift',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-config:query' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8901,'新增礼品','zsjos:gift-config:create',3,1,8900,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-config:create' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8902,'编辑礼品','zsjos:gift-config:update',3,2,8900,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-config:update' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8903,'删除礼品','zsjos:gift-config:delete',3,3,8900,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-config:delete' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8910,'礼品采购','zsjos:gift-purchase:query',2,27,6735,'gift-purchase','ep:shopping-cart','zsjos/gift-purchase/index','ZsjosGiftPurchase',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-purchase:query' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 8911,'礼品采购详情','zsjos:gift-purchase:detail',3,1,8910,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0' WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='zsjos:gift-purchase:detail' AND deleted=b'0');

