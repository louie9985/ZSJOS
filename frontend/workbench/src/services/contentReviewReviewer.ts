import type { FormInstance } from 'antd'
import { ApiError } from './api'
import { submitContentReview } from './contentReviewErrors'
import type { ContentReviewSelection, ContentReviewSubmitContext } from './materialApi'

export function resolveDraftReviewer(context: ContentReviewSubmitContext, saved: ContentReviewSelection | null | undefined,
  allowPrevious: boolean): { selection?: ContentReviewSelection; warning?: string } {
  if (saved && saved.processDefinitionId !== context.processDefinitionId)
    return { warning: '审批流程版本已变化，请重新选择审批编导' }
  const id = saved?.directorUserId ?? (allowPrevious ? context.previousDirectorUserId : undefined)
  if (id && !context.candidates.some(candidate => candidate.id === id))
    return { warning: '原选编导已不可用，请重新选择审批编导' }
  return { selection: id ? { directorUserId: id, processDefinitionId: context.processDefinitionId } : undefined }
}

/** Save remains optional; only a submit action validates the currently loaded selection. */
export function requireContentReviewReviewer(form: FormInstance): ContentReviewSelection {
  const selection = form.getFieldValue('draftReviewerSelection') as ContentReviewSelection | undefined
  if (selection && form.getFieldValue('_reviewerReady') === true) return selection
  const errors = ['请选择可用的审批编导，候选加载失败时请重试']
  form.setFields([{ name: ['draftReviewerSelection'], errors }])
  form.scrollToField('draftReviewerSelection', { block: 'center' })
  throw { errorFields: [{ name: ['draftReviewerSelection'], errors }] }
}

export async function submitWithFormReviewer(form: FormInstance, id: number, version: number,
  selection: ContentReviewSelection) {
  try { await submitContentReview(id, version, undefined, selection) }
  catch (error) {
    if (error instanceof ApiError && [1900012141, 1900012143].includes(error.code)) {
      form.setFieldValue('draftReviewerSelection', null)
      form.setFieldValue('_reviewerReady', false)
      form.setFieldValue('_reviewerReload', (form.getFieldValue('_reviewerReload') || 0) + 1)
      form.setFields([{ name: ['draftReviewerSelection'], errors: [error.message] }])
      form.scrollToField('draftReviewerSelection', { block: 'center' })
    }
    throw error
  }
}
