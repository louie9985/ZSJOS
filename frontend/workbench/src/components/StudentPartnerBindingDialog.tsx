import BusinessTable from './BusinessTable'
import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Input, Modal, Space, Typography } from 'antd'
import { managementApi, type Partner } from '../services/managementApi'

type Props = { studentPersonId: number; studentName: string; onClose: () => void; onBound: () => void }
const errorText = (cause: unknown) => cause instanceof Error ? cause.message : '操作失败，请重试'

export default function StudentPartnerBindingDialog({ studentPersonId, studentName, onClose, onBound }: Props) {
  const [rows, setRows] = useState<Partner[]>([])
  const [keyword, setKeyword] = useState(''), [query, setQuery] = useState('')
  const [page, setPage] = useState(1), [total, setTotal] = useState(0), [retry, setRetry] = useState(0)
  const [loading, setLoading] = useState(true), [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState(''), [saveError, setSaveError] = useState('')
  const [selected, setSelected] = useState<Partner>(), [reason, setReason] = useState('')
  const mounted = useRef(true), submitting = useRef(false)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    let active = true
    setLoading(true); setLoadError(''); setRows([]); setSelected(undefined)
    void managementApi.partnerPage({ pageNo: page, pageSize: 10, keyword: query || undefined }).then(result => {
      if (active) { setRows(result.list); setTotal(result.total) }
    }).catch(cause => { if (active) setLoadError(errorText(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, query, retry])
  const bind = async () => {
    if (!selected || loading || loadError || submitting.current) return
    submitting.current = true; setSaving(true); setSaveError('')
    try {
      await managementApi.bindPartnerStudent({ partnerId: selected.id, studentPersonId, reason: reason.trim() || undefined })
    } catch (cause) {
      if (mounted.current) setSaveError(errorText(cause))
      submitting.current = false
      if (mounted.current) setSaving(false)
      return
    }
    if (mounted.current) onBound()
  }
  return <Modal open title="绑定已有兼职账号" width={720} mask={{ closable: false }} closable={!saving} keyboard={!saving}
    onCancel={onClose} onOk={() => void bind()} okText="确认绑定" cancelText="取消" confirmLoading={saving}
    cancelButtonProps={{ disabled: saving }} okButtonProps={{ disabled: !selected || loading || Boolean(loadError) }}
    styles={{ body: { maxHeight: 'calc(100dvh - 240px)', overflowY: 'auto' } }}>
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Typography.Text>绑定学员：{studentName}</Typography.Text>
      <Alert type="info" showIcon message="请选择该学员已注册的兼职账号。绑定后，账号主页将汇总该兼职的客资数据，兼职原有运营归属保持不变。" />
      <Input.Search aria-label="搜索兼职账号" placeholder="搜索兼职姓名、手机号或编号" value={keyword} disabled={saving}
        onChange={event => setKeyword(event.target.value)} onSearch={value => { setQuery(value.trim()); setPage(1); setRetry(value => value + 1) }} enterButton="搜索" />
      {loadError ? <Alert type="error" showIcon message={loadError} action={<Button onClick={() => setRetry(value => value + 1)}>重试加载</Button>} /> :
        <BusinessTable<Partner> tableKey="student-partner-binding-dialog-1" columnMode="native" mode="compact" size="small" rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 520 }}
          locale={{ emptyText: '没有找到兼职账号，请调整搜索条件' }}
          rowSelection={{ type: 'radio', selectedRowKeys: selected ? [selected.id] : [], getCheckboxProps: () => ({ disabled: saving || loading }), onChange: (_, selectedRows) => { setSelected(selectedRows[0]); setSaveError('') } }}
          columns={[{ title: '兼职编号', dataIndex: 'partnerNo' }, { title: '姓名', dataIndex: 'name' }, { title: '手机号', dataIndex: 'mobile' }]}
          pagination={{ current: page, pageSize: 10, total, showSizeChanger: false, disabled: saving, onChange: setPage }} />}
      {selected && <Typography.Text>已选择：{selected.name}（{selected.partnerNo}）</Typography.Text>}
      <Input.TextArea aria-label="绑定说明" placeholder="绑定说明（选填，最多500字）" maxLength={500} value={reason} disabled={saving} onChange={event => setReason(event.target.value)} />
      {saveError && <Alert type="error" showIcon message={saveError} />}
    </Space>
  </Modal>
}
