# -*- coding: utf-8 -*-
"""Isolated MySQL test of target-source locking and idempotent merge storage."""
import subprocess, sys, threading, time, uuid

container = sys.argv[1]
db = 'perf_merge_' + uuid.uuid4().hex[:12]
def run(sql):
    p = subprocess.run(['docker','exec','-i',container,'mysql','--default-character-set=utf8mb4','-uroot','--batch','--skip-column-names'], input=sql.encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode: raise RuntimeError(p.stderr.decode('utf-8',errors='replace'))
    return p.stdout.decode('utf-8').strip()
run(f'''CREATE DATABASE `{db}`; USE `{db}`;
CREATE TABLE target(id BIGINT PRIMARY KEY, domain VARCHAR(16) NOT NULL, scope_id BIGINT NOT NULL, period_start DATE NOT NULL, dept_id BIGINT NOT NULL, amount DECIMAL(12,2) NOT NULL, version INT NOT NULL, merged_delivery_target_id BIGINT NULL, UNIQUE KEY uk_target(domain,scope_id,period_start));
CREATE TABLE revision(id BIGINT AUTO_INCREMENT PRIMARY KEY,target_id BIGINT NOT NULL,amount DECIMAL(12,2) NOT NULL);
INSERT INTO target VALUES(1,'sales',7,'2026-11-01',10,100,0,NULL),(2,'delivery',7,'2026-11-01',11,200,0,NULL);
INSERT INTO revision(target_id,amount) VALUES(1,100),(2,200);''')
assert run(f"USE `{db}`; SELECT SUM(amount) FROM target WHERE scope_id=7;") == '300.00'
started = threading.Event()
def holder():
    started.set()
    run(f"USE `{db}`; START TRANSACTION; SELECT id FROM target WHERE scope_id=7 AND period_start='2026-11-01' ORDER BY domain FOR UPDATE; DO SLEEP(2); UPDATE target SET amount=50,dept_id=20,version=version+1,merged_delivery_target_id=2 WHERE id=1; INSERT INTO revision(target_id,amount) VALUES(1,50); COMMIT;")
t = threading.Thread(target=holder);t.start();started.wait();time.sleep(.5)
start=time.monotonic()
result=run(f"USE `{db}`; START TRANSACTION; SELECT CONCAT(GROUP_CONCAT(CONCAT(id,':',version) ORDER BY domain DESC SEPARATOR '|')) FROM target WHERE scope_id=7 AND period_start='2026-11-01' FOR UPDATE; COMMIT;")
t.join();assert time.monotonic()-start > 1
assert result == '1:1|2:0', result
assert result != '1:0|2:0'
assert run(f"USE `{db}`; SELECT CASE WHEN s.merged_delivery_target_id=d.id THEN s.amount ELSE s.amount+d.amount END FROM target s JOIN target d ON d.id=2 WHERE s.id=1;") == '50.00'
assert run(f"USE `{db}`; SELECT COUNT(*) FROM revision WHERE target_id IN (1,2);") == '3'
assert run(f"USE `{db}`; SELECT dept_id FROM target WHERE id=1;") == '20'
print('PASS isolated SQL: first sum, row locks, stale versions, smaller saved total, no repeat addition, current department, preserved revisions; schema='+db)
