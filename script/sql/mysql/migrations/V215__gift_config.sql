-- Depends on V210. Creates tenant-scoped gift configuration nodes. Repeatable with CREATE TABLE IF NOT EXISTS; no business rows are seeded.\nSET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS zsjos_gift_config (id bigint NOT NULL AUTO_INCREMENT,parent_id bigint NOT NULL DEFAULT 0,name varchar(128) NOT NULL,code varchar(128) NOT NULL,status tinyint NOT NULL DEFAULT 1,sort int NOT NULL DEFAULT 0,tenant_id bigint NOT NULL DEFAULT 0,creator varchar(64),create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,updater varchar(64),update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,deleted bit NOT NULL DEFAULT 0,PRIMARY KEY(id),UNIQUE KEY uk_tenant_code(tenant_id,code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



