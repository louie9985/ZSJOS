import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, TreeSelect, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import BusinessTable from '../BusinessTable';
import { useBusinessOverlay } from '../OverlayCoordinator';
import { examCollectionApi, type ExamCandidate, type ExamClass, type ExamTaskDetail, type ExamCreateCommand } from '../../services/examCollection';
import { ApiError } from '../../services/api';
import { studentInfoError, type StudentInfoField } from '../../services/studentInfo';
import { useExamLeaveGuard } from './useExamLeaveGuard';
import { examClassTree, selectedExamClassIds } from '../../services/examClassSelection';

import ExamCollectionBodyPreview from './ExamCollectionBodyPreview';
const BodyEditor = lazy(() => import('./ExamCollectionBodyEditor'));

const modalStyles = { container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column' as const},body:{overflowY:'auto' as const,minHeight:0},footer:{flexShrink:0} };
export function ExamCreateDialog({onClose,onCreated}:{onClose:()=>void;onCreated:(task:ExamTaskDetail)=>void}) {
  useBusinessOverlay(true);
  const [form]=Form.useForm<{title:string;description:string;content:string}>();
  const [fields,setFields]=useState<StudentInfoField[]>([]),[selected,setSelected]=useState<string[]>([]);
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [uploadLimits,setUploadLimits]=useState<Record<string,{maxSizeMb:number;maxImages:number}>>({});
  const selectedUploads=fields.filter(f=>selected.includes(f.key)&&f.type==='images');
  const [ready,setReady]=useState(false);
  const [pending,setPending]=useState<ExamCreateCommand>(),[discard,setDiscard]=useState(false);
  const [uploads,setUploads]=useState(0),[failures,setFailures]=useState(0);
  const [preview,setPreview]=useState(false);
  const uploadCount=useRef(0);
  const blocked=busy||uploads>0;
  const flight=useRef(false);
  const title=Form.useWatch('title',form),description=Form.useWatch('description',form);
  const content=Form.useWatch('content',form);
  const dirty=Boolean(title?.trim()||description?.trim()||content?.replace(/<[^>]*>/g,'').trim()||content?.match(/<(img|video|table)\b/)||selected.length||pending);
  const leave=useExamLeaveGuard(dirty,blocked,pending?'生成结果尚未确认，任务可能已创建。离开后请先在任务列表核对，避免再次发起。':undefined);
  const close=()=>{if(flight.current||uploadCount.current>0)return;if(dirty)setDiscard(true);else onClose()};
  useEffect(()=>{let active=true;setLoading(true);setError('');setReady(false);void examCollectionApi.fields().then(v=>{if(active){setFields(v);setReady(true)}}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[reload]);
  const create=async()=>{
    if(flight.current||loading||!ready||uploadCount.current>0||failures>0)return;
    flight.current=true;setBusy(true);setError('');
    try{
      let command=pending;
      if(!command){const values=await form.validateFields().catch(()=>undefined);if(!values)return;
        command={...values,selectedFields:[...selected],uploadLimits:Object.fromEntries(selectedUploads.map(f=>[f.key,uploadLimits[f.key]||{maxSizeMb:10,maxImages:1}])),idempotencyKey:crypto.randomUUID()};setPending(command);}
      onCreated(await examCollectionApi.create(command));
    }catch(e){setError(studentInfoError(e));
      // Only a definite server rejection permits editing. An unknown outcome retries the exact command.
      if(e instanceof ApiError&&(e.code>=1_000_000_000||[400,401,403].includes(e.code)))setPending(undefined);
    }finally{flight.current=false;setBusy(false)}
  };
  return <>{leave.guard}<Modal open title="发起考试资料收集" width="min(1400px, calc(100vw - 32px))" style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles}
    onCancel={close} onOk={()=>void create()} okText={pending?'重试确认生成结果':'生成统一链接'} confirmLoading={busy} okButtonProps={{disabled:loading||!ready||uploads>0||failures>0}} closable={!blocked} maskClosable={false}>
    <Space orientation="vertical" className="exam-collection-stack">
      {error&&<Alert type="error" title={error} action={!fields.length?<Button onClick={()=>setReload(v=>v+1)}>重新加载字段</Button>:undefined}/>}
      {pending&&!busy&&<Alert type="warning" title="生成结果尚未确认" description="填写内容已保留，请重试确认结果；重试会返回同一任务，不会重复创建。"/>}
      <Form form={form} layout="vertical" disabled={busy||!!pending} initialValues={{title:'',description:'',content:''}}>
        <Form.Item name="title" label="任务名称" rules={[{required:true,whitespace:true,message:'请填写任务名称'},{max:200}]}><Input placeholder="例如：11 月健康管理师考试资料收集" maxLength={200}/></Form.Item>
        <div inert={busy||!!pending}>
          <Suspense fallback={<Spin/>}><Form.Item name="content" label="收集表正文" rules={[{max:200000,message:'正文格式内容不能超过 200000 字符'}]}>
            <BodyEditor onError={setError} onFailureChange={setFailures} onUploadChange={delta=>{uploadCount.current=Math.max(0,uploadCount.current+delta);setUploads(uploadCount.current)}}/>
          </Form.Item></Suspense>
        </div>
        <Button onClick={()=>setPreview(true)}>预览电脑 / 手机界面</Button>
        <Form.Item name="description" label="填写说明" rules={[{max:2000}]}><Input.TextArea rows={3} maxLength={2000} showCount placeholder="向学员说明材料要求和注意事项"/></Form.Item>
      </Form>
      <Alert type="info" showIcon title="姓名、手机号、身份证号码固定必填" description="勾选的材料必填，未勾选的字段不会出现在收集表中。"/>
      <Typography.Text strong>选择本次收集字段（已选 {selected.length} 项）</Typography.Text>
      {loading?<Spin/>:!ready?null:fields.length?<Checkbox.Group className="exam-collection-field-options" value={selected} disabled={busy||!!pending} onChange={v=>setSelected(v.map(String))}>
        {fields.map(f=><Checkbox key={f.key} value={f.key}>{f.label}</Checkbox>)}
      </Checkbox.Group>:<Empty description="尚无可用的已发布材料字段"/>}
      {selectedUploads.map(field=>{
        const limits=uploadLimits[field.key]||{maxSizeMb:10,maxImages:1};
        return <Space key={field.key} orientation="vertical">
          <Typography.Text strong>{field.label}</Typography.Text>
          <Space wrap>
            <label>单个文件大小限制 <Select aria-label={`${field.label}单个文件大小限制`} style={{width:120}} options={Array.from({length:100},(_,index)=>({value:index+1,label:`${index+1}M`}))} value={limits.maxSizeMb} disabled={busy||!!pending}
              onChange={value=>setUploadLimits(current=>({...current,[field.key]:{...limits,maxSizeMb:value??10}}))}/></label>
            <label>文件数量限制 <Select aria-label={`${field.label}文件数量限制`} style={{width:100}} options={Array.from({length:9},(_,index)=>({value:index+1,label:`${index+1}个`}))} value={limits.maxImages} disabled={busy||!!pending||field.key==='identity_images'}
              onChange={value=>setUploadLimits(current=>({...current,[field.key]:{...limits,maxImages:value??1}}))}/></label>
          </Space>
          {field.key==='identity_images'&&<Typography.Text type="secondary">身份证正反面各上传 1 个文件</Typography.Text>}
        </Space>;
      })}
      <Typography.Text type="secondary">生成后可复制统一链接，再选择服务中的班级通知学习规划师。</Typography.Text>
    </Space>
  </Modal>
  {preview&&<ExamCollectionBodyPreview title={title||''} description={description||''} content={content||''}
    fields={fields.filter(field=>selected.includes(field.key)).map(field=>field.type==='images'?{...field,...(uploadLimits[field.key]||{maxSizeMb:10,maxImages:1})}:field)} onClose={()=>setPreview(false)}/>}
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
