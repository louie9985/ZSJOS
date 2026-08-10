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

const selectNode = (nodes: AreaNode[], code?: string, name?: string) => {
  const codeMatches = code ? nodes.filter(node => node.selectionCode === code) : []
  if (name) {
    const namedCodeMatch = codeMatches.find(node => node.name === name)
    if (namedCodeMatch) return namedCodeMatch
    const namedMatch = nodes.find(node => node.name === name)
    if (namedMatch) return namedMatch
  }
  return codeMatches[0]
}

export const resolveLeadAreaPath = (
  areas: AreaNode[], provinceCode?: string, cityCode?: string, provinceName?: string, cityName?: string
): string[] => {
  const province = selectNode(areas, provinceCode, provinceName)
  if (!province) return []
  const children = province.children || []
  const city = selectNode(children, cityCode, cityName)
  if (city) return [province.selectionCode, city.selectionCode]
  return province.leafSelectable ? [province.selectionCode] : []
}
