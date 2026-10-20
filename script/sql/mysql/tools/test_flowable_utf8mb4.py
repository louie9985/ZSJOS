# -*- coding: utf-8 -*-
"""Retained MySQL integration fixtures for V331; optional authorized local apply.

Never discovers or writes remote targets. Credentials stay inside the existing local
connection helper. --apply-local requires that the local backend has been stopped.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

from order_history_repair import connect

ROOT = Path(__file__).resolve().parents[4]
PATH = ROOT / 'script/sql/mysql/migrations/V331__flowable_utf8mb4.sql'
SQL = PATH.read_text(encoding='utf-8')
LOCAL = 'ruoyi-vue-pro'
STAMP = dt.datetime.now().strftime('%Y%m%d%H%M%S%f')
TABLES = re.findall(r"\('((?:ACT_|FLW_)[A-Z_]+)'\)", SQL)
assert len(TABLES) == 41
NAMES = ','.join("'" + table + "'" for table in TABLES)


def query(database, sql, expect_success=True, force=False):
    command = ['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 '
               '-uroot --batch --raw --skip-column-names ' + ('--force ' if force else '') + '"' + database + '"']
    result = subprocess.run(command, input=('SET NAMES utf8mb4;\n' + sql).encode('utf-8'), capture_output=True)
    if expect_success and result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result


def scalar(database, sql):
    return int(query(database, sql).stdout.decode().strip())


def ledgers(database):
    return query(database, "SELECT version,checksum FROM zsjos_schema_version ORDER BY version;"
                 "SELECT module_code,version,checksum,release_version FROM zsjos_module_schema_version ORDER BY module_code,version;").stdout


def signature(database):
    conn = connect(database=database)
    try:
        with conn.cursor() as cur:
            schema = []
            for sql in [
                f"SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLUMN_COMMENT,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ({NAMES}) ORDER BY TABLE_NAME,ORDINAL_POSITION",
                f"SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE,IS_VISIBLE,EXPRESSION,COLLATION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ({NAMES}) ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX",
                f"SELECT k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION,k.COLUMN_NAME,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME IN ({NAMES}) ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION",
            ]:
                cur.execute(sql)
                schema.append(cur.fetchall())
            data = {}
            available = {row['TABLE_NAME'] for row in schema[0]}
            for table in TABLES:
                if table not in available:
                    continue
                cur.execute(f'SELECT * FROM `{table}`')
                # Hash bytes as well as decoded text; include all fields, NULLs, case and BLOBs.
                rows = [json.dumps(row, sort_keys=True, ensure_ascii=False,
                                   default=lambda value: value.hex() if isinstance(value, bytes) else str(value))
                        for row in cur.fetchall()]
                data[table] = (len(rows), hashlib.sha256('\n'.join(sorted(rows)).encode('utf-8')).hexdigest())
            return schema, data
    finally:
        conn.close()


def fixture(suffix, old=True):
    database = 'flowable_v331_' + STAMP + '_' + suffix
    query(LOCAL, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;')
    text = (ROOT / 'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8')
    creates = []
    for table in TABLES:
        match = re.search(r'CREATE TABLE IF NOT EXISTS `' + table + r'` \([\s\S]*?\n\) ENGINE=[^\n]*;', text)
        assert match, table
        ddl = match.group()
        if old:
            ddl = ddl.replace('utf8mb4_bin', 'utf8mb3_bin').replace('CHARSET=utf8mb4', 'CHARSET=utf8mb3')
        creates.append(ddl)
    conn = connect()
    try:
        with conn.cursor() as cur:
            for table in ['zsjos_schema_version', 'zsjos_module_schema_version']:
                cur.execute(f'SHOW CREATE TABLE `{table}`')
                creates.append(cur.fetchone()['Create Table'])
    finally:
        conn.close()
    query(database, 'SET FOREIGN_KEY_CHECKS=0;\n' + ';\n'.join(creates) + ';\nSET FOREIGN_KEY_CHECKS=1;')
    query(database, """
INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V330','fixture prerequisite','fixture');
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V330','fixture prerequisite','fixture','fixture');
INSERT INTO ACT_RE_DEPLOYMENT(ID_,NAME_,TENANT_ID_) VALUES ('deploy-1','历史审批','1');
INSERT INTO ACT_GE_BYTEARRAY(ID_,NAME_,DEPLOYMENT_ID_,BYTES_) VALUES ('bytes-1','历史资源','deploy-1',UNHEX('E4B8ADE69687'));
INSERT INTO ACT_RE_PROCDEF(ID_,KEY_,VERSION_,NAME_,DEPLOYMENT_ID_,TENANT_ID_) VALUES ('proc-1','fixture',1,'流程名称','deploy-1','1');
INSERT INTO ACT_RE_MODEL(ID_,REV_,NAME_,META_INFO_,DEPLOYMENT_ID_,EDITOR_SOURCE_VALUE_ID_,TENANT_ID_) VALUES ('model-1',1,'模型名称','{"label":"中文"}','deploy-1','bytes-1','1');
INSERT INTO ACT_RU_EXECUTION(ID_,REV_,PROC_INST_ID_,PROC_DEF_ID_,TENANT_ID_,NAME_) VALUES ('exec-1',1,'exec-1','proc-1','1','流程实例');
INSERT INTO ACT_RU_TASK(ID_,REV_,EXECUTION_ID_,PROC_INST_ID_,PROC_DEF_ID_,NAME_,DESCRIPTION_,TENANT_ID_) VALUES ('task-1',1,'exec-1','exec-1','proc-1','审批任务','历史说明','1');
INSERT INTO ACT_RU_VARIABLE(ID_,REV_,TYPE_,NAME_,EXECUTION_ID_,PROC_INST_ID_,TEXT_,TEXT2_) VALUES ('var-1',1,'string','姓名','exec-1','exec-1','中文','备注');
INSERT INTO ACT_HI_COMMENT(ID_,TIME_,TYPE_,TASK_ID_,MESSAGE_,FULL_MSG_) VALUES ('comment-1',NOW(3),'comment','task-1','历史审批意见',UNHEX('E4B8ADE69687'));
INSERT INTO ACT_ID_GROUP(ID_,NAME_) VALUES ('case-A','组一'),('case-a','组二');
INSERT INTO FLW_RU_BATCH(ID_,TYPE_,CREATE_TIME_,TENANT_ID_) VALUES ('batch-1','fixture',NOW(3),'1');
INSERT INTO FLW_RU_BATCH_PART(ID_,BATCH_ID_,TYPE_,CREATE_TIME_) VALUES ('part-1','batch-1','fixture',NOW(3));
""")
    print('FIXTURE', database, flush=True)
    return database


def converted(database):
    assert scalar(database, f"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ({NAMES}) AND TABLE_COLLATION='utf8mb4_bin'") == 41
    assert scalar(database, f"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ({NAMES}) AND CHARACTER_SET_NAME IS NOT NULL AND COLLATION_NAME<>'utf8mb4_bin'") == 0
    assert scalar(database, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V331'") == 1
    assert scalar(database, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V331'") == 1


def verify(database, four_byte=True):
    before = signature(database)
    query(database, SQL)
    assert signature(database) == before, 'column/index/FK/row content changed'
    converted(database)
    saved = ledgers(database)
    query(database, SQL)
    assert ledgers(database) == saved
    assert signature(database) == before
    if four_byte:
        query(database, "UPDATE ACT_RU_VARIABLE SET TEXT_='中文😀𠮷' WHERE ID_='var-1'; UPDATE ACT_HI_COMMENT SET MESSAGE_='审批😀𠮷' WHERE ID_='comment-1';")
        actual = query(database, "SELECT HEX(TEXT_) FROM ACT_RU_VARIABLE WHERE ID_='var-1';").stdout.decode().strip()
        assert actual == '中文😀𠮷'.encode().hex().upper()
        assert query(database, "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V331';").stdout.decode().strip() == 'Flowable UTF-8 四字节字符支持'.encode().hex().upper()
        bad = query(database, "INSERT INTO ACT_RU_VARIABLE(ID_,REV_,TYPE_,NAME_,EXECUTION_ID_) VALUES ('invalid',1,'string','invalid','missing');", expect_success=False)
        assert bad.returncode and b'foreign key constraint fails' in bad.stderr, bad.stderr.decode('utf-8')
    print('PASS first/replay/definitions/data/UTF-8/FK enforcement', database, flush=True)


def failure(database, sql=SQL, force=False):
    before = ledgers(database)
    result = query(database, sql, expect_success=False, force=force)
    assert b'ERROR' in result.stderr, result.stdout
    assert ledgers(database) == before, 'failure advanced a ledger'
    return result


def run_preflight_tests():
    wide = fixture('wide_index')
    query(wide, 'CREATE INDEX fixture_wide ON ACT_HI_ENTITYLINK(SCOPE_ID_,SCOPE_TYPE_,LINK_TYPE_,PARENT_ELEMENT_ID_(40));')
    before = signature(wide)
    result = failure(wide)
    assert b'exceeds 3072' in result.stderr
    assert signature(wide) == before
    print('PASS utf8mb4 index overflow rejected before DDL', flush=True)

    external = fixture('external_fk')
    query(external, 'CREATE TABLE fixture_external(id BIGINT PRIMARY KEY, prop VARCHAR(64), FOREIGN KEY(prop) REFERENCES ACT_GE_PROPERTY(NAME_)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;')
    before = signature(external)
    result = failure(external)
    assert b'crossing the reviewed table scope' in result.stderr
    assert signature(external) == before
    print('PASS out-of-scope foreign key rejected before DDL', flush=True)


def run_tests():
    run_preflight_tests()
    verify(fixture('upgrade'))
    verify(fixture('fresh', old=False))
    missing = fixture('missing')
    query(missing, 'RENAME TABLE ACT_EVT_LOG TO unrelated_event_log;')
    before = signature(missing)
    failure(missing)
    assert signature(missing) == before
    print('PASS missing-table preflight has no DDL/ledger effect', flush=True)

    incompatible = fixture('collation')
    query(incompatible, 'ALTER TABLE ACT_EVT_LOG CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    before = signature(incompatible)
    failure(incompatible)
    assert signature(incompatible) == before
    print('PASS unexpected collation rejected before DDL', flush=True)

    partial = fixture('partial')
    anchor = '        IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()'
    assert SQL.count(anchor) == 1
    interrupted = SQL.replace(anchor, "        IF v_table='ACT_HI_COMMENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture DDL interruption'; END IF;\n" + anchor)
    result = failure(partial, interrupted + '\nSELECT CONCAT(\'SESSION_RESTORED=\',@@foreign_key_checks);', force=True)
    assert b'SESSION_RESTORED=1' in result.stdout
    mb4 = scalar(partial, f"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ({NAMES}) AND TABLE_COLLATION='utf8mb4_bin'")
    assert 0 < mb4 < 41
    verify(partial)
    print('PASS partial-DDL recovery and session restoration', flush=True)

    failed = fixture('ledger')
    query(failed, "CREATE TRIGGER reject_v331 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure';")
    failure(failed)
    assert scalar(failed, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V331'") == 0
    query(failed, 'DROP TRIGGER reject_v331;')
    verify(failed)
    print('PASS atomic ledger failure/recovery', flush=True)

    existing = fixture('marker')
    query(existing, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V331','existing fixture evidence','unchanged'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V331','existing fixture evidence','unchanged','fixture');")
    saved = ledgers(existing)
    failure(existing, interrupted)
    query(existing, SQL)
    converted(existing)
    assert ledgers(existing) == saved
    print('PASS existing marker never skips repair or rewrites checksum', flush=True)

    orphan = fixture('orphan')
    query(orphan, "SET FOREIGN_KEY_CHECKS=0; UPDATE ACT_RU_VARIABLE SET EXECUTION_ID_='missing' WHERE ID_='var-1'; SET FOREIGN_KEY_CHECKS=1;")
    result = failure(orphan)
    assert b'orphaned foreign key' in result.stderr
    query(orphan, "UPDATE ACT_RU_VARIABLE SET EXECUTION_ID_='exec-1' WHERE ID_='var-1';")
    verify(orphan)
    print('PASS orphan validation blocks success', flush=True)

    locked = fixture('lock')
    conn = connect(database=locked)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT GET_LOCK(CONCAT('zsjos:v331:',LEFT(SHA2(DATABASE(),256),40)),0)")
            failure(locked)
    finally:
        conn.close()
    verify(locked)
    print('PASS concurrent migration lock', flush=True)

    killed = fixture('disconnect')
    slow = SQL.replace(anchor, "        IF v_table='ACT_HI_COMMENT' THEN DO SLEEP(30); END IF;\n" + anchor)
    command = ['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot "' + killed + '"']
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    process.stdin.write(slow.encode('utf-8'))
    process.stdin.close()
    control = connect(database=killed)
    try:
        deadline = time.monotonic() + 20
        worker = None
        while time.monotonic() < deadline:
            with control.cursor() as cur:
                cur.execute("SELECT ID FROM information_schema.PROCESSLIST WHERE DB=%s AND ID<>CONNECTION_ID() AND COMMAND='Query' AND STATE='User sleep'", (killed,))
                worker = cur.fetchone()
            if worker:
                break
            time.sleep(0.1)
        assert worker, 'interruption fixture did not reach controlled pause'
        with control.cursor() as cur:
            cur.execute('KILL CONNECTION ' + str(int(worker['ID'])))
        assert process.wait(timeout=10) != 0
    finally:
        control.close()
        if process.poll() is None:
            process.terminate()
    verify(killed)
    print('PASS actual connection-loss recovery, FKs retained', flush=True)


def apply_local():
    connections = query(LOCAL, "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID() AND COMMAND<>'Sleep';").stdout.decode().strip()
    assert connections == '0', 'local database has active queries; stop writers before applying'
    backup_dir = ROOT / 'backups/mysql'
    backup_dir.mkdir(parents=True, exist_ok=True)
    backup = backup_dir / ('ruoyi-vue-pro_flowable_before_v331_' + STAMP + '.sql')
    command = ['docker', 'exec', 'yudao-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot '
               '--single-transaction --hex-blob --set-gtid-purged=OFF --no-tablespaces '
               '--skip-add-drop-table --skip-add-locks --skip-lock-tables "ruoyi-vue-pro" '
               + ' '.join('"' + table + '"' for table in TABLES + ['zsjos_schema_version', 'zsjos_module_schema_version'])]
    with backup.open('xb') as stream:
        subprocess.run(command, stdout=stream, stderr=subprocess.PIPE, check=True)
    before = signature(LOCAL)
    prior = ledgers(LOCAL)
    # Restore the exact private backup into a new database before touching the target.
    restored = 'flowable_v331_restore_' + STAMP
    query(LOCAL, f'CREATE DATABASE `{restored}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;')
    query(restored, backup.read_text(encoding='utf-8'))
    assert signature(restored) == before, 'backup restore does not reproduce the target'
    assert ledgers(restored) == prior
    query(LOCAL, SQL)
    converted(LOCAL)
    assert signature(LOCAL) == before
    saved = ledgers(LOCAL)
    query(LOCAL, SQL)
    assert signature(LOCAL) == before
    new = ledgers(LOCAL)
    assert new == saved, 'replay changed a ledger'
    assert set(prior.splitlines()) <= set(new.splitlines()), 'existing ledger evidence changed'
    evidence = {
        'database': LOCAL, 'backup': str(backup), 'backup_bytes': backup.stat().st_size,
        'backup_sha256': hashlib.sha256(backup.read_bytes()).hexdigest(),
        'migration_sha256': hashlib.sha256(PATH.read_bytes()).hexdigest(),
        'table_counts_and_hashes': before[1], 'definitions_and_data_preserved': True,
        'backup_restored_and_verified_in': restored,
        'replay_preserved_ledgers': new == saved,
        'previous_ledger_hash': hashlib.sha256(prior).hexdigest(),
    }
    evidence_path = backup.with_suffix('.verification.json')
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS local conversion/replay/definitions/data preservation', LOCAL)
    print('BACKUP', backup)
    print('EVIDENCE', evidence_path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply-local', action='store_true', help='Apply to local Docker only; stop backend first')
    args = parser.parse_args()
    if args.apply_local:
        apply_local()
    else:
        run_tests()
