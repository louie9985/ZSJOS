import { useEffect, useState } from 'react'
import dayjs, { type Dayjs } from 'dayjs'
import { Button, DatePicker, Form, InputNumber, List, Modal, Radio, Select, Space, Upload, message } from 'antd'
import { DeleteOutlined, PaperClipOutlined, UploadOutlined } from '@ant-design/icons'
import type { FmsVoucher, FmsVoucherMoveReq, FmsVoucherTidyReq } from '../../services/fms/voucher'
import { fmsVoucher } from '../../services/fms/voucher'
import type { FmsVoucherWordVO } from '../../services/fms/types'
import { FMS_VOUCHER_ATTACHMENT_FILE_TYPES, FMS_VOUCHER_TIDY_TYPE } from '../../services/fms/constants'
import { DICT_TYPE } from '../../constants'
import { useDict } from '../../services/useDict'

function defaultVoucherWordId(words: FmsVoucherWordVO[]): number | undefined {
  return words.find(word => word.defaultStatus)?.id || words[0]?.id
}

export function FmsVoucherMoveModal(props: {
  open: boolean
  accountSetId: number
  defaultMonth: string
  voucherWords: FmsVoucherWordVO[]
  onClose: () => void
  onSuccess: () => void
}) {
  const [form] = Form.useForm<Omit<FmsVoucherMoveReq, 'accountSetId' | 'month'> & { month: Dayjs }>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!props.open) return
    form.setFieldsValue({
      month: dayjs(props.defaultMonth),
      voucherWordId: defaultVoucherWordId(props.voucherWords),
      sourceNumber: undefined,
      targetNumber: undefined
    })
  }, [form, props.defaultMonth, props.open, props.voucherWords])

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await fmsVoucher.move({ ...values, accountSetId: props.accountSetId, month: values.month.format('YYYY-MM') })
      message.success('凭证移动成功')
      props.onClose()
      props.onSuccess()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '凭证移动失败')
    } finally {
      setSaving(false)
    }
  }

  return <Modal title="移动凭证" open={props.open} onCancel={props.onClose} onOk={() => void submit()} confirmLoading={saving} width={720} destroyOnHidden>
    <Form form={form} layout="vertical">
      <Form.Item name="month" label="期间" rules={[{ required: true, message: '请选择期间' }]}><DatePicker picker="month" allowClear={false} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="voucherWordId" label="凭证字" rules={[{ required: true, message: '请选择凭证字' }]}><Select options={props.voucherWords.map(word => ({ value: word.id, label: word.name }))} /></Form.Item>
      <Space align="start">
        <Form.Item name="sourceNumber" label="原凭证号" rules={[{ required: true, message: '请输入原凭证号' }]}><InputNumber min={1} controls={false} /></Form.Item>
        <Form.Item
          name="targetNumber"
          label="移动到该号之前"
          dependencies={['sourceNumber']}
          rules={[
            { required: true, message: '请输入目标凭证号' },
            ({ getFieldValue }) => ({ validator: (_, value) => !value || value < getFieldValue('sourceNumber') ? Promise.resolve() : Promise.reject(new Error('目标凭证号必须小于原凭证号')) })
          ]}
        ><InputNumber min={1} controls={false} /></Form.Item>
      </Space>
    </Form>
  </Modal>
}

