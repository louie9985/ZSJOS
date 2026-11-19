"""UTF-8. Retained isolated MySQL fixtures; application database is read-only."""
from pathlib import Path
import subprocess
import uuid
from test_positioning_single_source import query
sql = (Path(__file__).resolve().parents[1] / 'migrations/V377__flowable_8_schema_metadata.sql').read_text(encoding='utf-8')
source = 'ruoyi-vue-pro'
tables = query(source, "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND (UPPER(table_name) REGEXP '^(ACT|FLW)_' OR table_name IN ('zsjos_schema_version','zsjos_module_schema_version'))").splitlines()
r = subprocess.run(['docker','exec','yudao-mysql','sh','-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-data --skip-add-drop-table --set-gtid-purged=OFF "$@"','dump',source]+tables,capture_output=True)
assert r.returncode == 0
schema = r.stdout.decode('utf-8')
prefix = 'v377_' + uuid.uuid4().hex[:8]
def fixture(suffix, populated=True):
    name = prefix + suffix
    query(source, 'CREATE DATABASE ' + name + ' CHARACTER SET utf8mb4')
    query(name, schema)
    if populated:
        query(name, "INSERT INTO ACT_RU_TASK(ID_,REV_,NAME_) VALUES ('11111111-1111-4111-8111-111111111111',1,'fixture')")
    return name
def reject(name, mode):
    before = query(name, 'CHECKSUM TABLE ACT_GE_PROPERTY,ACT_ID_PROPERTY,zsjos_schema_version,zsjos_module_schema_version,ACT_RU_TASK')
    try: query(name, mode + sql)
    except RuntimeError: pass
    else: raise AssertionError('expected refusal')
    assert before == query(name, 'CHECKSUM TABLE ACT_GE_PROPERTY,ACT_ID_PROPERTY,zsjos_schema_version,zsjos_module_schema_version,ACT_RU_TASK')
def apply(name, mode): query(name, mode + sql)
mode = "SET @zsjos_v377_id_generator='strong-uuid';\n"
unknown = fixture('_unknown')
reject(unknown, '')
reject(unknown, "SET @zsjos_v377_id_generator='database';")
reject(unknown, "SET @zsjos_v377_id_generator='typo';")
apply(unknown, mode)
assert query(unknown, "SELECT COUNT(*) FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid'").strip() == '0'
before = query(unknown, 'CHECKSUM TABLE ACT_GE_PROPERTY,ACT_ID_PROPERTY,zsjos_schema_version,zsjos_module_schema_version,ACT_RU_TASK')
apply(unknown, mode)
assert before == query(unknown, 'CHECKSUM TABLE ACT_GE_PROPERTY,ACT_ID_PROPERTY,zsjos_schema_version,zsjos_module_schema_version,ACT_RU_TASK')
existing = fixture('_existing')
query(existing, "INSERT INTO ACT_GE_PROPERTY VALUES ('next.dbid','54321',7)")
apply(existing, mode)
assert query(existing, "SELECT VALUE_,REV_ FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid'").strip() == '54321\t7'
empty = fixture('_empty', False)
apply(empty, '')
assert query(empty, "SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid'").strip() == '1'
invalid = fixture('_invalid')
query(invalid, "INSERT INTO ACT_GE_PROPERTY VALUES ('next.dbid','bad',1)")
reject(invalid, mode)
ledger = fixture('_ledger')
query(ledger, "CREATE TRIGGER refuse_marker BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
reject(ledger, mode)
query(ledger, 'DROP TRIGGER refuse_marker')
apply(ledger, mode)
shape = fixture('_shape')
query(shape, 'ALTER TABLE ACT_RU_TASK ADD COLUMN fixture_extra INT')
reject(shape, mode)
print('PASS UUID/no-mode/database/invalid-mode/existing-counter/invalid-counter/empty/replay/ledger rollback/recovery/schema rejection; retained',prefix)
