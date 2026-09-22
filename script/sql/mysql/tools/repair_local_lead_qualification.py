"""UTF-8. One-off authorized local tenant-1 repair; never a versioned migration.

Run with --backup-dir ABSOLUTE_PATH, then repeat with the same directory and --apply.
Scope is frozen in the private backup, not rediscovered during application. Requires
the existing local Docker MySQL and installed PyMySQL. No dependency installation.
Full original rows are retained outside Git as pickle (trusted local backup only).
Recovery requires review of subsequent lifecycle changes; never restore wholesale
or delete generated audit history automatically. Temporary-table rehearsal precedes
live writes. Missing/partial/conflicting history aborts without overwriting it.
"""
import argparse
import datetime as dt
import hashlib
import json
import pickle
import subprocess
from pathlib import Path

import pymysql

DB = 'ruoyi-vue-pro'
LEAD = 'zsjos_lead'
TASK = 'zsjos_business_task'
EVENT = 'zsjos_business_event'
RULE = 'zsjos_lead_follow_up_rule'
FOLLOW = 'zsjos_lead_follow_up_record'
HISTORY = 'zsjos_lead_assignment_history'
PREDICATE = "tenant_id=1 AND deleted=0 AND status='submitted' AND assignment_status='owned' AND current_assignment_first_follow_up_at IS NOT NULL AND qualification_deadline_at IS NULL"
MARKER = 'local-qualification-repair'


def connect():
    data = json.loads(subprocess.check_output(['docker', 'inspect', 'yudao-mysql']))[0]
    env = dict(x.split('=', 1) for x in data['Config']['Env'] if '=' in x)
    assert env.get('MYSQL_DATABASE', DB) == DB
    port = int(data['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort'])
    conn = pymysql.connect(host='127.0.0.1', port=port, user='root',
                           password=env['MYSQL_ROOT_PASSWORD'], database=DB,
                           charset='utf8mb4', autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)
    with conn.cursor() as c:
        c.execute('SET NAMES utf8mb4')
    return conn


def rows(conn, sql, args=()):
    with conn.cursor() as c:
        c.execute(sql, args)
        return c.fetchall()


def execute(conn, sql, args=()):
    with conn.cursor() as c:
        c.execute(sql, args)
        return c.rowcount


def scoped(conn, table, key, ids, lock=False):
    return rows(conn, f'SELECT * FROM {table} WHERE tenant_id=1 AND {key} IN (' +
                ','.join(['%s'] * len(ids)) + ') ORDER BY id' + (' FOR UPDATE' if lock else ''), ids) if ids else []


def rule(conn, lock=False):
    found = rows(conn, f"SELECT * FROM {RULE} WHERE tenant_id=1 AND code='default' AND status=0 AND deleted=0" + (' FOR UPDATE' if lock else ''))
    assert len(found) == 1 and found[0]['qualification_timeout_minutes'] > 0, 'Default rule absent/ambiguous/invalid'
    return found[0]


def digest(value):
    return hashlib.sha256(pickle.dumps(value)).hexdigest()


def ledgers(conn):
    return [rows(conn, 'SELECT * FROM zsjos_schema_version ORDER BY version'),
            rows(conn, 'SELECT * FROM zsjos_module_schema_version ORDER BY module_code,version')]


def audit_original(conn, leads):
    for lead in leads:
        assert lead['owner_user_id'] and lead['ownership_started_at'] and lead['lead_no'], 'Missing owner/start/business number'
        assert not lead['qualification_started_at'] and not lead['qualification_rule_snapshot'], 'Partial qualification fields require review'
        assert not lead['qualification_round_no'], 'Historical round requires review'
        assert rows(conn, 'SELECT id FROM system_users WHERE id=%s AND tenant_id=1 AND deleted=0', (lead['owner_user_id'],)), 'Owner missing'
        assert rows(conn, f'''SELECT id FROM {FOLLOW} WHERE lead_id=%s AND tenant_id=1 AND deleted=0
            AND owner_user_id_snapshot=%s AND occurred_at=%s AND occurred_at>=%s''',
            (lead['id'], lead['owner_user_id'], lead['current_assignment_first_follow_up_at'], lead['ownership_started_at'])), 'Actual first follow-up missing'
        # Imported history may have action=other and no to-owner. Preserve it; the
        # current Lead ownership and matching follow-up owner are the repair source.
        assert rows(conn, f'SELECT id FROM {HISTORY} WHERE lead_id=%s AND tenant_id=1 AND deleted=0', (lead['id'],)), 'Assignment history missing'


