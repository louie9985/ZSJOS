#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only comparison of controlled bootstrap schema with the local Docker development DB.
No credentials or business row data are printed. Run after test_v209_replay.py.
"""
import argparse,subprocess
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--container',required=True);parser.add_argument('--database',required=True);args=parser.parse_args()
if not args.database.replace('_','').replace('-','').isalnum():raise ValueError('Invalid database name')
root=Path(__file__).resolve().parents[1]
query="SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),extra,COALESCE(collation_name,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_media_account','zsjos_media_account_profile_entry') ORDER BY table_name,ordinal_position;"
command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N --batch --raw "$1"'
r=subprocess.run(['docker','exec','-i',args.container,'sh','-c',command,'profile-verify',args.database],input=query.encode(),capture_output=True)
if r.returncode: raise RuntimeError(r.stderr.decode(errors='replace'))
expected=(root/'output/account-profile-acceptance/expected-schema.tsv').read_text(encoding='utf-8').strip().splitlines()
actual=r.stdout.decode().strip().splitlines();rows={tuple(row.split('\t')[:2]):row for row in actual}
differences=[]
for row in expected:
    key=tuple(row.split('\t')[:2])
    if key not in rows:differences.append('.'.join(key)+': missing')
    elif row!=rows[key]:differences.append('.'.join(key)+': definition differs')
text='\n'.join(differences) if differences else 'PASS: schemas match'
(root/'output/account-profile-acceptance/development-schema-diff.txt').write_text(text+'\n',encoding='utf-8')
print(text)
