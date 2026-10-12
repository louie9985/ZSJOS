# -*- coding: utf-8 -*-
"""Source-backed V305 plan builder. No database or filesystem writes in this module."""
import copy
import datetime as dt
import gzip
import hashlib
import json
import re
from collections import Counter
from decimal import Decimal
from zoneinfo import ZoneInfo

from recover_order_actors import dump_values

VERSION = 'V305'
KEY = 'order-history-v305'
SOURCE_SHA = 'bb7f6234c81647ea750ad854622478830e9b90f538a4ed02e2e53a4db60c95db'
EXCLUDED = {'seed-liudandan', 'seed-students', 'seed-llyilin', 'seed-batch4'}
LEGACY_CREATORS = {'legacy-migration', 'legacy-increment-20260917', 'legacy-increment-20260921'}
ACTORS = {
    528: 'OD202609212049560024 OR202609020006 OR202608280009 OR202609160010 OR202609160011'.split(),
    400: ['OD202609221356340006'],
    51: 'OR202608240009 OR202608260007'.split(),
    529: 'OR202608250005 OR202609150013 OR202609150014'.split(),
    37: 'OR202608290001 OR202608290002 OR202608300015 OR202609010005 OR202609010008 OR202609020005 OR202609040008 OR202609150011 OR202609160008 OR202609160009 OR202609160014 OR202609180022'.split(),
    30: 'OR202609040003 OR202609100003 OR202609110009 OR202609140005 OR202609170010 OR202609180019'.split(),
    27: 'OR202609070011 OR202609080001 OR202609080002 OR202609110013 OR202609110018 OR202609160017 OR202609160018 OR202609160019 OR202609180007'.split(),
}
USERNAMES = {528:'lucky',400:'XiaoZhou',51:'XiaoMin',529:'mocaiqiaosales',37:'XiaoHui',30:'XiaoShu',27:'XiaoJiang',19:'MoCaiQiao'}
ORDER_ACTOR = {number: uid for uid, numbers in ACTORS.items() for number in numbers}
LEADS = {
    'LD202608250029': (529,19,421), 'LD202609150034': (529,19,421),
    'LD202609150070': (529,19,421), 'LD202608280039': (528,528,15),
    'LD202609020059': (528,528,15), 'LD202609160067': (528,528,15),
    'LD202609160070': (528,528,15), 'LD202609160052': (37,37,30),
}
DUPLICATES = {'OR202609110002':('P100009','59.90'), 'OR202609160012':('P100014','2180.00'), 'OR202609170008':('P100001','3480.00')}
MISSING_ITEMS = {'OR202609180020':'2380.00','OR202609180021':'4980.00'}
CHANNEL_ORDERS = {'OD202609241427020006','OD202609241950360013'}
FIELDS = 'buyerName studentName studentNature studentMobile studentWechatId provinceCode provinceName cityCode cityName agreedExamTime classType servicePeriod studentSource feeMode paymentMethod remark studentSpecialRequirements materialDeliveryContact giftItems giftShippingAddress paymentVoucherRefs repurchaseReason formalOwnerIdentity totalAmount customerPaidAt'.split()
DICT_TYPES = {f:'zsjos_order_'+re.sub(r'(?<!^)(?=[A-Z])','_',f).lower() for f in ['studentNature','servicePeriod','studentSource','feeMode','paymentMethod']}
SOURCE_LABELS = {'partner':'兼职提交','internal_new_media':'新媒体提交','education_self_sourced':'教务自拓录','sales_self_sourced':'销售自拓录'}
IDENTITIES = {'sales':'销售','education':'教务'}
TABLES = ['zsjos_order','zsjos_order_approval_round','zsjos_order_item','zsjos_lead','zsjos_opportunity','zsjos_performance_attribution']
READ_TABLES = TABLES + ['system_users','system_dept','zsjos_partner','zsjos_performance_org','zsjos_lead_assignment_history']


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',',':'))


def digest(value):
    return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()


