#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Guarded old-notice V295 ledger recovery; caller owns target authorization and backup.

This module never opens a connection or applies other migrations. It archives the exact
two version rows outside MySQL before removing only their conflicting V295 identities.
V313 is registered later by its ordinary migration, after the real V312.
"""
import hashlib
import json
import os
from pathlib import Path

OLD_CHECKSUM = hashlib.sha256(b'V295__notice_content_capacity.sql').hexdigest()


def inspect(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT DATABASE() AS db")
        database = cur.fetchone()['db']
        cur.execute("SELECT * FROM zsjos_schema_version WHERE version='V295'")
        global_rows = cur.fetchall()
        cur.execute("SELECT * FROM zsjos_module_schema_version WHERE module_code='core' AND version='V295'")
        module_rows = cur.fetchall()
        cur.execute("SELECT column_type,is_nullable,character_set_name,collation_name,HEX(column_comment) AS comment_hex "
                    "FROM information_schema.columns WHERE table_schema=DATABASE() "
                    "AND table_name='system_notice' AND column_name='content'")
        column = cur.fetchone()
    expected = {'column_type': 'mediumtext', 'is_nullable': 'NO',
                'character_set_name': 'utf8mb4', 'collation_name': 'utf8mb4_unicode_ci',
                'comment_hex': '公告内容'.encode().hex().upper()}
    # MySQL drivers may preserve information_schema field-name case.
    column = {k.lower(): v for k, v in (column or {}).items()}
    if column != expected:
        raise ValueError('Notice structure differs from verified old expansion')
    if len(global_rows) != 1 or len(module_rows) != 1:
        raise ValueError('Expected exactly both old V295 rows; manual partial-state review required')
    for row in global_rows + module_rows:
        if row['description'] != 'Notice content capacity' or row['checksum'] != OLD_CHECKSUM:
            raise ValueError('V295 is not the exact old notice identity; refusing recovery')
    if module_rows[0]['release_version'] != 'baseline':
        raise ValueError('Unexpected old notice release provenance')
    return {'database': database, 'global': global_rows, 'module': module_rows, 'column': column}


def release_old_version(conn, archive):
    """Archive first, then atomically release only the verified two old version rows."""
    before = inspect(conn)
    path = Path(archive)
    payload = json.dumps(before, ensure_ascii=False, sort_keys=True, default=str, indent=2) + '\n'
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())
    conn.begin()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM zsjos_schema_version WHERE version='V295' FOR UPDATE")
            if cur.fetchall() != before['global']:
                raise ValueError('Global ledger changed after archive')
            cur.execute("SELECT * FROM zsjos_module_schema_version WHERE module_code='core' AND version='V295' FOR UPDATE")
            if cur.fetchall() != before['module']:
                raise ValueError('Module ledger changed after archive')
            cur.execute("DELETE FROM zsjos_schema_version WHERE version='V295' AND checksum=%s", (OLD_CHECKSUM,))
            if cur.rowcount != 1:
                raise ValueError('Global ledger identity changed')
            cur.execute("DELETE FROM zsjos_module_schema_version WHERE module_code='core' AND version='V295' AND checksum=%s", (OLD_CHECKSUM,))
            if cur.rowcount != 1:
                raise ValueError('Module ledger identity changed')
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    return hashlib.sha256(payload.encode()).hexdigest()
