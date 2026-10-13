# -*- coding: utf-8 -*-
"""V305 private evidence, exact preimage guard, transactional apply and recovery.

preflight is read-only. prepare writes only an ignored private evidence file. apply
requires its SHA256. Credentials are read internally, never printed. Default local
Docker target; remote targets require explicit connection arguments and authorization.
"""
import argparse
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from decimal import Decimal

import pymysql
from pymysql.cursors import DictCursor

from order_history_repair_model import (VERSION,KEY,TABLES,READ_TABLES,EXCLUDED,canonical,digest,build,source_package)

ROOT=Path(__file__).resolve().parents[4]
SQL_PATH=ROOT/'script/sql/mysql/migrations/V305__order_history_repair.sql'
DEFAULT_SOURCE=Path.home()/'Desktop/parttimecrm-cst+0800-20260920-222103.sql.gz'


def ident(value):
    if not re.fullmatch(r'[A-Za-z0-9_-]+',value): raise ValueError('Invalid SQL identifier')
    return '`'+value+'`'


def connect(database='ruoyi-vue-pro',host=None,port=None,user='root',password_file=None):
    if host is None:
        info=json.loads(subprocess.check_output(['docker','inspect','yudao-mysql']))[0]
        env=dict(x.split('=',1) for x in info['Config']['Env'] if '=' in x)
        password=env['MYSQL_ROOT_PASSWORD'];host='127.0.0.1'
        port=int(info['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort'])
    else:
        if password_file is None: raise ValueError('Explicit target requires a private password file')
        password=Path(password_file).read_text(encoding='utf-8').strip()
    conn=pymysql.connect(host=host,port=port or 3306,user=user,password=password,database=database,
        charset='utf8mb4',autocommit=True,cursorclass=DictCursor)
    with conn.cursor() as cur:
        cur.execute("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci")
        cur.execute("SET time_zone='+08:00'")
    return conn


def scalar(value):
    if isinstance(value,Decimal): return str(value)
    if isinstance(value,(dt.datetime,dt.date)): return value.isoformat(sep=' ') if isinstance(value,dt.datetime) else value.isoformat()
    if isinstance(value,bytes): return int.from_bytes(value,'big')
    return value


def schema(cur):
    cur.execute('SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema=DATABASE()')
    result={}
    for r in cur.fetchall(): result.setdefault(r['TABLE_NAME'],{})[r['COLUMN_NAME']]=r['DATA_TYPE']
    return result


def normalize(row,types):
    return {k:json.loads(v) if v is not None and types.get(k)=='json' and isinstance(v,str)
            else {'binarySha256':hashlib.sha256(v).hexdigest()} if isinstance(v,bytes) and types.get(k)!='bit'
            else scalar(v) for k,v in row.items()}


def load_data(conn,lock=False):
    with conn.cursor() as cur:
        types=schema(cur);result={}
        for table in READ_TABLES:
            suffix=' WHERE tenant_id=1' if 'tenant_id' in types[table] else ''
            cur.execute('SELECT * FROM '+ident(table)+suffix+(' FOR UPDATE' if lock else ''))
            result[table]=[normalize(r,types[table]) for r in cur.fetchall()]
        return result


def private_write(path,value):
    path=Path(path).resolve();path.parent.mkdir(parents=True,exist_ok=True)
    if path.is_relative_to(ROOT):
        result=subprocess.run(['git','check-ignore','--quiet',str(path)],cwd=ROOT)
        if result.returncode: raise ValueError('Private data path must be git-ignored')
    payload=canonical(value).encode('utf-8')
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as stream:stream.write(payload)
    return hashlib.sha256(payload).hexdigest()


def statements(text):
    delimiter=';';buf=''
    for line in text.splitlines():
        if line.lstrip().startswith('--'):continue
        if line.strip().upper().startswith('DELIMITER '):delimiter=line.strip().split()[1];continue
        buf+=line+'\n'
        if buf.rstrip().endswith(delimiter):
            statement=buf.rstrip()[:-len(delimiter)].strip();buf=''
            if statement:yield statement
    if buf.strip():raise ValueError('Unterminated migration SQL')


