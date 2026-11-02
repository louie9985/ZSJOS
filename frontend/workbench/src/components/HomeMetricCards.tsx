import {ReloadOutlined} from '@ant-design/icons'
import {formatMetricDecimal} from '../services/metricDecimal'
import {useCallback,useEffect,useRef,useState} from 'react'
import {Alert,Button,Card,Empty,Modal,Select,Skeleton,Typography} from 'antd'
import {useSearchParams} from 'react-router-dom'
import {useWorkbenchPageNavigation} from './WorkbenchPageNavigation'
import {metricSourcePath} from './MetricPageRouter'
import HomeMetricBusinessPage from './HomeMetricBusinessPage'
import './HomeMetricCards.css'
import {metricApi,metricStatus,type MetricConfig,type Evaluation,type MetricContext,type EvaluatedMetric} from '../services/homeMetric'
const errorText=(e:unknown)=>e instanceof Error?e.message:'加载失败，请重试'
function valueLabel(result:Evaluation['result'],spec:MetricConfig['spec']) {
 if(result.status!=='OK'||result.value===null)return metricStatus(result.status)
 return `${formatMetricDecimal(result.value,spec.decimals,spec.percentage)}${spec.percentage?'':spec.unit}`
}
function MetricCard({config,onOpen,refreshVersion}:{config:MetricConfig;refreshVersion:number;onOpen:(id:string,route:boolean)=>void}) {
 const [result,setResult]=useState<EvaluatedMetric>(); const [error,setError]=useState('');const [loading,setLoading]=useState(false);const sequence=useRef(0)
 const businessSource=useRef(config.spec.operands?.some(o=>o.calculation.source==='business_todo')??false)
 const previousRefresh=useRef(refreshVersion)
 const load=useCallback(async()=>{const request=++sequence.current;setLoading(true);setError('');setResult(undefined);try{const value=await metricApi.evaluate(config.id);if(request===sequence.current){businessSource.current=value.spec.operands?.some(o=>o.calculation.source==='business_todo')??false;setResult(value)}}catch(e){if(request===sequence.current)setError(errorText(e))}finally{if(request===sequence.current)setLoading(false)}},[config.id,config.publishedVersion])
 useEffect(()=>{void load();return()=>{sequence.current++}},[load])
 useEffect(()=>{if(previousRefresh.current===refreshVersion)return;previousRefresh.current=refreshVersion;if(businessSource.current)void load()},[refreshVersion,load])
 const spec=result?.spec??config.spec
 return <Card className="home-stat-card" data-metric-version={result?.publishedVersion} styles={{body:{padding:12,gap:4}}}><div style={{display:'flex',alignItems:'center',justifyContent:'space-between',minWidth:0}}><Typography.Text ellipsis title={spec.name} style={{minWidth:0}}>{spec.name}</Typography.Text><Button size="small" type="text" icon={<ReloadOutlined/>} aria-label={'刷新'+spec.name} onClick={()=>void load()} loading={loading}/></div>{loading?<Skeleton active paragraph={false}/>:error?<Typography.Text type="danger" title={error} ellipsis>{error}</Typography.Text>:result&&<Button type="text" disabled={!result.contextId} onClick={()=>result.contextId&&onOpen(result.contextId,spec.action==='ROUTE')} style={{height:'auto',padding:0,minWidth:0}}><Typography.Text strong style={{fontSize:22}} ellipsis title={valueLabel(result.evaluation.result,spec)}>{valueLabel(result.evaluation.result,spec)}</Typography.Text></Button>}<Typography.Text type="secondary" ellipsis title={spec.description}>{spec.description}</Typography.Text></Card>
}
function MetricDetails({id,onClose,permissions}:{id:string;onClose:()=>void;permissions:string[]}) {
 const [context,setContext]=useState<MetricContext>();const [operand,setOperand]=useState('');const [error,setError]=useState('');const sequence=useRef(0);const dirty=useRef(false)
 const load=useCallback(async()=>{const request=++sequence.current;setContext(undefined);setError('');try{const c=await metricApi.context(id);if(request!==sequence.current)return;setContext(c);setOperand(c.evaluation.inputs[0]?.name??'')}catch(e){if(request===sequence.current)setError(errorText(e))}},[id])
 useEffect(()=>{void load();return()=>{sequence.current++}},[load])
 const navigation=useWorkbenchPageNavigation()
 const canLeave=async()=>navigation&&source?navigation.canClose(metricSourcePath(source)):!dirty.current||window.confirm('当前表单尚未提交，关闭将丢失已填写内容。确定继续吗？')
 const source=context?.spec.operands?.find(o=>o.calculation.name===operand)?.calculation.source
 return <Modal open className="home-metric-business-modal" title={context?.spec.name??'指标明细'} onCancel={async()=>{if(await canLeave())onClose()}} footer={null} width="96vw" styles={{body:{height:'82dvh',overflow:'auto'}}}>
  {error?<Alert type="error" title={error} action={<Button onClick={()=>void load()}>重试</Button>}/>:!context?<Skeleton active/>:<>
   {context.evaluation.inputs.length>1&&<Select aria-label="选择统计来源" value={operand} options={context.evaluation.inputs.map(i=>({value:i.name,label:`${i.name} · ${i.sourceLabel||i.name}`}))} onChange={async value=>{if(await canLeave()){dirty.current=false;setOperand(value)}}}/>}
   {source&&<HomeMetricBusinessPage key={operand} contextId={id} operand={operand} source={source} permissions={permissions} onDirtyChange={value=>{dirty.current=value}}/>}
  </>}
 </Modal>
}
export default function HomeMetricCards({refreshVersion=0,slots=12,permissions=[]}:{refreshVersion?:number;slots?:number;permissions?:string[]}){
 const [configs,setConfigs]=useState<MetricConfig[]>([]);const [error,setError]=useState('');const [loading,setLoading]=useState(true);const [modal,setModal]=useState<string>();const [params,setParams]=useSearchParams();const deep=params.get('metricContext');const sequence=useRef(0)
 const load=useCallback(async()=>{const request=++sequence.current;setLoading(true);setError('');try{const list=await metricApi.visible();if(request===sequence.current)setConfigs(list)}catch(e){if(request===sequence.current)setError(errorText(e))}finally{if(request===sequence.current)setLoading(false)}},[])
 useEffect(()=>{void load();return()=>{sequence.current++}},[load])
 const close=()=>{setModal(undefined);if(deep){const next=new URLSearchParams(params);next.delete('metricContext');setParams(next,{replace:true})}}
 return <>{loading?<div style={{gridColumn:'1 / -1'}}><Skeleton active/></div>:error?<div style={{gridColumn:'1 / -1'}}><Alert type="error" title={error} action={<Button onClick={()=>void load()}>重试</Button>}/></div>:configs.length?configs.slice(0,12).map(c=><MetricCard key={c.id} config={c} refreshVersion={refreshVersion} onOpen={(id,route)=>{if(route){const next=new URLSearchParams(params);next.set('metricContext',id);setParams(next)}else setModal(id)}}/>):<div style={{gridColumn:'1 / -1'}}><Empty description="暂无已配置指标"/></div>}{!loading&&!error&&configs.length>0&&Array.from({length:Math.max(0,slots-configs.length)},(_,i)=><Card key={"unused-"+i} className="home-stat-card placeholder" aria-label="未配置指标"><Typography.Text type="secondary">未配置</Typography.Text></Card>)}{(deep||modal)&&<MetricDetails key={deep||modal} id={(deep||modal)!} onClose={close} permissions={permissions}/>}</>
}
