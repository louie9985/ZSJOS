import request from '@/config/axios'

export interface AdvancedFilterCondition { fieldKey: string; operator: string; value?: unknown; valueFrom?: unknown; valueTo?: unknown }
export interface AdvancedFilterGroup { logic: 'AND' | 'OR'; conditions: AdvancedFilterCondition[]; groups: AdvancedFilterGroup[] }
export interface AdvancedFilterField { fieldKey: string; group: string; label: string; valueType: 'text' | 'select' | 'number' | 'date'; operators: string[]; optionSource?: string; options: Array<{ value: string; label: string }> }
export const getCatalog = (scene: 'lead' | 'order') => request.get<{ fields: AdvancedFilterField[] }>({ url: '/zsjos/advanced-filter/catalog', params: { scene } })
