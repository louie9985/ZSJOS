import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Space, Tag, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api } from '../services/api'

/** 招聘淘汰原因设置：维护候选人在淘汰时可选的预设原因。 */
export default function HrmRecruitEliminatePage({ permissions }: { permissions: string[] }) {
  const [reasons, setReasons] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [newReason, setNewReason] = useState('')
  const [saving, setSaving] = useState(false)
  const version = useRef(0)

  const canUpdate = permissions.includes('hrm:recruit:config:update')

  const load = useCallback(async () => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const list = await api.hrm.recruit.eliminateReason.list()
      if (current !== version.current) return
      setReasons(list)
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : '淘汰原因加载失败') }
    finally { if (current === version.current) setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  const removeReason = (index: number) => {
    setReasons(current => current.filter((_, i) => i !== index))
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      await api.hrm.recruit.eliminateReason.save(reasons)
      message.success('已保存')
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const addReason = () => {
    const trimmed = newReason.trim()
    if (!trimmed) return
    setReasons(current => [...current, trimmed])
    setNewReason('')
  }

  return <section className="workspace-page hrm-page hrm-recruit-eliminate-page">
    <div className="page-heading">
      <span className="hrm-muted">候选人在淘汰时可选择的预设原因</span>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    <div className="hrm-table-area">
      {loading && !reasons.length ? <Empty description="加载中..."/> :
        reasons.length
          ? <div className="hrm-eliminate-list">
            {reasons.map((reason, index) => (
              <Tag key={reason} closable={canUpdate} onClose={() => removeReason(index)} className="hrm-eliminate-tag">
                {reason}
              </Tag>
            ))}
          </div>
          : <Empty description="暂无淘汰原因"/>}
      {canUpdate && <div className="hrm-eliminate-add">
        <Input value={newReason} onChange={e => setNewReason(e.target.value)} onPressEnter={addReason}
          placeholder="新增淘汰原因" style={{ width: 220 }}/>
        <Button icon={<PlusOutlined/>} onClick={addReason}>添加</Button>
        <Button type="primary" loading={saving} onClick={() => void handleSave()}>保存</Button>
      </div>}
    </div>
  </section>
}
