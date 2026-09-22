import dayjs from 'dayjs'
import type { FormInstance } from 'antd'
import { ApiError } from './api'
import { contentReviewApi, type ContentReviewBatch } from './materialApi'

export function httpsLinkError(value: unknown): string | undefined {
  if (value == null || String(value).trim() === '') return
  const text = String(value).trim()
  try {
    const url = new URL(text)
    if (!/^https:\/\//i.test(text) || url.protocol !== 'https:' || !url.hostname || /[\s\\]/.test(text)) throw new Error()
  } catch { return '请填写包含有效域名的完整 HTTPS 地址' }
}

export function plannedTimeError(value: unknown, now = Date.now()): string | undefined {
  if (!value) return '请选择预计发布时间'
  const time = dayjs(value as string).valueOf()
  return !Number.isFinite(time) || time < now ? '预计发布时间不能早于当前时间，请重新选择' : undefined
}

export function locateContentError(form: FormInstance, error: unknown): void {
  if (!(error instanceof ApiError) || !error.details || typeof error.details !== 'object') return
  const path = (error.details as { fieldPath?: unknown }).fieldPath
  if (typeof path !== 'string' || !/^(works|accountIds|accountSnapshots|platformUrl|publishedAt)(\[\d+\]|\.[a-zA-Z0-9_]+)*$/.test(path)) return
  const name = path.replace(/\[(\d+)\]/g, '.$1').split('.').map(part => /^\d+$/.test(part) ? Number(part) : part)
  const leaf = name[name.length - 1]
  if (leaf === 'coverFileId' || leaf === 'coverSnapshotJson') name[name.length - 1] = 'coverItems'
  if (leaf === 'deliverableSnapshotJson') name[name.length - 1] = 'attachmentItems'
  form.setFields([{ name, errors: [error.message] }])
  form.scrollToField(name, { block: 'center', focus: true })
}

export const requestMayHaveSucceeded = (error: unknown) => error instanceof ApiError
  && (error.code === 0 || error.code >= 500 && error.code < 600)

export type ContentSavePhase = 'prepare' | 'save' | 'refresh' | 'submit'
export const contentResultUncertain = (error: unknown, phase: ContentSavePhase) =>
  phase === 'refresh' || (phase === 'save' || phase === 'submit') && requestMayHaveSucceeded(error)

export const approvalHasStarted = (batch: ContentReviewBatch) => Boolean(batch.processInstanceId)
  || ['DIRECTOR_REVIEW', 'FINAL_REVIEW', 'COMPLETED', 'PUBLISHED', 'NEED_MODIFY', 'REJECTED'].includes(batch.status)

/** A lost submit response is resolved using the authorized batch query, never by creating a new round. */
export async function submitContentReview(id: number, version: number,
  transport = { submit: contentReviewApi.submit, get: contentReviewApi.get }): Promise<void> {
  try { await transport.submit(id, version) }
  catch (error) {
    if (!requestMayHaveSucceeded(error)) throw error
    let current: ContentReviewBatch
    try { current = await transport.get(id) }
    catch { throw new ApiError(0, '暂未确认审批提交结果。草稿已保存，请刷新该批次确认状态后再操作') }
    if (approvalHasStarted(current)) return
    if (current.status === 'DRAFT') throw new ApiError(503, '草稿已保存，当前查询仍为草稿；请刷新确认后重试提交')
    throw new ApiError(0, '批次状态已变化，请刷新该批次确认提交结果')
  }
}

export function contentSaveError(error: unknown, saved: boolean, phase: ContentSavePhase = 'submit'): string {
  const reason = error instanceof Error ? error.message : '请求失败，请重试'
  if (phase === 'refresh') return `草稿已保存，但未能读取最新版本，尚未发起审批：${reason}。请查询草稿状态后继续`
  if (contentResultUncertain(error, phase)) return `暂未确认${phase === 'save' ? '保存' : '审批提交'}结果：${reason}。请保留填写内容并查询草稿状态后再操作`
  return `${saved ? '草稿已保存，但审批未提交' : '本次修改未保存'}：${reason}`
}
