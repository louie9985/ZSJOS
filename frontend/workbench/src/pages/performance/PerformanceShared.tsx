import { Alert, Button, Drawer, Empty, Input, Select, Space, Spin, Tree, Typography } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined, ReloadOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { DataNode } from 'antd/es/tree'
import { performanceApi, money, percent, periodRange, type Group, type OrgNode, type Scope } from '../../services/salesPerformance'
import BusinessTable from '../../components/BusinessTable'
import './performance.css'
export function useResource<T>(loader: (signal: AbortSignal) => Promise<T>, dependencies: unknown[]) {
 const [data,setData]=useState<T>(), [error,setError]=useState(''), [loading,setLoading]=useState(true), [version,setVersion]=useState(0)
 useEffect(()=>{ const controller=new AbortController();setLoading(true);setError('');setData(undefined)
  loader(controller.signal).then(value=>{if(!controller.signal.aborted)setData(value)}).catch((e: unknown)=>{if(!controller.signal.aborted)setError(e instanceof Error?e.message:'加载失败，请重试')}).finally(()=>{if(!controller.signal.aborted)setLoading(false)})
  return ()=>controller.abort()
 // Requests are keyed by explicit primitive business parameters.
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[...dependencies,version])
 return {data,error,loading,reload:()=>setVersion(v=>v+1)}
}
export function Resource({state,children}:{state:{loading:boolean;error:string;reload:()=>void};children:ReactNode}) {
 if(state.loading)return <div className="performance-loading"><Spin tip="正在加载业绩数据" /></div>
 if(state.error)return <Alert type="error" showIcon title={state.error} action={<Button onClick={state.reload}>重试</Button>}/>
 return <>{children}</>
}
export function PerformanceShell({targets=false,children}:{targets?:boolean;children:(scope:Scope,node:OrgNode,reloadTree:()=>void,nodes:OrgNode[],select:(key:string)=>void)=>ReactNode}) {
 const tree=useResource(signal=>performanceApi.tree(targets,signal),[targets]);const [selected,setSelected]=useState<string>();const [keyword,setKeyword]=useState('');const [collapsed,setCollapsed]=useState(false);const [open,setOpen]=useState(false)
 const active=tree.data?.find(x=>x.key===selected&&x.selectable)??tree.data?.find(x=>x.selectable)
 const nodes=useMemo(()=>{const rows=tree.data??[];const keep=new Set<string>();for(const x of rows.filter(n=>!keyword||n.title.includes(keyword))){let current:OrgNode|undefined=x;while(current&&!keep.has(current.key)){keep.add(current.key);current=rows.find(n=>n.key===current?.parentKey)}}
 const build=(parent?:string):DataNode[]=>rows.filter(n=>(n.parentKey===parent||parent===undefined&&!rows.some(x=>x.key===n.parentKey))&&keep.has(n.key)).map(n=>({key:n.key,title:n.title,selectable:n.selectable,children:build(n.key)}));return build()
 },[tree.data,keyword])
 const content=<><Input.Search placeholder="搜索组织或人员" value={keyword} onChange={e=>setKeyword(e.target.value)} allowClear/><Resource state={tree}><Tree key={keyword} blockNode defaultExpandAll treeData={nodes} selectedKeys={active?[active.key]:[]} onSelect={keys=>{if(keys[0])setSelected(String(keys[0]));setOpen(false)}}/></Resource></>
 return <div className={`performance-shell ${collapsed?'is-collapsed':''}`}>
  <aside className="performance-tree"><Button type="text" aria-label={collapsed?'展开组织树':'收起组织树'} icon={collapsed?<MenuUnfoldOutlined/>:<MenuFoldOutlined/>} onClick={()=>setCollapsed(!collapsed)}/>{!collapsed&&content}</aside>
  <main className="performance-main"><div className="performance-mobile-tree"><Button icon={<MenuUnfoldOutlined/>} onClick={()=>setOpen(true)}>选择组织 / 人员</Button></div><Drawer title="统计范围" open={open} onClose={()=>setOpen(false)} placement="left">{content}</Drawer>
  <Resource state={tree}>{active?children({scopeType:active.scopeType,scopeId:active.scopeId},active,tree.reload,tree.data??[],setSelected):<Empty description={targets?'暂无可设置对象，请先配置销售组织及数据范围':'暂无授权业绩视图，请联系管理员配置权限和数据范围'}/>}</Resource></main>
 </div>
}
export function DateFilter({value,onChange,grain,onGrain}:{value:{start:string;end:string;cumulative?:boolean;periodKey?:string};onChange:(v:{start:string;end:string;cumulative?:boolean;periodKey?:string})=>void;grain?:'day'|'week'|'month';onGrain?:(v:'day'|'week'|'month')=>void}) {
 return <Space wrap className="performance-filter"><Select aria-label="统计周期" placeholder="选择周期" style={{width:130}} onChange={k=>onChange(periodRange(k))} options={[['today','今日'],['week','本周'],['month','本月'],['quarter','本季度'],['year','本年'],['all','累计'],['last7','近7日'],['last30','近30日'],['last60','近60日'],['last90','近90日']].map(([value,label])=>({value,label}))}/><Input type="date" aria-label="开始日期" value={value.start} onChange={e=>onChange({...value,start:e.target.value,cumulative:false,periodKey:undefined})}/><span>至</span><Input type="date" aria-label="结束日期" value={value.end} onChange={e=>onChange({...value,end:e.target.value,cumulative:false,periodKey:undefined})}/>{onGrain&&<Select aria-label="汇总粒度" value={grain} onChange={onGrain} options={[{value:'day',label:'按日'},{value:'week',label:'按周'},{value:'month',label:'按月'}]}/>}</Space>
}
export function Panel({title,extra,children}:{title:string;extra?:ReactNode;children:ReactNode}){return <section className="performance-panel"><header><Typography.Title level={5}>{title}</Typography.Title>{extra}</header>{children}</section>}
export function GroupChart({rows,tableKey,amount=true,onOpen}:{rows:Group[];tableKey:string;amount?:boolean;onOpen?:(row:Group)=>void}) {
 const max=Math.max(1,...rows.map(x=>amount?Number(x.amount):x.count))
 return <>{rows.length===0?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE}/>:<div className="performance-bars">{rows.map(x=><div key={x.key} className="performance-bar-row"><span title={x.label}>{onOpen?<Button type="link" onClick={()=>onOpen(x)}>{x.label}</Button>:x.label}</span><div><i style={{width:`${(amount?Number(x.amount):x.count)/max*100}%`}}/></div><strong>{amount?money(x.amount):x.count}</strong></div>)}</div>}
 <BusinessTable<Group> tableKey={tableKey} mode="compact" rowKey="key" dataSource={rows} pagination={{pageSize:10}} search={false} columns={[{title:'项目',dataIndex:'label'},{title:'数量',dataIndex:'count'},{title:amount?'成交金额':'',dataIndex:'amount',hideInTable:!amount,render:(_,x)=>money(x.amount)},{title:'占比',dataIndex:'share',render:(_,x)=>percent(x.share)}]}/></>
}
export function Refresh({onClick}:{onClick:()=>void}){return <Button icon={<ReloadOutlined/>} onClick={onClick}>刷新</Button>}

