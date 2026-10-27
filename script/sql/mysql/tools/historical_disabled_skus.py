#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Reviewed tenant-1 historical SKUs: prepare/apply, no DDL or version-ledger writes."""
import argparse
import hashlib
import json
import re
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'historical-student-import'))
import import_students as imp

NOTE = '历史规格，价格未核实，禁止销售；0元仅为技术占位，保持停用。'
SPECS = (
    ('公共营养师', '公共营养师｜人社部技能等级｜一级｜超值精品班', '人社部技能等级｜一级｜超值精品班', 'PACKAGE'),
    ('心理咨询师', '中国心理卫生协会｜中国心理学会｜初级', '中国心理卫生协会｜中国心理学会｜初级', 'PACKAGE'),
    ('心理咨询师', '中国心理卫生协会｜中国心理学会｜助理', '中国心理卫生协会｜中国心理学会｜助理', 'PACKAGE'),
    ('心理咨询师', '中国心理卫生协会｜中国心理学会｜无级别', '中国心理卫生协会｜中国心理学会｜无级别', 'PACKAGE'),
    ('执业中药师', '执业中药师｜金牌押题班｜单科', '金牌押题班｜单科', 'SUBJECT'),
)

def connect(database, connection_file=None):
    imp.check(re.fullmatch(r'[A-Za-z0-9_-]+', database), 'Invalid database identifier')
    if connection_file is None:
        return imp.connect(database)
    # Credentials stay in an operator-managed [client] file, never command arguments or plans.
    import pymysql
    c = pymysql.connect(read_default_file=str(Path(connection_file).resolve()), read_default_group='client',
                        database=database, charset='utf8mb4', autocommit=False,
                        cursorclass=pymysql.cursors.DictCursor)
    with c.cursor() as q:
        q.execute('SET NAMES utf8mb4')
        q.execute("SET time_zone='+08:00'")
        q.execute('SET SESSION innodb_lock_wait_timeout=10')
    return c

def inspect(c, lock=False):
    with c.cursor() as q:
        target = imp.one(q, 'SELECT DATABASE() db,@@server_uuid serverUuid,VERSION() version')
        suffix = ' FOR UPDATE' if lock else ''
        rows = []
        for product, name, value, unit in SPECS:
            ps = imp.fetch(q, 'SELECT * FROM zsjos_product WHERE tenant_id=1 AND deleted=0 AND name=%s ORDER BY id'+suffix, (product,))
            imp.check(len(ps)==1 and ps[0]['name']==product and ps[0]['status']==0, 'Expected one enabled owning product')
            p = ps[0]
            attrs = imp.fetch(q, "SELECT * FROM zsjos_product_attr WHERE tenant_id=1 AND deleted=0 AND spu_id=%s ORDER BY id"+suffix, (p['id'],))
            imp.check(len(attrs)==1 and attrs[0]['attr_key']=='sales_spec' and attrs[0]['status']==0,
                      'Owning product must use the existing single sales_spec attribute')
            a = attrs[0]
            vs = imp.fetch(q, 'SELECT * FROM zsjos_product_attr_value WHERE tenant_id=1 AND deleted=0 AND attr_id=%s AND value=%s ORDER BY id'+suffix, (a['id'], value))
            imp.check(len(vs)<=1 and all(v['value']==value and v['label']==value and v['status']==0 for v in vs), 'Conflicting attribute value; manual review required')
            skus = imp.fetch(q, 'SELECT * FROM zsjos_product_sku WHERE tenant_id=1 AND deleted=0 AND (spu_id=%s OR sku_name=%s) ORDER BY id'+suffix, (p['id'], name))
            desired = {'sales_spec':value}
            matches = [s for s in skus if s['sku_name']==name or (s['spu_id']==p['id'] and json.loads(s['attr_values_json'])==desired)]
            imp.check(len(matches)<=1, 'Duplicate historical SKU; manual review required')
            for s in matches:
                imp.check(s['sku_name']==name and s['spu_id']==p['id'] and json.loads(s['attr_values_json'])==desired
                          and s['status']==1 and s['price_unit']==unit, 'Existing SKU is enabled or incompatible; refuse to overwrite')
                imp.check(s['price']==0 and s['pricing_note']==NOTE, 'Historical price placeholder requires review')
                actual = imp.one(q, 'SELECT HEX(sku_name) h FROM zsjos_product_sku WHERE tenant_id=1 AND id=%s',(s['id'],))
                imp.check(actual['h']==name.encode('utf-8').hex().upper(), 'Stored SKU UTF-8 mismatch')
            rows.append(dict(productId=p['id'], productRef=p['product_ref'], attributeId=a['id'], name=name,
                             value=value, unit=unit, existingSkuId=matches[0]['id'] if matches else None,
                             existingValueId=vs[0]['id'] if vs else None,
                             stateHash=imp.digest([p,a,vs,matches])))
    return dict(policy='historical-disabled-skus-20261027-v1',target=target,rows=rows)

