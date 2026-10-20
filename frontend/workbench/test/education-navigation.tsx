// UTF-8. Synthetic server menus, using the production navigation restriction.
import { useEffect,useState } from 'react'
import { createRoot } from 'react-dom/client'
import { buildMenuTree,type RawMenu } from '../src/services/api'
import { readEducationNavigationContext,replaceDeliveryEmployeeEntry } from '../src/services/educationTeamNavigation'
import { findMenuByPath } from '../src/services/menu'
function Fixture(){
 const [menus,setMenus]=useState<RawMenu[]>(),[error,setError]=useState(''),[attempt,setAttempt]=useState(0)
 const params=new URLSearchParams(location.search)
 useEffect(()=>{setError('');setMenus(undefined);readEducationNavigationContext().then(context=>{
  const old:RawMenu={id:6814,parentId:6735,name:'下属员工',path:'subordinate-sales',component:'zsjos/subordinateSales/index',visible:true,children:[]}
  const education:RawMenu={id:602371,parentId:6735,name:'教务团队',path:'education-team',component:'zsjos/educationTeam/index',visible:true,children:[]}
  setMenus(replaceDeliveryEmployeeEntry([{id:6735,parentId:0,name:'学生交付中心',path:'/zsjos',visible:true,children:params.has('ungranted')?[old]:[old,education]}],context.delivery))
 }).catch(cause=>setError(cause.message))},[attempt])
 if(error)return <div><p>{error}</p><button onClick={()=>setAttempt(x=>x+1)}>重试</button></div>
 if(!menus)return <p>加载导航</p>
 const tree=buildMenuTree(menus),pages=tree.flatMap(menu=>menu.children),path=params.get('path')
 return <><nav>{pages.map(page=><a key={page.id} href={'?path='+page.path}>{page.name}</a>)}</nav>{path&&<p>{findMenuByPath(tree,path)?'允许打开':'无权打开'}</p>}{!pages.length&&<p>暂无授权入口</p>}</>
}
createRoot(document.getElementById('root')!).render(<Fixture/>)
