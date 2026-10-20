import request from '@/config/axios'
import type { NavigationOrder } from '@/utils/navigationOrder'
export const getNavigationPreference = (): Promise<NavigationOrder> =>
  request.get({ url: '/zsjos/navigation-preference', params: { client: 'admin' } })
export const saveNavigationPreference = (data: NavigationOrder): Promise<boolean> =>
  request.put({ url: '/zsjos/navigation-preference', params: { client: 'admin' }, data })