def prepare_schema(conn):
    text=SQL_PATH.read_text(encoding='utf-8')
    with conn.cursor() as cur:
        cur.execute('SET @v305_executor=1')
        for statement in statements(text.split('-- BEGIN PREPARE')[1].split('-- END PREPARE')[0]):cur.execute(statement)
        # Routine DDL must precede the business transaction (MySQL implicitly commits DDL).
        for statement in statements(text.split('-- BEGIN FINALIZE')[1].split('-- END FINALIZE')[0]):
            if statement.startswith('CALL ') or statement in ('DROP PROCEDURE zsjos_v305_finalize','START TRANSACTION','COMMIT'):continue
            cur.execute(statement)


def cleanup_schema(conn):
    # Call only after commit/rollback: DROP PROCEDURE implicitly commits in MySQL.
    # These helpers are recreated by prepare_schema, not persistent application APIs.
    # Leaving root-defined helpers behind also blocks the migrator's routine backup.
    with conn.cursor() as cur:
        for name in ('zsjos_v305_finalize','zsjos_v305_prepare','zsjos_v305_completed'):
            cur.execute('DROP PROCEDURE IF EXISTS '+name)


def encode(value,kind):
    if value is not None and kind=='json':return canonical(value)
    return value


def resolve(value,ids):
    if isinstance(value,str) and value.startswith('@zsjos_'):return ids[value]
    if isinstance(value,dict):return {k:resolve(v,ids) for k,v in value.items()}
    if isinstance(value,list):return [resolve(v,ids) for v in value]
    return value


def fetch(cur,table,row_id,types):
    cur.execute('SELECT * FROM '+ident(table)+' WHERE id=%s FOR UPDATE',(row_id,))
    row=cur.fetchone()
    return normalize(row,types[table]) if row else None


def dependency_guards(cur,plan,types):
    for table,rows in plan['dependencies'].items():
        for expected in rows:
            actual=fetch(cur,table,expected['id'],types)
            if not actual or any(actual.get(k)!=v for k,v in expected.items()):raise ValueError('Dependency changed: '+table)


def duplicate_reference_guard(cur,plan,types):
    ids=[op['before']['id'] for op in plan['operations'] if op['table']=='zsjos_order_item' and 'before' in op and not op['before']['deleted'] and op['after']['deleted']]
    if not ids:return
    placeholders=','.join(['%s']*len(ids))
    # All explicit relational references, including soft-deleted rows, are protected.
    for table,columns in types.items():
        if '_bak_' in table or table.startswith('zsjos_data_repair'):continue
        for col,kind in columns.items():
            if col in ('order_item_id','source_order_item_id'):
                cur.execute(f'SELECT COUNT(*) n FROM {ident(table)} WHERE {ident(col)} IN ({placeholders})',ids)
                if cur.fetchone()['n']:raise ValueError('Duplicate item acquired relational reference: '+table)
            if kind=='json':
                cur.execute(f'SELECT {ident(col)} value FROM {ident(table)} WHERE {ident(col)} IS NOT NULL')
                def reference(value):
                    if isinstance(value,dict):
                        return any((k in ('orderItemId','order_item_id','sourceOrderItemId','source_order_item_id') and str(v) in {str(x) for x in ids}) or reference(v) for k,v in value.items())
                    if isinstance(value,list):return any(reference(v) for v in value)
                    return False
                for row in cur.fetchall():
                    if reference(json.loads(row['value'])):raise ValueError('Duplicate item acquired JSON reference: '+table)


def dependencies(data,plan):
    order_ids={op['before']['id'] for op in plan['operations'] if op['table']=='zsjos_order' and 'before' in op}
    # Dependencies used to construct facts are guarded in addition to write targets.
    result={}
    eligible={o['id'] for o in data['zsjos_order'] if o['order_no'] in plan['eligibleOrders']}
    leads={o['lead_id'] for o in data['zsjos_order'] if o['id'] in eligible and o.get('lead_id')}
    touched={(op['table'],op['before']['id']) for op in plan['operations'] if 'before' in op}
    for table in READ_TABLES:
        selected=[]
        for row in data[table]:
            if (table,row['id']) in touched:continue
            if table=='system_users':
                selected.append({k:row.get(k) for k in ('id','tenant_id','deleted','status','username','nickname','dept_id')})
            elif table=='system_dept':
                selected.append({k:row.get(k) for k in ('id','deleted','status','name','parent_id')})
            elif table=='zsjos_partner':
                selected.append({k:row.get(k) for k in ('id','tenant_id','deleted','name')})
            elif table=='zsjos_performance_org':selected.append(row)
            elif table=='zsjos_order' and row['id'] in eligible:selected.append(row)
            elif table=='zsjos_lead' and row['id'] in leads:selected.append(row)
            elif table=='zsjos_lead_assignment_history' and row['lead_id'] in leads:selected.append(row)
        if selected:result[table]=selected
    return result


