import { Alert, App, Button, Empty, Spin } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import ViralContentMaterialForm from '../components/ViralContentMaterialForm'
import { api, type DictData } from '../services/api'
import { materialApi, type MaterialType } from '../services/materialApi'

export default function ViralContentDecomposePage() {
  const { message } = App.useApp()
  const [type, setType] = useState<MaterialType>(); const [dicts, setDicts] = useState<Record<string, Array<{ value: string; label: string }>>>({}); const [error, setError] = useState(''); const [loading, setLoading] = useState(true); const [formKey, setFormKey] = useState(0)
  const load = useCallback(async () => { setLoading(true); setError(''); try { const names = ['zsjos_account_platform', 'zsjos_viral_content_type', 'zsjos_persona_type', 'zsjos_material_profession', 'zsjos_media_account_stage', 'zsjos_media_account_primary_problem']; const [types, ...rows] = await Promise.all([materialApi.types(), ...names.map(name => api.dictDataByType(name))]); setType(types.find(item => item.code === 'viral_content')); const next: Record<string, Array<{ value: string; label: string }>> = {}; rows.forEach((row, index) => { next[names[index]] = (row as DictData[]).map(item => ({ value: item.value, label: item.label })) }); setDicts(next) } catch (cause) { setError(cause instanceof Error ? cause.message : '加载失败') } finally { setLoading(false) } }, [])
  useEffect(() => { void load() }, [load])
  if (loading) return <Spin />; if (error) return <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />; if (!type) return <Empty description="爆款内容模板尚未发布" />
  return <section className="workspace-page standalone-viral-decompose"><ViralContentMaterialForm key={formKey} mode="create" type={type} dicts={dicts} onRetry={() => void load()} onClose={() => setFormKey(value => value + 1)} onSaved={() => { message.success('已保存，继续创建下一条'); setFormKey(value => value + 1) }} /></section>
}
