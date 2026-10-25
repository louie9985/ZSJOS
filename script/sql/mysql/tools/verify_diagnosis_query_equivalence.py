#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only MySQL old/new publication comparison; no fixture tables or business writes."""
import datetime as dt
import html
import json
import re
import time
from test_positioning_single_source import ROOT, query


def statements():
    source = (ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/account/DiagnosisMetricMapper.java').read_text(encoding='utf-8')
    result = {}
    for match in re.finditer(r'@Select\("""\s*(.*?)\s*"""\)\s*[^\n]*?\s(\w+)\(', source, re.S):
        result[match.group(2)] = html.unescape(match.group(1).replace('<script>', '').replace('</script>', '')).strip()
    return result


def render(sql, **values):
    for key, value in values.items():
        literal = str(value) if isinstance(value, int) else "'" + str(value).replace("'", "''") + "'"
        sql = sql.replace('#{' + key + '}', literal)
    assert '#{' not in sql
    return sql


def comparison(sqls, account, end, cutoff, index, prefix=''):
    end = dt.datetime.fromisoformat(end)
    common = dict(tenant=1, account=account, to=end, cutoff=cutoff)
    old7 = render(sqls['publications'], **common, **{'from': end-dt.timedelta(days=7)})
    old28 = render(sqls['publications'], **common, **{'from': end-dt.timedelta(days=28)})
    new = render(sqls['publicationPeriods'], **common, from7=end-dt.timedelta(days=7), from28=end-dt.timedelta(days=28))
    return (prefix + f" SELECT {index},'old7',published,missing,0 FROM ({old7}) q"
            + f" UNION ALL SELECT {index},'old28',published,missing,0 FROM ({old28}) q"
            + f" UNION ALL SELECT {index},'new',published7,published28,missing FROM ({new}) q;")


def compare_rows(output):
    cases = {}
    for row in output.strip().splitlines():
        index, label, *values = row.split('\t')
        cases.setdefault(index, {})[label] = tuple(int(v) for v in values)
    for index, case in cases.items():
        old7, old28, new = case['old7'], case['old28'], case['new']
        assert new == (old7[0], old28[0], old7[1]) and old7[1] == old28[1], f'case {index} differs'
    return cases


def fixture_prefix():
    # SELECT-only CTEs shadow the production tables; labels contain no personal data.
    batches = []
    def batch(i, submitted='2026-10-02', status='COMPLETED', accounts='[10,11]', account=10,
              finalized=None, tenant=1, deleted=0):
        batches.append(dict(id=i, tenant_id=tenant, deleted=deleted, submitted_at=submitted,
                            status=status, account_ids_json=accounts, account_id=account, finalized_at=finalized))
    batch(1)
    batch(2, '2026-10-23')
    batch(3, status='CANCELLED', finalized='2026-10-20')
    batch(4, status='CANCELED', finalized='2026-10-29')
    batch(5, status='DRAFT')
    batch(6, status='VOID')
    batch(7, '2026-10-29')
    batch(8, accounts=None)
    batch(9, tenant=2)
    batch(10, deleted=1)
    batch(11, accounts='[]')
    batch(12, accounts='')
    batch(13, submitted=None)
    items = []
    def item(i, b, content, planned=None, deleted=0, tenant=1):
        items.append(dict(id=i, batch_id=b, tenant_id=tenant, deleted=deleted, content_id=content,
                          content_snapshot_json=json.dumps({} if planned is None else {'plannedPublishAt': planned})))
    item(1,1,100,'2026-10-05T00:00:00')
    item(2,2,100,'2026-10-23T00:00:00')
    item(3,1,101,'2026-10-01T00:00:00')  # 28-day left boundary included
    item(4,1,102,'2026-10-22T00:00:00')  # 7-day left boundary included
    item(5,1,103,'2026-10-29T00:00:00')  # right boundary excluded
    item(6,1,104)
    item(7,3,105,'2026-10-23T00:00:00')
    item(8,4,106,'2026-10-24T00:00:00')
    for b in (5,6,7,9,10,13):
        item(10+b,b,200+b,'2026-10-24T00:00:00',tenant=2 if b==9 else 1)
    for b in (8,11,12):
        item(30+b,b,300+b,'2026-10-24T00:00:00')
    item(50,1,400,'2026-10-24T00:00:00',deleted=1)
    item(51,2,100,'2026-10-25T00:00:00')  # same submission: larger item ID wins
    def cte(name, rows):
        selects=[]
        for row in rows:
            values=[]
            for key,value in row.items():
                literal='NULL' if value is None else str(value) if isinstance(value,int) else "'"+value.replace("'","''")+"'"
                values.append(literal+' AS '+key)
            selects.append('SELECT '+','.join(values))
        return name+' AS ('+' UNION ALL '.join(selects)+')'
    return 'WITH '+cte('zsjos_content_review_batch',batches)+','+cte('zsjos_content_review_batch_item',items)


if __name__ == '__main__':
    sqls=statements()
    prefix=fixture_prefix()
    fixture_sql='\n'.join(comparison(sqls, account, '2026-10-29', cutoff, index, prefix)
                           for index,(account,cutoff) in enumerate([(10,'2026-10-29'),(11,'2026-10-29'),
                                                                   (12,'2026-10-29'),(10,'2026-10-25')]))
    samples=compare_rows(query('ruoyi-vue-pro',fixture_sql))
    assert samples['0']['new']==(6,7,1),samples['0']
    assert samples['1']['new']==(3,4,1),samples['1']
    assert samples['2']['new']==(0,0,0),samples['2']
    print('PASS 4 SELECT-only boundary fixtures: revisions/ties/multi-account/legacy/cancellation/tenant/deletion/missing/empty/early cutoff')
    accounts=query('ruoyi-vue-pro',"""SELECT DISTINCT account_id FROM zsjos_content_review_batch WHERE tenant_id=1 AND account_id IS NOT NULL
        UNION SELECT DISTINCT j.account_id FROM zsjos_content_review_batch b,
        JSON_TABLE(COALESCE(NULLIF(b.account_ids_json,''),'[]'),'$[*]' COLUMNS(account_id BIGINT PATH '$')) j WHERE b.tenant_id=1""").split()
    cases=[]
    for account in accounts:
        for end,cutoff in [('2026-10-08','2026-10-08'),('2026-10-29','2026-10-25'),('2026-10-29','2026-10-29')]:
            cases.append(comparison(sqls,int(account),end,cutoff,len(cases)))
    start=time.perf_counter()
    result=compare_rows(query('ruoyi-vue-pro',
        'SET TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;\n'
        +'\n'.join(cases)+'\nROLLBACK;'))
    print(f'PASS {len(result)} live snapshot cases across {len(accounts)} accounts: old 7/28-day counts and missing-time totals equal merged query ({time.perf_counter()-start:.2f}s)')
