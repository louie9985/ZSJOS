import { Alert, App, Button, Select, Space, Spin, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { contentReviewApi, type ContentReviewSelection, type ContentReviewSubmitContext } from '../services/materialApi'
import { submitContentReview } from '../services/contentReviewErrors'

export function ContentReviewReviewerSelect({ batchId, onChange }: {
  batchId: number; onChange: (value: ContentReviewSelection | undefined) => void
}) {
  const [context, setContext] = useState<ContentReviewSubmitContext>()
  const [selected, setSelected] = useState<number>()
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const generation = useRef(0)
  const load = async () => {
    const run = ++generation.current
    setLoading(true); setError(''); setContext(undefined); onChange(undefined)
    try {
      const value = await contentReviewApi.submitContext(batchId)
      if (run !== generation.current) return
      setContext(value)
      const previous = value.candidates.find(c => c.id === value.previousDirectorUserId)?.id
      setSelected(previous)
      onChange(previous ? { directorUserId: previous, processDefinitionId: value.processDefinitionId } : undefined)
    } catch (e) { if (run === generation.current) setError(e instanceof Error ? e.message : '审核编导加载失败') }
    finally { if (run === generation.current) setLoading(false) }
  }
  useEffect(() => { void load(); return () => { generation.current++ } }, [batchId])
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <Typography.Paragraph>请选择本轮审核编导。对方可在本轮审批期间只读查看本次相关学员资料，审批结束后收回额外权限。</Typography.Paragraph>
    {loading ? <Spin /> : error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />
      : !context?.candidates.length ? <Alert type="warning" showIcon message="当前没有可选编导" description="请管理员检查流程角色范围、成员启用状态及内容审核和学员资料读取权限。" action={<Button onClick={() => void load()}>重新加载</Button>} />
      : <Select aria-label="本次审核编导" placeholder="请选择本次审核编导" showSearch optionFilterProp="label" style={{ width: '100%' }} value={selected}
        options={context.candidates.map(c => ({ value: c.id, label: c.nickname }))} onChange={id => {
          setSelected(id); onChange({ directorUserId: id, processDefinitionId: context.processDefinitionId })
        }} />}
    {context && <Typography.Text type="secondary">审批流程版本：{context.processDefinitionVersion}；确认后提交本轮审批。</Typography.Text>}
  </Space>
}

/** Every submit surface uses the same explicit selection and lost-response recovery. */
export function useContentReviewSubmit() {
  const { modal, message } = App.useApp()
  return async (batchId: number, version: number): Promise<boolean> => {
    const selection = await new Promise<ContentReviewSelection | undefined>(resolve => {
      let current: ContentReviewSelection | undefined
      modal.confirm({ title: '选择审核编导', width: 520, maskClosable: false,
        content: <ContentReviewReviewerSelect batchId={batchId} onChange={value => { current = value }} />,
        okText: '确认并提交', cancelText: '暂不提交',
        onOk: () => {
          if (!current) { message.warning('请先选择可用的审核编导'); return Promise.reject(new Error('selection required')) }
          resolve(current)
        }, onCancel: () => { resolve(undefined) } })
    })
    if (!selection) return false
    await submitContentReview(batchId, version, undefined, selection)
    return true
  }
}
