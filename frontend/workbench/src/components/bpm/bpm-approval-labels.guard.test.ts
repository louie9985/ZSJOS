import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const detail = readFileSync('src/components/bpm/BpmApprovalDetail.tsx', 'utf8')
const status = readFileSync('src/components/bpm/bpmStatus.ts', 'utf8')
const page = readFileSync('src/pages/BpmApprovalCenterPage.tsx', 'utf8')

/**
 * 审批中心的字段名必须中文化。
 *
 * 这段守卫是照着一次真实事故写的：需求反馈审批的流程定义 formType=20（业务表单），
 * 没有摘要也没有表单字段，界面就退到把 processVariables 原样铺开——
 * feedbackId / workOrderId / hasDepartmentLeader / chairmanAssignee 九个英文 key
 * 加一屏系统字段，全摆给审批人看。它们本来是给 Flowable 做网关条件与候选人计算的路由变量。
 *
 * 因此这里守两条：映射不到就不显示；内部标识不进业务字段区。
 */
describe('审批中心字段名中文化', () => {
  it('变量名映射不到时不退回显示英文 key', () => {
    // 退回 key 就是当初出问题的写法：BPM_VARIABLE_LABELS[key] || key
    expect(status).toContain('export function bpmVariableLabel')
    expect(status).not.toMatch(/BPM_VARIABLE_LABELS\[key\]\s*\|\|/)
    expect(status).toContain('export function hasChinese')
  })

  it('兜底字段表过滤掉没有中文名的流程变量', () => {
    expect(detail).toContain('fallbackItems')
    // 兜底项两次 filter：summary 路径过滤掉没有中文 label 的项，
    // 变量路径过滤掉 bpmVariableLabel 返回 undefined 的项。
    expect(detail).toMatch(/\.filter\(\(item\): item is \{ key: string; label: string; value: string \}/)
    expect(detail).toMatch(/\.filter\(\(item\): item is \{ key: string; label: string; value: unknown \}/)
  })

  it('列表摘要在拼不出中文名时给占位，而不是漏出内部编号', () => {
    expect(page).toContain('bpmVariableLabel')
    expect(page).toContain('hasChinese')
    // 曾经的写法是 summary[0] || task.formName || task.processInstanceId
    expect(page).not.toContain('|| task.processInstanceId')
  })

  it('流程实例与任务编号收进默认折叠的排查区', () => {
    // UUID 对审批人没有信息量，但排查时要拿得到：默认折叠 + 可复制，不做截断。
    expect(detail).toContain('ProcessIdentifiers')
    expect(detail).toContain('流程标识（排查用）')
    expect(detail).toContain('navigator.clipboard.writeText')
    // 不再作为流程元数据卡的常规字段平铺。
    expect(detail).not.toContain("label: '流程实例'")
    expect(detail).not.toContain("label: '任务编号'")
    expect(detail).not.toContain("label: '表单名称'")
  })
})
