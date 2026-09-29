import { Button, Empty, Tooltip } from 'antd'
import type { ReactNode } from 'react'
import { averageFormula, money, percent, type CategoryPoint, type Group, type HistoryMonth, type Metric } from '../../services/salesPerformance'

const colors = ['var(--ant-color-primary)', 'var(--ant-color-success)', 'var(--ant-color-warning)', 'var(--ant-color-error)', 'var(--ant-color-info)', 'var(--crm-text-secondary)']
const empty = <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="所选范围暂无数据" />
const fmtTime = (value?: string) => value?.replace('T', ' ') ?? '—'
const range = (start: string, end: string) => `${fmtTime(start)} 至 ${fmtTime(end)}（截止不含）`
function Tip({ title, children }: { title: ReactNode; children: ReactNode }) {
  return <Tooltip title={title} trigger={['hover', 'focus']}>{children}</Tooltip>
}
function Axes({ max, labels, unit }: { max: number; labels: string[]; unit: string }) {
  return <><text x="8" y="17" fill="currentColor">{unit}</text>{[0, 1, 2, 3, 4].map(i => <g key={i}>
    <line x1="80" x2="870" y1={240 - i * 50} y2={240 - i * 50} stroke="var(--crm-border)" />
    <text x="72" y={244 - i * 50} textAnchor="end" fill="currentColor">{new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1, notation: max >= 10000 ? 'compact' : 'standard' }).format(max * i / 4)}</text>
  </g>)}{labels.map((label, i) => i % Math.max(1, (labels.length <= 12 ? 1 : Math.ceil(labels.length / 10))) === 0 || i === labels.length - 1 ? <text key={i} x={80 + (i + .5) * 790 / labels.length} y="265" textAnchor="middle" fill="currentColor">{label}</text> : null)}</>
}
function Plot({ title, children }: { title: string; children: ReactNode }) {
  return <div className="performance-plot-scroll"><svg className="performance-plot" viewBox="0 0 900 285" role="img" aria-label={title}>{children}</svg></div>
}
export function HistoryChart({ rows, year }: { rows: HistoryMonth[]; year: number }) {
  if (!rows.length) return empty
  const max = Math.max(1, ...rows.flatMap(x => [Number(x.amount), Number(x.previousAmount)]))
  return <><div className="performance-legend"><span style={{ color: colors[0] }}>● {year}年</span><span style={{ color: colors[1] }}>● {year - 1}年</span><span>当月按同期已过时段比较；未来月份不计算</span></div>
    <Plot title="历史月业绩与去年同期对比"><Axes max={max} labels={rows.map(x => `${x.month}月`)} unit="成交金额（元）" />{rows.map((x, i) => {
      const diff = Number(x.amount) - Number(x.previousAmount)
      return <Tip key={x.month} title={x.future ? `${x.month}月尚未开始` : <div>{x.month}月<br />{year}年：{money(x.amount)}<br />{year - 1}年：{money(x.previousAmount)}<br />差额：{money(diff)} · 同比：{percent(x.previousAmount ? diff / x.previousAmount : null)}<br />本期：{range(x.start, x.end)}<br />同期：{range(x.previousStart, x.previousEnd)}</div>}><g tabIndex={0} aria-label={`${x.month}月 ${x.future ? '尚未开始' : money(x.amount)}`}>
        <rect x={80 + i * 790 / rows.length} y="35" width={790 / rows.length} height="210" fill="transparent" />
        {x.future ? <text x={80 + (i + .5) * 790 / rows.length} y="225" textAnchor="middle" fill="currentColor">—</text> : [x.amount, x.previousAmount].map((v, j) => <rect key={j} x={80 + (i + .16 + j * .36) * 790 / rows.length} y={240 - Number(v) / max * 200} width={790 / rows.length * .28} height={Number(v) / max * 200} fill={colors[j]} />)}
      </g></Tip>
    })}</Plot></>
}
export function RevenueTrend({ rows }: { rows: Metric[] }) {
  if (!rows.length) return empty
  const max = Math.max(1, ...rows.map(x => Number(x.amount)))
  return <Plot title="区间成交金额趋势"><Axes max={max} labels={rows.map(x => x.label.slice(5))} unit="成交金额（元）" />{rows.map((x, i) => <Tip key={x.key} title={<div>{range(x.start, x.end)}<br />成交金额：{money(x.amount)}<br />订单数：{x.orders}笔</div>}><g tabIndex={0} aria-label={`${x.label} ${money(x.amount)} ${x.orders}笔`}><rect x={80 + i * 790 / rows.length} y="35" width={790 / rows.length} height="210" fill="transparent" /><rect x={80 + (i + .2) * 790 / rows.length} y={240 - Number(x.amount) / max * 200} width={790 / rows.length * .6} height={Number(x.amount) / max * 200} fill={colors[0]} /></g></Tip>)}</Plot>
}
export function AverageTrend({ series }: { series: Record<string, Metric[]> }) {
  const keys = ['all', 'inbound', 'self'], names = ['整体', '线上引流', '非引流']
  const rows = series.all ?? [], max = Math.max(1, ...keys.flatMap(k => (series[k] ?? []).map(x => Number(x.average))))
  if (!keys.some(k => series[k]?.some(x => x.average != null))) return empty
  const x = (i: number) => 80 + (i + .5) * 790 / Math.max(1, rows.length)
  return <><div className="performance-legend">{names.map((name, i) => <span key={name} style={{ color: colors[i] }}>● {name}</span>)}<span>无符合口径订单的时段留空，不按零元连接</span></div>
    <Plot title="各来源客单价趋势"><Axes max={max} labels={rows.map(r => r.label.slice(5))} unit="客单价（元/笔）" />{keys.map((key, i) => <g key={key}>{(series[key] ?? []).map((r, j, points) => r.average == null ? null : <g key={r.key}>
      {j > 0 && points[j - 1].average != null && <line x1={x(j - 1)} y1={240 - Number(points[j - 1].average) / max * 200} x2={x(j)} y2={240 - r.average / max * 200} stroke={colors[i]} strokeWidth="2" />}
      <Tip title={<div>{names[i]} · {range(r.start, r.end)}<br />{averageFormula(r)}</div>}><circle tabIndex={0} aria-label={`${names[i]} ${r.label} ${averageFormula(r)}`} cx={x(j)} cy={240 - r.average / max * 200} r="5" fill={colors[i]} /></Tip>
    </g>)}</g>)}</Plot></>
}
export function Distribution({ rows, amount = false, onOpen }: { rows: Group[]; amount?: boolean; onOpen?: (row: Group) => void }) {
  const value = (r: Group) => amount ? Number(r.amount) : r.count
  const total = rows.reduce((n, r) => n + value(r), 0)
  if (!total) return empty
  let offset = 0
  return <div className="performance-distribution"><svg viewBox="0 0 200 200" role="img" aria-label={amount ? '成交来源金额占比' : '客资分类人数占比'}>{rows.map((r, i) => {
    const length = value(r) / total * 440, start = offset; offset += length
    return <Tip key={r.key} title={`${r.label}：${amount ? money(r.amount) : `${r.count}人`} · ${percent(value(r) / total)}`}><circle tabIndex={0} cx="100" cy="100" r="70" fill="none" stroke={colors[i % colors.length]} strokeWidth="24" strokeDasharray={`${length} ${440 - length}`} strokeDashoffset={-start} transform="rotate(-90 100 100)" /></Tip>
  })}<text x="100" y="96" textAnchor="middle" fill="currentColor">{amount ? '成交金额' : '客资人数'}</text><text x="100" y="118" textAnchor="middle" fill="currentColor">{amount ? money(total) : total}</text></svg><div className="performance-distribution-legend">{rows.map((r, i) => <div key={r.key}><span style={{ color: colors[i % colors.length] }}>● </span>{onOpen ? <Button type="link" onClick={() => onOpen(r)}>{r.label}</Button> : r.label}<strong>{amount ? money(r.amount) : `${r.count}人`} · {percent(value(r) / total)}</strong></div>)}</div></div>
}
export function CategoryTrend({ rows }: { rows: CategoryPoint[] }) {
  if (!rows.length) return empty
  const buckets = [...new Set(rows.map(x => x.bucket))].sort(), categories = [...new Set(rows.map(x => x.category))].sort()
  const max = Math.max(1, ...buckets.map(b => rows.filter(x => x.bucket === b).reduce((n, x) => n + x.count, 0)))
  return <><div className="performance-legend">{categories.map((c, i) => <span key={c} style={{ color: colors[i % colors.length] }}>● {c}</span>)}</div><Plot title="客资分类接收趋势"><Axes max={max} labels={buckets.map(b => b.slice(5))} unit="接收客资（人）" />{buckets.map((b, i) => {
    let offset = 0
    return <g key={b}>{categories.map((c, j) => { const count = rows.find(r => r.bucket === b && r.category === c)?.count ?? 0; const bottom = offset; offset += count; return count ? <Tip key={c} title={`${b} · ${c}：${count}人`}><rect tabIndex={0} x={80 + (i + .2) * 790 / buckets.length} y={240 - offset / max * 200} width={790 / buckets.length * .6} height={(offset - bottom) / max * 200} fill={colors[j % colors.length]} /></Tip> : null })}</g>
  })}</Plot></>
}
export function FollowUpCompletion({ rows }: { rows: Group[] }) {
  const total = rows.reduce((n, r) => n + r.count, 0), cancelled = rows.find(x => x.key === 'cancelled')?.count ?? 0
  if (!total) return empty
  const completed = rows.find(x => x.key === 'completed')?.count ?? 0
  return <><p>计划总数 {total} · 已完成 {completed} · 完成率 {percent(total > cancelled ? completed / (total - cancelled) : null)}（已完成 ÷ 未取消计划数）</p><div className="performance-stack" role="img" aria-label="跟进任务互斥状态分布">{rows.map((r, i) => r.count > 0 && <Tip key={r.key} title={`${r.label}：${r.count}`}><div tabIndex={0} style={{ flex: r.count, background: colors[i % colors.length] }} /></Tip>)}</div><div className="performance-legend">{rows.map((r, i) => <span key={r.key} style={{ color: colors[i % colors.length] }}>● {r.label} {r.count}</span>)}</div></>
}
export function Funnel({ rows }: { rows: Group[] }) {
  const max = Math.max(1, ...rows.map(r => r.count))
  return <div className="performance-funnel">{rows.map((r, i) => <div className="performance-funnel-row" key={r.key}><div><strong>{r.label} {r.count}</strong><span>{i ? `较上一步 ${percent(rows[i - 1].count ? r.count / rows[i - 1].count : null)}` : '所选月接收批次'}</span></div><svg viewBox="0 0 400 50" role="img" aria-label={`${r.label} ${r.count}`}><polygon points={`${200 - 190 * r.count / max},0 ${200 + 190 * r.count / max},0 ${200 + 190 * (rows[i + 1]?.count ?? r.count) / max},48 ${200 - 190 * (rows[i + 1]?.count ?? r.count) / max},48`} fill={colors[i % colors.length]} /></svg></div>)}</div>
}
