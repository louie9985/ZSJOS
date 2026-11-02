import { useContext, useMemo, useState, type ReactNode } from 'react'
import { createPath, resolvePath, NavigationType, UNSAFE_LocationContext, UNSAFE_NavigationContext, UNSAFE_RouteContext, type Location, type Navigator, type To } from 'react-router-dom'
import { APP_ROUTES } from '../constants'

const paths: Record<string, string> = {
  lead: APP_ROUTES.LEAD_MANAGEMENT, order: APP_ROUTES.SALES_ORDERS,
  student: APP_ROUTES.MY_STUDENTS, registration: APP_ROUTES.REGISTRATION_POOL,
  lead_appeal: APP_ROUTES.LEAD_APPEALS, duplicate_review: APP_ROUTES.LEAD_DUPLICATE_REVIEW,
  media_student: APP_ROUTES.MEDIA_STUDENTS, subordinate_sales: APP_ROUTES.SUBORDINATE_SALES,
  education_team: APP_ROUTES.EDUCATION_TEAM, cashback: APP_ROUTES.CASHBACK,
  withdrawal: APP_ROUTES.WITHDRAWAL, bpm_todo: APP_ROUTES.BPM_TODO,
  business_todo: APP_ROUTES.TODAY_TASKS,
}
export const metricSourcePath = (source: string) => paths[source] ?? APP_ROUTES.TODAY_TASKS

/** Isolate list selection/search params from the home deep link; actual navigation still uses the host router. */
export default function MetricPageRouter({ source, children }: { source: string; children: ReactNode }) {
  const parent = useContext(UNSAFE_NavigationContext)
  const pathname = metricSourcePath(source)
  const [location, setLocation] = useState<Location>({ pathname, search: '', hash: '', state: null, key: 'metric-page' })
  const navigator = useMemo<Navigator>(() => {
    const navigate = (to: To, state: unknown, replace: boolean) => {
      const path = resolvePath(to, pathname)
      if (path.pathname !== pathname) {
        if (replace) parent.navigator.replace(to, state)
        else parent.navigator.push(to, state)
        return
      }
      setLocation(previous => ({ ...path, state, key: `${previous.key}-next` }))
    }
    return {
      createHref: to => createPath(resolvePath(to, pathname)),
      go: delta => parent.navigator.go(delta),
      push: (to, state) => navigate(to, state, false),
      replace: (to, state) => navigate(to, state, true),
    }
  }, [parent.navigator, pathname])
  return <UNSAFE_NavigationContext.Provider value={{ ...parent, navigator }}>
    <UNSAFE_LocationContext.Provider value={{ location, navigationType: NavigationType.Pop }}>
      <UNSAFE_RouteContext.Provider value={{ outlet: null, matches: [], isDataRoute: false }}>{children}</UNSAFE_RouteContext.Provider>
    </UNSAFE_LocationContext.Provider>
  </UNSAFE_NavigationContext.Provider>
}
