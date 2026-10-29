#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""User-authorized database-only legacy form compatibility; no application restart."""
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import subprocess
import sys
import tempfile
import zipfile

import yaml
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding
from close_legacy_registration_20261028 import connect, require, rows, save, read

MARKER = 'legacy-info-compat-20261029'
IDS = (800,803,804,805,813,817,818,824,826,828,829,830,831,832,833,834,
       835,836,837,838,839,847,855,858,859,861,862,863,864,868,869,870,1127)
ID_SQL = ','.join(map(str, IDS))
CASE, FORM = 'zsjos_registration_case', 'zsjos_student_info_form'
ZERO = b'\x00'
RELATED = ('zsjos_registration_case_checklist_item','zsjos_registration_case_route',
           'zsjos_registration_class_assignment','zsjos_registration_item_attachment',
           'zsjos_registration_item','zsjos_service_relation','zsjos_registration_command')

# Uses the deployed libraries offline, not the running Spring service. Secret material is stdin-only.
JAVA = r'''
import java.lang.reflect.*;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFormService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationServiceImpl;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationCaseRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
public class LegacyInfoCheck {
  static void check(boolean value) { if (!value) throw new IllegalStateException("Contract mismatch"); }
  public static void main(String[] args) throws Exception {
    var mapper = new ObjectMapper(); var input = mapper.readTree(System.in);
    var fields = new StudentInfoFields();
    var definitions = fields.parse(input.get("fields").asText());
    definitions.removeIf(f -> !Boolean.TRUE.equals(f.getEnabled()));
    definitions = fields.withAttachmentPolicy(definitions);
    if (input.has("tokens")) {
      var aes = SecureUtil.aes(Base64.getDecoder().decode(input.get("key").asText()));
      var aesField = EncryptTypeHandler.class.getDeclaredField("aes");
      aesField.setAccessible(true); aesField.set(null,aes);
      var handler = new EncryptTypeHandler();
      for (var pair : input.get("tokens")) {
        var ciphertext = pair.get("ciphertext").asText();
        var rs = (ResultSet)Proxy.newProxyInstance(LegacyInfoCheck.class.getClassLoader(),
          new Class<?>[]{ResultSet.class},(p,m,a)->m.getName().equals("getString")?ciphertext:null);
        var raw = handler.getNullableResult(rs,"token_ciphertext");
        check(raw.matches("[A-Za-z0-9_-]{43}"));
        check(SecureUtil.sha256(raw).equals(pair.get("hash").asText()));
      }
    }
    var form = new StudentInfoFormDO(); form.setStatus("DRAFT");
    form.setExpiresAt(LocalDateTime.now().plusDays(30));
    var state = StudentInfoFormService.class.getDeclaredMethod("state",StudentInfoFormDO.class);
    state.setAccessible(true); check("DRAFT".equals(state.invoke(null,form)));
    var service = new RegistrationServiceImpl(); var c = new RegistrationCaseDO(); c.setStatus("pending");
    var order = new SalesOrderDO(); order.setStatus("effective");
    var result = new RegistrationCaseRespVO(); result.setInformationSent(false); result.setItems(List.of());
    var gate = RegistrationServiceImpl.class.getDeclaredMethod("applyCompletionState",
      RegistrationCaseRespVO.class,RegistrationCaseDO.class,SalesOrderDO.class);
    gate.setAccessible(true); gate.invoke(service,result,c,order);
    check("information_not_sent".equals(result.getCompletionBlockCode()));
    result.setInformationSent(true); gate.invoke(service,result,c,order);
    check("checklist_incomplete".equals(result.getCompletionBlockCode()));
    check(Boolean.FALSE.equals(result.getCompletable()));
    System.out.println("LEGACY_CHECK:"+mapper.writeValueAsString(definitions));
  }
}
'''


