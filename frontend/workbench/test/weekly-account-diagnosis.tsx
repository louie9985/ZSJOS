// Real account panel on synthetic transport; no live business requests.
import './account-positioning-summary';
import {http} from '../src/services/api';
import type {AxiosAdapter} from 'axios';
import type {WeeklyDiagnosisNode} from '../src/services/mediaAccountProfile';
const previous=http.defaults.adapter as AxiosAdapter;
const due=new Date('2026-10-29T00:00:00+08:00').getTime();
const params=new URLSearchParams(location.search);
const fixture={
  events:[] as {endpoint:string;accountId:number;nodeKey?:string;started:number;finished?:number}[],
  delays:{overview:params.has('slow-overview')?Number(params.get('slow-overview')||1200):0,
    preview:params.has('slow-preview')?Number(params.get('slow-preview')||1200):0} as Record<string,number>,
  failures:{overview:params.has('overview-error')?1:0,
    preview:params.has('preview-error')||params.has('weekly-error')?1:0} as Record<string,number>,
};
(window as Window & {diagnosisFixture?:typeof fixture}).diagnosisFixture=fixture;
let completed=false,entry:Record<string,unknown>|undefined;
const node:WeeklyDiagnosisNode={key:'900001:round:2026-10-29',roundKey:'round',week:4,dueAt:due,taskIds:[700,701,702],
  parts:[7,14,28].map((days,i)=>({days,cycle:28/days,templateType:`diagnosis_${days}d` as 'diagnosis_7d',taskId:700+i,
    from:due-days*86400000,to:due,metric:{state:'OPEN',asOf:Date.now(),publishedCount:days,missingPublishTimeCount:0,
      validLeads:location.search.includes('unbound')?undefined:8,deals:2,conversionRate:0.25,highestLikes:12,likesCoverage:3,
      reason:location.search.includes('unbound')?'学员未绑定兼职账号，暂无法统计':undefined,
      publicationExplanation:'按本时间段内预计发布且已正式提交审核的作品去重统计，不代表实际发布数量。',
      conversionExplanation:'有效客资按本周期提交、周期截止时有效的客资统计；成交数为这批客资截至当前已成交的数量，提交后仍会更新。'}}))};
http.defaults.adapter=async config=>{
  const url=config.url || '';
  const accountId=Number(config.params?.accountId || url.match(/media-account\/(\d+)\//)?.[1] || 900001);
  const endpoint=url.split('/').at(-1)!;
  const requestNode={...node,key:node.key.replace('900001',String(accountId))};
  const tasks=completed||params.has('empty-tasks')?[]:requestNode.parts.map(p=>({taskId:p.taskId,accountId,studentPersonId:900001,
    title:`${p.days}天诊断`,templateType:p.templateType,cycle:p.cycle,dueAt:due,payload:{requirementSnapshot:{[p.templateType]:'合成测试要求'}}}));
  const nodes=completed||params.has('empty-tasks')||params.has('future-nodes')?[]:[requestNode];
  if(url.includes('/diagnosis/')){
    const event={endpoint,accountId,nodeKey:config.params?.nodeKey as string|undefined,started:performance.now(),finished:undefined as number|undefined};
    fixture.events.push(event);
    if(fixture.delays[endpoint])await new Promise(resolve=>setTimeout(resolve,fixture.delays[endpoint]));
    event.finished=performance.now();
    if(fixture.failures[endpoint]){
      fixture.failures[endpoint]--;
      throw new Error(endpoint==='overview'?'测试任务加载失败，请重试':'测试统计预览加载失败，请重试');
    }
    if(params.has('denied-preview')&&endpoint==='preview')throw new Error('没有该账号诊断填写权限');
  }
  let data:unknown;
  if(url.endsWith('/diagnosis/overview'))data={tasks,nodes:nodes.map(n=>({...n,parts:n.parts.map(p=>({...p,metric:null}))}))};
  else if(url.endsWith('/diagnosis/tasks'))data=tasks;
  else if(url.endsWith('/diagnosis/nodes'))data=nodes;
  else if(url.endsWith('/diagnosis/preview') || url.endsWith('/diagnosis/entry-metrics'))data={node:requestNode,metricToken:'fixture-token'};
  else if(url.endsWith('/diagnosis/weekly')){
    const payload=JSON.parse(config.data);document.documentElement.dataset.weeklyPayload=JSON.stringify(payload);
    completed=true;entry={...payload.common,nodeKey:node.key,weeklyNode:node,changeDirectionLabel:'是',trafficNormalLabel:'否',highestLikes:payload.highestLikes,directionSummary:payload.directionSummary};data=2;
  }
  else if(url.includes('/dict-data/')){
    const result=await previous(config);
    result.data.data.push({value:'true',label:'是',dictType:'infra_boolean_string',status:0},{value:'false',label:'否',dictType:'infra_boolean_string',status:0});return result;
  }else {
    const result=await previous(config);
    if(url.endsWith('/profile') && config.method!=='put')result.data.data.account.id=accountId;
    if(entry && url.endsWith('/profile')){
      result.data.data.latestRecords={diagnosis_7d:{id:99,kind:'DIAGNOSIS',fieldKey:'diagnosis_7d',title:'综合诊断',content:JSON.stringify(entry),files:[],snapshots:[],operatedAt:Date.now(),resultVersion:2}};
    }
    return result;
  }
  return {config,status:200,statusText:'OK',headers:{},data:{code:0,data}};
};
