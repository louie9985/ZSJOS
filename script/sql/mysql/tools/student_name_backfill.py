#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Explicit local repair from current Person names; no migration or historical inference.

prepare writes a private recovery plan, apply-local requires its exact SHA256.
Only tenant-1 nondeleted independent services with uninitialized name/source qualify.
No existing names are overwritten; updates preserve all original audit/version fields.
Plans contain personal data: keep outside the repository and never print their contents.
"""
import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from order_history_repair import connect

SOURCE = 'person_current_repair'
SCOPE = "sr.tenant_id=1 AND sr.deleted=0 AND sr.order_id IS NULL"
JOIN = " FROM zsjos_service_relation sr JOIN zsjos_person p ON p.id=sr.person_id AND p.tenant_id=sr.tenant_id AND p.deleted=0"


def candidates(q, lock=False):
    q.execute("SELECT sr.id,sr.person_id,sr.student_name_snapshot,sr.student_name_source,"
              "TRIM(p.name) AS repair_name" + JOIN + " WHERE " + SCOPE
              + " AND sr.student_name_snapshot IS NULL AND sr.student_name_source IS NULL"
              + " AND NULLIF(TRIM(p.name),'') IS NOT NULL ORDER BY sr.id"
              + (" FOR UPDATE" if lock else ""))
    return list(q.fetchall())


def repair(c, plan):
    c.begin()
    try:
        with c.cursor() as q:
            pending = candidates(q, True)
            if pending:
                if pending != plan:
                    raise ValueError('Candidate/source drift; prepare a new reviewed plan')
                q.execute('SELECT * FROM zsjos_service_relation ORDER BY id FOR UPDATE')
                before = list(q.fetchall())
                for row in plan:
                    q.execute("UPDATE zsjos_service_relation SET student_name_snapshot=%s,"
                              "student_name_source=%s,update_time=update_time WHERE id=%s"
                              " AND tenant_id=1 AND deleted=0 AND order_id IS NULL"
                              " AND student_name_snapshot IS NULL AND student_name_source IS NULL",
                              (row['repair_name'], SOURCE, row['id']))
                    if q.rowcount != 1:
                        raise ValueError('Target changed during repair')
                q.execute('SELECT * FROM zsjos_service_relation ORDER BY id')
                after = list(q.fetchall())
                expected = {r['id']: r['repair_name'] for r in plan}
                if len(before) != len(after):
                    raise ValueError('Service count changed')
                for old, new in zip(before, after):
                    wanted = dict(old)
                    if old['id'] in expected:
                        wanted.update(student_name_snapshot=expected[old['id']], student_name_source=SOURCE)
                    if new != wanted:
                        raise ValueError('Unexpected service-column change')
            # Also makes replay fail closed if a repaired row was subsequently changed.
            for row in plan:
                q.execute('SELECT sr.student_name_source,HEX(sr.student_name_snapshot) AS name_hex'
                          + JOIN + ' WHERE ' + SCOPE + ' AND sr.id=%s FOR UPDATE', (row['id'],))
                actual = q.fetchone()
                if actual != {'student_name_source': SOURCE, 'name_hex': row['repair_name'].encode('utf-8').hex().upper()}:
                    raise ValueError('Snapshot UTF-8/source verification failed')
        c.commit()
        return len(pending)
    except Exception:
        c.rollback()
        raise


def fixture():
    from test_core_baseline import create_database
    import uuid
    db = create_database('nr_' + uuid.uuid4().hex[:8])
    db.query('CREATE TABLE zsjos_service_relation LIKE `ruoyi-vue-pro`.zsjos_service_relation')
    db.query('CREATE TABLE zsjos_person(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT(1),name VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')
    db.query("INSERT INTO zsjos_person VALUES(1,1,0,' 核实姓名 '),(2,1,0,'另一姓名'),(3,1,0,'   ')")
    db.query("INSERT INTO zsjos_service_relation(id,person_id,status,activated_at,version,tenant_id,update_time) VALUES(1,1,'active',NOW(),4,1,'2020-01-01'),(2,2,'active',NOW(),5,1,'2020-01-01'),(3,3,'active',NOW(),6,1,'2020-01-01')")
    db.query("INSERT INTO zsjos_service_relation(id,person_id,order_id,status,activated_at,version,tenant_id,student_name_snapshot) VALUES(4,1,NULL,'active',NOW(),0,1,'已有快照'),(5,1,99,'active',NOW(),0,1,NULL)")
    c = connect(database=db.database)
    try:
        with c.cursor() as q: plan = candidates(q)
        assert len(plan) == 2
        db.query("DELIMITER $$\nCREATE TRIGGER reject_second BEFORE UPDATE ON zsjos_service_relation FOR EACH ROW BEGIN IF NEW.id=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'; END IF; END$$\nDELIMITER ;")
        try:
            repair(c, plan)
            raise AssertionError('Expected rollback')
        except Exception as error:
            assert 'fixture failure' in str(error)
        assert db.query('SELECT COUNT(*) FROM zsjos_service_relation WHERE student_name_source IS NOT NULL') == '0'
        db.query('DROP TRIGGER reject_second')
        assert repair(c, plan) == 2
        assert repair(c, plan) == 0
        assert db.query("SELECT COUNT(*) FROM zsjos_service_relation WHERE id IN (1,2,3) AND update_time='2020-01-01'") == '3'
        assert db.query('SELECT HEX(student_name_snapshot) FROM zsjos_service_relation WHERE id=4') == '已有快照'.encode().hex().upper()
        print('PASS actual MySQL atomic rollback/replay/UTF-8/audit preservation/existing-name/order exclusions')
    finally:
        c.close()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('command', choices=['prepare', 'apply-local', 'fixture'])
    p.add_argument('--plan', type=Path)
    p.add_argument('--sha256')
    a = p.parse_args()
    if a.command == 'fixture':
        fixture()
        return
    c = connect()  # Existing local Docker connection only; no remote-target arguments.
    try:
        if a.command == 'prepare':
            with c.cursor() as q: rows = candidates(q)
            directory = Path(tempfile.mkdtemp(prefix='zsjos-student-name-backfill-'))
            path = directory / 'plan.json'
            path.write_text(json.dumps({'database': 'ruoyi-vue-pro', 'tenant': 1, 'source': SOURCE, 'rows': rows}, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps({'count': len(rows), 'plan': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}))
        else:
            if not a.plan or not a.sha256 or hashlib.sha256(a.plan.read_bytes()).hexdigest() != a.sha256:
                raise ValueError('Exact private plan and SHA256 required')
            plan = json.loads(a.plan.read_text(encoding='utf-8'))
            if (plan['database'], plan['tenant'], plan['source']) != ('ruoyi-vue-pro', 1, SOURCE):
                raise ValueError('Plan target mismatch')
            print('PASS local name repair; changed:', repair(c, plan['rows']))
    finally:
        c.close()


if __name__ == '__main__':
    main()
