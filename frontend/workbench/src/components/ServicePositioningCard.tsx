import { Alert, App, Button, Collapse, Empty, Input, Modal, Skeleton, Space, Tag, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { api, type PositioningCard, type PositioningServiceOverview } from '../services/api'
import { CardSnapshot } from './AccountPositioningCard'
const labels: Record<string, string> = { co_creating: '草稿', operator_feasibility: '待运营审核', student_link_pending: '待生成学员确认链接', student_confirm: '待学员确认', confirmed: '已确认', change_requested: '学员提出修改', superseded: '历史已确认', student_agreed: '历史已确认' }
export default function ServicePositioningCard({ serviceRelationId, canQuery, refresh, onEdit }: { serviceRelationId: number; canQuery: boolean; refresh: number; onEdit: (id?: number) => void }) {
  const { modal, message } = App.useApp()
  const [data, setData] = useState<PositioningServiceOverview>(), [error, setError] = useState(''), [loading, setLoading] = useState(true), [retry, setRetry] = useState(0), [busy, setBusy] = useState(false)
  const [reject, setReject] = useState(false), [reason, setReason] = useState(''), [link, setLink] = useState('')
  useEffect(() => {
    let active = true; setData(undefined); setError(''); setLoading(canQuery)
    if (canQuery) api.positioningCard.serviceOverview(serviceRelationId).then(value => { if(active)setData(value) }).catch(cause => { if(active)setError(cause.message) }).finally(() => {if(active)setLoading(false)})
    return () => {active=false}
  }, [serviceRelationId, canQuery, refresh, retry])
  const lock = useRef(false)
  const run = async (action: () => Promise<unknown>) => { if(lock.current)return; lock.current=true; setBusy(true); try {await action();setRetry(v=>v+1)} catch(cause){message.error(cause instanceof Error?cause.message:'操作失败')}finally{lock.current=false;setBusy(false)} }
  const current = data?.current
  const action = (key: string) => current?.availableActions.includes(key)
  const select = (card: PositioningCard) => modal.confirm({title:'选择此卡作为持续修订的主卡？',mask:{closable:false},keyboard:false,content:'其他历史卡和草稿保留只读；选定后不再切换主卡。',okText:'确认选择',cancelText:'取消',onOk:()=>run(()=>api.positioningCard.selectMaster(serviceRelationId,card.id))})
  return <section className="lead-card"><Typography.Title level={5}>定位卡</Typography.Title>
    {!canQuery ? <Alert type="info" message="暂无定位卡查询权限" /> : loading ? <Skeleton active /> : error ? <Alert type="error" message={error} action={<Button onClick={()=>setRetry(v=>v+1)}>重试</Button>} /> : <Space orientation="vertical" style={{width:'100%'}}>
      {data?.canSelectMaster && <Alert type="warning" message="此课程服务存在多份历史定位卡，请责任编导选择持续修订的主卡" />}
      {!data?.masterCardId && data?.candidates.map(card=><div key={card.id}><Tag>{labels[card.status]||card.status}</Tag><CardSnapshot card={card}/>{data.canSelectMaster&&<Button disabled={busy} onClick={()=>select(card)}>选择为主卡</Button>}</div>)}
      {current ? <><Tag>{labels[current.status]||current.status}</Tag><CardSnapshot card={current}/>{(current.operatorReviewComment||current.studentDecisionComment)&&<Alert type="info" message={current.operatorReviewComment||current.studentDecisionComment}/>}<Space wrap>
        {current.status==='co_creating'&&action('EDIT_POSITIONING_DRAFT')&&<Button onClick={()=>onEdit(current.id)}>继续填写／提交审核</Button>}
        {action('APPROVE_POSITIONING_FEASIBILITY')&&<Button disabled={busy} onClick={()=>void run(()=>api.positioningCard.operatorApprove(current.id,current.version))}>审核通过</Button>}
        {action('REJECT_POSITIONING_FEASIBILITY')&&<Button disabled={busy} onClick={()=>{setReason('');setReject(true)}}>退回修改</Button>}
        {action('GENERATE_POSITIONING_STUDENT_LINK')&&<Button disabled={busy} onClick={()=>void run(async()=>{const result=await api.positioningCard.generateStudentLink(current.id,current.version);setLink(result.sharePath)})}>生成学员确认链接</Button>}
        {action('START_POSITIONING_REVISION')&&<Button disabled={busy} onClick={()=>void run(async()=>{await api.positioningCard.startRevision(current.id,current.version);onEdit(current.id)})}>修订定位卡</Button>}
      </Space></> : !data?.candidates.length && <Empty description="尚未填写定位卡" />}
      {data?.canCreate&&<Button type="primary" onClick={()=>onEdit()}>填写定位卡</Button>}
      {data?.effective&&<Collapse items={[{key:'effective',label:'最新已确认版本',children:<CardSnapshot card={data.effective}/>}]}/>}
      {!!data?.history.length&&<Collapse items={data.history.map(card=>({key:card.submissionId,label:`${card.cardNo} · 第 ${card.submissionNo} 次提交 · ${labels[card.status]||card.status}`,children:<CardSnapshot card={card}/>}))}/>}
    </Space>}
    <Modal title="退回定位卡修改" open={reject} mask={{closable:false}} keyboard={false} onCancel={()=>{if(!busy)setReject(false)}} confirmLoading={busy} onOk={()=>{if(!reason.trim()){message.error('请填写退回原因');return}void run(async()=>{await api.positioningCard.operatorReject(current!.id,current!.version,reason.trim());setReject(false)})}}><Input.TextArea value={reason} onChange={e=>setReason(e.target.value)} maxLength={500}/></Modal>
    <Modal title="学员确认链接" open={!!link} onCancel={()=>setLink('')} footer={<Button onClick={()=>void navigator.clipboard.writeText(link).then(()=>message.success('已复制链接'))}>复制链接</Button>}><Input.TextArea readOnly value={link}/></Modal>
  </section>
}
