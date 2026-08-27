import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { APP_ROUTES, RENDERABLE_APP_ROUTES } from '../constants'

const routeHostSource = readFileSync(new URL('../layouts/RouteHost.tsx', import.meta.url), 'utf8')
const pageSource = readFileSync(new URL('./FeedbackPage.tsx', import.meta.url), 'utf8')

describe('feedback workbench route contract', () => {
  it('registers the server-owned public path and renders the feedback page', () => {
    expect(APP_ROUTES.FEEDBACK).toBe('/zsjos/feedback')
    expect(RENDERABLE_APP_ROUTES.has(APP_ROUTES.FEEDBACK)).toBe(true)
    expect(routeHostSource).toContain("import FeedbackPage from '../pages/FeedbackPage'")
    expect(routeHostSource).toContain('menu?.path === APP_ROUTES.FEEDBACK')
  })

  it('opens notification deep links only through the independent read permission', () => {
    expect(pageSource).toContain("searchParams.get('feedbackId')")
    expect(pageSource).toContain("hasPermission(permissions, 'zsjos:feedback:read')")
    expect(pageSource).toContain('disabled={!canRead}')
  })
})