export function Donut({rows}:{rows:Group[]}){
 const colors=['#397bee','#8b67d9','#26a69a','#e8b443','#e27691','#8193b0'];const total=rows.reduce((n,x)=>n+Number(x.amount),0);let offset=0
 if(!total)return <Empty description="暂无成交金额" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
 return <div className="performance-donut"><svg viewBox="0 0 160 160" width="170" height="170" role="img" aria-label="成交来源金额占比">{rows.map((x,i)=>{const n=Number(x.amount)/total*377,start=offset;offset+=n;return <circle key={x.key} cx="80" cy="80" r="60" fill="none" stroke={colors[i%colors.length]} strokeWidth="22" strokeDasharray={`${n} ${377-n}`} strokeDashoffset={-start} transform="rotate(-90 80 80)"><title>{x.label}：{money(x.amount)}，{percent(Number(x.amount)/total)}</title></circle>})}<text x="80" y="84" textAnchor="middle" fill="currentColor">成交来源</text></svg><div>{rows.map((x,i)=><p key={x.key}><span style={{color:colors[i%colors.length]}}>● </span>{x.label}　{percent(Number(x.amount)/total)}</p>)}</div></div>
}
export function AverageTrend({series}:{series:Record<string,import('../../services/salesPerformance').Metric[]>}){
 const keys=['all','inbound','self','repurchase'],colors=['#397bee','#26a69a','#e8b443','#8b67d9'];const max=Math.max(1,...Object.values(series).flatMap(rows=>rows.map(x=>Number(x.average??0))))
 if(!Object.values(series).some(rows=>rows.some(row=>row.average!=null)))return <Empty description="暂无客单价趋势数据" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
 return <svg viewBox="0 0 800 190" className="performance-history" role="img" aria-label="各来源客单价趋势">{keys.map((key,i)=>{const rows=series[key]??[];return <g key={key}>{rows.slice(1).map((r,j)=>r.average==null||rows[j].average==null?null:<line key={`line-${r.key}`} x1={30+j*730/Math.max(1,rows.length-1)} y1={160-Number(rows[j].average)/max*130} x2={30+(j+1)*730/Math.max(1,rows.length-1)} y2={160-Number(r.average)/max*130} stroke={colors[i]} strokeWidth="2"/>)}{rows.map((r,j)=>r.average==null?null:<circle key={r.key} cx={30+j*730/Math.max(1,rows.length-1)} cy={160-Number(r.average)/max*130} r="3" fill={colors[i]}><title>{r.label} {['整体','线上引流','非引流','复购'][i]}：{money(r.average)}</title></circle>)}</g>})}</svg>
}

export function scopeTitle(node:OrgNode,nodes:OrgNode[]){const names=[node.title];const seen=new Set([node.key]);let current=nodes.find(x=>x.key===node.parentKey);while(current&&!seen.has(current.key)){seen.add(current.key);names.unshift(current.title);current=nodes.find(x=>x.key===current?.parentKey)}return names.join(' / ')}

export function Funnel({rows}:{rows:Group[]}){
 const max=Math.max(1,...rows.map(x=>x.count));return <svg viewBox={`0 0 620 ${rows.length*72+10}`} width="100%" role="img" aria-label="接收、有效、成交客资漏斗">{rows.map((x,i)=>{const top=440*x.count/max,bottom=440*(rows[i+1]?.count??x.count)/max;return <g key={x.key}><polygon points={`${260-top/2},${i*72} ${260+top/2},${i*72} ${260+bottom/2},${i*72+60} ${260-bottom/2},${i*72+60}`} fill={['#397bee','#638ee2','#8b67d9'][i%3]}/><text x="260" y={i*72+34} textAnchor="middle" fill="white">{x.label} {x.count}</text><text x="490" y={i*72+34} fill="currentColor" fontSize="12">{i?percent(rows[i-1].count?x.count/rows[i-1].count:null):'接收批次'}</text></g>})}</svg>
}
