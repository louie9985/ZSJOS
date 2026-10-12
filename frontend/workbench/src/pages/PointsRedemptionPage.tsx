import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Empty, Form, Input, Modal, Pagination, Popconfirm, Result, Select, Skeleton, Space, Typography, Upload, message } from 'antd'
import { PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import BusinessTable from '../components/BusinessTable'
import { pointsApi, pointsState, type PointsRequest, type PointsPackage, type PointsAttachment } from '../services/pointsRedemptionApi'
import { createIdempotencyKey } from '../services/idempotency'
import { formatTimestamp } from '../services/time'

export default function PointsRedemptionPage({ permissions }: { permissions: string[] }) {
  const can = (action: string) => permissions.includes('*:*:*') || permissions.includes(`zsjos:points-redemption:${action}`)
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<PointsRequest[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1)
  const [management, setManagement] = useState(false), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [detail, setDetail] = useState<PointsRequest>(), [detailError, setDetailError] = useState(''), [detailLoading, setDetailLoading] = useState(false)
  const [code, setCode] = useState(''), [busy, setBusy] = useState(false)
  const [creating, setCreating] = useState(false), [packages, setPackages] = useState<PointsPackage[]>([]), [packageError, setPackageError] = useState('')
  const [attachments, setAttachments] = useState<PointsAttachment[]>([]), [uploading, setUploading] = useState(false)
  const [form] = Form.useForm<{ packageId: number; reason: string }>()
  const submissionKey = useRef(''), detailGeneration = useRef(0), listGeneration = useRef(0)
  const id = Number(params.get('id')) || undefined
  const load = useCallback(async () => {
    const generation = ++listGeneration.current; setLoading(true); setError('')
    try { const result = await pointsApi.page(page, management); if (generation === listGeneration.current) { setRows(result.list); setTotal(result.total) } }
    catch (e) { if (generation === listGeneration.current) setError(e instanceof Error ? e.message : '申请加载失败') }
    finally { if (generation === listGeneration.current) setLoading(false) }
  }, [page, management])
  const loadDetail = useCallback(async () => {
    const generation = ++detailGeneration.current; setCode(''); setDetail(undefined); setDetailError('')
    if (!id) return
    setDetailLoading(true)
    try { const row = await pointsApi.get(id); if (generation === detailGeneration.current) setDetail(row) }
    catch (e) { if (generation === detailGeneration.current) setDetailError(e instanceof Error ? e.message : '详情加载失败') }
    finally { if (generation === detailGeneration.current) setDetailLoading(false) }
  }, [id])
  useEffect(() => { if (can('query')) void load() }, [load])
  useEffect(() => { void loadDetail(); return () => { ++detailGeneration.current } }, [loadDetail])
  useEffect(() => { if (!detail || detail.approvalStatus !== 'approved' || !['queued', 'issuing'].includes(detail.issueStatus)) return
    const timer = window.setTimeout(() => void loadDetail(), 5000); return () => window.clearTimeout(timer)
  }, [detail, loadDetail])
  const loadPackages = async () => { setPackageError(''); try { setPackages(await pointsApi.packages()) } catch (e) { setPackageError(e instanceof Error ? e.message : '套餐加载失败') } }
  const openCreate = () => { form.resetFields(); setAttachments([]); submissionKey.current = createIdempotencyKey(); setCreating(true); void loadPackages() }
  const submit = async () => {
    try { const values = await form.validateFields(); if (!attachments.length) { message.warning('请至少上传一份附件'); return }
      setBusy(true); const created = await pointsApi.create({ ...values, attachmentIds: attachments.map(a => a.id), submissionKey: submissionKey.current })
      setCreating(false); setParams({ id: String(created) }); await load(); message.success('申请已提交审批')
    } catch (e) { if (e instanceof Error) message.error(e.message) } finally { setBusy(false) }
  }
  const action = async (name: 'cancel' | 'retry' | 'refresh' | 'disable') => {
    if (!id) return; setBusy(true)
    try { await pointsApi.action(id, name); await loadDetail(); await load() } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') } finally { setBusy(false) }
  }
  const reveal = async () => {
    if (!id) return; const generation = detailGeneration.current; setBusy(true)
    try { const value = await pointsApi.reveal(id); if (generation === detailGeneration.current) setCode(value) }
    catch (e) { message.error(e instanceof Error ? e.message : '兑换码读取失败') } finally { setBusy(false) }
  }
  if (!can('query')) return <Result status="403" title="无积分兑换码查询权限" />
  return <section className="workspace-page">
    <div className="page-heading"><Typography.Title level={4}>积分兑换码</Typography.Title><Space wrap>
      {can('manage') && <Button onClick={() => { setManagement(!management); setPage(1) }}>{management ? '查看我的申请' : '管理申请'}</Button>}
      <Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button>
      {can('create') && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>申请兑换码</Button>}
    </Space></div>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {loading ? <Skeleton active /> : <BusinessTable tableKey="points-redemption" columnMode="native" rowKey="id" pagination={false} dataSource={rows}
      locale={{ emptyText: <Empty description="暂无积分兑换码申请" /> }} columns={[
        { title: '申请编号', dataIndex: 'requestNo', ellipsis: true }, { title: '套餐', dataIndex: 'packageName' },
        { title: '积分', dataIndex: 'creditsAmount' }, { title: '申请人', dataIndex: 'applicantName' },
        { title: '状态', render: (_, row: PointsRequest) => pointsState(row) },
        { title: '申请时间', render: (_, row: PointsRequest) => formatTimestamp(row.submittedAt) },
        { title: '操作', render: (_, row: PointsRequest) => <Button type="link" onClick={() => setParams({ id: String(row.id) })}>查看详情</Button> }
      ]} />}
    {total > 20 && <Pagination current={page} total={total} pageSize={20} onChange={setPage} showSizeChanger={false} />}
    <Modal open={creating} title="申请积分兑换码" onCancel={() => !busy && setCreating(false)} onOk={() => void submit()} confirmLoading={busy} okButtonProps={{ disabled: uploading || !!packageError }} destroyOnHidden>
      <Typography.Paragraph type="secondary">每次申请一个兑换码，上级及董事长审批通过后自动生成。</Typography.Paragraph>
      {packageError && <Alert type="error" message={packageError} action={<Button onClick={() => void loadPackages()}>重试</Button>} />}
      {!packages.length && !packageError && <Empty description="暂无可用套餐，请联系管理员配置" />}
      <Form form={form} layout="vertical" onValuesChange={() => { submissionKey.current = createIdempotencyKey() }}>
        <Form.Item name="packageId" label="积分套餐" rules={[{ required: true, message: '请选择套餐' }]}><Select options={packages.map(p => ({ value: p.id, label: `${p.name} · ${p.creditsAmount}积分 · ${p.validDays ? p.validDays + '天有效' : '永久有效'}` }))} /></Form.Item>
        <Form.Item name="reason" label="申请理由" rules={[{ required: true, whitespace: true, message: '请填写申请理由' }]}><Input.TextArea rows={4} maxLength={2000} showCount /></Form.Item>
        <Form.Item label="附件" required>
          <Upload showUploadList={false} accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.png,.jpg,.jpeg,.webp,.zip" disabled={uploading || attachments.length >= 10}
            beforeUpload={file => { void (async () => { setUploading(true); try { const attachment = await pointsApi.upload(file); setAttachments(old => [...old, attachment]); submissionKey.current = createIdempotencyKey() }
              catch (e) { message.error(e instanceof Error ? e.message : '上传失败') } finally { setUploading(false) } })(); return false }}>
            <Button icon={<UploadOutlined />} loading={uploading}>上传附件</Button>
          </Upload>
          <Typography.Paragraph type="secondary">至少1份，最多10份，每份不超过20MB。</Typography.Paragraph>
          {attachments.map(a => <div key={a.id}>{a.name} <Button type="link" onClick={() => { setAttachments(old => old.filter(f => f.id !== a.id)); submissionKey.current = createIdempotencyKey() }}>移除</Button></div>)}
        </Form.Item>
      </Form>
    </Modal>
    <Modal open={!!id} title="积分申请详情" footer={null} onCancel={() => { ++detailGeneration.current; setCode(''); setParams({}) }} width={720} destroyOnHidden>
      {detailLoading ? <Skeleton active /> : detailError ? <Alert type="error" message={detailError} action={<Button onClick={() => void loadDetail()}>重试</Button>} /> : detail && <>
        <Descriptions column={1} items={[
          { key: 'no', label: '申请编号', children: detail.requestNo }, { key: 'applicant', label: '申请人', children: detail.applicantName },
          { key: 'package', label: '套餐', children: `${detail.packageName} · ${detail.creditsAmount}积分` },
          { key: 'status', label: '状态', children: pointsState(detail) }, { key: 'reason', label: '申请理由', children: detail.reason },
          { key: 'validity', label: '兑换有效期', children: detail.validDays ? (detail.expiresAt ? formatTimestamp(detail.expiresAt) : `生成后${detail.validDays}天`) : '永久有效' },
          { key: 'synced', label: '状态同步时间', children: formatTimestamp(detail.syncedAt) },
          { key: 'files', label: '附件', children: detail.attachments?.map(a => <div key={a.id}><a href={a.url} target="_blank" rel="noreferrer">{a.name}</a></div>) }
        ]} />
        {detail.lastError && <Alert showIcon type="warning" message={detail.lastError} />}
        <Space wrap>
          {detail.canReveal && <Button type="primary" loading={busy} onClick={() => void reveal()}>查看兑换码</Button>}
          {detail.issueStatus === 'issued' && can('refresh') && <Button loading={busy} onClick={() => void action('refresh')}>同步兑换状态</Button>}
          {detail.approvalStatus === 'pending' && !management && can('cancel') && <Popconfirm title="取消这份申请？" onConfirm={() => action('cancel')}><Button disabled={busy}>取消申请</Button></Popconfirm>}
          {management && detail.issueStatus === 'failed' && can('retry') && <Button disabled={busy} onClick={() => void action('retry')}>重试发码</Button>}
          {management && detail.issueStatus === 'issued' && can('disable') && <Popconfirm title="作废后无法恢复，确定继续？" onConfirm={() => action('disable')}><Button danger disabled={busy}>作废兑换码</Button></Popconfirm>}
        </Space>
        {code && <Alert type="success" message="兑换码" description={<><Typography.Paragraph copyable={{ text: code }} style={{ overflowWrap: 'anywhere' }}>{code}</Typography.Paragraph><Typography.Text>请妥善保管，持有码的人可兑换一次。</Typography.Text></>} />}
      </>}
    </Modal>
  </section>
}
