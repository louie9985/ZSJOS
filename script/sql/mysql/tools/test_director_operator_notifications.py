#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Isolated V269 replay: empty/default/custom tenants, preservation, UTF-8 and repeatability."""
import re
import subprocess
import unittest
import zsjos_db as db


class NotificationDefaultsTest(unittest.TestCase):
    def sql(self, container, text):
        result = subprocess.run(['docker', 'exec', '-i', '-w', '/workspace', container, 'mysql',
            '--default-character-set=utf8mb4', '-uroot', '-N', '--batch', '--raw', 'zsjos_test'],
            input=text.encode('utf-8'), capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr.decode('utf-8'))
        return result.stdout.decode('utf-8').strip()

    def test_replay(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        baseline = (db.SQL_ROOT / '00-bootstrap-schema.sql').read_text()
        for table in ('system_tenant', 'system_notify_template', 'system_notify_rule', 'zsjos_module_schema_version'):
            ddl = re.search(r'CREATE TABLE IF NOT EXISTS `' + table + r'` \(.*?;\n', baseline, re.S).group()
            self.sql(container, ddl)
        seed = (db.SQL_ROOT / '02-bootstrap-zsjos-seed.sql').read_text()
        self.sql(container, re.search(r'CREATE TABLE IF NOT EXISTS `zsjos_schema_version` \(.*?;', seed, re.S).group())
        self.sql(container, """SET NAMES utf8mb4;
            INSERT INTO system_tenant(id,name,contact_name,package_id,expire_time,account_count)
            VALUES(1,'测试一','合成',1,'2099-01-01',10),(2,'测试二','合成',1,'2099-01-01',10),(3,'测试三','合成',1,'2099-01-01',10);
            SOURCE script/sql/mysql/migrations/V216__media_account_diagnosis_notifications.sql;
            SOURCE script/sql/mysql/migrations/V220__student_delivery_notification_defaults.sql;
            UPDATE system_notify_rule SET status=1,updater='administrator' WHERE tenant_id=2 AND scene_code='media.account.diagnosis';
            INSERT INTO system_notify_rule(name,scene_code,template_id,recipient_roles,specified_user_ids,action_type,status,tenant_id)
              SELECT '保留停用规则','zsjos.content_review.batch_submitted',id,'["director"]','[]','message_detail',1,2 FROM system_notify_template LIMIT 1;
        """)
        self.sql(container, """SET NAMES utf8mb4;
            INSERT INTO system_notify_template(name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status)
            VALUES ('业务反馈','TEST_BUSINESS','中仕健','test.business','in_app','业务反馈','业务反馈','反馈内容',2,'[]',0),
                   ('自定义企微模板','ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED_WECOM','中仕健',
                    'media.student.interview_completed','wecom','管理员标题','管理员摘要','管理员内容',2,'[]',1);
            INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,tenant_id,creator)
            SELECT '提前提醒','test.business','in_app',id,'["assignee"]','[]','business_detail','advance',60,0,1,'administrator'
              FROM system_notify_template WHERE code='TEST_BUSINESS';
            INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,timing_stage,timing_offset_minutes,status,tenant_id,creator)
            SELECT '停用逾期提醒','test.business','in_app',id,'["supervisor"]','[7]','message_detail','overdue',120,1,1,'administrator'
              FROM system_notify_template WHERE code='TEST_BUSINESS';
            INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,tenant_id,creator)
            SELECT '管理员停用企微','media.student.interview_completed','wecom',id,'["assignee"]','[]','message_detail',1,2,'administrator'
              FROM system_notify_template WHERE code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED_WECOM';
        """)
        self.sql(container, """SET NAMES utf8mb4;
            INSERT INTO system_notify_template(name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status)
            VALUES ('自定义提现','TEST_WITHDRAWAL_CUSTOM','中世健','zsjos.withdrawal.approved','in_app','保留标题','保留摘要','保留内容',2,'[]',0),
                   ('销售回复','TEST_PARTNER_REPLY','中世健','zsjos.lead.submitter_feedback_created','in_app','销售回复','销售回复','请查看回复',2,'[]',0),
                   ('提交人补充','TEST_PARTNER_SUPPLEMENT','中世健','zsjos.lead.submitter_supplemented','in_app','提交人补充','提交人补充','请查看补充',2,'[]',0);
            INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,tenant_id,creator)
            SELECT '停用提现审批通知',scene_code,'in_app',id,'["applicant"]','[]','none',1,2,'administrator'
                FROM system_notify_template WHERE code='TEST_WITHDRAWAL_CUSTOM';
            INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,tenant_id,creator)
            SELECT name,scene_code,'in_app',id,IF(code='TEST_PARTNER_REPLY','["submitter"]','["owner"]'),'[]','business_detail',0,1,'administrator'
                FROM system_notify_template WHERE code IN ('TEST_PARTNER_REPLY','TEST_PARTNER_SUPPLEMENT');
        """)
        preserved_templates = self.sql(container, "SELECT * FROM system_notify_template ORDER BY id")
        original_template_ids = self.sql(container, "SELECT GROUP_CONCAT(id) FROM system_notify_template")
        preserved = self.sql(container, 'SELECT * FROM system_notify_rule WHERE tenant_id=2 ORDER BY id')
        migration = 'SOURCE script/sql/mysql/migrations/V269__director_operator_notifications.sql;'
        self.sql(container, migration)
        self.assertEqual(preserved, self.sql(container, "SELECT * FROM system_notify_rule WHERE tenant_id=2 AND creator<>'migration-V269' ORDER BY id"))
        self.assertEqual('2', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND scene_code='media.account.diagnosis' AND channel_code='in_app'"))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=2 AND scene_code='media.account.diagnosis' AND channel_code='in_app'"))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=2 AND scene_code='zsjos.content_review.batch_submitted' AND channel_code='in_app'"))
        self.assertEqual('0', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule r LEFT JOIN system_notify_template t ON t.id=r.template_id WHERE r.creator='migration-V269' AND (t.id IS NULL OR r.scene_code<>t.scene_code OR r.channel_code<>t.channel_code)"))
        self.assertEqual('定位访谈已完成'.encode().hex().upper(), self.sql(container, "SELECT HEX(title) FROM system_notify_template WHERE code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED'"))
        self.assertEqual(preserved_templates, self.sql(container,
            f"SELECT * FROM system_notify_template WHERE id IN ({original_template_ids}) ORDER BY id"))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=2 AND scene_code='media.student.interview_completed' AND channel_code='wecom'"))
        self.assertEqual('2', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND scene_code='test.business' AND channel_code='wecom'"))
        self.assertEqual('2', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND scene_code='media.account.diagnosis' AND channel_code='wecom'"))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=2 AND scene_code='media.account.diagnosis' AND channel_code='wecom' AND status=1"))
        self.assertEqual('0', self.sql(container, """SELECT COUNT(*) FROM system_notify_rule w
            WHERE w.channel_code='wecom' AND w.creator='migration-V269' AND NOT EXISTS (
                SELECT 1 FROM system_notify_rule a WHERE a.channel_code='in_app'
                  AND a.tenant_id=w.tenant_id AND a.scene_code=w.scene_code AND a.status=w.status
                  AND a.recipient_roles=w.recipient_roles AND a.specified_user_ids=w.specified_user_ids
                  AND a.action_type=w.action_type AND a.timing_stage <=> w.timing_stage
                  AND a.timing_offset_minutes <=> w.timing_offset_minutes)"""))
        self.assertEqual('定位访谈已预约'.encode().hex().upper(), self.sql(container,
            "SELECT HEX(title) FROM system_notify_template WHERE code='ZSJ_N269_MEDIA_STUDENT_INTERVIEW_SCHEDULED_WECOM'"))
        self.assertEqual('0', self.sql(container, """SELECT COUNT(*) FROM system_notify_template a
            WHERE a.code LIKE 'ZSJ_N269_%' AND a.channel_code='in_app' AND NOT EXISTS (
              SELECT 1 FROM system_notify_template w WHERE w.code=CONCAT(a.code,'_WECOM') AND w.channel_code='wecom')"""))
        self.assertEqual('10', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND scene_code LIKE 'zsjos.withdrawal.%'"))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=2 AND scene_code='zsjos.withdrawal.approved' AND channel_code='in_app' AND status=1"))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=2 AND scene_code='zsjos.withdrawal.approved' AND channel_code='wecom' AND status=1 AND action_type='none'"))
        for scene, roles in [('submitted','["finance"]'), ('approved','["applicant","finance"]'),
                             ('rejected','["applicant","finance"]'), ('paid','["applicant","finance"]'),
                             ('finance_reminder','["finance"]')]:
            self.assertEqual('2', self.sql(container, f"SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND scene_code='zsjos.withdrawal.{scene}' AND recipient_roles='{roles}'"))
        self.assertEqual('2', self.sql(container, "SELECT COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND scene_code IN ('zsjos.lead.submitter_feedback_created','zsjos.lead.submitter_supplemented') AND channel_code='wecom' AND status=0"))
        self.assertEqual('提现已记录打款'.encode().hex().upper(), self.sql(container, "SELECT HEX(title) FROM system_notify_template WHERE code='ZSJ_N269_WITHDRAWAL_PAID_WECOM'"))
        self.assertEqual('2', self.sql(container, "SELECT COUNT(*) FROM system_notify_template WHERE scene_code='zsjos.withdrawal.rejected' AND content LIKE '%{{withdrawal.rejectionReason}}%' AND content LIKE '%{{withdrawal.no}}%'"))
        before = self.sql(container, 'SELECT * FROM system_notify_template ORDER BY id; SELECT * FROM system_notify_rule ORDER BY id;')
        self.sql(container, migration)
        self.assertEqual(before, self.sql(container, 'SELECT * FROM system_notify_template ORDER BY id; SELECT * FROM system_notify_rule ORDER BY id;'))
        self.assertEqual('1', self.sql(container, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V269'"))
        print('PASS: V269 in_app/WeCom parity, multi-rule timing/status, disabled/custom preservation, multi-tenant scope, UTF-8 HEX, repeatability')


if __name__ == '__main__':
    unittest.main()
