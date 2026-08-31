import {
  Alert,
  App,
  Button,
  Checkbox,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Radio,
  Skeleton,
  Space,
  Tag,
  Typography,
  Upload,
} from 'antd'
import type { UploadFile, UploadProps } from 'antd'
import { InboxOutlined, ReloadOutlined } from '@ant-design/icons'
import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api, ApiError, FORCED_FORM_REQUIRED_CODE, type ForcedForm, type ForcedFormField, type ForcedFormRuntime } from '../services/api'
import { getAuthPlatform } from '../services/authSession'
import { formatTimestamp } from '../services/time'
import { useRealtime, useRealtimeEvent } from './RealtimeProvider'

type ForcedFormContextValue = {
  pendingCount: number
  loading: boolean
  error: string
  refresh: () => Promise<void>
}

type AttachmentAnswer = {
  uploadToken: string
  fileName: string
  fileSize: number
  contentType?: string
}

type ForcedFormAnswerValue = string | boolean | string[] | AttachmentAnswer[]
type ForcedFormAnswers = Record<string, ForcedFormAnswerValue>

const ForcedFormContext = createContext<ForcedFormContextValue | null>(null)
const FALLBACK_POLL_MS = 30_000

const isMobileViewport = () => window.matchMedia('(max-width: 768px)').matches

function useIsMobileViewport() {
  const [mobile, setMobile] = useState(isMobileViewport)
  useEffect(() => {
    const media = window.matchMedia('(max-width: 768px)')
    const sync = () => setMobile(media.matches)
    sync()
    media.addEventListener('change', sync)
    return () => media.removeEventListener('change', sync)
  }, [])
  return mobile
}

const errorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError && error.code === FORCED_FORM_REQUIRED_CODE) return '请先完成强制表单'
  return error instanceof Error ? error.message : fallback
}

const normalizePending = (items: ForcedForm[]) =>
  [...items].sort((left, right) => {
    const leftTime = Number(left.sentAt ?? 0)
    const rightTime = Number(right.sentAt ?? 0)
    if (leftTime !== rightTime) return leftTime - rightTime
    return (left.formId ?? left.id) - (right.formId ?? right.id)
  })

function toSubmitAnswers(values: ForcedFormAnswers, fields: ForcedFormField[]) {
  const result: Record<string, unknown> = {}
  fields.forEach((field) => {
    const value = values[field.key]
    if (field.type === 'attachment') {
      result[field.key] = Array.isArray(value)
        ? value.map((item) => typeof item === 'object' && item && 'uploadToken' in item ? item.uploadToken : item)
        : []
      return
    }
    result[field.key] = value
  })
  return result
}

export function ForcedFormProvider({ children }: PropsWithChildren) {
  const { message } = App.useApp()
  const { status: realtimeStatus } = useRealtime()
  const mountedRef = useRef(true)
  const requestSeq = useRef(0)
  const [pending, setPending] = useState<ForcedForm[]>([])
  const [runtime, setRuntime] = useState<ForcedFormRuntime>()
  const [loading, setLoading] = useState(true)
  const [runtimeLoading, setRuntimeLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [runtimeError, setRuntimeError] = useState('')
  const current = pending[0]

  const refresh = useCallback(async () => {
    const requestId = ++requestSeq.current
    setLoading(true)
    try {
      const items = normalizePending(await api.forcedFormsPending())
      if (!mountedRef.current || requestId !== requestSeq.current) return
      setPending(items)
      setError('')
    } catch (loadError) {
      if (!mountedRef.current || requestId !== requestSeq.current) return
      setError(errorMessage(loadError, '强制表单待办加载失败'))
    } finally {
      if (mountedRef.current && requestId === requestSeq.current) setLoading(false)
    }
  }, [])

  const loadRuntime = useCallback(async (formId: number) => {
    setRuntimeLoading(true)
    setRuntimeError('')
    try {
      setRuntime(await api.forcedFormRuntime(formId))
    } catch (loadError) {
      setRuntime(undefined)
      setRuntimeError(errorMessage(loadError, '表单内容加载失败'))
    } finally {
      setRuntimeLoading(false)
    }
  }, [])

  useEffect(() => {
    mountedRef.current = true
    void refresh()
    return () => {
      mountedRef.current = false
      requestSeq.current++
    }
  }, [refresh])

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    const timer = window.setInterval(() => void refresh(), FALLBACK_POLL_MS)
    window.addEventListener('focus', refreshWhenVisible)
    window.addEventListener('pageshow', refreshWhenVisible)
    window.addEventListener('online', refreshWhenVisible)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', refreshWhenVisible)
      window.removeEventListener('pageshow', refreshWhenVisible)
      window.removeEventListener('online', refreshWhenVisible)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
    }
  }, [refresh])

  useEffect(() => {
    if (realtimeStatus === 'open') void refresh()
  }, [realtimeStatus, refresh])

  useEffect(() => {
    const onRequired = () => void refresh()
    window.addEventListener('zsjos-forced-form-required', onRequired)
    return () => window.removeEventListener('zsjos-forced-form-required', onRequired)
  }, [refresh])

  useRealtimeEvent('zsjos_forced_form', () => { void refresh() })

  useEffect(() => {
    if (!current) {
      setRuntime(undefined)
      setRuntimeError('')
      return
    }
    void loadRuntime(current.formId ?? current.id)
  }, [current, loadRuntime])

  const submit = useCallback(async (answers: ForcedFormAnswers) => {
    if (!current || !runtime) return
    setSubmitting(true)
    try {
      await api.submitForcedForm(current.formId ?? current.id, {
        answersJson: JSON.stringify(toSubmitAnswers(answers, runtime.fields)),
        platform: getAuthPlatform().toLowerCase(),
      })
      message.success('强制表单已提交')
      setRuntime(undefined)
      await refresh()
    } catch (submitError) {
      message.error(errorMessage(submitError, '表单提交失败'))
    } finally {
      setSubmitting(false)
    }
  }, [current, message, refresh, runtime])

  const value = useMemo(
    () => ({ pendingCount: pending.length, loading, error, refresh }),
    [error, loading, pending.length, refresh],
  )

  return (
    <ForcedFormContext.Provider value={value}>
      {children}
      <ForcedFormGate
        current={current}
        error={error}
        loading={loading}
        runtime={runtime}
        runtimeError={runtimeError}
        runtimeLoading={runtimeLoading}
        submitting={submitting}
        onRefresh={refresh}
        onRetryRuntime={() => current && void loadRuntime(current.formId ?? current.id)}
        onSubmit={submit}
      />
    </ForcedFormContext.Provider>
  )
}

