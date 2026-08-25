import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Col, Empty, Row, Spin } from 'antd'
import { BarChartOutlined, EditOutlined, FileSearchOutlined, SearchOutlined } from '@ant-design/icons'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsHomeApi, type FmsHome, type FmsHomeMetricDetail } from '../../services/fms/home'
import { formatAmount, formatMoney } from '../../services/fms/format'
import { FMS_HOME_METRIC_COLORS } from '../../services/fms/constants'
import { APP_ROUTES } from '../../constants'
import { useNavigate } from 'react-router-dom'
import FmsChart from '../../components/fms/FmsChart'

const SHORTCUTS = [
  { label: '录凭证', path: APP_ROUTES.FMS_VOUCHER_CREATE, icon: <EditOutlined />, permission: 'fms:voucher:create', writeRequired: true },
  { label: '查凭证', path: APP_ROUTES.FMS_VOUCHER_LIST, icon: <SearchOutlined />, permission: 'fms:voucher:query', writeRequired: false },
  { label: '科目余额表', path: APP_ROUTES.FMS_LEDGER_SUBJECT_BALANCE, icon: <BarChartOutlined />, permission: 'fms:ledger:subject-balance:query', writeRequired: false },
  { label: '明细账', path: APP_ROUTES.FMS_LEDGER_DETAIL, icon: <FileSearchOutlined />, permission: 'fms:ledger:detail:query', writeRequired: false }
]

function formatCompactAmount(amount: number) {
  const value = Number(amount || 0)
  if (Math.abs(value) >= 10000) return `${(value / 10000).toFixed(1)}万`
  return value.toFixed(0)
}

