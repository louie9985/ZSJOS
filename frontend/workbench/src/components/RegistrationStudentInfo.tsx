import RegistrationArchiveCard from './RegistrationArchiveCard'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Modal, Space, Tag, Typography, Empty, Spin } from 'antd'
import { LinkOutlined, CheckOutlined, FileSearchOutlined, FileAddOutlined } from '@ant-design/icons'
import StudentInfoPanel from './StudentInfoPanel'
import OverflowToolbar, { type ToolbarAction } from './OverflowToolbar'
import StudentInfoGenerateModal from './StudentInfoGenerateModal'
import StudentInfoLinkModal, { type StudentInfoLinkClient } from './StudentInfoLinkModal'
import { collectionTasksApi, collectionTaskReader, currentArchiveReader, type CollectionTask } from '../services/studentInfoTasks'
import { hasPermission } from '../services/managementAccess'
import { studentInfoError } from '../services/studentInfo'

import type { RegistrationCase } from '../services/api'

const states: Record<string,string> = { CANCELLED: '已取消', PENDING: '待处理', UNSENT: '待发送', DRAFT: '待填写', SUBMITTED: '已提交', REUSED: '已复用', EXPIRED: '链接已过期', REVOKED: '链接已撤销' }
const stateColors: Record<string,string> = {CANCELLED:'default',PENDING:'gold',UNSENT:'gold',DRAFT:'blue',SUBMITTED:'green',REUSED:'purple',EXPIRED:'orange',REVOKED:'red'}
export default function RegistrationStudentInfo({ item, permissions, disabled, onChanged, onBusy }: {
  item: RegistrationCase; permissions: string[]; disabled: boolean; onChanged: () => void; onBusy?: (busy: boolean) => void
}) {
  const { id: caseId, version } = item
  const [informationOpen, setInformationOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selected,setSelected]=useState<CollectionTask>(), [source,setSource]=useState<number>(), [busy,setBusy]=useState(false)
  const [linkMode,setLinkMode]=useState<'generate'|'view'>(), [operationError,setOperationError]=useState('')
  const [archiveOpen,setArchiveOpen]=useState(false),[archiveTask,setArchiveTask]=useState<CollectionTask>(),[archiveLoading,setArchiveLoading]=useState(false),[archiveError,setArchiveError]=useState('')
  const [archiveReady,setArchiveReady]=useState(false)
  const archiveRun=useRef(0)
  const archiveSource = archiveTask?.candidates.find(candidate => candidate.formId === source)
  const archiveReader=useMemo(()=>archiveSource?currentArchiveReader(archiveSource.formId,archiveSource.revision,setArchiveReady):undefined,[archiveSource?.formId,archiveSource?.revision])
  const viewArchive=async()=>{
    if(!selected)return
    const run=++archiveRun.current;setArchiveOpen(true);setArchiveTask(undefined);setArchiveError('');setOperationError('');setArchiveReady(false);setArchiveLoading(true)
    try{const row=await collectionTasksApi.detail(selected.id);if(run===archiveRun.current){setArchiveTask(row);setSelected(row);setSource(row.candidates.find(candidate => candidate.missing.length === 0)?.formId ?? row.candidates[0]?.formId)}}catch(e){if(run===archiveRun.current)setArchiveError(studentInfoError(e))}finally{if(run===archiveRun.current)setArchiveLoading(false)}
  }
  const closeArchive=()=>{if(busy)return;archiveRun.current++;setArchiveOpen(false);setArchiveTask(undefined)}
  const permitted = (value: string) => hasPermission(permissions, value)
  const canQuery = permitted('zsjos:registration:query-my')
  const load = async (isCurrent: () => boolean = () => true) => {
    setLoading(true)
    setError('')
    try {
      const row = await collectionTasksApi.byRegistration(caseId)
      if (isCurrent()) {
        setSelected(row)
        setSource(row.candidates.find(candidate => candidate.missing.length === 0)?.formId ?? row.candidates[0]?.formId)
      }
    } catch (error) {
      if (isCurrent()) setError(studentInfoError(error))
    } finally {
      if (isCurrent()) setLoading(false)
    }
  }
  useEffect(() => {
    let current = true
    if (canQuery && item.orderItemId) void load(() => current)
    return () => { current = false }
  }, [caseId, version, canQuery, item.orderItemId])
  useEffect(() => {
    onBusy?.(busy || Boolean(linkMode) || archiveOpen)
    return () => onBusy?.(false)
  }, [busy, linkMode, archiveOpen, onBusy])
  const command=async(operation: 'generate'|'reuse'|'sent'|'regenerate')=>{
    if(!selected || disabled || busy)return;setBusy(true);setOperationError('')
    const candidate=selected.candidates.find(c=>c.formId===source)
    try{const result=await collectionTasksApi.command(selected.id,operation,{version:selected.version,idempotencyKey:crypto.randomUUID(),sourceFormId:candidate?.formId,sourceRevision:candidate?.revision});setSelected(result);setLinkMode(undefined);if(operation==='reuse')setArchiveOpen(false);onChanged()}catch(e){setOperationError(studentInfoError(e))}finally{setBusy(false)}
  }
  const candidate=selected?.candidates.find(c=>c.formId===source)
  const linkCommand = async (id: number, operation: 'generate'|'regenerate'|'revoke', request: {version:number;idempotencyKey:string}) => {
    const row=await collectionTasksApi.command(id,operation,{...request,sourceFormId:candidate?.formId,sourceRevision:candidate?.revision})
    setSelected(row);onChanged()
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
      if(selected.formId) actions.push({key:'link',label:'链接 / 二维码',icon:<LinkOutlined/>,disabled:disabled || busy,onClick:()=>setLinkMode('view')})
      else if(permitted('zsjos:student-info-form:create')&&permitted('zsjos:student-info-form:sensitive-read')) actions.push({key:'generate',label:'生成收集表',icon:<FileAddOutlined/>,disabled:disabled || busy,onClick:()=>setLinkMode('generate')})
    }
    if(selected.canProcess&&selected.formId&&selected.status==='UNSENT'&&permitted('zsjos:student-info-form:sent')) actions.push({key:'sent',label:'确认已发送',icon:<CheckOutlined/>,disabled:disabled || busy,onClick:()=>void command('sent')})
  }
  if(selected&&permitted('zsjos:student-info-form:read')) actions.unshift({key:'archive',label:'查看已有档案',icon:<FileSearchOutlined/>,disabled:disabled || busy,onClick:()=>void viewArchive()})
  return <section aria-label="报名资料收集">
    <Space wrap><Typography.Text strong>报名资料收集</Typography.Text>
      {!item.orderItemId && permitted('zsjos:student-info-form:read') &&
        <Button onClick={() => setInformationOpen(true)}>查看学员资料</Button>}
      {selected && <Tag color={stateColors[selected.status]}>{states[selected.status] ?? '未知状态'}</Tag>}
    </Space>
    <Modal title="学员资料" open={informationOpen} width="min(1520px, calc(100vw - 32px))" onCancel={() => setInformationOpen(false)} destroyOnHidden
      styles={{ body: { maxHeight: '70dvh', overflowY: 'auto' } }}
      footer={<Button onClick={() => setInformationOpen(false)}>关闭</Button>}>
      {informationOpen && permitted('zsjos:student-info-form:read') && (item.orderItemId
        ? selected && <StudentInfoPanel key={selected.id} caseId={selected.id} reader={collectionTaskReader} showDraft/>
        : <StudentInfoPanel caseId={caseId}/>)}
    </Modal>
    {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>}/>}
    {!archiveOpen && operationError && <Alert type="error" title={operationError}/>}
    {loading && <Spin size="small"/>}
    {selected && !error && <Space orientation="vertical" style={{ width: '100%' }}>
      <OverflowToolbar actions={actions}/>
      {!selected.formId && selected.canProcess && candidate && candidate.missing.length > 0 &&
        <Alert type="warning" title={`已有档案缺少：${candidate.missing.join('、')}`} description="可生成新表，请学员补充。"/>}
      {permitted('zsjos:student-info-form:read') &&
        <Button onClick={() => setInformationOpen(true)}>查看学员资料</Button>}
    </Space>}
    {selected&&linkMode==='generate'&&<StudentInfoGenerateModal key={selected.id} task={selected} onClose={()=>setLinkMode(undefined)} onGenerated={row=>{setSelected(row);setLinkMode('view');onChanged()}}/>}
    {selected&&linkMode==='view'&&<StudentInfoLinkModal caseId={selected.id} version={selected.version} mode={linkMode} client={linkClient} onClose={()=>setLinkMode(undefined)} onChanged={onChanged}/>}
    <Modal title="查看已有档案" open={archiveOpen} onCancel={closeArchive} width={1080} destroyOnHidden footer={<Space wrap>
      {archiveError && <Button disabled={busy||archiveLoading} onClick={()=>void viewArchive()}>重试</Button>}
      {archiveSource&&archiveTask?.canProcess&&!['SUBMITTED','REUSED'].includes(archiveTask.status)&&permitted('zsjos:student-info-task:reuse')&&permitted('zsjos:student-info-form:sensitive-read')&&<Button type="primary" loading={busy} disabled={!archiveReady||archiveLoading||!!archiveError||archiveSource.missing.length>0} onClick={()=>archiveTask.formId?Modal.confirm({title:'复用已有档案',content:'将以已有档案替换本任务未提交的收集表，旧填写链接将失效，原草稿会保留为订单记录。请将新链接发给学员阅读协议并提交。',onOk:()=>command('reuse')}):void command('reuse')}>复用已有档案</Button>}
      {archiveSource&&archiveTask?.canProcess&&!archiveTask.formId&&permitted('zsjos:student-info-form:create')&&permitted('zsjos:student-info-form:sensitive-read')&&permitted('zsjos:student-info-form:link-read')&&<Button disabled={!archiveReady||busy||archiveLoading} onClick={()=>{setArchiveOpen(false);setLinkMode('generate')}}>带入已有内容生成新表</Button>}
      <Button disabled={busy} onClick={closeArchive}>关闭</Button>
    </Space>}>
      {archiveError&&<Alert type="error" title={archiveError}/>}{operationError&&<Alert type="error" title={operationError}/>}
      {archiveLoading?<Spin/>:archiveTask&&archiveSource&&archiveReader?<>
        {archiveSource.missing.length>0&&<Alert type="warning" title={`本次项目缺少：${archiveSource.missing.join('、')}`} description="不能直接复用，请生成新表带入已有内容后补充。"/>}
        <RegistrationArchiveCard description="复用后资料会预填到新表，仍需学员阅读协议并提交。"><StudentInfoPanel key={`${archiveTask.id}:${archiveSource.formId}:${archiveSource.revision}`} caseId={archiveTask.id} reader={archiveReader}/></RegistrationArchiveCard>
      </>:!archiveError&&<Empty description="该客户暂无已填写档案"/>}
    </Modal>
  </section>
}
