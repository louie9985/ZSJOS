import { createContext, useContext, useMemo } from 'react'
import { http, unwrap } from './api'

export interface MetricPageScope { contextId: string; operand: string; source: string }
export const MetricPageScopeContext = createContext<MetricPageScope | undefined>(undefined)
export const useMetricPageScope = () => useContext(MetricPageScopeContext)

// Only the owning page's list queries are replaced. Detail and command APIs retain their own authorization.
const methods: Record<string, string[]> = {
  lead: ['allLeadPage'],
  order: ['managementSalesOrderPage', 'managementSalesOrderCursor'],
  student: ['myStudentServices'],
  lead_appeal: ['leadAppealInboxPage', 'leadAppealInboxCursor'],
  duplicate_review: ['duplicateReviewPage'],
  registration: ['registrationPoolPage'],
  media_student: ['page'],
  subordinate_sales: ['subordinateSalesPage'],
  education_team: ['page'],
  cashback: ['cashbacks'],
  withdrawal: ['withdrawals'],
  business_todo: ['businessTaskList'],
  bpm_todo: ['bpmTaskPage'],
}

export function useMetricSourceApi<T extends object>(source: string, original: T): T {
  const scope = useContext(MetricPageScopeContext)
  return useMemo(() => {
    if (!scope || scope.source !== source) return original
    return new Proxy(original, {
      get(target, key, receiver) {
        if (typeof key !== 'string' || !methods[source]?.includes(key)) return Reflect.get(target, key, receiver)
        return async (...args: unknown[]) => {
          const query = [...args].reverse().find(value => value !== null && typeof value === 'object' && !Array.isArray(value)) ?? {}
          if (source === 'bpm_todo' || source === 'business_todo') {
            unwrap(await http.get('/zsjos/home-metric/context', { params: { id: scope.contextId } }))
            const originalMethod = Reflect.get(target, key, receiver) as (...values: unknown[]) => Promise<unknown>
            const result = source === 'bpm_todo'
              ? await originalMethod('todo', { ...query, excludeProcessDefinitionKeys: undefined })
              : await originalMethod({ ...query, status: 'pending', readScope: 'SELF', targetUserId: undefined })
            unwrap(await http.get('/zsjos/home-metric/context', { params: { id: scope.contextId } }))
            return result
          }
          return unwrap(await http.post('/zsjos/home-metric/source-page', {
            ...scope, cursor: key.toLowerCase().includes('cursor'), query,
          }))
        }
      },
    })
  }, [scope, source, original])
}
