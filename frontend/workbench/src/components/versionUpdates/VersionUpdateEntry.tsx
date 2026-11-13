import { useLocation } from 'react-router-dom'
import { APP_ROUTES } from '../../constants'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Badge, Button, Drawer, Empty, Modal, Spin, Tag } from 'antd'
import { HistoryOutlined } from '@ant-design/icons'
import { ApiError } from '../../services/api'
import { versionUpdates, type VersionUpdate } from '../../services/versionUpdates'
import { formatTimestamp } from '../../services/time'
import { useRealtime, useRealtimeEvent } from '../RealtimeProvider'
import { useBusinessOverlay } from '../OverlayCoordinator'
import { useForcedForms } from '../ForcedFormProvider'
import { useExamReminderVisibility } from '../examCollection/useExamReminderVisibility'
import VersionContent from './VersionContent'
import './version-updates.css'

const unavailable = (cause: unknown) => cause instanceof ApiError && [1900080001, 1900080002, 1900080003].includes(cause.code)
const errorText = (cause: unknown) => cause instanceof Error ? cause.message : '版本更新加载失败'

export default function VersionUpdateEntry({ canAcknowledge, historyInitiallyOpen = false }: { canAcknowledge: boolean; historyInitiallyOpen?: boolean }) {
  const location = useLocation()
  const { status } = useRealtime()
  const forced = useForcedForms()
  const [pending, setPending] = useState<VersionUpdate>()
  const [count, setCount] = useState(0)
  const [history, setHistory] = useState(historyInitiallyOpen)
  const [records, setRecords] = useState<VersionUpdate[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [active, setActive] = useState<VersionUpdate>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [syncError, setSyncError] = useState('')
  const [saving, setSaving] = useState(false)
  const [highlight, setHighlight] = useState(false)
  const button = useRef<HTMLButtonElement>(null)
  const surface = useRef<HTMLDivElement>(null)
  const syncRequest = useRef(0)
  const detailRequest = useRef(0)
  const historyRequest = useRef(0)
  const alive = useRef(true)
  const savingRef = useRef(false)
  const opening = useRef(false)
  const historyBusy = useRef(false)
  const activeRef = useRef(active)
  activeRef.current = active
  useBusinessOverlay(history || !!active)
  const mayOpen = useExamReminderVisibility(!!pending && !active && !history && !forced.loading && forced.pendingCount === 0 && canAcknowledge)

  const refresh = useCallback(async () => {
    const request = ++syncRequest.current
    try {
      const result = await versionUpdates.mine({ pageNo: 1, pageSize: 1, pending: true })
      if (!alive.current || request !== syncRequest.current) return
      setPending(result.list[0]); setCount(result.total); setSyncError('')
      const current = activeRef.current
      const currentDetailRequest = detailRequest.current
      if (current && !savingRef.current && !opening.current) {
        try {
          const latest = await versionUpdates.get(current.id, true)
          if (alive.current && currentDetailRequest === detailRequest.current && activeRef.current?.id === current.id && !savingRef.current) setActive(latest)
        } catch (cause) {
          if (alive.current && activeRef.current?.id === current.id && unavailable(cause)) { setActive(undefined); setError('') }
        }
      }
    } catch (cause) { if (alive.current && request === syncRequest.current) setSyncError(errorText(cause)) }
  }, [])
  const loadHistory = useCallback(async (nextPage: number) => {
    if (historyBusy.current) return
    historyBusy.current = true
    const request = ++historyRequest.current
    setHistoryLoading(true); setHistoryError('')
    try {
      const result = await versionUpdates.mine({ pageNo: nextPage, pageSize: 20 })
      if (!alive.current || request !== historyRequest.current) return
      setRecords(current => nextPage === 1 ? result.list : [...current, ...result.list.filter(row => !current.some(existing => existing.id === row.id))])
      setPage(nextPage); setTotal(result.total)
    } catch (cause) { if (alive.current && request === historyRequest.current) setHistoryError(errorText(cause)) }
    finally { historyBusy.current = false; if (alive.current) setHistoryLoading(false) }
  }, [])
  const open = useCallback(async (record: VersionUpdate) => {
    if (opening.current || savingRef.current) return
    opening.current = true
    const request = ++detailRequest.current
    setActive(record); setLoading(true); setError('')
    try {
      const result = await versionUpdates.get(record.id, true)
      if (alive.current && request === detailRequest.current) setActive(result)
    } catch (cause) {
      if (alive.current && request === detailRequest.current) {
        if (unavailable(cause)) { setActive(undefined); void refresh() }
        else setError(errorText(cause))
      }
    } finally { opening.current = false; if (alive.current && request === detailRequest.current) setLoading(false) }
  }, [refresh])
  useEffect(() => {
    alive.current = true
    void refresh()
    const focus = () => { void refresh() }
    const timer = window.setInterval(() => { if (!document.hidden) void refresh() }, 60_000)
    window.addEventListener('focus', focus)
    return () => { alive.current = false; syncRequest.current++; detailRequest.current++; historyRequest.current++; window.clearInterval(timer); window.removeEventListener('focus', focus) }
  }, [refresh])
  useEffect(() => { if (status === 'open') void refresh() }, [refresh, status])
  useEffect(() => { if (historyInitiallyOpen || location.pathname === APP_ROUTES.VERSION_UPDATE_HISTORY) { setHistory(true); void loadHistory(1) } }, [historyInitiallyOpen, loadHistory, location.pathname])
  useRealtimeEvent('version-update-changed', () => { void refresh(); if (history) void loadHistory(1) })
  useEffect(() => { if (mayOpen && pending) void open(pending) }, [mayOpen, pending, open])
  const close = () => { detailRequest.current++; activeRef.current = undefined; setActive(undefined); setError('') }
  const acknowledge = async () => {
    if (!active || savingRef.current || loading || error) return
    savingRef.current = true; setSaving(true)
    let acknowledged = false
    try {
      await versionUpdates.acknowledge(active.id)
      acknowledged = true
      if (!alive.current) return
      const acknowledgedId = active.id
      setPending(undefined)
      syncRequest.current++
      setRecords(current => current.map(row => row.id === acknowledgedId ? { ...row, acknowledgedTime: Date.now() } : row))
      const source = surface.current
      const target = button.current
      if (source && target) {
        const from = source.getBoundingClientRect()
        const to = target.getBoundingClientRect()
        const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        const frames = reduced ? [{ opacity: 1 }, { opacity: 0 }] : [
          { transform: 'translate(0,0) scale(1)', opacity: 1 },
          { transform: `translate(${to.x + to.width / 2 - from.x - from.width / 2}px, ${to.y + to.height / 2 - from.y - from.height / 2}px) scale(0.04)`, opacity: 0 }
        ]
        await source.animate(frames, { duration: reduced ? 120 : 460, easing: 'cubic-bezier(.4,0,.2,1)', fill: 'forwards' }).finished.catch(() => undefined)
      }
    } catch (cause) {
      if (alive.current && !acknowledged) {
        if (unavailable(cause)) { close(); void refresh() }
        else setError(errorText(cause))
      }
    } finally {
      savingRef.current = false
      if (alive.current) {
        setSaving(false)
        if (acknowledged) { close(); setHighlight(true); button.current?.focus(); void refresh() }
      }
    }
  }
  return <>
    <Badge count={count} dot={!!syncError} size="small"><Button ref={button} type="text" icon={<HistoryOutlined />} className={highlight ? 'version-update-entry is-highlighted' : 'version-update-entry'} onAnimationEnd={() => setHighlight(false)}
      onClick={() => { setHistory(true); void loadHistory(1) }}>更新记录</Button></Badge>
    <Drawer title="更新记录" open={history} onClose={() => setHistory(false)} size="large" className="version-update-history">
      {(historyError || syncError) && <Alert type="error" showIcon title={historyError || syncError} action={<Button onClick={() => { void refresh(); void loadHistory(page || 1) }}>重试</Button>} />}
      <Spin spinning={historyLoading && !records.length}>
        {!historyLoading && !records.length && !historyError && <Empty description="暂无版本更新记录" />}
        <div className="version-update-list">{records.map(record => <button key={record.id} type="button" className="version-update-record" onClick={() => void open(record)}>
          <span><strong>{record.title}</strong><Tag color={record.acknowledgedTime ? undefined : 'blue'}>{record.acknowledgedTime ? '已知悉' : '待知悉'}</Tag></span>
          <time>{formatTimestamp(record.publishTime)}</time>
        </button>)}</div>
      </Spin>
      {records.length < total && <Button block loading={historyLoading} onClick={() => void loadHistory(page + 1)}>加载更多</Button>}
    </Drawer>
    <Modal open={!!active && forced.pendingCount === 0} title="版本更新通知" width="min(900px, calc(100vw - 24px))" closable={!!active?.acknowledgedTime || !canAcknowledge} keyboard={!saving && (!!active?.acknowledgedTime || !canAcknowledge)}
      mask={{ closable: false }} onCancel={close} destroyOnHidden modalRender={node => <div key={active?.id} ref={surface}>{node}</div>}
      footer={active?.acknowledgedTime || !canAcknowledge ? <Button onClick={close}>关闭</Button> : <Button type="primary" loading={saving} disabled={loading || !!error} onClick={() => void acknowledge()}>我已知悉，关闭</Button>}>
      {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => { if (active) void open(active) }}>重试</Button>} />}
      <Spin spinning={loading}>{active && <VersionContent record={active} reload={() => versionUpdates.get(active.id, true)} />}</Spin>
    </Modal>
  </>
}
