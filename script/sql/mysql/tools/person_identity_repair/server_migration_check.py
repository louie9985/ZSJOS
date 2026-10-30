# -*- coding: utf-8 -*-
"""V355 fault/recovery checks in fresh target-bound retained MySQL fixtures only."""
import argparse
from pathlib import Path
from server_target import ServerTarget,read,save,require
from server_rehearsal import execute_script,MIGRATION


def check(target,snapshot,output):
    audit=read(snapshot/'audit.json');results=[]
    for case in ('initial','partial_ddl','missing_prerequisite','ledger_failure','marker_only'):
        db=target.create_isolated();c=target.connect_isolated(db)
        try:
            with c.cursor() as q:
                for t in ('zsjos_schema_version','zsjos_module_schema_version'):
                    q.execute(audit['schemas'][t])
                q.execute("CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY,tenant_id BIGINT,person_id BIGINT,deleted BIT DEFAULT b'0',active_person_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN person_id ELSE NULL END) STORED,UNIQUE KEY uk_tenant_active_person(tenant_id,active_person_id))")
                q.execute('CREATE TABLE zsjos_order(id BIGINT PRIMARY KEY,person_id BIGINT)')
                q.execute('CREATE TABLE zsjos_opportunity(id BIGINT PRIMARY KEY,tenant_id BIGINT,active_person_id BIGINT,UNIQUE KEY uk_tenant_active_person(tenant_id,active_person_id))')
                q.execute('INSERT INTO zsjos_lead(id,tenant_id,person_id) VALUES(1,1,10)')
                if case!='missing_prerequisite':prerequisite(q)
                if case=='partial_ddl':q.execute('ALTER TABLE zsjos_lead DROP INDEX uk_tenant_active_person')
                if case=='ledger_failure':q.execute("CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated injected ledger failure'")
                if case=='marker_only':
                    q.execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V355','fixture','preserve-fixture')")
                    q.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V355','fixture','preserve-fixture','fixture')")
            c.commit()
            if case in ('missing_prerequisite','ledger_failure'):
                try:execute_script(c,MIGRATION.read_text(encoding='utf-8'))
                except Exception:pass
                else:raise ValueError('Expected migration failure')
                with c.cursor() as q:
                    for t in ('zsjos_schema_version','zsjos_module_schema_version'):
                        q.execute(f"SELECT COUNT(*) n FROM {t} WHERE version='V355'");require(q.fetchone()['n']==0,'Failed migration advanced a ledger')
                    if case=='missing_prerequisite':prerequisite(q)
                    else:q.execute('DROP TRIGGER fail_ledger')
                c.commit()
            execute_script(c,MIGRATION.read_text(encoding='utf-8'))
            execute_script(c,MIGRATION.read_text(encoding='utf-8'))
            with c.cursor() as q:
                q.execute("SELECT COUNT(*) n FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_opportunity') AND index_name='uk_tenant_active_person'")
                require(q.fetchone()['n']==0,'Legacy uniqueness remains')
                q.execute("SELECT COUNT(*) n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_order') AND column_name='identity_origin_person_id'")
                require(q.fetchone()['n']==2,'Origin columns missing')
                for t in ('zsjos_schema_version','zsjos_module_schema_version'):
                    q.execute(f"SELECT checksum,HEX(description) h FROM {t} WHERE version='V355'");rows=q.fetchall()
                    require(len(rows)==1,'Ledger repeat duplicated version')
                    if case=='marker_only':require(rows[0]['checksum']=='preserve-fixture','Existing marker overwritten')
                    else:require(rows[0]['h']=='客户历史多客资'.encode().hex().upper(),'UTF8 bytes differ')
                q.execute('INSERT INTO zsjos_lead(id,tenant_id,person_id) VALUES(2,1,10)')
                q.execute('INSERT INTO zsjos_opportunity VALUES(1,1,10),(2,1,10)')
            c.commit();results.append({'case':case,'database':db,'passed':True})
        finally:c.close()
    save(output,results);return results


def prerequisite(q):
    q.execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V354','fixture','fixture')")
    q.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V354','fixture','fixture','fixture')")


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--binding',type=Path,required=True);p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(check(ServerTarget(read(a.binding)),a.snapshot,a.output)))
