import { EditOutlined, HistoryOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, App, Button, DatePicker, Descriptions, Empty, Form, Input, Modal, Pagination, Select, Skeleton, Space, Tabs, Tag, Timeline, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useState } from 'react'
import { DICT_TYPE } from '../constants'
import { ApiError, api, type DictData, type MediaAccountLegacyStage, type MediaAccountMaintenanceRevision, type MediaStudentDetail } from '../services/api'
import { formatTimestamp } from '../services/time'

type Account = MediaStudentDetail['accounts'][number]
type Values = {
  currentStatusValue?: string
  stageValue?: string
  primaryProblemValues?: string[]
  executionMeasureValue?: string
  adjustmentDirection?: string
  dates?: [Dayjs, Dayjs]
}

const FIELD_LABELS: Record<string, string> = {
  currentStatus: '当下状态', stage: '阶段', primaryProblems: '主要问题', executionMeasure: '实行措施',
  adjustmentDirection: '修改方向', startDate: '开始日期', endDate: '结束日期'
}

export function canViewAccountHistory(account?: Account) {
  return Boolean(account?.availableActions.includes('VIEW_ACCOUNT_HISTORY'))
}

export default function AccountMaintenancePanel({ account, canMaintain, initiallyEditing = false, onEditingFinished, onSaved }: {
  account?: Account
  canMaintain: boolean
  initiallyEditing?: boolean
  onEditingFinished?: () => void
  onSaved: () => Promise<void>
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({})
  const [dictLoading, setDictLoading] = useState(false)
  const [dictError, setDictError] = useState('')
  const [history, setHistory] = useState<MediaAccountMaintenanceRevision[]>([])
  const [legacy, setLegacy] = useState<MediaAccountLegacyStage[]>([])
  const [historyTotal, setHistoryTotal] = useState(0)
  const [legacyTotal, setLegacyTotal] = useState(0)
  const [historyPage, setHistoryPage] = useState(1)
  const [legacyPage, setLegacyPage] = useState(1)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const canViewHistory = canViewAccountHistory(account)

  const loadDicts = async () => {
    setDictLoading(true); setDictError('')
    try {
      const types = [DICT_TYPE.MEDIA_ACCOUNT_CURRENT_STATUS, DICT_TYPE.MEDIA_ACCOUNT_STAGE,
        DICT_TYPE.MEDIA_ACCOUNT_PRIMARY_PROBLEM, DICT_TYPE.MEDIA_ACCOUNT_EXECUTION_MEASURE]
      const rows = await Promise.all(types.map(type => api.dictDataByType(type)))
      setDicts(Object.fromEntries(types.map((type, index) => [type, rows[index]])))
    } catch (cause) { setDictError(cause instanceof Error ? cause.message : '字典加载失败') }
    finally { setDictLoading(false) }
  }

  const loadHistory = async (kind: 'maintenance' | 'legacy', page = 1) => {
    if (!account || !canViewHistory) return
    setHistoryLoading(true); setHistoryError('')
    try {
      if (kind === 'maintenance') {
        const result = await api.mediaAccount.maintenanceHistory(account.id, { pageNo: page, pageSize: 10 })
        setHistory(result.list); setHistoryTotal(result.total); setHistoryPage(page)
      } else {
        const result = await api.mediaAccount.legacyStageHistory(account.id, { pageNo: page, pageSize: 10 })
        setLegacy(result.list); setLegacyTotal(result.total); setLegacyPage(page)
      }
    } catch (cause) {
      setHistoryError(cause instanceof ApiError && cause.code === 403 ? '无权查看该账号历史' : cause instanceof Error ? cause.message : '历史记录加载失败')
    } finally { setHistoryLoading(false) }
  }

  const openEditor = async () => {
    if (!account) return
    form.setFieldsValue({
      currentStatusValue: account.currentStatusValue,
      stageValue: account.stage,
      primaryProblemValues: account.primaryProblems?.map(item => item.value) || [],
      executionMeasureValue: account.executionMeasureValue,
      adjustmentDirection: account.adjustmentDirection,
      dates: account.maintenanceStartDate && account.maintenanceEndDate
        ? [dayjs(account.maintenanceStartDate), dayjs(account.maintenanceEndDate)] : undefined
    })
    setOpen(true)
    if (!Object.keys(dicts).length) await loadDicts()
  }

  const closeEditor = () => {
    setOpen(false)
    onEditingFinished?.()
  }

  useEffect(() => {
    setHistory([]); setLegacy([]); setHistoryTotal(0); setLegacyTotal(0); setHistoryPage(1); setLegacyPage(1)
    if (account && canViewHistory) void loadHistory('maintenance', 1)
    // Account identity and server-projected access are the load boundary; pagination is handled by HistoryContent.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [account?.id, canViewHistory])

  useEffect(() => {
    if (initiallyEditing && canMaintain) void openEditor()
    // The parent remounts this panel for an explicit account-row edit request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initiallyEditing, account?.id, canMaintain])

  if (!account) return <section className="media-account-maintenance"><Empty description="请选择账号" /></section>

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      await api.mediaAccount.maintain(account.id, {
        version: account.version,
        currentStatusValue: values.currentStatusValue,
        stageValue: values.stageValue,
        primaryProblemValues: values.primaryProblemValues || [],
        executionMeasureValue: values.executionMeasureValue,
        adjustmentDirection: values.adjustmentDirection,
        startDate: values.dates?.[0].format('YYYY-MM-DD'),
        endDate: values.dates?.[1].format('YYYY-MM-DD')
      })
      message.success('账号状态已保存')
      setOpen(false)
      await onSaved()
      if (onEditingFinished) onEditingFinished()
      else await loadHistory('maintenance', 1)
    } catch (cause) {
      if (!(cause as { errorFields?: unknown }).errorFields) message.error(cause instanceof Error ? cause.message : '保存失败')
    } finally { setSaving(false) }
  }

  const maintenanceItems = history.map(item => ({
    children: <div className="media-account-history-item">
      <Space wrap><Typography.Text strong>版本 {item.revisionNo}</Typography.Text><Typography.Text type="secondary">{item.operatedByUserName || '未知用户'} · {formatTimestamp(item.operatedAt)}</Typography.Text></Space>
      <div>{item.changedFields.map(field => <Tag key={field}>{FIELD_LABELS[field] || field}</Tag>)}</div>
      <Descriptions size="small" column={{ xs: 1, sm: 2 }} items={[
        { key: 'status', label: '当下状态', children: item.currentStatusLabelSnapshot || '未填写' },
        { key: 'stage', label: '阶段', children: item.stageLabelSnapshot || '未填写' },
        { key: 'problems', label: '主要问题', children: item.primaryProblems.map(problem => problem.labelSnapshot).join('、') || '未填写' },
        { key: 'measure', label: '实行措施', children: item.executionMeasureLabelSnapshot || '未填写' },
        { key: 'direction', label: '修改方向', children: item.adjustmentDirection || '未填写' },
        { key: 'dates', label: '日期', children: item.startDate && item.endDate ? `${item.startDate} 至 ${item.endDate}` : '未填写' }
      ]} />
    </div>
  }))

  const legacyItems = legacy.map(item => ({
    children: <div className="media-account-history-item"><Space wrap><Typography.Text strong>{(item.fromStage || '未记录').toUpperCase()} → {(item.toStage || '未记录').toUpperCase()}</Typography.Text><Typography.Text type="secondary">{item.judgedByUserName || '未知用户'} · {formatTimestamp(item.judgedAt)}</Typography.Text></Space>{item.judgmentBasis && <Typography.Paragraph>{item.judgmentBasis}</Typography.Paragraph>}</div>
  }))

  return <section className="media-students-card media-account-maintenance">
    <div className="media-students-tab-heading"><div><Typography.Title level={5}>账号状态维护</Typography.Title><Typography.Text type="secondary">{account.nickname || account.accountNo}</Typography.Text></div>{canMaintain && <Button type="primary" icon={<EditOutlined />} onClick={() => void openEditor()}>维护状态</Button>}</div>
    <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} items={[
      { key: 'status', label: '当下状态', children: account.currentStatusLabelSnapshot || '未填写' },
      { key: 'stage', label: '阶段', children: account.stageLabelSnapshot || '未填写' },
      { key: 'problems', label: '主要问题', children: account.primaryProblems?.map(item => item.labelSnapshot).join('、') || '未填写' },
      { key: 'measure', label: '实行措施', children: account.executionMeasureLabelSnapshot || '未填写' },
      { key: 'direction', label: '修改方向', children: account.adjustmentDirection || '未填写' },
      { key: 'dates', label: '日期', children: account.maintenanceStartDate && account.maintenanceEndDate ? `${account.maintenanceStartDate} 至 ${account.maintenanceEndDate}` : '未排期' }
    ]} />
    {canViewHistory && <Tabs className="media-account-history-tabs" items={[
      { key: 'maintenance', label: <span><HistoryOutlined /> 维护版本</span>, children: <HistoryContent loading={historyLoading} error={historyError} empty="暂无维护版本" items={maintenanceItems} total={historyTotal} page={historyPage} onLoad={page => void loadHistory('maintenance', page)} /> },
      { key: 'legacy', label: '原阶段记录', children: <HistoryContent loading={historyLoading} error={historyError} empty="暂无原阶段记录" items={legacyItems} total={legacyTotal} page={legacyPage} onLoad={page => void loadHistory('legacy', page)} /> }
    ]} onChange={key => { if (key === 'maintenance' && !history.length) void loadHistory('maintenance'); if (key === 'legacy' && !legacy.length) void loadHistory('legacy') }} />}
    <Modal title="维护账号状态" open={open} onCancel={closeEditor} onOk={() => void save()} confirmLoading={saving} okText="保存" destroyOnClose>
      {dictLoading ? <Skeleton active /> : dictError ? <Alert type="error" showIcon message={dictError} action={<Button size="small" icon={<ReloadOutlined />} onClick={() => void loadDicts()}>重试</Button>} /> : <Form form={form} layout="vertical">
        <Form.Item name="currentStatusValue" label="当下状态"><Select allowClear options={(dicts[DICT_TYPE.MEDIA_ACCOUNT_CURRENT_STATUS] || []).map(item => ({ value: item.value, label: item.label }))} /></Form.Item>
        <Form.Item name="stageValue" label="阶段"><Select allowClear options={(dicts[DICT_TYPE.MEDIA_ACCOUNT_STAGE] || []).map(item => ({ value: item.value, label: item.label }))} /></Form.Item>
        <Form.Item name="primaryProblemValues" label="主要问题"><Select allowClear mode="multiple" options={(dicts[DICT_TYPE.MEDIA_ACCOUNT_PRIMARY_PROBLEM] || []).map(item => ({ value: item.value, label: item.label }))} /></Form.Item>
        <Form.Item name="executionMeasureValue" label="实行措施"><Select allowClear options={(dicts[DICT_TYPE.MEDIA_ACCOUNT_EXECUTION_MEASURE] || []).map(item => ({ value: item.value, label: item.label }))} /></Form.Item>
        <Form.Item name="adjustmentDirection" label="修改方向" rules={[{ max: 1000 }]}><Input.TextArea rows={4} showCount maxLength={1000} /></Form.Item>
        <Form.Item name="dates" label="开始与结束日期"><DatePicker.RangePicker allowClear style={{ width: '100%' }} /></Form.Item>
      </Form>}
    </Modal>
  </section>
}

function HistoryContent({ loading, error, empty, items, total, page, onLoad }: {
  loading: boolean; error: string; empty: string; items: Array<{ children: React.ReactNode }>; total: number; page: number; onLoad: (page: number) => void
}) {
  if (loading) return <Skeleton active paragraph={{ rows: 3 }} />
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => onLoad(page)}>重试</Button>} />
  if (!items.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={empty}><Button onClick={() => onLoad(1)}>加载记录</Button></Empty>
  return <><Timeline items={items} />{total > 10 && <Pagination size="small" current={page} pageSize={10} total={total} onChange={onLoad} />}</>
}
