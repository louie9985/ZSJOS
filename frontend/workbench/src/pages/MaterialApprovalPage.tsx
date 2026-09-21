import BusinessTable from '../components/BusinessTable'
import { Alert, App, Button, Empty, Form, Image, Input, Modal, Result, Skeleton, Space, Spin, Tabs, Tag, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import DateTimeText from '../components/DateTimeText'
import { MaterialFields } from './MaterialLibraryPage'
import ResourceLink, { ResourceLinkPresentation } from '../components/ResourceLink'
import { materialApprovalApi, type MaterialApproval } from '../services/materialApprovalApi'

const errorText = (error: unknown) => error instanceof Error ? error.message : '加载失败，请重试'
const APPROVAL_PAGE_SIZE_PER_TYPE = 20
const APPROVAL_PAGE_SIZE = 40
export default function MaterialApprovalPage({ permissions }: { permissions: string[] }) {
  const allowed = permissions.includes('zsjos:material-approval:query')
  const [params, setParams] = useSearchParams()
  const done = params.get('done') === 'true'
  const [types, setTypes] = useState<Array<{code: string; name: string}>>([])
  const typeCode = params.get('typeCode') || types[0]?.code
  const [rows, setRows] = useState<MaterialApproval[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [typesError, setTypesError] = useState('')
  const [typesLoading, setTypesLoading] = useState(false)
  const [detail, setDetail] = useState<MaterialApproval>()
  const [detailError, setDetailError] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)
  const [action, setAction] = useState<'approve' | 'reject'>()
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{reason: string}>()
  const { message } = App.useApp()
  const listRequest = useRef(0)
  const detailRequest = useRef(0)
  const taskId = params.get('taskId')
  const versionId = Number(params.get('versionId'))
  const loadTypes = useCallback(async () => {
    setTypesError(''); setTypesLoading(true)
    try { setTypes(await materialApprovalApi.types()) } catch (e) { setTypesError(errorText(e)) } finally { setTypesLoading(false) }
  }, [])
  useEffect(() => { if (allowed) void loadTypes() }, [allowed, loadTypes])
  const load = useCallback(async () => {
    if (!allowed || !types.length) return
    const request = ++listRequest.current
    setLoading(true); setError(''); setRows([])
    try {
      const results = await Promise.all(types.map(type => materialApprovalApi.page({typeCode: type.code, done, pageNo: page, pageSize: APPROVAL_PAGE_SIZE_PER_TYPE})))
      const merged = results.flatMap((result, index) => result.list.map(item => ({ ...item, typeCode: types[index].code })))
        .sort((a, b) => String((done ? b.task.endTime : b.task.createTime) || '').localeCompare(String((done ? a.task.endTime : a.task.createTime) || '')))
      if (request === listRequest.current) { setRows(merged); setTotal(results.reduce((sum, result) => sum + result.total, 0)) }
    } catch (e) { if (request === listRequest.current) setError(errorText(e)) }
    finally { if (request === listRequest.current) setLoading(false) }
  }, [allowed, done, page, types])
  useEffect(() => { void load(); return () => { listRequest.current++ } }, [load])
  const loadDetail = useCallback(async () => {
    const request = ++detailRequest.current
    setDetail(undefined); setDetailError(''); setAction(undefined)
    if (!allowed || !taskId || !versionId) { setDetailLoading(false); return }
    setDetailLoading(true)
    try {
      const result = await materialApprovalApi.get(versionId, taskId, done)
      if (request === detailRequest.current) setDetail(result)
    } catch (e) { if (request === detailRequest.current) setDetailError(errorText(e)) }
    finally { if (request === detailRequest.current) setDetailLoading(false) }
  }, [allowed, taskId, versionId, done])
  useEffect(() => { void loadDetail(); return () => { detailRequest.current++ } }, [loadDetail])
  const close = () => { const next = new URLSearchParams(params); next.delete('taskId'); next.delete('versionId'); setParams(next); setAction(undefined) }
  const decide = async () => {
    if (!detail || !action) return
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    setSaving(true)
    try {
      await materialApprovalApi.decide(action, detail.versionId, detail.task.id, values.reason.trim())
      message.success(action === 'approve' ? '审批已通过' : '已驳回'); close(); void load()
    } catch (e) { message.error(errorText(e)); setAction(undefined); void loadDetail(); void load() }
    finally { setSaving(false) }
  }
  if (!allowed) return <Result status="403" title="无权访问素材审批" />
  return <section className="workspace-page material-approval-page">
    <Typography.Title level={4}>素材审批</Typography.Title>
    {typesError && <Alert type="error" showIcon title={typesError} action={<Button onClick={() => void loadTypes()}>重试</Button>}/>}
    <Space wrap className="material-approval-toolbar"><Typography.Text type="secondary">爆款账号审批与爆款内容审批</Typography.Text><Button onClick={() => void load()} loading={loading}>刷新</Button></Space>
    <Tabs activeKey={done ? 'done' : 'todo'} items={[{key:'todo',label:'待我审批'},{key:'done',label:'我已审批'}]}
      onChange={value => {setPage(1); setParams({...(typeCode ? {typeCode} : {}),done:String(value === 'done')})}}/>
    {error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试</Button>}/> :
      <BusinessTable<MaterialApproval> tableKey="material-approval-page-1" columnMode="native" rowKey={r => r.task.id} dataSource={rows} loading={loading} scroll={{x:640}}
        locale={{emptyText:<Empty description={done ? '暂无已审批素材' : '暂无待审批素材'}/>}}
        pagination={{current:page,total,pageSize:APPROVAL_PAGE_SIZE,showSizeChanger:false,onChange:setPage}}
        columns={[{title:'审批类型',render:(_,r) => <Tag color={r.typeCode === 'viral_content' ? 'blue' : 'gold'}>{types.find(type => type.code === r.typeCode)?.name || r.typeCode}</Tag>},{title:'素材编号',dataIndex:'materialNo'},{title:'拆解标题',dataIndex:'title'},
          {title:done ? '处理时间' : '到达时间',render:(_,r) => <DateTimeText value={done ? r.task.endTime : r.task.createTime}/>},
          { key: 'action',title:'操作',render:(_,r) => <Button type="link" onClick={() => {const next=new URLSearchParams(params); next.set('taskId',r.task.id);next.set('versionId',String(r.versionId));if (r.typeCode) next.set('typeCode',r.typeCode);setParams(next)}}>{done ? '查看记录' : '审批'}</Button>}]}/>}
    <Modal className="material-approval-detail-modal" open={Boolean(taskId)} title={detail ? <Space><span>{detail.title}</span><Tag>{types.find(type => type.code === (detail.typeCode || typeCode))?.name || detail.typeCode || typeCode}</Tag></Space> : '素材审批详情'} width="min(1280px, calc(100vw - 32px))" footer={null} onCancel={() => !saving && close()}>
      {detailLoading ? <Skeleton active paragraph={{rows: 12}}/> : detailError ? <Alert type="error" showIcon title={detailError} action={<Button onClick={() => void loadDetail()}>重试</Button>}/> : detail?.snapshot && <ResourceLinkPresentation.Provider value={true}><div className="material-approval-detail">
        <aside className="material-approval-visual">
          <Typography.Title level={5}>{(detail.typeCode || typeCode) === 'viral_content' ? '封面图' : '账号主截图'}</Typography.Title>
          {detail.snapshot.coverPreviewUrl ? <Image src={detail.snapshot.coverPreviewUrl} alt={(detail.typeCode || typeCode) === 'viral_content' ? '封面图' : '账号主截图'} className="material-approval-cover"/> : <Typography.Text type="secondary">暂无主截图</Typography.Text>}
          <div className="material-approval-links">{detail.snapshot.fields.filter(field => field.type === 'https-link' && detail.snapshot?.values[field.key]).map(field => <ResourceLink key={field.key} href={String(detail.snapshot?.values[field.key])} title={field.label} variant="resource"/>)}</div>
        </aside>
        <main className="material-approval-content"><div className="material-approval-meta"><Typography.Text>{detail.materialNo}</Typography.Text><Typography.Text type="secondary">提交版本 V{detail.snapshot.versionNo}</Typography.Text>{done && <span className="material-approval-record"><strong>本次处理记录</strong><span>{detail.task.reason || '未记录审批意见'}</span><DateTimeText value={detail.task.endTime}/></span>}</div>
        {detail.snapshot.summary && <Typography.Paragraph>{detail.snapshot.summary}</Typography.Paragraph>}
        <MaterialFields version={detail.snapshot}/>
        {!done && <Space wrap className="material-approval-actions">
          {permissions.includes('zsjos:material-approval:approve') && <Button type="primary" onClick={() => {form.resetFields();setAction('approve')}}>通过</Button>}
          {permissions.includes('zsjos:material-approval:reject') && <Button danger onClick={() => {form.resetFields();setAction('reject')}}>驳回</Button>}
        </Space>}</main>
      </div></ResourceLinkPresentation.Provider>}
    </Modal>
    <Modal open={Boolean(action)} title={action === 'approve' ? '通过素材审批' : '驳回素材'} confirmLoading={saving}
      onCancel={() => !saving && setAction(undefined)} onOk={() => void decide()}>
      <Form form={form} layout="vertical"><Form.Item name="reason" label="审批意见" rules={[{required:true,whitespace:true,message:'请填写审批意见'},{max:1000}]}><Input.TextArea maxLength={1000} rows={4}/></Form.Item></Form>
    </Modal>
  </section>
}
