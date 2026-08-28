import { describe, expect, it } from 'vitest'
import { serializeWorkOrderDynamicValues } from './workOrderForm'

describe('work-order dynamic form serialization', () => {
  it('serializes attachment fields as file IDs and collects unique files', () => {
    const first = { id: 7, name: '需求.pdf' }
    const second = { id: 8, name: '截图.png' }
    const result = serializeWorkOrderDynamicValues([
      { key: 'request_files', label: '需求附件', type: 'attachment', required: true },
      { key: 'evidence', label: '补充材料', type: 'attachment' },
      { key: 'subject', label: '主题', type: 'text' }
    ], { request_files: [first, second], evidence: [first], subject: '协作' })

    expect(result.values).toEqual({ request_files: [7, 8], evidence: [7], subject: '协作' })
    expect(result.attachmentFiles).toEqual([first, second])
  })
})
