import type {TimestampValue} from './time'
import {http,unwrap,type PageResult} from './api'
export interface MetricConfig {id:number;version:number;enabled:boolean;publishedVersion?:number;spec:{name:string;description:string;unit:string;action:string;formula:string;percentage:boolean;decimals:number}}
export interface Evaluation {evaluatedAt:TimestampValue;result:{status:string;value:string|null};inputs:{name:string;sourceLabel?:string;range?:{start:TimestampValue;end:TimestampValue};result:{status:string;value:string|null}}[]}
export interface MetricContext {id:number;spec:MetricConfig['spec'];evaluation:Evaluation;expiresAt:TimestampValue}
export interface EvaluatedMetric {id:number;publishedVersion:number;spec:MetricConfig['spec'];contextId:string|null;evaluation:Evaluation}
let active=0
const queue:Array<()=>void>=[]
async function scheduled<T>(run:()=>Promise<T>):Promise<T>{if(active>=4)await new Promise<void>(resolve=>queue.push(resolve));else active++;try{return await run()}finally{const next=queue.shift();if(next)next();else active--}}
const base='/zsjos/home-metric'
export const metricApi={
 visible:async()=>unwrap<MetricConfig[]>(await http.get(base+'/visible')),
 evaluate:async(id:number)=>scheduled(async()=>unwrap<EvaluatedMetric>(await http.post(base+'/evaluate',{id}))),
 context:async(id:string)=>unwrap<MetricContext>(await http.get(base+'/context',{params:{id}})),
 details:async(contextId:string,operand:string,pageNo:number,pageSize:number)=>unwrap<PageResult<Record<string,unknown>>>(await http.post(base+'/details',{contextId,operand,pageNo,pageSize}))
}
export const metricStatus=(status:string)=>(({ EMPTY:'暂无数据',FORBIDDEN:'暂无权限',INVALID_CONFIG:'配置已失效',DIVIDE_BY_ZERO:'除数为零，无法计算',SOURCE_ERROR:'数据加载失败',TIMEOUT:'查询超时',OVERFLOW:'数值超出范围'} as Record<string,string>)[status]??'无法计算')





