import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { DICT_TYPE } from '../constants'

const pageSource = readFileSync(new URL('./EamAssetPage.tsx', import.meta.url), 'utf8')

describe('EAM employee asset workbench contract', () => {
  it('uses server dictionaries for asset statuses and system-dictionary custom fields', () => {
    expect(DICT_TYPE.EAM_ASSET_STATUS).toBe('eam_asset_status')
    expect(pageSource).toContain('useDict(DICT_TYPE.EAM_ASSET_STATUS)')
    expect(pageSource).toContain("field.optionSource === 'SYSTEM_DICT'")
    expect(pageSource).toContain('dictionary.reload()')
    expect(pageSource).not.toContain("const ASSET_STATUS = ['闲置'")
  })

  it('honors collection visibility and required flags', () => {
    expect(pageSource).toContain('field.collectionVisible !== false')
    expect(pageSource).toContain('field.collectionRequired ?? field.required')
  })

  it('allows repair for a holding only while it is actively held', () => {
    expect(pageSource).toContain('row.holdingId ? row.status === 1')
  })

  it('shows a read-only stock preview before the demand is submitted', () => {
    expect(pageSource).toContain('api.eam.previewStockCandidates')
    expect(pageSource).toContain('审批后将再次确认')
  })
})