function ForcedFormGate({
  current,
  error,
  loading,
  runtime,
  runtimeError,
  runtimeLoading,
  submitting,
  onRefresh,
  onRetryRuntime,
  onSubmit,
}: {
  current?: ForcedForm
  error: string
  loading: boolean
  runtime?: ForcedFormRuntime
  runtimeError: string
  runtimeLoading: boolean
  submitting: boolean
  onRefresh: () => Promise<void>
  onRetryRuntime: () => void
  onSubmit: (answers: ForcedFormAnswers) => Promise<void>
}) {
  const mobile = useIsMobileViewport()
  const open = Boolean(current)
  const body = (
    <ForcedFormModalContent
      current={current}
      error={error}
      loading={loading}
      runtime={runtime}
      runtimeError={runtimeError}
      runtimeLoading={runtimeLoading}
      submitting={submitting}
      onRefresh={onRefresh}
      onRetryRuntime={onRetryRuntime}
      onSubmit={onSubmit}
    />
  )

  if (mobile) {
    return (
      <Drawer
        open={open}
        title="必须完成的表单"
        placement="bottom"
        height="100%"
        closable={false}
        maskClosable={false}
        keyboard={false}
        destroyOnHidden
      >
        {body}
      </Drawer>
    )
  }

  return (
    <Modal
      open={open}
      title="必须完成的表单"
      width={760}
      closable={false}
      mask={{ closable: false }}
      keyboard={false}
      destroyOnHidden
      footer={null}
    >
      {body}
    </Modal>
  )
}

