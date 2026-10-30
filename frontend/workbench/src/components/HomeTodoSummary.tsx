import {useCallback,useEffect,useRef,useState} from 'react'
import {Button,Card,Skeleton,Statistic,Typography} from 'antd'
import {ReloadOutlined,RightOutlined} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import {api,type BusinessTaskSummary} from '../services/api'
import {APP_ROUTES} from '../constants'

export default function HomeTodoSummary({permissions,summary,loading,error,refreshVersion,onRefresh}:{permissions:readonly string[];summary?:BusinessTaskSummary;loading:boolean;error:string;refreshVersion:number;onRefresh:()=>Promise<void>}) {
 const navigate=useNavigate();const canBusiness=permissions.includes('zsjos:business-task:query');const canBpm=permissions.includes('bpm:task:query')
 const [bpm,setBpm]=useState<number>();const [bpmError,setBpmError]=useState('');const [bpmLoading,setBpmLoading]=useState(false);const sequence=useRef(0)
 const loadBpm=useCallback(async()=>{const request=++sequence.current;setBpm(undefined);setBpmError('');setBpmLoading(canBpm);if(!canBpm)return;try{const page=await api.bpmTaskPage('todo',{pageNo:1,pageSize:1});if(request===sequence.current)setBpm(page.total)}catch(e){if(request===sequence.current)setBpmError(e instanceof Error?e.message:'审批待办加载失败')}finally{if(request===sequence.current)setBpmLoading(false)}},[canBpm])
 useEffect(()=>{void loadBpm();return()=>{sequence.current++}},[loadBpm,refreshVersion])
 const businessValue=(value:number|undefined)=>!canBusiness?<Typography.Text type="secondary">暂无权限</Typography.Text>:loading?<Skeleton active paragraph={false}/>:error?<Typography.Text type="danger">{error}</Typography.Text>:<Statistic value={value??'—'} suffix="项"/>
 return <>
  <Card className="home-stat-card business" bordered={false}><div className="home-stat-card-head"><Typography.Text type="secondary">业务待办</Typography.Text>{canBusiness&&<Button type="text" icon={<ReloadOutlined/>} aria-label="刷新待办计数" loading={loading} onClick={()=>void onRefresh()}/>}</div>{businessValue(summary?summary.overdue+summary.today+summary.future:undefined)}<div className="home-stat-breakdown">{(['overdue','today','future'] as const).map((key,i)=><span key={key}>{['逾期','今日','未来'][i]} {canBusiness&&!error&&!loading&&summary?summary[key]:'—'}</span>)}</div></Card>
  <Card className="home-stat-card" bordered={false}><Typography.Text type="secondary">其他待办</Typography.Text>{businessValue(summary?.unscheduled)}<Typography.Text type="secondary">待处理的其他任务</Typography.Text></Card>
  <Card className={`home-stat-card${canBpm?' approval':' placeholder'}`} bordered={false} role={canBpm?'button':undefined} tabIndex={canBpm?0:undefined} onClick={canBpm?()=>navigate(APP_ROUTES.BPM_TODO):undefined} onKeyDown={event=>{if(event.target===event.currentTarget&&canBpm&&(event.key==='Enter'||event.key===' ')){event.preventDefault();navigate(APP_ROUTES.BPM_TODO)}}}><div className="home-stat-card-head"><Typography.Text type="secondary">审批待办</Typography.Text>{canBpm&&<RightOutlined/>}</div>{!canBpm?<Typography.Text type="secondary">暂无权限</Typography.Text>:bpmLoading?<Skeleton active paragraph={false}/>:bpmError?<Typography.Text type="danger">{bpmError}</Typography.Text>:<Statistic value={bpm??'—'} suffix="项"/>}<Typography.Text type="secondary">工作流程待办任务</Typography.Text>{bpmError&&<Button size="small" onClick={e=>{e.stopPropagation();void loadBpm()}}>重试</Button>}</Card>
 </>
}
