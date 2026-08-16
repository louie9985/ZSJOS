import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, DatePicker, Empty, Form, Input, Select, Space, Spin } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { api, type DictData, type LeadAttachment, type LeadFollowUp, type ManagedLead } from '../services/api'
import { DICT_TYPE } from '../constants'
import { useBusinessOverlay } from './OverlayCoordinator'
import { appendQuickNote, filterFollowUps } from '../services/leadFollowUp'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'
import FollowUpTimeline from './FollowUpTimeline'

const QUICK_DAYS = [1, 2, 3, 5, 7, 14, 30]
const PAGE_SIZE = 10

type Values = { method: string; result: string; leadCategory?: string; remark: string; nextFollowUpAt: dayjs.Dayjs }

export default function LeadFollowUpPanel({ lead, open, onOpen, onClose, onChanged, onDirtyChange, onTotalChange }: {
  lead: ManagedLead; open: boolean; onOpen?: () => void; onClose: () => void; onChanged?: () => void
  onDirtyChange?: (dirty: boolean) => void; onTotalChange?: (total: number) => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const [dirty, setDirty] = useState(false)
  const { submitting, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [images, setImages] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const [records, setRecords] = useState<LeadFollowUp[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [methods, setMethods] = useState<DictData[]>([])
  const [results, setResults] = useState<DictData[]>([])
  const [categories, setCategories] = useState<DictData[]>([])
  const [quickNotes, setQuickNotes] = useState<DictData[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<Values>()
  const [filterMethod, setFilterMethod] = useState<string>()
  const [filterResult, setFilterResult] = useState<string>()
  useBusinessOverlay(dirty)
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  useEffect(() => { if (open) resetIntent() }, [open, lead.id, resetIntent])

  const loadRecords = useCallback(async (pageNo = 1) => {
    setLoading(true); setError('')
    try {
      const page = await api.leadFollowUpPage(lead.id, { pageNo, pageSize: PAGE_SIZE })
      setRecords(current => pageNo === 1 ? page.list : [...current, ...page.list])
      setTotal(page.total)
      onTotalChange?.(page.total)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '跟进记录加载失败')
    } finally { setLoading(false) }
  }, [lead.id, onTotalChange])

  useEffect(() => {
    void loadRecords()
    Promise.all([
      api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_METHOD), api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_RESULT),
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY), api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_QUICK_NOTE)
    ]).then(([methodData, resultData, categoryData, notes]) => {
      setMethods(methodData); setResults(resultData); setCategories(categoryData); setQuickNotes(notes)
    }).catch(() => setError('跟进字典加载失败，请重试'))
    form.setFieldsValue({ leadCategory: lead.leadCategory })
    setDirty(false); setImages([])
  }, [form, lead.id, lead.leadCategory, loadRecords])

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
    form.resetFields(); form.setFieldsValue({ leadCategory: lead.leadCategory })
    setImages([]); setDirty(false)
  }
  const prepareSubmit = async () => {
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    setPendingValues(values)
    setConfirmOpen(true)
  }
  const submit = async () => {
    const values = pendingValues
    setConfirmOpen(false)
    if (!values) return
    await runSubmission(async ({ idempotencyKey, complete }) => {
      const uploadResult = await uploadDeferredFiles(images, file => api.uploadLeadFollowUpImage(lead.id, file), setImages)
      if (uploadResult.failed) { message.error('有跟进图片上传失败，请重试失败项'); return }
      await api.createLeadFollowUp(lead.id, {
        method: values.method, result: values.result, leadCategory: values.leadCategory,
        remark: values.remark?.trim() || undefined,
        nextFollowUpAt: values.nextFollowUpAt?.valueOf(),
        images: uploadResult.items.filter(image => image.uploaded).map(image => ({ infraFileId: image.uploaded!.infraFileId })), idempotencyKey
      })
      complete()
      reset(); await loadRecords(); onChanged?.(); onClose(); message.success('跟进记录已提交')
    }).catch(submitError => message.error(submitError instanceof Error ? submitError.message : '提交失败'))
  }
  const appendNote = (note: string) => {
    const current = form.getFieldValue('remark') || ''
    form.setFieldValue('remark', appendQuickNote(current, note)); setDirty(true)
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
                <Button size="small" type="text" onClick={onClose}>收起</Button>
              </div>
              <Form form={form} layout="vertical" className="follow-up-form" onValuesChange={() => setDirty(true)} disabled={submitting}>
                <Form.Item name="method" label="跟进方式" rules={[{ required: true, message: '请选择跟进方式' }]}><Select options={methods.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
                <Form.Item name="result" label="跟进结果" rules={[{ required: true, message: '请选择跟进结果' }]}><Select options={results.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
                <Form.Item name="leadCategory" label="客资分类"><Select allowClear options={categories.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
                {quickNotes.length > 0 && <Space wrap className="follow-up-quick-notes">{quickNotes.map(note => <Button size="small" key={note.value} onClick={() => appendNote(note.label)}>{note.label}</Button>)}</Space>}
                <Form.Item name="remark" label="跟进备注" rules={[{ required: true, whitespace: true, message: '请输入跟进备注' }]}><Input.TextArea rows={3} maxLength={2000} showCount/></Form.Item>
                <Form.Item name="nextFollowUpAt" label="下次跟进时间" rules={[{ required: true, message: '请选择下次跟进时间' }, { validator: (_, value) => !value || value.isAfter(dayjs()) ? Promise.resolve() : Promise.reject(new Error('下次跟进时间必须晚于当前时间')) }]}>
                  <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} disabledDate={date => date.endOf('day').isBefore(dayjs())}/>
                </Form.Item>
                <Space wrap className="follow-up-day-shortcuts">{QUICK_DAYS.map(days => <Button size="small" key={days} onClick={() => { form.setFieldValue('nextFollowUpAt', dayjs().add(days, 'day')); setDirty(true) }}>+{days} 天</Button>)}</Space>
                <div className="follow-up-upload-row">
                  <Form.Item label={`跟进图片${images.some(image => image.status === 'uploading') ? '（上传中）' : ''}`}>
                    <DeferredAttachmentPicker value={images} onChange={value => { setImages(value); setDirty(true) }} accept="image/jpeg,image/png,image/webp"/>
                  </Form.Item>
                </div>
                <Space>
                  <IrreversiblePopconfirm action={`提交客资「${lead.submittedName}」的跟进记录`} open={confirmOpen} onOpenChange={setConfirmOpen} onConfirm={submit}>
                    <Button type="primary" loading={submitting} onClick={() => void prepareSubmit()}>提交跟进</Button>
                  </IrreversiblePopconfirm>
                  <Button onClick={reset}>重置</Button>
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
