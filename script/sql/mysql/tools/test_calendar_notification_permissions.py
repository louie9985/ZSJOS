# -*- coding: utf-8 -*-
"""V282 metadata checks; retained MySQL 8 fixtures, no grants or business writes.

--fresh verifies the complete baseline and pending migration chain.
--apply-dev applies only the verified metadata block to local ruoyi-vue-pro after
explicit scoped authorization; never updates ledgers, roles or tenant packages.
"""
import datetime
import hashlib
import json
import re
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import (query, table, menu_setup, menu_signature,
    verify_menus, versions, signature, MIGRATION, ROOT, SQL, MENU_IDS)

# Historical migrations include DATABASE() in 64-character MySQL advisory-lock names.
PREFIX = 'cp_' + datetime.datetime.now().strftime('%m%d%H%M%S')
BLOCK = MIGRATION.split('-- BEGIN CALENDAR NOTIFICATION PERMISSIONS', 1)[1].split(
    '-- END CALENDAR NOTIFICATION PERMISSIONS', 1)[0]
CORRECTION = '''SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_calendar_permissions_correct;
DELIMITER $$
CREATE PROCEDURE zsjos_calendar_permissions_correct()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
''' + BLOCK + '''
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_calendar_permissions_correct();
DROP PROCEDURE zsjos_calendar_permissions_correct;
'''


def guard_snapshot(database):
    # Hash complete rows without printing account/role/package data.
    parts = []
    for name, ordering in (('system_role_menu', 'id'), ('system_tenant_package', 'id'),
                           ('zsjos_schema_version', 'version'), ('zsjos_module_schema_version', 'module_code,version')):
        value = query(database, f'SELECT * FROM {name} ORDER BY {ordering};')[0]
        parts.append(hashlib.sha256(value.encode('utf-8')).hexdigest())
    value = query(database, f'SELECT * FROM system_menu WHERE id NOT IN ({MENU_IDS}) ORDER BY id;')[0]
    parts.append(hashlib.sha256(value.encode('utf-8')).hexdigest())
    return parts


def scoped():
    for case in ('initial', 'partial', 'parent_missing', 'id_conflict', 'permission_conflict',
                 'deleted_identity', 'ledger_failure'):
        database = PREFIX + '_' + case
        query(None, f'CREATE DATABASE {database} CHARACTER SET utf8mb4;')
        setup = menu_setup() + table('zsjos_schema_version') + table('zsjos_module_schema_version')
        setup += table('system_tenant_package')
        for name in ('zsjos_course_calendar_event', 'zsjos_exam_schedule'):
            setup += table(name)
        setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V281','fixture','fixture');"
        setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V281','fixture','fixture','fixture');"
        if case == 'partial':
            setup += "INSERT INTO system_menu(id,name,permission,type,sort,parent_id,status) VALUES (73613,'管理员自定义','zsjos:exam-calendar:notify',3,99,73610,1);"
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V282','keep','original');"
            setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V282','keep','original','keep');"
        if case == 'parent_missing':
            setup += 'UPDATE system_menu SET deleted=1 WHERE id=73610;'
        if case in ('id_conflict', 'deleted_identity'):
            perm = 'fixture:unrelated' if case == 'id_conflict' else 'zsjos:exam-calendar:notify'
            deleted = 0 if case == 'id_conflict' else 1
            setup += f"INSERT INTO system_menu(id,name,permission,type,parent_id,deleted) VALUES (73613,'fixture','{perm}',3,73610,{deleted});"
        if case == 'permission_conflict':
            setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (99001,'fixture','zsjos:exam-calendar:notify',3,73610);"
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER reject_v282 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        query(database, setup)
        original = versions(database)
        before_menu = menu_signature(database)
        _, error = query(database, MIGRATION, force=True)
        if case not in ('initial', 'partial'):
            assert 'ERROR' in error and versions(database) == original and menu_signature(database) == before_menu, case
            if case == 'parent_missing':
                query(database, 'UPDATE system_menu SET deleted=0 WHERE id=73610;')
            elif case == 'id_conflict':
                query(database, 'UPDATE system_menu SET id=99002 WHERE id=73613;')
            elif case == 'permission_conflict':
                query(database, 'UPDATE system_menu SET id=73613 WHERE id=99001;')
            elif case == 'deleted_identity':
                query(database, 'UPDATE system_menu SET deleted=0 WHERE id=73613;')
            else:
                query(database, 'DROP TRIGGER reject_v282;')
            query(database, MIGRATION)
            assert len(versions(database).splitlines()) == 2
            print('PASS failure preserves menus/ledgers, then recovery:', case, flush=True)
        if case == 'initial':
            verify_menus(database)
        if case == 'partial':
            assert not error and versions(database) == original
            expected = '管理员自定义'.encode('utf-8').hex().upper()
            assert query(database, 'SELECT HEX(name),sort,status FROM system_menu WHERE id=73613;')[0].split(chr(9)) == [expected, '99', '1']
        assert query(database, 'SELECT COUNT(*) FROM system_role_menu;')[0] == '2'
        preserved = guard_snapshot(database)
        menu_before = menu_signature(database)
        query(database, MIGRATION)
        assert guard_snapshot(database) == preserved and menu_signature(database) == menu_before
        query(database, CORRECTION)
        assert guard_snapshot(database) == preserved and menu_signature(database) == menu_before
        print('PASS repeat/configuration/grant/checksum preservation:', case, flush=True)
    return PREFIX + '_initial'


