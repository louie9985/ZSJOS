import { describe, it, expect } from 'vitest'
import { annotationChanged, annotationOptions, annotationValues } from './studentServiceAnnotation'

describe('student course annotation', () => {
  it('defaults to non-proxy and service required without writing on load', () => {
    expect(annotationValues({ proxyStudent: null, agentName: 'old', serviceNeeded: null })).toEqual({ proxyStudent: 'false', agentName: null, serviceNeeded: 'true' })
    expect(annotationChanged({ proxyStudent: null, agentName: null, serviceNeeded: null }, { proxyStudent: 'false', agentName: null, serviceNeeded: 'true' })).toBe(false)
  })
  it('normalizes whitespace without making a spurious write', () => {
    expect(annotationChanged({ proxyStudent: 'true', agentName: '张', serviceNeeded: null }, { proxyStudent: 'true', agentName: ' 张 ', serviceNeeded: null })).toBe(false)
  })
  it('displays the saved label after dictionary renaming and retains removed selections', () => {
    expect(annotationOptions([{value:'true',label:'改名'}], 'true', '原标签')[0].label).toBe('原标签')
    expect(annotationOptions([], 'true', '原标签')).toEqual([{value:'true',label:'原标签',disabled:true}])
  })
})