export default function FmsHomePage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const [home, setHome] = useState<FmsHome>()
  const [metricDetail, setMetricDetail] = useState<FmsHomeMetricDetail>()
  const [selectedMetricKey, setSelectedMetricKey] = useState<string>()
  const [loading, setLoading] = useState(false)
  const [homeError, setHomeError] = useState('')
  const [metricLoading, setMetricLoading] = useState(false)
  const [metricError, setMetricError] = useState('')
  const version = useRef(0)
  const metricSequence = useRef(0)

  const loadHome = useCallback(async () => {
    if (!accountSetId) { setHome(undefined); return }
    const v = ++version.current
    setLoading(true)
    setHomeError('')
    try {
      const result = await fmsHomeApi.getHome(accountSetId)
      if (v !== version.current) return
      setHome(result)
      const first = result.metrics[0]
      if (first) await selectMetric(first.key)
    } catch (e) {
      if (v !== version.current) return
      setHome(undefined)
      setHomeError(e instanceof Error ? e.message : '财务首页加载失败')
    } finally {
      if (v === version.current) setLoading(false)
    }
  }, [accountSetId])

  const selectMetric = useCallback(async (key: string) => {
    if (!accountSetId) return
    const seq = ++metricSequence.current
    setSelectedMetricKey(key)
    setMetricDetail(undefined)
    setMetricLoading(true)
    setMetricError('')
    try {
      const result = await fmsHomeApi.getMetricDetail(accountSetId, key)
      if (seq === metricSequence.current) setMetricDetail(result)
    } catch (e) {
      if (seq === metricSequence.current) setMetricError(e instanceof Error ? e.message : '指标明细加载失败')
    } finally {
      if (seq === metricSequence.current) setMetricLoading(false)
    }
  }, [accountSetId])

  useEffect(() => {
    if (accountSetId) void loadHome()
    else { setHome(undefined); setMetricDetail(undefined); setHomeError('') }
  }, [accountSetId, loadHome])

  const visibleShortcuts = SHORTCUTS.filter(shortcut =>
    permissions.includes(shortcut.permission) && (!shortcut.writeRequired || writable))

  const trendOptions = useMemo(() => {
    const common = {
      color: FMS_HOME_METRIC_COLORS,
      grid: { left: 16, right: 24, top: 48, bottom: 12, containLabel: true },
      legend: { top: 0 },
      tooltip: { trigger: 'axis' as const, valueFormatter: (value: unknown) => formatAmount(Number(value)) },
      yAxis: { type: 'value' as const, axisLabel: { formatter: (v: number) => formatCompactAmount(v) } }
    }
    if (metricDetail) {
      return {
        ...common,
        xAxis: { type: 'category' as const, boundaryGap: false, data: metricDetail.trends.map(t => t.month) },
        series: [{ name: metricDetail.name, type: 'line' as const, smooth: true, areaStyle: { opacity: 0.12 }, data: metricDetail.trends.map(t => t.amount) }]
      }
    }
    return {
      ...common,
      xAxis: { type: 'category' as const, boundaryGap: false, data: home?.trends.map(t => t.month) || [] },
      series: (home?.metrics || []).map(m => ({
        name: m.name, type: 'line' as const, smooth: true,
        data: (home?.trends || []).map(t => t.metrics.find(x => x.key === m.key)?.amount || 0)
      }))
    }
  }, [metricDetail, home, selectedMetricKey])

  const structureData = useMemo(() => {
    if (metricDetail) {
      const structure = metricDetail.structure.filter(s => Number(s.amount) > 0).map(s => ({ name: `${s.subjectCode} ${s.subjectName}`, value: Number(s.amount) }))
      if (structure.length === 0) {
        const metric = home?.metrics.find(x => x.key === selectedMetricKey)
        return metric && Number(metric.amount) > 0 ? [{ name: metric.name, value: Number(metric.amount) }] : []
      }
      const result = structure.slice(0, 5)
      if (structure.length > 5) result.push({ name: '其他', value: structure.slice(5).reduce((s, x) => s + x.value, 0) })
      return result
    }
    return (home?.metrics || []).filter(m => Number(m.amount) > 0).map(m => ({ name: m.name, value: Number(m.amount) }))
  }, [metricDetail, home, selectedMetricKey])

  const structureOptions = useMemo(() => ({
    color: FMS_HOME_METRIC_COLORS,
    tooltip: { trigger: 'item' as const, valueFormatter: (value: unknown) => formatAmount(Number(value)) },
    legend: { bottom: 0 },
    series: [{ name: '本期指标', type: 'pie' as const, radius: ['46%', '72%'], center: ['50%', '44%'], label: { formatter: '{b}\n{d}%' }, data: structureData }]
  }), [structureData])

  const currentMonthLabel = home?.currentMonth ? `${Number(home.currentMonth.slice(5, 7))}月` : ''

  return (
    <section className="workspace-page fms-page">
      {/* 快捷入口 */}
      {visibleShortcuts.length > 0 && <div className="fms-table-area" style={{ marginBlockEnd: 'var(--crm-sp-3)' }}>
        <Row gutter={16}>
          {visibleShortcuts.map(s => (
            <Col key={s.path} xs={12} sm={8} md={6} lg={4}>
              <button type="button" onClick={() => navigate(s.path)} style={{ width: '100%', cursor: 'pointer', textAlign: 'center', padding: 'var(--crm-sp-2)', border: 0, background: 'transparent' }}>
                <div style={{ fontSize: 24 }}>{s.icon}</div>
                <div>{s.label}</div>
              </button>
            </Col>
          ))}
        </Row>
      </div>}

      {homeError && <Alert type="error" showIcon message="财务首页加载失败" description={homeError} action={<Button size="small" onClick={() => void loadHome()}>重试</Button>} style={{ marginBottom: 12 }} />}

      <Spin spinning={loading}>
        {/* 指标卡片 */}
        <div className="fms-table-area" style={{ marginBlockEnd: 'var(--crm-sp-3)' }}>
          <Row gutter={16}>
            {(home?.metrics || []).map(m => (
              <Col key={m.key} xs={12} sm={8} md={6} lg={4}>
                <div
                  onClick={() => selectMetric(m.key)}
                  style={{ cursor: 'pointer', padding: 'var(--crm-sp-2)', borderRadius: 'var(--crm-radius-md)',
                    border: selectedMetricKey === m.key ? '1px solid var(--crm-color-primary)' : '1px solid var(--crm-border)' }}
                >
                  <div style={{ color: 'var(--crm-text-secondary)', fontSize: 13 }}>{m.name}</div>
                  <div style={{ fontSize: 18, fontWeight: 600 }}>{formatMoney(m.amount) || '0.00'}</div>
                </div>
              </Col>
            ))}
          </Row>
          {!home && !homeError && <Empty description={accountSetId ? '暂无财务指标' : '请选择账套'}/>}
        </div>

        {/* 图表区 */}
        <Row gutter={16}>
          <Col xs={24} xl={14}>
            <div className="fms-table-area">
              <div style={{ marginBlockEnd: 16, fontWeight: 500 }}>
                {metricDetail ? `${metricDetail.name}变化趋势（单位：元）` : '财务指标趋势（单位：元）'}
              </div>
              {metricError && <Alert type="warning" showIcon message={metricError} action={selectedMetricKey ? <Button size="small" onClick={() => void selectMetric(selectedMetricKey)}>重试</Button> : undefined} style={{ marginBottom: 12 }} />}
              <FmsChart options={trendOptions} height={360} loading={metricLoading}/>
            </div>
          </Col>
          <Col xs={24} xl={10}>
            <div className="fms-table-area">
              <div style={{ marginBlockEnd: 16, fontWeight: 500 }}>
                {metricDetail ? `${currentMonthLabel} ${metricDetail.name}结构分析（单位：元）` : '本期指标结构（单位：元）'}
              </div>
              {structureData.length > 0
                ? <FmsChart options={structureOptions} height={360}/>
                : <Empty style={{ height: 360, display: 'flex', justifyContent: 'center', alignItems: 'center' }} description="暂无科目构成数据"/>}
            </div>
          </Col>
        </Row>
      </Spin>
    </section>
  )
}
