import { describe, it, expect } from 'vitest'
import { approvalHistory, approvalStatus } from './approvalHistoryModel'
import type { BpmTask } from '../../services/api'
const task = (value: Partial<BpmTask> & Pick<BpmTask, 'id'>): BpmTask => ({ name: '审核', status: 1, createTime: 1, processInstanceId: 'p', ...value })
describe('approval history identity', () => {
  it('preserves repeated executions, multi-assignees and sign children without duplicate tasks', () => {
    const records = [task({ id: 'a', reason: '通过', children: [task({ id: 'child', name: '加签' })] }), task({ id: 'b', reason: '重新审核' }), task({ id: 'left', name: '转办' })]
    const result = approvalHistory([{ id: 'first', name: '审核', tasks: [{ id: 'a' }] }, { id: 'again', name: '审核', tasks: [{ id: 'b' }, { id: 'a' }] }], records)
    expect(result.nodes[0].tasks.map(t => t.id)).toEqual(['a', 'child'])
    expect(result.nodes[0].tasks[0].reason).toBe('通过')
    expect(result.nodes[1].tasks.map(t => t.id)).toEqual(['b'])
    expect(result.extra.map(t => t.id)).toEqual(['left'])
  })
  it('does not move an explicitly positioned subtask into a parent node', () => {
    const result = approvalHistory([{ id: 'parent', name: '主管', tasks: [{ id: 'p' }] }, { id: 'sign', name: '会签', tasks: [{ id: 'c' }] }], [task({ id: 'p', children: [task({ id: 'c', name: '会签' })] })])
    expect(result.nodes[0].tasks.map(t => t.id)).toEqual(['p'])
    expect(result.nodes[1].tasks[0].parentTaskId).toBe('p')
  })
  it('retains candidate users and missing or unknown status meaning', () => {
    expect(approvalHistory([{ id: 'future', name: '终审', candidateUsers: [{ id: 7, nickname: '候选人' }] }], []).nodes[0].candidateUsers?.[0].id).toBe(7)
    for (const missing of [null, undefined, NaN]) expect(approvalStatus(missing, String)).toBe('状态未返回')
    expect(approvalStatus(999, String)).toBe('999')
  })
})
