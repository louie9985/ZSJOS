import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Space, Spin, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import BusinessTable from './BusinessTable'
import { managementApi, type Cashback, type CashbackWithdrawal, type FinanceSource, type WithdrawalSource } from '../services/managementApi'
import { APP_ROUTES } from '../constants'
import { hasPermission } from '../services/managementAccess'
import { useFinanceFilterOptions } from '../hooks/useFinanceFilterOptions'
import { money } from '../pages/financeTableColumns'
import { formatTimestamp } from '../services/time'

export const sourceMessage = (access?: string) => access === 'not_applicable' ? '有效客资返现，无关联订单'
  : access === 'denied' ? '无权查看来源信息' : '历史来源信息缺失'

export function SourceSummary({ source, permissions }: { source?: FinanceSource; permissions: string[] }) {
  const navigate = useNavigate()
  if (!source) return <Alert type="info" title="历史来源信息缺失" />
  const orderPath = hasPermission(permissions, 'zsjos:sales-order:query-management') ? APP_ROUTES.SALES_ORDERS
    : hasPermission(permissions, 'zsjos:sales-order:query-own') ? APP_ROUTES.MY_SALES_ORDERS : APP_ROUTES.TEAM_SALES_ORDERS
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <Typography.Title level={5}>客资／订单来源</Typography.Title>
    {source.leadAccess === 'available' ? <Descriptions column={1} bordered items={[
      { key: 'lead', label: '客资编号', children: <Button type="link" onClick={() => navigate(`${APP_ROUTES.LEAD_MANAGEMENT}?leadId=${source.leadId}`)}>{source.leadNo || '历史未记录'}</Button> },
      { key: 'customer', label: '客户姓名', children: source.customerName || '未提供可见姓名' }
    ]} /> : <Alert type="info" title={`客资：${sourceMessage(source.leadAccess)}`} />}
    {source.orderAccess === 'available' ? <Descriptions column={2} bordered items={[
      { key: 'order', label: '订单号', children: <Button type="link" onClick={() => navigate(`${orderPath}?orderId=${source.orderId}`)}>{source.orderNo || '历史未记录'}</Button> },
      { key: 'student', label: '学员', children: source.studentName || '-' },
      { key: 'sales', label: '负责销售', children: source.salesName || '-' },
      { key: 'type', label: '订单类型', children: source.orderTypeLabel || '-' },
      { key: 'status', label: '订单状态', children: source.orderStatusLabel || '-' },
      { key: 'total', label: '整单总金额', children: money(source.orderTotalAmount) },
      { key: 'payable', label: '整单应付金额', children: money(source.orderPayableAmount) },
      { key: 'paidAt', label: '客户付款时间', children: formatTimestamp(source.customerPaidAt) },
      { key: 'product', label: '订单商品快照', children: [source.productName, source.skuName].filter(Boolean).join(' / ') || '历史未记录' },
      { key: 'quantity', label: '商品数量', children: source.quantity ?? '-' },
      { key: 'unit', label: '商品单价', children: money(source.unitPrice) },
      { key: 'discount', label: '商品项优惠', children: money(source.discountAmount) },
      { key: 'item', label: '对应商品项应付', children: money(source.itemPayableAmount) }
    ]} /> : <Alert type="info" title={`订单：${sourceMessage(source.orderAccess)}`} />}
  </Space>
}