def apply(c, plan, fail_after=None):
    c.rollback()
    try:
        with c.cursor() as q:
            q.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        current=inspect(c,True)
        imp.check(current['policy']==plan['policy'] and current['target']==plan['target'], 'Wrong policy/server/database')
        # An already completed rerun is a no-op; partial changes require a fresh reviewed plan.
        if all(r['existingSkuId'] and r['existingValueId'] for r in current['rows']):
            c.rollback()
            return current
        imp.check(imp.digest(current)==imp.digest(plan), 'Configuration changed; prepare a fresh plan')
        with c.cursor() as q:
            for i,r in enumerate(current['rows'],1):
                if not r['existingValueId']:
                    q.execute("INSERT INTO zsjos_product_attr_value(attr_id,value,label,sort,status,creator,updater,tenant_id) VALUES(%s,%s,%s,999,0,'historical-sku-maintenance','historical-sku-maintenance',1)", (r['attributeId'],r['value'],r['value']))
                if not r['existingSkuId']:
                    attrs=imp.packed({'sales_spec':r['value']})
                    ref='sku_'+uuid.uuid5(uuid.NAMESPACE_URL,'zsjos:historical-sku:'+r['productRef']+':'+r['name']).hex
                    q.execute("INSERT INTO zsjos_product_sku(spu_id,sku_ref,sku_name,attr_values_json,attr_values_hash,price,retail_price,price_unit,pricing_note,status,sort,remark,creator,updater,tenant_id) VALUES(%s,%s,%s,%s,%s,0,0,%s,%s,1,999,%s,'historical-sku-maintenance','historical-sku-maintenance',1)", (r['productId'],ref,r['name'],attrs,hashlib.sha256(attrs.encode('utf-8')).hexdigest(),r['unit'],NOTE,NOTE))
                if fail_after==i:raise RuntimeError('Injected configuration rollback')
            final=inspect(c,True)
            imp.check(all(r['existingSkuId'] and r['existingValueId'] for r in final['rows']), 'Historical SKU postcondition failed')
        c.commit()
        return final
    except Exception:
        c.rollback()
        raise

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=('prepare','apply'))
    p.add_argument('--database',required=True)
    p.add_argument('--connection-file',type=Path)
    p.add_argument('--plan',type=Path,required=True)
    p.add_argument('--sha256')
    p.add_argument('--result',type=Path)
    a=p.parse_args()
    c=connect(a.database,a.connection_file)
    try:
        if a.command=='prepare':
            result=inspect(c)
            imp.save_private(a.plan,result)
            print(imp.packed({'missingSkus':sum(r['existingSkuId'] is None for r in result['rows']), 'sha256':hashlib.sha256(a.plan.read_bytes()).hexdigest()}))
        else:
            imp.check(a.sha256 and hashlib.sha256(a.plan.read_bytes()).hexdigest()==a.sha256,'Exact reviewed plan SHA256 required')
            imp.check(a.result and not a.result.exists() and not a.result.resolve().is_relative_to(imp.ROOT),'New private result path required')
            result=apply(c,json.loads(a.plan.read_text(encoding='utf-8')))
            imp.save_private(a.result,result)
            print('Verified: all five historical SKUs exist and remain disabled')
    finally:c.rollback();c.close()

if __name__=='__main__':
    try:main()
    except Exception as e:
        print(str(e) if isinstance(e,ValueError) else type(e).__name__+': failed; inspect target before recovery',file=sys.stderr)
        sys.exit(1)
