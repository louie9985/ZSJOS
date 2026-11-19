# -*- coding: utf-8 -*-
"""V371 isolated MySQL lifecycle; pass a task-owned container name as argument."""
import subprocess, sys, time, re, uuid
from pathlib import Path
container = sys.argv[1]
run_id = uuid.uuid4().hex[:8]
sql = (Path(__file__).resolve().parents[1] / 'migrations/V371__sales_delivery_merge.sql').read_text(encoding='utf-8-sig')
def run(query, ok=True):
    query = re.sub(r'(?<![A-Za-z0-9_])v371_[A-Za-z0-9_]+', lambda match: match.group(0) + '_' + run_id, query)
    p = subprocess.run(['docker','exec','-i',container,'mysql','--default-character-set=utf8mb4','-uroot','--batch','--skip-column-names'], input=query.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if (p.returncode == 0) != ok: raise AssertionError(p.stderr.decode(errors='replace'))
    return p.stdout.decode().strip()
for _ in range(60):
    try: run('SELECT 1;'); break
    except AssertionError: time.sleep(1)
else: raise RuntimeError('isolated MySQL unavailable')
def setup(db):
    run(f'''CREATE DATABASE `{db}` CHARACTER SET utf8mb4; USE `{db}`;
CREATE TABLE system_menu(id bigint auto_increment primary key,name varchar(100),permission varchar(128),type int,sort int,parent_id bigint,path varchar(200),icon varchar(200),component varchar(200),status int,visible bit,keep_alive bit,always_show bit,creator varchar(64),create_time datetime,updater varchar(64),update_time datetime,deleted bit default b'0');
CREATE TABLE zsjos_schema_version(version varchar(20) primary key,description varchar(200),checksum varchar(128));
CREATE TABLE zsjos_module_schema_version(module_code varchar(20),version varchar(20),description varchar(200),checksum varchar(128),release_version varchar(20),primary key(module_code,version));
CREATE TABLE zsjos_performance_target(id bigint primary key);
CREATE TABLE zsjos_performance_org(id bigint primary key);
CREATE TABLE zsjos_media_student_talk_record(id bigint primary key, student_person_id bigint NOT NULL);
CREATE TABLE zsjos_order(id bigint primary key, agreed_exam_time varchar(100) NULL);
CREATE TABLE zsjos_delivery_class(id bigint primary key, tenant_id bigint NOT NULL, class_kind varchar(24) NOT NULL DEFAULT 'NORMAL', product_id bigint NULL, selected_skus_json json NULL, homeroom_user_id bigint NULL, status varchar(24) NOT NULL DEFAULT 'SERVING', deleted bit(1) NOT NULL DEFAULT b'0', dept_id bigint NULL);
CREATE TABLE system_role_menu(id bigint auto_increment primary key,role_id bigint,menu_id bigint,deleted bit default b'0');
INSERT INTO system_menu(name,permission,type,parent_id,deleted) VALUES('客资','zsjos:lead:query',2,0,b'0'),('媒体学员','zsjos:media-student:query-my',2,0,b'0');
INSERT INTO system_menu(id,name,permission,type,parent_id,status,visible,deleted) VALUES
 (100,'教务团队','zsjos:education-team:query',2,0,0,b'1',b'0'),(110,'交付业绩','zsjos:delivery-performance:query',2,0,0,b'1',b'0'),
 (120,'交付业绩目标','zsjos:delivery-performance-target:query',2,0,0,b'1',b'0'),(130,'教务自拓','zsjos:lead:education-self-sourced:create',2,0,0,b'1',b'0'),
 (101,'账号启停','zsjos:education-team:account-status',3,100,0,b'1',b'0'),(102,'教务客资转派','zsjos:education-team:lead-transfer',3,100,0,b'1',b'0'),
 (103,'教务客资恢复','zsjos:education-team:lead-restore',3,100,0,b'1',b'0'),(104,'教务客资回收','zsjos:education-team:lead-recycle',3,100,0,b'1',b'0'),
 (105,'教务客资改判有效','zsjos:education-team:lead-overturn-valid',3,100,0,b'1',b'0'),(111,'个人交付业绩','zsjos:delivery-performance:self',3,110,0,b'1',b'0'),
 (112,'部门交付业绩','zsjos:delivery-performance:department',3,110,0,b'1',b'0'),(113,'中心交付业绩','zsjos:delivery-performance:center',3,110,0,b'1',b'0'),
 (114,'交付业绩明细','zsjos:delivery-performance:detail',3,110,0,b'1',b'0'),(121,'维护交付目标','zsjos:delivery-performance-target:update',3,120,0,b'1',b'0'),
 (122,'配置交付统计组织','zsjos:delivery-performance-target:configure',3,120,0,b'1',b'0'),(140,'销售业绩','zsjos:sales-performance:query',2,0,0,b'1',b'0');
INSERT INTO system_role_menu(role_id,menu_id) VALUES(3014,110),(3014,111),(3012,100),(3012,130),(3006,140);
INSERT INTO zsjos_schema_version VALUES('V370','fixture','fixture');
INSERT INTO zsjos_module_schema_version VALUES('core','V370','fixture','fixture','fixture');
CREATE TABLE zsjos_user_relation_scene(id bigint primary key,name varchar(100),code varchar(64),source_label varchar(64),target_label varchar(64),source_post_code varchar(64),target_post_code varchar(64),updater varchar(64),update_time datetime,deleted bit default b'0');
INSERT INTO zsjos_user_relation_scene(id,name,code,source_label,target_label,source_post_code,target_post_code) VALUES
 (2,'报名履约主管分配学习规划师','registration_manager_study_planner','报名履约主管','学习规划师','enrollment_manager','study_planner'),
 (3,'报名履约专员分配学习规划师','registration_specialist_study_planner','报名履约专员','学习规划师','enrollment_specialist','study_planner'),
 (4,'学习规划师分配编导','study_planner_content_director','学习规划师','编导','study_planner','content_director'),
 (5,'学习规划师分配职业规划师','study_planner_career_planner','学习规划师','职业规划师','study_planner','career_planner'),
 (9,'编导分配新媒体运营','content_director_operator','编导','新媒体运营','content_director','new_media_operator');
CREATE TABLE system_notify_rule(id bigint primary key,scene_code varchar(128),recipient_roles varchar(500),updater varchar(64),update_time datetime,deleted bit default b'0');
INSERT INTO system_notify_rule(id,scene_code,recipient_roles) VALUES(1,'zsjos.student.first_contact_reminder','["study_planner","delivery_supervisor"]'),
 (2,'zsjos.registration.delivery_cancelled','["study_planner"]'),(3,'other.scene','["study_planner"]');''')
def apply(db,ok=True): run(f'USE `{db}`;\n'+sql,ok)
def scalar(db,q): return run(f'USE `{db}`; '+q)
def verify(db):
    apply(db); apply(db)
    assert scalar(db,"SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:sales:eligible','zsjos:lead:receive','zsjos:sales:cross-department','zsjos:sales:boundary-configure','zsjos:media-student:history-review')") == '5'
    assert scalar(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '1'
    assert scalar(db,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V371'") == '1'
    assert scalar(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:sales:eligible'") == '销售业务资格'.encode().hex().upper()
    assert scalar(db,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='zsjos_order' AND column_name='agreed_exam_month') OR (table_name='zsjos_delivery_class' AND column_name IN ('exam_month','auto_class_guard')))") == '3'
    assert scalar(db,"SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='uk_delivery_auto_class' AND non_unique=0") == 'tenant_id,auto_class_guard,exam_month,homeroom_user_id'
    assert scalar(db,"SELECT HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='agreed_exam_month'") == '商定考试月份 YYYY-MM'.encode().hex().upper()
    assert scalar(db,"SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 100 AND 130 AND deleted=1 AND status=1 AND visible=0 AND updater='V371'") == '15'
    assert scalar(db,"SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 100 AND 130 AND deleted=0") == '0'
    assert scalar(db,"SELECT CONCAT(deleted+0,status,visible+0) FROM system_menu WHERE permission='zsjos:sales-performance:query'") == '001'
    assert scalar(db,"SELECT COUNT(*) FROM system_role_menu WHERE deleted=0") == '5'
    assert scalar(db,"SELECT HEX(name) FROM system_menu WHERE id=100") == '教务团队'.encode().hex().upper()
    assert scalar(db,"SELECT COUNT(*) FROM zsjos_user_relation_scene WHERE 'study_planner' IN (source_post_code,target_post_code)") == '0'
    assert scalar(db,"SELECT GROUP_CONCAT(CONCAT(code,':',source_post_code,'>',target_post_code) ORDER BY id) FROM zsjos_user_relation_scene") == 'registration_manager_study_planner:enrollment_manager>sales_specialist,registration_specialist_study_planner:enrollment_specialist>sales_specialist,study_planner_content_director:sales_specialist>content_director,study_planner_career_planner:sales_specialist>career_planner,content_director_operator:content_director>new_media_operator'
    assert scalar(db,"SELECT HEX(name) FROM zsjos_user_relation_scene WHERE id=4") == '班主任分配编导'.encode().hex().upper()
    assert scalar(db,"SELECT HEX(target_label) FROM zsjos_user_relation_scene WHERE id=2") == '班主任'.encode().hex().upper()
    assert scalar(db,"SELECT GROUP_CONCAT(recipient_roles ORDER BY id SEPARATOR '|') FROM system_notify_rule") == '["head_teacher","sales_supervisor"]|["head_teacher"]|["study_planner"]'
setup('v371_initial10'); verify('v371_initial10')
setup('v371_partial10'); run("USE v371_partial10; ALTER TABLE zsjos_performance_target ADD COLUMN merged_delivery_target_id bigint NULL;"); verify('v371_partial10')
setup('v371_missing10'); run("USE v371_missing10; DELETE FROM zsjos_schema_version WHERE version='V370';"); apply('v371_missing10',False)
assert scalar('v371_missing10',"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
setup('v371_ledger10'); run("USE v371_ledger10; CREATE TRIGGER reject_v371 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure';"); apply('v371_ledger10',False)
assert scalar('v371_ledger10',"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
assert scalar('v371_ledger10',"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:sales:%' OR permission='zsjos:lead:receive'") == '0'
assert scalar('v371_ledger10',"SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 100 AND 130 AND deleted=0") == '15'
run('USE v371_ledger10; DROP TRIGGER reject_v371;'); verify('v371_ledger10')
setup('v371_conflict10'); run("USE v371_conflict10; ALTER TABLE zsjos_performance_org ADD COLUMN sales_boundary_root varchar(10);"); apply('v371_conflict10',False)
assert scalar('v371_conflict10',"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
# Menu retirement: partial retirement completes, replay after V371 rechecks metadata, conflicts stop before writes.
setup('v371_retire_partial')
run("USE v371_retire_partial; UPDATE system_menu SET deleted=b'1' WHERE id IN (100,101); UPDATE system_menu SET status=1 WHERE id=130;")
verify('v371_retire_partial')
run("USE v371_retire_partial; UPDATE system_menu SET deleted=b'0',status=0,visible=b'1' WHERE id=112;")
apply('v371_retire_partial')
assert scalar('v371_retire_partial',"SELECT CONCAT(deleted+0,status,visible+0) FROM system_menu WHERE id=112") == '110'
setup('v371_retire_child')
run("USE v371_retire_child; INSERT INTO system_menu(name,permission,type,parent_id,deleted) VALUES('其他按钮','zsjos:other:action',3,110,b'0');")
apply('v371_retire_child',False)
assert scalar('v371_retire_child',"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
assert scalar('v371_retire_child',"SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 100 AND 130 AND deleted=0") == '15'
assert scalar('v371_retire_child',"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='agreed_exam_month'") == '0'
setup('v371_retire_type')
run("USE v371_retire_type; UPDATE system_menu SET type=3 WHERE id=130;")
apply('v371_retire_type',False)
assert scalar('v371_retire_type',"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V371'") == '0'
setup('v371_retire_parent')
run("USE v371_retire_parent; UPDATE system_menu SET parent_id=140 WHERE id=114;")
apply('v371_retire_parent',False)
assert scalar('v371_retire_parent',"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
print('PASS initial/replay/partial/prerequisite/ledger failure/recovery/conflict/UTF-8 HEX/menu retirement')
# Auto-class DDL: partial recovery from each intermediate state, existing rows preserved, key semantics.
setup('v371_auto_partial1')
run("USE v371_auto_partial1; INSERT INTO zsjos_order VALUES (1,'明年三月'); INSERT INTO zsjos_delivery_class(id,tenant_id,product_id,homeroom_user_id) VALUES (1,1,401,31),(2,1,401,31); ALTER TABLE zsjos_order ADD COLUMN agreed_exam_month char(7) NULL COMMENT '商定考试月份 YYYY-MM' AFTER agreed_exam_time;")
verify('v371_auto_partial1')
assert scalar('v371_auto_partial1', "SELECT CONCAT(agreed_exam_time,'|',COALESCE(agreed_exam_month,'NULL')) FROM zsjos_order WHERE id=1") == '明年三月|NULL'
assert scalar('v371_auto_partial1', 'SELECT COUNT(*) FROM zsjos_delivery_class WHERE exam_month IS NULL AND auto_class_guard IS NULL') == '2'
setup('v371_auto_partial2')
run("USE v371_auto_partial2; ALTER TABLE zsjos_order ADD COLUMN agreed_exam_month char(7) NULL COMMENT '商定考试月份 YYYY-MM'; ALTER TABLE zsjos_delivery_class ADD COLUMN exam_month char(7) NULL AFTER selected_skus_json, ADD COLUMN auto_class_guard bigint GENERATED ALWAYS AS (CASE WHEN (`class_kind`='NORMAL' AND `deleted`=b'0' AND `status`='SERVING' AND `exam_month` > '') THEN `product_id` ELSE NULL END) STORED AFTER exam_month;")
verify('v371_auto_partial2')
db = 'v371_auto_partial2'
run(f"USE {db}; INSERT INTO zsjos_delivery_class(id,tenant_id,product_id,exam_month,homeroom_user_id) VALUES (10,1,401,'2027-03',31),(11,1,402,'2027-03',31),(12,1,401,'2027-04',31),(13,1,401,'2027-03',32),(14,2,401,'2027-03',31);")
run(f"USE {db}; INSERT INTO zsjos_delivery_class(id,tenant_id,product_id,exam_month,homeroom_user_id) VALUES (15,1,401,'2027-03',31);", False)
run(f"USE {db}; UPDATE zsjos_delivery_class SET status='COMPLETED' WHERE id=10; INSERT INTO zsjos_delivery_class(id,tenant_id,product_id,exam_month,homeroom_user_id) VALUES (16,1,401,'2027-03',31);")
run(f"USE {db}; INSERT INTO zsjos_delivery_class(id,tenant_id,class_kind,product_id,exam_month,homeroom_user_id) VALUES (17,1,'UNSCHEDULED',401,'2027-03',31);")
print('PASS auto-class partial recovery, legacy rows untouched, key unique per tenant/product/month/homeroom, completed and non-normal classes excluded')
setup('v371_auto_bad_index')
run('USE v371_auto_bad_index; ALTER TABLE zsjos_delivery_class ADD COLUMN exam_month char(7) NULL, ADD KEY uk_delivery_auto_class(dept_id);')
apply('v371_auto_bad_index', False)
assert scalar('v371_auto_bad_index', "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
assert scalar('v371_auto_bad_index', "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V371'") == '0'
setup('v371_auto_bad_column')
run('USE v371_auto_bad_column; ALTER TABLE zsjos_order ADD COLUMN agreed_exam_month varchar(20) NULL;')
apply('v371_auto_bad_column', False)
assert scalar('v371_auto_bad_column', "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
setup('v371_auto_missing_prereq')
run('USE v371_auto_missing_prereq; ALTER TABLE zsjos_order DROP COLUMN agreed_exam_time;')
apply('v371_auto_missing_prereq', False)
assert scalar('v371_auto_missing_prereq', "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V371'") == '0'
assert scalar('v371_auto_missing_prereq', "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name IN ('agreed_exam_month','exam_month')") == '0'
print('PASS auto-class conflicting index/column and missing prerequisite stop without ledger rows')
