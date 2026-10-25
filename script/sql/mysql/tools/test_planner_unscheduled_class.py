#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V343 on retained synthetic MySQL databases; application database stays read-only."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sys
from test_teaching_service_close import fixture, apply as apply339, create, definition, PREFIX
from test_core_baseline import Client
import zsjos_db as db

ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/migrations/V343__planner_unscheduled_class.sql').read_text(encoding='utf-8')

def ledger(c):
    return c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V343'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V343';")

def shape(c):
    # V344 group columns/index are outside this V343 fixture and comparison.
    return c.query("SELECT column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,COALESCE(generation_expression,''),HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name<>'group_id' ORDER BY column_name; SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name<>'idx_delivery_class_group' ORDER BY index_name,seq_in_index;")

def main():
    c = fixture('unscheduled'); apply339(c)
    services = c.query('SELECT id,person_id,status,owner_user_id,class_id FROM zsjos_service_relation;')
    c.query(SQL); first = shape(c); c.query(SQL)
    assert shape(c) == first and ledger(c) == '1\n1'
    assert c.query('SELECT id,person_id,status,owner_user_id,class_id FROM zsjos_service_relation;') == services
    def insert(owner, suffix):
        return c.execute("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,homeroom_user_id,status,tenant_id) "
                         f"VALUES('US{owner}{suffix}','未确定考期',1,'UNSCHEDULED',{owner},'SERVING',1);", expect_success=False).returncode
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda suffix: insert(401,suffix), ['a','b']))
    assert sorted(results) == [0,1], results
    assert insert(402,'a') == 0
    assert c.execute("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,status,tenant_id) VALUES('P2','待分班',1,'PENDING','SERVING',1);",expect_success=False).returncode != 0
    c.query("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,homeroom_user_id,status,tenant_id) VALUES('EX401','异常结业',1,'EXCEPTION',401,'SERVING',1);")
    assert c.query("SELECT DISTINCT HEX(class_name) FROM zsjos_delivery_class WHERE class_kind='UNSCHEDULED';") == '未确定考期'.encode().hex().upper()
    assert c.query("SELECT HEX(description) FROM zsjos_schema_version WHERE version='V343';") == '规划师未确定考期默认班'.encode().hex().upper()
    c.query("UPDATE zsjos_delivery_class SET deleted=1 WHERE class_kind='UNSCHEDULED' AND homeroom_user_id=401;")
    assert insert(401,'restored') == 0
    print('PASS initial/replay, no service move, concurrent uniqueness, pending uniqueness, archive coexistence, logical deletion, UTF-8 HEX',flush=True)

    # Replaying an older migration must preserve the newer generated-column rule.
    # Compare complete synthetic rows, including audit fields and stored ledger hashes.
    snapshot_sql = (
        'SELECT * FROM zsjos_delivery_class ORDER BY id; '
        'SELECT * FROM zsjos_service_relation ORDER BY id; '
        'SELECT * FROM system_menu ORDER BY id; '
        'SELECT * FROM zsjos_schema_version ORDER BY version; '
        'SELECT * FROM zsjos_module_schema_version ORDER BY module_code,version;'
    )
    before_replay = c.query(snapshot_sql)
    apply339(c); apply339(c)
    assert shape(c) == first and c.query(snapshot_sql) == before_replay
    assert insert(403,'after_replay') == 0
    assert c.execute("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,status,tenant_id) VALUES('P3','待分班',1,'PENDING','SERVING',1);",expect_success=False).returncode != 0
    # Partial V343 application can expose the new column before its ledger commit.
    c.query("UPDATE zsjos_schema_version SET version='fixture-V343' WHERE version='V343'; "
            "UPDATE zsjos_module_schema_version SET version='fixture-V343' WHERE module_code='core' AND version='V343';")
    before_replay = c.query(snapshot_sql)
    apply339(c)
    assert shape(c) == first and c.query(snapshot_sql) == before_replay
    c.query(SQL)
    assert shape(c) == first and ledger(c) == '1\n1'
    print('PASS V339 replay after V343 and partial V343 preserves rows, ledger hashes and generated rules; pending uniqueness retained',flush=True)

    missing = fixture('unscheduled_missing'); apply339(missing)
    missing.query("UPDATE zsjos_module_schema_version SET version='fixture-V339' WHERE version='V339';")
    assert missing.execute(SQL,expect_success=False).returncode != 0 and ledger(missing) == '0\n0'
    missing.query("UPDATE zsjos_module_schema_version SET version='V339' WHERE version='fixture-V339';")
    missing.query(SQL); assert ledger(missing) == '1\n1'
    partial = fixture('unscheduled_partial'); apply339(partial)
    partial.query("ALTER TABLE zsjos_delivery_class ADD COLUMN unscheduled_guard bigint GENERATED ALWAYS AS (CASE WHEN class_kind='UNSCHEDULED' AND deleted=0 THEN homeroom_user_id ELSE NULL END) STORED;")
    partial.query(SQL); partial.query(SQL); assert shape(partial) == first
    failure = fixture('unscheduled_failure'); apply339(failure)
    failure.query("CREATE TRIGGER reject_v343 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled ledger failure';")
    assert failure.execute(SQL,expect_success=False).returncode != 0 and ledger(failure) == '0\n0'
    failure.query('DROP TRIGGER reject_v343;'); failure.query(SQL); assert shape(failure) == first
    print('PASS missing prerequisite, partial DDL and second-ledger failure/recovery; no false success markers',flush=True)

    ddl = fixture('unscheduled_ddl'); apply339(ddl)
    ddl.query("ALTER TABLE zsjos_delivery_class MODIFY COLUMN pending_guard bigint GENERATED ALWAYS AS (CASE WHEN class_kind='PENDING' AND deleted=b'0' THEN tenant_id ELSE NULL END) STORED;")
    ddl.query("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,homeroom_user_id,status,tenant_id) VALUES('USdup1','未确定考期',1,'UNSCHEDULED',401,'SERVING',1),('USdup2','未确定考期',1,'UNSCHEDULED',401,'SERVING',1);")
    assert ddl.execute(SQL,expect_success=False).returncode != 0 and ledger(ddl) == '0\n0'
    ddl.query("UPDATE zsjos_delivery_class SET class_kind='NORMAL',system_class=0 WHERE class_no='USdup2';")
    ddl.query(SQL); assert ledger(ddl) == '1\n1'
    print('PASS duplicate prerequisite rows cause DDL failure without success; scoped fixture repair/replay succeeds',flush=True)

    canonical = create(PREFIX+'_us_canonical')
    canonical.query(definition((ROOT/'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8'),'zsjos_delivery_class'))
    assert shape(canonical) == first
    target = Client('ruoyi-vue-pro')
    print('Application read-only V343 markers:',ledger(target).replace('\n','/'),flush=True)
    print('Application default-class columns:',target.query("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name IN ('class_kind','unscheduled_guard','pending_guard');"),flush=True)
    if '--skip-fresh' in sys.argv:
        print('PASS scoped migration checks; complete fresh-chain evidence is run separately',flush=True)
        return
    fresh = create(PREFIX+'_us_fresh')
    fresh.execute_file(db.SQL_ROOT / 'bootstrap.sql')
    manifests = db.load_manifests()
    for migration in db.pending_migrations(manifests, db.installed_versions(fresh)):
        print('Applying',migration.module,migration.version,flush=True)
        fresh.execute_file(migration.path); db.record_migration(fresh,migration)
    assert not db.pending_migrations(manifests,db.installed_versions(fresh))
    drift = db.schema_drift(fresh,manifests)
    assert not [line for line in drift if 'zsjos_delivery_class' in line],drift
    if drift: print('UNRELATED full-schema drift (separate workstreams):',drift,flush=True)
    assert shape(fresh) == first
    print('PASS complete fresh chain, canonical/upgrade definitions; retained schemas '+PREFIX+'_*',flush=True)

if __name__ == '__main__': main()
