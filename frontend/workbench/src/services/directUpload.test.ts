import axios from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, uploadDirectFile } from './api'

describe('direct upload', () => {
  afterEach(() => vi.restoreAllMocks())

  it('waits for raw PUT before completing the business upload', async () => {
    const calls: string[] = []
    vi.spyOn(http, 'post')
      .mockImplementationOnce(async () => {
        calls.push('init')
        return {
          data: {
            code: 0,
            data: {
              uploadToken: 'secret-token',
              uploadUrl: 'https://objects.test/upload',
              uploadHeaders: { 'Content-Type': 'video/mp4' },
              expiresAt: '2026-09-08T12:00:00',
            },
          },
        }
      })
      .mockImplementationOnce(async (_url, data) => {
        calls.push(`complete:${(data as { uploadToken: string }).uploadToken}`)
        return { data: { code: 0, data: { fileId: 9 } } }
      })
    const rawPut = vi.spyOn(axios, 'put').mockImplementation(async (_url, _file, config) => {
      calls.push('put')
      expect(config?.timeout).toBe(0)
      expect(config?.withCredentials).toBe(false)
      expect(config?.headers).toEqual({ 'Content-Type': 'video/mp4' })
      return { data: undefined }
    })

    const result = await uploadDirectFile<{ fileId: number }>(
      '/upload/init',
      '/upload/complete',
      new File(['video'], 'video.mp4', { type: 'video/mp4' }),
    )

    expect(result).toEqual({ fileId: 9 })
    expect(calls).toEqual(['init', 'put', 'complete:secret-token'])
    expect(rawPut).toHaveBeenCalledWith(
      'https://objects.test/upload',
      expect.any(File),
      expect.objectContaining({ timeout: 0, withCredentials: false }),
    )
  })

  it('does not complete when object storage upload fails', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValueOnce({
      data: {
        code: 0,
        data: {
          uploadToken: 'secret-token',
          uploadUrl: 'https://objects.test/upload',
          uploadHeaders: {},
          expiresAt: '2026-09-08T12:00:00',
        },
      },
    })
    vi.spyOn(axios, 'put').mockRejectedValueOnce(new Error('upload failed'))

    await expect(uploadDirectFile(
      '/upload/init',
      '/upload/complete',
      new File(['x'], 'image.png', { type: 'image/png' }),
    )).rejects.toThrow('文件传输失败：upload failed')

    expect(post).toHaveBeenCalledTimes(1)
  })
})
