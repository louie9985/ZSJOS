import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import StageDurationTag, { stageDurationDescription } from './StageDurationTag'

const value = { sStageEnteredAt: Date.parse('2026-10-20T23:59:00+08:00'),
  sStageDurationAsOf: Date.parse('2026-10-21T00:01:00+08:00'), sStageDurationDays: 1, sStageTimingSource: 'RECORDED' }
describe('frozen account stage duration', () => {
  it('renders server days including zero and explicit Shanghai cutoff', () => {
    expect(renderToStaticMarkup(<StageDurationTag value={value} />)).toContain('已维持 1 天')
    expect(renderToStaticMarkup(<StageDurationTag value={{ ...value, sStageDurationDays: 0 }} />)).toContain('已维持 0 天')
    expect(stageDurationDescription(value)).toContain('截至本轮提交时：2026-10-21 00:01:00')
    expect(stageDurationDescription(value, true)).toContain('截至预览时，正式提交时重新计算')
    expect(stageDurationDescription({ ...value, sStageTimingSource: 'HISTORY_RECONSTRUCTED' })).toContain('根据诊断历史恢复')
  })
  it('keeps missing or invalid historical timing unknown', () => {
    for (const bad of [{}, { ...value, sStageDurationDays: -1 }, { ...value, sStageDurationDays: '1' },
      { ...value, sStageEnteredAt: value.sStageDurationAsOf + 1 }, { ...value, sStageTimingSource: undefined }]) {
      expect(renderToStaticMarkup(<StageDurationTag value={bad} />)).toContain('阶段时长未记录')
    }
  })
})
