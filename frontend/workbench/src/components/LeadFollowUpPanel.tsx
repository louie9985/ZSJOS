import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, App, Button, Empty, Form, Select, Space, Spin } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useLeadSalesStages } from '../services/useLeadSalesStages'
import { api, type LeadAttachment, type LeadFollowUp, type ManagedLead } from '../services/api'
import { useBusinessOverlay } from './OverlayCoordinator'
import { filterFollowUps } from '../services/leadFollowUp'
import FollowUpFormFields from './FollowUpFormFields'
import { useFollowUpDictionaries } from '../services/useFollowUpDictionaries'
import type { FollowUpValues } from '../services/followUpForm'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import FollowUpTimeline from './FollowUpTimeline'

const PAGE_SIZE = 10

export default function LeadFollowUpPanel({ lead, open, refreshVersion, onOpen, onClose, onChanged, onDirtyChange, onTotalChange }: {
  lead: ManagedLead; open: boolean; onOpen?: () => void; onClose: () => void; onChanged?: () => void
  refreshVersion?: number; onDirtyChange?: (dirty: boolean) => void; onTotalChange?: (total: number) => void
}) {
  const { message } = App.useApp()
  const stages = useLeadSalesStages(lead)
  const [form] = Form.useForm<FollowUpValues>()
  const [dirty, setDirty] = useState(false)
  const { submitting, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [images, setImages] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const [records, setRecords] = useState<LeadFollowUp[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const dictionaries = useFollowUpDictionaries(lead.id)
  const { methods, results } = dictionaries
  const blocked = dictionaries.blocked || stages.loading || Boolean(stages.error) || !stages.options.length
  const recordVersion = useRef(0)
  const [resetVersion, setResetVersion] = useState(0)
  const [filterMethod, setFilterMethod] = useState<string>()
  const [filterResult, setFilterResult] = useState<string>()
  useBusinessOverlay(dirty)
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  useEffect(() => { if (open) resetIntent() }, [open, lead.id, resetIntent])

  const loadRecords = useCallback(async (pageNo = 1) => {
    const version = ++recordVersion.current
    setLoading(true); setError('')
    try {
      const page = await api.leadFollowUpPage(lead.id, { pageNo, pageSize: PAGE_SIZE })
      if (version !== recordVersion.current) return
      setRecords(current => pageNo === 1 ? page.list : [...current, ...page.list])
      setTotal(page.total)
      onTotalChange?.(page.total)
    } catch (loadError) {
      if (version !== recordVersion.current) return
      setError(loadError instanceof Error ? loadError.message : '跟进记录加载失败')
    } finally { if (version === recordVersion.current) setLoading(false) }
  }, [lead.id, onTotalChange])

  useEffect(() => () => { recordVersion.current++ }, [loadRecords, refreshVersion])
  useEffect(() => { void loadRecords() }, [loadRecords, refreshVersion])

  useEffect(() => {
    form.resetFields()
    form.setFieldsValue({ leadCategory: lead.leadCategory, salesStage: lead.salesStage })
    setDirty(false); setImages([]); setFilterMethod(undefined); setFilterResult(undefined)
  }, [form, lead.id])

  useEffect(() => {
    if (!dirty) form.setFieldsValue({ leadCategory: lead.leadCategory, salesStage: lead.salesStage })
  }, [dirty, form, lead.leadCategory, lead.salesStage])

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const filteredRecords = useMemo(() =>
    filterFollowUps(records, filterMethod, filterResult),
    [records, filterMethod, filterResult])

  const isFiltered = Boolean(filterMethod || filterResult)

  const reset = () => {
    form.resetFields(); form.setFieldsValue({ leadCategory: lead.leadCategory, salesStage: lead.salesStage })
    setImages([]); setDirty(false); setResetVersion(value => value + 1); resetIntent()
  }
  const submit = async () => {
    if (blocked) return
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    await runSubmission(async ({ idempotencyKey, complete }) => {
      const uploadResult = await uploadDeferredFiles(images, file => api.uploadLeadFollowUpImage(lead.id, file), setImages)
      if (uploadResult.failed) { message.error('有跟进图片上传失败，请重试失败项'); return }
      await api.createLeadFollowUp(lead.id, {
        method: values.method, result: values.result, leadCategory: values.leadCategory, salesStage: values.salesStage,
        remark: values.remark?.trim() || undefined,
        nextFollowUpAt: values.nextFollowUpAt?.valueOf(),
        images: uploadResult.items.filter(image => image.uploaded).map(image => ({ infraFileId: image.uploaded!.infraFileId })), idempotencyKey
      })
      complete()
      reset(); await loadRecords(); onChanged?.(); onClose(); message.success('跟进记录已提交')
    }).catch(submitError => message.error(submitError instanceof Error ? submitError.message : '提交失败'))
  }

  return <section className="fu-panel">
    {/* Two-column layout: left = timeline, right = form */}
    <div className="fu-panel-layout">
      {/* Left column: header + timeline */}
      <div className="fu-panel-main">
        <div className="fu-panel-header">
          <span className="fu-panel-title">
            跟进记录<span className="fu-panel-count">· {total} 条</span>
          </span>
          {isFiltered && <span className="fu-panel-filtered">显示 {filteredRecords.length} / 共 {total}</span>}
          <span className="fu-panel-spacer"/>
          <Select
            size="small" allowClear placeholder="跟进方式" value={filterMethod} onChange={setFilterMethod}
            options={methods.map(item => ({ value: item.value, label: item.label }))}
            style={{ minWidth: 90 }}
          />
          <Select
            size="small" allowClear placeholder="跟进结果" value={filterResult} onChange={setFilterResult}
            options={results.map(item => ({ value: item.value, label: item.label }))}
            style={{ minWidth: 90 }}
          />
          {onOpen && !open && <Button size="small" type="primary" icon={<PlusOutlined/>} onClick={onOpen}>新增跟进</Button>}
        </div>

        {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadRecords()}>重试</Button>}/>}

        {!loading && filteredRecords.length === 0 && !error
          ? <div className="fu-panel-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={isFiltered ? '当前筛选无匹配记录' : '暂无跟进记录'}/></div>
          : <FollowUpTimeline records={filteredRecords}/>
        }

        {loading && <div className="fu-panel-loading"><Spin size="small"/> 加载中</div>}

        {!loading && records.length < total && (
          <Button block onClick={() => void loadRecords(Math.floor(records.length / PAGE_SIZE) + 1)}>加载更多</Button>
        )}
      </div>

      {/* Right column: inline form (visible when open) */}
      {onOpen && (
        <aside className={`fu-panel-aside${open ? ' fu-panel-aside--open' : ''}`}>
          {open ? (
            <div className="fu-panel-form-wrapper">
              <div className="fu-panel-form-header">
                <span className="fu-panel-form-title">新增跟进</span>
                <Button size="small" type="text" disabled={submitting} onClick={onClose}>收起</Button>
              </div>
              <Form form={form} layout="vertical" className="follow-up-form follow-up-form--shared" onValuesChange={() => setDirty(true)} disabled={submitting}>
                <FollowUpFormFields key={`${lead.id}-${resetVersion}`} lead={lead} form={form} dictionaries={dictionaries} stages={stages} images={images} onImagesChange={setImages} disabled={submitting} onModified={() => setDirty(true)}/>
                <Space>
                  <Button type="primary" loading={submitting} disabled={blocked} onClick={() => void submit()}>提交跟进</Button>
                  <Button disabled={submitting} onClick={reset}>重置</Button>
                </Space>
              </Form>
            </div>
          ) : (
            <div className="fu-panel-aside-placeholder">
              <Button type="dashed" icon={<PlusOutlined/>} onClick={onOpen} block>新增跟进</Button>
            </div>
          )}
        </aside>
      )}
    </div>
  </section>
}
