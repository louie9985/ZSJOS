# UTF-8. Retained synthetic database; no real account grants or business data writes.
import datetime as dt
from pathlib import Path
from test_positioning_single_source import query
ROOT=Path(__file__).resolve().parents[4]
MIGRATION=ROOT/'script/sql/mysql/migrations/V276__sales_performance.sql'
def verify_baseline_tables(db):
 import re
 expected=[]
 for filename in ['00-bootstrap-schema.sql','schema/core.sql']:
  text=(ROOT/'script/sql/mysql'/filename).read_text(encoding='utf-8')
  tables=re.findall(r'CREATE TABLE IF NOT EXISTS zsjos_performance_.*?ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;',text,re.S)
  assert len(tables)==4
  expected.append(tables)
 assert expected[0]==expected[1]
 fresh=db+'_fresh'
 query('ruoyi-vue-pro',f'CREATE DATABASE `{fresh}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
 query(fresh,'\n'.join(expected[0]))
 for table in ['org','target','revision','attribution']:
  sql=f"SELECT column_name,column_type,is_nullable,COALESCE(column_default,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_{table}' ORDER BY ordinal_position;"
  assert query(fresh,sql)==query(db,sql)
 print('PASS scoped fresh baseline tables equal controlled upgrade schema:',fresh)

def verify_queries(db):
 import re
 import xml.etree.ElementTree as ET
 tables=['zsjos_order','zsjos_order_item','zsjos_lead','zsjos_lead_assignment_history','zsjos_business_task','zsjos_lead_follow_up_record','zsjos_opportunity_follow_up_record']
 for table in tables:query(db,f'CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`;')
 query(db,"""INSERT INTO zsjos_lead(id,lead_no,person_id,submitted_name,source_type,status,assignment_status,submitted_at,tenant_id,lead_category_label_snapshot) VALUES(1,'KZ-TEST-001',1,'测试','internal_new_media','won','owned','2026-09-01',991,'测试分类');
 INSERT INTO zsjos_lead_assignment_history(id,lead_id,action_type,operator_user_id,occurred_at,to_owner_user_id,tenant_id) VALUES(1,1,'accept',1,'2026-09-01',1,991);
 INSERT INTO zsjos_order(id,order_no,person_id,lead_id,formal_sales_user_id,order_type,status,total_amount,payable_amount,submitted_at,tenant_id) VALUES(1,'ORDER-TEST-001',1,1,1,'first_purchase','effective',100,100,'2026-09-02',991),(2,'ORDER-TEST-002',1,1,1,'first_purchase','pending_approval',900,900,'2026-09-02',991),(3,'OTHER-TENANT',1,1,1,'first_purchase','effective',500,500,'2026-09-02',992);
 INSERT INTO zsjos_order_item(order_id,unit_price,payable_amount,product_ref,product_snapshot,tenant_id) VALUES(1,100,100,'P1','{\"name\":\"测试课程\"}',991);
 INSERT INTO zsjos_performance_attribution(fact_type,fact_id,user_id,dept_id,center_id,lead_id,assignment_id,received_at,tenant_id) VALUES('ORDER',1,1,11,10,1,1,'2026-09-01',991),('ASSIGNMENT',1,1,11,10,1,1,'2026-09-01',991);
 """)
 text=(ROOT/'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceFactMapper.java').read_text(encoding='utf-8')
 statements=re.findall(r'@Select\("""\s*(.*?)\s*"""\)\s*List<PerformanceFact> (\w+)',text,re.S)
 for scope,identifier in [('USER',1),('DEPT',11),('CENTER',10)]:
  def render(element):
   result=element.text or ''
   for child in element:
    if child.tag=='choose':
     match=next((c for c in child if c.tag=='when' and scope in re.findall("'([^']+)'",c.attrib['test'])),next((c for c in child if c.tag=='otherwise'),None))
     result+=render(match) if match is not None else ''
    elif child.tag=='foreach':result+='(1)'
    else:result+=render(child)
    result+=child.tail or ''
   return result
  for xml,name in statements:
   sql=render(ET.fromstring(xml)).replace('#{tenant}','991').replace('#{id}',str(identifier))
   result=query(db,sql).strip()
   if name in ('orders','receipts','products'):assert len(result.splitlines())==1,(scope,name,result)
   if name=='orders':assert 'ORDER-TEST-001' in result and '900' not in result and 'OTHER-TENANT' not in result
   if name=='products':assert '测试课程' in result
 print('PASS actual mapper SQL: user/department/center scopes, approval filtering, tenant isolation, history joins, products')

def main():
 db='performance_verify_'+dt.datetime.now().strftime('%Y%m%d%H%M%S')
 query('ruoyi-vue-pro',f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
 for table in ['system_menu','zsjos_schema_version','zsjos_module_schema_version','system_role_menu']:
  query(db,f'CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`;')
 query(db,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,deleted) VALUES('中世健工作台','',1,1,0,'/zsjos','','',0,1,1,0,0);")
 query(db,"INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V275','synthetic prerequisite','test');")
 sql=MIGRATION.read_text(encoding='utf-8');query(db,sql)
 assert query(db,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_performance_%';").strip()=='4'
 assert query(db,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:sales-performance%';").strip()=='8'
 query(db,"INSERT INTO zsjos_performance_target(scope_type,scope_id,period_type,period_start,floor_amount,sprint_amount,manual,reason,tenant_id) VALUES('USER',1,'month','2026-09-01',100,200,1,'测试目标',991),('USER',1,'month','2026-09-01',300,400,1,'其他租户',992);")
 query(db,"UPDATE system_menu SET name='管理员维护的业绩统计' WHERE permission='zsjos:sales-performance:query';")
 query(db,sql)
 assert query(db,"SELECT COUNT(*) FROM zsjos_performance_target;").strip()=='2'
 assert query(db,"SELECT COUNT(*) FROM system_role_menu;").strip()=='0'
 assert query(db,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:sales-performance%';").strip()=='8'
 assert query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:sales-performance:query';").strip()== '管理员维护的业绩统计'.encode().hex().upper()
 assert query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V276';").strip()=='1'
 verify_baseline_tables(db)
 verify_queries(db)
 print('PASS retained database',db,'four tables, tenant uniqueness, empty grants, eight menus, repeatability, administrator edits and UTF-8 HEX')
 return db
def synchronize_local(verified):
 import hashlib
 local='ruoyi-vue-pro'
 tables=['zsjos_performance_org','zsjos_performance_target','zsjos_performance_revision','zsjos_performance_attribution']
 before=query(local,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS(':',id,role_id,menu_id,tenant_id))),0) FROM system_role_menu;")
 existing=query(local,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_performance_%';").strip()
 menus=query(local,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:sales-performance%' AND deleted=0;").strip()
 if existing!='0' or menus!='0':raise RuntimeError('Expected inspected empty performance scope; inspect before retrying synchronization')
 backup=Path('D:/ZSJ-OS-backups/sales-performance');backup.mkdir(parents=True,exist_ok=True)
 (backup/'local-preflight.txt').write_text('New table count='+existing+'; existing menu count='+menus+'\nRole assignment aggregate='+before,encoding='utf-8')
 sql=MIGRATION.read_text(encoding='utf-8');query(local,sql);query(local,sql)
 digest=hashlib.sha256(MIGRATION.read_bytes()).hexdigest()
 query(local,f"UPDATE zsjos_module_schema_version SET checksum='{digest}' WHERE module_code='core' AND version='V276'; UPDATE zsjos_schema_version SET checksum='{digest}' WHERE version='V276';")
 assert query(local,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS(':',id,role_id,menu_id,tenant_id))),0) FROM system_role_menu;")==before
 for table in tables:
  statement=f"SELECT column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),COALESCE(collation_name,'') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' ORDER BY ordinal_position;"
  assert query(local,statement)==query(verified,statement),table
  assert query(local,f'SELECT COUNT(*) FROM {table};').strip()=='0'
 rows=query(local,"SELECT permission,HEX(name) FROM system_menu WHERE permission LIKE 'zsjos:sales-performance%' AND deleted=0 ORDER BY permission;")
 assert len(rows.strip().splitlines())==8
 assert '业绩统计'.encode().hex().upper() in rows
 (backup/'local-verification.txt').write_text(rows+'\nV276 SHA256='+digest,encoding='utf-8')
 print('PASS local synchronization: four empty tables, eight metadata rows, matching columns, V276 only, unchanged role grants, UTF-8 HEX')
if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser();parser.add_argument('--sync-local',action='store_true');args=parser.parse_args()
 verified=main()
 if args.sync_local:synchronize_local(verified)
