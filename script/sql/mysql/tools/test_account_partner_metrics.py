#!/usr/bin/env python3
# UTF-8
"""Controlled MySQL replay of actual mapper SQL and scoped metric config correction."""
import datetime
import json
import re
from pathlib import Path
from test_account_appearance_text import query, configs, ROOT

SQL = (ROOT / 'script/sql/mysql/media-account-partner-metrics.sql').read_bytes()
KEYS = ['total_leads', 'month_leads', 'total_conversion', 'month_conversion', 'total_amount', 'month_amount']


def mapper_sql(relative, method):
    text = (ROOT / relative).read_text('utf8')
    end = text.index(method)
    start = text.rfind('@Select(', 0, end)
    return ''.join(json.loads('"' + part + '"') for part in re.findall(r'"((?:[^"\\]|\\.)*)"', text[start:end]))


if __name__ == '__main__':
    db = 'partner_metrics_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    query('', f'CREATE DATABASE {db} CHARACTER SET utf8mb4')
    query(db, 'CREATE TABLE zsjos_media_account_field_config LIKE `ruoyi-vue-pro`.zsjos_media_account_field_config;')
    fields = [dict(key=k, label='指标', sourceType='PENDING', ownerType='AUTO', type='number') for k in KEYS]
    fields.append(dict(key='cover', type='image', sourceType='MANUAL'))
    encoded = json.dumps(fields,ensure_ascii=False).encode('utf8').hex()
    for version,status in enumerate(['published','draft'],1):
        query(db,f"INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,version,tenant_id) VALUES({version},'{status}',CONVERT(0x{encoded} USING utf8mb4),0,1)")
    query(db,SQL)
    after=configs(db)
    for row in after:
        expected=json.loads(json.dumps(fields))
        if row['status']!='archived':
            for f in expected[:-1]: f['sourceType']='ACCOUNT'
        assert row['fields']==expected
    query(db,SQL)
    assert configs(db)==after
    query(db,"""CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,partner_id BIGINT,deleted BIT,status VARCHAR(30),submitted_at DATETIME);
    CREATE TABLE zsjos_order(id BIGINT,lead_id BIGINT,tenant_id BIGINT,deleted BIT,status VARCHAR(30),effective_at DATETIME,payable_amount DECIMAL(12,2));
    INSERT INTO zsjos_lead VALUES
    (1,1,40,0,'won','2026-09-01'),(2,1,40,0,'valid','2026-09-18'),(3,1,40,0,'converted','2026-09-10'),
    (4,1,40,0,'invalid','2026-09-10'),(5,1,40,0,'pending','2026-09-10'),(6,1,40,1,'won','2026-09-10'),
    (7,2,40,0,'won','2026-09-10'),(8,1,41,0,'won','2026-09-10'),(9,1,40,0,'won','2026-08-31'),(10,1,40,0,'won','2026-10-01');
    INSERT INTO zsjos_order VALUES
    (1,1,1,0,'effective','2026-09-02',100),(2,1,1,0,'effective','2026-09-03',50),(3,9,1,0,'effective','2026-09-04',200),
    (4,9,1,0,'effective','2026-08-31',300),(5,1,1,0,'pending_approval','2026-09-03',999),
    (6,1,1,1,'effective','2026-09-03',999),(7,7,2,0,'effective','2026-09-03',999),
    (8,8,1,0,'effective','2026-09-03',999),(9,6,1,0,'effective','2026-09-03',999),
    (10,1,1,0,'effective','2026-10-01',999);
    """)
    base='backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/'
    lead=mapper_sql(base+'lead/LeadMapper.java','aggregatePartnerValidCohort')
    orders=mapper_sql(base+'order/SalesOrderMapper.java','sumPartnerEffectiveGross')
    def run(sql, start):
        for key,value in dict(tenantId='1',partnerId='40',from_=start,to="'2026-09-18 23:59:59'").items():
            sql=sql.replace('#{'+key.rstrip('_')+'}',value)
        return query(db,sql).strip()
    assert run(lead,"'2026-09-01'")=='3\t1'
    assert run(lead,'NULL')=='4\t2'
    assert run(orders,"'2026-09-01'")=='350.00'
    assert run(orders,'NULL')=='650.00'
    print('PASS config first/repeat, valid submission cohorts, repeat orders, month boundaries, tenant/partner/deletion:',db)