def runtime():
    pid = subprocess.check_output(['systemctl','show','zsjos-backend','-p','MainPID','--value'],text=True).strip()
    require(pid.isdigit() and int(pid)>0,'Backend process unavailable')
    proc=Path('/proc')/pid
    argv=proc.joinpath('cmdline').read_text().split('\0')
    jar=Path(argv[argv.index('-jar')+1]).resolve()
    env=dict(v.split('=',1) for v in proc.joinpath('environ').read_text().split('\0') if '=' in v)
    # Resolve the inspected packaged default plus external production config and ordinary overrides.
    key=None
    with zipfile.ZipFile(jar) as archive:
        for name in ('BOOT-INF/classes/application.yaml','BOOT-INF/classes/application-prod.yaml'):
            for doc in yaml.safe_load_all(archive.read(name)):
                candidate=(doc or {}).get('mybatis-plus',{}).get('encryptor',{}).get('password')
                if candidate is not None: key=candidate
    external=Path('/opt/zsjos-runtime/application-prod.yaml')
    for doc in yaml.safe_load_all(external.read_text()):
        candidate=(doc or {}).get('mybatis-plus',{}).get('encryptor',{}).get('password')
        if candidate is not None:key=candidate
    key=env.get('MYBATIS_PLUS_ENCRYPTOR_PASSWORD',key)
    for arg in argv+shlex.split(env.get('JAVA_OPTS','')):
        if arg.startswith(('-Dmybatis-plus.encryptor.password=','--mybatis-plus.encryptor.password=')):
            key=arg.split('=',1)[1]
    if isinstance(key,str) and key.startswith('${'):
        match=re.fullmatch(r'\$\{([^:}]+)(?::(.*))?\}',key)
        require(match is not None,'Unsupported encryption property expression')
        key=env.get(match[1],match[2])
    require(isinstance(key,str) and len(key.encode()) in (16,24,32),'Encryption key unavailable')
    start=proc.joinpath('stat').read_text().rsplit(')',1)[1].split()[19]
    return dict(pid=pid,start_ticks=start,jar=str(jar)),key.encode()


def java_check(jar, fields, key, additions):
    with tempfile.TemporaryDirectory(prefix='legacy-info-offline-') as directory:
        root=Path(directory);lib=root/'lib';lib.mkdir()
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                    (lib/Path(name).name).write_bytes(archive.read(name))
        (root/'LegacyInfoCheck.java').write_text(JAVA,encoding='utf-8')
        cp=str(lib/'*')
        compiled=subprocess.run(['javac','-encoding','UTF-8','-cp',cp,str(root/'LegacyInfoCheck.java')],capture_output=True)
        require(compiled.returncode==0,'Offline deployed-library compilation failed')
        payload=dict(fields=fields,key=base64.b64encode(key).decode(),
                     tokens=[dict(ciphertext=r['token_ciphertext'],hash=r['token_hash']) for r in additions])
        tested=subprocess.run(['java','-cp',str(root)+os.pathsep+cp,'LegacyInfoCheck'],
                              input=json.dumps(payload).encode(),capture_output=True)
        require(tested.returncode==0,'Offline deployed-library compatibility failed')
        output=[line[len('LEGACY_CHECK:'):] for line in tested.stdout.decode().splitlines() if line.startswith('LEGACY_CHECK:')]
        require(len(output)==1,'Offline contract result missing')
        return json.loads(output[0])


def mutable(q,prefix=''):
    return {t:rows(q,f'SELECT * FROM {prefix}{t} ORDER BY id') for t in (CASE,FORM)}


