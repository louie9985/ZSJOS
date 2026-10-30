# -*- coding: utf-8 -*-
"""Explicit server binding. Application connections are always READ ONLY.

The separate isolated connector can only create a fresh task-owned schema. It never
changes environment identity or weakens the existing local import/repair adapters.
"""
import datetime as dt
import decimal
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

import pymysql
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from zsjos_db import db_config

ROOT = Path(__file__).resolve().parents[5]


def require(ok, message):
    if not ok:
        raise ValueError(message)


def environment():
    marker = Path('/etc/zsjos/agent-environment')
    value = os.environ.get('ZSJOS_AGENT_ENV') if 'ZSJOS_AGENT_ENV' in os.environ else (
        marker.read_text(encoding='utf-8').strip() if marker.exists() else 'local')
    require(value in ('local', 'test', 'production'), 'Invalid explicit environment')
    return value


def encode(value):
    def default(v):
        if isinstance(v, bytes): return {'$bytes': v.hex()}
        if isinstance(v, dt.datetime): return {'$datetime': v.isoformat()}
        if isinstance(v, dt.date): return {'$date': v.isoformat()}
        if isinstance(v, decimal.Decimal): return {'$decimal': str(v)}
        raise TypeError(type(v).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), default=default)


def decode(raw):
    def hook(v):
        if set(v) == {'$bytes'}: return bytes.fromhex(v['$bytes'])
        if set(v) == {'$datetime'}: return dt.datetime.fromisoformat(v['$datetime'])
        if set(v) == {'$date'}: return dt.date.fromisoformat(v['$date'])
        if set(v) == {'$decimal'}: return decimal.Decimal(v['$decimal'])
        return v
    return json.loads(raw, object_hook=hook)


def digest(value):
    return hashlib.sha256(encode(value).encode('utf-8')).hexdigest()


def save(path, value):
    path = Path(path).resolve()
    require(not path.is_relative_to(ROOT), 'Sensitive evidence must be outside the repository')
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(path.parent.stat().st_mode & 0o077 == 0, 'Evidence directory must be private (0700)')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as out:
        raw = encode(value).encode('utf-8')
        out.write(gzip.compress(raw) if path.suffix == '.gz' else raw)
        out.flush(); os.fsync(out.fileno())
    fd = os.open(path.parent, os.O_RDONLY)
    try: os.fsync(fd)
    finally: os.close(fd)


def read(path):
    path = Path(path)
    return decode(gzip.decompress(path.read_bytes()) if path.suffix == '.gz' else path.read_bytes())


class ServerTarget:
    def __init__(self, binding):
        require(environment() == binding['environment'] and environment() in ('test', 'production'),
                'Server environment does not match binding')
        self.binding = binding
        self.config = db_config(binding['environment'])
        c = self.config
        require((c.host, c.port, c.database) == (binding['host'], binding['port'], binding['database']),
                'Configured endpoint differs from explicit target')
        require(binding['tenantId'] == 1 and bool(binding['serverUuid']), 'Explicit tenant/server UUID required')
        require(re.fullmatch(r'[A-Za-z0-9_-]+', c.database), 'Invalid database identifier')
        self.created = set()

    def validate(self, conn, database):
        with conn.cursor() as q:
            q.execute('SELECT DATABASE() db,@@server_uuid uuid,@@version version')
            row = q.fetchone()
            require(row['db'] == database and row['uuid'] == self.binding['serverUuid'], 'Connected target mismatch')
            require(row['version'] == self.binding['mysqlVersion'], 'MySQL engine/version changed')
        conn.rollback()

    def connect_readonly(self):
        c = self.config
        conn = pymysql.connect(host=c.host, port=c.port, user=c.user, password=c.password,
                               database=c.database, charset='utf8mb4', autocommit=False,
                               cursorclass=pymysql.cursors.DictCursor)
        self.validate(conn, c.database)
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
            q.execute('SET SESSION TRANSACTION READ ONLY')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        return conn

    def _isolated_admin(self, database):
        # Existing container credentials are read privately; never printed or put on argv.
        name = self.binding['container']
        meta = json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]
        require(meta['Id'] == self.binding['containerId'], 'Container changed since target binding')
        env = dict(v.split('=', 1) for v in meta['Config']['Env'] if '=' in v)
        password = env.get('MYSQL_ROOT_PASSWORD')
        if not password and env.get('MYSQL_ROOT_PASSWORD_FILE'):
            password = subprocess.check_output(['docker', 'exec', name, 'cat', env['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
        require(bool(password), 'Existing isolated verification credentials unavailable')
        c = self.config
        conn = pymysql.connect(host=c.host, port=c.port, user='root', password=password,
                               database=database, charset='utf8mb4', autocommit=False,
                               cursorclass=pymysql.cursors.DictCursor)
        self.validate(conn, database)
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
        return conn

    def create_isolated(self):
        database = 'person_verify_' + uuid.uuid4().hex[:20]
        conn = self._isolated_admin('information_schema')
        try:
            with conn.cursor() as q:
                q.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
            conn.commit()
        finally: conn.close()
        self.created.add(database)
        return database

    def connect_isolated(self, database):
        require(database in self.created and database != self.config.database, 'Only this run\'s fresh isolated schemas are writable')
        return self._isolated_admin(database)
