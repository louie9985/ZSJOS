import PositioningFeedback from './PositioningFeedback'
import { Alert, App, Button, Collapse, Empty, Input, Modal, Skeleton, Space, Steps, Tag, Typography } from 'antd'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { api, type PositioningCard, type PositioningServiceOverview } from '../services/api'
import { CardSnapshot } from './AccountPositioningCard'
import PositioningEvidence from './PositioningEvidence'
import { positioningStatus } from '../services/positioningStatus'

export default function ServicePositioningCard({ serviceRelationId, canQuery, refresh, onEdit, statusContent, profileContent, interviewContent }: { serviceRelationId: number; canQuery: boolean; refresh: number; onEdit: (id?: number) => void; statusContent?: ReactNode; profileContent?: ReactNode; interviewContent?: ReactNode }) {
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
  const actionable = current && (current.canUploadEvidence || ['EDIT_POSITIONING_DRAFT', 'APPROVE_POSITIONING_FEASIBILITY', 'REJECT_POSITIONING_FEASIBILITY', 'GENERATE_POSITIONING_STUDENT_LINK', 'START_POSITIONING_REVISION'].some(key => action(key)))
  const select = (card: PositioningCard) => modal.confirm({title:'选择此卡作为持续修订的主卡？',mask:{closable:false},keyboard:false,content:'其他历史卡和草稿保留只读；选定后不再切换主卡。',okText:'确认选择',cancelText:'取消',onOk:()=>run(()=>api.positioningCard.selectMaster(serviceRelationId,card.id))})
  return <div className="student-overview-grid"><section className="lead-card student-overview-main"><div className="student-positioning-heading"><Typography.Title level={5}>定位卡</Typography.Title><Typography.Text type="secondary">当前版本正文与历史记录</Typography.Text></div>
    {!canQuery ? <Alert type="info" message="暂无定位卡查询权限" /> : loading ? <Skeleton active /> : error ? <Alert type="error" message={error} action={<Button onClick={()=>setRetry(v=>v+1)}>重试</Button>} /> : <Space orientation="vertical" style={{width:'100%'}}>
      {data?.canSelectMaster && <Alert type="warning" message="此课程服务存在多份历史定位卡，请责任编导选择持续修订的主卡" />}
      {!data?.masterCardId && data?.candidates.map(card=><div key={card.id}><Tag>{positioningStatus(card)}</Tag><PositioningFeedback card={card}><PositioningEvidence mode="files" card={card} onChanged={() => setRetry(v => v + 1)} /></PositioningFeedback><CardSnapshot reading card={card}/>{data.canSelectMaster&&<Button disabled={busy} onClick={()=>select(card)}>选择为主卡</Button>}</div>)}
      {current ? <><Tag>{positioningStatus(current)}</Tag><Typography.Paragraph type="secondary">第 {current.submissionNo || 0} 次提交 · 责任编导：{current.directorName || '未记录'} · 责任运营：{current.operatorName || '未记录'}</Typography.Paragraph>
      <Steps size="small" current={current.status === 'confirmed' ? 4 : current.status === 'student_evidence_pending' ? 3 : current.status === 'student_confirm' || current.status === 'student_link_pending' ? 2 : current.status === 'operator_feasibility' ? 1 : 0} items={[
        { title: '编导提交', description: current.submittedAt ? new Date(current.submittedAt).toLocaleString() : undefined },
        { title: '运营复核', description: current.operatorReviewedAt ? new Date(current.operatorReviewedAt).toLocaleString() : undefined },
        { title: '学员确认', description: current.studentDecidedAt ? new Date(current.studentDecidedAt).toLocaleString() : undefined },
        { title: '运营上传凭证' }, { title: '可应用到账号' }]}/>
      <PositioningFeedback card={current}><PositioningEvidence mode="files" card={current} onChanged={() => setRetry(v => v + 1)} /></PositioningFeedback><CardSnapshot reading card={current}/></> : !data?.candidates.length && <Empty description="尚未填写定位卡" />}
      {data?.effective && data.effective.submissionId !== current?.submissionId && <section><Typography.Title level={5}>最新完成确认版本</Typography.Title><Tag>{positioningStatus(data.effective)}</Tag><PositioningFeedback card={data.effective}><PositioningEvidence mode="files" card={data.effective} onChanged={() => setRetry(v => v + 1)} /></PositioningFeedback><CardSnapshot reading card={data.effective}/></section>}
      {!!data?.history.length&&<Collapse items={data.history.map(card=>({key:card.submissionId,label:`第 ${card.submissionNo} 次提交 · ${positioningStatus(card)} · ${card.submittedAt ? new Date(card.submittedAt).toLocaleString() : '提交时间未记录'}`,children:<><Tag>历史版本 · 只读</Tag><Typography.Paragraph type="secondary">{card.cardNo}</Typography.Paragraph><PositioningFeedback card={card}><PositioningEvidence mode="files" card={card} onChanged={() => setRetry(v => v + 1)} /></PositioningFeedback><CardSnapshot reading card={card}/></>}))}/>}
    </Space>}
    </section><aside className="student-overview-aside">
      <section className="lead-card student-overview-status"><Typography.Title level={5}>状态与操作</Typography.Title>{statusContent}
        <div className="student-overview-flow"><Typography.Text strong>定位卡流程</Typography.Text>
          {!canQuery ? <Typography.Text type="secondary">暂无定位卡查询权限</Typography.Text> : loading ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : error ? <Alert type="error" message={error} action={<Button onClick={() => setRetry(v => v + 1)}>重试</Button>} /> : <>
          {current ? <><Tag color="processing">{positioningStatus(current)}</Tag><dl className="student-overview-fields">
            <div><dt>当前版本</dt><dd>第 {current.submissionNo || 0} 次提交</dd></div>
            <div><dt>责任编导</dt><dd>{current.directorName || '未记录'}</dd></div>
            <div><dt>责任运营</dt><dd>{current.operatorName || '未记录'}</dd></div>
            <div><dt>提交时间</dt><dd>{current.submittedAt ? new Date(current.submittedAt).toLocaleString() : '尚未提交'}</dd></div>
          </dl><div className="student-overview-actions" aria-label="当前定位卡操作">{!actionable && <Typography.Text type="secondary">当前暂无可执行操作</Typography.Text>}
        {current.status==='co_creating'&&action('EDIT_POSITIONING_DRAFT')&&<Button type="primary" onClick={()=>onEdit(current.id)}>继续填写／提交审核</Button>}
        {action('APPROVE_POSITIONING_FEASIBILITY')&&<Button type="primary" disabled={busy} onClick={()=>modal.confirm({ title:'确认复核通过此定位卡？', width:520, content:`正在处理第 ${current.submissionNo || 0} 次提交。通过后进入学员确认环节，请确认正文、参考账号与素材均已核对。`, okText:'确认通过', cancelText:'取消', mask:{closable:false}, keyboard:false, onOk:()=>run(()=>api.positioningCard.operatorApprove(current.id,current.version)) })}>复核通过</Button>}
        {action('REJECT_POSITIONING_FEASIBILITY')&&<Button disabled={busy} onClick={()=>{setReason('');setReject(true)}}>驳回修改</Button>}
        {action('GENERATE_POSITIONING_STUDENT_LINK')&&<Button disabled={busy} onClick={()=>void run(async()=>{const result=await api.positioningCard.generateStudentLink(current.id,current.version);setLink(result.sharePath)})}>生成学员确认链接</Button>}
        {action('START_POSITIONING_REVISION')&&<Button disabled={busy} onClick={()=>modal.confirm({
          title: '确认修改定位卡？', width: 520,
          content: '确认后，当前定位卡将进入草稿状态。修改后需重新提交运营审核、学员确认并补齐确认凭证。已有历史版本和账号已应用版本保留。是否继续修改？',
          okText: '确认修改', cancelText: '取消', autoFocusButton: 'cancel', mask: { closable: false }, keyboard: false,
          onOk: () => run(async () => { await api.positioningCard.startRevision(current.id, current.version); onEdit(current.id) }),
        })}>修订定位卡</Button>}

          <PositioningEvidence mode="actions" card={current} onChanged={() => setRetry(v => v + 1)} /></div></> : <Typography.Text type="secondary">{data?.candidates.length ? '待选择主定位卡' : '尚未填写定位卡'}</Typography.Text>}
          {data?.canCreate && <Button type="primary" block onClick={() => onEdit()}>填写定位卡</Button>}
          </>}
        </div>{interviewContent}
      </section>{profileContent}
    </aside>
    <Modal width={520} title={`驳回定位卡修改 · 第 ${current?.submissionNo || 0} 次提交`} open={reject} mask={{closable:false}} keyboard={false} onCancel={()=>{if(!busy)setReject(false)}} confirmLoading={busy} onOk={()=>{if(!reason.trim()){message.error('请填写退回原因');return}void run(async()=>{await api.positioningCard.operatorReject(current!.id,current!.version,reason.trim());setReject(false)})}}><Input.TextArea value={reason} onChange={e=>setReason(e.target.value)} maxLength={500}/></Modal>
    <Modal width={520} title="学员确认链接" open={!!link} onCancel={()=>setLink('')} footer={<Button onClick={()=>void navigator.clipboard.writeText(link).then(()=>message.success('已复制链接'))}>复制链接</Button>}><Input.TextArea readOnly value={link}/></Modal>
  </div>
}
