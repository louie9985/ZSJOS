import { useCallback, useEffect, useRef, useState, useMemo } from 'react'
import { Alert, Button, Card, Col, DatePicker, Modal, Row, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsClosing } from '../../services/fms'
import type { FmsClosingOverview } from '../../services/fms/types'
import { formatMoney } from '../../services/fms/format'
import FmsClosingSchemes from '../../components/fms/FmsClosingSchemes'

const EMPTY_OVERVIEW: FmsClosingOverview = {
  month: '', closed: false, voucherReviewRequired: false, pendingVoucherCount: 0,
  voucherCount: 0, profitLossBalance: 0, balanceSheetDifference: 0,
  initialBalanceBalanced: false, voucherNumberContinuous: false,
  profitLossVoucherGenerated: false, incomeStatementBalanced: false,
  incomeStatementUnmappedSubjectCount: 0, balanceSheetProfitLossTransferred: false,
  balanceSheetBalanced: false, balanceSheetUnmappedSubjectCount: 0, canClose: false
}

export default function FmsClosingPeriodPage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth: providerCurrentMonth, writable, reloadCurrentMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'))
  const [currentMonth, setCurrentMonth] = useState(dayjs().format('YYYY-MM'))
  const [overview, setOverview] = useState<FmsClosingOverview>(EMPTY_OVERVIEW)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const version = useRef(0)

  const monthLabel = useMemo(() => month.replace(/^(\d{4})-(\d{2})$/, '$1年$2月'), [month])
  const currentMonthLabel = useMemo(() => currentMonth.replace(/^(\d{4})-(\d{2})$/, '$1年$2月'), [currentMonth])
  const isCurrentPeriod = month === currentMonth
  const isBeforeCurrentPeriod = dayjs(`${month}-01`).isBefore(dayjs(`${currentMonth}-01`), 'month')

  const balanceSheetCheckLabel = useMemo(() => {
    if (!overview.balanceSheetProfitLossTransferred) return '损益未结转'
    if (overview.balanceSheetUnmappedSubjectCount > 0) return `${overview.balanceSheetUnmappedSubjectCount} 个科目未纳入公式`
    return overview.balanceSheetBalanced ? '检查通过' : '不平衡'
  }, [overview])

  const getOverview = useCallback(async (targetMonth: string) => {
    if (!accountSetId) return
    const v = ++version.current
    setLoading(true)
    try {
      const result = await fmsClosing.period.getOverview({ accountSetId, month: targetMonth })
      if (v !== version.current) return
      setOverview(result)
    } catch (e) {
      if (v !== version.current) return
      message.error(e instanceof Error ? e.message : '获取结账概况失败')
    } finally {
      if (v === version.current) setLoading(false)
    }
  }, [accountSetId])

  useEffect(() => {
    if (!accountSetId || !providerCurrentMonth) {
      setOverview(EMPTY_OVERVIEW)
      return
    }
    setCurrentMonth(providerCurrentMonth)
    setMonth(providerCurrentMonth)
    void getOverview(providerCurrentMonth)
  }, [accountSetId, providerCurrentMonth, getOverview])

  const handleClose = useCallback(async () => {
    if (!accountSetId || isBeforeCurrentPeriod) return
    Modal.confirm({
      title: '确认结账',
      content: isCurrentPeriod
        ? `结账后将锁定 ${monthLabel}，是否继续？`
        : `将按期间顺序结账至 ${monthLabel}，是否继续？`,
      onOk: async () => {
        setSubmitting(true)
        try {
          await fmsClosing.period.close({ accountSetId, month })
          message.success('结账成功')
          const newMonth = await reloadCurrentMonth()
          if (newMonth) { setCurrentMonth(newMonth); setMonth(newMonth) }
          await getOverview(newMonth || month)
        } catch (e) {
          message.error(e instanceof Error ? e.message : '结账失败')
        } finally {
          setSubmitting(false)
        }
      }
    })
  }, [accountSetId, month, monthLabel, isCurrentPeriod, isBeforeCurrentPeriod, getOverview, reloadCurrentMonth])

  const handleCancel = useCallback(async () => {
    if (!accountSetId || !overview.closed) return
    Modal.confirm({
      title: '确认反结账',
      content: `反结账会影响历史报表数据，将撤销 ${monthLabel} 及之后的结账，确认继续吗？`,
      onOk: async () => {
        setSubmitting(true)
        try {
          await fmsClosing.period.cancel({ accountSetId, month })
          message.success('反结账成功')
          const newMonth = await reloadCurrentMonth()
          if (newMonth) { setCurrentMonth(newMonth); setMonth(newMonth) }
          await getOverview(newMonth || month)
        } catch (e) {
          message.error(e instanceof Error ? e.message : '反结账失败')
        } finally {
          setSubmitting(false)
        }
      }
    })
  }, [accountSetId, month, monthLabel, overview.closed, getOverview, reloadCurrentMonth])

  const canCloseAction = permissions.includes('fms:closing:close')
  const canCancelAction = permissions.includes('fms:closing:cancel')

  return (
    <section className="workspace-page fms-page">
      {/* 会计期间选择 */}
      <div className="fms-search-area">
        <Space>
          <span>会计期间</span>
          <DatePicker
            picker="month"
            value={dayjs(month)}
            onChange={d => { if (d) { const m = d.format('YYYY-MM'); setMonth(m); getOverview(m) } }}
            allowClear={false}
            format="YYYY年MM月"
          />
          <Button icon={<ReloadOutlined/>} loading={loading} onClick={() => getOverview(month)}>刷新</Button>
        </Space>
      </div>

      {/* 结账检查 */}
      <div className="fms-table-area" style={{ marginBlockEnd: 'var(--crm-sp-3)' }}>
        <Alert
          message={overview.closed ? `${monthLabel} 已结账` : `${monthLabel} 尚未结账`}
          type={overview.closed ? 'success' : 'info'}
          showIcon
          style={{ marginBlockEnd: 16 }}
        />
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="凭证审核" value={`${overview.pendingVoucherCount} 张待审核`}
              ok={!overview.voucherReviewRequired || overview.pendingVoucherCount === 0}
              label={overview.voucherReviewRequired ? '结账前必须审核' : '当前未强制审核'}/>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="初始余额" value={overview.initialBalanceBalanced ? '试算平衡' : '试算不平衡'}
              ok={overview.initialBalanceBalanced} label={overview.initialBalanceBalanced ? '检查通过' : '需要处理'}/>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="凭证编号" value={overview.voucherNumberContinuous ? '编号连续' : '存在断号'}
              ok={overview.voucherNumberContinuous} label={overview.voucherNumberContinuous ? '检查通过' : '需要整理'}/>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="损益结转" value={formatMoney(overview.profitLossBalance) || '0.00'}
              ok={overview.profitLossVoucherGenerated && overview.profitLossBalance === 0}
              warn={!overview.profitLossVoucherGenerated}
              label={!overview.profitLossVoucherGenerated ? '未生成结转凭证' : overview.profitLossBalance === 0 ? '已结平' : '待结转'}/>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="利润表检查"
              value={overview.incomeStatementUnmappedSubjectCount
                ? `${overview.incomeStatementUnmappedSubjectCount} 个科目未纳入公式`
                : overview.incomeStatementBalanced ? '勾稽平衡' : '勾稽不平衡'}
              ok={overview.incomeStatementBalanced && overview.incomeStatementUnmappedSubjectCount === 0}
              label={overview.incomeStatementBalanced && overview.incomeStatementUnmappedSubjectCount === 0 ? '检查通过' : '需要处理'}/>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="资产负债平衡" value={`差额 ${formatMoney(overview.balanceSheetDifference) || '0.00'}`}
              ok={overview.balanceSheetProfitLossTransferred && overview.balanceSheetBalanced && overview.balanceSheetUnmappedSubjectCount === 0}
              label={balanceSheetCheckLabel}/>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatusCard title="期间状态" value={overview.closed ? '已结账' : '未结账'}
              ok={overview.closed} label={overview.closed ? '账簿已锁定' : '允许继续记账'}/>
          </Col>
        </Row>
      </div>

      {accountSetId && (
        <FmsClosingSchemes
          accountSetId={accountSetId}
          month={month}
          currentPeriod={isCurrentPeriod}
          closed={overview.closed}
          writable={writable}
          voucherCount={overview.voucherCount}
          profitLossBalance={overview.profitLossBalance}
          permissions={permissions}
          onChanged={() => void getOverview(month)}
        />
      )}

      {/* 执行结账 */}
      <Card size="small" title="执行结账" bordered={false} className="fms-table-area">
        <Space>
          {writable && !overview.closed && !isBeforeCurrentPeriod && canCloseAction && (
            <Button type="primary" loading={submitting} disabled={!overview.canClose} onClick={handleClose}>
              {isCurrentPeriod ? '结账' : `结账到 ${monthLabel}`}
            </Button>
          )}
          {writable && overview.closed && canCancelAction && (
            <Button danger loading={submitting} onClick={handleCancel}>
              {isCurrentPeriod ? '反结账' : `反结账到 ${monthLabel}`}
            </Button>
          )}
          {!overview.closed && !isBeforeCurrentPeriod && !overview.canClose && (
            <span style={{ color: 'var(--crm-color-warning)' }}>完成上方检查后才可结账</span>
          )}
          {!overview.closed && isBeforeCurrentPeriod && (
            <span style={{ color: 'var(--crm-color-warning)' }}>结账目标不能早于当前会计期间 {currentMonthLabel}</span>
          )}
        </Space>
      </Card>
    </section>
  )
}

/** 结账状态卡片 */
function StatusCard({ title, value, ok, warn, label }: {
  title: string; value: string; ok: boolean; warn?: boolean; label: string
}) {
  const color = ok ? 'success' : warn ? 'warning' : 'error'
  return (
    <Card size="small" bordered>
      <div style={{ marginBlockEnd: 8, fontWeight: 500 }}>{title}</div>
      <div style={{ marginBlockEnd: 8 }}>{value}</div>
      <Tag color={color}>{label}</Tag>
    </Card>
  )
}
