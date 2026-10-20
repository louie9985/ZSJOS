import { deliveryPerformanceApi } from '../../services/deliveryPerformance'
import { APP_ROUTES } from '../../constants'

// Delivery owns its API, permissions and routes; no sales context is consulted.
const delivery = { api: deliveryPerformanceApi, label: '交付', domain: 'delivery' as const,
 targetRoute: APP_ROUTES.DELIVERY_PERFORMANCE_TARGET, permission: (value: string) => value }
export const usePerformanceModule = () => delivery
