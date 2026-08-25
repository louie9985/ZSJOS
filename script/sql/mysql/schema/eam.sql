-- EAM (Enterprise Asset Management) desired schema.
-- This file is the authoritative structure reference for the `eam` optional module.
-- Fresh installs are performed through migrations/eam/V001__eam_schema.sql; this file
-- documents the same structure for schema-drift comparison and review.
--
-- Ownership: EAM module. Depends on Core only for system_users / system_dept references,
-- which are held as plain identifiers, not foreign keys, matching the repository's
-- cross-module convention.

CREATE TABLE IF NOT EXISTS `eam_category` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `parent_id`   bigint       NOT NULL DEFAULT 0       COMMENT '父分类编号，根为 0',
  `name`        varchar(100) NOT NULL                 COMMENT '分类名称',
  `code`        varchar(50)  NOT NULL                 COMMENT '分类编码，用于资产编号前缀',
  `sort`        int          NOT NULL DEFAULT 0       COMMENT '排序',
  `status`      tinyint      NOT NULL DEFAULT 0       COMMENT '状态：0 开启 1 关闭',
  `management_mode` tinyint  NOT NULL DEFAULT 1       COMMENT '管理模式：1 单件 2 批量',
  `unit`        varchar(20)  NOT NULL DEFAULT '个'    COMMENT '默认计量单位',
  `remark`      varchar(500)          DEFAULT NULL    COMMENT '备注',
  `creator`     varchar(64)           DEFAULT ''      COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)           DEFAULT ''      COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_category_parent` (`tenant_id`, `parent_id`),
  KEY `idx_eam_category_code` (`tenant_id`, `code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 资产分类';

CREATE TABLE IF NOT EXISTS `eam_category_field` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '字段编号',
  `category_id` bigint       NOT NULL                 COMMENT '所属分类编号',
  `field_key`   varchar(50)  NOT NULL                 COMMENT '字段标识，同分类内唯一',
  `field_name`  varchar(100) NOT NULL                 COMMENT '字段显示名',
  `field_type`  tinyint      NOT NULL                 COMMENT '字段类型：1 单行文本 2 多行文本 3 数字 4 日期 5 下拉选择 6 图片/文件',
  `options`     json                  DEFAULT NULL    COMMENT '下拉选项数组，仅字段类型为下拉选择时使用',
  `option_source` varchar(30) DEFAULT NULL            COMMENT '下拉选项来源：STATIC/SYSTEM_DICT',
  `dict_type` varchar(100) DEFAULT NULL               COMMENT 'System 字典类型编码',
  `required`    bit(1)       NOT NULL DEFAULT b'0'    COMMENT '是否必填',
  `admin_visible` bit(1)     NOT NULL DEFAULT b'1'    COMMENT '管理端是否显示',
  `collection_visible` bit(1) NOT NULL DEFAULT b'1'   COMMENT '员工收集表是否显示',
  `collection_required` bit(1) NOT NULL DEFAULT b'0'  COMMENT '员工收集表是否必填',
  `condition_rule` json               DEFAULT NULL    COMMENT '员工收集表条件规则',
  `sort`        int          NOT NULL DEFAULT 0       COMMENT '排序',
  `creator`     varchar(64)           DEFAULT ''      COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)           DEFAULT ''      COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_category_field_category` (`tenant_id`, `category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 分类自定义字段定义';

