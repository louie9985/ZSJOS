import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Select, Skeleton, Space, Switch, Tag, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import { fmsConfig } from '../../services/fms'
import type { FmsAuxiliaryTypeVO, FmsAuxiliaryItemOptionVO } from '../../services/fms/types'
import { useFmsAccountSet, useFmsResource } from '../../services/useFmsAccountSet'
import type { ColumnsType } from 'antd/es/table'
import { FMS_SUBJECT_STATUS } from '../../services/fms/constants'
import { saveBlob } from '../../services/download'
import FmsImportModal from '../../components/fms/FmsImportModal'

export default function FmsConfigAuxiliaryPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const { data: types, loading, error, reload } = useFmsResource<FmsAuxiliaryTypeVO[]>(
    (id) => fmsConfig.auxiliaryType.list(id)
  )

  // 当前选中的辅助核算类别（主从布局左侧）
  const [currentType, setCurrentType] = useState<FmsAuxiliaryTypeVO>()
  // 类别表单
  const [typeModalOpen, setTypeModalOpen] = useState(false)
  const [savingType, setSavingType] = useState(false)
  const [typeForm] = Form.useForm<{ name: string }>()
  const editingTypeId = useRef<number | undefined>(undefined)
  const version = useRef(0)

  // 右侧项目列表
  const [itemList, setItemList] = useState<FmsAuxiliaryItemOptionVO[]>([])
  const [itemSearch, setItemSearch] = useState('')
  const [itemLoading, setItemLoading] = useState(false)
  const [itemModalOpen, setItemModalOpen] = useState(false)
  const [savingItem, setSavingItem] = useState(false)
  const [itemForm] = Form.useForm()
  const editingItemId = useRef<number | undefined>(undefined)
  const [importOpen, setImportOpen] = useState(false)

  const canCreate = writable && permissions.includes('fms:config:auxiliary:create')
  const canUpdate = writable && permissions.includes('fms:config:auxiliary:update')
  const canDelete = writable && permissions.includes('fms:config:auxiliary:delete')
  const canItemCreate = writable && permissions.includes('fms:config:auxiliary:create')
  const canItemUpdate = writable && permissions.includes('fms:config:auxiliary:update')
  const canItemDelete = writable && permissions.includes('fms:config:auxiliary:delete')
  const canImport = writable && permissions.includes('fms:config:auxiliary:import')
  const canExport = permissions.includes('fms:config:auxiliary:export')

  // 加载当前类别的项目列表
  const loadItems = useCallback(async (typeId: number | undefined, search?: string) => {
    if (!accountSetId || !typeId) { setItemList([]); return }
    const v = ++version.current
    setItemLoading(true)
    try {
      const result = await fmsConfig.auxiliaryItem.page({ accountSetId, auxiliaryTypeId: typeId, search: search || itemSearch })
      if (v !== version.current) return
      setItemList(result.list)
    } catch (e) {
      if (v !== version.current) return
      message.error(e instanceof Error ? e.message : '加载项目失败')
      setItemList([])
    } finally {
      if (v === version.current) setItemLoading(false)
    }
  }, [accountSetId, itemSearch])

  // 默认选中第一个类别
  useEffect(() => {
    if (!currentType && types?.length) {
      const first = types[0]
      setCurrentType(first)
      setTimeout(() => loadItems(first.id), 0)
    }
  }, [types, currentType, loadItems])

  // 选中类别时加载项目
  const selectType = useCallback((type: FmsAuxiliaryTypeVO) => {
    setCurrentType(type)
    setItemSearch('')
    setTimeout(() => loadItems(type.id), 0)
  }, [loadItems])

  // 类别 CRUD
  const openTypeCreate = useCallback(() => {
    editingTypeId.current = undefined
    typeForm.resetFields()
    setTypeModalOpen(true)
  }, [typeForm])

  const openTypeEdit = useCallback((record: FmsAuxiliaryTypeVO) => {
    editingTypeId.current = record.id
    typeForm.setFieldsValue({ name: record.name })
    setTypeModalOpen(true)
  }, [typeForm])

  const saveType = useCallback(async () => {
    if (!accountSetId) return
    const values = await typeForm.validateFields()
    setSavingType(true)
    try {
      if (editingTypeId.current != null) {
        await fmsConfig.auxiliaryType.update({ name: values.name, id: editingTypeId.current, accountSetId })
        message.success('类别已更新')
      } else {
        await fmsConfig.auxiliaryType.create({ name: values.name, accountSetId })
        message.success('类别已添加')
      }
      setTypeModalOpen(false)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setSavingType(false)
    }
  }, [accountSetId, typeForm, reload])

  const deleteType = useCallback((id: number) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '确认删除', content: '确定要删除该辅助核算类别吗？',
      okType: 'danger', okText: '删除',
      onOk: async () => {
        try {
          await fmsConfig.auxiliaryType.delete(accountSetId, id)
          message.success('已删除')
          if (currentType?.id === id) setCurrentType(undefined)
          reload()
        } catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }, [accountSetId, currentType, reload])

  // 项目 CRUD
  const openItemCreate = useCallback(() => {
    editingItemId.current = undefined
    itemForm.resetFields()
    setItemModalOpen(true)
  }, [itemForm])

  const openItemEdit = useCallback((record: FmsAuxiliaryItemOptionVO) => {
    editingItemId.current = record.id
    itemForm.setFieldsValue({
      code: record.code, name: record.name, remark: record.remark,
      specification: record.specification, unit: record.unit, status: record.status
    })
    setItemModalOpen(true)
  }, [itemForm])

  const saveItem = useCallback(async () => {
    if (!accountSetId || !currentType) return
    const values = await itemForm.validateFields()
    setSavingItem(true)
    try {
      const payload: FmsAuxiliaryItemOptionVO = {
        accountSetId, auxiliaryTypeId: currentType.id!, ...values
      }
      if (editingItemId.current != null) {
        await fmsConfig.auxiliaryItem.update({ ...payload, id: editingItemId.current })
        message.success('项目已更新')
      } else {
        await fmsConfig.auxiliaryItem.create(payload)
        message.success('项目已添加')
      }
      setItemModalOpen(false)
      loadItems(currentType.id)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setSavingItem(false)
    }
  }, [accountSetId, currentType, itemForm, loadItems])

  const deleteItem = useCallback((id: number) => {
    if (!accountSetId || !currentType) return
    Modal.confirm({
      title: '确认删除', content: '确定要删除该项目吗？',
      okType: 'danger', okText: '删除',
      onOk: async () => {
        try {
          await fmsConfig.auxiliaryItem.delete(accountSetId, [id])
          message.success('已删除')
          loadItems(currentType.id)
        } catch (e) { message.error(e instanceof Error ? e.message : '删除失败'); throw e }
      }
    })
  }, [accountSetId, currentType, loadItems])

  // 项目状态切换
  const toggleItemStatus = useCallback(async (record: FmsAuxiliaryItemOptionVO, checked: boolean) => {
    if (!accountSetId || !currentType) return
    try {
      await fmsConfig.auxiliaryItem.updateStatus(accountSetId, record.id, checked ? FMS_SUBJECT_STATUS.ENABLED : FMS_SUBJECT_STATUS.DISABLED)
      message.success(checked ? '已启用' : '已禁用')
      loadItems(currentType.id)
    } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') }
  }, [accountSetId, currentType, loadItems])

  const exportItems = useCallback(async () => {
    if (!accountSetId || !currentType) return
    try {
      const blob = await fmsConfig.auxiliaryItem.exportExcel({ accountSetId, auxiliaryTypeId: currentType.id!, search: itemSearch })
      saveBlob(blob, `辅助核算项目-${currentType.name}.xls`)
    } catch (e) { message.error(e instanceof Error ? e.message : '导出失败') }
  }, [accountSetId, currentType, itemSearch])

  // 类别表格列
  const typeColumns: ColumnsType<FmsAuxiliaryTypeVO> = [{
    key: 'name', render: (_, row) => (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.name}</span>
        {row.systemPreset ? <Tag color="blue" style={{ marginInlineStart: 6 }}>预设</Tag> : null}
        {(canUpdate || canDelete) && !row.systemPreset && (
          <Space size={0}>
            {canUpdate && <Button type="text" size="small" onClick={e => { e.stopPropagation(); openTypeEdit(row) }}>编辑</Button>}
            {canDelete && row.id != null && <Button type="text" size="small" danger onClick={e => { e.stopPropagation(); deleteType(row.id!) }}>删除</Button>}
          </Space>
        )}
      </div>
    )
  }]

  // 项目表格列
  const itemColumns: ColumnsType<FmsAuxiliaryItemOptionVO> = [
    { title: '编码', dataIndex: 'code', width: 130 },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    { title: '规格', dataIndex: 'specification', width: 110 },
    { title: '单位', dataIndex: 'unit', width: 90, align: 'center' },
    {
      title: '状态', dataIndex: 'status', width: 90, align: 'center',
      render: (val: number | undefined, row) => <Switch
        size="small" checked={val === FMS_SUBJECT_STATUS.ENABLED}
        disabled={!canItemUpdate}
        onChange={checked => toggleItemStatus(row, checked)}
      />
    },
    ...(canItemUpdate || canItemDelete ? [{
      title: '操作', width: 110, align: 'center' as const,
      render: (_: unknown, row: FmsAuxiliaryItemOptionVO) => (
        <Space size={4}>
          {canItemUpdate && <Button type="link" size="small" onClick={() => openItemEdit(row)}>编辑</Button>}
          {canItemDelete && row.id != null && <Button type="link" size="small" danger onClick={() => deleteItem(row.id)}>删除</Button>}
        </Space>
      )
    }] : [])
  ]

  return (
    <section className="workspace-page fms-page">
      <div style={{ display: 'grid', gridTemplateColumns: '280px minmax(0, 1fr)', gap: 16 }}>
        {/* 左侧：辅助核算类别 */}
        <div className="fms-table-area">
          <div style={{ marginBlockEnd: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontWeight: 500 }}>核算类别</span>
            <Space>
              {canCreate && <Button size="small" icon={<PlusOutlined/>} onClick={openTypeCreate}>新增</Button>}
              <Button size="small" icon={<ReloadOutlined/>} onClick={reload}/>
            </Space>
          </div>
          {loading && !types
            ? <Skeleton active paragraph={{ rows: 4 }} />
            : error
              ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={reload}>重试</Button>} />
              : !types?.length
                ? <Empty description="暂无类别"/>
                : <FmsProTable<FmsAuxiliaryTypeVO>
                    rowKey="id" columns={typeColumns} dataSource={types} pagination={false}
                    size="small" showHeader={false}
                    rowClassName={row => row.id === currentType?.id ? 'row-selected' : ''}
                    onRow={row => ({ onClick: () => selectType(row) })}
                    style={{ cursor: 'pointer' }}
                  />}
        </div>

        {/* 右侧：项目 */}
        <div className="fms-table-area">
          <div style={{ marginBlockEnd: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <span style={{ fontWeight: 500 }}>{currentType ? `${currentType.name} · 核算项目` : '核算项目'}</span>
            <Space>
              <Input.Search size="small" placeholder="搜索编码/名称" value={itemSearch}
                onChange={e => setItemSearch(e.target.value)}
                onSearch={v => loadItems(currentType?.id, v)}
                style={{ width: 180 }} allowClear/>
              {canItemCreate && currentType && <Button size="small" icon={<PlusOutlined/>} onClick={openItemCreate}>新增项目</Button>}
              {canImport && currentType && <Button size="small" icon={<UploadOutlined/>} onClick={() => setImportOpen(true)}>导入</Button>}
              {currentType && canExport && <Button size="small" icon={<DownloadOutlined/>} onClick={exportItems}>导出</Button>}
            </Space>
          </div>
          {!currentType
            ? <Empty description="请选择左侧类别" style={{ padding: 24 }}/>
            : <FmsProTable<FmsAuxiliaryItemOptionVO>
                rowKey="id" columns={itemColumns} dataSource={itemList} pagination={false}
                size="small" loading={itemLoading} bordered
              />}
        </div>
      </div>

      {/* 类别表单 */}
      <Modal title={editingTypeId.current != null ? '编辑类别' : '新增类别'} open={typeModalOpen}
        onCancel={() => setTypeModalOpen(false)} onOk={saveType} confirmLoading={savingType} destroyOnClose width={720}>
        <Form form={typeForm} layout="vertical">
          <Form.Item name="name" label="类别名称" rules={[{ required: true, message: '请输入类别名称' }]}>
            <Input placeholder="请输入类别名称" maxLength={50}/>
          </Form.Item>
        </Form>
      </Modal>

      {/* 项目表单 */}
      <Modal title={editingItemId.current != null ? '编辑项目' : '新增项目'} open={itemModalOpen}
        onCancel={() => setItemModalOpen(false)} onOk={saveItem} confirmLoading={savingItem} destroyOnClose width={760}>
        <Form form={itemForm} layout="vertical">
          <Form.Item name="code" label="项目编码" rules={[{ required: true, message: '请输入编码' }]}>
            <Input maxLength={30} placeholder="如 001"/>
          </Form.Item>
          <Form.Item name="name" label="项目名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={50}/>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={200}/>
          </Form.Item>
          <Space size="large">
            <Form.Item name="specification" label="规格">
              <Input maxLength={50} style={{ width: 160 }}/>
            </Form.Item>
            <Form.Item name="unit" label="单位">
              <Input maxLength={10} style={{ width: 100 }}/>
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 导入弹窗 */}
      <FmsImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="导入辅助核算项目"
        onGetTemplate={() => fmsConfig.auxiliaryItem.getImportTemplate()}
        onUpload={(file) => fmsConfig.auxiliaryItem.import(accountSetId!, file)}
        onSuccess={() => loadItems(currentType?.id)}
      />
    </section>
  )
}
