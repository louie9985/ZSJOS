import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, http } from './api'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'
import { buildMenuTree } from './api'
import { filterRenderableMenus } from './menu'

afterEach(() => vi.restoreAllMocks())
describe('positioning interview template consumer', () => {
  it('lists, copies, saves and publishes the same new template contract as Admin', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: [] } })
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { code: 0, data: true } })
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: { code: 0, data: true } })
    await api.directorConfig.templates(false)
    await api.directorConfig.copyDraft(false, 9, 2)
    const command = { versionId: 10, version: 2, name: '大纲', defaultTemplate: true, fields: [] }
    await api.directorConfig.saveDraft(false, 9, command)
    await api.directorConfig.publish(false, 9, { versionId: 10, version: 3 })
    expect(get).toHaveBeenCalledWith('/zsjos/positioning-interview-template/list')
    expect(post).toHaveBeenNthCalledWith(1, '/zsjos/positioning-interview-template/9/draft/copy', null, { params: { version: 2 } })
    expect(put).toHaveBeenCalledWith('/zsjos/positioning-interview-template/9/draft', command)
    expect(post).toHaveBeenNthCalledWith(2, '/zsjos/positioning-interview-template/9/publish', { versionId: 10, version: 3 })
  })
  it('preserves the independent positioning card API', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { code: 0, data: [] } })
    await api.directorConfig.templates(true)
    expect(get).toHaveBeenCalledWith('/zsjos/positioning-template/list')
  })
  it('renders only a supplied server-authorized configuration menu', () => {
    const tree = buildMenuTree([{ id: 73483, parentId: 0, name: '定位访谈大纲配置', path: APP_ROUTES.POSITIONING_INTERVIEW_TEMPLATE, component: 'zsjos/directorTemplate/index', visible: true, keepAlive: true }])
    expect(filterRenderableMenus(tree, RENDERABLE_APP_ROUTES)).toHaveLength(1)
    expect(filterRenderableMenus([], RENDERABLE_APP_ROUTES)).toEqual([])
  })
})
