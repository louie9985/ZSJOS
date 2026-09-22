# UTF-8. Retained synthetic database; no real account grants or business mutations.
import datetime as dt
import re
from pathlib import Path
from test_positioning_single_source import query

ROOT = Path(__file__).resolve().parents[4]
MIGRATION = ROOT / 'script/sql/mysql/migrations/V277__lead_follow_up_calendar.sql'
PERMISSION = 'zsjos:lead-follow-up-calendar:query'

def signature(db):
    return query(db, f"SELECT HEX(name),permission,path,component,parent_id FROM system_menu WHERE permission='{PERMISSION}' AND deleted=0;")

def main():
    db = 'lead_calendar_verify_' + dt.datetime.now().strftime('%Y%m%d%H%M%S')
    query('ruoyi-vue-pro', f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version', 'system_role_menu', 'zsjos_lead', 'zsjos_business_task']:
        query(db, f'CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`;')
    sql = MIGRATION.read_text(encoding='utf-8')
    try:
        query(db, sql)
        raise AssertionError('Missing calendar prerequisite must fail')
    except RuntimeError as error:
        assert 'requires exactly one' in str(error)
    assert query(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V277';").strip() == '0'
    query(db, """INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,deleted)
      VALUES(73600,'日历','',1,6,0,'/calendar','ep:calendar','',0,1,1,1,0);
      INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
      VALUES('core','V276','synthetic prerequisite','test','baseline');""")
    query(db, sql)
    before = signature(db)
    query(db, sql)
    assert signature(db) == before
    assert query(db, 'SELECT COUNT(*) FROM system_role_menu;').strip() == '0'
    assert query(db, f"SELECT COUNT(*) FROM system_menu WHERE permission='{PERMISSION}' AND deleted=0;").strip() == '1'
    assert query(db, f"SELECT HEX(name) FROM system_menu WHERE permission='{PERMISSION}';").strip() == '销售客资跟进日历'.encode().hex().upper()
    assert 'sales-lead-follow-up' in before and 'zsjos/leadFollowUpCalendar/index' in before
    query(db, f"UPDATE system_menu SET sort=93, name='管理员名称' WHERE permission='{PERMISSION}';")
    query(db, sql)
    assert query(db, f"SELECT sort,HEX(name) FROM system_menu WHERE permission='{PERMISSION}';").strip() == '93\t'+'管理员名称'.encode().hex().upper()
    # Execute the actual production mapper statement, including tenant/owner/assignee/deleted boundaries.
    for lead_id, owner, tenant, deleted in [(1,7,91,0),(2,7,91,0),(3,8,91,0),(4,7,92,0),(5,7,91,1),(6,7,91,0),(7,7,91,0),(8,7,91,0),(9,7,91,0),(10,7,91,0)]:
        query(db, f"INSERT INTO zsjos_lead(id,lead_no,person_id,submitted_name,source_type,status,assignment_status,submitted_at,owner_user_id,tenant_id,deleted) VALUES({lead_id},'TEST-{lead_id}',{lead_id},'测试客资','internal_new_media','valid','owned','2026-09-01',{owner},{tenant},{deleted});")
    tasks = [(1,1,7,91,'pending','2026-09-22 09:00:00','lead_first_follow_up',0),
             (2,1,7,91,'pending','2026-09-23 09:00:00','lead_follow_up_reminder',0),
             (3,2,7,91,'pending','2026-09-23 00:00:00','lead_follow_up_reminder',0),
             (4,3,7,91,'pending','2026-09-22 09:00:00','lead_follow_up_reminder',0),
             (5,4,7,92,'pending','2026-09-22 09:00:00','lead_follow_up_reminder',0),
             (6,5,7,91,'pending','2026-09-22 09:00:00','lead_follow_up_reminder',0),
             (7,6,7,91,'completed','2026-09-22 09:00:00','lead_follow_up_reminder',0),
             (8,7,7,91,'cancelled','2026-09-22 09:00:00','lead_follow_up_reminder',0),
             (9,8,8,91,'pending','2026-09-22 09:00:00','lead_follow_up_reminder',0),
             (10,9,7,91,'pending','2026-09-22 09:00:00','lead_qualification',0),
             (11,10,7,91,'pending','2026-09-22 09:00:00','lead_follow_up_reminder',1)]
    for task_id, lead_id, assignee, tenant, status, deadline, kind, deleted in tasks:
        query(db, f"INSERT INTO zsjos_business_task(id,task_type,biz_type,biz_id,status,assignee_type,assignee_id,title_snapshot,action_code,due_at,idempotency_key,tenant_id,deleted) VALUES({task_id},'{kind}','lead',{lead_id},'{status}','user',{assignee},'测试','OPEN_LEAD_FOLLOW_UP','{deadline}','calendar-test-{task_id}',{tenant},{deleted});")
    mapper = ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/LeadCalendarMapper.java'
    statement = re.search(r'@Select\("""(.*?)"""\)', mapper.read_text(encoding='utf-8'), re.S).group(1)
    def run(start, end, tenant=91, user=7):
        text = statement.replace('#{tenantId}', str(tenant)).replace('#{userId}', str(user)).replace('#{start}', f"'{start}'").replace('#{end}', f"'{end}'")
        return query(db, text).strip()
    assert run('2026-09-22', '2026-09-23').split('\t')[0] == '1'
    assert len(run('2026-09-22', '2026-09-23').splitlines()) == 1
    assert run('2026-09-23', '2026-09-24').split('\t')[0] == '2', 'Lead 1 must stay on its earliest pending day'
    assert run('2026-09-22', '2026-09-23', 92).split('\t')[0] == '4'
    assert run('2026-09-22', '2026-09-23', 91, 99) == ''
    query(db, "UPDATE zsjos_business_task SET status='completed' WHERE id=1;")
    assert run('2026-09-22', '2026-09-23') == ''
    assert len(run('2026-09-23', '2026-09-24').splitlines()) == 2
    print('PASS', db, 'menu repeatability/admin-edit retention/UTF-8/no grants; actual mapper SQL tenant, owner, assignee, deleted, status, boundary, deduplication and completion refresh')

if __name__ == '__main__':
    main()
