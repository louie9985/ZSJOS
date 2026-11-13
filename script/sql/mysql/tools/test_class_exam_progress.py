"""UTF-8: V366 scoped MySQL lifecycle verification; retains synthetic databases."""
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[4]
MIGRATION = ROOT / 'script/sql/mysql/migrations/V366__class_exam_progress.sql'
PREFIX = 'verify_class_exam_' + uuid.uuid4().hex[:8]

def sql(database, source, success=True):
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names --skip-force ' + database],
        input=source.encode('utf-8'), capture_output=True)
    if (result.returncode == 0) != success:
        raise AssertionError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8')

def fixture(suffix, prerequisite=True):
    name = PREFIX + suffix
    sql('', f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4;')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version', 'system_dict_type', 'system_dict_data']:
        sql(name, f'CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};')
    if prerequisite:
        sql(name, "INSERT INTO zsjos_schema_version(version,description) VALUES('V365','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V365','fixture','fixture','fixture');")
    return name

def markers(name):
    return sql(name, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V366'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V366';").strip().splitlines()

source = MIGRATION.read_text(encoding='utf-8')
good = fixture('_repeat')
sql(good, source); sql(good, source)
assert markers(good) == ['1', '1']
assert sql(good, "SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_exam_outcome';").strip() == '2'
assert sql(good, "SELECT HEX(label) FROM system_dict_data WHERE value='graduated';").strip() == 'E7BB93E4B89A'
missing = fixture('_missing', False)
sql(missing, source, False); assert markers(missing) == ['0', '0']
partial = fixture('_partial')
sql(partial, f'CREATE TABLE zsjos_class_exam_progress LIKE {good}.zsjos_class_exam_progress;')
sql(partial, source); assert markers(partial) == ['1', '1']
failure = fixture('_failure')
sql(failure, "CREATE TRIGGER reject_marker BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';")
sql(failure, source, False); assert markers(failure) == ['0', '0']
assert sql(failure, 'SELECT COUNT(*) FROM system_dict_data;').strip() == '0'
sql(failure, 'DROP TRIGGER reject_marker;')
sql(failure, source); assert markers(failure) == ['1', '1']
print('PASS initial/repeat/prerequisite/partial/ledger-failure/recovery/UTF8; retained databases: ' + PREFIX)
