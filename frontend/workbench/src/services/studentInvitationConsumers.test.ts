// UTF-8. Execute existing client adapters with isolated transport to check shared wire contracts.
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import { describe, expect, it, vi } from 'vitest'

function client(path: string, request: object): Record<string, (...args: unknown[]) => unknown> {
  const exports = {}
  const source = ts.transpileModule(readFileSync(path, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  runInNewContext(source, { exports, require: () => ({ default: request }) })
  return exports
}

describe('existing invitation consumers', () => {
  it('Vue administration retains normal invitation/operator request and response shapes', async () => {
    const invitation = { id: 1, inviteCode: 'TEST1234', assignedOperatorUserId: 9, assignedOperatorName: '测试运营', expiresAt: 1900000000000 }
    const request = { get: vi.fn().mockResolvedValue({ list: [{ id: 9, nickname: '测试运营' }], total: 1 }), post: vi.fn().mockResolvedValue(invitation) }
    const api = client('../admin/src/api/zsjos/partner/index.ts', request)
    await api.getInvitationOperatorCandidates({ keyword: '测试', pageNo: 2 })
    expect(request.get).toHaveBeenCalledWith({ url: '/zsjos/partner-invitation/operator-candidates', params: { keyword: '测试', pageNo: 2, pageSize: 100 } })
    const body = { name: '测试账号', mobile: '13800000000', assignedOperatorUserId: 9, expiresAt: 1900000000000 }
    expect(await api.createInvitation(body)).toEqual(invitation)
    expect(request.post).toHaveBeenCalledWith({ url: '/zsjos/partner-invitation/create', data: body })
  })
  it('H5 activation still sends invitation credentials without an operator override', async () => {
    const request = { post: vi.fn().mockResolvedValue({ userId: 1 }) }
    const api = client('../h5/src/api/auth.ts', request)
    const body = { mobile: '13800000000', password: 'Synthetic123', confirmPassword: 'Synthetic123', inviteCode: 'TEST1234' }
    expect(await api.activate(body)).toEqual({ userId: 1 })
    expect(request.post).toHaveBeenCalledWith('/zsjos/auth/activate', { ...body, platform: 'MOBILE' })
    expect(request.post.mock.calls[0][1]).not.toHaveProperty('assignedOperatorUserId')
  })
})
