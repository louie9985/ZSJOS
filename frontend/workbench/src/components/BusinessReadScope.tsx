import { Alert, Button, Select, Space, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { api, type SimpleUser } from '../services/api'

export type BusinessReadScopeValue = { readScope: 'SELF' | 'ALL' | 'USER'; targetUserId?: number }

export default function BusinessReadScope({ value, onChange }: {
  value: BusinessReadScopeValue
  onChange: (value: BusinessReadScopeValue) => void
}) {
  const [users, setUsers] = useState<SimpleUser[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (value.readScope !== 'USER') return
    let active = true
    setLoading(true); setError('')
    api.simpleUsers(true).then(rows => { if (active) setUsers(rows) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '人员加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [value.readScope, attempt])
  return <Space wrap>
    <Select aria-label="查看范围" value={value.readScope} onChange={readScope => onChange({ readScope })}
      options={[{ value: 'SELF', label: '本人' }, { value: 'ALL', label: '全部（只读）' }, { value: 'USER', label: '指定人员（只读）' }]} />
    {value.readScope === 'USER' && <Select aria-label="指定人员" placeholder="请选择人员" showSearch optionFilterProp="label"
      loading={loading} value={value.targetUserId} onChange={targetUserId => onChange({ ...value, targetUserId })}
      options={users.map(user => ({ value: user.id, label: user.nickname }))} style={{ minWidth: 180 }} />}
    {error && <Alert type="error" message={error} action={<Button onClick={() => setAttempt(n => n + 1)}>重试</Button>} />}
    {value.readScope !== 'SELF' && <Typography.Text type="secondary">当前为只读查看，不代替他人办理业务</Typography.Text>}
  </Space>
}