def snake(value):
    return re.sub(r'(?<!^)(?=[A-Z])','_',value).lower()


def epoch(value):
    if value is None: return None
    if isinstance(value,(int,float)): return value
    parsed = dt.datetime.fromisoformat(value)
    return int(parsed.replace(tzinfo=ZoneInfo('Asia/Shanghai')).timestamp()*1000)


def source_package(path, order_numbers):
    """Parse the original dump, never execute it; restrict retained evidence to the order set."""
    if hashlib.sha256(path.read_bytes()).hexdigest() != SOURCE_SHA:
        raise ValueError('Unexpected source backup digest')
    wanted = {'orders_order','orders_order_item','leads_lead','employees_employee_profile'}
    columns, rows, active = {}, {t:[] for t in wanted}, None
    with gzip.open(path,'rt',encoding='utf-8',errors='strict') as stream:
        for line in stream:
            match = re.match(r'CREATE TABLE `([^`]+)`',line)
            if match:
                active = match[1] if match[1] in wanted else None
                if active: columns[active]=[]
            if active:
                match = re.match(r'\s*`([^`]+)`\s+',line)
                if match: columns[active].append(match[1])
                if line.startswith(')'): active=None
            match = re.match(r'INSERT INTO `([^`]+)` VALUES ',line)
            if match and match[1] in wanted:
                table=match[1]
                for values in dump_values(line[match.end():]):
                    if len(values)!=len(columns[table]): raise ValueError('Source column mismatch')
                    rows[table].append(dict(zip(columns[table],values)))
    orders=[r for r in rows['orders_order'] if r['order_no'] in order_numbers]
    if len({r['order_no'] for r in orders})!=len(orders): raise ValueError('Duplicate source order')
    ids={r['id'] for r in orders}; leads={r['lead_id'] for r in orders}
    employees={r[k] for r in orders for k in ('recorder_id','sales_id') if r.get(k)}
    return {'sha256':SOURCE_SHA,'capturedAt':'2026-09-20T22:21:03+08:00',
            'orders':orders,'items':[r for r in rows['orders_order_item'] if r['order_id'] in ids],
            'leads':[r for r in rows['leads_lead'] if r['id'] in leads],
            'employees':[r for r in rows['employees_employee_profile'] if r['id'] in employees]}


