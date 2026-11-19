"""Exercise V378 on a unique local MySQL fixture; retain it for inspection."""
import subprocess
import uuid
from pathlib import Path

source = "`ruoyi-vue-pro`"
database = "agreement_" + uuid.uuid4().hex[:12]
migration = (Path(__file__).resolve().parents[1] / "migrations/V378__enrollment_agreement.sql").read_text(encoding="utf-8")

def query(sql, expect_success=True):
    command = ["docker", "exec", "-i", "yudao-mysql", "sh", "-c",
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 -BN']
    result = subprocess.run(command, input=sql.encode("utf-8"), capture_output=True)
    assert (result.returncode == 0) == expect_success, result.stderr.decode("utf-8", "replace")
    return result.stdout.decode("utf-8").strip()

def run(sql, ok=True):
    return query(f"USE {database};\n" + sql, ok)

query(f"CREATE DATABASE {database} CHARACTER SET utf8mb4")
run("CREATE TABLE zsjos_student_info_form (id BIGINT PRIMARY KEY)")
for table in ["system_menu", "zsjos_schema_version", "zsjos_module_schema_version"]:
    run(f"CREATE TABLE {table} LIKE {source}.{table}")
run(f"INSERT INTO system_menu SELECT * FROM {source}.system_menu WHERE path='/zsjos' AND type=1 AND deleted=0")
run(migration, False)
assert run("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V378'") == "0"
for table in ["zsjos_schema_version", "zsjos_module_schema_version"]:
    run(f"INSERT INTO {table} SELECT * FROM {source}.{table} WHERE version='V370'")
# Recover missing menu/ledger entries after a complete initial application in a separate fixture.
fresh = database + "_fresh"
query(f"CREATE DATABASE {fresh} CHARACTER SET utf8mb4")
for table in ["system_menu", "zsjos_schema_version", "zsjos_module_schema_version", "zsjos_student_info_form"]:
    columns = query(f"SELECT GROUP_CONCAT(CONCAT('`',column_name,'`') ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='{database}' AND table_name='{table}' AND generation_expression=''")
    query(f"CREATE TABLE {fresh}.{table} LIKE {database}.{table}; INSERT INTO {fresh}.{table} ({columns}) SELECT {columns} FROM {database}.{table}")
query(f"USE {fresh};\n" + migration)
assert query(f"SELECT COUNT(*) FROM {fresh}.zsjos_schema_version WHERE version='V378'") == "1"
run("ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_version BIGINT NULL")
run(migration)
run(migration)
assert run("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:agreement:%'") == "3"
assert run("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V378'") == "1"
assert run("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V378'") == "1"
assert run("SELECT HEX(SUBSTRING(published_body,5,4)) FROM zsjos_enrollment_agreement") == "职业证书".encode().hex().upper()
assert run("SELECT COUNT(*) FROM zsjos_enrollment_agreement WHERE tenant_id=1 AND published_body LIKE '%{{实际费用}}%' AND published_body LIKE '%第十条%'") == "1"
print("V378 prerequisite failure, partial recovery, repeat, menu, UTF-8 and ledgers passed:", database)
upgrade = (Path(__file__).resolve().parents[1] / "migrations/V380__enrollment_category_agreement.sql").read_text(encoding="utf-8")
run("DELETE FROM zsjos_module_schema_version WHERE module_code='core' AND version='V378'")
run(upgrade, False)
assert run("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V380'") == "0"
run(migration)
query(f"USE {fresh};\n" + upgrade)
run("ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_category_name INT NULL")
run(upgrade, False)
for table in ["zsjos_schema_version", "zsjos_module_schema_version"]:
    assert run(f"SELECT COUNT(*) FROM {table} WHERE version='V380'") == "0"
run("ALTER TABLE zsjos_student_info_form MODIFY COLUMN agreement_category_name VARCHAR(255) NULL")
run(upgrade)
assert run("SELECT COUNT(*) FROM zsjos_enrollment_category_agreement") == "0"
run("INSERT INTO zsjos_enrollment_category_agreement(id,tenant_id,revision,published_version,draft_body,published_body) VALUES (11,1,1,1,'甲','甲'),(12,1,1,0,'乙','')")
run(upgrade)
assert run("SELECT GROUP_CONCAT(HEX(draft_body) ORDER BY id) FROM zsjos_enrollment_category_agreement") == ",".join(value.encode().hex().upper() for value in ['甲','乙'])
for table in ["zsjos_schema_version", "zsjos_module_schema_version"]:
    assert run(f"SELECT HEX(description) FROM {table} WHERE version='V380'") == '末级分类报名协议'.encode().hex().upper()
print("V380 fresh upgrade, failed prerequisite/postcondition, recovery, repeat, category isolation and UTF-8 passed")
