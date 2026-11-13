"""UTF-8. Scoped V368 MySQL verification, retained fixtures and optional local apply."""
from pathlib import Path
import subprocess
import uuid
import sys

ROOT = Path(__file__).resolve().parents[4]
PREFIX = 'verify_exam_export_' + uuid.uuid4().hex[:8]

def sql(database, source, success=True):
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names --skip-force ' + database],
        input=source.encode('utf-8'), capture_output=True)
    assert (result.returncode == 0) == success, result.stderr.decode('utf-8', errors='replace')
    return result.stdout.decode('utf-8').strip()

def fixture(suffix, prerequisite=True):
    name = PREFIX + suffix
    sql('', f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4;')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version']:
        sql(name, f'CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};')
    sql(name, 'CREATE TABLE zsjos_exam_collection_task(id bigint PRIMARY KEY, tenant_id bigint, title varchar(255), content mediumtext) CHARACTER SET utf8mb4;')
    sql(name, "SET NAMES utf8mb4; INSERT INTO zsjos_exam_collection_task VALUES(1,1,'合成考试任务','正文');")
    if prerequisite:
        sql(name, "INSERT INTO zsjos_schema_version(version,description) VALUES('V367','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V367','fixture','fixture','fixture');")
    return name

def markers(name):
    return sql(name, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V368'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V368';").splitlines()

source = (ROOT / 'script/sql/mysql/migrations/V368__exam_collection_export_settings.sql').read_text(encoding='utf-8')
good = fixture('_repeat')
sql(good, source); sql(good, source)
assert markers(good) == ['1', '1']
assert sql(good, "SELECT HEX(title), export_settings_json IS NULL FROM zsjos_exam_collection_task WHERE tenant_id=1;") == 'E59088E68890E88083E8AF95E4BBBBE58AA1\t1'
sql(good, "SET NAMES utf8mb4; UPDATE zsjos_exam_collection_task SET export_settings_json='{\"naming\":[\"text:考试资料\"]}' WHERE tenant_id=1 AND id=1;")
assert sql(good, "SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(export_settings_json,'$.naming[0]'))) FROM zsjos_exam_collection_task WHERE tenant_id=1;") == '746578743AE88083E8AF95E8B584E69699'
missing = fixture('_missing', False)
sql(missing, source, False); assert markers(missing) == ['0', '0']
partial = fixture('_partial')
sql(partial, 'ALTER TABLE zsjos_exam_collection_task ADD COLUMN export_settings_json mediumtext DEFAULT NULL')
sql(partial, source); assert markers(partial) == ['1', '1']
failure = fixture('_failure')
sql(failure, "CREATE TRIGGER reject_marker BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';")
sql(failure, source, False); assert markers(failure) == ['0', '0']
sql(failure, 'DROP TRIGGER reject_marker;'); sql(failure, source)
assert markers(failure) == ['1', '1']
incompatible = fixture('_incompatible')
sql(incompatible, 'ALTER TABLE zsjos_exam_collection_task ADD COLUMN export_settings_json int DEFAULT NULL')
sql(incompatible, source, False); assert markers(incompatible) == ['0', '0']
print('PASS initial/repeat/prerequisite/partial/ledger-failure/recovery/incompatible/UTF8; retained ' + PREFIX)
if '--apply-local' in sys.argv:
    target = 'ruoyi-vue-pro'
    assert sql(target, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V367'") == '1'
    before = sql(target, 'SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,tenant_id,title,COALESCE(content,\'\')))),0) FROM zsjos_exam_collection_task')
    sql(target, source); sql(target, source)
    assert markers(target) == ['1', '1']
    assert before == sql(target, 'SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,tenant_id,title,COALESCE(content,\'\')))),0) FROM zsjos_exam_collection_task')
    metadata = "SELECT data_type,character_set_name,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_task' AND column_name='export_settings_json'"
    assert sql(target, metadata) == sql(good, metadata) == 'mediumtext\tutf8mb4\tYES'
    print('PASS local ruoyi-vue-pro targeted apply/repeat/ledgers/schema comparison/task preservation')
