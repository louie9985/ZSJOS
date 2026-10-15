#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Non-destructive MySQL 8 checks in new, retained synthetic schemas only."""
import datetime, json, re, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/migrations/pms/V003__knowledge_folder_import.sql').read_text(encoding='utf-8')
prefix = 'pms_import_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
created = set()
def cli(source, schema=None, force=False):
    assert schema is None or schema in created
    command = 'if [ -n "$MYSQL_ROOT_PASSWORD_FILE" ]; then MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"; else MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; fi; export MYSQL_PWD; exec mysql --default-character-set=utf8mb4 -uroot -N -B'
    args = ['docker','exec','-i','yudao-mysql','sh','-c',command + (' --force' if force else '')]
    r = subprocess.run(args, input=('SET NAMES utf8mb4;\n' + (f'USE `{schema}`;\n' if schema else '') + source).encode(),capture_output=True)
    if not force and (r.returncode or b'ERROR' in r.stderr): raise RuntimeError(r.stderr.decode())
    return r.stdout.decode(),r.stderr.decode()
def schema(suffix, prereq=True):
    name=prefix+'_'+suffix
    cli(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;');created.add(name)
    cli("CREATE TABLE zsjos_module_schema_version (module_code varchar(32),version varchar(32),description varchar(255),checksum varchar(64),release_version varchar(32),PRIMARY KEY(module_code,version));" + ("INSERT INTO zsjos_module_schema_version VALUES ('pms','V002','','','baseline');" if prereq else ''),name)
    return name
fresh=schema('fresh');cli(SQL,fresh);cli(SQL,fresh)
assert cli("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V003';",fresh)[0].strip()=='1'
cli("INSERT INTO pms_knowledge_import_task(owner_id,library_id,folder_id,root_name,request_key,manifest_hash,mode,status) VALUES(1,1,0,'培训资料','fixture',REPEAT('a',64),'NEW','OPEN');",fresh)
assert cli("SELECT HEX(root_name) FROM pms_knowledge_import_task;",fresh)[0].strip()=='培训资料'.encode().hex().upper()
_,err=cli("INSERT INTO pms_knowledge_import_task(owner_id,library_id,folder_id,root_name,request_key,manifest_hash,mode,status) VALUES(1,1,0,'重复','fixture',REPEAT('a',64),'NEW','OPEN');",fresh,True)
assert 'Duplicate' in err
missing=schema('missing',False);_,err=cli(SQL,missing,True);assert 'requires V002' in err
assert cli("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V003';",missing)[0].strip()=='0'
partial=schema('partial')
first=re.search(r'CREATE TABLE IF NOT EXISTS pms_knowledge_import_task[\s\S]+?ENGINE[^;]+;',SQL).group(0)
cli(first,partial);cli(SQL,partial)
assert cli("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'pms_knowledge_import_%';",partial)[0].strip()=='2'
broken=schema('broken');cli('CREATE TABLE pms_knowledge_import_task(id bigint primary key);',broken)
_,err=cli(SQL,broken,True);assert 'invalid Task' in err
assert cli("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V003';",broken)[0].strip()=='0'
# Repair the deliberately incomplete empty fixture without dropping data/schema.
for line in first.splitlines()[1:]:
    line=line.strip().rstrip(',')
    if line.startswith(('PRIMARY','UNIQUE',')','id ')): continue
    cli('ALTER TABLE pms_knowledge_import_task ADD COLUMN '+line+';',broken)
cli('ALTER TABLE pms_knowledge_import_task MODIFY id bigint NOT NULL AUTO_INCREMENT, ADD UNIQUE KEY uk_import_request(tenant_id,owner_id,request_key);',broken)
cli(SQL,broken)
# Full PMS install/upgrade chain against real Core table shapes; no business data copied.
chain=schema('chain',False)
core=(ROOT/'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8')+(ROOT/'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
notify=re.search(r'CREATE TABLE IF NOT EXISTS `system_notify_template` \([\s\S]+?\) ENGINE=[^;]+;',core).group(0)
cli(notify,chain)
cli((ROOT/'script/sql/mysql/migrations/pms/V001__pms_schema.sql').read_text(encoding='utf-8'),chain)
cli("INSERT INTO zsjos_module_schema_version VALUES('pms','V001','','','baseline');",chain)
cli((ROOT/'script/sql/mysql/migrations/pms/V002__knowledge_document_share_notify_template.sql').read_text(encoding='utf-8'),chain)
cli(SQL,chain)
assert cli("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'pms_%';",chain)[0].strip()=='35'
snapshot=schema('snapshot')
cli((ROOT/'script/sql/mysql/schema/pms.sql').read_text(encoding='utf-8'),snapshot)
shape="SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'pms_%' ORDER BY table_name,ordinal_position;"
assert cli(shape,chain)[0]==cli(shape,snapshot)[0]
print(json.dumps({'passed':['initial','repeat','UTF8_HEX','unique_request','missing_prerequisite_no_ledger','partial_recovery','invalid_schema_no_ledger','repaired_schema_recovery','PMS_V001_to_V003','desired_schema_matches_chain'],'retained_synthetic_schemas':sorted(created)},ensure_ascii=False))