export function CashbackDetail({ id, permissions, onClose }: { id?: number; permissions: string[]; onClose: () => void }) {
  const [detail, setDetail] = useState<Cashback>()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const sequence = useRef(0)
  const financeOptions = useFinanceFilterOptions('cashback', !!id)
  const load = useCallback(async () => {
    const current = ++sequence.current; setDetail(undefined); setError('')
    if (!id) return
    setLoading(true)
    try { const value = await managementApi.cashback(id); if (current === sequence.current) setDetail(value) }
    catch (e) { if (current === sequence.current) setError(e instanceof Error ? e.message : '返现详情加载失败') }
    finally { if (current === sequence.current) setLoading(false) }
  }, [id])
  useEffect(() => { void load(); return () => { sequence.current++ } }, [load])
  return <Drawer title="返现详情" open={!!id} onClose={onClose} size={860}>
    <Spin spinning={loading}>
      {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
      {detail && <Space orientation="vertical" style={{ width: '100%' }}>
        {financeOptions.error && <Alert type="error" title={financeOptions.error} action={<Button onClick={() => void financeOptions.reload()}>重试</Button>} />}
        <Descriptions column={2} bordered items={[
          { key: 'no', label: '返现编号', children: detail.cashbackNo }, { key: 'beneficiary', label: '返现受益人', children: detail.beneficiaryName || '历史归属信息缺失' },
          { key: 'partner', label: '合作方', children: detail.partnerName || '-' }, { key: 'product', label: '返现产品快照', children: detail.productNameSnapshot || '-' },
          { key: 'type', label: '类型', children: financeOptions.options('type').find(x => x.value === detail.type)?.label || '类型暂不可用' },
          { key: 'status', label: '状态', children: financeOptions.options('status').find(x => x.value === detail.status)?.label || '状态暂不可用' },
          { key: 'base', label: '返现基数', children: detail.type === 'valid' ? '固定金额返现，不适用' : money(detail.baseAmount) },
          { key: 'rate', label: '返现比例', children: detail.type === 'valid' ? '不适用' : detail.rateSnapshot == null ? '-' : `${Number((detail.rateSnapshot * 100).toFixed(8))}%` },
          { key: 'amount', label: '返现金额', children: money(detail.amount) }, { key: 'days', label: '观察期（天）', children: detail.observationDaysSnapshot ?? '-' },
          { key: 'generated', label: '生成时间', children: formatTimestamp(detail.generatedAt) }, { key: 'available', label: '可提现时间', children: formatTimestamp(detail.availableAt) },
          { key: 'settled', label: '结算时间', children: formatTimestamp(detail.settledAt) }, { key: 'cancelled', label: '取消时间', children: formatTimestamp(detail.cancelledAt) },
          { key: 'reason', label: '取消原因', children: detail.cancelReason || '-' }
        ]} />
        <SourceSummary source={detail.source} permissions={permissions} />
        <Typography.Title level={5}>提现记录</Typography.Title>
        {hasPermission(permissions, 'zsjos:withdrawal:finance-query') || hasPermission(permissions, 'zsjos:withdrawal:admin-query')
          ? <CashbackHistory id={detail.id} /> : <Alert type="info" title="无权查看提现记录" />}
      </Space>}
    </Spin>
  </Drawer>
}

function CashbackHistory({ id }: { id: number }) {
  const navigate = useNavigate()
  const [rows, setRows] = useState<CashbackWithdrawal[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1), [error, setError] = useState(''), [loading, setLoading] = useState(false)
  const sequence = useRef(0)
  const options = useFinanceFilterOptions('withdrawal', true)
  const load = useCallback(async (next: number) => {
    const current = ++sequence.current; setLoading(true); setError(''); setRows([])
    try { const data = await managementApi.cashbackWithdrawals(id, next); if (sequence.current === current) { setRows(data.list); setTotal(data.total); setPage(next) } }
    catch (e) { if (sequence.current === current) setError(e instanceof Error ? e.message : '提现记录加载失败') }
    finally { if (sequence.current === current) setLoading(false) }
  }, [id])
  useEffect(() => { void load(1); return () => { sequence.current++ } }, [load])
  return <BusinessTable<CashbackWithdrawal> mode="compact" tableKey="cashback-history" rowKey="id" dataSource={rows} error={error || options.error} loading={loading} onReload={() => { void load(page); void options.reload() }} pagination={{ current: page, pageSize: 10, total, onChange: p => void load(p) }} columns={[
    { title: '提现单号', dataIndex: 'withdrawalNo', render: (_, row) => <Button type="link" onClick={() => navigate(`${APP_ROUTES.WITHDRAWAL}?withdrawalId=${row.id}`)}>{row.withdrawalNo}</Button> },
    { title: '本次提现金额', render: (_, row) => money(row.amount) },
    { title: '状态', render: (_, row) => options.options('status').find(x => x.value === row.status)?.label || '状态暂不可用' },
    { title: '申请时间', render: (_, row) => formatTimestamp(row.submittedAt) }
  ]} />
}

export function WithdrawalSources({ id, permissions }: { id: number; permissions: string[] }) {
  const [rows, setRows] = useState<WithdrawalSource[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1), [error, setError] = useState(''), [loading, setLoading] = useState(false)
  const [cashbackId, setCashbackId] = useState<number>()
  const sequence = useRef(0)
  const options = useFinanceFilterOptions('cashback', hasPermission(permissions, 'zsjos:cashback:finance-query'))
  const load = useCallback(async (next: number) => {
    const current = ++sequence.current; setRows([]); setError(''); setLoading(true)
    try { const data = await managementApi.withdrawalSources(id, next); if (sequence.current === current) { setRows(data.list); setTotal(data.total); setPage(next) } }
    catch (e) { if (sequence.current === current) setError(e instanceof Error ? e.message : '资金来源加载失败') }
    finally { if (sequence.current === current) setLoading(false) }
  }, [id])
  useEffect(() => { void load(1); return () => { sequence.current++ } }, [load])
  return <><Typography.Title level={5}>资金来源（共 {total} 笔返现）</Typography.Title>
    <BusinessTable<WithdrawalSource> mode="compact" tableKey="withdrawal-sources" rowKey="id" dataSource={rows} loading={loading} error={error || options.error} onReload={() => { void load(page); void options.reload() }} scroll={{ x: 1100 }} pagination={{ current: page, pageSize: 10, total, onChange: p => void load(p) }} expandable={{ expandedRowRender: row => <SourceSummary source={row.cashback?.source} permissions={permissions} /> }} columns={[
      { title: '返现编号', render: (_, row) => row.cashback ? hasPermission(permissions, 'zsjos:cashback:finance-query') ? <Button type="link" onClick={() => setCashbackId(row.cashback!.id)}>{row.cashback.cashbackNo}</Button> : row.cashback.cashbackNo : '历史返现信息缺失' },
      { title: '受益人', render: (_, row) => row.cashback?.beneficiaryName || '-' },
      { title: '类型', render: (_, row) => options.options('type').find(x => x.value === row.cashback?.type)?.label || '类型暂不可用' },
      { title: '客户／学员', render: (_, row) => row.cashback?.source?.studentName || row.cashback?.source?.customerName || '-' },
      { title: '来源单据', render: (_, row) => row.cashback?.source?.orderNo || row.cashback?.source?.leadNo || sourceMessage(row.cashback?.source?.orderAccess) },
      { title: '产品', render: (_, row) => row.cashback?.productNameSnapshot || '-' },
      { title: '返现基数', render: (_, row) => row.cashback?.type === 'valid' ? '不适用' : money(row.cashback?.baseAmount) },
      { title: '返现比例', render: (_, row) => row.cashback?.type === 'valid' ? '不适用' : row.cashback?.rateSnapshot == null ? '-' : `${Number((row.cashback.rateSnapshot * 100).toFixed(8))}%` },
      { title: '本次提现金额', render: (_, row) => money(row.amount) }
    ]} />
    <CashbackDetail id={cashbackId} permissions={permissions} onClose={() => setCashbackId(undefined)} />
  </>
}
