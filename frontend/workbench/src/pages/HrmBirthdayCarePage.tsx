import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, InputNumber, Switch, TimePicker, message } from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import { api, type HrmBirthdayCareConfig } from '../services/api'
import DeptTreeSelect from '../components/DeptTreeSelect'
import dayjs from 'dayjs'

/** 生日关怀设置：配置提前提醒天数、发送时刻与适用部门。 */
export default function HrmBirthdayCarePage({ permissions }: { permissions: string[] }) {
  const [config, setConfig] = useState<HrmBirthdayCareConfig>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{
    enabled: boolean; advanceDays: number; triggerTime: dayjs.Dayjs
    deptIds: number[]; includeChildDepartments: boolean
  }>()

  const canUpdate = permissions.includes('hrm:birthday-care:update')

  useEffect(() => {
    setLoading(true)
    api.hrm.birthdayCare.get()
      .then(result => {
        setConfig(result)
        form.setFieldsValue({
          enabled: result.enabled,
          advanceDays: result.advanceDays,
          triggerTime: result.triggerTime ? dayjs(result.triggerTime, 'HH:mm:ss') : dayjs('09:00', 'HH:mm'),
          deptIds: result.deptIds || [],
          includeChildDepartments: result.includeChildDepartments
        })
      })
      .catch(e => setError(e instanceof Error ? e.message : '配置加载失败'))
      .finally(() => setLoading(false))
  }, [form])

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await api.hrm.birthdayCare.save({
        enabled: values.enabled,
        advanceDays: values.advanceDays,
        triggerTime: values.triggerTime.format('HH:mm:ss'),
        deptIds: values.deptIds || [],
        includeChildDepartments: values.includeChildDepartments,
        recipientUserIds: config?.recipientUserIds,
        missingTaskPermissionUserIds: config?.missingTaskPermissionUserIds
      })
      message.success('已保存')
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  return <section className="workspace-page hrm-page hrm-birthday-care-page">
    <div className="page-heading">
      <span className="hrm-muted">员工生日提醒设置</span>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => window.location.reload()}>重试</Button>}/>}
    <Card className="hrm-table-area" loading={loading}>
      <Form form={form} layout="vertical" className="hrm-edit-grid">
        <Form.Item name="enabled" label="启用生日关怀" valuePropName="checked"
          tooltip="关闭后不再发送生日提醒">
          <Switch checkedChildren="启用" unCheckedChildren="停用"/>
        </Form.Item>
        <Form.Item name="advanceDays" label="提前提醒天数" rules={[{ required: true, message: '请输入天数' }]}
          extra="在员工生日前多少天发送提醒">
          <InputNumber min={-30} max={30} precision={0} style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item name="triggerTime" label="提醒发送时刻" rules={[{ required: true, message: '请选择发送时刻' }]}>
          <TimePicker format="HH:mm" style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item name="deptIds" label="适用部门">
          <DeptTreeSelect multiple treeCheckable placeholder="不选则全员"/>
        </Form.Item>
        <Form.Item name="includeChildDepartments" label="包含子部门" valuePropName="checked">
          <Switch checkedChildren="是" unCheckedChildren="否"/>
        </Form.Item>
      </Form>
      <Button type="primary" icon={<SaveOutlined/>} loading={saving} disabled={!canUpdate} onClick={() => void handleSave()}>保存</Button>
    </Card>
  </section>
}