def build(data, source, now):
    original=copy.deepcopy(data); work=copy.deepcopy(data)
    indexes={t:{r['id']:r for r in work[t]} for t in READ_TABLES}
    orders=[r for r in work['zsjos_order'] if r['tenant_id']==1 and not r['deleted'] and r['status']=='effective' and r['creator'] not in EXCLUDED]
    by_no={r['order_no']:r for r in orders}
    old_orders={r['order_no']:r for r in source['orders']}
    old_leads={r['id']:r for r in source['leads']}
    users={k:v for k,v in indexes['system_users'].items() if v.get('tenant_id')==1 and not v['deleted']}
    for uid in {uid for no,uid in ORDER_ACTOR.items() if no in by_no} | ({19} if any(no in by_no for no in ACTORS[529]) else set()):
        if uid not in users or users[uid]['username']!=USERNAMES[uid] or users[uid]['status']!=0:
            raise ValueError(f'Confirmed account identity unavailable: {uid}')
    def user_name(uid): return users.get(uid,{}).get('nickname')
    inserts=[]; notes=[]; next_ids={t:max(indexes[t],default=0)+1 for t in TABLES}
    def insert(table,row):
        # Symbolic IDs are resolved against actual AUTO_INCREMENT results by the executor.
        row['id']='@'+table+':'+str(len(inserts))
        row.update(tenant_id=1,deleted=0,creator=KEY,updater=KEY,create_time=now,update_time=now)
        inserts.append({'table':table,'after':row}); work[table].append(row)
        return row['id']
    target_leads={o['lead_id'] for o in orders if o.get('lead_id')}
    for l in work['zsjos_lead']:
        if l['id'] not in target_leads or l['tenant_id']!=1 or l['deleted'] or l['lead_no'] not in LEADS: continue
        owner,submitter,old=LEADS[l['lead_no']]
        if l['owner_user_id'] not in (old,owner) or l['source_user_id'] not in (old,submitter):
            raise ValueError('Lead actor changed since review: '+l['lead_no'])
        # Actor identity correction does not establish who sourced this Lead.
        # Provider and contribution changes require a separately confirmed source manifest.
        l.update(owner_user_id=owner,source_user_id=submitter)
        if owner==529: l['owner_identity']='sales'
        for p in work['zsjos_opportunity']:
            if p['tenant_id']==1 and not p['deleted'] and p.get('lead_id')==l['id']:
                if p['owner_user_id'] not in (old,owner): raise ValueError('Opportunity owner changed')
                p['owner_user_id']=owner
    for o in orders:
        uid=ORDER_ACTOR.get(o['order_no'])
        if uid:
            o['submitter_user_id']=o['formal_sales_user_id']=uid
            l=indexes['zsjos_lead'].get(o.get('lead_id'))
            if uid==529: o['formal_owner_identity']='sales'
            elif l and l.get('owner_identity')=='education': o['formal_owner_identity']='education'
    def active_items(o): return [r for r in work['zsjos_order_item'] if r['order_id']==o['id'] and r['tenant_id']==1 and not r['deleted']]
    for no,(product,amount) in DUPLICATES.items():
        if no not in by_no: continue
        items=active_items(by_no[no])
        matches=[i for i in items if (i.get('product_snapshot') or {}).get('legacyProductNo')==product and Decimal(i['payable_amount'])==Decimal(amount)]
        short=[i for i in matches if set(i.get('product_snapshot') or {})=={'name','legacyProductNo'} and i['creator']=='legacy-increment-20260921']
        full=[i for i in matches if 'categoryPath' in (i.get('product_snapshot') or {})]
        if len(short)!=1 or len(full)!=1: raise ValueError('Duplicate item evidence changed: '+no)
        short[0]['deleted']=1
    for o in orders:
        no=o['order_no']
        if o['creator'] not in LEGACY_CREATORS: continue
        old=old_orders.get(no)
        if not old or Decimal(old['total_amount'])!=Decimal(o['total_amount']): raise ValueError('Source order/amount mismatch: '+no)
        legacy_items=[r for r in source['items'] if r['order_id']==old['id']]
        items=active_items(o)
        if not items:
            if no not in MISSING_ITEMS or len(legacy_items)!=1 or Decimal(legacy_items[0]['actual_amount'])!=Decimal(MISSING_ITEMS[no]):
                raise ValueError('Unreviewed missing item: '+no)
            si=legacy_items[0]
            new=dict(order_id=o['id'],product_id=None,sku_id=None,product_ref=None,sku_ref=None,
                     quantity='1.0000',unit_price=si['reference_price_snapshot'],discount_amount='0.00',
                     payable_amount=si['actual_amount'],product_snapshot={'name':si['product_name_snapshot'],'legacyProductNo':si['product_no']},
                     product_rule_snapshot=None,product_rule_version=None)
            insert('zsjos_order_item',new);items=[new]
        for i in items:
            snap=i.get('product_snapshot') or {}
            if set(snap)!={'name','legacyProductNo'}: continue
            candidates=[r for r in legacy_items if r['product_no']==snap['legacyProductNo'] and Decimal(r['actual_amount'])==Decimal(i['payable_amount'])]
            if len(candidates)!=1: raise ValueError('Ambiguous source item: '+no)
            si=candidates[0]
            # Historical labels do not require invented current catalog IDs or SKU mappings.
            i['product_snapshot']={**snap,'name':si['product_name_snapshot'],'price':float(si['reference_price_snapshot']),
                'categoryName':si['sub_category_name_snapshot'] or si['category_name_snapshot'],
                'categoryPath':[{'id':None,'name':s} for s in (si['category_name_snapshot'],si['sub_category_name_snapshot']) if s],
                'level1CategoryName':si['category_name_snapshot'],'level2CategoryName':si['sub_category_name_snapshot'] or None,
                'productRef':i.get('product_ref'),'skuRef':None,'skuName':None,'specs':[],
                'selectedAttrValuesJson':None,'spuUnknown':False,'skuUnknown':True,'legacyOrderItemId':int(si['id'])}
        if sum(Decimal(i['payable_amount']) for i in active_items(o))!=Decimal(o['total_amount']):
            raise ValueError('Item amount mismatch: '+no)
    def profile(o):
        l=indexes['zsjos_lead'].get(o.get('lead_id'))
        if not l or l['deleted'] or l['tenant_id']!=1: return None
        result={k:l.get(v) for k,v in {'leadNo':'lead_no','submittedName':'submitted_name','submittedMobile':'submitted_mobile','submittedWechatId':'submitted_wechat_id',
          'sourceType':'source_type','ownerUserId':'owner_user_id','ownerIdentity':'owner_identity','sourceChannel':'source_channel_id','sourceChannelLabelSnapshot':'source_channel_label_snapshot',
          'provinceName':'province_name','cityName':'city_name','leadCategory':'lead_category','leadCategoryLabelSnapshot':'lead_category_label_snapshot','dispatchMode':'dispatch_mode'}.items()}
        partner=l['source_type']=='partner'
        result.update(sourceSubjectType='PARTNER' if partner else 'ADMIN',sourceSubjectId=l.get('partner_id') if partner else l.get('source_user_id'),
                      ownerIdentityLabel=IDENTITIES.get(l.get('owner_identity'),'未记录'),sourceLabel=SOURCE_LABELS.get(l['source_type'],'来源未配置'),ownerUserName=user_name(l.get('owner_user_id')))
        p=indexes['zsjos_partner'].get(l.get('partner_id'),{})
        suppress=l['source_type'] in ('sales_self_sourced','education_self_sourced') and l.get('source_provider_recorded') and l.get('source_provider_user_id') is None
        result['sourceUserName']=p.get('name') if partner else None if suppress else user_name(l.get('source_user_id'))
        return result
    def actor(o,role):
        uid=o['submitter_user_id' if role=='submitter' else 'formal_sales_user_id']
        identity=o.get('submitter_center_type' if role=='submitter' else 'formal_owner_identity')
        if o['order_no'] in ORDER_ACTOR: return {'subjectType':'ADMIN','id':uid,'name':user_name(uid),'identity':identity}
        imp=o.get('imported_actor_snapshot') or {}; a=imp.get(role)
        if (imp.get('version')==1 and imp.get('tenantId')==1 and imp.get('orderNo')==o['order_no'] and imp.get('sourceSha256')==SOURCE_SHA
            and a and a.get('orderUserId')==uid and a.get('name') and a.get('sourceEmployeeId')):
            return {'subjectType':'LEGACY_EMPLOYEE','id':a['sourceEmployeeId'],'name':a['name'],'identity':None}
        old=old_orders.get(o['order_no'])
        if role=='formalSales' and old and old.get('sales_id') and old.get('sales_name_snapshot'):
            source_id=int(old['sales_id'])
            established=set()
            for evidence_order in work['zsjos_order']:
                evidence=evidence_order.get('imported_actor_snapshot') or {}
                recorded=evidence.get('formalSales') or {}
                if evidence.get('sourceSha256')==SOURCE_SHA and evidence.get('tenantId')==1 and recorded.get('sourceEmployeeId')==source_id:
                    established.add(recorded.get('orderUserId'))
            if established=={uid}:
                return {'subjectType':'LEGACY_EMPLOYEE','id':source_id,'name':old['sales_name_snapshot'],'identity':None}
        return {'subjectType':'ADMIN','id':uid,'name':None,'identity':identity} if uid is not None else None
    for o in orders:
        no=o['order_no']; rounds=[r for r in work['zsjos_order_approval_round'] if r['order_id']==o['id'] and r['tenant_id']==1 and not r['deleted']]
        current=next((r for r in rounds if r['id']==o.get('current_approval_round_id')),None)
        if rounds and (not current or max(r['round_no'] for r in rounds)!=current['round_no']): raise ValueError('Current/latest round mismatch: '+no)
        oldsnap=copy.deepcopy(current.get('order_snapshot') or {}) if current else {}
        if o['creator'] in LEGACY_CREATORS:
            if current and (current['process_definition_key']!='legacy-finance-review' or current.get('process_instance_id')): raise ValueError('Unexpected live BPM: '+no)
            old=old_orders[no];l=indexes['zsjos_lead'].get(o.get('lead_id'));sl=old_leads.get(old['lead_id'])
            if not l or not sl or sl['lead_no']!=l['lead_no']: raise ValueError('Source Lead mismatch: '+no)
            facts={k:o.get(snake(k)) for k in FIELDS}
            for k in ('giftItems','paymentVoucherRefs'):
                if facts[k] is not None: facts[k]=canonical(facts[k])
            facts['totalAmount']=float(o['total_amount']);facts['customerPaidAt']=epoch(o.get('customer_paid_at'))
            labels={k:old.get(snake(k)) or None for k in ('studentNature','servicePeriod','studentSource')}
            labels.update(feeMode=o.get('fee_mode_label_snapshot'),paymentMethod=o.get('payment_method_label_snapshot'))
            snap={'snapshotVersion':2,'capturedAt':epoch(now),'order':facts,'orderLabels':labels,
                  'selections':{k:{'type':v,'value':facts[k],'label':labels[k]} for k,v in DICT_TYPES.items()},
                  'submitter':actor(o,'submitter'),'formalSales':actor(o,'formalSales'),'leadProfile':profile(o)}
            if not current:
                if any(r['order_id']==o['id'] and r['tenant_id']==1 for r in work['zsjos_order_approval_round']): raise ValueError('Deleted round requires review: '+no)
                if not o.get('submitted_at') or not o.get('submitter_user_id'): raise ValueError('Missing required round evidence: '+no)
                completed=old.get('reviewed_at') if old['review_status']=='approved' else None
                if completed: completed=dt.datetime.fromisoformat(completed).replace(microsecond=0).isoformat(sep=' ')
                current={'order_id':o['id'],'round_no':1,'status':'approved','order_snapshot':snap,'process_instance_id':None,
                         'process_definition_key':'legacy-finance-review','submitted_by_user_id':o['submitter_user_id'],
                         'submitted_at':o['submitted_at'],'completed_at':completed,'supervisor_confirmation_enabled':0,'version':0,
                         'submission_idempotency_key':KEY+':'+no}
                o['current_approval_round_id']=insert('zsjos_order_approval_round',current)
            else:
                if current['status']!='approved' and no!='OR202609200030': raise ValueError('Unreviewed round status: '+no)
                current.update(order_snapshot=snap,status='approved')
            notes.append({'order':no,'kind':'reconstructed','sourceOrderId':old['id'],'legacyRevision':old['revision_no'],'legacyStatus':old['review_status'],
                          'basis':'current state and user confirmation; original dump fills recorded labels only'})
        elif oldsnap.get('snapshotVersion')==1:
            snap=copy.deepcopy(oldsnap); facts={k:(oldsnap.get('order') or {}).get(k) for k in FIELDS}
            for k in FIELDS:
                if k in ('customerPaidAt','giftItems','paymentVoucherRefs','formalOwnerIdentity'): continue
                oldval=facts[k];cur=o.get(snake(k))
                if k=='totalAmount': equal=Decimal(str(oldval))==Decimal(str(cur))
                else: equal=oldval==cur
                if not equal: raise ValueError('V1 fact mismatch: '+no+':'+k)
            if no in ORDER_ACTOR: facts['formalOwnerIdentity']=o.get('formal_owner_identity')
            snap.update(snapshotVersion=2,order=facts)
            snap['selections']={k:{'type':v,'value':facts.get(k),'label':snap.get('orderLabels',{}).get(k)} for k,v in DICT_TYPES.items()}
            if no in ORDER_ACTOR: snap.update(submitter=actor(o,'submitter'),formalSales=actor(o,'formalSales'))
            current['order_snapshot']=snap;notes.append({'order':no,'kind':'v1_projection'})
        if no in CHANNEL_ORDERS:
            snap=current['order_snapshot'];p=snap['leadProfile'];l=indexes['zsjos_lead'][o['lead_id']]
            if p.get('sourceChannel')!='其他' or l.get('source_channel_label_snapshot')!='其他' or p.get('sourceChannelLabelSnapshot'):
                raise ValueError('Channel evidence changed: '+no)
            p['sourceChannelLabelSnapshot']='其他'
    for no,uid in ORDER_ACTOR.items():
        if no not in by_no: continue
        o=by_no[no]
        if any(a['tenant_id']==1 and a['fact_type']=='ORDER' and a['fact_id']==o['id'] and not a['deleted'] for a in work['zsjos_performance_attribution']):
            raise ValueError('Attribution already exists outside repair: '+no)
        u=users[uid];dept=u['dept_id'];d=indexes['system_dept'].get(dept)
        mappings=[m for m in work['zsjos_performance_org'] if m['tenant_id']==1 and not m['deleted'] and m['dept_id']==dept]
        center=mappings[0]['center_id'] if len(mappings)==1 else None
        if dept!=(1021 if uid==529 else 1051) or center!=(1020 if uid==529 else None): raise ValueError('Organization changed since approval')
        l=indexes['zsjos_lead'].get(o.get('lead_id'))
        source_type=l.get('source_type') if l else None
        group='repurchase' if o['order_type']=='repurchase' else 'self' if source_type in ('sales_self_sourced','education_self_sourced') else 'inbound' if source_type in ('partner','internal_new_media') else 'unknown'
        histories=[h for h in work['zsjos_lead_assignment_history'] if l and h['tenant_id']==1 and not h['deleted'] and h['lead_id']==l['id'] and h.get('to_owner_user_id')==uid and h['action_type'] in ('accept','claim','transfer') and h.get('occurred_at') and o.get('submitted_at') and h['occurred_at']<=o['submitted_at']]
        latest=max(histories,key=lambda h:(h['occurred_at'],h['id'])) if histories else None
        insert('zsjos_performance_attribution',dict(fact_type='ORDER',fact_id=o['id'],user_id=uid,user_name=u['nickname'],dept_id=dept,dept_name=d['name'],center_id=center,
            center_name=indexes['system_dept'][center]['name'] if center else None,lead_id=o.get('lead_id'),assignment_id=latest['id'] if latest else None,received_at=latest['occurred_at'] if latest else None,
            org_source='current',source_group=group,channel_code=l.get('source_channel_id') if l else None,channel_label=l.get('source_channel_label_snapshot') if l else None,outcome=None,completed_at=None))
    updates=[]
    for table in TABLES:
        before={r['id']:r for r in original[table]}
        for row in work[table]:
            if row['id'] not in before or row==before[row['id']]: continue
            if 'version' in row: row['version']=(row['version'] or 0)+1
            row.update(updater=KEY,update_time=now)
            updates.append({'table':table,'before':before[row['id']],'after':row})
    ops=updates+inserts
    # Dependency-safe ordering: rounds before their new order FK; other insertions use existing orders.
    ops.sort(key=lambda op: (0 if op['table']=='zsjos_lead' else 1 if op['table']=='zsjos_opportunity' else 2 if op['table']=='zsjos_order_item' else 3 if op['table']=='zsjos_order_approval_round' else 4 if op['table']=='zsjos_order' else 5))
    return {'version':VERSION,'repairKey':KEY,'createdAt':now,'source':source,'operations':ops,'notes':notes,
            'eligibleOrders':[o['order_no'] for o in orders], 'counts':dict(Counter(('update:' if 'before' in op else 'insert:')+op['table'] for op in ops))}