def capture(q,lock=False):
    suffix=' FOR UPDATE' if lock else ''
    parents=rows(q,f'SELECT * FROM zsjos_order WHERE tenant_id=1 AND id IN '
                  f'(SELECT order_id FROM {CASE} WHERE tenant_id=1 AND id IN ({ID_SQL})) ORDER BY id'+suffix)
    require(bool(parents),'Missing parent orders')
    rows(q,f'SELECT * FROM {CASE} WHERE tenant_id=1 AND id IN ({ID_SQL}) ORDER BY id'+suffix)
    rows(q,f'SELECT * FROM {FORM} WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL}) ORDER BY id'+suffix)
    state={'mutable':mutable(q),'orders':parents}
    order_ids=','.join(str(r['id']) for r in parents)
    for t in ('zsjos_order_item','zsjos_order_approval_round'):
        state[t]=rows(q,f'SELECT * FROM {t} WHERE tenant_id=1 AND order_id IN ({order_ids}) ORDER BY id')
    for t in RELATED:
        state[t]=rows(q,f'SELECT * FROM {t} WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL}) ORDER BY id'+suffix)
    state['tasks']=rows(q,"SELECT * FROM zsjos_business_task WHERE tenant_id=1 AND "
                        f"biz_type IN ('registration_case','registration-case') AND biz_id IN ({ID_SQL}) ORDER BY id"+suffix)
    for t in ('zsjos_student_info_form_value','zsjos_student_info_image'):
        state[t]=rows(q,f'SELECT * FROM {t} WHERE tenant_id=1 AND form_id IN '
                      f'(SELECT id FROM {FORM} WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL})) ORDER BY id')
    state['config']=rows(q,"SELECT * FROM zsjos_student_info_form_config WHERE tenant_id=1 AND deleted=0 AND status='PUBLISHED'"+suffix)
    return state


def validate(state):
    require(len(state['config'])==1 and state['config'][0]['id']==3,'Published information config changed')
    selected=[r for r in state['mutable'][CASE] if r['tenant_id']==1 and r['id'] in IDS]
    require(len(selected)==33,'Target membership changed')
    orders={r['id']:r for r in state['orders']};items={r['id']:r for r in state['zsjos_order_item']}
    for c in selected:
        o,i=orders.get(c['order_id']),items.get(c['order_item_id'])
        require(c['deleted']==ZERO and c['status']=='pending' and c['assignment_mode']=='class_per_item'
                and c['updater']=='registration-mode-20261029' and c['completed_at'] is None
                and o is not None and o['deleted']==ZERO and o['status']=='effective' and o['person_id'] is not None
                and i is not None and i['deleted']==ZERO and i['order_id']==o['id']
                and i['product_id'] is None and i['product_ref'] is None,'Case/order/product prerequisite changed')
    require(not any(r['tenant_id']==1 and r['registration_case_id'] in IDS for r in state['mutable'][FORM]),'A target form already exists')
    for t in RELATED[2:]+('tasks',):
        require(not any(r['deleted']==ZERO for r in state[t]),'Related activity requires review: '+t)
    require(len(state[RELATED[0]])==198 and all(r['checked']==ZERO for r in state[RELATED[0]]),'Checklist changed')


def additions(state,now,key,definitions):
    orders={r['id']:r for r in state['orders']};items={r['id']:r for r in state['zsjos_order_item']}
    result=[]
    for c in state['mutable'][CASE]:
        if c['tenant_id']!=1 or c['id'] not in IDS:continue
        i=items[c['order_item_id']];name=json.loads(i['product_snapshot']).get('name')
        require(isinstance(name,str) and bool(name),'Historical product name unavailable')
        raw=secrets.token_urlsafe(32).encode()
        pad=padding.PKCS7(128).padder();padded=pad.update(raw)+pad.finalize()
        encryptor=Cipher(algorithms.AES(key),modes.ECB()).encryptor()
        ciphertext=base64.b64encode(encryptor.update(padded)+encryptor.finalize()).decode()
        result.append(dict(tenant_id=1,lead_id=None,sales_user_id=0,config_version_id=state['config'][0]['id'],
            token_hash=hashlib.sha256(raw).hexdigest(),token_ciphertext=ciphertext,status='DRAFT',
            expires_at=now+dt.timedelta(days=30),revoked_at=None,submitted_at=None,submit_source=None,
            creator=MARKER,create_time=now,updater=MARKER,update_time=now,deleted=ZERO,
            registration_case_id=c['id'],order_item_id=c['order_item_id'],person_id=orders[c['order_id']]['person_id'],
            product_id=None,sent_by_user_id=None,sent_at=now,
            fields_snapshot=json.dumps(definitions,ensure_ascii=False,separators=(',',':')),
            category_path_snapshot=None,product_name=name,draft_snapshot=None,draft_revision=0,draft_saved_at=None))
    return result


