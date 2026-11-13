"""UTF-8. Controlled V369 verification with retained synthetic databases."""
from pathlib import Path
import subprocess
import uuid
import sys

ROOT = Path(__file__).resolve().parents[4]
PREFIX = 'verify_version_updates_' + uuid.uuid4().hex[:8]
def sql(database, source, success=True):
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names --skip-force ' + database],
        input=source.encode('utf-8'), capture_output=True)
    assert (result.returncode == 0) == success, result.stderr.decode('utf-8', errors='replace')
    return result.stdout.decode('utf-8').strip()
def fixture(suffix, prerequisite=True):
    name = PREFIX + suffix
    sql('', f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4;')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version', 'system_menu']:
        sql(name, f'CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};')
    sql(name, "INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,status,deleted) VALUES(1,'Workbench','',1,1,0,'/zsjos',0,0);")
    if prerequisite:
        sql(name, "INSERT INTO zsjos_schema_version(version,description) VALUES('V368','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V368','fixture','fixture','fixture');")
    return name
def markers(name):
    return sql(name, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V369'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V369';").splitlines()
source = (ROOT / 'script/sql/mysql/migrations/V369__version_updates.sql').read_text(encoding='utf-8')
good = fixture('_repeat')
sql(good, source); sql(good, source)
assert markers(good) == ['1','1']
assert sql(good, "SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:version-update:%'") == '7'
assert sql(good, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:version-update:read'") == 'E69BB4E696B0E8AEB0E5BD95'
missing = fixture('_missing', False)
sql(missing, source, False); assert markers(missing) == ['0','0']
partial = fixture('_partial')
sql(partial, f'CREATE TABLE zsjos_version_update LIKE {good}.zsjos_version_update;')
sql(partial, source); assert markers(partial) == ['1','1']
failure = fixture('_failure')
sql(failure, "CREATE TRIGGER reject_marker BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure';")
sql(failure, source, False); assert markers(failure) == ['0','0']
assert sql(failure,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:version-update:%'") == '0'
sql(failure, 'DROP TRIGGER reject_marker;'); sql(failure, source)
assert markers(failure) == ['1','1']
conflict = fixture('_conflict')
sql(conflict, "INSERT INTO system_menu(name,permission,type,sort,parent_id,path,status,deleted) VALUES('conflict','zsjos:version-update:query',2,1,1,'wrong',0,0);")
sql(conflict, source, False); assert markers(conflict) == ['0','0']
# Prove the unique recipient relationship and conditional first acknowledgement.
sql(good, "SET NAMES utf8mb4; INSERT INTO zsjos_version_update(title,content,status,target_user_ids,attachment_ids,inline_file_ids,tenant_id) VALUES('合成版本更新','正文','PUBLISHED','[9]','[]','[]',1); INSERT INTO zsjos_version_update_recipient(update_id,user_id,user_name,tenant_id) VALUES(1,9,'合成人员',1);")
sql(good, "INSERT INTO zsjos_version_update_recipient(update_id,user_id,user_name,tenant_id) VALUES(1,9,'duplicate',1)",False)
sql(good, "UPDATE zsjos_version_update_recipient SET acknowledged_time='2026-11-11 12:00:00' WHERE update_id=1 AND user_id=9 AND tenant_id=1 AND acknowledged_time IS NULL; UPDATE zsjos_version_update_recipient SET acknowledged_time='2026-11-11 13:00:00' WHERE update_id=1 AND user_id=9 AND tenant_id=1 AND acknowledged_time IS NULL;")
assert sql(good,"SELECT acknowledged_time FROM zsjos_version_update_recipient WHERE tenant_id=1 AND user_id=9") == '2026-11-11 12:00:00'
print('PASS initial/repeat/prerequisite/partial/ledger-failure/recovery/menu-conflict/UTF8/unique-recipient/first-ack; retained '+PREFIX)
if '--apply-local' in sys.argv:
    target='ruoyi-vue-pro'
    assert sql(target,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V368'") == '1'
    before=sql(target,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,role_id,menu_id))),0) FROM system_role_menu")
    sql(target,source); sql(target,source)
    assert markers(target)==['1','1']
    assert sql(target,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,role_id,menu_id))),0) FROM system_role_menu")==before
    assert sql(target,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:version-update:read'")=='E69BB4E696B0E8AEB0E5BD95'
    metadata="SELECT table_name,column_name,column_type,is_nullable,COALESCE(character_set_name,'') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_version_update','zsjos_version_update_recipient') ORDER BY table_name,ordinal_position"
    assert sql(target,metadata)==sql(good,metadata)
    print('PASS local ruoyi-vue-pro apply/repeat/ledgers/schema/UTF8/role-grant-preservation')
