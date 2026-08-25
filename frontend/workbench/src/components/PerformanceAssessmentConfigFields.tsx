import { Button, Form, Input, InputNumber, Select, Space, Switch } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import type { NamePath } from 'antd/es/form/interface'

function childPath(prefix: NamePath | undefined, name: string): NamePath {
  if (prefix == null) return name
  return [...(Array.isArray(prefix) ? prefix : [prefix]), name]
}

/** 绩效考核配置快照字段，供模板和绩效计划复用。 */
export default function PerformanceAssessmentConfigFields({ namePrefix }: { namePrefix?: NamePath }) {
  return <>
    <Space size="large" wrap>
      <Form.Item name={childPath(namePrefix, 'scoreCalculation')} label="计分方式" rules={[{ required: true, message: '请选择计分方式' }]}>
        <Select style={{ width: 180 }} options={[{ value: 1, label: '加权计算' }]}/>
      </Form.Item>
      <Form.Item name={childPath(namePrefix, 'upperLimitType')} label="评分上限方式" rules={[{ required: true, message: '请选择评分上限方式' }]}>
        <Select style={{ width: 180 }} options={[{ value: 1, label: '统一上限' }]}/>
      </Form.Item>
      <Form.Item name={childPath(namePrefix, 'upperLimitScore')} label="评分上限" rules={[{ required: true, message: '请输入评分上限' }]}>
        <InputNumber min={0} max={100} precision={2} style={{ width: 140 }}/>
      </Form.Item>
    </Space>

    <Form.List name={childPath(namePrefix, 'dimensions')}>
      {(dimensionFields, { add: addDimension, remove: removeDimension }) => <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {dimensionFields.map(({ key: dimensionKey, name: dimensionName, ...dimensionRest }) => <div key={dimensionKey} className="hrm-dimension-block">
          <Space align="start" wrap>
            <Form.Item {...dimensionRest} name={[dimensionName, 'name']} label="维度名称" rules={[{ required: true, message: '请输入维度名称' }]}>
              <Input maxLength={50} style={{ width: 160 }}/>
            </Form.Item>
            <Form.Item {...dimensionRest} name={[dimensionName, 'quotaType']} label="指标类型" rules={[{ required: true, message: '请选择指标类型' }]}>
              <Select style={{ width: 140 }} options={[{ value: 1, label: '业绩指标' }, { value: 2, label: '行为态度' }]}/>
            </Form.Item>
            <Form.Item {...dimensionRest} name={[dimensionName, 'weight']} label="维度权重" rules={[{ required: true, message: '请输入维度权重' }]}>
              <InputNumber min={0} max={100} precision={2} addonAfter="%" style={{ width: 130 }}/>
            </Form.Item>
            <Form.Item {...dimensionRest} name={[dimensionName, 'allowEdit']} label="员工可编辑" valuePropName="checked">
              <Switch/>
            </Form.Item>
            <Button type="text" danger icon={<DeleteOutlined/>} title="删除维度" onClick={() => removeDimension(dimensionName)}/>
          </Space>
          <Form.Item {...dimensionRest} name={[dimensionName, 'remark']} label="维度备注">
            <Input maxLength={200}/>
          </Form.Item>
          <Form.List name={[dimensionName, 'quotas']}>
            {(quotaFields, { add: addQuota, remove: removeQuota }) => <Space direction="vertical" size="small" style={{ width: '100%' }}>
              {quotaFields.map(({ key: quotaKey, name: quotaName, ...quotaRest }) => <div key={quotaKey} className="hrm-quota-row">
                <Space align="start" wrap>
                  <Form.Item {...quotaRest} name={[quotaName, 'name']} label="指标名称" rules={[{ required: true, message: '请输入指标名称' }]}>
                    <Input maxLength={50} style={{ width: 180 }}/>
                  </Form.Item>
                  <Form.Item {...quotaRest} name={[quotaName, 'weight']} label="指标权重" rules={[{ required: true, message: '请输入指标权重' }]}>
                    <InputNumber min={0} max={100} precision={2} addonAfter="%" style={{ width: 130 }}/>
                  </Form.Item>
                  <Form.Item {...quotaRest} name={[quotaName, 'scoreType']} label="评分类型" rules={[{ required: true, message: '请选择评分类型' }]}>
                    <Select style={{ width: 130 }} options={[{ value: 1, label: '直接输入' }]}/>
                  </Form.Item>
                  <Button type="text" danger icon={<DeleteOutlined/>} title="删除指标" onClick={() => removeQuota(quotaName)}/>
                </Space>
                <Form.Item {...quotaRest} name={[quotaName, 'illustrate']} label="指标说明">
                  <Input maxLength={200}/>
                </Form.Item>
                <Form.Item {...quotaRest} name={[quotaName, 'standard']} label="评分标准" rules={[{ required: true, message: '请输入评分标准' }]}>
                  <Input.TextArea rows={2} maxLength={200}/>
                </Form.Item>
              </div>)}
              <Button type="dashed" block icon={<PlusOutlined/>} onClick={() => addQuota({ name: '', illustrate: '', standard: '', weight: 0, scoreType: 1 })}>添加指标</Button>
            </Space>}
          </Form.List>
        </div>)}
        <Button type="dashed" block icon={<PlusOutlined/>} onClick={() => addDimension({ name: '', quotaType: 1, weight: 0, allowEdit: false, remark: '', quotas: [{ name: '', illustrate: '', standard: '', weight: 100, scoreType: 1 }] })}>添加维度</Button>
      </Space>}
    </Form.List>
  </>
}
