# -*- coding: utf-8 -*-
"""Full consistent backup of the bound server database during approved maintenance."""
import argparse
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
from server_target import ServerTarget,read,save,require,ROOT
from server_apply import maintenance_check


def backup(target,output):
    maintenance=maintenance_check()
    require(not output.resolve().is_relative_to(ROOT),'Backup must be outside the repository')
    meta=json.loads(subprocess.check_output(['docker','inspect',target.binding['container']]))[0]
    require(meta['Id']==target.binding['containerId'],'Backup container differs from binding')
    conn=target.connect_readonly()
    try:
        with conn.cursor() as q:
            q.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND engine<>'InnoDB'")
            require(q.fetchone()['n']==0,'Nontransactional table prevents consistent single-transaction backup')
    finally:conn.close()
    output.mkdir(mode=0o700,parents=True,exist_ok=False)
    # Credentials stay in the container; no command-line password or log payload.
    command='''if [ -n "$MYSQL_ROOT_PASSWORD_FILE" ]; then MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"; else MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; fi
export MYSQL_PWD
exec mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --quick --hex-blob --routines --triggers --events --set-gtid-purged=OFF --no-tablespaces "$1"'''
    file=output/'before-merge.sql.gz'
    with (output/'dump.stderr').open('xb') as err:
        proc=subprocess.Popen(['docker','exec',target.binding['container'],'sh','-c',command,'backup',target.binding['database']],stdout=subprocess.PIPE,stderr=err)
        with file.open('xb') as raw:
            with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=1) as out:
                while block:=proc.stdout.read(1024*1024):out.write(block)
            raw.flush();__import__('os').fsync(raw.fileno())
        require(proc.wait()==0,'Backup failed; inspect private stderr, do not merge')
    # Fully read the gzip stream to verify CRC/trailer, not merely file existence.
    count=0
    with gzip.open(file,'rb') as stream:
        while block:=stream.read(1024*1024):count+=len(block)
    require(count>0,'Empty backup')
    require(maintenance_check()==maintenance,'Maintenance state changed during backup')
    receipt={'target':target.binding,'file':str(file.resolve()),'sha256':hashlib.file_digest(file.open('rb'),'sha256').hexdigest(),
             'consistent':True,'maintenanceToken':maintenance,'uncompressedBytes':count,'createdAt':dt.datetime.now(dt.timezone.utc).isoformat()}
    save(output/'backup.json',receipt)
    return {'file':str(file),'sha256':receipt['sha256'],'uncompressedBytes':count}


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--binding',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(backup(ServerTarget(read(a.binding)),a.output)))