CREATE TABLE IF NOT EXISTS `eam_asset` (
  `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '资产主键',
  `asset_code`      varchar(64)   NOT NULL                 COMMENT '资产业务编号，按编号规则生成',
  `name`            varchar(200)  NOT NULL                 COMMENT '资产名称',
  `category_id`     bigint        NOT NULL                 COMMENT '分类编号',
  `management_mode` tinyint       NOT NULL DEFAULT 1       COMMENT '管理模式快照：1 单件 2 批量',
  `quantity`        int           NOT NULL DEFAULT 1       COMMENT '资产数量',
  `unit`            varchar(20)   NOT NULL DEFAULT '个'    COMMENT '计量单位快照',
  `status`          tinyint       NOT NULL DEFAULT 0       COMMENT '资产状态：0 闲置 1 在用 2 借出 3 维修中 4 待报废 5 已报废 6 已丢失 7 已冻结',
  `previous_status` tinyint                DEFAULT NULL    COMMENT '进入可逆中间态前的状态，用于维修完成/报废驳回/解冻恢复',
  `brand`           varchar(100)           DEFAULT NULL    COMMENT '品牌型号',
  `specification`   varchar(255)           DEFAULT NULL    COMMENT '规格参数',
  `sn`              varchar(100)           DEFAULT NULL    COMMENT '序列号',
  `barcode`         varchar(128)           DEFAULT NULL    COMMENT '条码',
  `original_value`  decimal(12, 2)         DEFAULT NULL    COMMENT '原值',
  `net_value`       decimal(12, 2)         DEFAULT NULL    COMMENT '净值，手工维护',
  `purchase_date`   date                   DEFAULT NULL    COMMENT '购入日期',
  `source`          tinyint                DEFAULT NULL    COMMENT '来源，字典 eam_asset_source',
  `source_label_snapshot` varchar(100)      DEFAULT NULL    COMMENT '来源标签快照',
  `warranty_date`   date                   DEFAULT NULL    COMMENT '保修到期日',
  `use_dept_id`     bigint                 DEFAULT NULL    COMMENT '使用部门编号，引用 system_dept',
  `use_user_id`     bigint                 DEFAULT NULL    COMMENT '使用人编号，引用 system_users',
  `use_user_name_snapshot` varchar(100)       DEFAULT NULL    COMMENT '使用人姓名快照',
  `supervisor_user_id` bigint                 DEFAULT NULL    COMMENT '直属上级用户编号',
  `supervisor_name_snapshot` varchar(100)     DEFAULT NULL    COMMENT '直属上级姓名快照',
  `join_date`       date                      DEFAULT NULL    COMMENT '使用人入司日期',
  `commitment_accepted` bit(1)                DEFAULT NULL    COMMENT '使用人承诺是否确认',
  `commitment_date` date                      DEFAULT NULL    COMMENT '承诺日期',
  `location`        varchar(255)           DEFAULT NULL    COMMENT '存放地点',
  `expected_life`   int                    DEFAULT NULL    COMMENT '预计使用年限，单位月',
  `remark`          varchar(500)           DEFAULT NULL    COMMENT '备注',
  `file_urls`       json                   DEFAULT NULL    COMMENT '附件地址数组',
  `ext_fields`      json                   DEFAULT NULL    COMMENT '分类自定义字段值',
  `ext_field_labels` json                  DEFAULT NULL    COMMENT '自定义下拉字段标签快照',
  `ext_field_dict_types` json              DEFAULT NULL    COMMENT '自定义下拉字段字典类型快照',
  `creator`         varchar(64)            DEFAULT ''      COMMENT '创建者',
  `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)            DEFAULT ''      COMMENT '更新者',
  `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)        NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`       bigint        NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_eam_asset_code` (`tenant_id`, `asset_code`, `deleted`),
  KEY `idx_eam_asset_category` (`tenant_id`, `category_id`),
  KEY `idx_eam_asset_status` (`tenant_id`, `status`),
  KEY `idx_eam_asset_use_user` (`tenant_id`, `use_user_id`),
  KEY `idx_eam_asset_use_dept` (`tenant_id`, `use_dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 资产卡片';

