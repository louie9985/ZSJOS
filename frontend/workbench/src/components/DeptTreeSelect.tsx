import { useEffect, useMemo, useState } from 'react'
import { TreeSelect } from 'antd'
import { api } from '../services/api'

type DeptNode = { id: number; name: string; parentId: number }

/** 部门树选择器，用于员工档案的部门归属选择。数据来自 /system/dept/simple-list。 */
export default function DeptTreeSelect({ value, onChange, ...props }: {
  value?: number
  onChange?: (value?: number) => void
  [key: string]: unknown
}) {
  const [tree, setTree] = useState<DeptNode[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    let mounted = true
    setLoading(true)
    api.hrm.deptSimpleList()
      .then((list) => { if (mounted) setTree(list) })
      .catch(() => { if (mounted) setTree([]) })
      .finally(() => { if (mounted) setLoading(false) })
    return () => { mounted = false }
  }, [])

  const treeData = useMemo(() => buildTree(tree), [tree])

  return <TreeSelect
    value={value}
    onChange={onChange}
    loading={loading}
    treeData={treeData}
    showSearch
    treeDefaultExpandAll
    treeNodeFilterProp="title"
    placeholder="请选择部门"
    {...props}
  />
}

function buildTree(list: DeptNode[]) {
  const map = new Map<number, { value: number; title: string; children: ReturnType<typeof buildTree> }>()
  for (const node of list) {
    map.set(node.id, { value: node.id, title: node.name, children: [] })
  }
  const roots: Array<{ value: number; title: string; children: ReturnType<typeof buildTree> }> = []
  for (const node of list) {
    const item = map.get(node.id)!
    const parent = map.get(node.parentId)
    if (parent) parent.children.push(item)
    else roots.push(item)
  }
  return roots
}
