import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import { readOrderGifts, giftSummary, giftOptions } from './orderGifts'

const old = { code: 'old', name: '原礼品', path: ['原分类', '原礼品'], snapshotAt: '2026-01-01' }
describe('order gift history', () => {
  it('reads both stored shapes and prefers server snapshots without re-resolving labels', () => {
    for (const giftItems of [JSON.stringify([old]), JSON.stringify([JSON.stringify(old)])]) {
      expect(readOrderGifts({ giftItems })).toEqual([old])
      expect(giftSummary({ giftItems })).toBe('原礼品')
    }
    expect(readOrderGifts({ giftItems: 'bad', giftItemSnapshots: [old] })).toEqual([old])
    expect(readOrderGifts()).toEqual([])
    expect(readOrderGifts({ giftItemCodes: ['old'], giftItemSnapshots: [old] })).toEqual([old])
    expect(() => readOrderGifts({ giftItemCodes: [], giftItemSnapshots: [old] })).toThrow('不一致')
  })
  it('blocks damaged historical selection instead of clearing it', () => {
    for (const giftItems of ['bad', '{}', '["code"]', '[{"code":"x","name":2}]']) {
      expect(() => readOrderGifts({ giftItems })).toThrow()
      expect(giftSummary({ giftItems })).toBe('历史礼品信息无法读取')
    }
    expect(() => readOrderGifts({ giftItemsInvalid: true })).toThrow('联系管理员')
    expect(giftSummary({ giftItemSnapshots: [{ code: 'legacy' }] })).toBe('历史名称未记录')
  })
  it('retains historical selection labels while offering only server-returned new options', () => {
    expect(giftOptions([{ value: 'old', title: '现名称' }, { value: 'new', title: '新选项' }], [old])).toEqual([
      { value: 'old', title: '原礼品（历史选择）' }, { value: 'new', title: '新选项' }
    ])
    expect(giftOptions([], [old])).toEqual([{ value: 'old', title: '原礼品（历史选择）' }])
  })
  it('Admin consumer renders both formats, missing labels and errors without raw JSON', () => {
    const source = readFileSync(new URL('../../../admin/src/api/zsjos/gift/index.ts', import.meta.url), 'utf8')
    const exports: Record<string, unknown> = {}
    const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
    runInNewContext(js, { exports, require: () => ({ default: {} }) })
    const format = exports.formatGiftPurchase as (row: unknown) => string
    expect(format({ giftItemsJson: JSON.stringify([old]) })).toBe('原礼品')
    expect(format({ giftItemsJson: JSON.stringify([JSON.stringify(old)]) })).toBe('原礼品')
    expect(format({ giftItemSnapshots: [{ code: 'legacy' }] })).toBe('历史名称未记录')
    expect(format({ giftItemsJson: 'bad' })).toBe('历史礼品信息无法读取')
  })
})
