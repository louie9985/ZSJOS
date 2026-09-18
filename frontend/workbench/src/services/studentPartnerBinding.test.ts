import { describe, expect, it, vi } from 'vitest'
vi.mock('./api', () => ({ http: { post: vi.fn(), get: vi.fn() }, unwrap: (response: { data: unknown }) => response.data }))
import { http } from './api'
import { managementApi } from './managementApi'

describe('existing student partner binding wire contract', () => {
  it('uses query parameters required by the existing controller', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: true })
    const params = { partnerId: 12, studentPersonId: 34, reason: '人工核对' }
    expect(await managementApi.bindPartnerStudent(params)).toBe(true)
    expect(http.post).toHaveBeenCalledWith('/zsjos/partner-student-link/bind', null, { params })
  })
  it('preserves binding conflicts for the user to correct', async () => {
    const conflict = new Error('兼职账号或学员已绑定其他身份')
    vi.mocked(http.post).mockRejectedValue(conflict)
    await expect(managementApi.bindPartnerStudent({ partnerId: 12, studentPersonId: 34 })).rejects.toBe(conflict)
  })
})
