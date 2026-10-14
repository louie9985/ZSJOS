import { createContext, useContext, type ReactNode } from 'react'
import { performanceApi, deliveryPerformanceApi } from '../../services/salesPerformance'
import { APP_ROUTES } from '../../constants'
export type PerformanceModule = { api: typeof performanceApi; label: string; domain: 'sales'|'delivery'; targetRoute: string; permission: (value: string) => string }
const sales: PerformanceModule = { api: performanceApi, label: '销售', domain: 'sales', targetRoute: APP_ROUTES.SALES_PERFORMANCE_TARGET,
  permission: (value: string) => value }
const delivery: PerformanceModule = { api: deliveryPerformanceApi, label: '交付', domain: 'delivery', targetRoute: APP_ROUTES.DELIVERY_PERFORMANCE_TARGET,
  permission: (value: string) => value.replace('zsjos:sales-performance', 'zsjos:delivery-performance') }
const Context = createContext(sales)
export const usePerformanceModule = () => useContext(Context)
export function DeliveryPerformanceModule({children}: {children: ReactNode}) {
 return <Context.Provider value={delivery}>{children}</Context.Provider>
}
