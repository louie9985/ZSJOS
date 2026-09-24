import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type DictData } from './api'
import { DICT_TYPE } from '../constants'

type Data = { methods: DictData[]; results: DictData[]; categories: DictData[]; quickNotes: DictData[] }
const empty: Data = { methods: [], results: [], categories: [], quickNotes: [] }

export function useFollowUpDictionaries(leadId: number, active = true) {
  const version = useRef(0)
  const [state, setState] = useState<{ data: Data; loading: boolean; error: string; leadId?: number }>({ data: empty, loading: true, error: '' })
  const reload = useCallback(async () => {
    if (!active) return
    const request = ++version.current
    setState(current => ({ data: current.leadId === leadId ? current.data : empty, leadId, loading: true, error: '' }))
    try {
      const [methods, results, categories, quickNotes] = await Promise.all([
        api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_METHOD), api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_RESULT),
        api.dictDataByType(DICT_TYPE.LEAD_CATEGORY), api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_QUICK_NOTE),
      ])
      if (request === version.current) setState({ data: { methods, results, categories, quickNotes }, leadId, loading: false, error: '' })
    } catch (cause) {
      if (request === version.current) setState(current => ({ ...current, loading: false, error: cause instanceof Error ? cause.message : '跟进字典加载失败，请重试' }))
    }
  }, [active, leadId])
  useEffect(() => { void reload(); return () => { version.current++ } }, [reload])
  const current = state.leadId === leadId
  const data = current ? state.data : empty
  const loading = active && (!current || state.loading)
  const error = current ? state.error : ''
  return { ...data, loading, error, reload, blocked: !active || loading || Boolean(error) || !data.methods.length || !data.results.length }
}
