import {ReloadOutlined} from '@ant-design/icons'
import {formatTimestamp} from '../services/time'
import {formatMetricDecimal} from '../services/metricDecimal'
import {useCallback,useEffect,useRef,useState} from 'react'
import {Alert,Button,Card,Empty,Modal,Select,Skeleton,Space,Typography} from 'antd'
import {useSearchParams} from 'react-router-dom'
import BusinessTable from './BusinessTable'
import {metricApi,metricStatus,type MetricConfig,type Evaluation,type MetricContext,type EvaluatedMetric} from '../services/homeMetric'
const errorText=(e:unknown)=>e instanceof Error?e.message:'加载失败，请重试'
function valueLabel(result:Evaluation['result'],spec:MetricConfig['spec']) {
 if(result.status!=='OK'||result.value===null)return metricStatus(result.status)
 return `${formatMetricDecimal(result.value,spec.decimals,spec.percentage)}${spec.percentage?'':spec.unit}`
}
function MetricCard({config,onOpen}:{config:MetricConfig;onOpen:(id:string,route:boolean)=>void}) {
 const [result,setResult]=useState<EvaluatedMetric>(); const [error,setError]=useState('');const [loading,setLoading]=useState(false);const sequence=useRef(0)
 const load=useCallback(async()=>{const request=++sequence.current;setLoading(true);setError('');setResult(undefined);try{const value=await metricApi.evaluate(config.id);if(request===sequence.current)setResult(value)}catch(e){if(request===sequence.current)setError(errorText(e))}finally{if(request===sequence.current)setLoading(false)}},[config.id,config.publishedVersion])
 useEffect(()=>{void load();return()=>{sequence.current++}},[load])
 const spec=result?.spec??config.spec
 return <Card className="home-stat-card" data-metric-version={result?.publishedVersion} styles={{body:{padding:12,gap:4}}}><div style={{display:'flex',alignItems:'center',justifyContent:'space-between',minWidth:0}}><Typography.Text ellipsis title={spec.name} style={{minWidth:0}}>{spec.name}</Typography.Text><Button size="small" type="text" icon={<ReloadOutlined/>} aria-label={'刷新'+spec.name} onClick={()=>void load()} loading={loading}/></div>{loading?<Skeleton active paragraph={false}/>:error?<Typography.Text type="danger" title={error} ellipsis>{error}</Typography.Text>:result&&<Button type="text" disabled={!result.contextId} onClick={()=>result.contextId&&onOpen(result.contextId,spec.action==='ROUTE')} style={{height:'auto',padding:0,minWidth:0}}><Typography.Text strong style={{fontSize:22}} ellipsis title={valueLabel(result.evaluation.result,spec)}>{valueLabel(result.evaluation.result,spec)}</Typography.Text></Button>}<Typography.Text type="secondary" ellipsis title={spec.description}>{spec.description}</Typography.Text></Card>
}
function MetricDetails({id,onClose}:{id:string;onClose:()=>void}) {
 const [context,setContext]=useState<MetricContext>();const [operand,setOperand]=useState('');const [error,setError]=useState('');const [rows,setRows]=useState<Record<string,unknown>[]>([]);const [total,setTotal]=useState(0);const [page,setPage]=useState(1);const [loading,setLoading]=useState(false);const contextSequence=useRef(0);const detailSequence=useRef(0)
 const loadContext=useCallback(async()=>{const request=++contextSequence.current;detailSequence.current++;setContext(undefined);setOperand('');setRows([]);setPage(1);setError('');try{const c=await metricApi.context(id);if(request!==contextSequence.current)return;setContext(c);setOperand(c.evaluation.inputs[0]?.name??'')}catch(e){if(request===contextSequence.current)setError(errorText(e))}},[id])
 useEffect(()=>{void loadContext();return()=>{contextSequence.current++;detailSequence.current++}},[loadContext])
 const load=useCallback(async()=>{if(!operand||!context)return;const request=++detailSequence.current;setLoading(true);setRows([]);setTotal(0);setError('');try{const p=await metricApi.details(id,operand,page,20);if(request!==detailSequence.current)return;setRows(p.list);setTotal(p.total)}catch(e){if(request===detailSequence.current)setError(errorText(e))}finally{if(request===detailSequence.current)setLoading(false)}},[id,operand,page,context])
 useEffect(()=>{void load();return()=>{detailSequence.current++}},[load])
 const input=context?.evaluation.inputs.find(i=>i.name===operand)
 const body=<>
 {error&&<Alert type="error" title={error} action={<Button onClick={()=>void(context?load():loadContext())}>重试</Button>}/>}
 {context?<Space orientation="vertical" style={{width:'100%'}}><Typography.Text>{context.spec.formula} = {valueLabel(context.evaluation.result,context.spec)}</Typography.Text><Typography.Text type="secondary">统计时间：{formatTimestamp(context.evaluation.evaluatedAt)}。数据可能已更新；明细按原指标条件展示。</Typography.Text>{context.evaluation.inputs.map(i=><Typography.Text key={i.name}>{i.name}{i.sourceLabel ? `（${i.sourceLabel}）` : ``} = {i.result.status==='OK'?i.result.value:metricStatus(i.result.status)}{i.range?`（${formatTimestamp(i.range.start)} 至 ${formatTimestamp(i.range.end)}）`:'（不限时间）'}</Typography.Text>)}<Select aria-label="选择操作数明细" style={{minWidth:200}} value={operand} options={context.evaluation.inputs.map(i=>({value:i.name,label:`${i.name}${i.sourceLabel ? `（${i.sourceLabel}）` : ``} · ${i.result.status==='OK'?i.result.value:metricStatus(i.result.status)}`}))} onChange={v=>{detailSequence.current++;setOperand(v);setPage(1)}}/>{input?.range&&<Typography.Text>{formatTimestamp(input.range.start)} 至 {formatTimestamp(input.range.end)}</Typography.Text>}<BusinessTable<Record<string,unknown>> tableKey="home-metric-details" mode="compact" rowKey={(row)=>String(row.id??row.personId??JSON.stringify(row))} search={false} loading={loading} dataSource={rows} columns={[{title:'业务编号',dataIndex:'businessNo',render:(_,row)=>String(row.businessNo??row['客资编号']??'—')},{title:'名称',dataIndex:'name',render:(_,row)=>String(row.name??row['说明']??'—')},...(rows.some(row=>row.v!=null)?[{title:'统计字段值',dataIndex:'v',render:(_:unknown,row:Record<string,unknown>)=>String(row.v??'—')}]:[])]} pagination={{current:page,pageSize:20,total,onChange:setPage}}/></Space>:!error&&<Skeleton active/>}
 </>
 return <Modal open title={context?.spec.name??'指标明细'} onCancel={onClose} footer={null} width="min(1100px, 95vw)">{body}</Modal>
}
export default function HomeMetricCards(){
 const [configs,setConfigs]=useState<MetricConfig[]>([]);const [error,setError]=useState('');const [loading,setLoading]=useState(true);const [modal,setModal]=useState<string>();const [params,setParams]=useSearchParams();const deep=params.get('metricContext');const sequence=useRef(0)
 const load=useCallback(async()=>{const request=++sequence.current;setLoading(true);setError('');try{const list=await metricApi.visible();if(request===sequence.current)setConfigs(list)}catch(e){if(request===sequence.current)setError(errorText(e))}finally{if(request===sequence.current)setLoading(false)}},[])
 useEffect(()=>{void load();return()=>{sequence.current++}},[load])
 const close=()=>{setModal(undefined);if(deep){const next=new URLSearchParams(params);next.delete('metricContext');setParams(next,{replace:true})}}
 return <>{loading?<div style={{gridColumn:'1 / -1',gridRow:'1 / -1'}}><Skeleton active/></div>:error?<div style={{gridColumn:'1 / -1',gridRow:'1 / -1'}}><Alert type="error" title={error} action={<Button onClick={()=>void load()}>重试</Button>}/></div>:configs.length?configs.slice(0,12).map(c=><MetricCard key={c.id} config={c} onOpen={(id,route)=>{if(route){const next=new URLSearchParams(params);next.set('metricContext',id);setParams(next)}else setModal(id)}}/>):<div style={{gridColumn:'1 / -1',gridRow:'1 / -1'}}><Empty description="暂无已配置指标"/></div>}{!loading&&!error&&configs.length>0&&Array.from({length:Math.max(0,12-configs.length)},(_,i)=><Card key={"unused-"+i} className="home-stat-card placeholder" aria-label="未配置指标"><Typography.Text type="secondary">未配置</Typography.Text></Card>)}{(deep||modal)&&<MetricDetails key={deep||modal} id={(deep||modal)!} onClose={close}/>}</>
}