def expected_cases(before,now):
    return [dict(r,version=r['version']+1,updater=MARKER,update_time=now)
            if r['tenant_id']==1 and r['id'] in IDS else dict(r) for r in before]


def verify_mutable(state,current,now,new):
    require(current[CASE]==expected_cases(state['mutable'][CASE],now),'Case field whitelist differs')
    original_ids={r['id'] for r in state['mutable'][FORM]}
    require([r for r in current[FORM] if r['id'] in original_ids]==state['mutable'][FORM],'Original forms changed')
    actual=[{k:v for k,v in r.items() if k!='id'} for r in current[FORM] if r['id'] not in original_ids]
    require(actual==[dict(r,active_lead_id=None) for r in new],'Inserted forms differ from intent')


def apply(q,state,now,new,prefix='',fail=None):
    current=mutable(q,prefix)
    if current!=state['mutable']:
        verify_mutable(state,current,now,new);return 0
    for n,row in enumerate(new,1):
        q.execute(f"INSERT INTO {prefix}{FORM} ({','.join(row)}) VALUES ({','.join(['%s']*len(row))})",tuple(row.values()))
        if n==fail:raise RuntimeError('Injected failure')
    if fail=='forms':raise RuntimeError('Injected failure')
    for r in state['mutable'][CASE]:
        if r['tenant_id']!=1 or r['id'] not in IDS:continue
        q.execute(f'UPDATE {prefix}{CASE} SET version=version+1,updater=%s,update_time=%s '
                  "WHERE tenant_id=1 AND id=%s AND version=%s AND status='pending' AND deleted=0",
                  (MARKER,now,r['id'],r['version']))
        require(q.rowcount==1,'Concurrent case modification')
    verify_mutable(state,mutable(q,prefix),now,new)
    if fail=='final':raise RuntimeError('Injected failure')
    return 33


def fixtures(db,state,now,new):
    prefix='tmp_legacy_info_'
    with db.cursor() as q:
        for t in (CASE,FORM):
            q.execute(f'CREATE TEMPORARY TABLE {prefix}{t} LIKE {t}')
            cols=rows(q,'SELECT column_name name FROM information_schema.columns WHERE table_schema=DATABASE() '
                        "AND table_name=%s AND generation_expression='' ORDER BY ordinal_position",(t,))
            names=','.join('`'+r['name']+'`' for r in cols)
            q.execute(f'INSERT INTO {prefix}{t} ({names}) SELECT {names} FROM {t}')
    db.commit()
    with db.cursor() as q:
        require(mutable(q,prefix)==state['mutable'],'Fixture source changed')
        for phase in (1,17,33,'forms','final'):
            try:
                apply(q,state,now,new,prefix,phase);raise RuntimeError('Failure injection not reached')
            except RuntimeError as error:
                require(str(error)=='Injected failure','Unexpected fixture error');db.rollback()
            require(mutable(q,prefix)==state['mutable'],'Fixture rollback failed')
        require(apply(q,state,now,new,prefix)==33,'Fixture first execution failed')
        require(apply(q,state,now,new,prefix)==0,'Fixture repeat failed');db.commit()
        q.execute(f'UPDATE {prefix}{FORM} SET sent_at=NULL WHERE registration_case_id=835 AND tenant_id=1')
        try:
            apply(q,state,now,new,prefix);raise RuntimeError('Later edit was not rejected')
        except RuntimeError as error:
            require(str(error)=='Inserted forms differ from intent','Unexpected later-edit error')
        db.rollback();verify_mutable(state,mutable(q,prefix),now,new)
    return dict(first=33,repeat=0,rollback_phases=5,recovery=True,later_edit_rejected=True)


def verify_state(before,after,now,new):
    require({k:v for k,v in before.items() if k!='mutable'}=={k:v for k,v in after.items() if k!='mutable'},'Protected related data changed')
    verify_mutable(before,after['mutable'],now,new)


