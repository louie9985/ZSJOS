import { http, unwrap } from './api'
import type { NavigationOrder } from './navigationOrder'
export const getNavigationPreference = async (): Promise<NavigationOrder> => unwrap(await http.get('/zsjos/navigation-preference', { params: { client: 'workbench' } }))
export const saveNavigationPreference = async (data: NavigationOrder): Promise<boolean> => unwrap(await http.put('/zsjos/navigation-preference', data, { params: { client: 'workbench' } }))
