import { NameAvatar } from '../components/LeadDetailOverview'
import RegistrationDetailLayout from '../components/RegistrationDetailLayout'
import ProductSpecs from '../components/ProductSpecs'
import { APP_ROUTES } from '../constants'
import InboxLoadMore from '../components/InboxLoadMore'
import { useInboxPageMerge } from '../services/inboxAppend'
import RegistrationArchiveCard from '../components/RegistrationArchiveCard'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Input, Modal, Select, Space, Tag, Typography, Tabs, Empty, Spin, Result } from 'antd'
import { LinkOutlined, CheckOutlined, FileSearchOutlined, FileAddOutlined, ArrowRightOutlined } from '@ant-design/icons'
import { useInboxTableLayout } from '../services/inboxLayout'
import BusinessTable from '../components/BusinessTable'
import StudentInfoPanel from '../components/StudentInfoPanel'
import { useSearchParams, useNavigate } from 'react-router-dom'
import OverflowToolbar, { type ToolbarAction } from '../components/OverflowToolbar'
import StudentInfoLinkModal, { type StudentInfoLinkClient } from '../components/StudentInfoLinkModal'
import '../styles/pages/student-info-tasks.css'
import { collectionTasksApi, collectionTaskReader, currentArchiveReader, type CollectionTask } from '../services/studentInfoTasks'
import { hasPermission } from '../services/managementAccess'
import { studentInfoError } from '../services/studentInfo'