CREATE TABLE IF NOT EXISTS `eam_asset_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT, `file_hash` char(64) NOT NULL, `file_name` varchar(255) NOT NULL,
  `sheet_name` varchar(100) NOT NULL, `total_rows` int NOT NULL DEFAULT 0, `create_count` int NOT NULL DEFAULT 0,
  `update_count` int NOT NULL DEFAULT 0, `skip_count` int NOT NULL DEFAULT 0, `warning_count` int NOT NULL DEFAULT 0,
  `operator_id` bigint DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_eam_asset_import_batch_hash` (`tenant_id`,`file_hash`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产导入批次';

CREATE TABLE IF NOT EXISTS `eam_asset_import_row` (
  `id` bigint NOT NULL AUTO_INCREMENT, `batch_id` bigint NOT NULL, `file_hash` char(64) NOT NULL,
  `sheet_name` varchar(100) NOT NULL, `row_num` int NOT NULL, `asset_id` bigint NOT NULL,
  `asset_code` varchar(64) NOT NULL, `import_action` tinyint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_eam_asset_import_source` (`tenant_id`,`file_hash`,`sheet_name`,`row_num`,`deleted`),
  KEY `idx_eam_asset_import_row_batch` (`tenant_id`,`batch_id`), KEY `idx_eam_asset_import_row_asset` (`tenant_id`,`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产导入行来源';

CREATE TABLE IF NOT EXISTS `eam_asset_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT, `asset_id` bigint NOT NULL, `result` varchar(100) DEFAULT NULL,
  `label_status` varchar(20) DEFAULT NULL, `verifier_user_id` bigint DEFAULT NULL,
  `verifier_name_snapshot` varchar(100) DEFAULT NULL, `verified_at` datetime DEFAULT NULL, `remark` varchar(500) DEFAULT NULL,
  `import_batch_id` bigint DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0, PRIMARY KEY (`id`),
  KEY `idx_eam_asset_verification_asset` (`tenant_id`,`asset_id`,`verified_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产行政核对历史';

CREATE TABLE IF NOT EXISTS `eam_asset_handover` (
  `id` bigint NOT NULL AUTO_INCREMENT, `asset_id` bigint NOT NULL, `content` varchar(500) DEFAULT NULL,
  `from_user_id` bigint DEFAULT NULL, `to_user_id` bigint DEFAULT NULL, `handover_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL, `import_batch_id` bigint DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0, PRIMARY KEY (`id`), KEY `idx_eam_asset_handover_asset` (`tenant_id`,`asset_id`,`handover_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产交接历史';

CREATE TABLE IF NOT EXISTS `eam_asset_change_log` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '记录编号',
  `asset_id`       bigint       NOT NULL                 COMMENT '资产编号',
  `change_type`    tinyint      NOT NULL                 COMMENT '变更类型，见 EamChangeTypeEnum',
  `before_status`  tinyint               DEFAULT NULL    COMMENT '变更前状态',
  `after_status`   tinyint               DEFAULT NULL    COMMENT '变更后状态',
  `before_user_id` bigint                DEFAULT NULL    COMMENT '变更前使用人',
  `after_user_id`  bigint                DEFAULT NULL    COMMENT '变更后使用人',
  `before_dept_id` bigint                DEFAULT NULL    COMMENT '变更前使用部门',
  `after_dept_id`  bigint                DEFAULT NULL    COMMENT '变更后使用部门',
  `biz_id`         bigint                DEFAULT NULL    COMMENT '关联单据编号',
  `content`        varchar(500)          DEFAULT NULL    COMMENT '变更描述',
  `operator_id`    bigint                DEFAULT NULL    COMMENT '操作人编号',
  `operate_time`   datetime     NOT NULL                 COMMENT '操作时间',
  `creator`        varchar(64)           DEFAULT ''      COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)           DEFAULT ''      COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_change_log_asset` (`tenant_id`, `asset_id`, `operate_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 资产变更记录';

CREATE TABLE IF NOT EXISTS `eam_transfer` (
  `id`                   bigint      NOT NULL AUTO_INCREMENT COMMENT '单据编号',
  `no`                   varchar(64) NOT NULL                 COMMENT '单据业务编号',
  `type`                 tinyint     NOT NULL                 COMMENT '流转类型：1 领用 2 退还 3 借用 4 归还 5 调拨',
  `asset_id`             bigint      NOT NULL                 COMMENT '资产编号',
  `from_user_id`         bigint               DEFAULT NULL    COMMENT '转出使用人',
  `from_dept_id`         bigint               DEFAULT NULL    COMMENT '转出部门',
  `to_user_id`           bigint               DEFAULT NULL    COMMENT '接收使用人',
  `to_dept_id`           bigint               DEFAULT NULL    COMMENT '接收部门',
  `expected_return_date` date                 DEFAULT NULL    COMMENT '预计归还日期，仅借用',
  `actual_return_date`   date                 DEFAULT NULL    COMMENT '实际归还日期，仅归还',
  `status`               tinyint     NOT NULL DEFAULT 0       COMMENT '单据状态：0 审批中 1 已生效 2 已驳回 3 已取消',
  `process_instance_id`  varchar(64)          DEFAULT NULL    COMMENT 'BPM 流程实例编号',
  `reason`               varchar(500)         DEFAULT NULL    COMMENT '事由',
  `apply_user_id`        bigint               DEFAULT NULL    COMMENT '申请人编号',
  `apply_time`           datetime             DEFAULT NULL    COMMENT '申请时间',
  `creator`              varchar(64)          DEFAULT ''      COMMENT '创建者',
  `create_time`          datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`              varchar(64)          DEFAULT ''      COMMENT '更新者',
  `update_time`          datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bit(1)      NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`            bigint      NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_transfer_asset` (`tenant_id`, `asset_id`),
  KEY `idx_eam_transfer_status` (`tenant_id`, `status`),
  KEY `idx_eam_transfer_process` (`process_instance_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 资产流转单';

CREATE TABLE IF NOT EXISTS `eam_inventory` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '盘点单编号',
  `no`             varchar(64)  NOT NULL                 COMMENT '盘点单号',
  `name`           varchar(200) NOT NULL                 COMMENT '盘点名称',
  `scope_type`     tinyint      NOT NULL                 COMMENT '范围类型：1 全部 2 按部门 3 按分类 4 按存放地点',
  `scope_value`    varchar(500)          DEFAULT NULL    COMMENT '范围值',
  `status`         tinyint      NOT NULL DEFAULT 0       COMMENT '盘点状态：0 进行中 1 已完成',
  `total_count`    int          NOT NULL DEFAULT 0       COMMENT '应盘数量',
  `checked_count`  int          NOT NULL DEFAULT 0       COMMENT '已盘数量',
  `normal_count`   int          NOT NULL DEFAULT 0       COMMENT '正常数量',
  `abnormal_count` int          NOT NULL DEFAULT 0       COMMENT '异常数量',
  `start_time`     datetime              DEFAULT NULL    COMMENT '开始时间',
  `end_time`       datetime              DEFAULT NULL    COMMENT '结束时间',
  `remark`         varchar(500)          DEFAULT NULL    COMMENT '备注',
  `creator`        varchar(64)           DEFAULT ''      COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)           DEFAULT ''      COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_inventory_status` (`tenant_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 盘点单';

CREATE TABLE IF NOT EXISTS `eam_inventory_detail` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '明细编号',
  `inventory_id`    bigint       NOT NULL                 COMMENT '盘点单编号',
  `asset_id`        bigint       NOT NULL                 COMMENT '资产编号',
  `expect_user_id`  bigint                DEFAULT NULL    COMMENT '账面使用人',
  `expect_dept_id`  bigint                DEFAULT NULL    COMMENT '账面使用部门',
  `expect_location` varchar(255)          DEFAULT NULL    COMMENT '账面存放地点',
  `actual_user_id`  bigint                DEFAULT NULL    COMMENT '实盘使用人',
  `actual_dept_id`  bigint                DEFAULT NULL    COMMENT '实盘使用部门',
  `actual_location` varchar(255)          DEFAULT NULL    COMMENT '实盘存放地点',
  `result`          tinyint      NOT NULL DEFAULT 0       COMMENT '盘点结果：0 未盘 1 正常 2 位置不符 3 未找到',
  `remark`          varchar(500)          DEFAULT NULL    COMMENT '备注',
  `check_user_id`   bigint                DEFAULT NULL    COMMENT '盘点人',
  `check_time`      datetime              DEFAULT NULL    COMMENT '盘点时间',
  `creator`         varchar(64)           DEFAULT ''      COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)           DEFAULT ''      COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_inventory_detail_inventory` (`tenant_id`, `inventory_id`, `result`),
  KEY `idx_eam_inventory_detail_asset` (`tenant_id`, `asset_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 盘点明细';

CREATE TABLE IF NOT EXISTS `eam_repair` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '维修记录编号',
  `asset_id`      bigint        NOT NULL                 COMMENT '资产编号',
  `fault_desc`    varchar(500)  NOT NULL                 COMMENT '故障描述',
  `repair_vendor` varchar(200)           DEFAULT NULL    COMMENT '维修方',
  `cost`          decimal(10, 2)         DEFAULT NULL    COMMENT '维修费用',
  `start_time`    datetime      NOT NULL                 COMMENT '送修时间',
  `end_time`      datetime               DEFAULT NULL    COMMENT '完成时间，空表示维修中',
  `result`        varchar(500)           DEFAULT NULL    COMMENT '维修结果',
  `creator`       varchar(64)            DEFAULT ''      COMMENT '创建者',
  `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)            DEFAULT ''      COMMENT '更新者',
  `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)        NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`     bigint        NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_repair_asset` (`tenant_id`, `asset_id`, `start_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 维修记录';

CREATE TABLE IF NOT EXISTS `eam_scrap` (
  `id`                  bigint      NOT NULL AUTO_INCREMENT COMMENT '单据编号',
  `no`                  varchar(64) NOT NULL                 COMMENT '单据业务编号',
  `asset_id`            bigint      NOT NULL                 COMMENT '资产编号',
  `reason_type`         tinyint     NOT NULL                 COMMENT '报废原因类型，字典 eam_scrap_reason',
  `reason`              varchar(500)         DEFAULT NULL    COMMENT '详细原因',
  `scrap_date`          date                 DEFAULT NULL    COMMENT '报废日期',
  `status`              tinyint     NOT NULL DEFAULT 0       COMMENT '状态：0 审批中 1 已报废 2 已驳回',
  `process_instance_id` varchar(64)          DEFAULT NULL    COMMENT 'BPM 流程实例编号',
  `apply_user_id`       bigint               DEFAULT NULL    COMMENT '申请人编号',
  `apply_time`          datetime             DEFAULT NULL    COMMENT '申请时间',
  `creator`             varchar(64)          DEFAULT ''      COMMENT '创建者',
  `create_time`         datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`             varchar(64)          DEFAULT ''      COMMENT '更新者',
  `update_time`         datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bit(1)      NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`           bigint      NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_scrap_asset` (`tenant_id`, `asset_id`),
  KEY `idx_eam_scrap_status` (`tenant_id`, `status`),
  KEY `idx_eam_scrap_process` (`process_instance_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 报废单';

CREATE TABLE IF NOT EXISTS `eam_code_rule` (
  `id`                bigint      NOT NULL AUTO_INCREMENT COMMENT '规则编号',
  `category_id`       bigint               DEFAULT NULL    COMMENT '适用分类编号，NULL 表示全局默认规则',
  `prefix`            varchar(20)          DEFAULT NULL    COMMENT '固定前缀',
  `use_category_code` bit(1)      NOT NULL DEFAULT b'0'    COMMENT '是否拼接分类编码',
  `date_format`       varchar(20)          DEFAULT NULL    COMMENT '日期格式，空则不含日期',
  `serial_length`     int         NOT NULL DEFAULT 4       COMMENT '流水号位数',
  `separator`         varchar(5)           DEFAULT '-'     COMMENT '分隔符',
  `current_serial`    bigint      NOT NULL DEFAULT 0       COMMENT '当前流水号',
  `creator`           varchar(64)          DEFAULT ''      COMMENT '创建者',
  `create_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`           varchar(64)          DEFAULT ''      COMMENT '更新者',
  `update_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           bit(1)      NOT NULL DEFAULT b'0'    COMMENT '是否删除',
  `tenant_id`         bigint      NOT NULL DEFAULT 0       COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_eam_code_rule_category` (`tenant_id`, `category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EAM 资产编号规则';
