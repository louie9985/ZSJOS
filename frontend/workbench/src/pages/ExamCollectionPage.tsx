import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Input, Result, Select, Space, Spin, Tabs, Tag, Typography } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { useSearchParams } from 'react-router-dom';
import BusinessTable from '../components/BusinessTable';
import { hasPermission } from '../services/managementAccess';
import { ApiError, AuthenticationError } from '../services/api';
import { examCollectionApi, type ExamTask, type ExamTaskDetail } from '../services/examCollection';
import { studentInfoError } from '../services/studentInfo';
import { formatTimestamp } from '../services/time';
import { ExamCreateDialog, ExamNotifyDialog, ExamReasonDialog } from '../components/examCollection/ExamCollectionDialogs';
import { ExamEvents, ExamMembers, ExamSubmissions } from '../components/examCollection/ExamTaskPanels';
import StudentSummaryPage from './StudentSummaryPage';
import '../styles/pages/exam-collection.css';

const deniedError=(e:unknown)=>e instanceof AuthenticationError||e instanceof ApiError&&[401,403,1900170002].includes(e.code);
function TaskDetails({task,permissions,onBack,onSummary,onReload,refreshing}:{task:ExamTaskDetail;permissions:string[];onBack:()=>void;onSummary:()=>void;onReload:()=>void;refreshing:boolean}){
  const can=(action:string)=>hasPermission(permissions,`zsjos:exam-collection:${action}`);
  const [tab,setTab]=useState('members'),[notify,setNotify]=useState(false),[transition,setTransition]=useState<'complete'|'reopen'>();
  const open=task.status==='OPEN';
  return <div className="exam-collection-detail">
    <div className="exam-collection-header"><Space orientation="vertical"><Button type="link" icon={<ArrowLeftOutlined/>} onClick={onBack}>返回任务列表</Button>
      <Space wrap><Typography.Title level={4} className="exam-collection-title">{task.title}</Typography.Title><Tag color={open?'processing':'default'}>{open?'收集中':'已完成'}</Tag></Space>
      {task.description&&<Typography.Paragraph>{task.description}</Typography.Paragraph>}
      <div className="exam-collection-stats"><span><strong>{task.collected}/{task.expected}</strong>应收学员已填写</span><span><strong>{task.additional}</strong>额外收集人员</span><Button type="link" onClick={()=>setTab('exceptions')}>{task.exceptions} 条待处理异常</Button></div>
    </Space><Space wrap>
      <Button icon={<ReloadOutlined/>} loading={refreshing} onClick={onReload}>刷新</Button>
      <Button type="primary" onClick={onSummary}>查看收集情况</Button>
      {open&&!task.classIds.length&&can('notify')&&<Button onClick={()=>setNotify(true)}>选择班级通知</Button>}
      {open&&can('complete')&&<Button disabled={refreshing} onClick={()=>setTransition('complete')}>标记已完成</Button>}
      {!open&&can('reopen')&&<Button disabled={refreshing} onClick={()=>setTransition('reopen')}>重新开启</Button>}
    </Space></div>
    <section className="exam-collection-link" aria-label="统一收集链接"><Typography.Text strong>统一收集链接</Typography.Text><Typography.Paragraph copyable={{text:task.url}}>{task.url}</Typography.Paragraph>
      <Typography.Text type="secondary">{open?'所有学员使用同一链接，重复填写会更新档案并保留历史。':'收集已结束，链接展示结束提示并停止接收新提交。'}</Typography.Text>
    </section>
    {!task.classIds.length&&<Alert type="info" showIcon title="链接已生成，尚未通知班级" description="请选择服务中的班级，固定本次应收名单并通知对应学习规划师。"/>}
    {!open&&task.completionReason&&<Alert type="info" title="任务结束说明" description={task.completionReason}/>}
    <Tabs activeKey={tab} onChange={setTab} destroyOnHidden items={[
      {key:'members',label:'应收名单',children:<ExamMembers task={task} canAdjust={open&&can('members')&&task.classIds.length>0} canResolve={can('resolve')} onChanged={onReload}/>},
      {key:'submissions',label:'填写记录',children:<ExamSubmissions taskId={task.id} canResolve={can('resolve')} onChanged={onReload}/>},
      {key:'exceptions',label:`异常处理（${task.exceptions}）`,children:<ExamSubmissions taskId={task.id} exceptionsOnly canResolve={can('resolve')} onChanged={onReload}/>},
      {key:'events',label:'操作记录',children:<ExamEvents taskId={task.id}/>},
    ]}/>
    {notify&&<ExamNotifyDialog task={task} onClose={()=>setNotify(false)} onSaved={()=>{setNotify(false);onReload()}}/>}
    {transition&&<ExamReasonDialog title={transition==='complete'?'完成考试资料收集':'重新开启考试资料收集'}
      description={transition==='complete'?`目前已填写 ${task.collected}/${task.expected} 人，另有 ${task.exceptions} 条异常。完成后停止收集并移除规划师待办，已有资料和真实填写状态保留。`:'恢复统一链接提交，并为仍有缺交学员的规划师恢复待办。'}
      required={transition==='complete'&&(task.collected<task.expected||task.exceptions>0)} onClose={()=>setTransition(undefined)}
      onConfirm={async reason=>{await examCollectionApi[transition](task.id,task.revision,reason);onReload()}}/>}
  </div>;
}
export default function ExamCollectionPage({permissions}:{permissions:string[]}){
  const [params,setParams]=useSearchParams();
  const raw=params.get('taskId');const taskId=raw&&/^[1-9][0-9]*$/.test(raw)&&Number.isSafeInteger(Number(raw))?Number(raw):undefined;
  const allowed=hasPermission(permissions,'zsjos:exam-collection:query');
  const [keyword,setKeyword]=useState(''),[status,setStatus]=useState<string>(),[page,setPage]=useState(1),[size,setSize]=useState(20),[reload,setReload]=useState(0);
  const [rows,setRows]=useState<ExamTask[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[denied,setDenied]=useState(false),[creating,setCreating]=useState(false);
  const [task,setTask]=useState<ExamTaskDetail>(),[detailLoading,setDetailLoading]=useState(false),[detailError,setDetailError]=useState('');
  const lastTask=useRef<number|undefined>(undefined);
  useEffect(()=>{let active=true;if(!allowed){setRows([]);return}setLoading(true);setError('');setDenied(false);
    void examCollectionApi.page({pageNo:page,pageSize:size,keyword,status}).then(v=>{if(active){setRows(v.list);setTotal(v.total)}})
      .catch(e=>{if(active){setRows([]);setError(studentInfoError(e));setDenied(deniedError(e))}}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[allowed,page,size,keyword,status,reload]);
  useEffect(()=>{let active=true;if(!allowed||!taskId){setTask(undefined);return}if(lastTask.current!==taskId)setTask(undefined);lastTask.current=taskId;setDetailLoading(true);setDetailError('');
    void examCollectionApi.detail(taskId).then(v=>{if(active)setTask(v)}).catch(e=>{if(active){setDetailError(studentInfoError(e));if(deniedError(e)){setTask(undefined);setDenied(true)}}}).finally(()=>{if(active)setDetailLoading(false)});return()=>{active=false}},[allowed,taskId,reload]);
  const open=(id:number,summary=false)=>setParams({taskId:String(id),...(summary?{view:'summary'}:{})});
  const columns:ProColumns<ExamTask>[]=[{title:'任务名称',dataIndex:'title',width:280,render:(_,r)=><Button type="link" onClick={()=>open(r.id)}>{r.title}</Button>},
    {title:'状态',dataIndex:'status',width:120,render:(_,r)=><Tag color={r.status==='OPEN'?'processing':'default'}>{r.status==='OPEN'?'收集中':'已完成'}</Tag>},
    {title:'收集进度',key:'coverage',width:150,render:(_,r)=>`${r.collected}/${r.expected} 人`},{title:'待处理异常',dataIndex:'exceptions',width:140,render:(_,r)=><Tag color={r.exceptions?'warning':'default'}>{r.exceptions}</Tag>},
    {title:'创建时间',dataIndex:'createdAt',width:170,render:(_,r)=>formatTimestamp(r.createdAt)},
    {title:'操作',key:'action',valueType:'option',width:230,render:(_,r)=><Space><Button type="link" onClick={()=>open(r.id)}>管理任务</Button><Button type="link" onClick={()=>open(r.id,true)}>查看收集情况</Button></Space>}];
  if(!allowed||denied)return <Result status="403" title="无权查看考试资料收集" subTitle={detailError||error}/>;
  if(raw&&!taskId)return <Result status="error" title="收集任务链接无效" extra={<Button onClick={()=>setParams({})}>返回任务列表</Button>}/>;
  if(taskId&&task&&params.get('view')==='summary')return <StudentSummaryPage key={`exam-summary:${task.id}`} permissions={permissions} examTaskId={task.id} initialClassIds={task.classIds} taskTitle={task.title} onBack={()=>open(task.id)}/>;
  if(taskId)return <section className="workspace-page">
    {detailError&&<Alert type="error" title={detailError} action={<Button onClick={()=>setReload(v=>v+1)}>重试</Button>}/>}
    {!task&&detailLoading&&<Spin/>}
    {task&&<TaskDetails key={task.id} task={task} permissions={permissions} onBack={()=>setParams({})} onSummary={()=>open(task.id,true)} onReload={()=>setReload(v=>v+1)} refreshing={detailLoading}/>}
  </section>;
  return <section className="workspace-page"><BusinessTable<ExamTask> tableKey="exam-collection" headerTitle="考试资料收集" rowKey="id" columns={columns} dataSource={rows} loading={loading} error={error} onReload={()=>setReload(v=>v+1)}
    filters={<><Input.Search aria-label="搜索考试收集任务" placeholder="搜索任务名称" allowClear onSearch={v=>{setKeyword(v);setPage(1)}}/><Select aria-label="任务状态" placeholder="全部状态" allowClear value={status} style={{minWidth:140}} options={[{value:'OPEN',label:'收集中'},{value:'COMPLETED',label:'已完成'}]} onChange={v=>{setStatus(v);setPage(1)}}/></>}
    actions={hasPermission(permissions,'zsjos:exam-collection:create')?<Button type="primary" icon={<PlusOutlined/>} onClick={()=>setCreating(true)}>发起收集任务</Button>:undefined}
    pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(s!==size?1:p);setSize(s)}}}/>
    {creating&&<ExamCreateDialog onClose={()=>setCreating(false)} onCreated={created=>{setCreating(false);setTask(created);open(created.id);setReload(v=>v+1)}}/>}
  </section>;
}
