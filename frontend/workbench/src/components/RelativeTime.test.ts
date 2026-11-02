import {describe,it,expect} from 'vitest'
import {cloneFilterGroup,normalizeFilterGroup} from './AdvancedFilter'
import {initialRelativeTime,relativeTimeLabel} from './relativeTime'
import {relativeTimeLabel as adminLabel} from '../../../admin/src/views/zsjos/components/relativeTime'
describe('relative date operands',()=>{
 it('preserves expressions across template clone and interval normalization',()=>{
  const expression={...initialRelativeTime(),offset:-7}
  const group={logic:'AND' as const,conditions:[{fieldKey:'lead.qualificationDeadlineAt',operator:'between',valueFrom:expression,valueTo:1793491200000}],groups:[]}
  expect(normalizeFilterGroup(cloneFilterGroup(group))).toEqual(group)
  expect(JSON.parse(JSON.stringify(group))).toEqual(group)
 })
 it('both clients describe the same boundary without invalid dates',()=>{
  const value={...initialRelativeTime(),offset:-7}
  expect(relativeTimeLabel(value)).toContain('− 7 天')
  expect(adminLabel(value)).toEqual(relativeTimeLabel(value))
 })
})
