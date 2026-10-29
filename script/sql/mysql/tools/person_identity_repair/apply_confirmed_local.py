# -*- coding: utf-8 -*-
"""Local-only confirmed batch; actual application requires the local backend stopped."""
import argparse,datetime,gzip,hashlib,json,os,socket,subprocess
from pathlib import Path
from repair import helpers,fetch,LIVE,HISTORICAL
from group_repair import prepare_group,apply_group,restore_group,capture,metadata,generated_column

def private_save(path,value):
    helpers.check(not path.resolve().is_relative_to(helpers.ROOT),'Evidence must be outside repository')
    with path.open('x',encoding='utf-8') as out:
        json.dump(value,out,ensure_ascii=False,default=helpers.json_default,sort_keys=True)
        out.flush();os.fsync(out.fileno())

def run(audit_path,manual_dir,evidence,execute):
    audit=json.loads(audit_path.read_text(encoding='utf-8'));manual={}
    for file in manual_dir.glob('G*.json'):
        record=json.loads(file.read_text(encoding='utf-8'));manual[int(file.stem[1:])]=record['plan']['confirmation']
    selected={i for i,g in enumerate(audit['groups'],1) if g['identityDecision']=='AUTO_MERGE'}|set(manual)
    deferred=set(range(1,len(audit['groups'])+1))-selected
    helpers.check(len(selected)==125 and len(deferred)==6,'Confirmed batch scope differs from approval')
    if execute:
        probe=socket.socket();probe.settimeout(1)
        try:running=probe.connect_ex(('127.0.0.1',48080))==0
        finally:probe.close()
        helpers.check(not running,'Stop the approved local backend before actual merge')
    evidence.mkdir(parents=True,exist_ok=False)
    c=helpers.connect('ruoyi-vue-pro');applied=[]
    def signatures(conn):
        result={}
        for i in sorted(deferred):
            with conn.cursor() as cur:s,_=capture(cur,audit['groups'][i-1]['personIds'],'deferred-six')
            conn.rollback();result[i]=helpers.digest(s)
        return result
    try:
        baseline=signatures(c)
        if execute:
            backup=evidence/'before-merge.sql.gz'
            command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --hex-blob --routines --triggers --events --set-gtid-purged=OFF ruoyi-vue-pro'
            proc=subprocess.Popen(['docker','exec','yudao-mysql','sh','-c',command],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            with gzip.open(backup,'wb',compresslevel=1) as stream:
                while block:=proc.stdout.read(1024*1024):stream.write(block)
            helpers.check(proc.wait()==0,'Backup failed; merge not started')
            with backup.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
            private_save(evidence/'backup.json',{'database':'ruoyi-vue-pro','file':backup.name,'sha256':digest})
            database='ruoyi-vue-pro';work=c
        else:
            database='historical_student_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
            with c.cursor() as cur:
                meta=metadata(cur);tables=(set(LIVE)|HISTORICAL|{'zsjos_person','zsjos_person_contact_claim'})&{r['t'] for r in meta}
                cur.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4')
                for table in sorted(tables):
                    fields=','.join('`'+r['c']+'`' for r in meta if r['t']==table and not generated_column(r['e']))
                    cur.execute(f'CREATE TABLE `{database}`.`{table}` LIKE `ruoyi-vue-pro`.`{table}`')
                    cur.execute(f'INSERT INTO `{database}`.`{table}` ({fields}) SELECT {fields} FROM `ruoyi-vue-pro`.`{table}` WHERE tenant_id=1')
            c.commit();work=helpers.connect(database)
        before_deferred=signatures(work)
        try:
            for i in sorted(selected):
                plan=prepare_group(work,database,audit['groups'][i-1]['personIds'],manual.get(i))
                private_save(evidence/f'G{i:03d}-before.json',plan)
                result=apply_group(work,plan,execute=True,before_commit=lambda r,i=i:private_save(evidence/f'G{i:03d}-after.json',r))
                applied.append((i,plan,result))
                helpers.check(apply_group(work,plan,execute=True)['replay'],'Merge replay failed')
            helpers.check(before_deferred==signatures(work),'Deferred groups changed')
            if not execute:
                for _,plan,result in reversed(applied):restore_group(work,plan,result,execute=True)
                helpers.check(baseline==signatures(c),'Application data changed during isolated rehearsal')
            summary={'database':database,'applicationCommitted':execute,'groups':len(applied),'archivedSources':sum(len(p['sourcePersonIds']) for _,p,_ in applied),'deferredGroups':sorted(deferred),'deferredUnchanged':True,'recovered':not execute}
            private_save(evidence/'summary.json',summary);print(json.dumps(summary))
        except Exception:
            # Fail closed: completed groups are undone in reverse order while services remain stopped.
            for _,plan,result in reversed(applied):restore_group(work,plan,result,execute=True)
            raise
        finally:
            if work is not c:work.close()
    finally:c.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--audit',type=Path,required=True);p.add_argument('--manual-dir',type=Path,required=True);p.add_argument('--evidence',type=Path,required=True);p.add_argument('--execute',action='store_true')
    a=p.parse_args();run(a.audit,a.manual_dir,a.evidence,a.execute)
