import { SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Checkbox, Empty, Image, Input, Modal, Pagination, Select, Space, Spin, Table, Tag, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../services/api'
import { materialApi, type Material, type MaterialType } from '../services/materialApi'
import DateTimeText from './DateTimeText'

const PAGE_SIZE = 10

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '审批中', value: 'IN_APPROVAL' },
  { label: '已生效', value: 'EFFECTIVE' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已停用', value: 'DISABLED' },
]

const sourceOptions = [
  { label: '手工创建', value: 'MANUAL' },
  { label: 'Excel 导入', value: 'IMPORT' },
  { label: '内容审核收录', value: 'CONTENT_REVIEW' },
]

const statusTag = (status: string) => {
  const map: Record<string, { color: string; text: string }> = {
    DRAFT: { color: 'default', text: '草稿' },
    IN_APPROVAL: { color: 'processing', text: '审批中' },
    EFFECTIVE: { color: 'success', text: '已生效' },
    REJECTED: { color: 'error', text: '已驳回' },
    DISABLED: { color: 'default', text: '已停用' },
  }
  const item = map[status] || { color: 'default', text: status }
  return <Tag color={item.color}>{item.text}</Tag>
}

type Filters = { keyword?: string; materialTypeId?: number; status?: string; source?: string }

export default function MaterialSelectorModal({
  open,
  onCancel,
  onConfirm,
  defaultSelected = [],
  maxCount = 20,
}: {
  open: boolean
  onCancel: () => void
  onConfirm: (materials: Material[]) => void
  defaultSelected?: Material[]
  maxCount?: number
}) {
  const [filters, setFilters] = useState<Filters>({})
  const [types, setTypes] = useState<MaterialType[]>([])
  const [rows, setRows] = useState<Material[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Material[]>([])

  const load = useCallback(async (page = 1, nextFilters: Filters = filters) => {
    setLoading(true); setError('')
    try {
      const result = await materialApi.page({ pageNo: page, pageSize: PAGE_SIZE, ...nextFilters })
      setRows(result.list || []); setTotal(result.total || 0); setPageNo(page)
    } catch (cause) {
      setRows([]); setTotal(0)
      setError(cause instanceof ApiError && cause.code === 403
        ? '无权访问素材库，请联系管理员开通素材查询权限'
        : cause instanceof Error ? cause.message : '素材加载失败，请重试')
    } finally { setLoading(false) }
  }, [filters])

  useEffect(() => {
    if (!open) return
    setSelected(defaultSelected)
    setFilters({})
    void materialApi.types().then(setTypes).catch(() => setTypes([]))
    void load(1, {})
  }, [open])

  const toggle = (material: Material) => {
    const exists = selected.some(item => item.id === material.id)
    if (exists) { setSelected(selected.filter(item => item.id !== material.id)); return }
    if (selected.length >= maxCount) return
    setSelected([...selected, material])
  }

  const search = (next: Filters) => { setFilters(next); void load(1, next) }

  const columns = [
    {
      title: '选择', width: 64, fixed: 'left' as const,
      render: (_: unknown, record: Material) => {
        const checked = selected.some(item => item.id === record.id)
        return <Checkbox checked={checked} disabled={!checked && selected.length >= maxCount} onChange={() => toggle(record)} />
      },
    },
    {
      title: '素材', minWidth: 280,
      render: (_: unknown, record: Material) => <Space align="start" size={10}>
        {record.coverPreviewUrl
          ? <Image src={record.coverPreviewUrl} width={56} height={56} style={{ objectFit: 'cover', borderRadius: 4 }} />
          : <div style={{ width: 56, height: 56, borderRadius: 4, background: 'var(--crm-bg-sunken)' }} />}
        <span><Typography.Text strong>{record.title}</Typography.Text><br />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{record.summary || '无摘要'}</Typography.Text></span>
      </Space>,
    },
    { title: '素材编号', dataIndex: 'materialNo', width: 170 },
    { title: '类型', dataIndex: 'materialTypeName', width: 130 },
    { title: '状态', width: 100, render: (_: unknown, record: Material) => statusTag(record.status) },
    { title: '创建人', dataIndex: 'ownerName', width: 120 },
    { title: '更新时间', width: 170, render: (_: unknown, record: Material) => <DateTimeText value={record.updateTime} /> },
  ]

  return <Modal
    title="素材浏览 · 选择参考素材"
    open={open}
    onCancel={onCancel}
    width="min(1180px, calc(100vw - 32px))"
    okText={`确认选择${selected.length ? `（${selected.length}）` : ''}`}
    cancelText="取消"
    okButtonProps={{ disabled: !selected.length }}
    onOk={() => onConfirm(selected)}
    styles={{ body: { maxHeight: 'calc(100vh - 240px)', overflowY: 'auto' } }}
  >
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="标题、摘要或可检索字段"
          style={{ width: 240 }}
          value={filters.keyword}
          onChange={event => setFilters({ ...filters, keyword: event.target.value })}
          onPressEnter={() => search(filters)}
        />
        <Select
          allowClear showSearch optionFilterProp="label" placeholder="素材类型" style={{ width: 170 }}
          value={filters.materialTypeId}
          options={types.map(type => ({ value: type.id, label: type.name }))}
          onChange={value => search({ ...filters, materialTypeId: value })}
        />
        <Select
          allowClear placeholder="状态" style={{ width: 130 }}
          value={filters.status}
          options={statusOptions}
          onChange={value => search({ ...filters, status: value })}
        />
        <Select
          allowClear placeholder="来源" style={{ width: 150 }}
          value={filters.source}
          options={sourceOptions}
          onChange={value => search({ ...filters, source: value })}
        />
        <Button type="primary" loading={loading} onClick={() => search(filters)}>查询</Button>
        <Button onClick={() => search({})}>重置</Button>
      </Space>

      {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(pageNo)}>重试</Button>} />}

      <Typography.Text type="secondary">
        已选 {selected.length} / {maxCount} 项；选中的素材会随作品一起提交，审批人可在审批详情中查看。
      </Typography.Text>

      <Spin spinning={loading}>
        <Table
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 1000 }}
          locale={{ emptyText: <Empty description="暂无素材" /> }}
        />
      </Spin>
      <Pagination
        current={pageNo}
        pageSize={PAGE_SIZE}
        total={total}
        showSizeChanger={false}
        showTotal={value => `共 ${value} 条`}
        onChange={page => void load(page)}
      />
    </Space>
  </Modal>
}
