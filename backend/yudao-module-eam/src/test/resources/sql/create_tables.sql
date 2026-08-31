-- EAM 单元测试用建表语句（H2，MySQL 兼容模式）
-- 与 script/sql/mysql/schema/eam.sql 结构对应，去掉 MySQL 专有语法。

CREATE TABLE IF NOT EXISTS "eam_category" (
    "id"          bigint       NOT NULL AUTO_INCREMENT,
    "parent_id"   bigint       NOT NULL DEFAULT 0,
    "name"        varchar(100) NOT NULL,
    "code"        varchar(50)  NOT NULL,
    "sort"        int          NOT NULL DEFAULT 0,
    "status"      tinyint      NOT NULL DEFAULT 0,
    "management_mode" tinyint  NOT NULL DEFAULT 1,
    "delivery_mode" tinyint             DEFAULT NULL,
    "custody_mode"  tinyint             DEFAULT NULL,
    "unit"        varchar(20)  NOT NULL DEFAULT '个',
    "remark"      varchar(500)          DEFAULT NULL,
    "creator"     varchar(64)           DEFAULT '',
    "create_time" timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"     varchar(64)           DEFAULT '',
    "update_time" timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"     bit          NOT NULL DEFAULT FALSE,
    "tenant_id"   bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_category_field" (
    "id"          bigint       NOT NULL AUTO_INCREMENT,
    "category_id" bigint       NOT NULL,
    "field_key"   varchar(50)  NOT NULL,
    "field_name"  varchar(100) NOT NULL,
    "field_type"  tinyint      NOT NULL,
    "options"     varchar(2000)         DEFAULT NULL,
    "option_source" varchar(30) DEFAULT NULL,
    "dict_type" varchar(100) DEFAULT NULL,
    "required"    bit          NOT NULL DEFAULT FALSE,
    "admin_visible" bit        NOT NULL DEFAULT TRUE,
    "collection_visible" bit   NOT NULL DEFAULT TRUE,
    "collection_required" bit  NOT NULL DEFAULT FALSE,
    "condition_rule" varchar(2000) DEFAULT NULL,
    "sort"        int          NOT NULL DEFAULT 0,
    "creator"     varchar(64)           DEFAULT '',
    "create_time" timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"     varchar(64)           DEFAULT '',
    "update_time" timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"     bit          NOT NULL DEFAULT FALSE,
    "tenant_id"   bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_asset" (
    "id"              bigint        NOT NULL AUTO_INCREMENT,
    "asset_code"      varchar(64)   NOT NULL,
    "name"            varchar(200)  NOT NULL,
    "category_id"     bigint        NOT NULL,
    "management_mode" tinyint       NOT NULL DEFAULT 1,
    "quantity"        int           NOT NULL DEFAULT 1,
    "unit"            varchar(20)   NOT NULL DEFAULT '个',
    "status"          tinyint       NOT NULL DEFAULT 0,
    "previous_status" tinyint                DEFAULT NULL,
    "brand"           varchar(100)           DEFAULT NULL,
    "specification"   varchar(255)           DEFAULT NULL,
    "sn"              varchar(100)           DEFAULT NULL,
    "barcode"         varchar(128)           DEFAULT NULL,
    "original_value"  decimal(12, 2)         DEFAULT NULL,
    "net_value"       decimal(12, 2)         DEFAULT NULL,
    "purchase_date"   date                   DEFAULT NULL,
    "source"          tinyint                DEFAULT NULL,
    "source_label_snapshot" varchar(100)     DEFAULT NULL,
    "warranty_date"   date                   DEFAULT NULL,
    "use_dept_id"     bigint                 DEFAULT NULL,
    "use_employee_id" bigint                 DEFAULT NULL,
    "use_employee_name_snapshot" varchar(100) DEFAULT NULL,
    "supervisor_employee_id" bigint          DEFAULT NULL,
    "location"        varchar(255)           DEFAULT NULL,
    "expected_life"   int                    DEFAULT NULL,
    "remark"          varchar(500)           DEFAULT NULL,
    "file_urls"       varchar(2000)          DEFAULT NULL,
    "ext_fields"      varchar(4000)          DEFAULT NULL,
    "ext_field_labels" varchar(4000)         DEFAULT NULL,
    "ext_field_dict_types" varchar(4000)     DEFAULT NULL,
    "creator"         varchar(64)            DEFAULT '',
    "create_time"     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"         varchar(64)            DEFAULT '',
    "update_time"     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"         bit           NOT NULL DEFAULT FALSE,
    "tenant_id"       bigint        NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_asset_import_batch" (
    "id" bigint NOT NULL AUTO_INCREMENT,
    "file_hash" char(64) NOT NULL,
    "file_name" varchar(255) NOT NULL,
    "sheet_name" varchar(100) NOT NULL,
    "total_rows" int NOT NULL DEFAULT 0,
    "create_count" int NOT NULL DEFAULT 0,
    "update_count" int NOT NULL DEFAULT 0,
    "skip_count" int NOT NULL DEFAULT 0,
    "warning_count" int NOT NULL DEFAULT 0,
    "operator_id" bigint DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_asset_import_row" (
    "id" bigint NOT NULL AUTO_INCREMENT,
    "batch_id" bigint NOT NULL,
    "file_hash" char(64) NOT NULL,
    "sheet_name" varchar(100) NOT NULL,
    "row_num" int NOT NULL,
    "asset_id" bigint NOT NULL,
    "asset_code" varchar(64) NOT NULL,
    "import_action" tinyint NOT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_asset_change_log" (
    "id"             bigint       NOT NULL AUTO_INCREMENT,
    "asset_id"       bigint       NOT NULL,
    "change_type"    tinyint      NOT NULL,
    "before_status"  tinyint               DEFAULT NULL,
    "after_status"   tinyint               DEFAULT NULL,
    "before_employee_id" bigint            DEFAULT NULL,
    "after_employee_id"  bigint            DEFAULT NULL,
    "before_dept_id" bigint                DEFAULT NULL,
    "after_dept_id"  bigint                DEFAULT NULL,
    "biz_id"         bigint                DEFAULT NULL,
    "content"        varchar(500)          DEFAULT NULL,
    "operator_id"    bigint                DEFAULT NULL,
    "operate_time"   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "creator"        varchar(64)           DEFAULT '',
    "create_time"    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"        varchar(64)           DEFAULT '',
    "update_time"    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        bit          NOT NULL DEFAULT FALSE,
    "tenant_id"      bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_transfer" (
    "id"                   bigint      NOT NULL AUTO_INCREMENT,
    "no"                   varchar(64) NOT NULL,
    "type"                 tinyint     NOT NULL,
    "asset_id"             bigint      NOT NULL,
    "asset_code_snapshot" varchar(100), "asset_name_snapshot" varchar(200), "type_label_snapshot" varchar(50),
    "from_employee_id"     bigint               DEFAULT NULL,
    "from_dept_id"         bigint               DEFAULT NULL,
    "from_employee_name_snapshot" varchar(100), "from_dept_name_snapshot" varchar(100),
    "to_employee_id"       bigint               DEFAULT NULL,
    "to_dept_id"           bigint               DEFAULT NULL,
    "to_employee_name_snapshot" varchar(100), "to_dept_name_snapshot" varchar(100),
    "expected_return_date" date                 DEFAULT NULL,
    "actual_return_date"   date                 DEFAULT NULL,
    "status"               tinyint     NOT NULL DEFAULT 0,
    "process_instance_id"  varchar(64)          DEFAULT NULL,
    "round_no" int NOT NULL DEFAULT 1,
    "reason"               varchar(500)         DEFAULT NULL,
    "apply_user_id"        bigint               DEFAULT NULL,
    "apply_user_name_snapshot" varchar(100), "apply_dept_id" bigint, "apply_dept_name_snapshot" varchar(100),
    "apply_time"           timestamp            DEFAULT NULL,
    "inspection_result" tinyint, "inspection_remark" varchar(500), "inspection_file_urls" varchar(2000),
    "inspected_by_user_id" bigint, "inspected_at" timestamp, "version" int NOT NULL DEFAULT 0,
    "creator"              varchar(64)          DEFAULT '',
    "create_time"          timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"              varchar(64)          DEFAULT '',
    "update_time"          timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"              bit         NOT NULL DEFAULT FALSE,
    "tenant_id"            bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_inventory" (
    "id"             bigint       NOT NULL AUTO_INCREMENT,
    "no"             varchar(64)  NOT NULL,
    "name"           varchar(200) NOT NULL,
    "scope_type"     tinyint      NOT NULL,
    "scope_value"    varchar(500)          DEFAULT NULL,
    "status"         tinyint      NOT NULL DEFAULT 0,
    "total_count"    int          NOT NULL DEFAULT 0,
    "checked_count"  int          NOT NULL DEFAULT 0,
    "normal_count"   int          NOT NULL DEFAULT 0,
    "abnormal_count" int          NOT NULL DEFAULT 0,
    "start_time"     timestamp             DEFAULT NULL,
    "end_time"       timestamp             DEFAULT NULL,
    "remark"         varchar(500)          DEFAULT NULL,
    "creator"        varchar(64)           DEFAULT '',
    "create_time"    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"        varchar(64)           DEFAULT '',
    "update_time"    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        bit          NOT NULL DEFAULT FALSE,
    "tenant_id"      bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_inventory_detail" (
    "id"              bigint       NOT NULL AUTO_INCREMENT,
    "inventory_id"    bigint       NOT NULL,
    "asset_id"        bigint       NOT NULL,
    "expect_employee_id" bigint             DEFAULT NULL,
    "expect_dept_id"  bigint                DEFAULT NULL,
    "expect_location" varchar(255)          DEFAULT NULL,
    "actual_employee_id" bigint             DEFAULT NULL,
    "actual_dept_id"  bigint                DEFAULT NULL,
    "actual_location" varchar(255)          DEFAULT NULL,
    "result"          tinyint      NOT NULL DEFAULT 0,
    "remark"          varchar(500)          DEFAULT NULL,
    "check_user_id"   bigint                DEFAULT NULL,
    "check_time"      timestamp             DEFAULT NULL,
    "creator"         varchar(64)           DEFAULT '',
    "create_time"     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"         varchar(64)           DEFAULT '',
    "update_time"     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"         bit          NOT NULL DEFAULT FALSE,
    "tenant_id"       bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_repair" (
    "id"            bigint        NOT NULL AUTO_INCREMENT,
    "asset_id"      bigint        NOT NULL,
    "fault_desc"    varchar(500)  NOT NULL,
    "repair_vendor" varchar(200)           DEFAULT NULL,
    "cost"          decimal(10, 2)         DEFAULT NULL,
    "start_time"    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "end_time"      timestamp              DEFAULT NULL,
    "result"        varchar(500)           DEFAULT NULL,
    "creator"       varchar(64)            DEFAULT '',
    "create_time"   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"       varchar(64)            DEFAULT '',
    "update_time"   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"       bit           NOT NULL DEFAULT FALSE,
    "tenant_id"     bigint        NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_scrap" (
    "id"                  bigint      NOT NULL AUTO_INCREMENT,
    "no"                  varchar(64) NOT NULL,
    "asset_id"            bigint      NOT NULL,
    "reason_type"         tinyint     NOT NULL,
    "reason"              varchar(500)         DEFAULT NULL,
    "scrap_date"          date                 DEFAULT NULL,
    "status"              tinyint     NOT NULL DEFAULT 0,
    "process_instance_id" varchar(64)          DEFAULT NULL,
    "apply_user_id"       bigint               DEFAULT NULL,
    "apply_time"          timestamp            DEFAULT NULL,
    "creator"             varchar(64)          DEFAULT '',
    "create_time"         timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"             varchar(64)          DEFAULT '',
    "update_time"         timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"             bit         NOT NULL DEFAULT FALSE,
    "tenant_id"           bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_code_rule" (
    "id"                bigint      NOT NULL AUTO_INCREMENT,
    "category_id"       bigint               DEFAULT NULL,
    "prefix"            varchar(20)          DEFAULT NULL,
    "use_category_code" bit         NOT NULL DEFAULT FALSE,
    "date_format"       varchar(20)          DEFAULT NULL,
    "serial_length"     int         NOT NULL DEFAULT 4,
    "separator"         varchar(5)           DEFAULT '-',
    "current_serial"    bigint      NOT NULL DEFAULT 0,
    "creator"           varchar(64)          DEFAULT '',
    "create_time"       timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"           varchar(64)          DEFAULT '',
    "update_time"       timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"           bit         NOT NULL DEFAULT FALSE,
    "tenant_id"         bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_stock_balance" (
    "id"                  bigint       NOT NULL AUTO_INCREMENT,
    "name"                varchar(200) NOT NULL,
    "category_id"         bigint       NOT NULL,
    "management_mode"     tinyint      NOT NULL,
    "delivery_mode"       tinyint      NOT NULL,
    "custody_mode"        tinyint      NOT NULL,
    "unit"                varchar(20)  NOT NULL,
    "attribute_signature" char(64)     NOT NULL,
    "ext_fields"          varchar(4000)         DEFAULT NULL,
    "ext_field_labels"    varchar(4000)         DEFAULT NULL,
    "ext_field_dict_types" varchar(4000)        DEFAULT NULL,
    "on_hand_quantity"    int          NOT NULL DEFAULT 0,
    "reserved_quantity"   int          NOT NULL DEFAULT 0,
    "frozen_quantity"     int          NOT NULL DEFAULT 0,
    "minimum_quantity"    int          NOT NULL DEFAULT 0,
    "next_expiry_date"    date                  DEFAULT NULL,
    "version"             int          NOT NULL DEFAULT 0,
    "creator"             varchar(64)           DEFAULT '',
    "create_time"         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"             varchar(64)           DEFAULT '',
    "update_time"         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"             bit          NOT NULL DEFAULT FALSE,
    "tenant_id"           bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"),
    UNIQUE ("tenant_id", "category_id", "unit", "attribute_signature",
            "management_mode", "delivery_mode", "custody_mode")
);

