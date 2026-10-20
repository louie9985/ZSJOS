import { http, unwrap, type RawMenu } from './api'

export async function readEducationNavigationContext(): Promise<{delivery:boolean}> {
 const context=unwrap<{delivery:boolean}>(await http.get('/zsjos/education-team/navigation-context'))
 if(!context || typeof context.delivery!=='boolean')throw new Error('教务导航归属暂时无法确认，请重试')
 return context
}

/** Restrict supported entries; never add menus or derive permissions from account labels. */
export function replaceDeliveryEmployeeEntry(menus:RawMenu[], delivery:boolean):RawMenu[] {
 if(!delivery)return menus
 return menus.flatMap(menu=>{
  if(menu.component==='zsjos/subordinateSales/index'
    || menu.path==='subordinate-sales' || menu.path==='/zsjos/subordinate-sales')return []
  const children=replaceDeliveryEmployeeEntry(menu.children??[],true)
  if(menu.children?.length && !children.length && !menu.component)return []
  return [{...menu,children}]
 })
}
