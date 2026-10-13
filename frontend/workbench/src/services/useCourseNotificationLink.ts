import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { api, ApiError, type CourseCalendarEvent } from './api'

export function useCourseNotificationLink(onResolved: (row: CourseCalendarEvent) => void, onReset: () => void) {
  const location = useLocation()
  const resolved = useRef(onResolved), reset = useRef(onReset)
  resolved.current = onResolved; reset.current = onReset
  const [error, setError] = useState(''), [loading, setLoading] = useState(false), [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let cancelled = false
    reset.current(); setError('')
    const raw = new URLSearchParams(location.search).get('courseId')
    if (raw === null) { setLoading(false); return }
    const id = Number(raw)
    if (!/^[1-9]\d*$/.test(raw) || !Number.isSafeInteger(id)) { setError('课程通知链接无效'); setLoading(false); return }
    setLoading(true)
    api.courseCalendar.get(id).then(row => { if (!cancelled) resolved.current(row) }).catch(cause => {
      if (cancelled) return
      setError(cause instanceof ApiError && [404, 1900020001].includes(cause.code) ? '课程已删除或不存在'
        : cause instanceof ApiError && cause.code === 403 ? '当前账号无权查看该课程'
        : '课程详情加载失败，请重试')
    }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [location.search, location.key, attempt])
  return { error, loading, retry: () => setAttempt(value => value + 1) }
}
