import { Alert, App, Button, DatePicker, Form, Input, Skeleton, Space, Tag, Typography, Select } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { api, type StudentDeliveryPlan, type PositioningCard } from '../services/api'
import { formatTimestamp } from '../services/time'
import PositioningDialog from './PositioningDialog'
import { MaterialReference } from './PositioningSnapshot'

export type StudentDeliveryPanelProps = { accountId:number;submittedBy:number;canQuery:boolean;canSubmit:boolean;canDefer:boolean;onChanged?:()=>void }
const labels:Record<string,string>={ACTIVE:'交付进行中',WAITING_SOURCE:'等待定位卡生效',WAITING_PREDECESSOR:'等待前序阶段确认',EFFECTIVE:'延期已生效',PENDING:'待交付',OVERDUE:'已逾期',COMPLETED:'已完成',DEFER_PENDING:'历史延期审批中',WAITING:'待开始',MONITORING:'每周客资监测中',REPOSITIONING:'等待新版定位卡生效',CLOSED:'历史轮次',NEEDS_REVIEW:'缺少计时依据，待核实'}
export function StudentDeliveryPanel({accountId,submittedBy,canQuery,onChanged}:StudentDeliveryPanelProps) {
 const {message,modal}=App.useApp();const [plan,setPlan]=useState<StudentDeliveryPlan|null>(null),[historyPlan,setHistoryPlan]=useState<number>();
 const [loading,setLoading]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0),[saving,setSaving]=useState(false);
 const [edit,setEdit]=useState<{stageId:number;kind:'submit'|'defer'}>();const [form]=Form.useForm();const request=useRef<{fingerprint:string;key:string}|undefined>(undefined);
 useEffect(()=>{setEdit(undefined);setHistoryPlan(undefined);setPlan(null)},[accountId]);
 useEffect(()=>{let active=true;if(!canQuery)return;setLoading(true);setError('');void api.studentDelivery.plan(accountId,historyPlan).then(r=>{if(active)setPlan(r)}).catch(e=>{if(active)setError(e instanceof Error?e.message:'交付计划加载失败')}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[accountId,canQuery,reload,historyPlan]);
 const stage=plan?.stages.find(x=>x.id===edit?.stageId);
 const open=(stageId:number,kind:'submit'|'defer')=>{form.resetFields();request.current=undefined;setEdit({stageId,kind})};
 const submit=async()=>{if(!stage||saving)return;let values:Record<string,unknown>;try{values=await form.validateFields()}catch{return}setSaving(true);setError('');try{
   const data=edit?.kind==='submit'?{deliveryCompleted:'是',diagnosis:values.diagnosis,improvement:values.improvement}:values;
   const fingerprint=JSON.stringify(data);if(request.current?.fingerprint!==fingerprint)request.current={fingerprint,key:crypto.randomUUID()};
   const base={stageId:stage.id,version:stage.version,idempotencyKey:request.current.key};
   if(edit?.kind==='submit')await api.studentDelivery.submit({...base,submittedBy,fieldValuesJson:JSON.stringify(data)});
   else await api.studentDelivery.defer({...base,requestedBy:submittedBy,newDueAt:(values.newDueAt as {valueOf:()=>number}).valueOf(),reason:String(values.reason).trim()});
   message.success(edit?.kind==='submit'?'交付已确认':'延期已生效');setEdit(undefined);setReload(x=>x+1);onChanged?.();
 }catch(e){setError(e instanceof Error?e.message:'提交失败')}finally{setSaving(false)}};
 const reposition=(item:StudentDeliveryPlan['stages'][number])=>modal.confirm({title:'结束本轮并重新定位？',width:520,content:'旧轮次保留。新版定位卡完成学员确认和运营凭证后，重新开始S0—S6。',onOk:async()=>{await api.studentDelivery.reposition({accountId,planId:plan!.id,version:item.version});setReload(x=>x+1);onChanged?.();message.success('已发起修订，请到课程服务定位卡继续填写')}});
 if(!canQuery)return <Alert type="info" title="暂无交付记录查看权限"/>;
 return <section className="account-review-delivery"><header className="account-review-delivery-heading"><Typography.Text strong>阶段交付确认（S0—S6）</Typography.Text><Typography.Text type="secondary">共 7 个阶段</Typography.Text></header>{error&&<Alert type="error" title={error} action={<Button onClick={()=>setReload(x=>x+1)}>重试</Button>}/>}{loading?<Skeleton active/>:<>
  {plan?.rounds&&plan.rounds.length>1&&<Select value={historyPlan??plan.rounds[0].id} options={plan.rounds.map(r=>({value:r.id,label:`第${r.roundNo??'?'}轮 · ${labels[r.status]||r.status}`}))} onChange={setHistoryPlan}/>}
  {!plan?.sourceAvailable&&<Alert type="info" title="等待定位卡生效"/>}
  {plan&&<p>第{plan.roundNo??1}轮 · {labels[plan.status]||plan.status}</p>}
  {Array.from({length:7},(_,i)=>`S${i}`).map(code=>{const item=plan?.stages.find(x=>x.stageCode===code);const source=item?.agreement;return <article key={code} className="account-profile-row"><Typography.Text strong>{code}期交付确认</Typography.Text>
   <p><Tag>{item?labels[item.status]||item.status:'等待前序阶段确认'}</Tag>{item?.dueAt?`截止 ${formatTimestamp(item.dueAt)}`:item?.triggerAt?`提醒 ${formatTimestamp(item.triggerAt)}`:''}</p>
   <Typography.Paragraph style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{source?.agreement||'等待定位卡交付约定'}</Typography.Paragraph>
   {source?.submissionNo&&<p>来源：定位卡第{source.submissionNo}次提交</p>}
   {source?.references?.map(ref=><MaterialReference key={ref.materialVersionId} card={{id:source.cardId,submissionId:source.submissionId,status:'confirmed'} as PositioningCard} id={ref.materialVersionId} title={ref.titleSnapshot||'历史参考素材'}/>)}
   {item?.confirmation&&<><p>确认人：{item.completedByName||'未记录'} · {formatTimestamp(item.completedAt)}</p><p style={{whiteSpace:'pre-wrap'}}>确认结果：{String(item.confirmation.diagnosis??'未记录')}</p><p style={{whiteSpace:'pre-wrap'}}>改进措施：{String(item.confirmation.improvement??'未记录')}</p></>}
   {code==='S6'&&item?.status==='MONITORING'&&<p>{plan?.weeklyLeads==null?'学员未绑定兼职账号或统计来源不可用':`上周有效客资：${plan.weeklyLeads}条`}</p>}
   <Space wrap>{item?.canSubmit&&<Button onClick={()=>open(item.id,'submit')}>确认交付完成</Button>}{item?.canDefer&&<Button onClick={()=>open(item.id,'defer')}>延期</Button>}{item?.canReposition&&<><Button onClick={()=>{void api.studentDelivery.acknowledge([item.id]).then(()=>message.info('已记录继续观察，低客资条件持续时明天再次提醒')).catch(e=>message.error(e instanceof Error?e.message:'操作失败，请重试'))}}>继续观察</Button><Button onClick={()=>reposition(item)}>结束本期并重新定位</Button></>}</Space>
   {!!item?.defers?.length&&<details><summary>延期历史（{item.defers.length}）</summary>{item.defers.map(d=><p key={d.id}>{formatTimestamp(d.createdAt)} · {d.reason} · 新截止：{d.newDueAt?formatTimestamp(d.newDueAt):'历史未记录'} · {labels[d.status]||d.status}</p>)}</details>}
  </article>})}
 </>}
 <PositioningDialog title={edit?.kind==='submit'?'确认本期交付完成':'交付延期'} open={!!edit} onCancel={()=>{if(!saving)setEdit(undefined)}} onOk={()=>void submit()} confirmLoading={saving} okText={edit?.kind==='submit'?'确认完成':'确认延期'} maskClosable={false}>
  {error&&<Alert type="error" title={error}/>}
  <Typography.Paragraph style={{whiteSpace:'pre-wrap'}}>交付约定：{stage?.agreement.agreement}</Typography.Paragraph>
  <Form form={form} layout="vertical" disabled={saving}>{edit?.kind==='submit'?<><Form.Item name="diagnosis" label="交付完成情况" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={5} maxLength={10000}/></Form.Item><Form.Item name="improvement" label="改进措施"><Input.TextArea rows={3} maxLength={2000}/></Form.Item></>:<>{!plan?.notificationRecipient && <Alert type="warning" title="延期配置暂不可用，请联系管理员"/>}<Form.Item name="newDueAt" label="新的截止时间" rules={[{required:true}]}><DatePicker showTime/></Form.Item><Form.Item name="reason" label="延期原因" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={4} maxLength={1000}/></Form.Item></>}</Form>
 </PositioningDialog></section>
}
