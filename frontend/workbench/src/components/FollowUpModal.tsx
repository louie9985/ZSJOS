import { useEffect, useState } from 'react'
import { Alert, App, Button, DatePicker, Empty, Form, Input, Modal, Select, Space, Spin } from 'antd'
import dayjs from 'dayjs'
import { useLeadSalesStages } from '../services/useLeadSalesStages'
import { api, type DictData, type LeadAttachment, type ManagedLead } from '../services/api'
import { DICT_TYPE } from '../constants'
import { applyFollowUpTimeShortcut, appendQuickNote, FOLLOW_UP_TIME_SHORTCUTS } from '../services/leadFollowUp'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'
import IrreversiblePopconfirm from './IrreversiblePopconfirm'

type Values = { salesStage?: string; method: string; result: string; leadCategory?: string; remark: string; nextFollowUpAt: dayjs.Dayjs }

export default function FollowUpModal({ lead, open, onClose, onSuccess }: {
  lead: ManagedLead; open: boolean; onClose: () => void; onSuccess: () => void
}) {
  const { message } = App.useApp()
  const stages = useLeadSalesStages(lead)
  const [form] = Form.useForm<Values>()
  const { submitting, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [images, setImages] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const [methods, setMethods] = useState<DictData[]>([])
  const [results, setResults] = useState<DictData[]>([])
  const [categories, setCategories] = useState<DictData[]>([])
  const [quickNotes, setQuickNotes] = useState<DictData[]>([])
  const [dictLoading, setDictLoading] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pendingValues, setPendingValues] = useState<Values>()

  useEffect(() => {
    if (!open) return
    resetIntent()
    setDictLoading(true)
    Promise.all([
      api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_METHOD),
      api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_RESULT),
      api.dictDataByType(DICT_TYPE.LEAD_CATEGORY),
      api.dictDataByType(DICT_TYPE.LEAD_FOLLOW_UP_QUICK_NOTE)
    ]).then(([methodData, resultData, categoryData, notes]) => {
      setMethods(methodData); setResults(resultData); setCategories(categoryData); setQuickNotes(notes)
    }).catch(() => message.error('跟进字典加载失败，请重试'))
      .finally(() => setDictLoading(false))
    form.resetFields()
    form.setFieldsValue({ leadCategory: lead.leadCategory, salesStage: lead.salesStage })
    setImages([])
  }, [open, lead.id, lead.leadCategory, lead.salesStage, form, message, resetIntent])

  const appendNote = (note: string) => {
    const current = form.getFieldValue('remark') || ''
    form.setFieldValue('remark', appendQuickNote(current, note))
  }

  const prepareSubmit = async () => {
    if (stages.loading || stages.error) return
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
        method: values.method, result: values.result, leadCategory: values.leadCategory, salesStage: values.salesStage,
        remark: values.remark?.trim() || undefined,
        nextFollowUpAt: values.nextFollowUpAt?.valueOf(),
        images: uploadResult.items.filter(image => image.uploaded).map(image => ({ infraFileId: image.uploaded!.infraFileId })),
        idempotencyKey
      })
      complete()
      message.success('跟进记录已提交')
      onSuccess()
      onClose()
    }).catch(submitError => message.error(submitError instanceof Error ? submitError.message : '提交失败'))
  }

  return (
    <Modal
      title="新增跟进"
      open={open}
      onCancel={onClose}
      destroyOnClose
      width={520}
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <IrreversiblePopconfirm
            action={`提交客资「${lead.submittedName}」的跟进记录`}
            open={confirmOpen}
            onOpenChange={setConfirmOpen}
            onConfirm={submit}
          >
            <Button type="primary" loading={submitting} disabled={stages.loading || Boolean(stages.error)} onClick={() => void prepareSubmit()}>提交跟进</Button>
          </IrreversiblePopconfirm>
        </Space>
      }
    >
      {dictLoading ? <Spin/> : (
        <Form form={form} layout="vertical" className="follow-up-form" disabled={submitting}>
          <Form.Item name="method" label="跟进方式" rules={[{ required: true, message: '请选择跟进方式' }]}>
            <Select options={methods.map(item => ({ value: item.value, label: item.label }))}/>
          </Form.Item>
          <Form.Item name="result" label="跟进结果" rules={[{ required: true, message: '请选择跟进结果' }]}>
            <Select options={results.map(item => ({ value: item.value, label: item.label }))}/>
          </Form.Item>
          {stages.error && <Alert type="error" showIcon title={stages.error} action={<Button size="small" onClick={() => void stages.reload()}>重试</Button>}/>}
          <Form.Item name="salesStage" label="跟进后销售阶段" rules={[{ required: true, message: '请选择销售阶段' }]}>
            <Select loading={stages.loading} disabled={Boolean(stages.error)} options={stages.options} notFoundContent={stages.loading ? <Spin size="small"/> : '暂无可选销售阶段，请联系管理员'}/>
          </Form.Item>
          <Form.Item name="leadCategory" label="客资分类">
            <Select allowClear options={categories.map(item => ({ value: item.value, label: item.label }))}/>
          </Form.Item>
          {quickNotes.length > 0 && (
            <Space wrap className="follow-up-quick-notes" style={{ marginBottom: 16 }}>
              {quickNotes.map(note => (
                <Button size="small" key={note.value} onClick={() => appendNote(note.label)}>{note.label}</Button>
              ))}
            </Space>
          )}
          {quickNotes.length === 0 && !dictLoading && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无快捷备注" style={{ margin: '0 0 16px' }}/>}
          <Form.Item name="remark" label="跟进备注" rules={[{ required: true, whitespace: true, message: '请输入跟进备注' }]}>
            <Input.TextArea rows={3} maxLength={2000} showCount/>
          </Form.Item>
          <Form.Item
            name="nextFollowUpAt"
            label="下次跟进时间"
            rules={[
              { required: true, message: '请选择下次跟进时间' },
              { validator: (_, value) => !value || value.isAfter(dayjs()) ? Promise.resolve() : Promise.reject(new Error('下次跟进时间必须晚于当前时间')) }
            ]}
          >
            <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} disabledDate={date => date.endOf('day').isBefore(dayjs())}/>
          </Form.Item>
          <Space wrap className="follow-up-day-shortcuts" style={{ marginBottom: 16 }}>
            {FOLLOW_UP_TIME_SHORTCUTS.map(shortcut => (
              <Button size="small" key={shortcut.key} onClick={() => form.setFieldValue('nextFollowUpAt', applyFollowUpTimeShortcut(shortcut))}>{shortcut.label}</Button>
            ))}
          </Space>
          <Form.Item label={`跟进图片${images.some(image => image.status === 'uploading') ? '（上传中）' : ''}`}>
            <DeferredAttachmentPicker value={images} onChange={setImages} accept="image/jpeg,image/png,image/webp"/>
          </Form.Item>
        </Form>
      )}
    </Modal>
  )
}