def expected(lead, r):
    n = (lead['qualification_round_no'] or 0) + 1
    start = lead['ownership_started_at']
    due = start + dt.timedelta(minutes=r['qualification_timeout_minutes'])
    payload = dict(roundNo=n, ruleId=r['id'], ruleVersion=r['version'] or 0,
                   timeoutMinutes=r['qualification_timeout_minutes'])
    snapshot = dict(payload)
    del snapshot['roundNo']
    snapshot['startedAt'] = start.isoformat()
    return n, start, due, payload, snapshot


def verify(conn, backup):
    for original in backup[LEAD]:
        lead = scoped(conn, LEAD, 'id', [original['id']])[0]
        n, start, due, payload, snapshot = expected(original, backup[RULE][0])
        for field in ('owner_user_id', 'ownership_started_at', 'current_assignment_history_id', 'current_assignment_first_follow_up_at'):
            assert lead[field] == original[field], 'Ownership/first-follow-up changed'
        assert (lead['qualification_round_no'], lead['qualification_started_at'], lead['qualification_deadline_at']) == (n, start, due)
        assert json.loads(lead['qualification_rule_snapshot']) == snapshot
        task = rows(conn, f'SELECT *,HEX(title_snapshot) AS title_hex FROM {TASK} WHERE tenant_id=1 AND idempotency_key=%s', (f"lead-qualification:{lead['id']}:{n}",))
        event = rows(conn, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s', (f"lead-qualification-started:{lead['id']}:{n}",))
        assert len(task) == len(event) == 1
        t, e = task[0], event[0]
        assert t['task_type']=='lead_qualification' and t['biz_type']=='lead' and t['biz_id']==lead['id'] and t['deleted']==b'\x00'
        assert t['assignee_type']=='user' and t['assignee_id']==lead['owner_user_id'] and t['due_at']==due
        assert json.loads(t['payload'])==payload and t['action_code']=='OPEN_LEAD_FOLLOW_UP'
        assert t['title_hex']==('有效性判定：'+lead['lead_no']).encode('utf-8').hex().upper()
        assert e['event_type']=='lead_qualification_started' and e['aggregate_type']=='lead' and e['aggregate_id']==lead['id'] and e['deleted']==b'\x00'
        assert e['operator_user_id']==lead['owner_user_id'] and e['occurred_at']==start
        assert e['from_status']==e['to_status']=='submitted'
        assert json.loads(e['related_object_refs'])==dict(roundNo=n,dueAt=due.isoformat())
    for table, key in ((FOLLOW,'lead_id'),(HISTORY,'lead_id')):
        assert scoped(conn,table,key,backup['ids'])==backup[table], 'Existing history changed'


def repair(conn, backup):
    conn.begin()
    try:
        assert rule(conn, True)==backup[RULE][0], 'Rule changed since backup'
        current = scoped(conn, LEAD, 'id', backup['ids'], True)
        missing = [x for x in current if x['qualification_deadline_at'] is None]
        if not missing:
            verify(conn, backup)
            conn.rollback()
            return 0
        assert current==backup[LEAD], 'Fixed scope changed since backup'
        audit_original(conn,current)
        for table,key in ((TASK,'biz_id'),(EVENT,'aggregate_id'),(FOLLOW,'lead_id'),(HISTORY,'lead_id')):
            assert scoped(conn,table,key,backup['ids'],True)==backup[table], 'History changed since backup'
        assert not any(t['task_type']=='lead_qualification' for t in backup[TASK]), 'Existing task requires review'
        assert not any(e['event_type']=='lead_qualification_started' for e in backup[EVENT]), 'Existing event requires review'
        for lead in current:
            n,start,due,payload,snapshot=expected(lead,backup[RULE][0])
            assert execute(conn,f'''UPDATE {LEAD} SET qualification_round_no=%s,qualification_started_at=%s,
                qualification_deadline_at=%s,qualification_rule_snapshot=%s,update_time=update_time
                WHERE id=%s AND {PREDICATE}''',(n,start,due,json.dumps(snapshot),lead['id']))==1
            execute(conn,f'''INSERT INTO {TASK}(task_type,biz_type,biz_id,status,assignee_type,assignee_id,title_snapshot,
                action_code,due_at,payload,idempotency_key,version,creator,updater,tenant_id)
                VALUES('lead_qualification','lead',%s,'pending','user',%s,%s,'OPEN_LEAD_FOLLOW_UP',%s,%s,%s,0,%s,%s,1)''',
                (lead['id'],lead['owner_user_id'],'有效性判定：'+lead['lead_no'],due,json.dumps(payload),f"lead-qualification:{lead['id']}:{n}",MARKER,MARKER))
            execute(conn,f'''INSERT INTO {EVENT}(event_type,aggregate_type,aggregate_id,operator_user_id,from_status,to_status,
                related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
                VALUES('lead_qualification_started','lead',%s,%s,'submitted','submitted',%s,%s,%s,%s,%s,1)''',
                (lead['id'],lead['owner_user_id'],json.dumps(dict(roundNo=n,dueAt=due.isoformat())),start,
                 f"lead-qualification-started:{lead['id']}:{n}",MARKER,MARKER))
        verify(conn,backup)
        conn.commit()
        return len(current)
    except BaseException:
        conn.rollback()
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backup-dir',type=Path,required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    dest=args.backup_dir.resolve()
    root=Path(__file__).resolve().parents[4]
    assert dest!=root and root not in dest.parents, 'Private backups must stay outside repository'
    dest.mkdir(parents=True,exist_ok=True)
    conn=connect()
    file=dest/'before.pickle'
    if not args.apply:
        assert not file.exists(), 'Do not overwrite frozen backup'
        leads=rows(conn,f'SELECT * FROM {LEAD} WHERE {PREDICATE} ORDER BY id')
        audit_original(conn,leads)
        backup={LEAD:leads,RULE:[rule(conn)],'ids':[x['id'] for x in leads], 'ledgers':ledgers(conn)}
        for table,key in ((TASK,'biz_id'),(EVENT,'aggregate_id'),(FOLLOW,'lead_id'),(HISTORY,'lead_id')):
            backup[table]=scoped(conn,table,key,backup['ids'])
        file.write_bytes(pickle.dumps(backup))
        conn.rollback()
        print(json.dumps(dict(candidates=len(leads),timeoutMinutes=backup[RULE][0]['qualification_timeout_minutes'],backupSHA256=hashlib.sha256(file.read_bytes()).hexdigest())))
    else:
        backup=pickle.loads(file.read_bytes())
        # Session-local clones execute the same repair with actual local schema and
        # frozen rows; closing the session discards them without touching live tables.
        for table in (LEAD,TASK,EVENT,RULE,FOLLOW,HISTORY):
            ddl = rows(conn, f'SHOW CREATE TABLE {table}')[0]['Create Table']
            generated = {x['Field'] for x in rows(conn, f'SHOW COLUMNS FROM {table}')
                         if 'GENERATED' in x['Extra'] and 'DEFAULT_GENERATED' not in x['Extra']}
            execute(conn, ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
            for row in backup[table]:
                row = {k: v for k, v in row.items() if k not in generated}
                execute(conn,f'INSERT INTO {table} ('+','.join('`'+k+'`' for k in row)+') VALUES ('+','.join(['%s']*len(row))+')',tuple(row.values()))
        conn.commit()
        rehearsal=repair(conn,backup)
        assert repair(conn,backup)==0
        conn.close()
        conn=connect()
        assert ledgers(conn)==backup['ledgers'], 'Migration ledger changed'
        conn.rollback()
        changed=repair(conn,backup)
        assert repair(conn,backup)==0
        verify(conn,backup)
        assert ledgers(conn)==backup['ledgers']
        remaining=rows(conn,f'SELECT COUNT(*) AS n FROM {LEAD} WHERE {PREDICATE}')[0]['n']
        report=dict(rehearsalRepaired=rehearsal,repaired=changed,repeatRepaired=0,remainingMissing=remaining,
                    historyUnchanged=True,ledgersUnchanged=True,threeTablesConsistent=True,chineseHexVerified=True,
                    overdue=sum(expected(x,backup[RULE][0])[2]<dt.datetime.now() for x in backup[LEAD]))
        (dest/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report))
    conn.close()


if __name__=='__main__':
    main()
