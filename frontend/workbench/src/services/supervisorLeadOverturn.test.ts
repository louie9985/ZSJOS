import { readFileSync } from 'node:fs'
import ts from 'typescript'
import { describe, expect, it, vi } from 'vitest'

describe('Admin compatibility with additive supervisor actions', () => {
  it('retains detail response and existing appeal transport', async () => {
    const response = { id: 1, leadNo: 'KZ-TEST', availableActions: [{ code: 'SUPERVISOR_OVERTURN_VALID', enabled: true, qualificationToken: 'token' }] }
    const request = { get: vi.fn(async () => response), put: vi.fn(async () => true) }
    const load = (file: string) => {
      const source = readFileSync(file, 'utf8')
      const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
      const exports: Record<string, (...args: unknown[]) => Promise<unknown>> = {}
      new Function('require', 'exports', js)(() => ({ default: request }), exports)
      return exports
    }
    const lead = load('../admin/src/api/zsjos/leadManagement/index.ts')
    expect(await lead.getLead(1)).toBe(response)
    expect(request.get).toHaveBeenCalledWith({ url: '/zsjos/lead/get', params: { id: 1 } })
    const commands = load('../admin/src/api/zsjos/workbenchMenus.ts')
    const data = { taskId: 'task', reason: '复核', attachments: [], idempotencyKey: 'key' }
    await commands.decideAppeal(2, 'overturn', data)
    expect(request.put).toHaveBeenCalledWith({ url: '/zsjos/lead/appeal/2/overturn', data })
    const page = readFileSync('../admin/src/views/zsjos/lead/index.vue', 'utf8')
    expect(page).not.toContain('SUPERVISOR_OVERTURN_VALID')
    expect(page).not.toContain('overturn-valid')
  })
})
