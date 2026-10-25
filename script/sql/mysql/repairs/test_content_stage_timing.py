#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Focused pure tests; --mysql additionally retains an isolated local MySQL schema."""
import copy
import datetime as dt
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import unittest

import pymysql

spec = importlib.util.spec_from_file_location('timing', Path(__file__).with_name('content_stage_timing.py'))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
START = dt.datetime(2026, 10, 18, 23, 59)


def entry(eid, day, stage, initial=False):
    return {'id': eid, 'account_id': 1, 'create_time': START + dt.timedelta(days=day),
            'field_key': 'diagnosis_initial' if initial else 'diagnosis_7d',
            'content': json.dumps({'currentStage': stage}), 'result_version': eid,
            'snapshot_json': json.dumps([{'key': 'stage', 'value': stage}])}


def state():
    return {'accounts': [{'id': 1, 's_stage': 'S1', 's_stage_entered_at': None,
                          'detail_values_json': '{}', 'student_person_id': 10, 'version': 3}],
            'entries': [entry(1, 0, 'S1', True), entry(2, 1, 'S1'), entry(3, 2, 'S2'), entry(4, 3, 'S1')],
            'batches': [{'id': 1, 'account_id': 1, 'account_ids_json': '[1]', 'student_person_id': 10,
                         'submitted_at': START + dt.timedelta(days=1, minutes=2), 'status': 'COMPLETED', 'version': 1,
                         'context_snapshot_json': json.dumps({'accountSnapshots': [{'id': 1, 'sStage': 'S1', 'nickname': '历史中文'}],
                                                             'account': {'id': 1, 'sStage': 'S1'}})}]}


class EvidenceTests(unittest.TestCase):
    def test_repeated_stage_and_return_have_independent_origins(self):
        plan = t.build(state(), START + dt.timedelta(days=5))
        self.assertEqual(2, len(plan['changes']))
        self.assertEqual(START + dt.timedelta(days=3), plan['changes'][0]['set']['s_stage_entered_at'])
        context = json.loads(plan['changes'][1]['set']['context_snapshot_json'])
        self.assertEqual(2, context['accountSnapshots'][0]['sStageDurationDays'])
        self.assertEqual(t.millis(START), context['account']['sStageEnteredAt'])
        self.assertEqual('历史中文', context['accountSnapshots'][0]['nickname'])
        self.assertNotIn('历史中文', t.serialized(t.public_plan(plan)))

    def test_incomplete_corrupt_duplicate_or_conflicting_history_is_rejected(self):
        variants = []
        s = state(); s['entries'].pop(0); variants.append(s)
        s = state(); s['entries'][1]['content'] = '{'; variants.append(s)
        s = state(); s['entries'][1]['create_time'] = START; variants.append(s)
        s = state(); s['entries'][1]['result_version'] = 0; variants.append(s)
        s = state(); s['entries'][1]['snapshot_json'] = '[]'; variants.append(s)
        s = state(); s['entries'][1]['field_key'] = 'diagnosis_initial'; variants.append(s)
        for sample in variants:
            with self.subTest(sample=variants.index(sample)):
                self.assertEqual([], t.build(sample, START + dt.timedelta(days=5))['changes'])

    def test_current_mismatch_does_not_prevent_proven_historical_interval(self):
        s = state(); s['accounts'][0]['s_stage'] = 'other'
        plan = t.build(s, START + dt.timedelta(days=5))
        self.assertEqual(['batches'], [c['kind'] for c in plan['changes']])

    def test_partial_existing_fields_and_missing_stage_are_not_overwritten(self):
        s = state(); context = json.loads(s['batches'][0]['context_snapshot_json'])
        context['accountSnapshots'][0]['sStageDurationDays'] = 99
        s['batches'][0]['context_snapshot_json'] = json.dumps(context)
        self.assertEqual(['accounts'], [c['kind'] for c in t.build(s, START + dt.timedelta(days=5))['changes']])
        s = state(); s['batches'][0]['submitted_at'] = START
        self.assertEqual(['accounts'], [c['kind'] for c in t.build(s, START + dt.timedelta(days=5))['changes']])

    def test_invalid_scope_time_and_draft_are_excluded(self):
        for field, value in [('student_person_id', 99), ('account_ids_json', '[9]'),
                             ('submitted_at', None), ('status', 'DRAFT')]:
            s = state(); s['batches'][0][field] = value
            if field == 'account_ids_json': s['batches'][0]['account_id'] = 9
            self.assertEqual(['accounts'], [c['kind'] for c in t.build(s, START + dt.timedelta(days=5))['changes']])

    def test_single_account_legacy_snapshot(self):
        s = state(); ctx = json.loads(s['batches'][0]['context_snapshot_json']); del ctx['accountSnapshots']
        s['batches'][0]['context_snapshot_json'] = json.dumps(ctx)
        change = t.build(s, START + dt.timedelta(days=5))['changes'][1]
        self.assertEqual(2, json.loads(change['set']['context_snapshot_json'])['account']['sStageDurationDays'])


