import type { AreaNode } from './api'

export const OTHER_AREA_CODE = 'OTHER'

export type LeadAreaOption = {
  label: string
  value: string
  disabled?: boolean
  children?: LeadAreaOption[]
}

export const buildLeadAreaOptions = (areas: AreaNode[]): LeadAreaOption[] => areas.map(province => {
  const children = province.children || []
  return {
    label: province.name,
    value: province.selectionCode,
    disabled: children.length === 0 && !province.leafSelectable,
    children: children.length
      ? children.map(city => ({ label: city.name, value: city.selectionCode }))
      : undefined
  }
})

export const normalizeLeadAreaPath = (path: string[]): [string, string] => {
  if (path.length === 1) return [path[0], OTHER_AREA_CODE]
  return [path[0], path[1]]
}
