import { describe, expect, it } from 'vitest'
import { buildMenuTaskBadgeMap, menuTaskBadgeLabel } from './menuTaskBadge'

describe('menu task badges', () => {
  it('indexes server summaries by menu path and caps display labels', () => {
    const map = buildMenuTaskBadgeMap({ generatedAt: 1, total: 101, items: [
      { menuPath: '/zsjos/leads/manage', count: 101, severity: 'urgent', sourceTypes: ['lead'] }
    ] })
    expect(map.get('/zsjos/leads/manage')).toEqual({ count: 101, urgent: true })
    expect(menuTaskBadgeLabel('客资管理', map.get('/zsjos/leads/manage'))).toBe('客资管理（99+）')
    expect(menuTaskBadgeLabel('首页', undefined)).toBe('首页')
  })
})
