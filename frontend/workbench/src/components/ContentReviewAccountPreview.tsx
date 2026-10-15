import { useEffect, useState } from 'react'
import { Alert, Button, Card, Descriptions, Form, Spin } from 'antd'
import { contentReviewApi, type ContentReviewAccountProfile } from '../services/materialApi'

const fields = [
  ['nickname', '账号名称'], ['platformAccountId', '平台账号'], ['platformLabel', '平台'],
  ['sStageLabel', '当前期段'], ['currentStatusLabel', '账号状态'], ['operatorName', '责任运营'],
  ['directorName', '责任编导'], ['productGoal', '承接产品目标'], ['productFormLabel', '主要产品形式'],
  ['publishFrequency', '当前发布节奏'], ['bottleneckLabel', '当前瓶颈'],
] as const

export default function ContentReviewAccountPreview({ batchId, studentPersonId, accountIds }: {
  batchId?: number; studentPersonId?: number; accountIds: number[]
}) {
  const form = Form.useFormInstance()
  const selection = accountIds.join(',')
  const [retry, setRetry] = useState(0)
  const key = JSON.stringify([batchId, studentPersonId, selection, retry])
  const [state, setState] = useState<{ key: string; rows: ContentReviewAccountProfile[]; error?: string }>({ key: '', rows: [] })
  const ready = state.key === key && !state.error && state.rows.length > 0
  useEffect(() => {
    let active = true
    setState({ key: '', rows: [] })
    if (batchId || selection) void contentReviewApi.accountProfilePreview({ batchId, studentPersonId,
      accountIds: selection ? selection.split(',').map(Number) : [] }).then(rows => {
      if (active) setState({ key, rows, error: rows.length ? undefined : '没有可用的账号资料' })
    }).catch(error => { if (active) setState({ key, rows: [], error: error instanceof Error ? error.message : '账号资料加载失败' }) })
    return () => { active = false }
  }, [batchId, studentPersonId, selection, key, retry])
  return <Card size="small" title="本轮账号资料">
    <Form.Item name="accountProfileReady" hidden rules={[{ validator: () => ready ? Promise.resolve() : Promise.reject(new Error('请先加载本轮账号资料')) }]}><input /></Form.Item>
    <Alert type="info" message="正式提交时再次同步当前账号资料并保存为本轮快照；账号名称请在账号资料中维护。" />
    {state.error ? <Alert type="error" message={state.error} action={<Button onClick={() => setRetry(x => x + 1)}>重试</Button>} />
      : !ready ? <Spin /> : state.rows.map(row => <Descriptions key={row.id} title={row.nickname || '未记录'} column={{ xs: 1, sm: 2 }}
        items={fields.map(([field, label]) => ({ key: field, label, children: row[field]?.trim() || '未记录' }))} />)}
    <Button size="small" onClick={() => { form.setFieldValue('accountProfileReady', false); setRetry(x => x + 1) }}>刷新账号资料</Button>
  </Card>
}