def local_connection(database):
    meta = json.loads(subprocess.check_output(['docker', 'inspect', 'yudao-mysql']))[0]
    env = dict(item.split('=', 1) for item in meta['Config']['Env'] if '=' in item)
    password = env.get('MYSQL_ROOT_PASSWORD')
    if password is None:
        password = subprocess.check_output(['docker', 'exec', 'yudao-mysql', 'cat', env['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    port = meta['NetworkSettings']['Ports']['3306/tcp'][0]
    return pymysql.connect(host='127.0.0.1', port=int(port['HostPort']), user='root', password=password,
                           database=database, charset='utf8mb4', autocommit=False, cursorclass=pymysql.cursors.DictCursor)


def mysql_verification():
    database = 'stage_timing_verify_' + dt.datetime.now().strftime('%Y%m%d_%H%M%S')
    conn = local_connection('ruoyi-vue-pro')
    with conn.cursor() as q:
        q.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        for table in t.TABLES.values():
            q.execute(f'CREATE TABLE `{database}`.{table} LIKE `ruoyi-vue-pro`.{table}')
    conn.close()
    conn = local_connection(database)
    root = Path(__file__).resolve().parents[4]
    evidence = root / 'frontend/workbench/tmp' / database
    evidence.mkdir(mode=0o700)
    try:
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute("INSERT INTO zsjos_media_account(id,account_no,ownership_type,student_person_id,s_stage,detail_values_json,tenant_id,version) VALUES (1,'fixture','STUDENT',10,'S1','{}',1,3),(2,'fixture-deleted','STUDENT',10,'S1','{}',1,3),(3,'fixture-other-tenant','STUDENT',10,'S1','{}',2,3)")
            q.execute('UPDATE zsjos_media_account SET deleted=1 WHERE id=2')
            for row in state()['entries']:
                q.execute('INSERT INTO zsjos_media_account_profile_entry(id,account_id,operated_by_user_id,kind,field_key,title,content,snapshot_json,idempotency_key,fingerprint,result_version,create_time,tenant_id) VALUES(%s,1,1,\'DIAGNOSIS\',%s,\'fixture\',%s,%s,%s,%s,%s,%s,1)',
                          (row['id'], row['field_key'], row['content'], row['snapshot_json'], str(row['id']), 'a'*64, row['result_version'], row['create_time']))
            q.execute('INSERT INTO zsjos_content_review_batch(id,batch_no,account_id,student_person_id,account_ids_json,operator_user_id,director_user_id,status,current_stage,version,tenant_id,submitted_at,context_snapshot_json) VALUES(1,\'fixture\',1,10,\'[1]\',1,2,\'COMPLETED\',\'FINAL\',1,1,%s,%s)',
                      (state()['batches'][0]['submitted_at'], state()['batches'][0]['context_snapshot_json']))
        conn.commit()
        with conn.cursor() as q:
            before = t.capture(q)
        conn.rollback()
        plan = t.build(before, START + dt.timedelta(days=5))
        assert len(plan['changes']) == 2
        # Failure after account write must roll back all candidate rows.
        with conn.cursor() as q:
            q.execute("CREATE TRIGGER reject_stage_batch BEFORE UPDATE ON zsjos_content_review_batch FOR EACH ROW BEGIN IF @stage_fail=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected'; END IF; END")
            q.execute("CREATE TRIGGER corrupt_stage_account BEFORE UPDATE ON zsjos_media_account FOR EACH ROW BEGIN IF @stage_corrupt=1 THEN SET NEW.s_stage='CORRUPT'; END IF; END")
            q.execute('SET @stage_fail=1')
        try:
            t.apply_plan(conn, before, copy.deepcopy(plan), evidence/'failed-before.json')
            raise AssertionError('failure not propagated')
        except pymysql.MySQLError:
            conn.rollback()
        with conn.cursor() as q:
            assert t.digest(t.capture(q)) == t.digest(before)
            q.execute('SET @stage_fail=0')
        conn.rollback()
        with conn.cursor() as q:
            q.execute('SET @stage_corrupt=1')
        try:
            t.apply_plan(conn, before, copy.deepcopy(plan), evidence/'corrupt-before.json')
            raise AssertionError('unrelated mutation accepted')
        except RuntimeError as error:
            assert str(error) == 'UNRELATED_FIELD_CHANGED'
            conn.rollback()
        with conn.cursor() as q:
            assert t.digest(t.capture(q)) == t.digest(before)
            q.execute('SET @stage_corrupt=0')
        conn.rollback()
        # Source mutation must reject a reviewed plan before the first update.
        changed = copy.deepcopy(before); changed['entries'][0]['result_version'] = 999
        try:
            t.apply_plan(conn, changed, copy.deepcopy(plan), evidence/'stale-before.json')
            raise AssertionError('stale source accepted')
        except RuntimeError:
            conn.rollback()
        t.apply_plan(conn, before, copy.deepcopy(plan), evidence/'before.json')
        with conn.cursor() as q:
            after = t.capture(q)
            assert t.build(after, START + dt.timedelta(days=5))['changes'] == []
            q.execute('SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(context_snapshot_json,\'$.accountSnapshots[0].nickname\'))) value FROM zsjos_content_review_batch WHERE id=1')
            assert q.fetchone()['value'] == '历史中文'.encode().hex().upper()
            q.execute('SELECT id,s_stage_entered_at FROM zsjos_media_account WHERE id IN(2,3) ORDER BY id')
            assert all(row['s_stage_entered_at'] is None for row in q.fetchall())
        conn.rollback()
        backup = t.read_backup(evidence/'before-verified.json')
        # Later writes forbid restoration; version is never rewound.
        with conn.cursor() as q:
            q.execute('UPDATE zsjos_media_account SET version=version+1 WHERE id=1')
        try:
            t.restore(conn, copy.deepcopy(backup))
            raise AssertionError('changed restore target accepted')
        except RuntimeError:
            conn.rollback()
        t.restore(conn, backup)
        with conn.cursor() as q:
            restored = t.capture(q)
            assert restored['accounts'][0]['s_stage_entered_at'] is None
            assert json.loads(restored['accounts'][0]['detail_values_json']) == {}
            assert json.loads(restored['batches'][0]['context_snapshot_json']) == json.loads(before['batches'][0]['context_snapshot_json'])
            assert restored['accounts'][0]['version'] == before['accounts'][0]['version'] + 2
        conn.rollback()
        print('MySQL PASS: apply, repeat, rollback, stale plan, restore, changed restore refusal, scope, UTF-8 HEX; retained ' + database)
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    mysql = '--mysql' in sys.argv
    if mysql: sys.argv.remove('--mysql')
    result = unittest.main(exit=False).result
    if not result.wasSuccessful(): sys.exit(1)
    if mysql: mysql_verification()
