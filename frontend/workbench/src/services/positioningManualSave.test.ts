import { describe, expect, it, vi } from 'vitest'
import { PositioningManualSave, type PositioningAttachmentValue } from './positioningManualSave'
const pending = (uid: string) => ({ uid, file: { name: uid } as File })
const options = () => ({
  values: { text: 'first', files: [pending('one'), pending('two')] },
  create: vi.fn().mockResolvedValue({ id: 19, version: 0 }),
  update: vi.fn().mockImplementation(async (draft: { id: number; version: number }) => ({ id: draft.id, version: draft.version + 1 })),
  upload: vi.fn().mockResolvedValueOnce({ id: 101 }).mockResolvedValue({ id: 102 }),
  onUploaded: vi.fn(),
})
describe('explicit positioning save', () => {
  it('does no writes before run; creates without pending files, uploads then links', async () => {
    const saver = new PositioningManualSave(), job = options()
    expect(job.create).not.toHaveBeenCalled()
    await saver.run(job)
    expect(job.create).toHaveBeenCalledWith({ text: 'first', files: [] })
    expect(job.update).toHaveBeenCalledWith({ id: 19, version: 0 }, { text: 'first', files: [101, 102] })
    expect(saver.draft).toEqual({ id: 19, version: 1 })
  })
  it('retries only failed uploads and retains the created draft', async () => {
    const saver = new PositioningManualSave(), job = options()
    let files: PositioningAttachmentValue[] = job.values.files
    job.onUploaded.mockImplementation((_key, items) => { files = items })
    job.upload.mockReset().mockResolvedValueOnce({ id: 101 }).mockRejectedValueOnce(new Error('network')).mockResolvedValue({ id: 102 })
    await expect(saver.run(job)).rejects.toThrow('network')
    expect(files[0]).toBe(101)
    expect(job.update).not.toHaveBeenCalled()
    await saver.run({ ...job, values: { ...job.values, files } })
    expect(job.create).toHaveBeenCalledTimes(1)
    expect(job.upload).toHaveBeenCalledTimes(3)
    expect(saver.draft?.version).toBe(1)
  })
  it('uses returned versions for subsequent edits and never recreates an existing card', async () => {
    const saver = new PositioningManualSave(), job = options()
    saver.draft = { id: 19, version: 4 }
    await saver.run({ ...job, values: { text: 'a' } })
    await saver.run({ ...job, values: { text: 'b' } })
    expect(job.create).not.toHaveBeenCalled()
    expect(job.update).toHaveBeenLastCalledWith({ id: 19, version: 5 }, { text: 'b' })
  })
  it('retains uploaded IDs when final linking conflicts; does not bypass version checks', async () => {
    const saver = new PositioningManualSave(), job = options()
    job.update.mockRejectedValue(new Error('version conflict'))
    await expect(saver.run(job)).rejects.toThrow('version conflict')
    expect(job.onUploaded).toHaveBeenLastCalledWith('files', [101, 102], expect.objectContaining({ id: 102, name: 'two' }))
    expect(saver.draft).toEqual({ id: 19, version: 0 })
    expect(saver.busy).toBe(false)
  })
  it('ignores a second save while the first is in flight', async () => {
    const saver = new PositioningManualSave(), job = options()
    let finish!: (value: { id: number; version: number }) => void
    job.create.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const first = saver.run(job)
    expect(await saver.run(job)).toBe(false)
    finish({ id: 19, version: 0 }); await first
    expect(job.create).toHaveBeenCalledTimes(1)
  })
})
