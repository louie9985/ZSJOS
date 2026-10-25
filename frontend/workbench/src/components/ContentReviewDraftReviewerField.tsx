import { Alert, Button, Form, Select, Space } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { contentReviewApi, type ContentReviewSelection, type ContentReviewSubmitContext } from '../services/materialApi'
import { resolveDraftReviewer } from '../services/contentReviewReviewer'

function ReviewerSelectControl({ value, onChange, context, loading, disabled, onSelected }: {
  value?: ContentReviewSelection | null; onChange?: (value: ContentReviewSelection | null) => void
  context?: ContentReviewSubmitContext; loading: boolean; disabled: boolean; onSelected: () => void
}) {
  return <Select aria-label="审批编导" placeholder="请选择审批编导" showSearch allowClear optionFilterProp="label"
    style={{ width: '100%' }} loading={loading} disabled={disabled} value={context ? value?.directorUserId : undefined}
    options={context?.candidates.map(candidate => ({ value: candidate.id, label: candidate.nickname }))}
    onChange={id => { onChange?.(id ? { directorUserId: id, processDefinitionId: context!.processDefinitionId } : null); onSelected() }} />
}

export default function ContentReviewDraftReviewerField({ batchId, studentPersonId, disabled }: {
  batchId?: number; studentPersonId?: number; disabled?: boolean
}) {
  const form = Form.useFormInstance()
  const reload = Form.useWatch('_reviewerReload', { form, preserve: true }) as number | undefined
  const [context, setContext] = useState<ContentReviewSubmitContext>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [warning, setWarning] = useState('')
  const [retry, setRetry] = useState(0)
  const initialized = useRef(false)
  useEffect(() => {
    let cancelled = false
    form.setFieldValue('_reviewerReady', false)
    setLoading(true); setError(''); setContext(undefined)
    void contentReviewApi.formSubmitContext(batchId ? { batchId } : { studentPersonId }).then(result => {
      if (cancelled) return
      const saved = form.getFieldValue('draftReviewerSelection') as ContentReviewSelection | null | undefined
      const resolved = resolveDraftReviewer(result, saved, !initialized.current && saved === undefined && !reload)
      initialized.current = true
      setContext(result); setWarning(resolved.warning || '')
      form.setFieldValue('draftReviewerSelection', resolved.selection || null)
      form.setFieldValue('_reviewerReady', true)
    }).catch(cause => {
      if (!cancelled) setError(cause instanceof Error ? cause.message : '审批编导加载失败')
    }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true; form.setFieldValue('_reviewerReady', false) }
  }, [batchId, studentPersonId, retry, reload, form])

  return <Form.Item label="审批编导"
    extra="本次所有账号和作品共用一名审批编导；保存草稿可暂不选择，提交审批时必选。">
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Form.Item name="draftReviewerSelection" noStyle>
        <ReviewerSelectControl context={context} loading={loading}
          disabled={Boolean(disabled || loading || error || !context?.candidates.length)}
          onSelected={() => { form.setFields([{ name: ['draftReviewerSelection'], errors: [] }]); setWarning('') }} />
      </Form.Item>
      {error && <Alert type="error" showIcon title={error} action={<Button disabled={disabled} onClick={() => setRetry(v => v + 1)}>重试</Button>} />}
      {warning && <Alert type="warning" showIcon title={warning} />}
      {!loading && !error && context && !context.candidates.length && <Alert type="warning" showIcon title="当前没有可选编导"
        description="请管理员检查流程角色范围、成员状态和审核权限。可继续填写并保存草稿。"
        action={<Button disabled={disabled} onClick={() => setRetry(v => v + 1)}>重新加载</Button>} />}
    </Space>
  </Form.Item>
}
