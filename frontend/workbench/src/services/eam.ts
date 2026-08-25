import type { EamCategory } from './api'

/** 资产状态 */
export const ASSET_STATUS = {
  IDLE: 0, IN_USE: 1, LENT: 2, REPAIRING: 3, PENDING_SCRAP: 4, SCRAPPED: 5, LOST: 6, FROZEN: 7
} as const

/** 流转类型 */
export const TRANSFER_TYPE = { RECEIVE: 1, RETURN: 2, BORROW: 3, GIVE_BACK: 4, ALLOCATE: 5 } as const

/** 需要审批的流转类型，与后端 EamTransferTypeEnum.NEED_APPROVAL 保持一致 */
export const NEED_APPROVAL_TYPES: readonly number[] = [TRANSFER_TYPE.RECEIVE, TRANSFER_TYPE.BORROW, TRANSFER_TYPE.ALLOCATE]

/** 领用/借用/调拨需要指定接收方；退还/归还只是把资产收回，不需要 */
export const NEED_RECEIVER_TYPES: readonly number[] = [TRANSFER_TYPE.RECEIVE, TRANSFER_TYPE.BORROW, TRANSFER_TYPE.ALLOCATE]

/** 流转单状态 */
export const TRANSFER_STATUS = { APPROVING: 0, APPROVED: 1, REJECTED: 2, CANCELLED: 3 } as const

/** 报废单状态 */
export const SCRAP_STATUS = { APPROVING: 0, SCRAPPED: 1, REJECTED: 2 } as const

/** 盘点范围类型 */
export const SCOPE_TYPE = { ALL: 1, DEPT: 2, CATEGORY: 3, LOCATION: 4 } as const

/** 盘点结果 */
export const INVENTORY_RESULT = { UNCHECKED: 0, NORMAL: 1, LOCATION_MISMATCH: 2, NOT_FOUND: 3 } as const

/** 分类自定义字段类型 */
export const FIELD_TYPE = { TEXT: 1, TEXTAREA: 2, NUMBER: 3, DATE: 4, SELECT: 5, FILE: 6 } as const

/** 管理模式 */
export const MANAGEMENT_MODE = { SINGLE: 1, BATCH: 2 } as const

export const SCRAP_STATUS_LABELS: Record<number, string> = { 0: '审批中', 1: '已报废', 2: '已驳回' }
export const SCRAP_STATUS_COLORS: Record<number, string> = { 0: 'warning', 1: 'error', 2: 'default' }

export const INVENTORY_RESULT_LABELS: Record<number, string> = { 0: '未盘', 1: '正常', 2: '位置不符', 3: '未找到' }
export const INVENTORY_RESULT_COLORS: Record<number, string> = { 0: 'default', 1: 'success', 2: 'warning', 3: 'error' }

export const FIELD_TYPE_LABELS: Record<number, string> = {
  1: '单行文本', 2: '多行文本', 3: '数字', 4: '日期', 5: '下拉选择', 6: '图片/文件'
}

/** 资产变更类型 */
export const CHANGE_TYPE_LABELS: Record<number, string> = {
  0: '创建', 1: '编辑', 2: '领用', 3: '退还', 4: '借用', 5: '归还', 6: '调拨', 7: '维修',
  8: '维修完成', 9: '申请报废', 10: '报废通过', 11: '报废驳回', 12: '盘点', 13: '标记丢失', 14: '冻结', 15: '解冻'
}

export type EamTreeNode<T> = T & { children: Array<EamTreeNode<T>> }

/**
 * 把扁平的 id/parentId 列表构造成树。根节点为 parentId 不在列表中的项，
 * 因此后端返回的子集（例如按权限过滤后的分类）也能得到完整的一棵树。
 */
export function buildEamTree<T extends { id: number; parentId: number }>(list: T[]): Array<EamTreeNode<T>> {
  const map = new Map<number, EamTreeNode<T>>()
  for (const item of list) map.set(item.id, { ...item, children: [] })
  const roots: Array<EamTreeNode<T>> = []
  for (const item of list) {
    const node = map.get(item.id)!
    const parent = map.get(item.parentId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

/** 在分类树中按 id 查找节点，用于读取所选分类的 managementMode / unit */
export function findCategory(nodes: Array<EamTreeNode<EamCategory>>, id?: number): EamTreeNode<EamCategory> | undefined {
  if (id == null) return undefined
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findCategory(node.children, id)
    if (found) return found
  }
  return undefined
}

export type TreeSelectNode = { title: string; value: number; children: TreeSelectNode[] }

/** 把 id/name 树转成 antd TreeSelect 的 title/value 结构 */
export function toTreeSelectData<T extends { id: number; name: string }>(nodes: Array<EamTreeNode<T>>): TreeSelectNode[] {
  return nodes.map(node => ({ title: node.name, value: node.id, children: toTreeSelectData(node.children) }))
}

/** 按关键字过滤分类树，保留命中节点的祖先链 */
export function filterCategoryTree(nodes: Array<EamTreeNode<EamCategory>>, keyword: string): Array<EamTreeNode<EamCategory>> {
  const search = keyword.trim().toLowerCase()
  if (!search) return nodes
  const walk = (list: Array<EamTreeNode<EamCategory>>): Array<EamTreeNode<EamCategory>> =>
    list.flatMap(node => {
      const children = walk(node.children)
      const matched = `${node.name} ${node.code}`.toLowerCase().includes(search)
      return matched || children.length ? [{ ...node, children }] : []
    })
  return walk(nodes)
}

/**
 * 按编号规则拼一个示例编号，让管理员保存前就能看清格式。
 * categoryCode 为空时用 XX 占位，与 admin 端保持一致。
 */
export function previewAssetCode(rule: { prefix?: string; useCategoryCode?: boolean; dateFormat?: string; serialLength?: number; separator?: string; currentSerial?: number }, categoryCode?: string, now = new Date()) {
  const separator = rule.separator || '-'
  const segments: string[] = []
  if (rule.prefix) segments.push(rule.prefix)
  if (rule.useCategoryCode) segments.push(categoryCode || 'XX')
  if (rule.dateFormat) {
    const year = String(now.getFullYear())
    const month = String(now.getMonth() + 1).padStart(2, '0')
    segments.push(rule.dateFormat === 'yyyyMM' ? `${year}${month}` : year)
  }
  const next = (rule.currentSerial ?? 0) + 1
  segments.push(String(next).padStart(rule.serialLength ?? 4, '0'))
  return segments.join(separator)
}

/**
 * 分类切换后丢弃不再属于新分类的扩展字段值，避免提交时被后端拒绝。
 */
export function pruneExtFields(values: Record<string, unknown>, allowedKeys: string[]): Record<string, unknown> {
  const allowed = new Set(allowedKeys)
  const next: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(values)) {
    if (allowed.has(key)) next[key] = value
  }
  return next
}
