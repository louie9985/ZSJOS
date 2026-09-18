import { DeleteOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, DatePicker, Form, Input, Select, Space, Tag, Typography, Upload } from 'antd'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { useState } from 'react'
import { api } from '../services/api'
import { materialApi, type Material } from '../services/materialApi'
import MaterialSelectorModal from './MaterialSelectorModal'

export type ContentApprovalAccount = {
  id: number
  accountNo: string
  nickname?: string
  platformLabel?: string
  stage?: string
  stageLabelSnapshot?: string
  currentStatusValue?: string
  currentStatusLabelSnapshot?: string
  primaryProblems?: Array<{ value: string; labelSnapshot: string }>
}

/** 参考素材快照：提交给审批流后即使素材被改动或停用，审批人仍能看到当时的标题与封面。 */
export type ContentApprovalReferenceMaterial = {
  materialId: number
  materialVersionId?: number
  materialNo?: string
  title?: string
  materialTypeName?: string
  coverPreviewUrl?: string
}

export type ContentApprovalWork = {
  coverFileId?: number
  coverPreviewUrl?: string
  purposeValue?: string
  purposeLabelSnapshot?: string
  formatValue?: string
  formatLabelSnapshot?: string
  title?: string
  scriptText?: string
  detailUrl?: string
  leadResourceUrl?: string
  commentHook?: string
  /** 参考作品链接：纯文本填写。 */
  referenceWorkUrl?: string
  /** 素材库参考素材：由弹窗多选产生。 */
  referenceMaterials?: ContentApprovalReferenceMaterial[]
  plannedPublishAt?: Dayjs
}

export type ContentApprovalDraftValues = {
  accountIds: number[]
  accountSnapshots?: Record<string, Record<string, unknown>>
  works: ContentApprovalWork[]
}

type Option = { value: string; label: string }

const accountLabel = (account: ContentApprovalAccount) => account.nickname?.trim() || account.accountNo || `账号 ${account.id}`
const accountSnapshotFields = [
  ['nickname', '账号名称'],
  ['platformLabel', '发布平台'],
  ['stageLabelSnapshot', '当前期段'],
  ['currentStatusLabelSnapshot', '账号状态'],
] as const

/** 只把审批展示需要的字段传给后端，避免把整个素材对象塞进请求体。 */
const toReferenceMaterials = (materials: Material[]): ContentApprovalReferenceMaterial[] => materials.map(material => ({
  materialId: material.id,
  materialVersionId: material.currentVersion?.id || material.currentEffectiveVersionId,
  materialNo: material.materialNo,
  title: material.title,
  materialTypeName: material.materialTypeName,
  coverPreviewUrl: material.coverPreviewUrl,
}))

function CoverUploadField({ name }: { name: (string | number)[] }) {
  const form = Form.useFormInstance()
  const { message } = App.useApp()
  const [uploading, setUploading] = useState(false)
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string>()
  const previewUrl = Form.useWatch(['works', ...name, 'coverPreviewUrl'], form) as string | undefined
  const handleUpload = async (file: File) => {
    setUploading(true)
    try {
      const uploaded = await api.mediaContent.uploadVersionFile(file)
      form.setFieldValue(['works', ...name, 'coverFileId'], uploaded.fileId)
      const preview = uploaded.previewUrl || URL.createObjectURL(file)
      setLocalPreviewUrl(preview)
      form.setFieldValue(['works', ...name, 'coverPreviewUrl'], preview)
      message.success('封面图已上传')
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '封面图上传失败，请重试')
    } finally {
      setUploading(false)
    }
  }
  return <Space direction="vertical" size={6}>
    <Upload accept="image/*" maxCount={1} showUploadList={false} beforeUpload={file => { void handleUpload(file); return Upload.LIST_IGNORE }} disabled={uploading}>
      <Button icon={<UploadOutlined />} loading={uploading}>上传封面图</Button>
    </Upload>
    {(previewUrl || localPreviewUrl) && <Space align="start"><img src={previewUrl || localPreviewUrl} alt="作品封面预览" style={{ width: 160, maxHeight: 100, objectFit: 'cover', borderRadius: 6 }} /><Button danger icon={<DeleteOutlined />} onClick={() => { setLocalPreviewUrl(undefined); form.setFieldValue(['works', ...name, 'coverFileId'], undefined); form.setFieldValue(['works', ...name, 'coverPreviewUrl'], undefined) }}>删除图片</Button></Space>}
    <Typography.Text type="secondary">支持 JPG、PNG 等图片格式，提交审批前必须上传。</Typography.Text>
  </Space>
}

