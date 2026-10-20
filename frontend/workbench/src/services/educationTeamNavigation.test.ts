import { describe,it,expect } from 'vitest'
import { replaceDeliveryEmployeeEntry } from './educationTeamNavigation'
import { buildMenuTree,type RawMenu } from './api'
import { findMenuByPath,buildTwoLevelNavigation } from './menu'
const page=(id:number,name:string,path:string,component:string,permission:string)=>({id,parentId:0,name,path,component,visible:true,children:[]} as RawMenu)
const old=page(1,'下属员工','subordinate-sales','zsjos/subordinateSales/index','zsjos:subordinate-sales:query')
const education=page(2,'教务团队','education-team','zsjos/educationTeam/index','zsjos:education-team:query')
describe('delivery entry replacement',()=>{
 it('removes old entry and old route while keeping authorized education entry',()=>{
  const source=[{...page(10,'学生交付中心','/zsjos','',''),children:[old,education]}]
  const menus=buildMenuTree(replaceDeliveryEmployeeEntry(source,true))
  expect(findMenuByPath(menus,'/zsjos/subordinate-sales')).toBeUndefined()
  expect(findMenuByPath(menus,'/zsjos/education-team')?.name).toBe('教务团队')
  expect(buildTwoLevelNavigation(menus)[0].pages.map(p=>p.label)).toEqual(['教务团队'])
  expect(source[0].children).toHaveLength(2)
 })
 it('does not manufacture education access when permission was not assigned',()=>{expect(replaceDeliveryEmployeeEntry([old],true)).toEqual([])})
 it('preserves sales entry for sales users',()=>{const menus=[old,education];expect(replaceDeliveryEmployeeEntry(menus,false)).toBe(menus)})
 it('removes nested and absolute old entries and preserves unrelated pages',()=>{const other=page(3,'我的学员','students','student','student:query');expect(replaceDeliveryEmployeeEntry([{...old,path:'/zsjos/subordinate-sales'},other],true)).toEqual([{...other,children:[]}])})
})
