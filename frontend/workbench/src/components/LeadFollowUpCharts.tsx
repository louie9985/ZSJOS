import { useEffect, useMemo, useState } from 'react'
import { Empty, Typography } from 'antd'
import { api, type LeadFollowUp } from '../services/api'

const PAGE_SIZE = 100

/* ========== 数据加载 hook ========== */

function useAllFollowUps(leadId: number) {
  const [records, setRecords] = useState<LeadFollowUp[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api.leadFollowUpPage(leadId, { pageNo: 1, pageSize: PAGE_SIZE })
      .then(page => { if (!cancelled) setRecords(page.list) })
      .catch(() => { if (!cancelled) setRecords([]) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [leadId])

  return { records, loading }
}

/* ========== 沉默天数 ========== */

function SilentDays({ records }: { records: LeadFollowUp[] }) {
  const days = useMemo(() => {
    if (!records.length) return null
    const latest = Math.max(...records.map(r => r.occurredAt))
    return Math.floor((Date.now() - latest) / (1000 * 60 * 60 * 24))
  }, [records])

  if (days === null) return null

  const color = days <= 2 ? 'var(--crm-color-success)' : days <= 5 ? 'var(--crm-color-warning)' : 'var(--crm-color-error)'

  return (
    <div className="chart-silent-days">
      <span className="chart-silent-days-number" style={{ color }}>{days}</span>
      <span className="chart-silent-days-label">天未跟进</span>
    </div>
  )
}

/* ========== 跟进间隔柱状图 ========== */

function FrequencyChart({ records }: { records: LeadFollowUp[] }) {
  const data = useMemo(() => {
    if (records.length < 2) return []
    const sorted = [...records].sort((a, b) => a.occurredAt - b.occurredAt)
    return sorted.slice(1).map((r, i) => ({
      date: r.occurredAt,
      gap: Math.round((r.occurredAt - sorted[i].occurredAt) / (1000 * 60 * 60 * 24))
    }))
  }, [records])

  if (data.length === 0) return null

  const maxGap = Math.max(...data.map(d => d.gap), 1)
  const width = 100
  const height = 40
  const barWidth = Math.min(8, (width - 4) / data.length - 1)
  const step = (width - 4) / data.length

  return (
    <div className="chart-frequency">
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="chart-frequency-svg">
        {data.map((d, i) => {
          const barHeight = Math.max(2, (d.gap / maxGap) * (height - 4))
          const color = d.gap <= 3 ? 'var(--crm-color-success)' : d.gap <= 7 ? 'var(--crm-color-warning)' : 'var(--crm-color-error)'
          return (
            <rect
              key={i}
              x={2 + i * step}
              y={height - 2 - barHeight}
              width={barWidth}
              height={barHeight}
              rx={1}
              fill={color}
              opacity={0.8}
            >
              <title>间隔 {d.gap} 天</title>
            </rect>
          )
        })}
      </svg>
      <div className="chart-frequency-legend">
        <span>每柱 = 两次跟进间隔天数</span>
      </div>
    </div>
  )
}

/* ========== 跟进方式环形图 ========== */

function MethodDonut({ records }: { records: LeadFollowUp[] }) {
  const [hovered, setHovered] = useState<{ index: number; x: number; y: number } | null>(null)
  const groups = useMemo(() => {
    const map = new Map<string, { label: string; count: number }>()
    records.forEach(r => {
      const existing = map.get(r.method)
      if (existing) existing.count++
      else map.set(r.method, { label: r.methodLabel, count: 1 })
    })
    return [...map.values()].sort((a, b) => b.count - a.count)
  }, [records])

  if (groups.length === 0) return null

  const total = records.length
  const colors = ['var(--crm-color-primary)', 'var(--crm-color-success)', 'var(--crm-color-warning)', 'var(--crm-color-error)', 'var(--crm-text-tertiary)']

  const size = 88
  const strokeWidth = 12
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius

  let offset = 0
  const segments = groups.map((g, i) => {
    const pct = g.count / total
    const dashLength = pct * circumference
    const dashOffset = -offset
    offset += dashLength
    return { ...g, pct, dashLength, dashOffset, color: colors[i % colors.length], index: i }
  })

  const handleMouseMove = (e: React.MouseEvent, index: number) => {
    const rect = e.currentTarget.closest('.chart-donut-wrapper')!.getBoundingClientRect()
    setHovered({ index, x: e.clientX - rect.left, y: e.clientY - rect.top })
  }

  return (
    <div className="chart-method-donut">
      <div className="chart-donut-wrapper">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="chart-donut-svg">
          {segments.map((seg) => (
            <circle
              key={seg.label}
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke={seg.color}
              strokeWidth={hovered?.index === seg.index ? strokeWidth + 3 : strokeWidth}
              strokeDasharray={`${seg.dashLength} ${circumference - seg.dashLength}`}
              strokeDashoffset={seg.dashOffset}
              opacity={hovered === null || hovered.index === seg.index ? 1 : 0.4}
              style={{ transition: 'stroke-width 0.2s, opacity 0.2s' }}
              onMouseMove={(e) => handleMouseMove(e, seg.index)}
              onMouseLeave={() => setHovered(null)}
            />
          ))}
        </svg>
        <div className="chart-donut-center">
          <span className="chart-donut-center-value">{total}</span>
          <span className="chart-donut-center-sub">次跟进</span>
        </div>
        {hovered !== null && (
          <div className="chart-donut-tooltip" style={{ left: hovered.x, top: hovered.y }}>
            <span className="chart-donut-tooltip-label">{groups[hovered.index].label}</span>
            <span className="chart-donut-tooltip-value">{Math.round(segments[hovered.index].pct * 100)}% · {groups[hovered.index].count} 次</span>
          </div>
        )}
      </div>
    </div>
  )
}

/* ========== 主导出 ========== */

export default function LeadFollowUpCharts({ leadId }: { leadId: number }) {
  const { records, loading } = useAllFollowUps(leadId)

  if (loading) return <div className="lead-charts-loading"><Typography.Text type="secondary">图表加载中...</Typography.Text></div>
  if (!records.length) return <div className="lead-charts-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跟进数据" /></div>

  return (
    <div className="lead-follow-up-charts">
      {/* 跟进活跃度（左）+ 跟进方式环形图（右）*/}
      <section className="lead-card lead-chart-card">
        <div className="lead-chart-split">
          <div className="lead-chart-split-left">
            <div className="lead-card-header">
              <Typography.Text strong>跟进活跃度</Typography.Text>
            </div>
            <SilentDays records={records} />
            <div className="chart-total-count">
              <span className="chart-total-number">{records.length}</span>
              <span className="chart-total-label">次跟进</span>
            </div>
          </div>
          <div className="lead-chart-split-right">
            <div className="lead-card-header">
              <Typography.Text strong>跟进方式</Typography.Text>
            </div>
            <MethodDonut records={records} />
          </div>
        </div>
      </section>

      {/* 跟进间隔 */}
      <section className="lead-card lead-chart-card">
        <div className="lead-card-header">
          <Typography.Text strong>跟进间隔</Typography.Text>
        </div>
        <FrequencyChart records={records} />
      </section>
    </div>
  )
}
