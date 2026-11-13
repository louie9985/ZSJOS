import type { ExamClass } from './examCollection'

export interface ExamClassNode {
  value: string
  title: string
  searchText: string
  disabled?: boolean
  children?: ExamClassNode[]
}

export function examClassTree(classes: ExamClass[], retainedClassIds: number[] = []): ExamClassNode[] {
  const groups = new Map<string, ExamClassNode>()
  const nodes: ExamClassNode[] = []
  for (const item of classes) {
    const groupName = item.groupId == null ? '' : item.groupName || '班级组名称未提供'
    const planner = item.plannerId ? item.plannerName || '学习规划师名称未提供' : '未分配学习规划师'
    const available = item.selectable !== false && Boolean(item.plannerId)
    const historical = !available && retainedClassIds.includes(item.id)
    const leaf = { value: `class:${item.id}`, title: `${item.name} · ${historical ? '已选历史班级，可保留或移除' : planner}`, searchText: `${groupName} ${item.name} ${planner}`, disabled: !available && !historical }
    if (item.groupId == null) {
      nodes.push(leaf)
      continue
    }
    const key = `group:${item.groupId}`
    if (!groups.has(key)) {
      const group = { value: key, title: groupName, searchText: groupName, children: [] as ExamClassNode[] }
      groups.set(key, group)
      nodes.push(group)
    }
    groups.get(key)!.children!.push(leaf)
  }
  return nodes.map(node => node.children ? { ...node, disabled: node.children.every(child => child.disabled) } : node)
}

export function selectedExamClassIds(values: string[], classes: ExamClass[], retainedClassIds: number[] = []): number[] {
  const selected = new Set(values)
  return classes.filter(item => (retainedClassIds.includes(item.id) || item.selectable !== false && item.plannerId) && selected.has(`class:${item.id}`)).map(item => item.id)
}
