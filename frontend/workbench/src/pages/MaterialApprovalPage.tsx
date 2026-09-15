import { Alert, App, Button, Drawer, Empty, Form, Image, Input, Modal, Result, Select, Space, Spin, Table, Tabs, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import DateTimeText from '../components/DateTimeText'
import { MaterialFields } from './MaterialLibraryPage'
import { materialApprovalApi, type MaterialApproval } from '../services/materialApprovalApi'

const errorText = (error: unknown) => error instanceof Error ? error.message : '加载失败，请重试'
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
    if (!allowed || !typeCode) return
    const request = ++listRequest.current
    setLoading(true); setError(''); setRows([])
    try {
      const result = await materialApprovalApi.page({typeCode, done, pageNo: page, pageSize: 20})
      if (request === listRequest.current) { setRows(result.list); setTotal(result.total) }
    } catch (e) { if (request === listRequest.current) setError(errorText(e)) }
    finally { if (request === listRequest.current) setLoading(false) }
  }, [allowed, typeCode, done, page])
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
  return <section className="workspace-page">
    <Typography.Title level={4}>素材审批</Typography.Title>
    {typesError && <Alert type="error" showIcon title={typesError} action={<Button onClick={() => void loadTypes()}>重试</Button>}/>}
    <Space wrap>
      <Select aria-label="素材类型" value={typeCode} loading={typesLoading} options={types.map(t => ({value:t.code,label:t.name}))}
        onChange={value => {setPage(1); setParams({typeCode:value,done:String(done)})}} />
      <Button onClick={() => void load()} loading={loading}>刷新</Button>
    </Space>
    <Tabs activeKey={done ? 'done' : 'todo'} items={[{key:'todo',label:'待我审批'},{key:'done',label:'我已审批'}]}
      onChange={value => {setPage(1); setParams({...(typeCode ? {typeCode} : {}),done:String(value === 'done')})}}/>
    {error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试</Button>}/> :
      <Table<MaterialApproval> rowKey={r => r.task.id} dataSource={rows} loading={loading} scroll={{x:640}}
        locale={{emptyText:<Empty description={done ? '暂无已审批素材' : '暂无待审批素材'}/>}}
        pagination={{current:page,total,pageSize:20,showSizeChanger:false,onChange:setPage}}
        columns={[{title:'素材编号',dataIndex:'materialNo'},{title:'拆解标题',dataIndex:'title'},
          {title:done ? '处理时间' : '到达时间',render:(_,r) => <DateTimeText value={done ? r.task.endTime : r.task.createTime}/>},
          {title:'操作',render:(_,r) => <Button type="link" onClick={() => {const next=new URLSearchParams(params); next.set('taskId',r.task.id);next.set('versionId',String(r.versionId));setParams(next)}}>{done ? '查看记录' : '审批'}</Button>}]}/>}
    <Drawer open={Boolean(taskId)} title="素材审批详情" width="min(960px, 100vw)" onClose={() => !saving && close()}>
      {detailLoading ? <Spin/> : detailError ? <Alert type="error" showIcon title={detailError} action={<Button onClick={() => void loadDetail()}>重试</Button>}/> : detail?.snapshot && <Space direction="vertical" size="large" style={{width:'100%'}}>
        <Typography.Title level={4}>{detail.title}</Typography.Title>
        <Typography.Text>{detail.materialNo} · 提交版本 V{detail.snapshot.versionNo}</Typography.Text>
        {detail.snapshot.coverPreviewUrl && <Image src={detail.snapshot.coverPreviewUrl} alt="素材封面" width={160}/>}
        {detail.snapshot.summary && <Typography.Paragraph>{detail.snapshot.summary}</Typography.Paragraph>}
        <MaterialFields version={detail.snapshot}/>
        {done ? <Alert type="info" title="本次处理记录" description={<>{detail.task.reason || '未记录审批意见'} · <DateTimeText value={detail.task.endTime}/></>}/> : <Space wrap>
          {permissions.includes('zsjos:material-approval:approve') && <Button type="primary" onClick={() => {form.resetFields();setAction('approve')}}>通过</Button>}
          {permissions.includes('zsjos:material-approval:reject') && <Button danger onClick={() => {form.resetFields();setAction('reject')}}>驳回</Button>}
        </Space>}
      </Space>}
    </Drawer>
    <Modal open={Boolean(action)} title={action === 'approve' ? '通过素材审批' : '驳回素材'} confirmLoading={saving}
      onCancel={() => !saving && setAction(undefined)} onOk={() => void decide()}>
      <Form form={form} layout="vertical"><Form.Item name="reason" label="审批意见" rules={[{required:true,whitespace:true,message:'请填写审批意见'},{max:1000}]}><Input.TextArea maxLength={1000} rows={4}/></Form.Item></Form>
    </Modal>
  </section>
}