export function FmsVoucherTidyModal(props: {
  open: boolean
  accountSetId: number
  defaultMonth: string
  voucherWords: FmsVoucherWordVO[]
  onClose: () => void
  onSuccess: () => void
}) {
  const tidyTypes = useDict(DICT_TYPE.FMS_VOUCHER_TIDY_TYPE)
  const [form] = Form.useForm<Omit<FmsVoucherTidyReq, 'accountSetId' | 'month'> & { month: Dayjs }>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!props.open) return
    form.setFieldsValue({
      month: dayjs(props.defaultMonth),
      voucherWordId: defaultVoucherWordId(props.voucherWords),
      startNumber: 1,
      type: tidyTypes.options.find(option => option.value === FMS_VOUCHER_TIDY_TYPE.FILL_GAPS)?.value ?? tidyTypes.options[0]?.value
    })
  }, [form, props.defaultMonth, props.open, props.voucherWords, tidyTypes.options])

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await fmsVoucher.tidy({ ...values, accountSetId: props.accountSetId, month: values.month.format('YYYY-MM') })
      message.success('凭证整理成功')
      props.onClose()
      props.onSuccess()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '凭证整理失败')
    } finally {
      setSaving(false)
    }
  }

  return <Modal
    title="整理凭证"
    open={props.open}
    onCancel={props.onClose}
    onOk={() => void submit()}
    okButtonProps={{ disabled: Boolean(tidyTypes.error) }}
    confirmLoading={saving}
    width={720}
    destroyOnHidden
  >
    <Form form={form} layout="vertical">
      <Form.Item name="month" label="整理范围" rules={[{ required: true, message: '请选择月份' }]}><DatePicker picker="month" allowClear={false} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="voucherWordId" label="凭证字" rules={[{ required: true, message: '请选择凭证字' }]}><Select options={props.voucherWords.map(word => ({ value: word.id, label: word.name }))} /></Form.Item>
      <Form.Item name="startNumber" label="起始编号" rules={[{ required: true, message: '请输入起始编号' }]}><InputNumber min={1} controls={false} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="type" label="整理方式" rules={[{ required: true, message: '请选择整理方式' }]}>
        <Radio.Group options={tidyTypes.options} />
      </Form.Item>
      {tidyTypes.error && <div role="alert" style={{ color: 'var(--ant-color-error)', marginBottom: 12 }}>整理方式加载失败：{tidyTypes.error} <Button size="small" onClick={() => void tidyTypes.reload()}>重试</Button></div>}
    </Form>
  </Modal>
}

function attachmentName(url: string): string {
  try { return decodeURIComponent(url.split('/').pop() || url) } catch { return url }
}

export function FmsVoucherAttachmentModal(props: {
  open: boolean
  accountSetId: number
  voucher?: FmsVoucher
  editable: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const [urls, setUrls] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (props.open) setUrls([...(props.voucher?.attachmentUrls || [])])
  }, [props.open, props.voucher])

  const save = async () => {
    if (!props.voucher) return
    setSaving(true)
    try {
      await fmsVoucher.updateAttachments({ id: props.voucher.id, accountSetId: props.accountSetId, attachmentUrls: urls })
      message.success('附件保存成功')
      props.onClose()
      props.onSuccess()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '附件保存失败')
    } finally {
      setSaving(false)
    }
  }

  return <Modal
    title="凭证附件"
    open={props.open}
    onCancel={props.onClose}
    onOk={props.editable ? () => void save() : props.onClose}
    okText={props.editable ? '保存' : '确定'}
    cancelButtonProps={{ style: props.editable ? undefined : { display: 'none' } }}
    confirmLoading={saving}
    width={820}
    destroyOnHidden
  >
    {props.editable && <Upload
      accept={FMS_VOUCHER_ATTACHMENT_FILE_TYPES.map(type => `.${type}`).join(',')}
      showUploadList={false}
      disabled={uploading || urls.length >= 100}
      beforeUpload={file => {
        const extension = file.name.split('.').pop()?.toLowerCase() || ''
        if (!FMS_VOUCHER_ATTACHMENT_FILE_TYPES.includes(extension)) {
          message.error('仅支持 JPG、JPEG、PNG、BMP 图片')
          return Upload.LIST_IGNORE
        }
        return true
      }}
      customRequest={async options => {
        setUploading(true)
        try {
          const url = await fmsVoucher.uploadAttachment(options.file as File)
          setUrls(current => [...current, url])
          options.onSuccess?.(url)
        } catch (e) {
          options.onError?.(e instanceof Error ? e : new Error('上传失败'))
          message.error(e instanceof Error ? e.message : '附件上传失败')
        } finally {
          setUploading(false)
        }
      }}
    ><Button icon={<UploadOutlined />} loading={uploading}>上传图片</Button></Upload>}
    <List
      style={{ marginTop: 12 }}
      locale={{ emptyText: '暂无附件' }}
      dataSource={urls}
      renderItem={(url, index) => <List.Item actions={props.editable ? [<Button key="remove" type="text" danger icon={<DeleteOutlined />} aria-label="移除附件" onClick={() => setUrls(current => current.filter((_, itemIndex) => itemIndex !== index))} />] : undefined}>
        <List.Item.Meta avatar={<PaperClipOutlined />} title={<a href={url} target="_blank" rel="noreferrer">{attachmentName(url)}</a>} />
      </List.Item>}
    />
  </Modal>
}
