import {describe,it,expect} from 'vitest'
import {formatMetricDecimal} from './metricDecimal'
describe('metric decimal display',()=>{
 it('preserves large money exactly',()=>expect(formatMetricDecimal('9007199254740993.25',2)).toBe('9,007,199,254,740,993.25'))
 it('formats ratios once as percentages',()=>expect(formatMetricDecimal('0.125',2,true)).toBe('12.50%'))
 it('rounds negative values without negative zero',()=>{expect(formatMetricDecimal('-1.255',2)).toBe('-1.26');expect(formatMetricDecimal('-0.001',2)).toBe('0.00')})
})
