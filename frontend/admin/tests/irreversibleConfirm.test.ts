import assert from 'node:assert/strict'
import test from 'node:test'
import {
  filterPublishConfirmAction,
  filterRollbackConfirmAction,
  irreversibleConfirmTitle,
  relationConfirmAction
} from '../src/views/zsjos/components/irreversibleConfirm.ts'

test('builds the shared irreversible confirmation copy', () => {
  assert.equal(irreversibleConfirmTitle('保存客资派单规则'), '确认执行「保存客资派单规则」操作吗？')
  assert.equal(filterPublishConfirmAction('审批人'), '发布「审批人」筛选方案到工作台')
  assert.equal(filterRollbackConfirmAction('负责人', 7), '将「负责人」筛选方案回滚至 V7 并重新发布')
})

test('builds single and batch relation actions for every mode', () => {
  assert.equal(
    relationConfirmAction('append', '派单关系', { name: '张三' }),
    '追加员工「张三」的派单关系'
  )
  assert.equal(
    relationConfirmAction('replace', '用户关系数据', { batchCount: 8 }),
    '批量替换 8 名员工的用户关系数据'
  )
  assert.equal(
    relationConfirmAction('remove', '派单关系', { batchCount: 2 }),
    '批量解除 2 名员工的派单关系'
  )
})