def verify_hex(q):
    selected=rows(q,f'SELECT product_name,HEX(product_name) name_hex,fields_snapshot,HEX(fields_snapshot) fields_hex '
                    f'FROM {FORM} WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL}) AND deleted=0')
    require(len(selected)==33,'Inserted form count differs')
    for r in selected:
        require(r['product_name'].encode().hex().upper()==r['name_hex']
                and r['fields_snapshot'].encode().hex().upper()==r['fields_hex'],'UTF-8 HEX differs')


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--evidence-dir',type=Path,required=True);parser.add_argument('--apply',action='store_true')
    args=parser.parse_args();os.umask(0o077)
    ep=Path('/etc/zsjos/agent-environment')
    require(os.environ.get('ZSJOS_AGENT_ENV',ep.read_text().strip() if ep.exists() else 'local')=='test','Only authorized test environment allowed')
    args.evidence_dir.mkdir(parents=True,exist_ok=True,mode=0o700)
    identity,key=runtime();db=connect()
    try:
        with db.cursor() as q:
            state=capture(q)
            if (args.evidence_dir/'pending-after.json').exists():
                require(state==read(args.evidence_dir/'pending-after.json'),'Current data differs; review before recovery')
                verify_hex(q);print('Exact post-image and UTF-8 verified; zero writes');return
            require(not (args.evidence_dir/'before.json').exists(),'Existing before-image requires recovery review')
            validate(state)
            require(rows(q,'SELECT COUNT(*) n FROM system_users WHERE id=0')[0]['n']==0,'Technical sentinel conflicts with actual account')
            now=rows(q,'SELECT NOW() now')[0]['now']
        db.rollback()
        definitions=java_check(identity['jar'],state['config'][0]['fields_json'],key,[])
        new=additions(state,now,key,definitions)
        require(java_check(identity['jar'],state['config'][0]['fields_json'],key,new)==definitions,'Deployed field snapshot changed')
        result=fixtures(db,state,now,new);save(args.evidence_dir/'fixtures.json',result);db.rollback()
        require(runtime()==(identity,key),'Backend runtime/config changed during verification')
        if not args.apply:
            print('PASS: 33 forms; deployed encryption/field/status/gate checks; MySQL rollback/repeat/recovery; no business writes');return
        with db.cursor() as q:
            before=capture(q,True);validate(before);require(before==state,'Live prerequisites changed during verification')
            require(rows(q,'SELECT COUNT(*) n FROM system_users WHERE id=0')[0]['n']==0,'Technical actor prerequisite changed')
            # Use the already verified planned timestamp, taken at the start of this repair execution.
            save(args.evidence_dir/'before.json',before)
            save(args.evidence_dir/'intent.json',dict(time=now,new_forms=new,runtime=identity))
            require(apply(q,before,now,new)==33,'Live insert count differs')
            after=capture(q);verify_state(before,after,now,new);verify_hex(q)
            save(args.evidence_dir/'pending-after.json',after)
        db.commit()
        check=connect()
        try:
            with check.cursor() as q:
                persisted=capture(q);require(persisted==after,'Independent post-image differs; inspect committed state')
                verify_state(before,persisted,now,new);verify_hex(q)
            require(runtime()==(identity,key),'Backend runtime/config changed')
            save(args.evidence_dir/'verified.json',dict(time=now,forms=33,status='DRAFT',sent_compatibility=True,
                 submitted=0,independent_readback=True,utf8_hex=True,deployed_library_contract=True,
                 backend_identity=identity,backend_unchanged=True,fixtures=result))
        finally:
            check.rollback();check.close()
        print('COMMITTED: 33 DRAFT compatibility forms; sent gate satisfied; no submissions/messages; independent UTF-8 readback; backend unchanged')
    finally:
        db.rollback();db.close()


if __name__=='__main__':
    try:main()
    except Exception as error:
        print('Compatibility repair stopped: '+(str(error) if type(error) is RuntimeError else type(error).__name__),file=sys.stderr)
        raise SystemExit(1)