export default function ContentApprovalDraft({
  accounts = [],
  purposeOptions,
  formatOptions,
}: {
  accounts?: ContentApprovalAccount[]
  purposeOptions: Option[]
  formatOptions: Option[]
}) {
  const form = Form.useFormInstance()
  const selectedAccountIds = (Form.useWatch('accountIds', form) as number[] | undefined) || []
  const [now] = useState(() => dayjs())
  // 作品可上移/下移，下标会变化，因此选择缓存挂在 Form.List 稳定的 key 上。
  const [picker, setPicker] = useState<{ key: number; index: number }>()
  const [materialsByWork, setMaterialsByWork] = useState<Record<number, Material[]>>({})
  return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
    <Alert type="info" showIcon message="内容将同步适用于所选账号" description="账号资料只保存为本次审批快照；作品会按当前列表逐件提交和审批。" />
    <Form.Item name="accountIds" label="发布账号" rules={[{ required: true, type: 'array', min: 1, message: '请选择至少一个账号' }]}>
      <Select mode="multiple" allowClear showSearch optionFilterProp="label" options={accounts.map(account => ({ value: account.id, label: `${accountLabel(account)} · ${account.platformLabel || '平台未记录'}` }))} placeholder="选择一个或多个账号" />
    </Form.Item>
    {selectedAccountIds.length > 0 && <Card size="small" title="账号资料快照（本批次可编辑）">
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        {accounts.filter(account => selectedAccountIds.includes(account.id)).map(account => <div key={account.id} style={{ borderBottom: '1px solid var(--crm-border)', paddingBottom: 10 }}>
          <Typography.Text strong>{accountLabel(account)}</Typography.Text>
          <Typography.Text type="secondary" style={{ marginLeft: 8 }}>{account.platformLabel || '平台未记录'} · {account.accountNo}</Typography.Text>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 10, marginTop: 8 }}>
            {accountSnapshotFields.map(([key, label]) => <Form.Item key={key} name={['accountSnapshots', String(account.id), key]} label={label} initialValue={key === 'nickname' ? account.nickname : key === 'platformLabel' ? account.platformLabel : key === 'stageLabelSnapshot' ? account.stageLabelSnapshot || account.stage : key === 'currentStatusLabelSnapshot' ? account.currentStatusLabelSnapshot || account.currentStatusValue : undefined}>
              <Input />
            </Form.Item>)}
          </div>
          {account.primaryProblems?.length ? <Typography.Text type="secondary">当前瓶颈：{account.primaryProblems.map(problem => problem.labelSnapshot).join('、')}</Typography.Text> : null}
        </div>)}
      </Space>
    </Card>}
    <Form.List name="works" initialValue={[{}]}>
      {(fields, { add, remove, move }) => <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {fields.map((field, index) => {
          const workName = [field.name]
          return <Card key={field.key} size="small" title={<Space><span>作品 {index + 1}</span><Tag color="blue">逐件审批</Tag></Space>} extra={<Space size={4}>
            <Button type="text" size="small" disabled={index === 0} onClick={() => move(index, index - 1)}>上移</Button>
            <Button type="text" size="small" disabled={index === fields.length - 1} onClick={() => move(index, index + 1)}>下移</Button>
            {fields.length > 1 && <Button danger type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)}>删除</Button>}
          </Space>}>
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)', gap: 12 }}>
              <Form.Item {...field} name={[field.name, 'coverFileId']} label="作品封面图" rules={[{ required: true, message: '请上传作品封面图' }]}>
                <CoverUploadField name={workName} />
              </Form.Item>
              <Form.Item {...field} name={[field.name, 'plannedPublishAt']} label="预计发布时间" rules={[{ required: true, message: '请选择预计发布时间' }, { validator: (_, value: Dayjs | undefined) => !value || !value.isBefore(now, 'minute') ? Promise.resolve() : Promise.reject(new Error('预计发布时间不能早于当前时间')) }]}>
                <DatePicker showTime style={{ width: '100%' }} disabledDate={date => date.isBefore(dayjs(), 'minute')} />
              </Form.Item>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 12 }}>
              <Form.Item {...field} name={[field.name, 'purposeValue']} label="作品目的" rules={[{ required: true, message: '请选择作品目的' }]}><Select options={purposeOptions} /></Form.Item>
              <Form.Item {...field} name={[field.name, 'formatValue']} label="作品形式" rules={[{ required: true, message: '请选择作品形式' }]}><Select options={formatOptions} /></Form.Item>
            </div>
            <Form.Item {...field} name={[field.name, 'title']} label="发布标题" rules={[{ required: true, whitespace: true, message: '请输入发布标题' }, { max: 200, message: '发布标题不能超过 200 字' }]}><Input showCount maxLength={200} /></Form.Item>
            <Form.Item {...field} name={[field.name, 'scriptText']} label="正文文稿" rules={[{ required: true, whitespace: true, message: '请输入正文文稿' }]}><Input.TextArea rows={7} showCount maxLength={10000} /></Form.Item>
            <Form.Item {...field} name={[field.name, 'detailUrl']} label="作品详情"><Input placeholder="可填写链接，或由审批详情页直接查看" /></Form.Item>
            <Form.Item {...field} name={[field.name, 'leadResourceUrl']} label="引流资料链接"><Input placeholder="可点击下载的资料链接" /></Form.Item>
            <Form.Item {...field} name={[field.name, 'commentHook']} label="评论区钩子"><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
            <Form.Item {...field} name={[field.name, 'referenceWorkUrl']} label="参考作品链接" extra="直接填写参考作品的链接，可留空。">
              <Input placeholder="https:// 参考作品链接" allowClear />
            </Form.Item>
            <Form.Item label="参考素材" extra="从素材库浏览并多选参考素材，审批人可在审批详情中查看。">
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Button onClick={() => setPicker({ key: field.key, index: field.name })}>素材浏览 · 选择参考素材</Button>
                {(materialsByWork[field.key] || []).map(material => <Card key={material.id} size="small">
                  <Space align="start" size={10} style={{ width: '100%' }}>
                    {material.coverPreviewUrl
                      ? <img src={material.coverPreviewUrl} alt={material.title} style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 4 }} />
                      : <div style={{ width: 56, height: 56, borderRadius: 4, background: 'var(--crm-bg-sunken)' }} />}
                    <span style={{ flex: 1 }}>
                      <Typography.Text strong>{material.title}</Typography.Text><br />
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>{material.materialNo} · {material.materialTypeName}</Typography.Text>
                    </span>
                    <Button danger type="text" size="small" icon={<DeleteOutlined />} onClick={() => {
                      const next = (materialsByWork[field.key] || []).filter(item => item.id !== material.id)
                      setMaterialsByWork({ ...materialsByWork, [field.key]: next })
                      form.setFieldValue(['works', field.name, 'referenceMaterials'], toReferenceMaterials(next))
                    }}>移除</Button>
                  </Space>
                </Card>)}
              </Space>
            </Form.Item>
          </Card>
        })}
        <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({})} block>新增作品</Button>
        <Typography.Text type="secondary">至少保留一个作品；驳回后可在当前列表中新增、删除和修改作品，再次提交。</Typography.Text>
      </Space>}
    </Form.List>
    <MaterialSelectorModal
      open={picker !== undefined}
      onCancel={() => setPicker(undefined)}
      onConfirm={(materials) => {
        if (picker) {
          setMaterialsByWork({ ...materialsByWork, [picker.key]: materials })
          form.setFieldValue(['works', picker.index, 'referenceMaterials'], toReferenceMaterials(materials))
        }
        setPicker(undefined)
      }}
      defaultSelected={picker ? materialsByWork[picker.key] || [] : []}
    />
  </Space>
}
