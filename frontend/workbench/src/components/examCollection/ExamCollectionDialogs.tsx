import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import BusinessTable from '../BusinessTable';
import { useBusinessOverlay } from '../OverlayCoordinator';
import { examCollectionApi, type ExamCandidate, type ExamClass, type ExamTaskDetail } from '../../services/examCollection';
import { studentInfoError, type StudentInfoField } from '../../services/studentInfo';

const modalStyles = { container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column' as const},body:{overflowY:'auto' as const,minHeight:0},footer:{flexShrink:0} };
export function ExamCreateDialog({onClose,onCreated}:{onClose:()=>void;onCreated:(task:ExamTaskDetail)=>void}) {
  useBusinessOverlay(true);
  const [form]=Form.useForm<{title:string;description:string}>();
  const [fields,setFields]=useState<StudentInfoField[]>([]),[selected,setSelected]=useState<string[]>([]);
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [ready,setReady]=useState(false);
  useEffect(()=>{let active=true;setLoading(true);setError('');setReady(false);void examCollectionApi.fields().then(v=>{if(active){setFields(v);setReady(true)}}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[reload]);
  const create=async()=>{
    if(busy||loading||!ready)return;
    const values=await form.validateFields().catch(()=>undefined);if(!values)return;
    setBusy(true);setError('');try{onCreated(await examCollectionApi.create({...values,selectedFields:selected}));}catch(e){setError(studentInfoError(e))}finally{setBusy(false)}
  };
  return <Modal open title="发起考试资料收集" width={720} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles}
    onCancel={()=>{if(!busy)onClose()}} onOk={()=>void create()} okText="生成统一链接" confirmLoading={busy} okButtonProps={{disabled:loading||!ready}} closable={!busy} maskClosable={false}>
    <Space orientation="vertical" className="exam-collection-stack">
      {error&&<Alert type="error" title={error} action={!fields.length?<Button onClick={()=>setReload(v=>v+1)}>重新加载字段</Button>:undefined}/>}
      <Form form={form} layout="vertical" disabled={busy} initialValues={{title:'',description:''}}>
        <Form.Item name="title" label="任务名称" rules={[{required:true,whitespace:true,message:'请填写任务名称'},{max:200}]}><Input placeholder="例如：11 月健康管理师考试资料收集" maxLength={200}/></Form.Item>
        <Form.Item name="description" label="填写说明" rules={[{max:2000}]}><Input.TextArea rows={3} maxLength={2000} showCount placeholder="向学员说明材料要求和注意事项"/></Form.Item>
      </Form>
      <Alert type="info" showIcon title="姓名、手机号、微信号、身份证号码固定必填" description="勾选的材料必填，未勾选的字段不会出现在收集表中。"/>
      <Typography.Text strong>选择本次收集字段（已选 {selected.length} 项）</Typography.Text>
      {loading?<Spin/>:!ready?null:fields.length?<Checkbox.Group className="exam-collection-field-options" value={selected} disabled={busy} onChange={v=>setSelected(v.map(String))}>
        {fields.map(f=><Checkbox key={f.key} value={f.key}>{f.label}</Checkbox>)}
      </Checkbox.Group>:<Empty description="尚无可用的已发布材料字段"/>}
      <Typography.Text type="secondary">生成后可复制统一链接，再选择服务中的班级通知学习规划师。</Typography.Text>
    </Space>
  </Modal>;
}
export function ExamNotifyDialog({task,onClose,onSaved}:{task:ExamTaskDetail;onClose:()=>void;onSaved:()=>void}){
  useBusinessOverlay(true);
  const [classes,setClasses]=useState<ExamClass[]>([]),[selected,setSelected]=useState<number[]>([]);
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  useEffect(()=>{let active=true;setLoading(true);setError('');void examCollectionApi.classes().then(v=>{if(active)setClasses(v)}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[reload]);
  const notify=async()=>{if(busy||!selected.length)return;setBusy(true);setError('');try{await examCollectionApi.notify(task.id,task.revision,selected);onSaved()}catch(e){setError(studentInfoError(e))}finally{setBusy(false)}};
  return <Modal open title="选择班级并通知学习规划师" width={640} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles} maskClosable={false}
    onCancel={()=>{if(!busy)onClose()}} onOk={()=>void notify()} okText={`通知 ${selected.length} 个班级`} confirmLoading={busy} okButtonProps={{disabled:loading||!selected.length}}>
    <Space orientation="vertical" className="exam-collection-stack">
      <Typography.Text strong>{task.title}</Typography.Text>
      {error&&<Alert type="error" title={error} action={<Button onClick={()=>setReload(v=>v+1)}>重试加载</Button>}/>}
      <Select mode="multiple" aria-label="选择通知班级" placeholder="选择服务中的班级" className="exam-collection-full" showSearch optionFilterProp="label" loading={loading} disabled={busy}
        value={selected} onChange={setSelected} options={classes.map(c=>({value:c.id,label:`${c.name} · ${c.plannerName||'未分配学习规划师'}`,disabled:!c.plannerId}))}/>
      <Alert type="info" showIcon title="本次通知将固定应收名单" description="以后转班不会自动改变人数。规划师收齐其负责学员的资料后，待办自动移除；考务也可以结束整个任务。"/>
      {selected.length>0&&<ul className="exam-collection-selection">{classes.filter(c=>selected.includes(c.id)).map(c=><li key={c.id}>{c.name}<span>{c.plannerName}</span></li>)}</ul>}
    </Space>
  </Modal>;
}
export function ExamReasonDialog({title,description,required=true,onClose,onConfirm}:{title:string;description:string;required?:boolean;onClose:()=>void;onConfirm:(reason:string)=>Promise<void>}){
  useBusinessOverlay(true);
  const [reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const confirm=async()=>{if(busy)return;if(required&&!reason.trim()){setError('请填写原因');return}setBusy(true);setError('');try{await onConfirm(reason.trim());onClose()}catch(e){setError(studentInfoError(e))}finally{setBusy(false)}};
  return <Modal open title={title} onCancel={()=>{if(!busy)onClose()}} onOk={()=>void confirm()} confirmLoading={busy} maskClosable={false}>
    <Space orientation="vertical" className="exam-collection-stack"><p>{description}</p>{error&&<Alert type="error" title={error}/>}<Input.TextArea rows={3} value={reason} onChange={e=>setReason(e.target.value)} maxLength={500} showCount disabled={busy} aria-label="操作原因" placeholder={required?'请填写原因，记录将保留在任务历史中':'可填写操作说明'}/></Space>
  </Modal>;
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
