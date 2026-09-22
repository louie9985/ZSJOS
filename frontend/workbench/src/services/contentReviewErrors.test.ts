import type { FormInstance } from 'antd'
import { describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { ApiError, normalizeRequestError, unwrap } from './api'
import { httpsLinkError, plannedTimeError, submitContentReview, contentResultUncertain, contentSaveError, locateContentError } from './contentReviewErrors'

describe('content review error guidance', () => {
  it('preserves structured business errors through both response paths', () => {
    const payload = { code: 1900012100, msg: '第 2 件作品：引流资料链接无效', data: { fieldPath: 'works[1].leadResourceUrl', workIndex: 1 } }
    expect(() => unwrap({ data: payload })).toThrow(payload.msg)
    try { unwrap({ data: payload }) } catch (error) { expect(error).toMatchObject({ code: payload.code, details: payload.data }) }
    const failure = new AxiosError('Request failed')
    Object.assign(failure, { response: { status: 400, data: payload } })
    expect(normalizeRequestError(failure)).toMatchObject({ code: payload.code, message: payload.msg, details: payload.data })
  })

  it('keeps server outages distinct from explicit business rejections', () => {
    const failure = new AxiosError('internal server detail')
    Object.assign(failure, { response: { status: 502, data: { msg: 'private detail' } } })
    const error = normalizeRequestError(failure)
    expect(error).toMatchObject({ code: 502, message: '服务器连接错误，请联系管理员' })
    expect(contentResultUncertain(error, 'submit')).toBe(true)
  })

  it('does not lock the draft when upload preparation or a local check fails', () => {
    expect(contentResultUncertain(new Error('上传失败'), 'prepare')).toBe(false)
    expect(contentResultUncertain(new ApiError(0, '连接中断'), 'prepare')).toBe(false)
    expect(contentResultUncertain(new ApiError(0, '连接中断'), 'save')).toBe(true)
    expect(contentSaveError(new Error('上传失败'), false, 'prepare')).toBe('本次修改未保存：上传失败')
  })

  it('distinguishes a committed save with failed refresh from a failed save', () => {
    const failure = new ApiError(403, '无权查看')
    expect(contentResultUncertain(failure, 'refresh')).toBe(true)
    expect(contentSaveError(failure, true, 'refresh')).toContain('草稿已保存，但未能读取最新版本，尚未发起审批')
    expect(contentSaveError(failure, true, 'submit')).toBe('草稿已保存，但审批未提交：无权查看')
  })

  it('targets the specified work and attachment control, ignoring unknown paths', () => {
    const form = { setFields: vi.fn(), scrollToField: vi.fn() }
    locateContentError(form as unknown as FormInstance, new ApiError(1900012118, '第 2 件作品：附件不可用', { fieldPath: 'works[1].deliverableSnapshotJson', workIndex: 1 }))
    expect(form.setFields).toHaveBeenCalledWith([{ name: ['works', 1, 'attachmentItems'], errors: ['第 2 件作品：附件不可用'] }])
    locateContentError(form as unknown as FormInstance, new ApiError(1, 'invalid', { fieldPath: '__proto__.test' }))
    expect(form.setFields).toHaveBeenCalledOnce()
  })

  it.each([
    [{ status: 'DRAFT' }, '当前查询仍为草稿'],
    [{ status: 'CANCELLED' }, '批次状态已变化'],
    [null, '暂未确认审批提交结果'],
  ])('keeps an unresolved submit blocked for status %j', async (current, message) => {
    const submit = vi.fn().mockRejectedValue(new ApiError(504, 'timeout'))
    const get = current ? vi.fn().mockResolvedValue(current) : vi.fn().mockRejectedValue(new Error('offline'))
    await expect(submitContentReview(8, 2, { submit, get })).rejects.toThrow(String(message))
    expect(submit).toHaveBeenCalledOnce()
  })

  it('requires a complete HTTPS link while allowing an omitted optional link', () => {
    expect(httpsLinkError(undefined)).toBeUndefined()
    expect(httpsLinkError('example.com/file')).toContain('HTTPS')
    expect(httpsLinkError('http://example.com/file')).toContain('HTTPS')
    expect(httpsLinkError('https://example.com/file')).toBeUndefined()
  })

  it('uses the validation call time for planned publication', () => {
    expect(plannedTimeError('2026-09-22T10:00:00+08:00', new Date('2026-09-22T09:00:00+08:00').getTime())).toBeUndefined()
    expect(plannedTimeError('2026-09-22T08:00:00+08:00', new Date('2026-09-22T09:00:00+08:00').getTime())).toContain('不能早于')
  })

  it('queries the batch after an uncertain submit instead of creating another round', async () => {
    const submit = vi.fn().mockRejectedValue(new ApiError(0, 'network timeout'))
    const get = vi.fn().mockResolvedValue({ status: 'DIRECTOR_REVIEW', processInstanceId: 'p-1' })
    await expect(submitContentReview(8, 2, { submit, get })).resolves.toBeUndefined()
    expect(submit).toHaveBeenCalledOnce()
    expect(get).toHaveBeenCalledWith(8)
  })

  it('keeps a confirmed business rejection actionable', async () => {
    const submit = vi.fn().mockRejectedValue(new ApiError(1_900_012_100, '链接格式不正确'))
    const get = vi.fn()
    await expect(submitContentReview(8, 2, { submit, get })).rejects.toMatchObject({ code: 1_900_012_100 })
    expect(get).not.toHaveBeenCalled()
  })
})
