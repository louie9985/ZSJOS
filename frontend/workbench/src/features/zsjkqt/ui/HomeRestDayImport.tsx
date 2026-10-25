import { Select } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { calendarRestDays } from '../../../services/calendarRestDays'
import { homeRestDates } from '../core/homeRestDays'

export function HomeRestDayImport({ year, month, allowed, onImport }: {
  year: number; month: number; allowed: boolean
  onImport: (days: number[], kind: 'rest' | 'paid') => void
}) {
  const [kind, setKind] = useState<'rest' | 'paid'>('rest')
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState('')
  const request = useRef<AbortController | null>(null)
  const latestImport = useRef(onImport)
  latestImport.current = onImport
  useEffect(() => { setStatus(''); setLoading(false); return () => request.current?.abort() }, [year, month, allowed])
  async function load() {
    const controller = new AbortController()
    request.current = controller
    setLoading(true); setStatus('')
    try {
      const prefix = `${year}-${String(month).padStart(2, '0')}-`
      const rows = await calendarRestDays.list(prefix + '01', prefix + new Date(year, month, 0).getDate(), controller.signal)
      if (controller.signal.aborted) return
      const days = homeRestDates(rows, year, month)
      latestImport.current(days, kind)
      setStatus(days.length ? `已读取 ${days.length} 个休息日，补充未设置日期并保留已有分类，可继续手动调整。` : '首页当月没有标记休息日，已有设置已保留。')
    } catch (error) {
      if (!controller.signal.aborted) setStatus(error instanceof Error ? error.message : '读取失败，请重试')
    } finally {
      if (!controller.signal.aborted) setLoading(false)
    }
  }
  return <div className="home-rest-import">
    <div className="home-rest-import-toolbar">
      <span className="home-rest-import-label">导入为</span>
      <Select<'rest' | 'paid'> aria-label="首页休息日导入类型" className="home-rest-import-select"
        disabled={loading || !allowed} value={kind} onChange={setKind}
        options={[{ value: 'rest', label: '普通休息日' }, { value: 'paid', label: '带薪休息日' }]} />
      <button className="ghost-button" disabled={loading || !allowed} onClick={() => void load()}>{loading ? '正在读取…' : '从首页导入休息日'}</button>
    </div>
    <p role="status">{!allowed ? '需要首页业务任务查看权限才能导入休息日。' : status || '读取当前考勤月份，仅补充未设置日期；失败后可再次点击重试。'}</p>
  </div>
}
