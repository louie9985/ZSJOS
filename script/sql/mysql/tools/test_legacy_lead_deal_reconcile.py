# -*- coding: utf-8 -*-
"""V304 tests on retained, isolated local MySQL schemas; never mutates development data."""
import concurrent.futures
import datetime as dt
import json
import time
from pathlib import Path
from legacy_lead_deal_reconcile import (query, TABLES, LOCAL_DB, MIGRATION, snapshot,
    signature, verify_diff, preflight, recovery_check, dump, ROOT)

PREFIX = 'v304_' + dt.datetime.now().strftime('%Y%m%d%H%M%S')
DDL = None


def setup(name):
    global DDL
    if DDL is None:
        DDL = '\n'.join(query(LOCAL_DB, f'SHOW CREATE TABLE `{t}`;')[0].split('\t', 1)[1] + ';' for t in TABLES)
    db = PREFIX + '_' + name
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    query(db, DDL)
    sql = """
      INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V303','fixture','fixture');
      INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
        VALUES ('core','V303','fixture','fixture','fixture');
    """
    for i in range(1, 9):
        tenant = 2 if i == 8 else 1
        status = 'converted' if i == 2 else ('won' if i == 6 else 'valid')
        batch = {1: 'legacy-migration', 2: 'legacy-increment-20260917',
                 3: 'legacy-increment-20260921', 4: 'normal-user'}.get(i, 'legacy-migration')
        order_type = 'repurchase' if i == 5 else 'first_purchase'
        order_status = 'terminated' if i == 7 else 'effective'
        sql += f"""
          INSERT INTO zsjos_person(id,person_no,name,identity_status,first_seen_at,last_seen_at,tenant_id)
            VALUES ({i},'P{i}','验证客户{i}','unverified','2026-09-01','2026-09-01',{tenant});
          INSERT INTO zsjos_lead(id,lead_no,person_id,submitted_name,source_type,status,assignment_status,
            submitted_at,owner_user_id,next_follow_up_at,creator,tenant_id)
            VALUES ({i},'LDTEST{i}',{i},'验证客户{i}','partner','{status}','owned',
              '2026-09-01',101,'2026-12-01','legacy-migration',{tenant});
          INSERT INTO zsjos_order(id,order_no,lead_id,person_id,order_type,status,total_amount,payable_amount,
            effective_at,creator,tenant_id)
            VALUES ({i},'ORTEST{i}',{i},{i},'{order_type}','{order_status}',2000,2000,
              '2026-09-02','{batch}',{tenant});
        """
    sql += """
      INSERT INTO zsjos_opportunity(id,person_id,type,lead_id,status,owner_user_id,next_follow_up_at,tenant_id)
        VALUES (1,1,'initial_conversion',1,'open',101,'2026-12-01',1),
               (3,3,'initial_conversion',3,'following',202,'2026-12-01',1);
      INSERT INTO zsjos_lead_intended_product(id,lead_id,product_name_snapshot,spu_ref,spu_name_snapshot,
        sku_ref,sku_name_snapshot,is_primary,tenant_id)
        VALUES (1,2,'历史课程','SP1','历史课程','SK1','历史规格',1,1);
      INSERT INTO zsjos_business_task(id,task_type,biz_type,biz_id,status,assignee_type,assignee_id,
        due_at,create_time,tenant_id) VALUES (1,'lead_follow_up_reminder','lead',2,'pending','system_user',101,
          '2026-12-01','2026-09-03',1);
    """
    query(db, sql)
    return db


