import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, Tag, TreeSelect, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import BusinessTable from '../BusinessTable';
import { useBusinessOverlay } from '../OverlayCoordinator';
import { examCollectionApi, type ExamCandidate, type ExamClass, type ExamTaskDetail, type ExamCreateCommand } from '../../services/examCollection';
import { ApiError } from '../../services/api';
import { studentInfoError, type StudentInfoField } from '../../services/studentInfo';
import { useExamLeaveGuard } from './useExamLeaveGuard';
import { examClassTree, selectedExamClassIds } from '../../services/examClassSelection';

import ExamCollectionBodyPreview from './ExamCollectionBodyPreview';
import { EXAM_ABSENCE_REASON_KEY, EXAM_ATTENDANCE_KEY, examFieldGroups, FIXED_EXAM_FIELDS } from './examFieldLayout';
const BodyEditor = lazy(() => import('./ExamCollectionBodyEditor'));

const modalStyles = { container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column' as const},body:{overflowY:'auto' as const,minHeight:0},footer:{flexShrink:0} };
export function ExamCreateDialog({onClose,onCreated}:{onClose:()=>void;onCreated:(task:ExamTaskDetail)=>void}) {
  useBusinessOverlay(true);
  const [form]=Form.useForm<{title:string;description:string;content:string}>();
  const [fields,setFields]=useState<StudentInfoField[]>([]),[selected,setSelected]=useState<string[]>([]);
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [uploadLimits,setUploadLimits]=useState<Record<string,{maxSizeMb:number;maxImages:number}>>({});
  const selectedUploads=fields.filter(f=>selected.includes(f.key)&&f.type==='images');
  const [customFields,setCustomFields]=useState<StudentInfoField[]>([]);
  const [fieldNotes,setFieldNotes]=useState<Record<string,string>>({}),[openNotes,setOpenNotes]=useState<string[]>([]);
  const updateCustom=(key:string,patch:Partial<StudentInfoField>)=>setCustomFields(current=>current.map(field=>field.key===key?{...field,...patch}:field));
  const [ready,setReady]=useState(false);
  const [pending,setPending]=useState<ExamCreateCommand>(),[discard,setDiscard]=useState(false);
  const [uploads,setUploads]=useState(0),[failures,setFailures]=useState(0);
  const [preview,setPreview]=useState(false);
  const uploadCount=useRef(0);
  const blocked=busy||uploads>0;
  const flight=useRef(false);
  const title=Form.useWatch('title',form),description=Form.useWatch('description',form);
  const content=Form.useWatch('content',form);
  const dirty=Boolean(title?.trim()||description?.trim()||content?.replace(/<[^>]*>/g,'').trim()||content?.match(/<(img|video|table)\b/)||selected.length||customFields.length||Object.values(fieldNotes).some(Boolean)||pending);
  const leave=useExamLeaveGuard(dirty,blocked,pending?'生成结果尚未确认，任务可能已创建。离开后请先在任务列表核对，避免再次发起。':undefined);
  const close=()=>{if(flight.current||uploadCount.current>0)return;if(dirty)setDiscard(true);else onClose()};
  useEffect(()=>{let active=true;setLoading(true);setError('');setReady(false);void examCollectionApi.fields().then(v=>{if(active){setFields(v);setReady(true)}}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[reload]);
  const create=async()=>{
    if(flight.current||loading||!ready||uploadCount.current>0||failures>0)return;
    flight.current=true;setBusy(true);setError('');
    try{
      let command=pending;
      if(!command){const values=await form.validateFields().catch(()=>undefined);if(!values)return;
        if(customFields.some(field=>!field.label.trim()||field.type!=='text'&&((field.options||[]).length<2||(field.options||[]).some(option=>!option.trim())||new Set(field.options?.map(option=>option.trim())).size!==field.options?.length))){setError('请填写自定义字段名称，选择题至少需要两个不重复的非空选项');return;}
        command={...values,customFields,fieldNotes:Object.fromEntries(Object.entries(fieldNotes).filter(([key])=>[...FIXED_EXAM_FIELDS.map(field=>field.key),...selected,...customFields.map(field=>field.key)].includes(key))),selectedFields:[...selected],uploadLimits:Object.fromEntries(selectedUploads.map(f=>[f.key,uploadLimits[f.key]||{maxSizeMb:10,maxImages:1}])),idempotencyKey:crypto.randomUUID()};setPending(command);}
      onCreated(await examCollectionApi.create(command));
    }catch(e){setError(studentInfoError(e));
      // Only a definite server rejection permits editing. An unknown outcome retries the exact command.
      if(e instanceof ApiError&&(e.code>=1_000_000_000||[400,401,403].includes(e.code)))setPending(undefined);
    }finally{flight.current=false;setBusy(false)}
  };
  const disabled=busy||!!pending;
  // The server snapshot marks every selected preset as required, regardless of its published default.
  const ordered=[...FIXED_EXAM_FIELDS,...fields.filter(field=>selected.includes(field.key)).map(field=>({...field,required:true,...(field.type==='images'?uploadLimits[field.key]||{maxSizeMb:10,maxImages:1}:{})})),...customFields]
    .map(field=>({...field,note:fieldNotes[field.key]??field.note}));
  const presetGroups=examFieldGroups(fields);
  const toggle=(key:string,checked:boolean)=>setSelected(current=>checked?[...current,key]:current.filter(value=>value!==key));
  const noteEditor=(field:StudentInfoField)=>{
    const value=fieldNotes[field.key]??field.note??'';
    return openNotes.includes(field.key)||value
      ?<Input.TextArea rows={2} maxLength={500} aria-label={`${field.label}填写说明`} placeholder="显示在该项下方，向学员说明怎么填（选填）" value={value} disabled={disabled}
        onChange={event=>setFieldNotes(current=>({...current,[field.key]:event.target.value}))}/>
      :<Button type="link" size="small" className="exam-create-field__note" disabled={disabled} onClick={()=>setOpenNotes(current=>[...current,field.key])}>+ 添加填写说明</Button>;
  };
  const presetRow=(field:StudentInfoField)=>{
    const checked=selected.includes(field.key),limits=uploadLimits[field.key]||{maxSizeMb:10,maxImages:1};
    return <div key={field.key} className="exam-create-field">
      <Checkbox checked={checked} disabled={disabled} onChange={event=>toggle(field.key,event.target.checked)}>{field.label}</Checkbox>
      {checked&&field.type==='images'&&<Space wrap>
        <label>单个文件大小 <Select aria-label={`${field.label}单个文件大小限制`} style={{width:100}} options={Array.from({length:100},(_,index)=>({value:index+1,label:`${index+1}M`}))} value={limits.maxSizeMb} disabled={disabled}
          onChange={value=>setUploadLimits(current=>({...current,[field.key]:{...limits,maxSizeMb:value??10}}))}/></label>
        {field.key==='identity_images'?<Typography.Text type="secondary">身份证正反面各 1 个文件</Typography.Text>
          :<label>文件数量 <Select aria-label={`${field.label}文件数量限制`} style={{width:90}} options={Array.from({length:9},(_,index)=>({value:index+1,label:`${index+1}个`}))} value={limits.maxImages} disabled={disabled}
            onChange={value=>setUploadLimits(current=>({...current,[field.key]:{...limits,maxImages:value??1}}))}/></label>}
      </Space>}
      {checked&&noteEditor(field)}
    </div>;
  };
  return <>{leave.guard}<Modal open title="发起考试资料收集" width={800} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles}
    onCancel={close} closable={!blocked} maskClosable={false}
    footer={<div className="exam-create-footer">
      <Button onClick={()=>setPreview(true)}>预览电脑 / 手机界面</Button>
      <Space><Button onClick={close}>取消</Button>
        <Button type="primary" loading={busy} disabled={loading||!ready||uploads>0||failures>0} onClick={()=>void create()}>{pending?'重试确认生成结果':'生成统一链接'}</Button></Space>
    </div>}>
    <Space orientation="vertical" className="exam-collection-stack">
      {error&&<Alert type="error" title={error} action={!fields.length?<Button onClick={()=>setReload(v=>v+1)}>重新加载字段</Button>:undefined}/>}
      {pending&&!busy&&<Alert type="warning" title="生成结果尚未确认" description="填写内容已保留，请重试确认结果；重试会返回同一任务，不会重复创建。"/>}
      <section className="exam-create-section">
        <Typography.Title level={5} className="exam-collection-title">任务信息</Typography.Title>
        <Form form={form} layout="vertical" disabled={disabled} initialValues={{title:'',description:'',content:''}}>
          <Form.Item name="title" label="任务名称" rules={[{required:true,whitespace:true,message:'请填写任务名称'},{max:200}]}><Input placeholder="例如：11 月健康管理师考试资料收集" maxLength={200}/></Form.Item>
          <Form.Item name="description" label="任务说明" extra="显示在收集表标题下方" rules={[{max:2000}]}><Input.TextArea rows={2} maxLength={2000} showCount placeholder="向学员说明材料要求和注意事项"/></Form.Item>
          <div inert={disabled}>
            <Suspense fallback={<Spin/>}><Form.Item name="content" label="收集表正文" rules={[{max:200000,message:'正文格式内容不能超过 200000 字符'}]}>
              <BodyEditor onError={setError} onFailureChange={setFailures} onUploadChange={delta=>{uploadCount.current=Math.max(0,uploadCount.current+delta);setUploads(uploadCount.current)}}/>
            </Form.Item></Suspense>
          </div>
        </Form>
      </section>
      <section className="exam-create-section">
        <Typography.Title level={5} className="exam-collection-title">收集字段</Typography.Title>
        <Typography.Text type="secondary">收集表共 {ordered.length} 项，排列顺序与收集表一致。勾选的资料均为必填；自定义字段可单独设置是否必填。</Typography.Text>
        <Typography.Text strong className="exam-create-group">基本信息</Typography.Text>
        {FIXED_EXAM_FIELDS.map(field=><div key={field.key} className="exam-create-field">
          <div className="exam-create-field__head"><Checkbox checked disabled>{field.label}</Checkbox>
            <Tag>{field.key===EXAM_ABSENCE_REASON_KEY?'选「否」时必填':'固定必填'}</Tag></div>
          {field.key===EXAM_ATTENDANCE_KEY&&<Typography.Text type="secondary">选项：是 / 否。选「是」显示以下全部内容；选「否」只需填写不参加原因。</Typography.Text>}
          {noteEditor(field)}
        </div>)}
        {loading?<Spin/>:ready&&presetGroups.basic.map(presetRow)}
        {customFields.map(field=><Card key={field.key} size="small" title={`${field.type==='text'?'文本框':field.type==='radio'?'单选题':'多选题'}${field.label.trim()?`：${field.label.trim()}`:''}`}
          extra={<Button type="text" danger disabled={disabled} onClick={()=>setCustomFields(current=>current.filter(item=>item.key!==field.key))}>删除字段</Button>}>
          <Space orientation="vertical" style={{width:'100%'}}>
            <label htmlFor={`${field.key}-label`}>字段名称</label>
            <Input id={`${field.key}-label`} placeholder="例如：是否参加考试" maxLength={64} value={field.label} disabled={disabled} onChange={event=>updateCustom(field.key,{label:event.target.value})}/>
            <Checkbox checked={field.required} disabled={disabled} onChange={event=>updateCustom(field.key,{required:event.target.checked})}>必填</Checkbox>
            {field.type!=='text'&&<>
              <Typography.Text>选项</Typography.Text>
              {(field.options||[]).map((option,index)=><div key={index} style={{display:'flex',gap:'var(--crm-sp-2)',alignItems:'center'}}>
                <Input aria-label={`${field.label||'选择题'}选项${index+1}`} placeholder={`选项${index+1}`} maxLength={200} value={option} disabled={disabled}
                  onChange={event=>updateCustom(field.key,{options:field.options?.map((value,position)=>position===index?event.target.value:value)})}/>
                <Button type="text" aria-label={`删除${field.label||'选择题'}选项${index+1}`} disabled={disabled||(field.options?.length||0)<=2}
                  onClick={()=>updateCustom(field.key,{options:field.options?.filter((_,position)=>position!==index)})}>删除</Button>
              </div>)}
              <Button type="dashed" block disabled={disabled||(field.options?.length||0)>=50}
                onClick={()=>updateCustom(field.key,{options:[...(field.options||[]),'']})}>新增选项</Button>
            </>}
            <label htmlFor={`${field.key}-note`}>填写说明（选填）</label>
            <Input.TextArea id={`${field.key}-note`} rows={2} maxLength={500} placeholder="向填写人说明这一项怎么填" value={fieldNotes[field.key]||''} disabled={disabled}
              onChange={event=>setFieldNotes(current=>({...current,[field.key]:event.target.value}))}/>
          </Space>
        </Card>)}
        <Space wrap>
          {(['text','radio','checkbox'] as const).map(type=><Button key={type} type="dashed" disabled={disabled||customFields.length>=30} onClick={()=>setCustomFields(current=>[...current,{key:`custom_${crypto.randomUUID()}`,label:'',type,enabled:true,required:true,sensitive:false,group:'custom',sort:500+current.length*10,options:type==='text'?undefined:['','']}])}>+ 自定义{type==='text'?'文本框':type==='radio'?'单选':'多选'}</Button>)}
        </Space>
        <Typography.Text strong className="exam-create-group">本次收集材料</Typography.Text>
        {loading?<Spin/>:!ready?null:presetGroups.materials.length?presetGroups.materials.map(presetRow):<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无可用的已发布材料字段"/>}
      </section>
      <Typography.Text type="secondary">生成后可复制统一链接，再选择服务中的班级通知学习规划师。</Typography.Text>
    </Space>
  </Modal>
  {preview&&<ExamCollectionBodyPreview title={title||''} description={description||''} content={content||''} fields={ordered} onClose={()=>setPreview(false)}/>}
  <Modal open={discard} title={pending?'离开尚未确认的创建结果？':'放弃填写内容？'} onCancel={()=>setDiscard(false)} onOk={onClose} okText="离开" cancelText="继续处理">
    <p>{pending?'任务可能已创建。建议先重试确认结果；离开后可在任务列表核对，避免再次发起相同任务。':'任务名称、说明和材料选择尚未提交。'}</p>
  </Modal></>;
}
export function ExamNotifyDialog({task,adjusting=false,onClose,onSaved}:{task:ExamTaskDetail;adjusting?:boolean;onClose:()=>void;onSaved:()=>void}){
  useBusinessOverlay(true);
  const [classes,setClasses]=useState<ExamClass[]>([]),[selected,setSelected]=useState<number[]>(adjusting?[...task.classIds]:[]);
  const [reason,setReason]=useState('');
  const [optionsReady,setOptionsReady]=useState(false);
  const retained=adjusting?task.classIds:[];
  const added=selected.filter(id=>!retained.includes(id));
  const removed=retained.filter(id=>!selected.includes(id));
  const changed=added.length>0||removed.length>0;
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const leave=useExamLeaveGuard(changed||Boolean(reason.trim()),busy);
  useEffect(()=>{let active=true;setLoading(true);setOptionsReady(false);setError('');const request=adjusting?examCollectionApi.classOptions(task.id):examCollectionApi.classes();void request.then(v=>{if(adjusting&&task.classIds.some(id=>!v.some(c=>c.id===id)))throw new Error('已有班级信息未完整加载，请重试');if(active){setClasses(v);setOptionsReady(true)}}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[reload,adjusting,task.id]);
  const notify=async()=>{if(busy||loading||!optionsReady||!selected.length||adjusting&&(!changed||!reason.trim()))return;setBusy(true);setError('');try{if(adjusting)await examCollectionApi.adjustClasses(task.id,task.revision,selected,reason.trim());else await examCollectionApi.notify(task.id,task.revision,selected);onSaved()}catch(e){setError(studentInfoError(e))}finally{setBusy(false)}};
  return <>{leave.guard}<Modal open title={adjusting?'调整收集班级':'选择班级并通知学习规划师'} width={640} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles} maskClosable={false}
    onCancel={()=>void leave.check().then(allowed=>{if(allowed)onClose()})} onOk={()=>void notify()} okText={adjusting?'确认调整班级':`通知 ${selected.length} 个班级`} confirmLoading={busy} okButtonProps={{disabled:loading||!optionsReady||!selected.length||adjusting&&(!changed||!reason.trim())}}>
    <Space orientation="vertical" className="exam-collection-stack">
      <Typography.Text strong>{task.title}</Typography.Text>
      {error&&<Alert type="error" title={error} action={<Button onClick={()=>setReload(v=>v+1)}>重试加载</Button>}/>}
      <TreeSelect aria-label="选择通知班级" placeholder="展开班级组，勾选服务中的班级" className="exam-collection-full exam-notify-class-select" treeCheckable showSearch allowClear
        maxTagCount={0} maxTagPlaceholder={()=>`已选 ${selected.length} 个班级`}
        treeNodeFilterProp="searchText" showCheckedStrategy={TreeSelect.SHOW_CHILD} loading={loading} disabled={busy||!optionsReady}
        treeData={examClassTree(classes,retained)} value={selected.map(id=>`class:${id}`)}
        onChange={values=>setSelected(selectedExamClassIds(values,classes,retained))}/>
      <Alert type="info" showIcon title={adjusting?`新增 ${added.length} 个班级，移除 ${removed.length} 个班级`:'本次通知将固定应收名单'} description={adjusting?'新增班级按当前服务中学员纳入名单；移除班级的应收成员停止催收，历史资料保留。继续保留的班级沿用原冻结名单，不重新拉取成员。':'以后转班不会自动改变人数。规划师收齐其负责学员的资料后，待办自动移除；考务也可以结束整个任务。'}/>
      {adjusting&&<><p>将移除：{classes.filter(c=>removed.includes(c.id)).map(c=>`${c.groupName?`${c.groupName} / `:''}${c.name}`).join('、')||'无'}</p><Input.TextArea aria-label="调整班级原因" placeholder="请填写新增或移除班级的原因" value={reason} onChange={e=>setReason(e.target.value)} maxLength={500} showCount disabled={busy}/></>}
      {selected.length>0&&<ul className="exam-collection-selection">{classes.filter(c=>selected.includes(c.id)).map(c=><li key={c.id}>{c.groupId != null ? `${c.groupName || '班级组名称未提供'} / ` : ''}{c.name}<span>{c.plannerName}</span></li>)}</ul>}
    </Space>
  </Modal></>;
}
export function ExamReasonDialog({title,description,required=true,onClose,onConfirm}:{title:string;description:string;required?:boolean;onClose:()=>void;onConfirm:(reason:string)=>Promise<void>}){
  useBusinessOverlay(true);
  const [reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const leave=useExamLeaveGuard(Boolean(reason.trim()),busy);
  const confirm=async()=>{if(busy)return;if(required&&!reason.trim()){setError('请填写原因');return}setBusy(true);setError('');try{await onConfirm(reason.trim());onClose()}catch(e){setError(studentInfoError(e))}finally{setBusy(false)}};
  return <>{leave.guard}<Modal open title={title} onCancel={()=>void leave.check().then(allowed=>{if(allowed)onClose()})} onOk={()=>void confirm()} confirmLoading={busy} maskClosable={false}>
    <Space orientation="vertical" className="exam-collection-stack"><p>{description}</p>{error&&<Alert type="error" title={error}/>}<Input.TextArea rows={3} value={reason} onChange={e=>setReason(e.target.value)} maxLength={500} showCount disabled={busy} aria-label="操作原因" placeholder={required?'请填写原因，记录将保留在任务历史中':'可填写操作说明'}/></Space>
  </Modal></>;
}
export function ExamCandidatePicker({taskId,onClose,onSelect}:{taskId:number;onClose:()=>void;onSelect:(candidate:ExamCandidate)=>void}){
  useBusinessOverlay(true);
  const [keyword,setKeyword]=useState(''),[page,setPage]=useState(1),[rows,setRows]=useState<ExamCandidate[]>([]),[total,setTotal]=useState(0);
  const [selected,setSelected]=useState<ExamCandidate>(),[loading,setLoading]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  useEffect(()=>{let active=true;setLoading(true);setError('');void examCollectionApi.candidates(taskId,{keyword,pageNo:page,pageSize:10}).then(v=>{if(active){setRows(v.list);setTotal(v.total)}}).catch(e=>{if(active){setRows([]);setError(studentInfoError(e))}}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[taskId,keyword,page,reload]);
  const columns:ProColumns<ExamCandidate>[]=[{title:'学员姓名',dataIndex:'studentName',width:140},{title:'手机号',dataIndex:'mobile',width:160},{title:'班级 / 服务归属',dataIndex:'className',width:240,render:(_,r)=>r.className||'未分班'}];
  return <Modal open title="选择学员及服务归属" width={880} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles} onCancel={onClose}
    onOk={()=>{if(selected)onSelect(selected)}} okText="选择该学员服务" okButtonProps={{disabled:!selected||loading||!!error}}>
    <BusinessTable<ExamCandidate> mode="compact" tableKey="exam-candidate-picker" rowKey="serviceRelationId" columns={columns} dataSource={rows} loading={loading} error={error} onReload={()=>setReload(v=>v+1)}
      filters={<Input.Search placeholder="姓名、手机号、学员编号" aria-label="检索归属学员" allowClear onSearch={v=>{setKeyword(v);setPage(1);setSelected(undefined)}}/>}
      rowSelection={{type:'radio',selectedRowKeys:selected?[selected.serviceRelationId]:[],onChange:(_,r)=>setSelected(r[0])}}
      pagination={{current:page,pageSize:10,total,showSizeChanger:false,onChange:p=>{setPage(p);setSelected(undefined)}}}/>
    <Typography.Text type="secondary">可以选择通知班级以外的学员；请选择具体服务，姓名与手机号只用于人工核对。</Typography.Text>
  </Modal>;
}