def make_plan(conn,source_path):
    with conn.cursor() as cur:cur.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
    try:
        data=load_data(conn)
        numbers={o['order_no'] for o in data['zsjos_order'] if o['status']=='effective' and not o['deleted'] and o['creator'] not in EXCLUDED}
        source=source_package(source_path,numbers)
        plan=build(data,source,dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
        plan['dependencies']=dependencies(data,plan)
        plan['scriptSha256']=hashlib.sha256(SQL_PATH.read_bytes()).hexdigest()
        plan['builderSha256']=hashlib.sha256((Path(__file__).with_name('order_history_repair_model.py')).read_bytes()).hexdigest()
        with conn.cursor() as cur:duplicate_reference_guard(cur,plan,schema(cur))
        return plan
    finally:conn.rollback()


def backup(cur,table,row_id,column,value):
    cur.execute('INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) VALUES (%s,%s,%s,%s,%s)',(KEY,table,row_id,column,canonical(value)))


def execute(conn,plan,plan_sha,dry_run=False,fail_after=None,rollback_mode=False):
    if plan['version']!=VERSION or plan['repairKey']!=KEY:raise ValueError('Wrong evidence package')
    if hashlib.sha256(SQL_PATH.read_bytes()).hexdigest()!=plan['scriptSha256']:raise ValueError('Migration bytes changed; prepare again')
    if hashlib.sha256(Path(__file__).with_name('order_history_repair_model.py').read_bytes()).hexdigest()!=plan['builderSha256']:raise ValueError('Builder changed; prepare again')
    for op in plan['operations']:
        if op['table'] not in TABLES or op['after'].get('tenant_id')!=1:raise ValueError('Unapproved table/tenant')
        if 'before' in op and op['before'].get('tenant_id')!=1:raise ValueError('Cross-tenant before image')
        if op['table']=='zsjos_lead' and 'before' in op:
            # V305 repairs confirmed actors, not acquisition decisions. Historical
            # source inconsistencies must survive unchanged for their own review;
            # enforcing the newer source contract here blocks the ordered upgrade.
            protected=('source_type','submission_source_type',
                       'provider_owner_type','provider_owner_id','provider_owner_name_snapshot','source_provider_user_id','source_provider_recorded',
                       'contribution_user_id_snapshot','contribution_user_name_snapshot','contribution_dept_id_snapshot','contribution_dept_name_snapshot',
                       'contribution_supervisor_user_id_snapshot','contribution_supervisor_name_snapshot')
            if any(op['before'].get(k)!=op['after'].get(k) for k in protected):
                raise ValueError('Order actor repair cannot change source attribution; use a confirmed source manifest')

    with conn.cursor() as cur:
        cur.execute("SELECT GET_LOCK(CONCAT('v305:',DATABASE()),10) locked")
        if cur.fetchone()['locked']!=1:raise ValueError('Repair lock unavailable')
    preparation_started=False
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) n FROM zsjos_data_repair_backup WHERE repair_key='lead-source-confirmed-v309-r2'")
            if cur.fetchone()['n']:
                raise ValueError('V305 actor repair cannot run after confirmed V309 source revision; review dependent changes first')
        preparation_started=True
        prepare_schema(conn)
        with conn.cursor() as cur:
            cur.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
            cur.execute('START TRANSACTION')
            types=schema(cur)
            cur.execute("SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE()")
            engines={r['TABLE_NAME']:r['ENGINE'] for r in cur.fetchall()}
            if any(engines.get(t)!='InnoDB' for t in TABLES+['zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version']):
                raise ValueError('Transactional tables require InnoDB')
            cur.execute('SELECT table_name,row_id,column_name,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s FOR UPDATE',(KEY,))
            saved={}
            for r in cur.fetchall():
                k=(r['table_name'],r['row_id'],r['column_name'])
                if k in saved:raise ValueError('Duplicate repair backup')
                saved[k]=json.loads(r['old_value'])
            recorded=saved.get(('__plan__',0,'manifest'))
            if recorded and recorded['sha256']!=plan_sha:raise ValueError('Different evidence package already registered')
            ids={}
            if recorded:ids=recorded['ids']
            if not rollback_mode:
                dependency_guards(cur,plan,types)
                duplicate_reference_guard(cur,plan,types)
                # Guard absence, not just existing rows, to prevent a concurrent round,
                # item or attribution from being duplicated after plan preparation.
                if not recorded:
                    for op in plan['operations']:
                        if 'before' in op:continue
                        table=op['table'];r=op['after']
                        if table in ('zsjos_order_approval_round','zsjos_order_item'):
                            cur.execute('SELECT id FROM '+ident(table)+' WHERE tenant_id=1 AND order_id=%s FOR UPDATE',(r['order_id'],))
                        elif table=='zsjos_performance_attribution':
                            cur.execute("SELECT id FROM zsjos_performance_attribution WHERE tenant_id=1 AND fact_type='ORDER' AND fact_id=%s FOR UPDATE",(r['fact_id'],))
                        else:raise ValueError('Unapproved insertion')
                        if cur.fetchall():raise ValueError('Planned insertion now conflicts: '+table)
            if rollback_mode and not recorded:raise ValueError('No applied repair to restore')
            # Validate every before/after state before the first business write.
            for op in plan['operations']:
                if 'before' in op:
                    actual=fetch(cur,op['table'],op['before']['id'],types)
                    after=resolve(op['after'],ids) if recorded else None
                    if rollback_mode:
                        if actual!=after:raise ValueError('Rollback would overwrite later changes: '+op['table'])
                    elif actual!=op['before'] and actual!=after:raise ValueError('Before-image conflict: '+op['table'])
                elif recorded:
                    row_id=ids[op['after']['id']];actual=fetch(cur,op['table'],row_id,types)
                    if actual!=saved.get((op['table'],row_id,'__inserted__')):raise ValueError('Inserted row changed or missing')
            count=0
            if rollback_mode:
                # Existing order FK is restored before removing inserted rounds.
                for op in reversed(plan['operations']):
                    if 'before' not in op:continue
                    row=op['before'];after=resolve(op['after'],ids)
                    cols=[k for k in row if k!='id' and row[k]!=after.get(k)]
                    cur.execute('UPDATE '+ident(op['table'])+' SET '+','.join(ident(k)+'=%s' for k in cols)+' WHERE id=%s',tuple(encode(row[k],types[op['table']][k]) for k in cols)+(row['id'],))
                for op in reversed(plan['operations']):
                    if 'before' in op:continue
                    cur.execute('DELETE FROM '+ident(op['table'])+' WHERE id=%s',(ids[op['after']['id']],))
                for op in plan['operations']:
                    if 'before' in op:
                        if fetch(cur,op['table'],op['before']['id'],types)!=op['before']:raise ValueError('Restored before image mismatch')
                    elif fetch(cur,op['table'],ids[op['after']['id']],types) is not None:
                        raise ValueError('Inserted row survived rollback rehearsal')
                # Only used for dry-run recovery rehearsal; standalone destructive restoration
                # is not exposed by the CLI. Neither backups nor success records are removed.
                conn.rollback();return {'rollbackRehearsal':True}
            for op in plan['operations']:
                table=op['table']
                if 'before' in op:
                    row=resolve(op['after'],ids);before=op['before'];actual=fetch(cur,table,before['id'],types)
                    if actual==row:continue
                    if not recorded:
                        backup(cur,table,before['id'],'__before__',before);backup(cur,table,before['id'],'__after__',row)
                    cols=[k for k in row if k!='id' and row[k]!=before.get(k)]
                    cur.execute('UPDATE '+ident(table)+' SET '+','.join(ident(k)+'=%s' for k in cols)+' WHERE id=%s',tuple(encode(row[k],types[table][k]) for k in cols)+(before['id'],))
                elif not recorded:
                    row=resolve({k:v for k,v in op['after'].items() if k!='id'},ids);cols=list(row)
                    cur.execute('INSERT INTO '+ident(table)+' ('+','.join(ident(k) for k in cols)+') VALUES ('+','.join(['%s']*len(cols))+')',tuple(encode(row[k],types[table][k]) for k in cols))
                    ids[op['after']['id']]=cur.lastrowid
                    backup(cur,table,cur.lastrowid,'__inserted__',fetch(cur,table,cur.lastrowid,types))
                else:continue
                count+=1
                if fail_after is not None and count>=fail_after:raise ValueError('Injected failure for isolated test')
            for op in plan['operations']:
                row=resolve(op['after'],ids);actual=fetch(cur,op['table'],row['id'],types)
                if 'before' in op:
                    if actual!=row:raise ValueError('Postimage mismatch: '+op['table'])
                elif any(actual.get(k)!=v for k,v in row.items()):raise ValueError('Inserted postimage mismatch')
                for k,v in row.items():
                    if isinstance(v,str) and any(ord(c)>127 for c in v):
                        cur.execute('SELECT HEX('+ident(k)+') h FROM '+ident(op['table'])+' WHERE id=%s',(row['id'],))
                        if cur.fetchone()['h']!=v.encode('utf-8').hex().upper():raise ValueError('UTF-8 byte mismatch')
            if not recorded:backup(cur,'__plan__',0,'manifest',{'sha256':plan_sha,'ids':ids,'counts':plan['counts'],'sources':plan['notes'],'sourceSha':plan['source']['sha256']})
            cur.execute('SET @v305_verified=1,@v305_plan_sha=%s',(plan_sha,))
            cur.execute('CALL zsjos_v305_finalize()')
        if dry_run:conn.rollback()
        else:conn.commit()
        return {'changedOperations':count,'dryRun':dry_run,'counts':plan['counts']}
    except Exception:
        conn.rollback();raise
    finally:
        try:
            if preparation_started:cleanup_schema(conn)
        finally:
            with conn.cursor() as cur:cur.execute("SELECT RELEASE_LOCK(CONCAT('v305:',DATABASE()))")


