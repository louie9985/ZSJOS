# -*- coding: utf-8 -*-
"""Read-only department coverage for Lead source/contribution and performance attribution."""
import argparse
import json
from order_history_repair import connect, scalar


def audit(c):
    queries={
        'leadSourceCoverage': """SELECT source_type,COUNT(*) total,SUM(source_dept_id IS NULL) missing
            FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 GROUP BY source_type""",
        'employeeSourceResolvable': """SELECT l.source_type,COUNT(*) missing,
            SUM(u.id IS NOT NULL AND d.id IS NOT NULL) current_department_available,
            SUM(u.id IS NULL) source_employee_missing
            FROM zsjos_lead l LEFT JOIN system_users u ON u.id=l.source_user_id AND u.tenant_id=1 AND u.deleted=0
            LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=1 AND d.deleted=0
            WHERE l.tenant_id=1 AND l.deleted=0 AND l.source_dept_id IS NULL AND l.source_type<>'partner'
            GROUP BY l.source_type""",
        'partnerSourceResolvable': """SELECT COUNT(*) missing,SUM(u.id IS NOT NULL AND d.id IS NOT NULL) ownership_department_available
            FROM zsjos_lead l LEFT JOIN zsjos_partner_ownership po ON po.partner_id=l.partner_id AND po.tenant_id=1 AND po.deleted=0
            LEFT JOIN system_users u ON u.id=po.employee_user_id AND u.tenant_id=1 AND u.deleted=0
            LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=1 AND d.deleted=0
            WHERE l.tenant_id=1 AND l.deleted=0 AND l.source_type='partner' AND l.source_dept_id IS NULL""",
        'contributionCoverage': """SELECT l.source_type,COUNT(*) missing,
            SUM(u.id IS NOT NULL AND d.id IS NOT NULL) current_department_available
            FROM zsjos_lead l LEFT JOIN system_users u ON u.id=l.contribution_user_id_snapshot AND u.tenant_id=1 AND u.deleted=0
            LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=1 AND d.deleted=0
            WHERE l.tenant_id=1 AND l.deleted=0 AND l.contribution_user_id_snapshot IS NOT NULL
            AND l.contribution_dept_id_snapshot IS NULL GROUP BY l.source_type""",
        'performanceCoverage': """SELECT p.org_source,COUNT(*) total,SUM(p.dept_id IS NULL) department_missing,
            SUM(p.center_id IS NULL) center_missing,SUM(p.center_id IS NULL AND o.center_id IS NOT NULL) missing_center_with_mapping
            FROM zsjos_performance_attribution p LEFT JOIN zsjos_performance_org o ON o.dept_id=p.dept_id AND o.tenant_id=1 AND o.deleted=0
            WHERE p.tenant_id=1 AND p.deleted=0 GROUP BY p.org_source""",
        'performanceExistingDifference': """SELECT p.org_source,COUNT(*) total,SUM(p.dept_id<>u.dept_id) existing_diff_current
            FROM zsjos_performance_attribution p JOIN system_users u ON u.id=p.user_id AND u.tenant_id=1 AND u.deleted=0
            WHERE p.tenant_id=1 AND p.deleted=0 GROUP BY p.org_source""",
        'snapshotColumnInventory': """SELECT table_name,column_name FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos\\_%'
            AND (column_name LIKE '%dept%snapshot%' OR column_name LIKE '%department%snapshot%' OR column_name LIKE '%center%snapshot%')
            ORDER BY table_name,column_name""",
    }
    try:
        with c.cursor() as q:
            q.execute('START TRANSACTION READ ONLY');result={}
            for name,sql in queries.items():q.execute(sql);result[name]=q.fetchall()
        return result
    finally:c.rollback()


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--database',default='ruoyi-vue-pro');args=p.parse_args()
    c=connect(args.database)
    try:print(json.dumps(audit(c),ensure_ascii=True,default=scalar))
    finally:c.close()
