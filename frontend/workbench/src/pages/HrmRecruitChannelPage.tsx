import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, Modal, Pagination, Space, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api, type HrmRecruitChannel } from '../services/api'
import type { ColumnsType } from 'antd/es/table'

const PAGE_SIZE = 20

/** 招聘渠道管理。 */
export default function HrmRecruitChannelPage({ permissions }: { permissions: string[] }) {
  const [items, setItems] = useState<HrmRecruitChannel[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [addOpen, setAddOpen] = useState(false)
  const [addLoading, setAddLoading] = useState(false)
  const [addForm] = Form.useForm<{ name: string }>()

  const canCreate = permissions.includes('hrm:recruit:channel:create')
  const canDelete = permissions.includes('hrm:recruit:channel:delete')

  const loadPage = useCallback(async (page: number) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.hrm.recruit.channel.page({ pageNo: page, pageSize: PAGE_SIZE })
      if (version !== listVersion.current) return
      setItems(result.list); setTotal(result.total)
    } catch (e) { if (version === listVersion.current) setError(e instanceof Error ? e.message : '渠道加载失败') }
    finally { if (version === listVersion.current) setLoading(false) }
  }, [])

  useEffect(() => { void loadPage(pageNo) }, [loadPage, pageNo])
  const reload = useCallback(() => { setPageNo(1); void loadPage(1) }, [loadPage])

  const handleAdd = async () => {
    const values = await addForm.validateFields()
    setAddLoading(true)
    try {
      await api.hrm.recruit.channel.create(values)
      message.success('已创建')
      setAddOpen(false); addForm.resetFields(); reload()
    } catch (e) { message.error(e instanceof Error ? e.message : '创建失败') }
    finally { setAddLoading(false) }
  }

  const handleDelete = (row: HrmRecruitChannel) => {
    Modal.confirm({ title: '删除渠道', content: `确定删除「${row.name}」吗？`, okType: 'danger', okText: '删除',
      onOk: async () => {
        try { await api.hrm.recruit.channel.delete(row.id!); message.success('已删除'); reload() }
        catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      } })
  }

  const columns: ColumnsType<HrmRecruitChannel> = [
    { title: '渠道名称', dataIndex: 'name', width: 300, render: (value: string) => value },
    { title: '创建时间', dataIndex: 'createTime', width: 200, render: (value?: string) => value ? new Date(value).toLocaleString('zh-CN') : '-' },
    { title: '操作', width: 120, align: 'center', render: (_, row) => canDelete
      ? <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>
      : <span className="hrm-muted">-</span> }
  ]

  const content = loading && !items.length ? <Empty description="加载中..."/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>}/>
      : !items.length ? <Empty description="暂无招聘渠道"/>
        : <>
          <HrmProTable<HrmRecruitChannel> advanced persistenceKey="recruit-channel" onReload={reload} rowKey="id" columns={columns} dataSource={items} pagination={false} loading={loading}/>
          <Pagination className="hrm-pagination" current={pageNo} total={total} pageSize={PAGE_SIZE} showSizeChanger={false} onChange={setPageNo} showTotal={count => `共 ${count} 条`}/>
        </>

  return <section className="workspace-page hrm-page hrm-recruit-channel-page">
    <div className="page-heading">
      <Space>
        {canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => { addForm.resetFields(); setAddOpen(true) }}>新增渠道</Button>}
        <Button icon={<ReloadOutlined/>} onClick={reload}>刷新</Button>
      </Space>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Modal title="新增招聘渠道" open={addOpen} onCancel={() => setAddOpen(false)} onOk={() => void handleAdd()}
      confirmLoading={addLoading} width="min(720px, 96vw)" destroyOnClose>
      <Form form={addForm} layout="vertical">
        <Form.Item name="name" label="渠道名称" rules={[{ required: true, message: '请输入渠道名称' }]}>
          <Input placeholder="如 BOSS直聘"/>
        </Form.Item>
      </Form>
    </Modal>
  </section>
}
