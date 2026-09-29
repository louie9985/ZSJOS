import type { ReactNode } from 'react'
import type { WorkbenchMenu } from '../services/api'
import RetainedReviewRoute from './RetainedReviewRoutes'

export default function RetainedPageHost({ menus, activePath, openPaths, tabsEnabled, scopeKey, renderPage }: {
  menus: WorkbenchMenu[]
  activePath: string
  openPaths: readonly string[]
  tabsEnabled: boolean
  scopeKey: string
  renderPage: (menu: WorkbenchMenu) => ReactNode
}) {
  return menus.map(menu => {
    const active = menu.path === activePath
    if (!active && (!tabsEnabled || !openPaths.includes(menu.path))) return null
    return <RetainedReviewRoute key={`${scopeKey}:${menu.id}:${menu.path}:${menu.component ?? ''}`} active={active}>
      {renderPage(menu)}
    </RetainedReviewRoute>
  })
}