def success():
    db = setup('initial')
    before = snapshot(db)
    assert preflight(db)['ready']
    assert len(preflight(db)['rows'].splitlines()) == 3
    print(query(db, MIGRATION)[0])
    after = snapshot(db)
    counts = verify_diff(before, after)
    assert counts['zsjos_lead']['updated'] == 3
    assert counts['zsjos_opportunity'] == {'updated': 2, 'inserted': 1}
    assert counts['zsjos_order']['updated'] == 3
    new = next(p for p in after['zsjos_opportunity'].values() if p['lead_id'] == 2)
    assert new['version'] == 0 and new['owner_user_id'] == 101
    assert new['next_follow_up_at'] == '2026-12-01 00:00:00.000000'
    assert '历史课程' in json.dumps(new['expected_product_summary'], ensure_ascii=False)
    assert query(db, 'SELECT HEX(spu_name_snapshot) FROM zsjos_lead_intended_product WHERE id=1;')[0] == '历史课程'.encode().hex().upper()
    assert after['zsjos_opportunity']['3']['owner_user_id'] == 202
    query(db, MIGRATION)
    assert signature(after) == signature(snapshot(db)), 'repeat changed data or backup'
    assert all(x['matchesAfterImage'] for x in recovery_check(db))
    # Recover a deliberately partial copy of a prior repair, with version records present.
    query(db, """UPDATE zsjos_lead l JOIN zsjos_data_repair_backup b ON b.repair_key='legacy-deal-state-v304'
      AND b.table_name='zsjos_lead' AND b.row_id=l.id AND b.column_name='__before__'
      SET l.status=JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.status')),
      l.version=JSON_EXTRACT(b.old_value,'$.version'),l.updater=JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.updater')),
      l.update_time=CAST(JSON_UNQUOTE(JSON_EXTRACT(b.old_value,'$.updateTime')) AS DATETIME) WHERE l.id=1;
    """)
    query(db, MIGRATION)
    assert signature(after) == signature(snapshot(db)), 'partial recovery drift'
    for table, row_id, fields in [('zsjos_order', 1, {'opportunity_id': 'opportunityId'}),
                                 ('zsjos_opportunity', 3, {'status': 'status', 'won_at': 'wonAt'})]:
        assignments = []
        original = before[table][str(row_id)]
        for key in [*fields, 'version', 'updater', 'update_time']:
            value = original[key]
            encoded = 'NULL' if value is None else (str(value) if isinstance(value, int) else "'" + value.replace("'", "''") + "'")
            assignments.append('t.' + key + '=' + encoded)
        query(db, f"UPDATE {table} t SET " + ','.join(assignments) + f' WHERE id={row_id};')
    query(db, MIGRATION)
    assert signature(after) == signature(snapshot(db)), 'order/opportunity partial recovery drift'
    # Completed replay preserves unrelated edits; destructive recovery still uses full images.
    query(db, "UPDATE zsjos_lead SET version=version+1,remark='后续业务修改' WHERE id=1;")
    assert any(not x['matchesAfterImage'] for x in recovery_check(db))
    before_failure = snapshot(db)
    _, err = query(db, MIGRATION, force=True)
    assert not err
    assert signature(before_failure) == signature(snapshot(db))
    return db


CASES = {
    'missing_owner': "UPDATE zsjos_lead SET owner_user_id=NULL WHERE id=2;",
    'missing_person': "UPDATE zsjos_person SET deleted=b'1' WHERE id=2;",
    'person_mismatch': "UPDATE zsjos_order SET person_id=1 WHERE id=2;",
    'missing_time': "UPDATE zsjos_order SET effective_at=NULL WHERE id=2;",
    'other_order_link': "UPDATE zsjos_order SET opportunity_id=1 WHERE id=2;",
    'opportunity_time': "UPDATE zsjos_opportunity SET won_at='2026-09-03' WHERE id=1;",
    'opportunity_lost': "UPDATE zsjos_opportunity SET status='lost' WHERE id=1;",
    'deleted_opportunity': "INSERT INTO zsjos_opportunity(person_id,type,lead_id,status,owner_user_id,deleted,tenant_id) VALUES (2,'initial_conversion',2,'open',101,b'1',1);",
    'person_opportunity': "INSERT INTO zsjos_opportunity(person_id,type,lead_id,status,owner_user_id,tenant_id) VALUES (2,'initial_conversion',NULL,'open',101,1);",
    'multiple_orders': "INSERT INTO zsjos_order(order_no,lead_id,person_id,order_type,status,total_amount,payable_amount,effective_at,creator,tenant_id) VALUES ('ORSECOND',2,2,'first_purchase','effective',1,1,'2026-09-02','normal-user',1);",
    'pending_approval': "INSERT INTO zsjos_order(order_no,lead_id,person_id,order_type,status,total_amount,payable_amount,creator,tenant_id) VALUES ('ORPENDING',2,2,'repurchase','pending_approval',1,1,'normal-user',1);",
    'public_sea': "INSERT INTO zsjos_lead_public_sea_record(lead_id,owner_user_id,released_by_user_id,released_at,release_reason,tenant_id) VALUES (2,101,101,NOW(),'验证',1);",
    'prerequisite': "UPDATE zsjos_schema_version SET version='V302' WHERE version='V303';",
    'ledger_failure': "CREATE TRIGGER v304_fail BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';",
    'insert_failure': "CREATE TRIGGER v304_fail BEFORE INSERT ON zsjos_opportunity FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected insertion failure';",
}


