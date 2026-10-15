import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Calendar, Modal, Space, Spin, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { ApiError } from '../services/api'
import { calendarRestDays, restDayRange, stageRestDays, REST_DAY_CONFLICT, type RestDay } from '../services/calendarRestDays'
import { homeCalendarLocale, RestDate, RestMonthToolbar, restDayError } from './HomeCalendarPanel'

export default function RestDayManager({ initialMonth, onClose, onSaved }: {
  initialMonth: Dayjs; onClose: () => void; onSaved: () => void
}) {
  const [modal, contextHolder] = Modal.useModal()
  const [month, setMonth] = useState(initialMonth)
  const [base, setBase] = useState<Record<string, RestDay>>({})
  const [pending, setPending] = useState<Record<string, RestDay>>({})
  const [selected, setSelected] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [conflict, setConflict] = useState(false)
  const [revision, setRevision] = useState(0)
  const request = useRef(0)
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const { startDate, endDate } = restDayRange(month)
  const load = useCallback(async (start: string, end: string, signal?: AbortSignal) => {
    const rows = await calendarRestDays.list(start, end, signal)
    const values: Record<string, RestDay> = {}
    for (let date = dayjs(start); !date.isAfter(dayjs(end), 'day'); date = date.add(1, 'day')) {
      const key = date.format('YYYY-MM-DD')
      values[key] = { date: key, rest: false, version: 0 }
    }
    for (const row of rows) values[row.date] = row
    return values
  }, [])
  useEffect(() => {
    const id = ++request.current
    const controller = new AbortController()
    setLoading(true); setError('')
    load(startDate, endDate, controller.signal).then(values => {
      if (id === request.current && !controller.signal.aborted) setBase(old => ({ ...old, ...values }))
    }).catch(cause => {
      if (id === request.current && !controller.signal.aborted) setError('休息日加载失败：' + restDayError(cause))
    }).finally(() => {
      if (id === request.current && !controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [startDate, endDate, revision, load])
  const changes = Object.values(pending)
  const close = () => {
    if (saving) return
    if (!changes.length) { onClose(); return }
    modal.confirm({ title: '放弃未保存的休息日修改？', content: '关闭后不会对其他员工生效。', okText: '放弃修改', cancelText: '继续编辑', onOk: onClose })
  }
  const stage = (rest: boolean) => {
    const next = stageRestDays(base, pending, selected, rest)
    if (Object.keys(next).length > 366) { setError('一次最多保存366个日期，请分批操作'); return }
    setPending(next); setSelected([])
  }
  const save = async () => {
    setSaving(true); setError('')
    try { await calendarRestDays.save(changes); if (alive.current) onSaved() }
    catch (cause) {
      if (alive.current) {
        setError(restDayError(cause))
        setConflict(cause instanceof ApiError && cause.code === REST_DAY_CONFLICT)
      }
    } finally { if (alive.current) setSaving(false) }
  }
  const reconcile = async () => {
    setSaving(true)
    try {
      // Refresh every edited month, retaining the user's intent until they review and save again.
      const months = [...new Set(changes.map(row => row.date.slice(0, 7)).concat(month.format('YYYY-MM')))]
      const maps = await Promise.all(months.map(key => {
        const range = restDayRange(dayjs(key + '-01')); return load(range.startDate, range.endDate)
      }))
      if (!alive.current) return
      const latest = Object.assign({}, ...maps) as Record<string, RestDay>
      setBase(old => ({ ...old, ...latest }))
      const next: Record<string, RestDay> = {}
      for (const change of changes) {
        const current = latest[change.date]
        if (current.rest !== change.rest) next[change.date] = { ...current, rest: change.rest }
      }
      setPending(next); setConflict(false); setError('')
    } catch (cause) { if (alive.current) setError(restDayError(cause)) }
    finally { if (alive.current) setSaving(false) }
  }
  return <Modal open title="管理休息日" width={560} onCancel={close} maskClosable={false} keyboard={!saving}
    closable={!saving} footer={<Space>
      <Button disabled={saving} onClick={close}>取消</Button>
      <Button type="primary" disabled={!changes.length || loading || conflict} loading={saving} onClick={() => void save()}>保存修改</Button>
    </Space>}>
    {contextHolder}
    <div className="home-rest-manager">
      <Alert type="info" title="保存后对所有员工的首页日历生效，仅作休息提示。" />
      <RestMonthToolbar month={month} disabled={saving} onChange={setMonth} />
      {error && <Alert type="error" title={error} action={conflict
        ? <Button disabled={saving || loading} onClick={() => void reconcile()}>刷新并核对</Button>
        : <Button disabled={saving} onClick={() => setRevision(v => v + 1)}>重试加载</Button>} />}
      <Spin spinning={loading || saving}>
        <Calendar fullscreen={false} locale={homeCalendarLocale} value={month} headerRender={() => null}
          onSelect={(date, info) => {
            if (info.source !== 'date' || loading || saving || error || conflict) return
            const key = date.format('YYYY-MM-DD')
            setSelected(old => old.includes(key) ? old.filter(d => d !== key) : [...old, key])
          }}
          fullCellRender={(date, info) => info.type === 'date' ? <RestDate date={date}
            rest={(pending[date.format('YYYY-MM-DD')] ?? base[date.format('YYYY-MM-DD')])?.rest ?? false}
            selected={selected.includes(date.format('YYYY-MM-DD'))} /> : info.originNode} />
      </Spin>
      <Space wrap>
        <Typography.Text>已选 {selected.length} 天</Typography.Text>
        <Button disabled={!selected.length || loading || saving || !!error || conflict} onClick={() => stage(true)}>标记休息</Button>
        <Button disabled={!selected.length || loading || saving || !!error || conflict} onClick={() => stage(false)}>取消休息</Button>
        <Button disabled={!selected.length || saving} onClick={() => setSelected([])}>清空选择</Button>
      </Space>
      <p aria-live="polite">待保存：新增 {changes.filter(row => row.rest).length} 天，取消 {changes.filter(row => !row.rest).length} 天。</p>
      {!!changes.length && <div className="home-rest-changes" aria-label="待保存日期">
        {changes.sort((a, b) => a.date.localeCompare(b.date)).map(row => <div key={row.date}>
          <span>{row.date} · {row.rest ? '标记休息' : '取消休息'}</span>
          <Button type="link" size="small" disabled={saving} aria-label={'撤销修改 ' + row.date}
            onClick={() => setPending(old => { const next = { ...old }; delete next[row.date]; return next })}>撤销</Button>
        </div>)}
      </div>}
      {conflict && <p>待保存内容已保留。刷新后请核对日期列表，再次保存。</p>}
    </div>
  </Modal>
}
