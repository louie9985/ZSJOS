import { useEffect, useState } from 'react'
import { App, Button, Form, Modal, Space } from 'antd'
import { useLeadSalesStages } from '../services/useLeadSalesStages'
import { api, type LeadAttachment, type ManagedLead } from '../services/api'
import FollowUpFormFields from './FollowUpFormFields'
import { useFollowUpDictionaries } from '../services/useFollowUpDictionaries'
import type { FollowUpValues } from '../services/followUpForm'
import { uploadDeferredFiles, type DeferredUploadItem } from '../services/deferredUpload'
import { useSubmissionGuard } from '../services/submissionGuard'

export default function FollowUpModal({ lead, open, onClose, onSuccess }: {
  lead: ManagedLead; open: boolean; onClose: () => void; onSuccess: () => void
}) {
  const { message } = App.useApp()
  const stages = useLeadSalesStages(lead, open)
  const [form] = Form.useForm<FollowUpValues>()
  const { submitting, run: runSubmission, resetIntent } = useSubmissionGuard()
  const [images, setImages] = useState<DeferredUploadItem<LeadAttachment>[]>([])
  const dictionaries = useFollowUpDictionaries(lead.id, open)
  const blocked = dictionaries.blocked || stages.loading || Boolean(stages.error) || !stages.options.length
  useEffect(() => {
    if (!open) return
    resetIntent()
    form.resetFields()
    form.setFieldsValue({ leadCategory: lead.leadCategory, salesStage: lead.salesStage })
    setImages([])
  }, [open, lead.id, form, resetIntent])

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
      onCancel={() => { if (!submitting) onClose() }}
      closable={!submitting}
      maskClosable={!submitting}
      keyboard={!submitting}
      destroyOnClose
      width={720}
      footer={
        <Space>
          <Button disabled={submitting} onClick={onClose}>取消</Button>
          <Button type="primary" loading={submitting} disabled={blocked} onClick={() => void submit()}>提交跟进</Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" className="follow-up-form follow-up-form--shared" disabled={submitting}>
        <FollowUpFormFields key={lead.id} lead={lead} form={form} dictionaries={dictionaries} stages={stages} images={images} onImagesChange={setImages} disabled={submitting}/>
      </Form>
    </Modal>
  )
}
