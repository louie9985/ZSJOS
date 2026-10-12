# -*- coding: utf-8 -*-
"""Controlled MySQL checks. Only reads source DB; writes uniquely named test databases."""
import datetime
import json
import uuid
import pymysql
from order_history_repair import connect, ident
from lead_provider_snapshot_repair import run, audit, KEY

TABLES = ['zsjos_lead', 'zsjos_partner', 'zsjos_data_repair_backup', 'zsjos_schema_version', 'zsjos_module_schema_version']


def clone(empty=False):
    name = 'v307_test_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '_' + uuid.uuid4().hex[:6]
    source = connect()
    with source.cursor() as q:
        q.execute('CREATE DATABASE ' + ident(name) + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        for table in TABLES:
            target = ident(name) + '.' + ident(table)
            q.execute('CREATE TABLE ' + target + ' LIKE ' + ident(table))
            if table != 'zsjos_data_repair_backup' and (not empty or 'schema_version' in table):
                q.execute('SHOW COLUMNS FROM ' + ident(table))
                columns = ','.join(ident(r['Field']) for r in q.fetchall() if 'GENERATED' not in r['Extra'])
                q.execute('INSERT INTO ' + target + ' (' + columns + ') SELECT ' + columns + ' FROM ' + ident(table))
    source.close()
    return name, connect(database=name)


def rows(c):
    with c.cursor() as q:
        q.execute('SELECT * FROM zsjos_lead ORDER BY id')
        return {r['id']: r for r in q.fetchall()}


def fail(c):
    try:
        run(c)
    except pymysql.MySQLError:
        return
    raise AssertionError('Expected SQL rejection')


def backups(c):
    with c.cursor() as q:
        q.execute('SELECT * FROM zsjos_data_repair_backup WHERE repair_key=%s ORDER BY id', (KEY,))
        return q.fetchall()


def restore_before(c, limit=None):
    with c.cursor() as q:
        q.execute("SELECT row_id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND column_name='__before__' ORDER BY row_id", (KEY,))
        saved = q.fetchall()
        for r in saved[:limit]:
            old = json.loads(r['old_value'])
            q.execute('UPDATE zsjos_lead SET ' + ','.join(ident(k)+'=%s' for k in old) + ' WHERE id=%s', tuple(old.values())+(r['row_id'],))


def main():
    name, c = clone()
    print('isolated_database', name)
    before = rows(c)
    with c.cursor() as q:
        q.execute("UPDATE zsjos_schema_version SET version='V306_TEST' WHERE version='V306'")
    fail(c)
    assert rows(c) == before and audit(c)['globalLedger'] == 0
    with c.cursor() as q:
        q.execute("UPDATE zsjos_schema_version SET version='V306' WHERE version='V306_TEST'")
        q.execute("CREATE TRIGGER reject_v307 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure'")
    fail(c)
    assert rows(c) == before and not backups(c) and audit(c)['globalLedger'] == 0
    with c.cursor() as q:
        q.execute('DROP TRIGGER reject_v307')
    run(c)
    after = rows(c)
    changed = [rid for rid in before if before[rid] != after[rid]]
    assert len(changed) == 496
    allowed = {'provider_owner_type','provider_owner_id','provider_owner_name_snapshot','source_provider_recorded','version','updater','update_time'}
    for rid in changed:
        assert all(before[rid][k] == after[rid][k] for k in before[rid] if k not in allowed)
        assert after[rid]['version'] == before[rid]['version'] + 1
    partner = [rid for rid in changed if before[rid]['provider_owner_type'] == 'partner']
    assert len(partner) == 492
    for rid in partner:
        assert before[rid]['provider_owner_id'] == after[rid]['provider_owner_id'] and after[rid]['provider_owner_name_snapshot']
    for rid in set(changed)-set(partner):
        assert after[rid]['provider_owner_type'] is None and after[rid]['provider_owner_id'] is None
        assert after[rid]['provider_owner_name_snapshot'] is None and after[rid]['source_provider_recorded'] in (1, b'\x01')
    with c.cursor() as q:
        q.execute("SELECT l.provider_owner_name_snapshot name,HEX(l.provider_owner_name_snapshot) hex_value FROM zsjos_lead l JOIN zsjos_data_repair_backup b ON b.row_id=l.id AND b.repair_key=%s AND b.column_name='__after__' WHERE l.provider_owner_name_snapshot IS NOT NULL", (KEY,))
        for r in q.fetchall():
            assert r['name'].encode('utf-8').hex().upper() == r['hex_value']
    saved = backups(c)
    assert len(saved) == 1488
    run(c)
    assert rows(c) == after and backups(c) == saved
    with c.cursor() as q:
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key='v307_test_hidden' WHERE repair_key=%s AND column_name='__provenance__' LIMIT 1", (KEY,))
    fail(c)
    assert rows(c) == after
    with c.cursor() as q:
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key=%s WHERE repair_key='v307_test_hidden'", (KEY,))
    restore_before(c, 3)
    run(c)
    assert rows(c) == after and backups(c) == saved
    with c.cursor() as q:
        q.execute('UPDATE zsjos_lead SET version=version+1 WHERE id=%s', (changed[0],))
    later = rows(c)
    fail(c)
    assert rows(c) == later
    with c.cursor() as q:
        q.execute('UPDATE zsjos_lead SET version=%s,update_time=%s WHERE id=%s', (after[changed[0]]['version'], after[changed[0]]['update_time'],changed[0]))
    c.begin()
    restore_before(c)
    assert rows(c) == before
    c.rollback()
    assert rows(c) == after
    print('PASS prerequisite rejection, ledger rollback, scoped initial apply, UTF-8 HEX, repeat, partial recovery with ledgers, later-edit rejection, rollback rehearsal', audit(c))
    c.close()
    empty_name, empty = clone(empty=True)
    run(empty); run(empty)
    assert audit(empty) == {'total':0,'named':0,'unnamed':0,'backupRows':0,'globalLedger':1,'moduleLedger':1}
    empty.close()
    print('PASS empty repeat', empty_name)


if __name__ == '__main__':
    main()
