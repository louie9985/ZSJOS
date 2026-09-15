import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import {
  expectSourceNotToContainTokens,
  expectSourceToContainTokens,
  sourceHasCall
} from '../../test/sourceGuard'

const actions = readFileSync('src/components/bpm/BpmApprovalActions.tsx', 'utf8')
const detail = readFileSync('src/components/bpm/BpmApprovalDetail.tsx', 'utf8')
const dynamicForm = readFileSync('src/components/bpm/BpmDynamicForm.tsx', 'utf8')
const page = readFileSync('src/pages/BpmApprovalCenterPage.tsx', 'utf8')
const api = readFileSync('src/services/api.ts', 'utf8')

describe('BPM 通用审批组件', () => {
  it('覆盖 BPM 的全部审批动作接口', () => {
    // 动作接口只依赖 taskId/processInstanceId，因此可被任意流程复用。
    const endpoints = [
      '/bpm/task/approve',
      '/bpm/task/reject',
      '/bpm/task/transfer',
      '/bpm/task/delegate',
      '/bpm/task/create-sign',
      '/bpm/task/delete-sign',
      '/bpm/task/return',
      '/bpm/task/copy',
      '/bpm/comment/create',
      '/bpm/task/list-by-return',
      '/bpm/task/list-by-process-instance-id',
      '/bpm/comment/list-by-process-instance-id',
      '/bpm/process-instance/get-approval-detail'
    ]
    for (const endpoint of endpoints) expect(api, endpoint).toContain(endpoint)
  })

  it('在动作组件内实际调用每个动作', () => {
    const calls = [
      'api.approveBpmTask',
      'api.rejectBpmTask',
      'api.transferBpmTask',
      'api.delegateBpmTask',
      'api.createBpmTaskSign',
      'api.deleteBpmTaskSign',
      'api.returnBpmTask',
      'api.copyBpmTask',
      'api.createBpmComment'
    ]
    for (const call of calls) expect(sourceHasCall(actions, call), call).toBe(true)
  })

  it('按钮显隐与名称由后端 buttonsSetting 决定，不硬编码流程语义', () => {
    expect(actions).toContain('buttonsSetting')
    expectSourceToContainTokens(actions, 'export function isBpmButtonVisible')
    expectSourceToContainTokens(actions, 'export function bpmButtonName')
    // 不得按流程 Key 或业务类型决定动作可用性，否则新增流程就要改前端。
    expect(actions).not.toContain('processDefinitionKey')
    expect(actions).not.toContain('zsjos_')
  })

  it('仅审批中的任务可处理，并遵守 bpm:task:update 权限', () => {
    expectSourceToContainTokens(actions, 'export function isBpmTaskActionable')
    expect(actions).toContain('TASK_STATUS_RUNNING')
    expect(actions).toContain('bpm:task:update')
    expect(page).toContain("permissions.includes('bpm:task:update')")
  })

  it('节点要求签名时必须先签名', () => {
    expect(actions).toContain('signEnable')
    expect(actions).toContain('signPicUrl')
    expectSourceToContainTokens(actions, 'message.warning(\'该节点要求签名，请先完成签名\')')
  })

  it('审批意见必填由节点配置 reasonRequire 驱动', () => {
    expect(actions).toContain('reasonRequire')
  })

  it('表单区域按 formType 自适应，业务表单降级为只读信息而不留空白', () => {
    expect(detail).toContain('FORM_TYPE_NORMAL')
    expect(detail).toContain('fallbackItems')
    // 业务表单的 Vue 组件路径在 React 中无法加载，不得据此渲染。
    expect(detail).not.toContain('formCustomViewPath')
    expect(dynamicForm).toContain('parseBpmFormFields')
    expect(dynamicForm).toContain('BPM_FIELD_PERMISSION')
  })

  it('动态表单解析单字段失败时跳过该字段而非整表失败', () => {
    expectSourceToContainTokens(dynamicForm, 'return parsed?.field ? [parsed] : []')
  })

  it('业务页入口只在后端声明已接入时展示', () => {
    expect(page).toContain('api.bpmBusinessTaskTarget')
    expect(page).toContain('target.supported')
    expect(detail).toContain('businessEntry')
  })

  it('通过、拒绝、退回三个推进流程的动作受 allowDecision 约束', () => {
    // 结论在业务页逐条产生的流程必须能关闭这三个动作，否则会绕过业务结论。
    for (const button of ['APPROVE', 'REJECT', 'RETURN']) {
      expectSourceToContainTokens(
        actions,
        `allowDecision && isBpmButtonVisible(task, BPM_OPERATION_BUTTON.${button})`
      )
    }
    // 流程类动作（加签、转办、委派、抄送、评论）不受约束，始终可用。
    for (const button of ['TRANSFER', 'DELEGATE', 'ADD_SIGN', 'COPY']) {
      expectSourceToContainTokens(
        actions,
        `isBpmButtonVisible(task, BPM_OPERATION_BUTTON.${button})`
      )
      expectSourceNotToContainTokens(
        actions,
        `allowDecision && isBpmButtonVisible(task, BPM_OPERATION_BUTTON.${button})`
      )
    }
  })

  it('生产内容批审在业务页展示流程并禁用流程结论', () => {
    const reviewPage = readFileSync('src/pages/ContentReviewBatchPage.tsx', 'utf8')
    const panel = readFileSync('src/components/bpm/BpmProcessPanel.tsx', 'utf8')
    // 批审结论按条保存，流程只能由「完成编导审核 / 完成终审」推进。
    expect(reviewPage).toContain('allowDecision={false}')
    expect(reviewPage).toContain('BpmProcessPanel')
    expect(reviewPage).toContain('selected.processInstanceId')
    expect(reviewPage).toContain('selected.currentTaskId')
    // 面板本身不得出现业务结论接口，业务结论只走 contentReviewApi。
    expect(panel).not.toContain('directorDecision')
    expect(panel).not.toContain('finalDecision')
    expect(panel).not.toContain('complete-director')
  })

  it('完成审核按钮只在流程面板内，不再留在页面顶部按钮区', () => {
    const reviewPage = readFileSync('src/pages/ContentReviewBatchPage.tsx', 'utf8')
    // 推进流程的动作通过 businessAdvance 交给流程面板承载。
    expect(reviewPage).toContain('businessAdvance={advanceAction}')
    expect(reviewPage).toContain('完成编导审核')
    expect(reviewPage).toContain('完成终审')
    // 顶部按钮区不得再直接挂完成按钮。
    expectSourceNotToContainTokens(
      reviewPage,
      "selected.availableActions.includes('DIRECTOR_COMPLETE') && <Button"
    )
    expectSourceNotToContainTokens(
      reviewPage,
      "selected.availableActions.includes('FINAL_COMPLETE') && <Button"
    )
    // 结论未填齐时禁用并说明原因，后端仍会再次校验。
    expect(reviewPage).toContain('disabledReason')
    expect(reviewPage).toContain('未填写结论')
  })

  it('业务推进动作不受 bpm:task:update 缺失影响', () => {
    const panel = readFileSync('src/components/bpm/BpmProcessPanel.tsx', 'utf8')
    // 完成审核由业务权限授权，没有 BPM 处理权限、或未解析到待办任务时都必须可用。
    expect(actions).toContain('businessOnly')
    expectSourceToContainTokens(actions, 'return businessOnly(')
    expectSourceToContainTokens(panel, '!loading && !todoTask && businessAdvance')
  })

  it('审批详情提供审批记录、流转记录与评论', () => {
    for (const tab of ['审批详情', '审批记录', '流转记录', '流程评论']) {
      expect(detail, tab).toContain(tab)
    }
  })
})
