# -*- coding: utf-8 -*-
"""Run scope regressions using read-only JDBC/CTE queries on the configured development MySQL."""
from pathlib import Path
import argparse
import os
import re
import runpy

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--actor', help='Optional account username for an existing SELF regression; never a password')
parser.add_argument('--student-number', help='Existing student business number for the optional target regression')
args = parser.parse_args()
if bool(args.actor) != bool(args.student_number):
    parser.error('--actor and --student-number must be supplied together')
if args.actor:
    os.environ.update(ZSJOS_SCOPE_TARGET_USERNAME=args.actor, ZSJOS_SCOPE_TARGET_STUDENT=args.student_number)
config = (root / 'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8')
match = re.search(r'url: jdbc:mysql://([^:/]+):(\d+)/([^?\s]+).*?\n\s*username:\s*([^\s#]+)\s*\n\s*password:\s*([^\s#]+)', config, re.S)
if not match:
    raise RuntimeError('Local datasource could not be resolved')
host, port, database, user, password = match.groups()
os.environ.update(ZSJOS_CLASS_MYSQL_VERIFY='true',
                  ZSJOS_CLASS_MYSQL_URL=f'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai',
                  ZSJOS_CLASS_MYSQL_USER=user, ZSJOS_CLASS_MYSQL_PASSWORD=password.strip('\"\''))
runpy.run_path(str(Path(__file__).with_name('verify_java.py')), run_name='__main__')
