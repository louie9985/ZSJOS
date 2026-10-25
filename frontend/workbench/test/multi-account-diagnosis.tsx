// Actual account panel and services on isolated synthetic transport; no live business writes.
import './weekly-account-diagnosis';
import {http, ApiError} from '../src/services/api';
import type {AxiosAdapter} from 'axios';
import type {DiagnosisBatchCandidate, DiagnosisBatchSubmit} from '../src/services/mediaAccountProfile';
const previous = http.defaults.adapter as AxiosAdapter;
const params = new URLSearchParams(location.search);
const nodeKey = (id: number) => `${id}:round:2026-10-29`;
const initial = params.has('initial');
const candidates: DiagnosisBatchCandidate[] = [
  {accountId:900001,accountName:'抖音主账号',platformLabel:'抖音',version:1,configVersionId:1,currentStage:'fixture_value',accountStatus:'fixture_value',nodeKey:nodeKey(900001),eligible:true},
  {accountId:900002,accountName:'视频号',platformLabel:'视频号',version:1,configVersionId:1,currentStage:'fixture_other',accountStatus:'fixture_other',nodeKey:nodeKey(900002),eligible:true},
  {accountId:900003,accountName:'小红书',platformLabel:'小红书',version:1,configVersionId:1,currentStage:'fixture_value',accountStatus:'fixture_other',nodeKey:nodeKey(900003),eligible:true},
  {accountId:900004,accountName:'已填写账号',platformLabel:'抖音',version:1,configVersionId:1,eligible:false,reason:'本节点已填写'},
];
const state = {events:[] as {endpoint:string;accountId?:number;key?:string}[],payloads:[] as DiagnosisBatchSubmit[],candidateCalls:0,previewCalls:0,submitCalls:0,committed:false};
(window as Window & {batchFixture?:typeof state}).batchFixture=state;
http.defaults.adapter=async config=>{
  const url=config.url||'';
  if(url.endsWith('/diagnosis/batch-candidates')) {
    state.events.push({endpoint:'candidates'});state.candidateCalls++;
    if(params.has('candidate-error')&&state.candidateCalls===1)throw new Error('测试账号加载失败，请重试');
    return {config,status:200,statusText:'OK',headers:{},data:{code:0,data:candidates}};
  }
  if(url.endsWith('/diagnosis/batch')) {
    const payload=JSON.parse(config.data) as DiagnosisBatchSubmit;
    state.payloads.push(payload);state.submitCalls++;state.events.push({endpoint:'batch',key:payload.idempotencyKey});
    document.documentElement.dataset.batchPayload=JSON.stringify(payload);
    await new Promise(resolve=>setTimeout(resolve,250));
    if(params.has('batch-error')&&state.submitCalls===1)throw new Error('测试提交失败，未保存任何账号');
    if(params.has('metric-error')&&state.submitCalls===1)throw new ApiError(1900011026,'统计数据已变化，请刷新指标并核对后再次提交');
    state.committed=true;
    return {config,status:200,statusText:'OK',headers:{},data:{code:0,data:payload.accounts.map(a=>({accountId:a.accountId,version:2}))}};
  }
  if(url.endsWith('/diagnosis/preview')) {
    const id=Number(config.params?.accountId);state.previewCalls++;state.events.push({endpoint:'preview',accountId:id});
    if(id===900002&&params.has('slow-preview'))await new Promise(resolve=>setTimeout(resolve,1500));
    if(id===900002&&params.has('account-preview-error')&&state.events.filter(e=>e.endpoint==='preview'&&e.accountId===id).length===1)throw new Error('测试视频号指标加载失败');
  }
  const response=await previous(config);
  if(url.includes('/dict-data/')) {
    response.data.data.push(...['zsjos_media_account_stage','zsjos_media_account_current_status'].map(dictType=>({dictType,value:'fixture_other',label:'其他历史标签',status:0})));
  }
  if(url.endsWith('/profile')&&config.method!=='put') {
    const profile=response.data.data;
    Object.assign(profile.account,{nickname:'抖音主账号',platformLabelSnapshot:'抖音',sStage:'fixture_value',currentStatusValue:'fixture_value'});
    Object.assign(profile,{canStartDiagnosis:initial&&!state.committed,canSubmitDiagnosis:!initial,diagnosisStarted:!initial||state.committed});
  }
  if(url.endsWith('/diagnosis/overview')&&state.committed)response.data.data={tasks:[],nodes:[]};
  return response;
};
