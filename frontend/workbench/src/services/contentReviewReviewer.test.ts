import { describe, expect, it, vi } from 'vitest'
import { requireContentReviewReviewer, resolveDraftReviewer, submitWithFormReviewer } from './contentReviewReviewer'
import { ApiError } from './api'
import { contentReviewApi, type ContentReviewSubmitContext } from './materialApi'
import type { FormInstance } from 'antd'

const context: ContentReviewSubmitContext = { processDefinitionId: 'v2', processDefinitionVersion: 2,
  directorTaskKey: 'director', roleIds: [7], candidates: [{ id: 20, nickname: '编导甲' }], previousDirectorUserId: 20 }
const choice = { directorUserId: 20, processDefinitionId: 'v2' }
function formFixture(values: Record<string, unknown> = {}) {
  return { getFieldValue: (name: string) => values[name], setFieldValue: (name: string, value: unknown) => { values[name] = value },
    setFields: vi.fn(), scrollToField: vi.fn() } as unknown as FormInstance
}
describe('draft reviewer selection', () => {
  it('leaves new forms unselected and restores an eligible previous reviewer only when requested', () => {
    expect(resolveDraftReviewer(context, undefined, false).selection).toBeUndefined()
    expect(resolveDraftReviewer(context, undefined, true).selection).toEqual(choice)
  })
  it('restores saved intent and clears unavailable or outdated choices', () => {
    expect(resolveDraftReviewer(context, choice, false).selection).toEqual(choice)
    expect(resolveDraftReviewer(context, { ...choice, directorUserId: 99 }, false).warning).toContain('不可用')
    expect(resolveDraftReviewer(context, { ...choice, processDefinitionId: 'v1' }, false).warning).toContain('版本已变化')
    expect(resolveDraftReviewer({ ...context, candidates: [] }, undefined, true).selection).toBeUndefined()
  })
  it('blocks submission until selection and candidate loading are valid', () => {
    for (const values of [{}, { draftReviewerSelection: choice, _reviewerReady: false }]) {
      const form = formFixture(values)
      expect(() => requireContentReviewReviewer(form)).toThrow()
      expect(form.setFields).toHaveBeenCalled()
    }
    expect(requireContentReviewReviewer(formFixture({ draftReviewerSelection: choice, _reviewerReady: true }))).toEqual(choice)
  })
  it('submits directly and refreshes the field after a definition change without retrying submit', async () => {
    const submit = vi.spyOn(contentReviewApi, 'submit').mockRejectedValueOnce(new ApiError(1900012141, '流程版本已变化'))
    const values = { draftReviewerSelection: choice, _reviewerReady: true } as Record<string, unknown>
    await expect(submitWithFormReviewer(formFixture(values), 10, 3, choice)).rejects.toThrow('流程版本')
    expect(submit).toHaveBeenCalledExactlyOnceWith(10, 3, choice)
    expect(values).toMatchObject({ draftReviewerSelection: null, _reviewerReady: false, _reviewerReload: 1 })
    submit.mockRestore()
  })
})
