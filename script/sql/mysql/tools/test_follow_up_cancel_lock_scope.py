# -*- coding: utf-8 -*-
"""MySQL 8 lock regression using synthetic rows in a retained isolated schema."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import time
from test_positioning_single_source import query

schema = 'follow_up_lock_' + datetime.now().strftime('%Y%m%d%H%M%S%f')
query('ruoyi-vue-pro', f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4;')
query(schema, 'CREATE TABLE zsjos_business_task LIKE `ruoyi-vue-pro`.zsjos_business_task;')
rows = [f"({i},'lead_follow_up_reminder','lead',{7 if i == 1000 else i + 1000},'pending','user',1,1)"
        for i in range(1, 2001)]
rows += ["(3001,'lead_follow_up_reminder','other',7,'pending','user',1,1)",
         "(3002,'lead_follow_up_reminder','lead',7,'pending','user',1,2)",
         "(3003,'lead_follow_up_reminder','lead',7,'completed','user',1,1)"]
query(schema, 'INSERT INTO zsjos_business_task(id,task_type,biz_type,biz_id,status,assignee_type,assignee_id,tenant_id) VALUES '
      + ','.join(rows) + '; ANALYZE TABLE zsjos_business_task;')
predicate = "deleted=0 AND task_type='lead_follow_up_reminder' AND biz_id=7 AND status='pending' AND tenant_id=1"
old = "UPDATE zsjos_business_task SET status='cancelled' WHERE " + predicate
fixed = old + " AND biz_type='lead'"
assert 'idx_tenant_biz' in query(schema, 'EXPLAIN ' + fixed)

with ThreadPoolExecutor(max_workers=1) as executor:
    for locked_id in (1, 1000):
        lock_name = schema + '_' + str(locked_id)
        held = executor.submit(query, schema, f"START TRANSACTION; SELECT id FROM zsjos_business_task WHERE id={locked_id} FOR UPDATE; SELECT GET_LOCK('{lock_name}',0); SELECT SLEEP(6); ROLLBACK; SELECT RELEASE_LOCK('{lock_name}');")
        deadline = time.monotonic() + 10
        while query(schema, f"SELECT IS_USED_LOCK('{lock_name}');").strip() == 'NULL':
            if time.monotonic() > deadline:
                raise AssertionError('Lock holder did not become ready')
            time.sleep(0.05)
        # The former broad UPDATE is blocked by an unrelated row; the scoped one bypasses it.
        blocked = old if locked_id == 1 else fixed
        try:
            query(schema, 'SET innodb_lock_wait_timeout=1; ' + blocked)
        except RuntimeError as error:
            assert 'Lock wait timeout' in str(error)
        else:
            raise AssertionError('Expected lock conflict')
        if locked_id == 1:
            query(schema, 'SET innodb_lock_wait_timeout=1; ' + fixed)
            assert query(schema, "SELECT id FROM zsjos_business_task WHERE status='cancelled';").strip() == '1000'
            query(schema, "UPDATE zsjos_business_task SET status='pending' WHERE id=1000;")
        held.result()
query(schema, fixed)
assert query(schema, "SELECT id FROM zsjos_business_task WHERE status='cancelled';").strip() == '1000'
print('PASS unrelated lock bypass, same-target serialization, tenant/business/status isolation;', schema)
