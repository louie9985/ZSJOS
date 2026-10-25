import { Form, Input, InputNumber, Select, Typography } from 'antd';
import type { DictData } from '../services/api';
import type { DiagnosisRequest, WeeklyDiagnosisNode } from '../services/mediaAccountProfile';
import { WeeklyDiagnosisMetrics } from './WeeklyDiagnosisMetrics';
import { formatTimestamp } from '../services/time';
import { useEffect, useRef, type ReactNode } from 'react';
import type { FormInstance } from 'antd';

// These templates are diagnosis commands in the backend contract, not business dictionary choices.
const templates = [
  { value: 'diagnosis_7d', label: '7天账号数据诊断' },
  { value: 'diagnosis_14d', label: '14天验证指标诊断' },
  { value: 'diagnosis_28d', label: '28天调整触发条件' },
];

export default function AccountDiagnosisForm({ type, seed, disabled, dicts, onFinish, weekly, onLikesChange, accountSection, form: suppliedForm, onValuesChange }: {
  type: DiagnosisRequest['templateType']; seed: Record<string, unknown>; revising: boolean;
  disabled: boolean; dicts: Record<string, DictData[]>; onFinish: (values: Record<string, unknown>) => void;
  weekly?: WeeklyDiagnosisNode;
  onLikesChange?: (value:number)=>void;
  accountSection?: ReactNode;
  form?: FormInstance;
  onValuesChange?: () => void;
}) {
  const [form] = Form.useForm(suppliedForm);
  const initial = type === 'diagnosis_initial';
  const secondary = Form.useWatch('secondaryProblem', form);
  const highestLikes = Form.useWatch('highestLikes', form);
  const refreshLikes=useRef(onLikesChange);refreshLikes.current=onLikesChange;
  useEffect(()=>{if(highestLikes==null || !Number.isSafeInteger(highestLikes) || highestLikes<0)return;
    const timer=window.setTimeout(()=>refreshLikes.current?.(Number(highestLikes)),300);
    return()=>window.clearTimeout(timer);
  },[highestLikes]);
  const options = (key: string) => (dicts[key] || []).map(x => ({ value: x.value, label: x.label }));
  const required = [{ required: true, message: '请填写此项' }];
  const text = (name: string, label: string) => <Form.Item name={name} label={label} rules={[{ required: true, whitespace: true, message: '请填写此项' }]}>
    <Input.TextArea rows={3} maxLength={2000} />
  </Form.Item>;
  return <Form id="account-diagnosis-form" form={form} clearOnDestroy layout="vertical" disabled={disabled}
    onValuesChange={onValuesChange}
    onFinish={values => onFinish({ ...values, templateType: type, cycle: initial ? 0 : seed.cycle })}
    initialValues={{ reposition: false, ...seed }}>
    {accountSection}
    {weekly && !accountSection && <WeeklyDiagnosisMetrics node={weekly}
      extra={part=>part.days===7 ? <Form.Item name="highestLikes" label={`7天内最高点赞（${formatTimestamp(part.from)} 至 ${formatTimestamp(part.to)}，结束不包含）`} rules={[{required:true,message:'请填写最高点赞量'},{type:'integer',min:0}]}><InputNumber min={0} precision={0} max={Number.MAX_SAFE_INTEGER} /></Form.Item> : null} />}
    {!initial && !weekly && <Typography.Paragraph>
      <Typography.Text strong>本次填写：{templates.find(template => template.value === type)?.label}</Typography.Text>
      <br /><Typography.Text type="secondary">请根据本周期实际情况填写以下内容，表单类型及周期由系统自动关联。</Typography.Text>
    </Typography.Paragraph>}
    {accountSection ? <Typography.Title level={5}>通用诊断（本次所选账号共用，填写一次）</Typography.Title> : weekly && <Typography.Title level={5}>通用诊断（本次各周期共用，填写一次）</Typography.Title>}
    <div className="account-diagnosis-grid account-diagnosis-basics">
      {!accountSection && <><Form.Item label="当前阶段" name="currentStage" rules={required}><Select options={options('zsjos_media_account_stage')} /></Form.Item>
      <Form.Item label="账号状态" name="accountStatus" rules={required}><Select options={options('zsjos_media_account_current_status')} /></Form.Item></>}
      <Form.Item label="是否重新定位" name="reposition" rules={required}><Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} /></Form.Item>
    </div>
    {weekly && <>
      <div className="account-diagnosis-grid">
        <Form.Item name="changeDirection" label="是否需要换内容方向测试" rules={required}><Select options={options('infra_boolean_string')} /></Form.Item>
        <Form.Item name="trafficNormal" label="流量是否正常" rules={required}><Select options={options('infra_boolean_string')} /></Form.Item>
      </div>
      <Form.Item name="directionSummary" label="方向一句话概况" rules={[{required:true,whitespace:true,message:'请填写方向概况'},{max:200}]}><Input.TextArea rows={2} maxLength={200} showCount /></Form.Item>
    </>}
    {!initial && <div className="account-diagnosis-grid">
      <Form.Item label="学员配合等级" name="cooperationLevel" rules={required}><Select options={options('zsjos_media_account_cooperation_level')} /></Form.Item>
      {text('cooperationEvidence', '配合等级证据')}
    </div>}
    <div className="account-diagnosis-grid">
      <div>
        <Form.Item label="主要瓶颈" name="primaryProblem" rules={required}><Select options={options('zsjos_media_account_primary_problem')} /></Form.Item>
        {text('primaryProblemEvidence', '主要瓶颈证据')}
      </div>
      <div>
        <Form.Item label="次要瓶颈" name="secondaryProblem" rules={initial ? [] : required}>
          <Select allowClear={initial} placeholder={initial ? '选填' : '请选择'} options={options('zsjos_media_account_primary_problem')}
            onChange={value => { if (initial && !value) form.setFields([{ name: 'secondaryProblemEvidence', value: undefined, errors: [] }]); }} />
        </Form.Item>
        <Form.Item label="次要瓶颈证据" name="secondaryProblemEvidence" dependencies={['secondaryProblem']}
          rules={[{ required: !initial || !!secondary, whitespace: true, message: '选择次要瓶颈后请填写证据' }]}>
          <Input.TextArea rows={3} maxLength={2000} disabled={disabled || (initial && !secondary)} />
        </Form.Item>
      </div>
    </div>
    {text('conclusion', '一句话诊断结论')}
    {!initial && <div className="account-diagnosis-grid">
      {text('improvementMeasures', '改进措施')}{text('observedData', '重点观测数据')}
    </div>}
  </Form>;
}
