import { readdirSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('delivery implementation ownership', () => {
 it('has no sales performance imports or configurable domain bridge', () => {
  const paths = ['src/pages/DeliveryPerformancePage.tsx','src/pages/DeliveryPerformanceTargetPage.tsx',
   ...readdirSync('src/pages/deliveryPerformance').map(name => `src/pages/deliveryPerformance/${name}`),
   ...['deliveryPerformance','deliveryPerformanceCache','deliveryPerformanceSession'].map(name => `src/services/${name}.ts`)]
  for (const path of paths) {
   const source = readFileSync(path,'utf8')
   expect(source, path).not.toMatch(new RegExp('from .*?(salesPerformance|SalesPerformance|/performance/|services/performance)'))
   expect(source, path).not.toContain('zsjos:sales-performance')
   expect(source, path).not.toMatch(/tableKey=[{]?['"`]performance-/)
  }
 })
 it('registers actual delivery pages and preserves sales routes', () => {
  const routes=readFileSync('src/layouts/RouteHost.tsx','utf8')
  expect(routes).toContain('<DeliveryPerformancePage permissions={permissions}/>')
  expect(routes).toContain('<DeliveryPerformanceTargetPage permissions={permissions}/>')
  expect(routes).toContain('<SalesPerformancePage permissions={permissions}/>')
  expect(routes).not.toContain('DeliveryPerformanceModule')
 })
})