CREATE TABLE IF NOT EXISTS "eam_demand" (
    "id" bigint NOT NULL AUTO_INCREMENT, "no" varchar(64) NOT NULL, "employee_id" bigint NOT NULL,
    "applicant_user_id" bigint NOT NULL, "applicant_dept_id" bigint DEFAULT NULL, "status" tinyint NOT NULL,
    "process_instance_id" varchar(64) DEFAULT NULL, "reason" varchar(500) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"), UNIQUE ("tenant_id", "no", "deleted")
);

CREATE TABLE IF NOT EXISTS "eam_demand_item" (
    "id" bigint NOT NULL AUTO_INCREMENT, "demand_id" bigint NOT NULL, "name" varchar(200) NOT NULL,
    "category_id" bigint NOT NULL, "management_mode" tinyint NOT NULL, "delivery_mode" tinyint NOT NULL,
    "delivery_mode_label_snapshot" varchar(50) NOT NULL, "custody_mode" tinyint NOT NULL,
    "custody_mode_label_snapshot" varchar(50) NOT NULL, "quantity" int NOT NULL, "unit" varchar(20) NOT NULL,
    "ext_fields" varchar(4000) DEFAULT NULL, "ext_field_labels" varchar(4000) DEFAULT NULL,
    "ext_field_dict_types" varchar(4000) DEFAULT NULL, "reserved_quantity" int NOT NULL DEFAULT 0,
    "purchased_quantity" int NOT NULL DEFAULT 0, "fulfilled_quantity" int NOT NULL DEFAULT 0,
    "closed_quantity" int NOT NULL DEFAULT 0, "creator" varchar(64) DEFAULT '',
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updater" varchar(64) DEFAULT '',
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "deleted" bit NOT NULL DEFAULT FALSE,
    "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_purchase" (
    "id" bigint NOT NULL AUTO_INCREMENT, "no" varchar(64) NOT NULL, "status" tinyint NOT NULL,
    "payment_mode" int NOT NULL, "payment_mode_label_snapshot" varchar(100) NOT NULL,
    "supplier_name_snapshot" varchar(200) DEFAULT NULL, "supplier_contact_snapshot" varchar(200) DEFAULT NULL,
    "estimated_amount" decimal(14,2) DEFAULT NULL, "actual_amount" decimal(14,2) DEFAULT NULL,
    "expected_arrival_date" date DEFAULT NULL, "process_instance_id" varchar(64) DEFAULT NULL,
    "expense_status" tinyint NOT NULL DEFAULT 0, "expense_process_instance_id" varchar(64) DEFAULT NULL,
    "applicant_user_id" bigint NOT NULL, "file_urls" varchar(4000) DEFAULT NULL, "remark" varchar(1000) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"), UNIQUE ("tenant_id", "no", "deleted")
);

CREATE TABLE IF NOT EXISTS "eam_purchase_item" (
    "id" bigint NOT NULL AUTO_INCREMENT, "purchase_id" bigint NOT NULL, "name" varchar(200) NOT NULL,
    "category_id" bigint NOT NULL, "management_mode" tinyint NOT NULL, "delivery_mode" tinyint NOT NULL,
    "delivery_mode_label_snapshot" varchar(50) NOT NULL, "custody_mode" tinyint NOT NULL,
    "custody_mode_label_snapshot" varchar(50) NOT NULL, "quantity" int NOT NULL,
    "received_quantity" int NOT NULL DEFAULT 0, "returned_quantity" int NOT NULL DEFAULT 0,
    "short_closed_quantity" int NOT NULL DEFAULT 0, "short_close_remark" varchar(500) DEFAULT NULL,
    "unit" varchar(20) NOT NULL, "unit_price" decimal(14,2) DEFAULT NULL,
    "ext_fields" varchar(4000) DEFAULT NULL, "ext_field_labels" varchar(4000) DEFAULT NULL,
    "ext_field_dict_types" varchar(4000) DEFAULT NULL, "creator" varchar(64) DEFAULT '',
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updater" varchar(64) DEFAULT '',
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "deleted" bit NOT NULL DEFAULT FALSE,
    "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_purchase_source" (
    "id" bigint NOT NULL AUTO_INCREMENT, "purchase_item_id" bigint NOT NULL, "demand_item_id" bigint DEFAULT NULL,
    "quantity" int NOT NULL, "fulfilled_quantity" int NOT NULL DEFAULT 0, "closed_quantity" int NOT NULL DEFAULT 0,
    "target_employee_id" bigint DEFAULT NULL, "target_dept_id" bigint DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_receipt" (
    "id" bigint NOT NULL AUTO_INCREMENT, "no" varchar(64) NOT NULL, "purchase_id" bigint NOT NULL,
    "type" tinyint NOT NULL, "operator_user_id" bigint DEFAULT NULL, "operate_time" timestamp NOT NULL,
    "file_urls" varchar(4000) DEFAULT NULL, "remark" varchar(500) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"), UNIQUE ("tenant_id", "no", "deleted")
);

CREATE TABLE IF NOT EXISTS "eam_receipt_item" (
    "id" bigint NOT NULL AUTO_INCREMENT, "receipt_id" bigint NOT NULL, "purchase_item_id" bigint NOT NULL,
    "stock_balance_id" bigint DEFAULT NULL, "quantity" int NOT NULL, "unit_price" decimal(14,2) DEFAULT NULL,
    "serial_numbers" varchar(4000) DEFAULT NULL, "actual_ext_fields" varchar(4000) DEFAULT NULL,
    "actual_ext_field_labels" varchar(4000) DEFAULT NULL, "actual_ext_field_dict_types" varchar(4000) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_stock_movement" (
    "id" bigint NOT NULL AUTO_INCREMENT, "stock_balance_id" bigint NOT NULL, "type" tinyint NOT NULL,
    "quantity" int NOT NULL, "before_quantity" int NOT NULL, "after_quantity" int NOT NULL,
    "business_type" varchar(50) NOT NULL, "business_id" bigint DEFAULT NULL, "operator_user_id" bigint DEFAULT NULL,
    "operate_time" timestamp NOT NULL, "remark" varchar(500) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_stock_reservation" (
    "id" bigint NOT NULL AUTO_INCREMENT, "demand_item_id" bigint NOT NULL, "stock_balance_id" bigint DEFAULT NULL,
    "asset_id" bigint DEFAULT NULL, "target_employee_id" bigint NOT NULL,
    "quantity" int NOT NULL, "status" tinyint NOT NULL, "creator" varchar(64) DEFAULT '',
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updater" varchar(64) DEFAULT '',
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "deleted" bit NOT NULL DEFAULT FALSE,
    "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_stock_holding" (
    "id" bigint NOT NULL AUTO_INCREMENT, "employee_id" bigint NOT NULL,
    "asset_id" bigint DEFAULT NULL, "stock_balance_id" bigint DEFAULT NULL, "name_snapshot" varchar(220) NOT NULL,
    "quantity" int NOT NULL, "custody_mode" tinyint NOT NULL, "status" tinyint NOT NULL,
    "signed_at" timestamp DEFAULT NULL, "return_applied_at" timestamp DEFAULT NULL,
    "return_inspected_at" timestamp DEFAULT NULL, "return_result" tinyint DEFAULT NULL,
    "return_remark" varchar(500) DEFAULT NULL, "creator" varchar(64) DEFAULT '',
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updater" varchar(64) DEFAULT '',
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "deleted" bit NOT NULL DEFAULT FALSE,
    "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "eam_stock_reminder" (
    "id" bigint NOT NULL AUTO_INCREMENT, "scene" varchar(40) NOT NULL, "business_type" varchar(40) NOT NULL,
    "business_id" bigint NOT NULL, "due_date" date DEFAULT NULL, "reminder_date" date NOT NULL,
    "status" tinyint NOT NULL DEFAULT 0, "content" varchar(500) NOT NULL, "creator" varchar(64) DEFAULT '',
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "updater" varchar(64) DEFAULT '',
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "deleted" bit NOT NULL DEFAULT FALSE,
    "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id"),
    UNIQUE ("tenant_id", "scene", "business_type", "business_id", "reminder_date", "deleted")
);

CREATE TABLE IF NOT EXISTS "eam_employee_asset_task" (
    "id" bigint NOT NULL AUTO_INCREMENT, "event_key" varchar(160) NOT NULL, "latest_event_key" varchar(160) NOT NULL,
    "type" tinyint NOT NULL, "status" tinyint NOT NULL, "employee_id" bigint NOT NULL,
    "leader_user_id" bigint DEFAULT NULL, "employee_name_snapshot" varchar(100) NOT NULL,
    "dept_id_snapshot" bigint DEFAULT NULL, "process_instance_id" varchar(64) DEFAULT NULL,
    "demand_id" bigint DEFAULT NULL, "planned_leave_time" timestamp DEFAULT NULL, "remark" varchar(500) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id"),
    UNIQUE ("tenant_id", "event_key", "deleted"), UNIQUE ("tenant_id", "latest_event_key", "deleted")
);

CREATE TABLE IF NOT EXISTS "eam_employee_asset_task_item" (
    "id" bigint NOT NULL AUTO_INCREMENT, "task_id" bigint NOT NULL, "asset_id" bigint DEFAULT NULL,
    "holding_id" bigint DEFAULT NULL, "asset_name_snapshot" varchar(220) NOT NULL, "action" tinyint DEFAULT NULL,
    "transfer_to_employee_id" bigint DEFAULT NULL, "status" tinyint NOT NULL DEFAULT 0, "remark" varchar(500) DEFAULT NULL,
    "creator" varchar(64) DEFAULT '', "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '', "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE, "tenant_id" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);
