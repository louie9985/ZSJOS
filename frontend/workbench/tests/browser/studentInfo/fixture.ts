// Test-only API adapter, used exclusively by the browser verification entry point.
import type { StudentInfoDetail, StudentInfoLink } from '../../../src/services/studentInfo'
const query = new URLSearchParams(location.search)
let fail = query.has('error')
let link: StudentInfoLink = { formId: 1, status: 'DRAFT', url: 'https://example.invalid/student-info-form#fixture', canRegenerate: true, canRevoke: true, createdAt: Date.UTC(2026,8,7), expiresAt: Date.UTC(2026,9,7) }
export const studentInfoStatus = { NONE: '尚未生成', DRAFT: '待填写', SUBMITTED: '已提交', EXPIRED: '已失效', REVOKED: '已撤销' }
export const studentInfoError = (error: unknown) => error instanceof Error ? error.message : '请求失败'
export const studentInfoApi = {
  detail: async (_: number, full: boolean): Promise<StudentInfoDetail> => {
    if (fail) { fail = false; throw new Error('测试加载失败') }
    return { status: query.has('empty') ? 'NONE' : 'SUBMITTED', configVersion: 1, submittedAt: Date.UTC(2026,8,7),
      canReadSensitive: !query.has('readonly'), canExport: !query.has('readonly'),
      fields: [{ key: 'mobile', label: '手机号', type: 'text', enabled: true, required: true, sensitive: true, sort: 1 },
        { key: 'purpose', label: '您报名的学习目的', type: 'textarea', enabled: true, required: false, sensitive: false, sort: 2 }],
      values: { mobile: full ? '测试完整字段' : '测试****字段', purpose: '测试历史快照与长文本显示。'.repeat(12) } }
  },
  generate: async () => ({ ...link }), link: async () => ({ ...link }),
  regenerate: async () => { link = { ...link, formId: 2, url: 'https://example.invalid/student-info-form#rotated-fixture' }; return link },
  revoke: async () => { link = { ...link, status: 'REVOKED', url: undefined, canRevoke: false }; return true },
  export: async () => { throw new Error('浏览器夹具不导出真实数据') }
}
