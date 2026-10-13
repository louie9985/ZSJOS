import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Empty, Form, Input, Modal, Pagination, Popconfirm, Result, Select, Skeleton, Space, Tabs, Tag, Typography, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import BusinessTable from '../components/BusinessTable'
import SubjectAvatar from '../components/SubjectAvatar'
import PointsApprovalPanel from '../components/PointsApprovalPanel'
import { PointsAttachmentPicker, PointsAttachments, type PointsAttachmentItem } from '../components/PointsAttachments'
import { uploadDeferredFiles } from '../services/deferredUpload'
import { pointsApi, pointsState, type PointsRequest, type PointsPackage } from '../services/pointsRedemptionApi'
import { createIdempotencyKey } from '../services/idempotency'
import { formatTimestamp } from '../services/time'
import '../styles/pages/points-redemption.css'

const validity = (days?: number | null) => days ? `生成后${days}天有效` : '永久有效'
function StateTag({ row }: { row: PointsRequest }) {
  const label = pointsState(row)
  const color = label === '可兑换' ? 'success' : label === '审批中' || label.includes('生成中') ? 'processing'
    : label.includes('失败') || label === '已驳回' ? 'error' : 'default'
  return <Tag color={color}>{label}</Tag>
}

export default function PointsRedemptionPage({ permissions }: { permissions: string[] }) {
  const can = (action: string) => permissions.includes('*:*:*') || permissions.includes(`zsjos:points-redemption:${action}`)
  const canQuery = can('query'), canPackages = can('create') || can('package'), canManage = can('manage')
  const [params, setParams] = useSearchParams()
  const [tab, setTab] = useState(canPackages ? 'packages' : 'mine')
  const activeTab = tab === 'packages' && !canPackages || tab === 'manage' && !canManage ? 'mine' : tab
  const management = activeTab === 'manage'
  const [rows, setRows] = useState<PointsRequest[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [detail, setDetail] = useState<PointsRequest>(), [detailError, setDetailError] = useState(''), [detailLoading, setDetailLoading] = useState(false)
  const [code, setCode] = useState(''), [busy, setBusy] = useState(false)
  const [creating, setCreating] = useState(false), [packages, setPackages] = useState<PointsPackage[]>([]), [packageError, setPackageError] = useState('')
  const [attachments, setAttachments] = useState<PointsAttachmentItem[]>([])
  const attachmentRef = useRef<PointsAttachmentItem[]>([])
  const [packageLoading, setPackageLoading] = useState(true)
  const [form] = Form.useForm<{ packageId: number; reason: string }>()
  const selectedPackageId = Form.useWatch('packageId', form)
  const selectedPackage = packages.find(p => p.id === selectedPackageId)
  const submissionKey = useRef(''), detailGeneration = useRef(0), listGeneration = useRef(0), packageGeneration = useRef(0), submitting = useRef(false)
  const updateAttachments = (items: PointsAttachmentItem[]) => {
    attachmentRef.current.filter(item => !items.some(next => next.uid === item.uid)).forEach(item => {
      if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
    })
    attachmentRef.current = items; setAttachments(items)
  }
  useEffect(() => () => { attachmentRef.current.forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) }) }, [])
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
  useEffect(() => { if (canQuery && activeTab !== 'packages') void load(); return () => { ++listGeneration.current } }, [load, canQuery, activeTab])
  useEffect(() => { if (canQuery) void loadDetail(); return () => { ++detailGeneration.current } }, [loadDetail, canQuery])
  useEffect(() => { if (!detail || detail.approvalStatus !== 'approved' || !['queued', 'issuing'].includes(detail.issueStatus)) return
    const timer = window.setTimeout(() => void loadDetail(), 5000); return () => window.clearTimeout(timer)
  }, [detail, loadDetail])
  const loadPackages = useCallback(async () => {
    const generation = ++packageGeneration.current; setPackageLoading(true); setPackageError('')
    try { const result = await pointsApi.packages(); if (generation === packageGeneration.current) setPackages(result) }
    catch (e) { if (generation === packageGeneration.current) { setPackages([]); setPackageError(e instanceof Error ? e.message : '套餐加载失败') } }
    finally { if (generation === packageGeneration.current) setPackageLoading(false) }
  }, [])
  useEffect(() => { if (canQuery && canPackages) void loadPackages(); return () => { ++packageGeneration.current } }, [canQuery, canPackages, loadPackages])
  const openCreate = (packageId?: number) => {
    form.resetFields(); form.setFieldValue('packageId', packageId); updateAttachments([])
    submissionKey.current = createIdempotencyKey(); setCreating(true); void loadPackages()
  }
  const submit = async () => {
    if (submitting.current) return
    submitting.current = true
    try { const values = await form.validateFields(); if (!attachments.length) { message.warning('请至少上传一份附件'); return }
      if (!packages.some(p => p.id === values.packageId)) { message.warning('所选套餐已不可用，请重新选择'); return }
      setBusy(true)
      const uploaded = await uploadDeferredFiles(attachmentRef.current, pointsApi.upload, updateAttachments)
      if (uploaded.failed) { message.error('部分附件上传失败，请重新提交重试'); return }
      const created = await pointsApi.create({ ...values, attachmentIds: uploaded.items.map(a => a.uploaded!.id), submissionKey: submissionKey.current })
      setCreating(false); updateAttachments([]); setTab('mine'); setPage(1); setParams({ id: String(created) }); await load(); message.success('申请已提交审批')
    } catch (e) { if (e instanceof Error) message.error(e.message) } finally { submitting.current = false; setBusy(false) }
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
  if (!canQuery) return <Result status="403" title="无积分兑换码查询权限" />
  const requestList = <>
    {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {loading ? <Skeleton active /> : !error && <BusinessTable tableKey="points-redemption" columnMode="native" rowKey="id" pagination={false} dataSource={rows}
      locale={{ emptyText: <Empty description="暂无积分兑换码申请" /> }} columns={[
        { title: '申请编号', dataIndex: 'requestNo', ellipsis: true }, { title: '套餐', dataIndex: 'packageName' },
        { title: '积分', dataIndex: 'creditsAmount' }, { title: '申请人', dataIndex: 'applicantName' },
        { title: '状态', render: (_, row: PointsRequest) => <StateTag row={row} /> },
        { title: '申请时间', render: (_, row: PointsRequest) => formatTimestamp(row.submittedAt) },
        { title: '操作', render: (_, row: PointsRequest) => <Button type="link" onClick={() => setParams({ id: String(row.id) })}>查看详情</Button> }
      ]} />}
    {!error && total > 20 && <Pagination current={page} total={total} pageSize={20} onChange={setPage} showSizeChanger={false} />}
  </>
  return <section className="workspace-page points-redemption-page">
    <div className="page-heading"><Typography.Title level={4}>积分兑换码</Typography.Title><Space wrap>
      <Button icon={<ReloadOutlined />} onClick={() => void (activeTab === 'packages' ? loadPackages() : load())}>刷新</Button>
      {activeTab !== 'packages' && can('create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>申请兑换码</Button>}
    </Space></div>
    <Tabs activeKey={activeTab} onChange={key => { setTab(key); setPage(1); setRows([]); setTotal(0); setLoading(true); setError('') }} items={[
      ...(canPackages ? [{ key: 'packages', label: '积分套餐', children: <>
        {packageLoading ? <Skeleton active /> : packageError ? <Alert type="error" showIcon title={packageError} action={<Button onClick={() => void loadPackages()}>重试</Button>} />
          : !packages.length ? <Empty description="暂无可用套餐，请联系管理员配置" /> : <div className="points-package-grid">{packages.map(p => <article className="points-package-card" key={p.id}>
            <SubjectAvatar seed={p.id} namespace="points-package" variant="collection" version="v2" size={76} shape="rounded" label={p.name} />
            <Typography.Title level={5}>{p.name}</Typography.Title>
            <Tag className="points-amount" color="processing"><strong>{p.creditsAmount.toLocaleString()}</strong> 积分</Tag>
            <Tag className="points-validity">{validity(p.validDays)}</Tag>
            {can('create') && <Button block onClick={() => openCreate(p.id)}>申请兑换码</Button>}
          </article>)}</div>}
        <Typography.Paragraph type="secondary" className="points-package-hint">每次申请一个兑换码，审批通过后自动生成。</Typography.Paragraph>
      </> }] : []),
      { key: 'mine', label: '我的申请', children: activeTab === 'mine' ? requestList : null },
      ...(canManage ? [{ key: 'manage', label: '申请管理', children: activeTab === 'manage' ? requestList : null }] : [])
    ]} />
    <Modal open={creating} title="申请积分兑换码" width={620} className="points-dialog" onCancel={() => { if (!busy) { setCreating(false); updateAttachments([]) } }} onOk={() => void submit()} okText="提交申请" cancelText="取消" confirmLoading={busy} okButtonProps={{ disabled: packageLoading || !!packageError || !packages.length }} cancelButtonProps={{ disabled: busy }} closable={!busy} maskClosable={!busy} keyboard={!busy} destroyOnHidden>
      <Typography.Paragraph type="secondary">每次申请一个兑换码，上级及董事长审批通过后自动生成。</Typography.Paragraph>
      {packageError && <Alert type="error" message={packageError} action={<Button onClick={() => void loadPackages()}>重试</Button>} />}
      {!packages.length && !packageError && !packageLoading && <Empty description="暂无可用套餐，请联系管理员配置" />}
      {selectedPackage && <div className="points-package-summary"><SubjectAvatar seed={selectedPackage.id} namespace="points-package" variant="collection" version="v2" size={52} shape="rounded" /><div><Typography.Text strong>{selectedPackage.name}</Typography.Text><Space wrap><Tag color="processing">{selectedPackage.creditsAmount.toLocaleString()} 积分</Tag><Tag>{validity(selectedPackage.validDays)}</Tag></Space></div></div>}
      <Form form={form} layout="vertical" disabled={busy} onValuesChange={() => { submissionKey.current = createIdempotencyKey() }}>
        <Form.Item name="packageId" label="积分套餐" rules={[{ required: true, message: '请选择套餐' }]}><Select options={packages.map(p => ({ value: p.id, label: `${p.name} · ${p.creditsAmount}积分 · ${p.validDays ? p.validDays + '天有效' : '永久有效'}` }))} /></Form.Item>
        <Form.Item name="reason" label="申请理由" rules={[{ required: true, whitespace: true, message: '请填写申请理由' }]}><Input.TextArea rows={4} maxLength={2000} showCount /></Form.Item>
        <Form.Item label="附件" required>
          <PointsAttachmentPicker value={attachments} disabled={busy} onChange={items => { updateAttachments(items); submissionKey.current = createIdempotencyKey() }} />
          <Typography.Paragraph type="secondary">至少1份，最多10份，每份不超过20MB。</Typography.Paragraph>
        </Form.Item>
      </Form>
    </Modal>
    <Modal open={!!id} title="积分申请详情" className="points-dialog" footer={null} onCancel={() => { ++detailGeneration.current; setCode(''); setParams({}) }} width={720} destroyOnHidden>
      {detailLoading ? <Skeleton active /> : detailError ? <Alert type="error" message={detailError} action={<Button onClick={() => void loadDetail()}>重试</Button>} /> : detail && <>
        <div className="points-package-summary"><div><Typography.Text strong>{detail.packageName}</Typography.Text><Space wrap><Tag color="processing">{detail.creditsAmount.toLocaleString()} 积分</Tag><StateTag row={detail} /></Space></div></div>
        <Descriptions column={{ xs: 1, sm: 2 }} items={[
          { key: 'no', label: '申请编号', children: detail.requestNo }, { key: 'applicant', label: '申请人', children: detail.applicantName },
          { key: 'submitted', label: '申请时间', children: formatTimestamp(detail.submittedAt) }
        ]} />
        <Typography.Title level={5}>申请理由</Typography.Title><div className="points-reason">{detail.reason}</div>
        <Typography.Title level={5}>申请附件</Typography.Title>
        {detail.attachments?.length ? <PointsAttachments requestId={detail.id} files={detail.attachments} /> : <Typography.Text type="secondary">暂无附件</Typography.Text>}
        <PointsApprovalPanel key={detail.id} id={detail.id} canUrge={can('urge')} />
        <Typography.Title level={5}>兑换信息</Typography.Title>
        <Descriptions column={{ xs: 1, sm: 2 }} items={[
          { key: 'validity', label: '兑换有效期', children: detail.validDays ? (detail.expiresAt ? formatTimestamp(detail.expiresAt) : `生成后${detail.validDays}天`) : '永久有效' },
          { key: 'synced', label: '状态同步时间', children: formatTimestamp(detail.syncedAt) },
          { key: 'redeemed', label: '兑换时间', children: formatTimestamp(detail.redeemedAt) }
        ]} />
        {detail.lastError && <Alert showIcon type="warning" message={detail.lastError} />}
        <Space wrap className="points-detail-actions">
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
