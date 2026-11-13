import { describe, expect, it } from 'vitest'
import { examClassTree, selectedExamClassIds } from './examClassSelection'

describe('exam class grouping', () => {
  it('retains unavailable original classes for removal without allowing new unavailable classes', () => {
    const unavailable = [{id:7,name:'已结束旧班',selectable:false},{id:8,name:'不可新增班',plannerId:1,selectable:false}]
    expect(examClassTree(unavailable,[7]).map(n=>n.disabled)).toEqual([false,true])
    expect(selectedExamClassIds(['class:7','class:8'],unavailable,[7])).toEqual([7])
    expect(selectedExamClassIds([],unavailable,[7])).toEqual([])
  })
  const rows = [
    {id:1,name:'同名班',groupId:10,groupName:'一组',plannerId:7,plannerName:'老师甲'},
    {id:2,name:'同名班',groupId:20,groupName:'二组',plannerId:8},
    {id:3,name:'待分配',groupId:10,groupName:'一组'},
    {id:4,name:'独立班',plannerId:7},
  ]
  it('uses IDs for groups and leaves and retains disabled/unassigned classes', () => {
    const tree=examClassTree(rows)
    expect(tree.map(node=>node.value)).toEqual(['group:10','group:20','class:4'])
    expect(tree[0].children?.map(node=>node.value)).toEqual(['class:1','class:3'])
    expect(tree[0].children?.[1].disabled).toBe(true)
    expect(tree[2].title).toContain('独立班')
    expect(tree[2].children).toBeUndefined()
    expect(selectedExamClassIds([tree[2].value], rows)).toEqual([4])
    expect(examClassTree([])).toEqual([])
  })
  it('only serializes available class leaves, never groups or disabled IDs', () => {
    expect(selectedExamClassIds(['group:10','class:1','class:2','class:3','class:1'],rows)).toEqual([1,2])
    expect(examClassTree([rows[2]])[0].disabled).toBe(true)
  })
})