const states: Record<string,string> = { CANCELLED: '已取消', PENDING: '待处理', UNSENT: '待发送', DRAFT: '待填写', SUBMITTED: '已提交', REUSED: '已复用', EXPIRED: '链接已过期', REVOKED: '链接已撤销' }
const stateColors: Record<string,string> = {CANCELLED:'default',PENDING:'gold',UNSENT:'gold',DRAFT:'blue',SUBMITTED:'green',REUSED:'purple',EXPIRED:'orange',REVOKED:'red'}
const orders: Record<string,string> = { pending_approval: '审核中', effective: '已通过', terminated: '已终止', revision_required: '待补正', superseded: '已接续重提' }
const archives: Record<string,string> = { NONE: '无已有资料', COMPLETE: '可直接复用', INCOMPLETE: '已有资料但不完整' }
const archiveColors: Record<string,string> = { NONE: 'blue', COMPLETE: 'green', INCOMPLETE: 'orange' }
const archiveTag = (status: string) => <Tag color={archiveColors[status] ?? 'default'}>{archives[status] ?? '未知状态'}</Tag>
export default function StudentInfoTasksPage({ permissions }: { permissions: string[] }) {
  const { useTableLayout, isDesktop } = useInboxTableLayout()
  const [status,setStatus]=useState<string>()
  const [orderStatus,setOrderStatus]=useState<string>()
  const [searchParams,setSearchParams]=useSearchParams()
  const navigate=useNavigate()
  const rawTask=searchParams.get('taskId'),rawCase=searchParams.get('registrationCaseId')
  const [targetError,setTargetError]=useState(''),[targetLoading,setTargetLoading]=useState(false),[targetReload,setTargetReload]=useState(0)
  const targetRun=useRef(0)
  const rawPerson=searchParams.get('personId')
  const personId=rawPerson&&/^[1-9][0-9]*$/.test(rawPerson)?Number(rawPerson):undefined
  const [rows,setRows]=useState<CollectionTask[]>([]), [total,setTotal]=useState(0), [page,setPage]=useState(1)
  const [keyword,setKeyword]=useState(''), [loading,setLoading]=useState(false), [error,setError]=useState('')
  const [selected,setSelected]=useState<CollectionTask>(), [source,setSource]=useState<number>(), [busy,setBusy]=useState(false)
  const [linkMode,setLinkMode]=useState<'generate'|'view'>(), [operationError,setOperationError]=useState('')
  const [archiveOpen,setArchiveOpen]=useState(false),[archiveTask,setArchiveTask]=useState<CollectionTask>(),[archiveLoading,setArchiveLoading]=useState(false),[archiveError,setArchiveError]=useState('')
  const [archiveReady,setArchiveReady]=useState(false)
  const archiveRun=useRef(0)
  const archiveSource=archiveTask?.candidates[0]
  const archiveReader=useMemo(()=>archiveSource?currentArchiveReader(archiveSource.formId,archiveSource.revision,setArchiveReady):undefined,[archiveSource?.formId,archiveSource?.revision])
  const viewArchive=async()=>{
    if(!selected)return
    const run=++archiveRun.current;setArchiveOpen(true);setArchiveTask(undefined);setArchiveError('');setOperationError('');setArchiveReady(false);setArchiveLoading(true)
    try{const row=await collectionTasksApi.detail(selected.id);if(run===archiveRun.current){setArchiveTask(row);setSelected(row);setSource(row.candidates[0]?.formId)}}catch(e){if(run===archiveRun.current)setArchiveError(studentInfoError(e))}finally{if(run===archiveRun.current)setArchiveLoading(false)}
  }
  const closeArchive=()=>{if(busy)return;archiveRun.current++;setArchiveOpen(false);setArchiveTask(undefined)}
  const generation=useRef(0),failedPage=useRef(1)
  const permitted=(value: string)=>hasPermission(permissions,value)
  const allowed=permitted('zsjos:student-info-task:query') && permitted('zsjos:registration:query-pool')
  const mergeRows=useInboxPageMerge<CollectionTask>(JSON.stringify([keyword,personId,status,orderStatus]),!useTableLayout,row=>row.id)
  const load=async(targetPage=useTableLayout?page:1)=>{ if(!allowed)return;failedPage.current=targetPage; const current=++generation.current; setLoading(true);setError('');try{const result=await collectionTasksApi.page(targetPage,keyword,personId,status,orderStatus);if(current===generation.current){setRows(mergeRows(targetPage,result.list));setTotal(result.total);setPage(targetPage)}}catch(e){if(current===generation.current)setError(studentInfoError(e))}finally{if(current===generation.current)setLoading(false)}}
  const filterScope=JSON.stringify([keyword,personId,status,orderStatus,allowed,useTableLayout])
  const previousFilterScope=useRef(filterScope)
  useEffect(()=>{
    const changed=previousFilterScope.current!==filterScope
    previousFilterScope.current=filterScope
    if(changed){setRows([]);setTotal(0);setPage(1)}
    void load(changed?1:undefined)
    return()=>{generation.current++}
  },[useTableLayout?page:0,filterScope])
  const open=(row: CollectionTask)=>{if(busy)return;setSelected(row);setSource(row.candidates.find(c=>c.missing.length===0)?.formId??row.candidates[0]?.formId);setOperationError('');setLinkMode(undefined)}
  useEffect(()=>{
    const run=++targetRun.current
    setTargetError('');setTargetLoading(false)
    if(!rawTask&&!rawCase)return
    setSelected(undefined);setSource(undefined);setLinkMode(undefined);setArchiveOpen(false);setArchiveTask(undefined);archiveRun.current++
    if(!allowed)return
    const raw=rawTask||rawCase||''
    if((rawTask&&rawCase)||!/^[1-9][0-9]*$/.test(raw)||!Number.isSafeInteger(Number(raw))){setTargetError('收集任务跳转参数无效');return}
    setTargetLoading(true)
    const request=rawTask?collectionTasksApi.detail(Number(raw)):collectionTasksApi.byRegistration(Number(raw))
    void request.then(row=>{if(run===targetRun.current){setSelected(row);setSource(row.candidates[0]?.formId);setLinkMode(undefined);setOperationError('')}})
      .catch(error=>{if(run===targetRun.current)setTargetError(studentInfoError(error))})
      .finally(()=>{if(run===targetRun.current)setTargetLoading(false)})
    return()=>{targetRun.current++}
  },[rawTask,rawCase,allowed,targetReload])
  const displayRows=selected&&(rawTask||rawCase)&&!rows.some(row=>row.id===selected.id)?[selected,...rows]:rows
  const command=async(operation: 'generate'|'reuse'|'sent'|'regenerate')=>{
    if(!selected)return;setBusy(true);setOperationError('')
    const candidate=selected.candidates.find(c=>c.formId===source)
    try{const result=await collectionTasksApi.command(selected.id,operation,{version:selected.version,idempotencyKey:crypto.randomUUID(),sourceFormId:candidate?.formId,sourceRevision:candidate?.revision});setSelected(result);setLinkMode(undefined);if(operation==='reuse')setArchiveOpen(false);void load()}catch(e){setOperationError(studentInfoError(e))}finally{setBusy(false)}
  }
  const candidate=selected?.candidates.find(c=>c.formId===source)
  const linkCommand = async (id: number, operation: 'generate'|'regenerate'|'revoke', request: {version:number;idempotencyKey:string}) => {
    const row=await collectionTasksApi.command(id,operation,{...request,sourceFormId:candidate?.formId,sourceRevision:candidate?.revision})
    setSelected(row);void load()
    const result=await collectionTasksApi.link(id)
    if(!result)throw new Error('收集表链接暂不可用，请重试')
    return result
  }
  const linkClient: StudentInfoLinkClient = {
    link: collectionTasksApi.link,
    generate: (id,request)=>linkCommand(id,'generate',request),
    regenerate: (id,request)=>linkCommand(id,'regenerate',request),
    revoke: (id,request)=>linkCommand(id,'revoke',request),
  }
  const actions: ToolbarAction[] = []
  if(selected) {
    if(selected.canProcess && permitted('zsjos:student-info-form:link-read')) {
      if(selected.formId) actions.push({key:'link',label:'链接 / 二维码',icon:<LinkOutlined/>,disabled:busy,onClick:()=>setLinkMode('view')})
      else if(permitted('zsjos:student-info-form:create')&&permitted('zsjos:student-info-form:sensitive-read')) actions.push({key:'generate',label:candidate?'带入资料并生成收集表':'生成收集表',icon:<FileAddOutlined/>,disabled:busy,onClick:()=>setLinkMode('generate')})
    }
    if(selected.canProcess&&selected.formId&&selected.status==='UNSENT'&&permitted('zsjos:student-info-form:sent')) actions.push({key:'sent',label:'确认已发送',icon:<CheckOutlined/>,disabled:busy,onClick:()=>void command('sent')})
  }
  if(selected&&permitted('zsjos:student-info-form:read')) actions.unshift({key:'archive',label:'查看已有档案',icon:<FileSearchOutlined/>,disabled:busy,onClick:()=>void viewArchive()})
  if(selected&&permitted('zsjos:registration:query-pool')) actions.push({key:'registration',label:selected.registrationCaseId?'前往报名履约任务':'报名履约任务尚未生成',icon:<ArrowRightOutlined/>,disabled:busy||!selected.registrationCaseId,onClick:()=>navigate(`${APP_ROUTES.REGISTRATION_POOL}?registrationCaseId=${selected.registrationCaseId}`)})
  const detailContent = selected&&<RegistrationDetailLayout
    hero={<><div><Typography.Title level={4}>{selected.studentName || '未填写学员姓名'}</Typography.Title><Typography.Text type="secondary">{selected.orderNo}</Typography.Text><div><Typography.Text strong>{selected.productName || '历史产品信息缺失'}</Typography.Text><ProductSpecs product={selected}/></div></div><Tag color={stateColors[selected.status]}>{states[selected.status]??'未知状态'}</Tag></>}
    information={<section className="registration-info-section"><Typography.Title level={5}>订单与学员</Typography.Title><dl className="registration-facts">
      <div><dt>订单号</dt><dd>{selected.orderNo}</dd></div><div><dt>学员</dt><dd>{selected.studentName || '未填写'}</dd></div><div><dt>购买课程</dt><dd>{selected.productName || '历史产品信息缺失'}<ProductSpecs product={selected}/></dd></div><div><dt>订单状态</dt><dd>{orders[selected.orderStatus]??'未知状态'}</dd></div><div><dt>已有档案</dt><dd>{archiveTag(selected.archiveStatus)}</dd></div>
    </dl></section>}
    actions={<>{operationError&&<Alert type="error" title={operationError}/>}<OverflowToolbar actions={actions}/></>}
  >
    <section className="registration-step">
      <div className="registration-step-heading"><span className="registration-step-number">1</span><Typography.Title level={5}>准备与发送资料</Typography.Title><Tag color={stateColors[selected.status]}>{states[selected.status]??'未知状态'}</Tag></div>
      <Space orientation="vertical" style={{width:'100%'}}>
        <Typography.Text type="secondary">{archiveTag(selected.archiveStatus)}生成收集表后，通过链接或二维码发送给学员。</Typography.Text>
        {!selected.registrationCaseId&&<Typography.Text type="secondary">当前还没有对应的报名履约任务。</Typography.Text>}
        {!selected.canProcess&&<Alert type="info" title="此任务已停止内部办理，已发出的链接仍可在有效期内填写和提交。"/>}
        {!selected.formId&&selected.canProcess&&candidate&&candidate.missing.length>0&&<Alert type="warning" title={`需补充：${candidate.missing.join('、')}`} description="生成新表后会带入已有内容，请发送给学员补充。"/>}
      </Space>
    </section>
    <section className="registration-step">
      <div className="registration-step-heading"><span className="registration-step-number">2</span><Typography.Title level={5}>查看填写与提交情况</Typography.Title></div>
      {permitted('zsjos:student-info-form:read')?<StudentInfoPanel key={`${selected.id}:${selected.version}`} caseId={selected.id} reader={collectionTaskReader} showDraft/>:<Empty description="暂无报名资料查看权限"/>}
    </section>
  </RegistrationDetailLayout>
  return <section className="workspace-page sales-order-inbox-page registration-page student-info-tasks-page">
    {targetError&&<Alert type="error" title={targetError} action={<Button onClick={()=>setTargetReload(value=>value+1)}>重试定位</Button>}/>}
    {targetLoading&&<div role="status"><Space><Spin/>正在定位收集任务</Space></div>}
    {personId&&<Alert type="info" title="当前显示该客户关联的收集任务" action={<Button type="link" size="small" onClick={()=>{setSearchParams({});setPage(1)}}>查看全部</Button>}/>}
    <div className="collection-filter-toolbar">
      <Select aria-label="订单状态筛选" disabled={busy} placeholder="全部订单状态" allowClear style={{minWidth:150}} value={orderStatus} onChange={value=>{setOrderStatus(value);setPage(1);setSelected(undefined)}} options={Object.entries(orders).map(([value,label])=>({value,label}))}/>
      <Input.Search aria-label="搜索订单号或学员" placeholder="订单号或学员姓名" allowClear onSearch={value=>{setPage(1);setKeyword(value);setSelected(undefined)}} style={{maxWidth:320}}/>
    </div>
    <Tabs activeKey={status||'ALL'} onChange={key=>{setStatus(key==='ALL'?undefined:key);setPage(1);setSelected(undefined)}} items={[{key:'ALL',label:'全部'},...['PENDING','UNSENT','DRAFT','SUBMITTED','REUSED','CANCELLED'].map(key=>({key,label:states[key],disabled:busy}))]}/>
    {!allowed?<Result status="403" title="暂无报名资料收集权限"/>:useTableLayout?<div className="collection-table-area"><BusinessTable<CollectionTask> tableKey="student-info-tasks" rowKey="id" headerTitle={false} options={{reload:false}} loading={loading} dataSource={rows} error={error} unauthorized={!allowed}
      onReload={load} search={false} scroll={{x:1100}} pagination={{current:page,pageSize:20,total,onChange:setPage}}

      columns={[
        {title:'订单号',dataIndex:'orderNo'}, {title:'学员',dataIndex:'studentName'}, {title:'购买项目',dataIndex:'productName'},
        {title:'订单状态',dataIndex:'orderStatus',render:(_,row)=><Tag>{orders[row.orderStatus]??'未知状态'}</Tag>},
        {title:'已有档案',dataIndex:'archiveStatus',render:(_,row)=>archiveTag(row.archiveStatus)},
        {title:'收集状态',dataIndex:'status',render:(_,row)=>states[row.status]??'未知状态'},
        {title:'操作',valueType:'option',render:(_,row)=><Button type="link" onClick={()=>open(row)}>{row.canProcess?'处理资料':'查看状态'}</Button>},
      ]}/></div>:<div className="lead-inbox-layout">
      <aside className="lead-inbox-list-pane">
        {error&&<Alert type="error" title={error} action={<Button onClick={()=>void load()}>重试</Button>}/>}
        <Spin spinning={loading} wrapperClassName="collection-list-loading"><div className="lead-inbox-scroll">{displayRows.map(row=><button type="button" key={row.id} className={`lead-inbox-item${selected?.id===row.id?' active':''}`} onClick={()=>open(row)} aria-label={`查看收集任务 ${row.orderNo}`}><div className="lead-inbox-item-main"><NameAvatar name={row.studentName || '学员'} size={36}/><div className="lead-inbox-item-copy"><div className="lead-inbox-item-title"><strong>{row.studentName}</strong><Tag color={stateColors[row.status]}>{states[row.status]}</Tag></div><span>{row.productName || '历史产品信息缺失'}</span><ProductSpecs product={row}/><span>{row.orderNo}{(rawTask||rawCase)&&selected?.id===row.id?' · 定位任务':''}</span><span>{orders[row.orderStatus]??'未知状态'} · {archiveTag(row.archiveStatus)}</span></div></div></button>)}{!loading&&!error&&!displayRows.length&&<Empty description="暂无匹配的收集任务"/>}</div></Spin>
        <InboxLoadMore page={page} total={total} pageSize={20} loading={loading} error={error} rootSelector=".lead-inbox-scroll" onMore={()=>void load(page+1)} onRetry={()=>void load(failedPage.current)}/>
      </aside>
      {isDesktop&&<main className="lead-inbox-detail-pane registration-detail-pane">{detailContent||<Empty description="选择左侧任务查看填写情况"/>}</main>}
    </div>}
    <Modal width="min(1440px, calc(100vw - 32px))" className="registration-page collection-detail-modal" title="报名资料收集" open={Boolean(selected)&&(useTableLayout||!isDesktop)} onCancel={()=>{if(!busy){setSelected(undefined);setLinkMode(undefined)}}} footer={<Button disabled={busy} onClick={()=>{setSelected(undefined);setLinkMode(undefined)}}>关闭</Button>}>
      {detailContent}
    </Modal>
    {selected&&linkMode&&<StudentInfoLinkModal caseId={selected.id} version={selected.version} mode={linkMode} client={linkClient} onClose={()=>setLinkMode(undefined)} onChanged={()=>void load()}/>}
    <Modal title="查看已有档案" open={archiveOpen} onCancel={closeArchive} width={1080} destroyOnHidden footer={<Space wrap>
      <Button disabled={busy||archiveLoading} onClick={()=>void viewArchive()}>刷新档案</Button>
      {archiveSource&&archiveTask?.canProcess&&!['SUBMITTED','REUSED'].includes(archiveTask.status)&&permitted('zsjos:student-info-task:reuse')&&permitted('zsjos:student-info-form:sensitive-read')&&<Button type="primary" loading={busy} disabled={!archiveReady||archiveLoading||!!archiveError||archiveSource.missing.length>0} onClick={()=>archiveTask.formId?Modal.confirm({title:'复用已有档案',content:'将以已有档案替换本任务未提交的收集表，旧填写链接将失效，原草稿会保留为订单记录。',onOk:()=>command('reuse')}):void command('reuse')}>复用已有档案</Button>}
      {archiveSource&&archiveTask?.canProcess&&!archiveTask.formId&&permitted('zsjos:student-info-form:create')&&permitted('zsjos:student-info-form:sensitive-read')&&permitted('zsjos:student-info-form:link-read')&&<Button disabled={!archiveReady||busy||archiveLoading} onClick={()=>{setArchiveOpen(false);setLinkMode('generate')}}>带入已有内容生成新表</Button>}
      <Button disabled={busy} onClick={closeArchive}>关闭</Button>
    </Space>}>
      {archiveError&&<Alert type="error" title={archiveError}/>}{operationError&&<Alert type="error" title={operationError}/>}
      {archiveLoading?<Spin/>:archiveTask&&archiveSource&&archiveReader?<>
        {archiveSource.missing.length>0&&<Alert type="warning" title={`本次项目缺少：${archiveSource.missing.join('、')}`} description="不能直接复用，请生成新表带入已有内容后补充。"/>}
        <RegistrationArchiveCard description="客户当前唯一可复用档案；新正式提交后更新"><StudentInfoPanel key={`${archiveTask.id}:${archiveSource.formId}:${archiveSource.revision}`} caseId={archiveTask.id} reader={archiveReader}/></RegistrationArchiveCard>
      </>:!archiveError&&<Empty description="该客户暂无已填写档案"/>}
    </Modal>
  </section>
}
