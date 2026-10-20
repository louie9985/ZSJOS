import { performanceApi } from '../../services/salesPerformance'
import { APP_ROUTES } from '../../constants'

const sales = { api: performanceApi, label: '销售', domain: 'sales' as const,
 targetRoute: APP_ROUTES.SALES_PERFORMANCE_TARGET, permission: (value: string) => value }
export const usePerformanceModule = () => sales
