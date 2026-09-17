// Isolated browser fixture: synthetic materials and in-memory draft persistence, no real writes.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, ConfigProvider, Form, Input, Modal } from 'antd'
import { api, http, type PositioningCard, type StudentContactFormField } from '../src/services/api'
import { loadPositioningDraft } from '../src/services/positioningDraft'
import { serializePositioningFormValues } from '../src/services/positioningJsonImport'
import PositioningCardFields from '../src/components/PositioningCardFields'
import PositioningCardMaterialPicker from '../src/components/PositioningCardMaterialPicker'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'

const fields: StudentContactFormField[] = [
  { key: 'pc_account_name', title: '账号名称建议', type: 'textarea', description: '注意描述：最好帮助运营直接定下来', enabled: true, systemField: false, required: false, sort: 1 },
  { key: 'pc_target_user', title: '目标用户', type: 'textarea', description: '说明计划服务的人群和需求。', enabled: true, systemField: false, required: false, sort: 2 },
  { key: 'pc_homepage_douyin', title: '抖音平台主页搭建', type: 'textarea', description: '主页搭建、头像选择、背景图设置、主页引导语设置、置顶视频描述等建议（暂时不做该平台就写“暂时不做”）', enabled: true, systemField: false, required: false, sort: 3 },
  { key: 'pc_homepage_douyin_refs', title: '参考账号', type: 'material_picker', materialTypeCode: 'viral_account', referenceFor: 'pc_homepage_douyin', recommendedCount: '1–3', enabled: true, systemField: false, required: false, sort: 4 },
]
const cover = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="640"><rect width="400" height="640" fill="#e8f2fa"/><text x="200" y="320" text-anchor="middle" fill="#184a6b" font-size="24">参考账号封面</text></svg>')
let saved: PositioningCard | undefined
http.defaults.adapter = async config => {
  const data = config.url?.includes('/material-type/') ? [{ id: 1, code: 'viral_account', name: '参考账号', status: 0 }]
    : config.url?.endsWith('/material/page') ? { list: [{ id: 11, title: '测试参考账号', currentEffectiveVersionId: 1 }], total: 1 }
    : config.url?.includes('/material/version/') ? { id: 1, title: '测试参考账号', coverPreviewUrl: cover, files: [], fields: [{ key: 'intro', label: '账号简介', type: 'text' }], values: { intro: '用于预览与恢复验证的合成内容' }, dictSnapshot: {} }
    : config.url?.includes('/positioning-card/get') ? saved : []
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
function Editor() {
  const [form] = Form.useForm(), [open, setOpen] = useState(false), [status, setStatus] = useState('未保存')
  const begin = async () => {
    const draft = await loadPositioningDraft(saved ? [{ id: saved.id, accountId: null }] : [], undefined, 30, api.positioningCard.get)
    form.resetFields(); form.setFieldsValue({ data: draft?.valuesSnapshot || {} }); setOpen(true)
  }
  const save = () => {
    saved = { id: 19, cardNo: 'fixture', serviceRelationId: 30, status: 'co_creating', version: 1, availableActions: [], fieldsSnapshot: fields, valuesSnapshot: JSON.parse(JSON.stringify(serializePositioningFormValues(form.getFieldValue('data') || {}, fields))) }
    setStatus('草稿已保存'); setOpen(false)
  }
  return <><Button onClick={() => void begin()}>填写定位卡草稿</Button><p>{status}</p>
    <Modal title="填写定位卡草稿" width="min(1480px, calc(100vw - 32px))" styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' } }} open={open} onCancel={() => setOpen(false)} onOk={save} okText="保存并关闭">
      <Form form={form} layout="vertical"><PositioningCardFields fields={fields} render={field => <Form.Item name={['data', field.key]} label={field.title}>
        {field.type === 'material_picker' ? <PositioningCardMaterialPicker field={field} canQuery /> : <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} />}
      </Form.Item>} /></Form>
    </Modal></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><Editor /></App></ConfigProvider></ThemeProvider>)
