import { expect, it } from 'vitest'
import { APP_ROUTES, MOBILE_RENDERABLE_APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'
import { filterRenderableMenus } from '../services/menu'

it('offers the same supported native routes to authorized Mobile and PC menus', () => {
  expect([...MOBILE_RENDERABLE_APP_ROUTES]).toEqual([...RENDERABLE_APP_ROUTES])
  expect(MOBILE_RENDERABLE_APP_ROUTES.has(APP_ROUTES.CONTENT_PRODUCTION)).toBe(true)
  expect(MOBILE_RENDERABLE_APP_ROUTES.has(APP_ROUTES.CONTENT_REVIEW)).toBe(true)
  expect(filterRenderableMenus([], MOBILE_RENDERABLE_APP_ROUTES)).toEqual([])
})