def inspect_applied(conn,plan,plan_sha):
    """Read-only completion audit, including complete saved insert images."""
    with conn.cursor() as cur:
        cur.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
        try:
            types=schema(cur)
            cur.execute('SELECT table_name,row_id,column_name,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s',(KEY,))
            saved={(r['table_name'],r['row_id'],r['column_name']):json.loads(r['old_value']) for r in cur.fetchall()}
            manifest=saved.get(('__plan__',0,'manifest'))
            if not manifest or manifest['sha256']!=plan_sha:raise ValueError('Applied plan manifest missing or different')
            mismatches=[]
            for op in plan['operations']:
                row=resolve(op['after'],manifest['ids']);table=op['table']
                cur.execute('SELECT * FROM '+ident(table)+' WHERE id=%s',(row['id'],))
                found=cur.fetchone();actual=normalize(found,types[table]) if found else None
                expected=row if 'before' in op else saved.get((table,row['id'],'__inserted__'))
                if actual!=expected:mismatches.append({'table':table,'id':row['id']})
            cur.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V305'")
            global_ok=cur.fetchone()['n']==1
            cur.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE version='V305' AND module_code='core'")
            return {'matchesAfterImages':not mismatches,'mismatches':mismatches,'ledgersPresent':global_ok and cur.fetchone()['n']==1}
        finally:conn.rollback()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['preflight','prepare','apply','verify','recovery-check'])
    parser.add_argument('--database',default='ruoyi-vue-pro');parser.add_argument('--source',type=Path,default=DEFAULT_SOURCE)
    parser.add_argument('--plan',type=Path);parser.add_argument('--expected-sha256')
    parser.add_argument('--host');parser.add_argument('--port',type=int);parser.add_argument('--user',default='root');parser.add_argument('--password-file')
    args=parser.parse_args()
    conn=connect(args.database,args.host,args.port,args.user,args.password_file)
    try:
        if args.mode in ('preflight','prepare'):
            plan=make_plan(conn,args.source)
            report={'eligibleOrders':len(plan['eligibleOrders']),'counts':plan['counts'],'sourceSha256':plan['source']['sha256']}
            if args.mode=='prepare':
                if not args.plan:raise ValueError('--plan required')
                report['planSha256']=private_write(args.plan,plan)
            print(json.dumps(report,ensure_ascii=True))
        else:
            if not args.plan or not args.expected_sha256:raise ValueError('Reviewed --plan and --expected-sha256 required')
            raw=args.plan.read_bytes();sha=hashlib.sha256(raw).hexdigest()
            if sha!=args.expected_sha256:raise ValueError('Evidence digest mismatch')
            plan=json.loads(raw)
            result=execute(conn,plan,sha) if args.mode=='apply' else inspect_applied(conn,plan,sha)
            print(json.dumps(result,ensure_ascii=True))
    finally:conn.close()


if __name__=='__main__':
    try:main()
    except Exception as exc:
        # MySQL/parser exceptions can contain full business JSON. Emit only safe exception type.
        if isinstance(exc,ValueError):raise SystemExit(str(exc))
        raise SystemExit('Repair failed ('+type(exc).__name__+'); no business payload logged')
