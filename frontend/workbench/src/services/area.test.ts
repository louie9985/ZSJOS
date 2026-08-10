import { describe, expect, it } from 'vitest'
import { buildLeadAreaOptions, normalizeLeadAreaPath, OTHER_AREA_CODE, resolveLeadAreaPath } from './area'

describe('buildLeadAreaOptions', () => {
  it('uses only database-provided province and city choices', () => {
    const options = buildLeadAreaOptions([{
      id: 11,
      name: '北京市',
      selectionCode: '110000',
      leafSelectable: false,
      children: [
        { id: 1101, name: '北京市', selectionCode: '110100', leafSelectable: false },
        { id: 9001, name: '其他', selectionCode: OTHER_AREA_CODE, leafSelectable: false }
      ]
    }])
    expect(options[0]).toMatchObject({ label: '北京市', value: '110000', disabled: false })
    expect(options[0].children?.map(item => item.value)).toEqual(['110100', OTHER_AREA_CODE])
  })

  it('allows configured province leaves and maps them to an internal OTHER city', () => {
    const options = buildLeadAreaOptions([
      { id: 810000, name: '香港特别行政区', selectionCode: '810000', leafSelectable: true }
    ])
    expect(options[0]).toEqual({
      label: '香港特别行政区', value: '810000', disabled: false, children: undefined
    })
    expect(normalizeLeadAreaPath(['810000'])).toEqual(['810000', OTHER_AREA_CODE])
  })

  it('disables an empty province unless administrators allow direct selection', () => {
    const options = buildLeadAreaOptions([
      { id: 830000, name: '未配置地区', selectionCode: '830000', leafSelectable: false }
    ])
    expect(options[0].disabled).toBe(true)
  })

  it('resolves stored OTHER codes through database-provided Chinese nodes', () => {
    const areas = [{
      id: 9000, name: '其他地区', selectionCode: OTHER_AREA_CODE, leafSelectable: false,
      children: [{ id: 9001, name: '其他城市', selectionCode: OTHER_AREA_CODE, leafSelectable: false }]
    }]
    expect(resolveLeadAreaPath(areas, OTHER_AREA_CODE, OTHER_AREA_CODE, '其他地区', '其他城市'))
      .toEqual([OTHER_AREA_CODE, OTHER_AREA_CODE])
    expect(buildLeadAreaOptions(areas)[0].label).toBe('其他地区')
  })

  it('uses snapshot names to disambiguate repeated selection codes', () => {
    const areas = [
      { id: 1, name: '境内其他', selectionCode: OTHER_AREA_CODE, leafSelectable: true },
      { id: 2, name: '海外', selectionCode: OTHER_AREA_CODE, leafSelectable: true }
    ]
    expect(resolveLeadAreaPath(areas, OTHER_AREA_CODE, OTHER_AREA_CODE, '海外', ''))
      .toEqual([OTHER_AREA_CODE])
  })

  it('does not return raw codes when the enabled area tree cannot resolve them', () => {
    expect(resolveLeadAreaPath([], OTHER_AREA_CODE, OTHER_AREA_CODE, '其他', '其他')).toEqual([])
  })
})
