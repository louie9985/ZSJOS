"""UTF-8. Retain isolated MySQL fixtures; never change the source development database."""
from pathlib import Path
from uuid import uuid4
from test_positioning_single_source import query

source = 'ruoyi-vue-pro'
db = 'textbook_shipping_check_' + uuid4().hex[:10]
sql = (Path(__file__).resolve().parents[1] / 'migrations/V382__textbook_shipping_menu.sql').read_text('utf8')
query(source, 'CREATE DATABASE ' + db + ' CHARACTER SET utf8mb4')
for table in ('system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version'):
    query(db, f'CREATE TABLE {table} LIKE `{source}`.{table}')
query(db, "CREATE TABLE zsjos_order_item(id BIGINT PRIMARY KEY, order_id BIGINT, tenant_id BIGINT, deleted BIT DEFAULT 0); INSERT INTO zsjos_order_item(id,order_id,tenant_id) VALUES(1,11,1)")
query(db, f"INSERT INTO system_menu SELECT * FROM `{source}`.system_menu WHERE path='/zsjos' AND type=1 AND deleted=0")

def markers():
    return query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V382'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V382'").split()

def reject():
    try:
        query(db, sql)
    except RuntimeError:
        return
    raise AssertionError('expected refusal')

reject()
assert markers() == ['0', '0']
for table in ('zsjos_schema_version', 'zsjos_module_schema_version'):
    query(db, f"INSERT INTO {table} SELECT * FROM `{source}`.{table} WHERE version='V381'")
query(db, 'ALTER TABLE zsjos_order_item ADD COLUMN textbook_carrier INT')
reject()
assert markers() == ['0', '0']
query(db, 'ALTER TABLE zsjos_order_item MODIFY textbook_carrier VARCHAR(100) NULL')
query(db, "CREATE TRIGGER fail382 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture'")
reject()
assert markers() == ['0', '0'] and query(db,'SELECT COUNT(*) FROM system_menu').strip() == '1'
query(db, 'DROP TRIGGER fail382')
query(db, sql)
assert markers() == ['1', '1']
assert query(db,'SELECT textbook_carrier IS NULL,textbook_tracking_no IS NULL,textbook_shipping_version FROM zsjos_order_item').split() == ['1','1','0']
query(db, "UPDATE zsjos_order_item SET textbook_carrier='测试快递',textbook_tracking_no='0001',textbook_shipping_version=1 WHERE id=1")
query(db, sql)
assert query(db,'SELECT HEX(textbook_carrier),textbook_tracking_no,textbook_shipping_version FROM zsjos_order_item').split() == ['测试快递'.encode().hex().upper(),'0001','1']
assert set(query(db,"SELECT HEX(name) FROM system_menu WHERE permission LIKE 'zsjos:textbook-shipping:%'").split()) == {s.encode().hex().upper() for s in ('教材邮寄','查看所有邮寄信息','填写邮寄信息')}
query(db, "UPDATE system_menu SET type=2 WHERE permission='zsjos:textbook-shipping:update'")
reject()
print('PASS V382 prerequisite, partial DDL, bad schema, transactional failure, recovery, replay, scope, preserved shipment and UTF-8', db)
