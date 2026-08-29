CREATE TABLE IF NOT EXISTS zsjos_forced_form (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL DEFAULT 0, name varchar(128) NOT NULL,
  description varchar(500) NULL, status varchar(20) NOT NULL DEFAULT 'DRAFT', fields_json text NOT NULL,
  version int NOT NULL DEFAULT 1, creator varchar(64) NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL, update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT 0, PRIMARY KEY(id), KEY idx_tenant_status(tenant_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS强制表单';
CREATE TABLE IF NOT EXISTS zsjos_forced_form_recipient (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL DEFAULT 0, form_id bigint NOT NULL, user_id bigint NOT NULL,
  source varchar(32) NOT NULL, status varchar(20) NOT NULL DEFAULT 'PENDING', completed_at datetime NULL, submission_id bigint NULL,
  creator varchar(64) NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) NULL, update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT 0, PRIMARY KEY(id), UNIQUE KEY uk_form_user(form_id,user_id), KEY idx_user_status(tenant_id,user_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS强制表单接收人';
CREATE TABLE IF NOT EXISTS zsjos_forced_form_submission (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL DEFAULT 0, form_id bigint NOT NULL, user_id bigint NOT NULL,
  fields_snapshot_json text NOT NULL, answers_json text NOT NULL, dict_snapshot_json text NULL, platform varchar(16) NULL,
  creator varchar(64) NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) NULL, update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT 0, PRIMARY KEY(id), UNIQUE KEY uk_form_user(form_id,user_id), KEY idx_form_time(tenant_id,form_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS强制表单提交';
