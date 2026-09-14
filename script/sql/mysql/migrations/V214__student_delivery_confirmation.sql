SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS zsjos_student_delivery_plan (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  student_person_id bigint NOT NULL,
  account_id bigint NOT NULL,
  service_relation_id bigint NULL,
  director_user_id bigint NULL,
  account_opened_at datetime NOT NULL,
  config_version int NOT NULL DEFAULT 1,
  status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  version int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_delivery_plan_account (tenant_id, account_id, deleted),
  UNIQUE KEY uk_delivery_plan_active (tenant_id, account_id, deleted, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员账号交付周期计划';

CREATE TABLE IF NOT EXISTS zsjos_student_delivery_stage (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL, plan_id bigint NOT NULL, account_id bigint NOT NULL,
  stage_code varchar(8) NOT NULL, director_user_id bigint NULL,
  trigger_at datetime NOT NULL, due_at datetime NULL, completed_at datetime NULL, completed_by bigint NULL,
  status varchar(32) NOT NULL DEFAULT 'WAITING', defer_days int NULL, defer_reason varchar(1000) NULL,
  bpm_process_instance_id varchar(64) NULL, form_version_id bigint NULL, submission_json longtext NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_delivery_stage (tenant_id, plan_id, stage_code, deleted),
  KEY idx_delivery_stage_due (tenant_id, director_user_id, status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员账号交付阶段任务';

CREATE TABLE IF NOT EXISTS zsjos_student_delivery_config (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, version int NOT NULL DEFAULT 1,
  s0_days int NOT NULL DEFAULT 3, s1_days int NOT NULL DEFAULT 7, s2_days int NOT NULL DEFAULT 7,
  s3_days int NOT NULL DEFAULT 14, s4_days int NOT NULL DEFAULT 14, s5_days int NOT NULL DEFAULT 14,
  s6_days int NOT NULL DEFAULT 30, enabled bit(1) NOT NULL DEFAULT b'1',
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (id), UNIQUE KEY uk_delivery_config (tenant_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员账号交付周期配置';

CREATE TABLE IF NOT EXISTS zsjos_student_delivery_form (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, stage_code varchar(8) NOT NULL,
  version int NOT NULL, status varchar(16) NOT NULL DEFAULT 'DRAFT', fields_json longtext NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (id), UNIQUE KEY uk_delivery_form (tenant_id, stage_code, version, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员账号交付确认表单版本';

CREATE TABLE IF NOT EXISTS zsjos_student_delivery_submission (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, stage_id bigint NOT NULL,
  template_version_id bigint NULL, field_values_json longtext NOT NULL,
  dictionary_snapshot_json longtext NULL, attachment_snapshot_json longtext NULL,
  submitted_by bigint NOT NULL, submitted_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (id), UNIQUE KEY uk_delivery_submission_stage (tenant_id, stage_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员账号交付确认提交快照';

CREATE TABLE IF NOT EXISTS zsjos_student_delivery_defer (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, stage_id bigint NOT NULL,
  requested_by bigint NOT NULL, supervisor_user_id bigint NULL, requested_days int NOT NULL,
  original_due_at datetime NOT NULL, reason varchar(1000) NOT NULL,
  bpm_process_instance_id varchar(64) NULL, status varchar(32) NOT NULL DEFAULT 'PENDING',
  decided_at datetime NULL, decision_reason varchar(1000) NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (id),
  UNIQUE KEY uk_delivery_defer_pending (tenant_id, stage_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员账号交付延期审批';
