"""UTF-8. Scoped V367 lifecycle checks in retained isolated MySQL databases."""
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[4]
PREFIX = 'verify_exam_body_' + uuid.uuid4().hex[:8]

def sql(database, source, success=True):
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names --skip-force ' + database],
        input=source.encode('utf-8'), capture_output=True)
    assert (result.returncode == 0) == success, result.stderr.decode('utf-8', errors='replace')
    return result.stdout.decode('utf-8').strip()

def fixture(suffix, prerequisite=True):
    name = PREFIX + suffix
    sql('', f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4;')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version', 'zsjos_exam_collection_task']:
        sql(name, f'CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};')
    if sql(name, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_task' AND column_name='content'") == '1':
        sql(name, 'ALTER TABLE zsjos_exam_collection_task DROP COLUMN content')
    if prerequisite:
        sql(name, "INSERT INTO zsjos_schema_version(version,description) VALUES('V366','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V366','fixture','fixture','fixture');")
    return name

def markers(name):
    return sql(name, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V367'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V367';").splitlines()

source = (ROOT / 'script/sql/mysql/migrations/V367__exam_collection_rich_body.sql').read_text(encoding='utf-8-sig')
good = fixture('_repeat')
sql(good, source); sql(good, source)
assert markers(good) == ['1', '1']
assert sql(good, "SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_collection_task' AND column_name='content'") == 'mediumtext'
sql(good, "SET NAMES utf8mb4; INSERT INTO zsjos_exam_collection_task(title,content,status,config_version_id,fields_snapshot,token_hash,token_ciphertext,tenant_id) VALUES('fixture','考试资料正文','OPEN',1,'[]','fixture','fixture',1);")
assert sql(good, "SELECT HEX(content) FROM zsjos_exam_collection_task WHERE tenant_id=1 AND token_hash='fixture';") == 'E88083E8AF95E8B584E69699E6ADA3E69687'
missing = fixture('_missing', False)
sql(missing, source, False); assert markers(missing) == ['0', '0']
partial = fixture('_partial')
sql(partial, 'ALTER TABLE zsjos_exam_collection_task ADD COLUMN content mediumtext DEFAULT NULL')
sql(partial, source); assert markers(partial) == ['1', '1']
failure = fixture('_failure')
sql(failure, "CREATE TRIGGER reject_marker BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';")
sql(failure, source, False); assert markers(failure) == ['0', '0']
sql(failure, 'DROP TRIGGER reject_marker;'); sql(failure, source)
assert markers(failure) == ['1', '1']
print('PASS initial/repeat/prerequisite/partial/ledger-failure/recovery/UTF8; retained ' + PREFIX)
