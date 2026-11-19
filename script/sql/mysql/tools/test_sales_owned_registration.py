# -*- coding: utf-8 -*-
"""Exercise V374 on a synthetic, retained schema using the local MySQL 8 engine."""
from datetime import datetime

from test_positioning_single_source import ROOT, query

SQL = (ROOT / "script/sql/mysql/migrations/V374__sales_owned_registration.sql").read_text(encoding="utf-8")
DB = "verify374_" + datetime.now().strftime("%Y%m%d%H%M%S")
TABLES = (
    "system_menu", "system_role_menu", "zsjos_schema_version", "zsjos_module_schema_version",
    "zsjos_registration_case", "zsjos_order", "zsjos_order_item", "zsjos_registration_checklist_template",
    "zsjos_registration_checklist_template_item", "zsjos_registration_case_checklist_item",
    "zsjos_registration_case_route", "zsjos_registration_route_option", "zsjos_student_info_form", "zsjos_student_info_task",
)


def execute(sql):
    return query(DB, sql).strip()


def expect_failure(message):
    try:
        execute(SQL)
    except RuntimeError as exc:
        assert message in str(exc), str(exc)
    else:
        raise AssertionError("V374 unexpectedly succeeded")
    assert execute("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V374';") == "0"
    assert execute("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V374';") == "0"


if __name__ == "__main__":
    query("ruoyi-vue-pro", f"CREATE DATABASE {DB} CHARACTER SET utf8mb4;")
    for table in TABLES:
        execute(f"CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};")
    execute("""
        INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,status)
        VALUES(1,'旧入口','zsjos:registration:query-pool',2,1,10,'registration-pool','','zsjos/registration-pool',0),
              (2,'旧采集','zsjos:student-info-task:query',2,2,10,'student-info-tasks','','',0),
              (3,'复用','zsjos:student-info-task:reuse',3,1,2,'','','',0);
        INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(100,1,1),(100,3,1);
        INSERT INTO zsjos_order(id,order_no,order_type,payable_amount,person_id,status,total_amount,tenant_id,formal_sales_user_id)
        VALUES(101,'A','new',1,9,'pending_approval',1,1,7),(102,'B','new',1,9,'effective',1,1,8),
              (103,'C','new',1,9,'revision_required',1,1,7);
        INSERT INTO zsjos_order_item(id,order_id,payable_amount,unit_price,product_snapshot,tenant_id)
        VALUES(201,101,1,1,'{}',1),(202,102,1,1,'{}',1),(203,103,1,1,'{}',1);
        INSERT INTO zsjos_registration_case(id,order_id,order_item_id,status,owner_user_id,tenant_id)
        VALUES(301,102,NULL,'processing',99,1),(302,103,203,'pending',99,1);
        INSERT INTO zsjos_registration_checklist_template(name,published_version_id,tenant_id) VALUES('模板',404,1);
        INSERT INTO zsjos_registration_checklist_template_item(id,version_id,item_key,item_type,title,sort,tenant_id)
        VALUES(405,404,'check','checkbox','核对资料',1,1);
        INSERT INTO zsjos_student_info_form(id,registration_case_id,person_id,config_version_id,expires_at,sales_user_id,
          status,tenant_id,token_ciphertext,token_hash)
        VALUES(401,301,9,404,'2027-12-01',8,'SUBMITTED',1,'fixture-unchanged','fixture-token-hash');
    """)
    expect_failure("requires Core V373")
    execute("ALTER TABLE zsjos_registration_case DROP INDEX idx_tenant_owner_status;")
    execute("""
        INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V373','fixture','fixture');
        INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
        VALUES('core','V373','fixture','fixture','baseline');
        INSERT INTO zsjos_order_item(id,order_id,payable_amount,unit_price,product_snapshot,tenant_id)
        VALUES(204,102,1,1,'{}',1);
    """)
    expect_failure("ambiguous unfinished legacy case")
    execute("UPDATE zsjos_order_item SET deleted=1 WHERE id=204;")
    execute("""
        CREATE TRIGGER reject374 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW
           SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure';
    """)
    expect_failure("fixture ledger failure")
    assert execute("SELECT COUNT(*) FROM zsjos_registration_case;") == "2"
    assert execute("SELECT COUNT(*) FROM zsjos_student_info_task;") == "0"
    execute("DROP TRIGGER reject374;")
    execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V374','fixture','fixture');")
    execute(SQL)
    assert execute("SELECT order_item_id,status,owner_user_id FROM zsjos_registration_case ORDER BY order_item_id;") == (
        "201\tpending\t7\n202\tprocessing\t8\n203\tcancelled\t7"
    )
    assert execute("SELECT order_item_id,form_id FROM zsjos_student_info_task ORDER BY order_item_id;") == "201\tNULL\n202\t401"
    assert execute("SELECT registration_case_id,token_ciphertext FROM zsjos_student_info_form;") == "301\tfixture-unchanged"
    assert execute("SELECT COUNT(*) FROM zsjos_registration_case_checklist_item;") == "1"
    assert execute("SELECT role_id,menu_id FROM system_role_menu ORDER BY menu_id;") == "100\t1\n100\t3"
    assert execute("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:registration:query-my';") == "我的报名履约".encode().hex().upper()
    assert execute("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:registration:transfer';") == "转交报名履约".encode().hex().upper()
    execute("UPDATE zsjos_registration_case SET owner_user_id=10 WHERE id=301;")
    execute(SQL)
    assert execute("SELECT owner_user_id FROM zsjos_registration_case WHERE id=301;") == "10"
    assert execute("SELECT COUNT(*) FROM zsjos_registration_case;") == "3"
    assert execute("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V374';") == "1"
    assert execute("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V374';") == "1"
    print("PASS initial replay missing prerequisite ambiguous case partial DDL transactional ledger rollback and recovery, Chinese HEX.", DB)

