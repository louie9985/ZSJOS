import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Checkbox } from 'antd'
import BusinessTable from '../BusinessTable'
import DateTimeText from '../DateTimeText'
import { versionUpdates, type VersionRecipient } from '../../services/versionUpdates'

export default function VersionRecipients({ id }: { id: number }) {
  const [rows, setRows] = useState<VersionRecipient[]>([])
  const [page, setPage] = useState(1)
  const [pending, setPending] = useState(false)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const generation = useRef(0)
  const load = useCallback(async () => {
    const request = ++generation.current
    setLoading(true); setError('')
    try { const result = await versionUpdates.recipients(id, { pageNo: page, pageSize: 20, pending }); if (request === generation.current) { setRows(result.list); setTotal(result.total) } }
    catch (cause) { if (request === generation.current) setError(cause instanceof Error ? cause.message : '名单加载失败') }
    finally { if (request === generation.current) setLoading(false) }
  }, [id, page, pending])
  useEffect(() => { void load(); return () => { generation.current++ } }, [load])
  return <>{error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    <BusinessTable<VersionRecipient> tableKey="version-update-recipients" mode="compact" rowKey="userId" dataSource={rows} loading={loading}
      filters={<Checkbox checked={pending} onChange={event => { setPending(event.target.checked); setPage(1) }}>仅看待知悉</Checkbox>}
      pagination={{ current: page, pageSize: 20, total, onChange: setPage }} columns={[
        { title: '姓名', dataIndex: 'userName' }, { title: '部门', dataIndex: 'deptName' },
        { title: '知悉状态', render: (_, row) => row.acknowledgedTime ? '已知悉' : '待知悉' },
        { title: '知悉时间', render: (_, row) => <DateTimeText value={row.acknowledgedTime} /> }
      ]} />
  </>
}
