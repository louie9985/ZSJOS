import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Empty, Form, Input, message, Modal, Tag } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsConfig } from '../../services/fms'
import type { FmsVoucherTemplateVO, FmsVoucherTemplateCategoryVO } from '../../services/fms/types'

export default function FmsConfigVoucherTemplatePage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const [templates, setTemplates] = useState<FmsVoucherTemplateVO[]>([])
  const [categories, setCategories] = useState<FmsVoucherTemplateCategoryVO[]>([])
  const [currentCategory, setCurrentCategory] = useState<FmsVoucherTemplateCategoryVO>()
  const [loading, setLoading] = useState(false)
  const [categoryModal, setCategoryModal] = useState(false)
  const [categoryForm] = Form.useForm()
  const [editingCategory, setEditingCategory] = useState<FmsVoucherTemplateCategoryVO | null>(null)
  const version = useRef(0)

  const getList = useCallback(async () => {
    if (!accountSetId) {
      setTemplates([]); setCategories([]); setCurrentCategory(undefined); return
    }
    const v = ++version.current
    setLoading(true)
    try {
      const [t, c] = await Promise.all([
        fmsConfig.voucherTemplate.list(accountSetId),
        fmsConfig.voucherTemplateCategory.list(accountSetId)
      ])
      if (v !== version.current) return
      setTemplates(t); setCategories(c)
      setCurrentCategory(prev => c.find(x => x.id === prev?.id) || c[0])
    } catch (e) {
      if (v !== version.current) return
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      if (v === version.current) setLoading(false)
    }
  }, [accountSetId])

  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    setTimeout(() => getList(), 0)
  }

  const currentTemplates = templates.filter(t => t.categoryId === currentCategory?.id)

  const openCategoryForm = useCallback((mode: 'create' | 'update', row?: FmsVoucherTemplateCategoryVO) => {
    setEditingCategory(row || null)
    setCategoryModal(true)
    if (mode === 'update' && row) {
      categoryForm.setFieldsValue({ name: row.name })
    } else {
      categoryForm.resetFields()
    }
  }, [categoryForm])

  const submitCategory = useCallback(async () => {
    if (!accountSetId) return
    try {
      const values = await categoryForm.validateFields()
      if (editingCategory) {
        await fmsConfig.voucherTemplateCategory.update({ id: editingCategory.id, accountSetId, ...values })
        message.success('分类更新成功')
      } else {
        await fmsConfig.voucherTemplateCategory.create({ accountSetId, ...values })
        message.success('分类创建成功')
      }
      setCategoryModal(false)
      getList()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    }
  }, [accountSetId, editingCategory, categoryForm, getList])

  const handleDeleteCategory = useCallback(async (row: FmsVoucherTemplateCategoryVO) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '删除分类',
      content: `确认删除「${row.name}」?`,
      onOk: async () => {
        try {
          await fmsConfig.voucherTemplateCategory.delete(accountSetId, row.id!)
          message.success('删除成功')
          getList()
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败')
        }
      }
    })
  }, [accountSetId, getList])

  const handleDeleteTemplate = useCallback(async (row: FmsVoucherTemplateVO) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '删除模板',
      content: `确认删除「${row.name}」?`,
      onOk: async () => {
        try {
          await fmsConfig.voucherTemplate.delete(accountSetId, row.id!)
          message.success('删除成功')
          getList()
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败')
        }
      }
    })
  }, [accountSetId, getList])

  const canCreateCategory = writable && permissions.includes('fms:config:voucher-template-category:create')
  const canUpdateCategory = writable && permissions.includes('fms:config:voucher-template-category:update')
  const canDeleteCategory = writable && permissions.includes('fms:config:voucher-template-category:delete')
  const canDeleteTemplate = writable && permissions.includes('fms:config:voucher-template:delete')

  const catColumns: ColumnsType<FmsVoucherTemplateCategoryVO> = [
    {
      key: 'name', render: (_: unknown, row: FmsVoucherTemplateCategoryVO) => (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.name}</span>
            <Tag style={{ marginInlineStart: 6 }}>{templates.filter(t => t.categoryId === row.id).length}</Tag>
          </div>
          {(canUpdateCategory || canDeleteCategory) && (
            <div style={{ marginInlineStart: 4, flexShrink: 0 }}>
              {canUpdateCategory && <Button type="text" size="small" icon={<EditOutlined/>} onClick={e => { e.stopPropagation(); openCategoryForm('update', row) }}/>}
              {canDeleteCategory && <Button type="text" size="small" danger icon={<DeleteOutlined/>} onClick={e => { e.stopPropagation(); handleDeleteCategory(row) }}/>}
            </div>
          )}
        </div>
      )
    }
  ]

  const tplColumns: ColumnsType<FmsVoucherTemplateVO> = [
    { title: '模板名称', dataIndex: 'name', ellipsis: true },
    { title: '分录数', align: 'center', width: 100, render: (_: unknown, row: FmsVoucherTemplateVO) => row.entries?.length ?? 0 },
    ...(canDeleteTemplate
      ? [{ title: '操作', align: 'center' as const, width: 120, key: 'actions', render: (_: unknown, row: FmsVoucherTemplateVO) => <Button type="link" size="small" danger onClick={() => handleDeleteTemplate(row)}>删除</Button> } satisfies ColumnsType<FmsVoucherTemplateVO>[number]]
      : [])
  ]

  return (
    <section className="workspace-page fms-page">
      <div style={{ display: 'grid', gridTemplateColumns: '320px minmax(0, 1fr)', gap: 16 }}>
        {/* 分类列表 */}
        <div className="fms-table-area">
          <div style={{ marginBlockEnd: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontWeight: 500 }}>凭证模板分类</span>
            {canCreateCategory && <Button size="small" icon={<PlusOutlined/>} onClick={() => openCategoryForm('create')}>新增</Button>}
          </div>
          <FmsProTable<FmsVoucherTemplateCategoryVO>
            rowKey="id"
            columns={catColumns}
            dataSource={categories}
            loading={loading}
            pagination={false}
            size="small"
            showHeader={false}
            rowClassName={row => row.id === currentCategory?.id ? 'row-selected' : ''}
            onRow={row => ({ onClick: () => setCurrentCategory(row) })}
            style={{ cursor: 'pointer' }}
          />
        </div>

        {/* 模板列表 */}
        <div className="fms-table-area">
          <div style={{ marginBlockEnd: 16, fontWeight: 500 }}>凭证模板</div>
          <FmsProTable<FmsVoucherTemplateVO>
            rowKey="id"
            columns={tplColumns}
            dataSource={currentTemplates}
            loading={loading}
            pagination={false}
            size="small"
            locale={{ emptyText: currentCategory ? '暂无凭证模板' : '请选择凭证模板分类' }}
          />
        </div>
      </div>

      {/* 分类表单 */}
      <Modal
        open={categoryModal}
        title={editingCategory ? '编辑分类' : '新增分类'}
        onCancel={() => setCategoryModal(false)}
        onOk={submitCategory}
        width={720}
        destroyOnClose
      >
        <Form form={categoryForm} layout="vertical" preserve={false}>
          <Form.Item label="分类名称" name="name" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input maxLength={50} placeholder="如：日常开支"/>
          </Form.Item>
        </Form>
      </Modal>
    </section>
  )
}
