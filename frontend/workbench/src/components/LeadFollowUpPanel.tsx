import { useCallback, useEffect, useState } from 'react'
import { Alert, App, Button, Card, DatePicker, Empty, Form, Image, Input, Modal, Select, Space, Spin, Tag, Timeline, Typography, Upload } from 'antd'
import { CameraOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { api, type DictData, type LeadAttachment, type LeadFollowUp, type ManagedLead } from '../services/api'
import { DICT_TYPE } from '../constants'
import { useBusinessOverlay } from './OverlayCoordinator'
import { appendQuickNote } from '../services/leadFollowUp'
import { formatTimestamp } from '../services/time'

const QUICK_DAYS = [1, 2, 3, 5, 7, 14, 30]
const PAGE_SIZE = 10

type Values = { method: string; result: string; leadCategory?: string; remark?: string; nextFollowUpAt?: dayjs.Dayjs }

export default function LeadFollowUpPanel({ lead, open, onClose, onChanged, onDirtyChange, onTotalChange }: {
  lead: ManagedLead; open: boolean; onClose: () => void; onChanged?: () => void
  onDirtyChange?: (dirty: boolean) => void; onTotalChange?: (total: number) => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const [dirty, setDirty] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [images, setImages] = useState<LeadAttachment[]>([])
  const [records, setRecords] = useState<LeadFollowUp[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [methods, setMethods] = useState<DictData[]>([])
  const [results, setResults] = useState<DictData[]>([])
  const [categories, setCategories] = useState<DictData[]>([])
  const [quickNotes, setQuickNotes] = useState<DictData[]>([])
  useBusinessOverlay(dirty)
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])

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

  const reset = () => {
    form.resetFields(); form.setFieldsValue({ leadCategory: lead.leadCategory })
    setImages([]); setDirty(false)
  }
  const submit = async (values: Values) => {
    setSubmitting(true)
    try {
      await api.createLeadFollowUp(lead.id, {
        method: values.method, result: values.result, leadCategory: values.leadCategory,
        remark: values.remark?.trim() || undefined,
        nextFollowUpAt: values.nextFollowUpAt?.valueOf(),
        images: images.map(image => ({ infraFileId: image.infraFileId })), idempotencyKey: crypto.randomUUID()
      })
      reset(); await loadRecords(); onChanged?.(); onClose(); message.success('跟进记录已提交')
    } catch (submitError) { message.error(submitError instanceof Error ? submitError.message : '提交失败') }
    finally { setSubmitting(false) }
  }
  const upload = async (file: File) => {
    if (images.length >= 9) { message.warning('最多上传 9 张图片'); return false }
    setUploading(true)
    try {
      const uploaded = await api.uploadLeadFollowUpImage(lead.id, file)
      setImages(current => [...current, uploaded]); setDirty(true)
    }
    catch (uploadError) { message.error(uploadError instanceof Error ? uploadError.message : '上传失败') }
    finally { setUploading(false) }
    return false
  }
  const appendNote = (note: string) => {
    const current = form.getFieldValue('remark') || ''
    form.setFieldValue('remark', appendQuickNote(current, note)); setDirty(true)
  }

  return <section className="lead-follow-up-panel">
    <Modal title="新增跟进" open={open} onCancel={onClose} footer={null} destroyOnHidden width={760}>
    {lead.status === 'invalid' && <Alert type="info" showIcon message="本次跟进仅作为客资证据记录，下次跟进时间不会生成待办。"/>}
    <Form form={form} layout="vertical" className="follow-up-form" onFinish={submit} onValuesChange={() => setDirty(true)}>
      <div className="follow-up-field-grid">
        <Form.Item name="method" label="跟进方式" rules={[{ required: true, message: '请选择跟进方式' }]}><Select options={methods.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
        <Form.Item name="result" label="跟进结果" rules={[{ required: true, message: '请选择跟进结果' }]}><Select options={results.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
        <Form.Item name="leadCategory" label="客资分类"><Select allowClear options={categories.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
      </div>
      {quickNotes.length > 0 && <Space wrap className="follow-up-quick-notes">{quickNotes.map(note => <Button size="small" key={note.value} onClick={() => appendNote(note.label)}>{note.label}</Button>)}</Space>}
      <Form.Item name="remark" label="跟进备注"><Input.TextArea rows={4} maxLength={2000} showCount/></Form.Item>
      <Form.Item name="nextFollowUpAt" label="下次跟进时间">
        <DatePicker showTime format="YYYY-MM-DD HH:mm" disabledDate={date => date.endOf('day').isBefore(dayjs())}/>
      </Form.Item>
      <Space wrap className="follow-up-day-shortcuts">{QUICK_DAYS.map(days => <Button size="small" key={days} onClick={() => { form.setFieldValue('nextFollowUpAt', dayjs().add(days, 'day')); setDirty(true) }}>+{days} 天</Button>)}</Space>
      <div className="follow-up-upload-row">
        <Upload accept="image/jpeg,image/png,image/webp" showUploadList={false} beforeUpload={upload} disabled={uploading || images.length >= 9}>
          <Button icon={<CameraOutlined/>} loading={uploading}>上传图片 {images.length}/9</Button>
        </Upload>
        <Image.PreviewGroup><div className="follow-up-image-strip">{images.map((image, index) => <div key={image.infraFileId}><Image src={image.fileUrl}/><Button size="small" danger onClick={() => { setImages(current => current.filter((_, itemIndex) => itemIndex !== index)); setDirty(true) }}>移除</Button></div>)}</div></Image.PreviewGroup>
      </div>
      <Space><Button type="primary" htmlType="submit" loading={submitting}>提交跟进</Button><Button onClick={reset}>重置</Button></Space>
    </Form>
    </Modal>

    <Card size="small" title="跟进时间线" className="lead-follow-up-card">
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadRecords()}>重试</Button>}/>} 
    {!loading && records.length === 0 && !error ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跟进记录"/> : <Timeline items={records.map(record => ({
      children: <div className="follow-up-timeline-item">
        <Space wrap><Typography.Text strong>{record.operatorName || `用户 #${record.operatorUserId}`}</Typography.Text><span>{formatTimestamp(record.occurredAt)}</span>{record.firstInAssignment && <Tag color="green">本轮首次跟进</Tag>}</Space>
        <div><Tag>{record.methodLabel}</Tag><Tag color="blue">{record.resultLabel}</Tag></div>
        {record.categoryBefore !== record.categoryAfter && <Typography.Text>分类：{record.categoryBeforeLabel || '未分类'} → {record.categoryAfterLabel || '未分类'}</Typography.Text>}
        {record.remark && <Typography.Paragraph>{record.remark}</Typography.Paragraph>}
        {record.nextFollowUpAt && <Typography.Text type="secondary">下次跟进：{formatTimestamp(record.nextFollowUpAt)}</Typography.Text>}
        {record.images.length > 0 && <Image.PreviewGroup><div className="follow-up-record-images">{record.images.map(image => <Image key={image.infraFileId} src={image.url} alt={image.originalName}/>)}</div></Image.PreviewGroup>}
      </div>
    }))}/>} 
    {loading && <div className="follow-up-loading"><Spin size="small"/> 加载中</div>}
    {!loading && records.length < total && <Button block onClick={() => void loadRecords(Math.floor(records.length / PAGE_SIZE) + 1)}>加载更多</Button>}
    </Card>
  </section>
}
