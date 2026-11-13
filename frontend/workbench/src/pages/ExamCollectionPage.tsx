import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Drawer, Empty, Input, Progress, Result, Segmented, Select, Space, Spin, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { useSearchParams } from 'react-router-dom';
import { useInboxTableLayout } from '../services/inboxLayout';
import InboxLoadMore from '../components/InboxLoadMore';
import ExamCollectionShare from '../components/examCollection/ExamCollectionShare';
import ExamExportDrawer from '../components/examCollection/ExamExportDrawer';
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

const statusOptions=[{value:'OPEN',label:'收集中'},{value:'COMPLETED',label:'已完成'}];
const deniedError=(e:unknown)=>e instanceof AuthenticationError||e instanceof ApiError&&[401,403,1900170002].includes(e.code);
function TaskDetails({task,permissions,onSummary,onReload,refreshing}:{task:ExamTaskDetail;permissions:string[];onSummary:()=>void;onReload:()=>void;refreshing:boolean}){
  const can=(action:string)=>hasPermission(permissions,`zsjos:exam-collection:${action}`);
  const [exporting,setExporting]=useState(false);
  const [tab,setTab]=useState('members'),[notify,setNotify]=useState(false),[transition,setTransition]=useState<'complete'|'reopen'>();
  const open=task.status==='OPEN';
  return <div className="exam-collection-detail">
    <header className="business-inbox-detail-hero exam-collection-hero">
      <div className="exam-collection-hero-copy">
        <div className="exam-collection-hero-title"><Typography.Title level={4} className="exam-collection-title">{task.title}</Typography.Title><Tag color={open?'processing':'default'}>{open?'收集中':'已完成'}</Tag></div>
        <div className="exam-collection-stats">
          <span><strong>{task.collected}/{task.expected}</strong>应收学员已填写</span>
          <span><strong>{task.additional}</strong>额外收集人员</span>
          {task.exceptions>0?<Typography.Link onClick={()=>setTab('exceptions')}><strong className="exam-collection-stat-warning">{task.exceptions}</strong>条待处理异常</Typography.Link>:<span><strong>0</strong>条待处理异常</span>}
        </div>
        {!open&&task.completionReason&&<Typography.Text type="secondary" className="exam-collection-reason">结束说明：{task.completionReason}</Typography.Text>}
      </div>
      <Space wrap className="business-inbox-detail-actions">
        <Button type="primary" onClick={onSummary}>查看收集情况</Button>
        {can('query')&&<Button onClick={()=>setExporting(true)}>导出资料</Button>}
        {open&&!task.classIds.length&&can('notify')&&<Button onClick={()=>setNotify(true)}>选择班级通知</Button>}
        {open&&task.classIds.length>0&&can('members')&&<Button disabled={refreshing} onClick={()=>setNotify(true)}>调整收集班级</Button>}
        {open&&can('complete')&&<Button disabled={refreshing} onClick={()=>setTransition('complete')}>标记已完成</Button>}
        {!open&&can('reopen')&&<Button disabled={refreshing} onClick={()=>setTransition('reopen')}>重新开启</Button>}
      </Space>
    </header>
    <ExamCollectionShare key={task.url} url={task.url} open={open}/>
    {exporting&&<ExamExportDrawer task={task} onClose={()=>setExporting(false)}/>}
    {!task.classIds.length&&<Alert type="info" showIcon title="链接已生成，尚未通知班级" description="请选择服务中的班级，固定本次应收名单并通知对应学习规划师。"/>}
    <Tabs className="business-inbox-card exam-collection-tabs" activeKey={tab} onChange={setTab} destroyOnHidden items={[
      {key:'members',label:'应收名单',children:<ExamMembers task={task} canAdjust={open&&can('members')&&task.classIds.length>0} canResolve={can('resolve')} onChanged={onReload}/>},
      {key:'submissions',label:'填写记录',children:<ExamSubmissions taskId={task.id} canResolve={can('resolve')} onChanged={onReload}/>},
      {key:'exceptions',label:`异常处理（${task.exceptions}）`,children:<ExamSubmissions taskId={task.id} exceptionsOnly canResolve={can('resolve')} onChanged={onReload}/>},
      {key:'events',label:'操作记录',children:<ExamEvents taskId={task.id}/>},
    ]}/>
    {notify&&<ExamNotifyDialog task={task} adjusting={task.classIds.length>0} onClose={()=>setNotify(false)} onSaved={()=>{setNotify(false);onReload()}}/>}
    {transition&&<ExamReasonDialog title={transition==='complete'?'完成考试资料收集':'重新开启考试资料收集'}
      description={transition==='complete'?`目前已填写 ${task.collected}/${task.expected} 人，另有 ${task.exceptions} 条异常。完成后停止收集并移除规划师待办，已有资料和真实填写状态保留。`:'恢复统一链接提交，并为仍有缺交学员的规划师恢复待办。'}
      required={transition==='complete'&&(task.collected<task.expected||task.exceptions>0)} onClose={()=>setTransition(undefined)}
      onConfirm={async reason=>{await examCollectionApi[transition](task.id,task.revision,reason);onReload()}}/>}
  </div>;
}
export default function ExamCollectionPage({permissions}:{permissions:string[]}){
  const [params,setParams]=useSearchParams();
  const {useTableLayout,isDesktop}=useInboxTableLayout();
  const raw=params.get('taskId');const taskId=raw&&/^[1-9][0-9]*$/.test(raw)&&Number.isSafeInteger(Number(raw))?Number(raw):undefined;
  const allowed=hasPermission(permissions,'zsjos:exam-collection:query');
  const [keyword,setKeyword]=useState(''),[status,setStatus]=useState<string>(),[page,setPage]=useState(1),[size,setSize]=useState(20),[reload,setReload]=useState(0);
  const [rows,setRows]=useState<ExamTask[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[denied,setDenied]=useState(false),[creating,setCreating]=useState(false);
  const [task,setTask]=useState<ExamTaskDetail>(),[detailLoading,setDetailLoading]=useState(false),[detailError,setDetailError]=useState('');
  const lastTask=useRef<number|undefined>(undefined);
  const listReload = useRef(reload);
  useEffect(() => {
    let active = true;
    if (!allowed) { setRows([]); return; }
    setLoading(true);
    setError('');
    setDenied(false);
    // A detail mutation can change any loaded page, including filtered membership.
    const refreshLoaded = !useTableLayout && listReload.current !== reload;
    const pages = refreshLoaded ? Array.from({ length: page }, (_, index) => index + 1) : [page];
    void Promise.all(pages.map(pageNo => examCollectionApi.page({ pageNo, pageSize: size, keyword, status })))
      .then(results => {
        if (!active) return;
        const incoming = results.flatMap(result => result.list);
        listReload.current = reload;
        setRows(previous => !useTableLayout && page > 1 && !refreshLoaded
          ? [...previous.filter(row => !incoming.some(next => next.id === row.id)), ...incoming]
          : incoming);
        setTotal(results[0].total);
      })
      .catch(e => { if (active) { setError(studentInfoError(e)); setDenied(deniedError(e)); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [allowed, page, size, keyword, status, reload, useTableLayout]);
  useEffect(()=>{let active=true;if(!allowed||!taskId){setTask(undefined);return}if(lastTask.current!==taskId)setTask(undefined);lastTask.current=taskId;setDetailLoading(true);setDetailError('');
    void examCollectionApi.detail(taskId).then(v=>{if(active)setTask(v)}).catch(e=>{if(active){setDetailError(studentInfoError(e));if(deniedError(e)){setTask(undefined);setDenied(true)}}}).finally(()=>{if(active)setDetailLoading(false)});return()=>{active=false}},[allowed,taskId,reload]);
  useEffect(()=>{setPage(1);setRows([])},[useTableLayout]);
  const open=(id:number,summary=false)=>setParams({taskId:String(id),...(summary?{view:'summary'}:{})});
  const columns:ProColumns<ExamTask>[]=[{title:'任务名称',dataIndex:'title',width:280,render:(_,r)=><Button type="link" onClick={()=>open(r.id)}>{r.title}</Button>},
    {title:'状态',dataIndex:'status',width:120,render:(_,r)=><Tag color={r.status==='OPEN'?'processing':'default'}>{r.status==='OPEN'?'收集中':'已完成'}</Tag>},
    {title:'收集进度',key:'coverage',width:150,render:(_,r)=>`${r.collected}/${r.expected} 人`},{title:'待处理异常',dataIndex:'exceptions',width:140,render:(_,r)=><Tag color={r.exceptions?'warning':'default'}>{r.exceptions}</Tag>},
    {title:'创建时间',dataIndex:'createdAt',width:170,render:(_,r)=>formatTimestamp(r.createdAt)},
    {title:'操作',key:'action',valueType:'option',width:230,render:(_,r)=><Space><Button type="link" onClick={()=>open(r.id)}>管理任务</Button><Button type="link" onClick={()=>open(r.id,true)}>查看收集情况</Button></Space>}];
  if(!allowed||denied)return <Result status="403" title="无权查看考试资料收集" subTitle={detailError||error}/>;
  if(raw&&!taskId)return <Result status="error" title="收集任务链接无效" extra={<Button onClick={()=>setParams({})}>返回任务列表</Button>}/>;
  if(taskId&&task&&params.get('view')==='summary')return <StudentSummaryPage key={`exam-summary:${task.id}`} permissions={permissions} examTaskId={task.id} initialClassIds={task.classIds} taskTitle={task.title} onBack={()=>open(task.id)}/>;
  const detail=<>
    {detailError&&<Alert type="error" title={detailError} action={<Button onClick={()=>setReload(v=>v+1)}>重试</Button>}/>}
    {!task&&detailLoading&&<Spin/>}
    {task&&<TaskDetails key={task.id} task={task} permissions={permissions} onSummary={()=>open(task.id,true)} onReload={()=>setReload(v=>v+1)} refreshing={detailLoading}/>}
  </>;
  const filters=<div className="exam-collection-filters"><Input.Search aria-label="搜索考试收集任务" placeholder="搜索任务名称" allowClear onSearch={v=>{setKeyword(v);setPage(1)}}/><Select aria-label="任务状态" placeholder="全部状态" allowClear value={status} options={statusOptions} onChange={v=>{setStatus(v);setPage(1)}}/></div>;
  const createAction=hasPermission(permissions,'zsjos:exam-collection:create')?<Button type="primary" icon={<PlusOutlined/>} onClick={()=>setCreating(true)}>{useTableLayout?'发起收集任务':'发起收集'}</Button>:undefined;
  return <section className="workspace-page business-inbox-page exam-collection-page">{useTableLayout?<BusinessTable<ExamTask> tableKey="exam-collection" options={{reload:false}} headerTitle="考试资料收集" rowKey="id" columns={columns} dataSource={rows} loading={loading} error={error} onReload={()=>setReload(v=>v+1)}
    filters={filters} actions={createAction}
    pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(s!==size?1:p);setSize(s)}}}/>:<>
    <div className="business-inbox-layout">
      <aside className="business-inbox-list-pane" aria-label="收集任务列表">
        <div className="exam-collection-list-toolbar">
          <div className="business-inbox-toolbar"><Input.Search aria-label="搜索考试收集任务" placeholder="搜索任务名称" allowClear onSearch={v=>{setKeyword(v);setPage(1)}}/>{createAction}</div>
          <Segmented block aria-label="任务状态" value={status??'ALL'} options={[{value:'ALL',label:'全部'},...statusOptions]} onChange={v=>{setStatus(v==='ALL'?undefined:String(v));setPage(1)}}/>
        </div>
        {error&&<Alert type="error" title={error} action={<Button onClick={()=>setReload(v=>v+1)}>重试</Button>}/>}
        <div className="business-inbox-scroll exam-collection-task-list">
          {!loading&&!error&&!rows.length&&<Empty description="暂无匹配的收集任务"/>}
          {rows.map(row=><button key={row.id} className={`business-inbox-item${taskId===row.id?' active':''}`} aria-pressed={taskId===row.id} onClick={()=>open(row.id)}>
            <div className="business-inbox-item-copy">
              <div className="business-inbox-item-title"><strong title={row.title}>{row.title}</strong><Tag color={row.status==='OPEN'?'processing':'default'}>{row.status==='OPEN'?'收集中':'已完成'}</Tag></div>
              <div className="exam-collection-item-progress"><Progress size="small" showInfo={false} percent={row.expected?Math.round(row.collected*100/row.expected):0} status={row.status==='OPEN'?'active':'normal'}/><span>{row.collected}/{row.expected} 人</span></div>
            </div>
            <div className="business-inbox-item-meta"><span>{formatTimestamp(row.createdAt)}</span>{row.exceptions>0&&<Tag color="warning">异常 {row.exceptions}</Tag>}</div>
          </button>)}
        </div>
        <InboxLoadMore page={page} pageSize={size} total={total} loading={loading} error={error} rootSelector=".exam-collection-task-list" onMore={()=>setPage(v=>v+1)} onRetry={()=>setReload(v=>v+1)}/>
      </aside>
      {isDesktop&&<div className="business-inbox-detail-pane exam-collection-detail-pane">{taskId?detail:<Empty description="选择任务查看收集进度与名单"/>}</div>}
    </div>
  </>}
    <Drawer title="考试资料收集任务" open={Boolean(taskId&&(useTableLayout||!isDesktop))} onClose={()=>setParams({})} size={isDesktop?'large':'default'} destroyOnHidden>{detail}</Drawer>
    {creating&&<ExamCreateDialog onClose={()=>setCreating(false)} onCreated={created=>{setCreating(false);setTask(created);open(created.id);setReload(v=>v+1)}}/>}
  </section>;
}
