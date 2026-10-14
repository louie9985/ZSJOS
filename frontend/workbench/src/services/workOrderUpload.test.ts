import { beforeEach, describe, expect, it, vi } from 'vitest'
import { workOrderApi } from './workOrderApi'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('./api', () => ({ http: { post }, unwrap: (value: unknown) => value }))

const fileOfSize = (size: number) => {
  const file = new File(['x'], '附件.bin')
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('work order upload boundary', () => {
  beforeEach(() => { post.mockReset() })

  it.each([0, 100 * 1024 * 1024 + 1])('rejects invalid size %s without a request', async size => {
    await expect(workOrderApi.upload(fileOfSize(size))).rejects.toThrow('附件不能为空且不能超过100MB')
    expect(post).not.toHaveBeenCalled()
  })

  it('allows exactly 100MB', async () => {
    post.mockResolvedValue({ id: 1, name: '附件.bin' })
    await expect(workOrderApi.upload(fileOfSize(100 * 1024 * 1024))).resolves.toEqual({ id: 1, name: '附件.bin' })
    expect(post).toHaveBeenCalledOnce()
  })

  it('preserves an actionable busy error for retry', async () => {
    post.mockRejectedValue(new Error('工单附件上传繁忙，请稍后重试'))
    await expect(workOrderApi.upload(fileOfSize(1))).rejects.toThrow('工单附件上传繁忙，请稍后重试')
    expect(post).toHaveBeenCalledOnce()
  })
})