def expand(path):
    source = path.read_text(encoding='utf-8-sig')
    return re.sub(r'^SOURCE\s+([^;]+);', lambda m: expand(ROOT / m.group(1)), source, flags=re.M | re.I)


def fresh():
    database = PREFIX + '_fresh'
    query(None, f'CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(database, expand(SQL / 'bootstrap.sql'))
    applied = set(query(database, 'SELECT version FROM zsjos_schema_version')[0].splitlines())
    count = 0
    for migration in sorted((SQL / 'migrations').glob('V*__*.sql')):
        if migration.name.split('__')[0] not in applied:
            query(database, expand(migration))
            count += 1
    verify_menus(database)
    assert query(database, f'SELECT COUNT(*) FROM system_role_menu WHERE menu_id IN ({MENU_IDS});')[0] == '0'
    output = query(database, expand(SQL / 'verify/core.sql'))[0]
    failures = [line for line in output.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)]
    # Existing baseline integrity warnings are outside this permission-only change; retain them as evidence.
    unexpected = [line for line in failures if 'calendar' in line.lower() or 'menu' in line.lower() and 'active menu' not in line]
    assert not unexpected, unexpected
    print('PASS complete fresh chain/menu verification:', count, 'migrations;', database,
          '; pre-existing core warnings:', len(failures), flush=True)
    return database


def apply_dev(expected_database):
    database = 'ruoyi-vue-pro'
    expected = menu_signature(expected_database)
    before = guard_snapshot(database)
    schema_before = signature(database)
    existing = menu_signature(database)
    assert existing == '', 'Local menu state changed; inspect before applying'
    backup = Path(tempfile.mkdtemp(prefix='calendar-permission-metadata-'))
    (backup / 'correction.sql').write_text(CORRECTION, encoding='utf-8')
    (backup / 'before.json').write_text(json.dumps({'menuRows': existing, 'guardHashes': before}, indent=2), encoding='utf-8')
    query(database, CORRECTION)
    verify_menus(database)
    assert menu_signature(database) == expected
    assert guard_snapshot(database) == before and signature(database) == schema_before
    query(database, CORRECTION)
    assert guard_snapshot(database) == before and menu_signature(database) == expected
    (backup / 'verification.txt').write_text('PASS four inserts; repeat stable; roles/packages/unrelated menus/ledgers/schema unchanged; Chinese HEX correct.', encoding='utf-8')
    print('PASS local metadata equals controlled result; unrelated state preserved; evidence:', backup, flush=True)


if __name__ == '__main__':
    reference = scoped()
    if '--fresh' in sys.argv:
        reference = fresh()
    if '--apply-dev' in sys.argv:
        apply_dev(reference)