def failures():
    for name, change in CASES.items():
        db = setup(name)
        query(db, change)
        before = snapshot(db)
        _, err = query(db, MIGRATION, force=True)
        assert 'ERROR' in err, name
        assert signature(before) == signature(snapshot(db)), name + ': failure changed business/ledgers/backups'
        if name in ('ledger_failure', 'insert_failure'):
            query(db, 'DROP TRIGGER v304_fail;')
            query(db, MIGRATION)
            verify_diff(before, snapshot(db))
        print('PASS', name, flush=True)


def concurrency():
    db = setup('concurrency')
    before = snapshot(db)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        first = pool.submit(query, db, MIGRATION.replace('START TRANSACTION;', 'DO SLEEP(1); START TRANSACTION;', 1))
        time.sleep(.2)
        second = pool.submit(query, db, MIGRATION)
        a, b = first.result(), second.result()
    counts = verify_diff(before, snapshot(db))
    assert counts['zsjos_opportunity']['inserted'] == 1
    assert 'already_completed_verified' in a[0] + b[0]
    print('PASS concurrency', flush=True)


def extended():
    db = setup('deleted_exclusions')
    query(db, "UPDATE zsjos_lead SET deleted=b'1' WHERE id=1; "
              "UPDATE zsjos_order SET deleted=b'1' WHERE id=3; "
              "UPDATE zsjos_lead_intended_product SET deleted=b'1' WHERE lead_id=2;")
    before = snapshot(db)
    assert query(db, MIGRATION)[0] == '1\t1\t0\t1\t1'
    after = snapshot(db)
    verify_diff(before, after)
    assert next(p for p in after['zsjos_opportunity'].values() if p['lead_id'] == 2)['expected_product_summary'] == []
    print('PASS deleted Lead/order exclusion and empty product snapshot', flush=True)
    db = setup('empty')
    query(db, "UPDATE zsjos_order SET creator='not-an-approved-import';")
    before = snapshot(db)
    assert query(db, MIGRATION)[0] == '0\t0\t0\t0\t0'
    verify_diff(before, snapshot(db))
    print('PASS empty candidate set', flush=True)
    db = setup('already_linked')
    query(db, "UPDATE zsjos_order SET opportunity_id=1 WHERE id=1;")
    before = snapshot(db)
    query(db, MIGRATION)
    after = snapshot(db)
    assert after['zsjos_order']['1'] == before['zsjos_order']['1']
    verify_diff(before, after)
    query(db, "UPDATE zsjos_opportunity SET version=version+1 WHERE lead_id=2;")
    before = snapshot(db)
    _, err = query(db, MIGRATION, force=True)
    assert not err
    assert signature(before) == signature(snapshot(db))
    print('PASS already-correct link and inserted-row edit guard', flush=True)


def clone_local():
    db = PREFIX + '_clone'
    directory = ROOT / 'backups/mysql' / db
    directory.mkdir(parents=True, exist_ok=False)
    dump(LOCAL_DB, directory / 'source.sql')
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    query(db, (directory / 'source.sql').read_text(encoding='utf-8'))
    before = snapshot(db)
    result = preflight(db)
    assert result['ready'], result['rows']
    output = query(db, MIGRATION)[0]
    after = snapshot(db)
    report = {'database': db, 'output': output, 'counts': verify_diff(before, after),
              'beforeHash': signature(before), 'afterHash': signature(after)}
    query(db, MIGRATION)
    assert signature(after) == signature(snapshot(db))
    (directory / 'verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', choices=['all', 'extended', 'clone'], default='all')
    args = parser.parse_args()
    print('Retained isolated databases:', PREFIX, flush=True)
    if args.suite == 'clone':
        clone_local()
    elif args.suite == 'extended':
        extended()
    else:
        print('PASS initial/repeat/partial/post-edit guard', success(), flush=True)
        failures()
        concurrency()
        extended()
    print('ALL PASS', flush=True)
