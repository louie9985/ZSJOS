import { describe, expect, it } from 'vitest'
import pageSource from './LeadSubmissionPage.tsx?raw'

describe('lead submission stepped form', () => {
  it('splits the form into steps that stay mounted so values survive going back', () => {
    expect(pageSource).toContain('<Steps')
    expect(pageSource).toContain('STEP_FIELDS')
    // hidden 而不是条件卸载：卸载会丢已填值与校验状态
    expect(pageSource).toContain('className="lead-form-step" hidden={current !== 0}')
    expect(pageSource).toContain('className="lead-form-step" hidden={current !== 3}')
    expect(pageSource).not.toContain('current === 0 &&')
  })

  it('validates the current step before advancing and the whole form before submit', () => {
    expect(pageSource).toContain('const validateStep =')
    expect(pageSource).toContain('if (!await validateStep(steps[current].key, true)) return')
    expect(pageSource).toContain('for (const [index, step] of steps.entries())')
    expect(pageSource).toContain('await form.validateFields()')
  })

  it('reports every submit outcome through an antd modal instead of a toast', () => {
    expect(pageSource).toContain('const { message, modal } = App.useApp()')
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
    expect(pageSource).toContain('current === lastIndex && <IrreversiblePopconfirm')
    expect(pageSource).toContain('onConfirm={submit}')
  })

  it('renders dispatch mode as consequence cards, not a bare radio list', () => {
    expect(pageSource).toContain('className="lead-dispatch-options"')
    expect(pageSource).toContain('lead-dispatch-option')
    expect(pageSource).toContain('lead-dispatch-hint')
    expect(pageSource).toContain('DISPATCH_MODE_HINTS')
    // 选中态不是由 JS 驱动，而是由 Form 值映射，避免卡在状态与表单不同步
    expect(pageSource).toContain("lead-dispatch-option${assignmentMode === option.value ? ' selected' : ''}")
  })
})
