import { describe, expect, it } from 'vitest'
import {
  normalizeFeedbackInitialValues,
  serializeFeedbackFormValues
} from './FeedbackDynamicForm'
import type { FeedbackField } from '../services/feedbackApi'

const fields: FeedbackField[] = [
  { key: 'title', label: '标题', type: 'text', required: true },
  { key: 'expectedDate', label: '期望完成时间', type: 'date', required: false },
  { key: 'supportType', label: '支持类型', type: 'dictionary', required: true },
  { key: 'attachments', label: '附件', type: 'upload', required: false }
]

describe('feedback dynamic form values', () => {
  it('restores dictionary and attachment snapshots into current form fields', () => {
    expect(normalizeFeedbackInitialValues(fields, {
      title: '增加审批提醒',
      expectedDate: '2026-09-01',
      supportType: {
        type: 'zsjos_feedback_support_type',
        value: 'account_permission',
        label: '账号与权限'
      },
      attachments: [{ id: 12, name: '需求说明.pdf' }],
      removedField: '不应带入'
    })).toEqual({
      title: '增加审批提醒',
      expectedDate: '2026-09-01',
      supportType: 'account_permission',
      attachments: [{ id: 12, name: '需求说明.pdf' }]
    })
  })

  it('submits only current fields and converts uploaded snapshots to file ids', () => {
    expect(serializeFeedbackFormValues(fields, {
      title: '增加审批提醒',
      expectedDate: '2026-09-01',
      supportType: 'account_permission',
      attachments: [{ id: 12, name: '需求说明.pdf' }, { id: 13, name: '原型.png' }],
      staleField: '旧表单值'
    })).toEqual({
      title: '增加审批提醒',
      expectedDate: '2026-09-01',
      supportType: 'account_permission',
      attachments: [12, 13]
    })
  })
})
