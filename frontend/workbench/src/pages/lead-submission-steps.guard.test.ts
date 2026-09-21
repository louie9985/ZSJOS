import { describe, expect, it } from 'vitest'
import pageSource from './LeadSubmissionPage.tsx?raw'
import { expectSourceNotToContainTokens, expectSourceToContainTokens } from '../test/sourceGuard'

describe('lead submission stepped form', () => {
  it('splits the form into steps that stay mounted so values survive going back', () => {
    expect(pageSource).toContain('<Steps')
    expect(pageSource).toContain('STEP_FIELDS')
    // hidden 而不是条件卸载：卸载会丢已填值与校验状态
    expectSourceToContainTokens(pageSource, 'className="lead-form-step" hidden={current !== 0}')
    expectSourceToContainTokens(pageSource, 'className="lead-form-step" hidden={current !== 2}')
    expect(pageSource).toContain("title: '客资信息'")
    expect(pageSource).toContain("title: '提交确认'")
    expectSourceNotToContainTokens(pageSource, 'current === 0 &&')
  })

  it('validates the current step before advancing and the whole form before submit', () => {
    expect(pageSource).toContain('const validateStep =')
    expectSourceToContainTokens(pageSource, 'if (!await validateStep(steps[current].key, true)) return')
    expectSourceToContainTokens(pageSource, 'for (const [index, step] of steps.entries())')
    expect(pageSource).toContain('await form.validateFields()')
  })

  it('reports every submit outcome through an antd modal instead of a toast', () => {
    expectSourceToContainTokens(pageSource, 'const { message, modal } = App.useApp()')
    expect(pageSource).toContain('modal.success({')
    expect(pageSource).toContain('modal.info({')
    expect(pageSource).toContain('modal.warning({')
    expect(pageSource).toContain('modal.error({')
    expect(pageSource).toContain("title: '提交失败'")
    // 结果提示不再用 message.success/info/warning
    expect(pageSource).not.toContain('message.success(')
    expect(pageSource).not.toContain('message.info(')
    expect(pageSource).not.toContain('message.warning(')
  })

  it('keeps the irreversible confirm gate on the final step', () => {
    expectSourceToContainTokens(pageSource, 'current === lastIndex && <IrreversiblePopconfirm')
    expect(pageSource).toContain('onConfirm={submit}')
  })

  it('offers first-step contact activation and blocks navigation after a match', () => {
    expectSourceToContainTokens(pageSource, 'api.checkLeadContact')
    expectSourceToContainTokens(pageSource, 'api.checkSelfSourcedLeadContact')
    expectSourceToContainTokens(pageSource, "'客资已存在，已激活提醒'")
    expectSourceToContainTokens(pageSource, 'currentContactResult?.matched')
  })

  it('shows the selected customer region before the final submit confirmation opens', () => {
    expectSourceToContainTokens(pageSource, 'const path: string[] = summary?.regionPath || []')
    expectSourceNotToContainTokens(pageSource, 'const path: string[] = pendingValues?.regionPath || []')
  })

  it('renders dispatch mode as consequence cards, not a bare radio list', () => {
    expect(pageSource).toContain('className="lead-dispatch-options"')
    expect(pageSource).toContain('lead-dispatch-option')
    expect(pageSource).toContain('lead-dispatch-hint')
    expect(pageSource).toContain('DISPATCH_MODE_HINTS')
    // 选中态不是由 JS 驱动，而是由 Form 值映射，避免卡在状态与表单不同步
    expect(pageSource).toMatch(/lead-dispatch-option\$\{\s*assignmentMode\s*===\s*option\.value\s*\?\s*['"] selected['"]\s*:\s*['"]{2}\s*\}/)
  })
})
