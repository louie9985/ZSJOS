# -*- coding: utf-8 -*-
"""V280 scoped upgrade/replay checks in a disposable MySQL, never the development database."""
import re
import subprocess
import zsjos_db as db

schema = (db.ROOT / 'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8')
seed = (db.ROOT / 'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
migration = db.ROOT / 'script/sql/mysql/migrations/V280__media_student_service_period.sql'


def table(source, name):
    return re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', source).group(0)


def sql(container, text):
    result = subprocess.run(['docker', 'exec', '-i', container, 'mysql', '--default-character-set=utf8mb4',
        '-uroot', '-N', '-B', 'zsjos_test'], input=('SET NAMES utf8mb4;\n' + text).encode('utf-8'), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8'))
    return result.stdout.decode('utf-8').strip()


def check(container):
    person = re.sub(r'^.*`in_service_period`.*\n', '', table(schema, 'zsjos_person'), flags=re.M)
    sql(container, person + table(schema, 'system_menu') + table(schema, 'zsjos_module_schema_version')
        + table(seed, 'zsjos_schema_version') + '''
        INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V279','fixture','fixture');
        INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
          VALUES ('core','V279','fixture','fixture','fixture');
        INSERT INTO system_menu(id,name,permission,type) VALUES (10,'我的学员','zsjos:media-student:query-my',2);
        INSERT INTO zsjos_person(id,person_no,name,identity_status,first_seen_at,last_seen_at,tenant_id)
          VALUES (1,'TEST-1','验收甲','student',NOW(),NOW(),1),(2,'TEST-2','验收乙','student',NOW(),NOW(),2);
        ''')
    db.docker_mysql_file(container, migration)
    assert sql(container, "SELECT COUNT(*) FROM zsjos_person WHERE in_service_period=b'1'") == '2'
    sql(container, "UPDATE zsjos_person SET in_service_period=b'0' WHERE id=1 AND tenant_id=1")
    db.docker_mysql_file(container, migration)
    assert sql(container, "SELECT in_service_period+0 FROM zsjos_person WHERE id=1 AND tenant_id=1") == '0'
    assert sql(container, "SELECT in_service_period+0 FROM zsjos_person WHERE id=2 AND tenant_id=2") == '1'
    sql(container, "INSERT INTO zsjos_person(person_no,name,identity_status,first_seen_at,last_seen_at,tenant_id) VALUES ('TEST-3','验收丙','student',NOW(),NOW(),1)")
    assert sql(container, "SELECT COUNT(*) FROM zsjos_person WHERE tenant_id=1 AND in_service_period=b'1'") == '1'
    assert sql(container, "SELECT COUNT(*) FROM zsjos_person WHERE tenant_id=1 AND in_service_period=b'0'") == '1'
    assert sql(container, "SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-student:update-service-period' AND parent_id=10") == '1'
    assert sql(container, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:media-student:update-service-period'") == '调整学员服务期'.encode('utf-8').hex().upper()
    assert sql(container, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V280'") == '1'
    assert sql(container, "SELECT CONCAT(column_type,'|',is_nullable,'|',column_default,'|',HEX(column_comment)) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_person' AND column_name='in_service_period'") == "bit(1)|NO|b'1'|" + '是否在服务期（学员列表人工归类）'.encode('utf-8').hex().upper()
    print('PASS: cloud V279 prerequisite, old/new defaults, false survives replay, tenant-scoped values, menu parent/uniqueness/UTF-8 and ledgers')


if __name__ == '__main__':
    db.with_test_mysql(check)