function ForcedFormModalContent({
  current,
  error,
  loading,
  runtime,
  runtimeError,
  runtimeLoading,
  submitting,
  onRefresh,
  onRetryRuntime,
  onSubmit,
}: {
  current?: ForcedForm
  error: string
  loading: boolean
  runtime?: ForcedFormRuntime
  runtimeError: string
  runtimeLoading: boolean
  submitting: boolean
  onRefresh: () => Promise<void>
  onRetryRuntime: () => void
  onSubmit: (answers: ForcedFormAnswers) => Promise<void>
}) {
  const [form] = Form.useForm<ForcedFormAnswers>()

  useEffect(() => {
    form.resetFields()
  }, [form, runtime?.formId, runtime?.versionId])

  if (loading && !current) {
    return <Skeleton active paragraph={{ rows: 4 }} />
  }
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message={error}
        description="强制表单状态未确认前，普通业务操作会被服务端拦截。"
        action={<Button icon={<ReloadOutlined />} onClick={() => void onRefresh()}>重试</Button>}
      />
    )
  }
  if (!current) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无强制表单待办" />
  }

  return (
    <Space direction="vertical" size={16} className="forced-form-gate">
      <Alert
        type="warning"
        showIcon
        message="请先完成该表单"
        description="提交成功后会自动检查下一份待办，全部完成后恢复业务操作。"
      />
      <div>
        <Typography.Title level={4}>{runtime?.name || current.name}</Typography.Title>
        <Space wrap>
          {current.version ? <Tag color="blue">V{current.version}</Tag> : null}
          {current.sentAt ? <Typography.Text type="secondary">发送时间：{formatTimestamp(current.sentAt)}</Typography.Text> : null}
        </Space>
        {(runtime?.description || current.description) && (
          <Typography.Paragraph type="secondary">{runtime?.description || current.description}</Typography.Paragraph>
        )}
      </div>
      {runtimeError ? (
        <Alert
          type="error"
          showIcon
          message={runtimeError}
          description="字段或字典选项加载失败，不能回退到静态选项。"
          action={<Button icon={<ReloadOutlined />} onClick={onRetryRuntime}>重试</Button>}
        />
      ) : runtimeLoading || !runtime ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <Form form={form} layout="vertical" disabled={submitting} onFinish={(values) => void onSubmit(values as ForcedFormAnswers)}>
          {runtime.fields.map((field) => <ForcedFormFieldRenderer key={field.key} formId={runtime.formId} field={field} />)}
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              提交表单
            </Button>
          </Form.Item>
        </Form>
      )}
    </Space>
  )
}

function ForcedFormFieldRenderer({ formId, field }: { formId: number; field: ForcedFormField }) {
  const rules = field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined
  if (field.type === 'textarea') {
    return <Form.Item name={field.key} label={field.label} rules={rules}>
      <Input.TextArea rows={4} maxLength={field.maxLength} showCount={Boolean(field.maxLength)} />
    </Form.Item>
  }
  if (field.type === 'radio') {
    return <Form.Item name={field.key} label={field.label} rules={rules}>
      <Radio.Group options={(field.options || []).map((item) => ({ label: item.label, value: item.value }))} />
    </Form.Item>
  }
  if (field.type === 'multi-select') {
    return <Form.Item name={field.key} label={field.label} rules={rules}>
      <Checkbox.Group options={(field.options || []).map((item) => ({ label: item.label, value: item.value }))} />
    </Form.Item>
  }
  if (field.type === 'checkbox') {
    return <Form.Item name={field.key} valuePropName="checked" rules={field.required ? [{
      validator: (_, value) => value === true ? Promise.resolve() : Promise.reject(new Error(`请确认${field.label}`)),
    }] : undefined}>
      <Checkbox>{field.label}</Checkbox>
    </Form.Item>
  }
  if (field.type === 'attachment') {
    return <AttachmentField formId={formId} field={field} />
  }
  return <Form.Item name={field.key} label={field.label} rules={rules}>
    <Input maxLength={field.maxLength} showCount={Boolean(field.maxLength)} />
  </Form.Item>
}

function AttachmentField({ formId, field }: { formId: number; field: ForcedFormField }) {
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const uploadProps: UploadProps = {
    fileList,
    multiple: (field.maxCount ?? 1) > 1,
    maxCount: field.maxCount ?? 1,
    customRequest: async ({ file, onError, onSuccess }) => {
      try {
        const result = await api.uploadForcedFormAttachment(formId, field.key, file as File)
        onSuccess?.(result)
      } catch (uploadError) {
        onError?.(uploadError instanceof Error ? uploadError : new Error('附件上传失败'))
      }
    },
    onChange: ({ fileList: next }) => setFileList(next),
  }
  const valueFromEvent = (event: { fileList?: UploadFile[] }) =>
    (event.fileList || [])
      .filter((item) => item.status === 'done' && item.response)
      .map((item) => item.response as AttachmentAnswer)

  return (
    <Form.Item
      name={field.key}
      label={field.label}
      valuePropName="fileList"
      getValueFromEvent={valueFromEvent}
      rules={field.required ? [{ required: true, message: `请上传${field.label}` }] : undefined}
      extra={[
        field.maxCount ? `最多 ${field.maxCount} 个` : undefined,
        field.allowedExtensions?.length ? `允许：${field.allowedExtensions.join('、')}` : undefined,
      ].filter(Boolean).join('；')}
    >
      <Upload.Dragger {...uploadProps}>
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">点击或拖拽上传附件</p>
      </Upload.Dragger>
    </Form.Item>
  )
}

export function useForcedForms() {
  const context = useContext(ForcedFormContext)
  if (!context) throw new Error('useForcedForms must be used inside ForcedFormProvider')
  return context
}
