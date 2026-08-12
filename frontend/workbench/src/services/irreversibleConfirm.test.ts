import { describe, expect, it } from 'vitest'
import {
  assignmentConfirmAction,
  irreversibleConfirmTitle,
  IRREVERSIBLE_CONFIRM_DESCRIPTION
} from './irreversibleConfirm'

describe('irreversible confirmation copy', () => {
  it('wraps a concrete action in the required warning copy', () => {
    expect(irreversibleConfirmTitle('暂停接单')).toBe('确认执行「暂停接单」操作吗？')
    expect(IRREVERSIBLE_CONFIRM_DESCRIPTION).toBe('该操作无法撤回。')
  })

  it('describes single and batch assignment changes', () => {
    expect(assignmentConfirmAction('replace', { name: '张三' }))
      .toBe('替换员工「张三」的派单关系')
    expect(assignmentConfirmAction('remove', { batchCount: 8 }))
      .toBe('批量解除 8 名员工的派单关系')
  })
})
