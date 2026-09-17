"""UTF-8: scoped, non-destructive V258 replay; retains the controlled verification database."""
import datetime
from pathlib import Path
from test_positioning_single_source import query
ROOT = Path(__file__).resolve().parents[4]
TABLES = ['zsjos_positioning_card','zsjos_positioning_card_submission','zsjos_media_account','system_menu','zsjos_schema_version']
NEW = ['zsjos_positioning_service_card','zsjos_positioning_application','zsjos_positioning_application_log']
SCRIPT = ROOT / 'script/sql/mysql/migrations/V258__positioning_service_application.sql'
def fingerprint(db):
    return query(db, "SELECT id,version,COALESCE(account_id,0),CRC32(COALESCE(values_snapshot_json,'')) FROM zsjos_positioning_card ORDER BY id;")
if __name__ == '__main__':
    db = 'positioning_apply_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    query('ruoyi-vue-pro', f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in TABLES:
        query(db, f'CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`; INSERT INTO `{table}` SELECT * FROM `ruoyi-vue-pro`.`{table}`;')
    # Synthetic history exercises nonempty backfill; copied real records are unchanged.
    query(db, """
INSERT INTO zsjos_media_account(id,account_no,ownership_type,tenant_id) VALUES(990001,'TEST-APP-1','student',991),(990002,'TEST-APP-2','student',992);
INSERT INTO zsjos_positioning_card(id,card_no,director_user_id,version_no,layer1_json,layer2_json,formula_json,feasibility_json,content_form_json,compliance_json,status,tenant_id,service_relation_id,student_person_id)
VALUES(990001,'TEST-PC-1',1,1,'{}','{}','{}','{}','{}','{}','confirmed',991,990001,990001),(990002,'TEST-PC-2',1,1,'{}','{}','{}','{}','{}','{}','confirmed',992,990001,990002);
INSERT INTO zsjos_positioning_card_submission(id,card_id,account_id,student_person_id,service_relation_id,submission_no,director_user_id,operator_user_id,status,tenant_id,submitted_at)
VALUES(990001,990001,990001,990001,990001,1,1,2,'confirmed',991,'2026-09-01'),(990002,990001,990001,990001,990001,2,1,2,'operator_feasibility',991,'2026-09-02'),(990003,990002,990002,990002,990001,1,1,2,'student_agreed',992,'2026-09-01');
""")
    before = fingerprint(db)
    sql = SCRIPT.read_text(encoding='utf-8')
    query(db, sql)
    first = query(db, ';'.join('SELECT * FROM '+ t +' ORDER BY id' for t in NEW))
    query(db, sql)
    assert first == query(db, ';'.join('SELECT * FROM '+t+' ORDER BY id' for t in NEW))
    assert fingerprint(db) == before
    assert query(db, "SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_positioning_card_submission' AND column_name='account_id';").strip() == 'YES'
    assert query(db, 'SELECT COUNT(*) FROM zsjos_positioning_service_card m JOIN (SELECT tenant_id,service_relation_id,COUNT(*) n FROM zsjos_positioning_card WHERE deleted=0 GROUP BY tenant_id,service_relation_id HAVING n>1) c ON c.tenant_id=m.tenant_id AND c.service_relation_id=m.service_relation_id;').strip() == '0'
    assert query(db, "SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:positioning-card:apply' AND deleted=0;").strip() == '1'
    assert query(db, "SELECT submission_id FROM zsjos_positioning_application WHERE tenant_id=991 AND account_id=990001;").strip() == '990001'
    assert query(db, "SELECT submission_id FROM zsjos_positioning_application WHERE tenant_id=992 AND account_id=990002;").strip() == '990003'
    assert query(db, "SELECT COUNT(*) FROM zsjos_positioning_service_card WHERE service_relation_id=990001;").strip() == '2'
    query(db, "UPDATE zsjos_positioning_card_submission SET status='confirmed' WHERE id=990002 AND tenant_id=991;")
    query(db, sql)
    assert query(db, "SELECT submission_id FROM zsjos_positioning_application WHERE tenant_id=991 AND account_id=990001;").strip() == '990001', 'New confirmation/replay must not move pinned account'
    assert query(db, "SELECT COUNT(*) FROM zsjos_positioning_application_log WHERE tenant_id IN (991,992);").strip() == '2'
    print('PASS nonempty backfill, tenant partition, pinned old version, audit, repeatability, nullable account, preserved cards, ambiguous master retained, menu metadata', db)
    print(query(db, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:positioning-card:apply' AND deleted=0;"))
