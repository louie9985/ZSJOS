import { useEffect, useState } from 'react';
import { Button, Input, Modal, Select, Space, Tag } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import BusinessTable from '../BusinessTable';
import { examCollectionApi, examExceptionLabels, examSubmissionLabels, type ExamCandidate, type ExamEvent, type ExamMember, type ExamSubmission, type ExamTaskDetail } from '../../services/examCollection';
import { studentInfoError } from '../../services/studentInfo';
import { formatTimestamp } from '../../services/time';
import { ExamCandidatePicker, ExamReasonDialog } from './ExamCollectionDialogs';
import ExamSubmissionViewer from './ExamSubmissionViewer';
import { useBusinessOverlay } from '../OverlayCoordinator';

export function ExamSubmissions({taskId,exceptionsOnly=false,serviceRelationId,canResolve,onChanged}:{taskId:number;exceptionsOnly?:boolean;serviceRelationId?:number;canResolve:boolean;onChanged:()=>void}){
  const [page,setPage]=useState(1),[size,setSize]=useState(20),[status,setStatus]=useState<string>(),[rows,setRows]=useState<ExamSubmission[]>([]),[total,setTotal]=useState(0);
  const [loading,setLoading]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0),[selected,setSelected]=useState<number>();
  useEffect(()=>{let active=true;setLoading(true);setError('');void examCollectionApi.submissions(taskId,{pageNo:page,pageSize:size,status:exceptionsOnly?'EXCEPTION':status,serviceRelationId})
    .then(v=>{if(active){setRows(v.list);setTotal(v.total)}}).catch(e=>{if(active){setRows([]);setError(studentInfoError(e))}}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[taskId,page,size,status,exceptionsOnly,serviceRelationId,reload]);
  const columns:ProColumns<ExamSubmission>[]=[{title:'填写姓名',dataIndex:['values','name'],width:140},{title:'手机号',dataIndex:['values','mobile'],width:150},{title:'身份证号码',dataIndex:['values','id_card'],width:210},
    {title:'提交时间',dataIndex:'submittedAt',width:170,render:(_,r)=>formatTimestamp(r.submittedAt)},{title:'处理状态',dataIndex:'status',width:140,render:(_,r)=><Tag color={r.status==='ARCHIVED'?'success':r.status==='EXCEPTION'?'warning':'default'}>{examSubmissionLabels[r.status]}</Tag>},
    {title:'异常原因',dataIndex:'exceptionCode',width:200,render:(_,r)=>examExceptionLabels[r.exceptionCode||'']||'—'},
    {title:'操作',key:'action',valueType:'option',width:140,render:(_,r)=><Button type="link" onClick={()=>setSelected(r.id)}>{r.status==='EXCEPTION'&&canResolve?'查看并处理':'查看原始填写'}</Button>}];
  return <><BusinessTable<ExamSubmission> mode="compact" tableKey={exceptionsOnly?'exam-exceptions':'exam-submissions'} rowKey="id" columns={columns} dataSource={rows} loading={loading} error={error} onReload={()=>setReload(v=>v+1)}
    filters={!exceptionsOnly?<Select aria-label="筛选提交状态" placeholder="全部提交状态" allowClear style={{minWidth:180}} value={status} options={Object.entries(examSubmissionLabels).map(([value,label])=>({value,label}))} onChange={v=>{setStatus(v);setPage(1)}}/>:undefined}
    pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(s!==size?1:p);setSize(s)}}}/>
    {selected&&<ExamSubmissionViewer taskId={taskId} submissionId={selected} canResolve={canResolve} onClose={()=>setSelected(undefined)} onChanged={()=>{setReload(v=>v+1);onChanged()}}/>}
  </>;
}
function MemberHistory({taskId,member,onClose,canResolve,onChanged}:{taskId:number;member:ExamMember;onClose:()=>void;canResolve:boolean;onChanged:()=>void}){
  useBusinessOverlay(true);
  return <Modal open title={`${member.studentName} · 填写历史`} width={1000} style={{maxWidth:'calc(100vw - 32px)',top:16}} onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>}>
    <ExamSubmissions taskId={taskId} serviceRelationId={member.serviceRelationId} canResolve={canResolve} onChanged={onChanged}/>
  </Modal>;
}
export function ExamMembers({task,canAdjust,canResolve,onChanged}:{task:ExamTaskDetail;canAdjust:boolean;canResolve:boolean;onChanged:()=>void}){
  const [keyword,setKeyword]=useState(''),[page,setPage]=useState(1),[size,setSize]=useState(20),[active,setActive]=useState(true);
  const [rows,setRows]=useState<ExamMember[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [history,setHistory]=useState<ExamMember>(),[picker,setPicker]=useState(false),[change,setChange]=useState<{candidate:Pick<ExamCandidate,'serviceRelationId'|'studentName'|'className'>;remove:boolean}>();
  useEffect(()=>{let current=true;setLoading(true);setError('');void examCollectionApi.members(task.id,{pageNo:page,pageSize:size,keyword,active})
    .then(v=>{if(current){setRows(v.list);setTotal(v.total)}}).catch(e=>{if(current){setRows([]);setError(studentInfoError(e))}}).finally(()=>{if(current)setLoading(false)});return()=>{current=false}},[task.id,task.revision,page,size,keyword,active,reload]);
  const columns:ProColumns<ExamMember>[]=[{title:'学员姓名',dataIndex:'studentName',width:140},{title:'手机号',dataIndex:'mobile',width:160},{title:'通知时班级',dataIndex:'className',width:220},{title:'学习规划师',dataIndex:'plannerName',width:150},
    {title:'本次填写',key:'filled',width:120,render:(_,r)=><Tag color={r.lastSubmissionId?'success':'warning'}>{r.lastSubmissionId?'已填写':'未填写'}</Tag>},
    {title:'操作',key:'action',valueType:'option',width:200,render:(_,r)=><Space><Button type="link" onClick={()=>setHistory(r)}>填写历史</Button>{canAdjust&&<Button type="link" danger={active} onClick={()=>setChange({candidate:r,remove:active})}>{active?'移出名单':'恢复应收'}</Button>}</Space>}];
  return <><BusinessTable<ExamMember> mode="compact" tableKey="exam-members" rowKey="serviceRelationId" columns={columns} dataSource={rows} loading={loading} error={error} onReload={()=>setReload(v=>v+1)}
    filters={<><Input.Search aria-label="搜索应收学员" placeholder="姓名、手机号" allowClear onSearch={v=>{setKeyword(v);setPage(1)}}/><Select aria-label="名单范围" value={active?'active':'removed'} options={[{value:'active',label:'当前应收名单'},{value:'removed',label:'已移出名单'}]} onChange={v=>{setActive(v==='active');setPage(1)}}/></>}
    actions={canAdjust?<Button onClick={()=>setPicker(true)}>添加学员</Button>:undefined}
    pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(s!==size?1:p);setSize(s)}}}/>
    {history&&<MemberHistory taskId={task.id} member={history} canResolve={canResolve} onClose={()=>setHistory(undefined)} onChanged={onChanged}/>}
    {picker&&<ExamCandidatePicker taskId={task.id} onClose={()=>setPicker(false)} onSelect={v=>{setPicker(false);setChange({candidate:v,remove:false})}}/>}
    {change&&<ExamReasonDialog title={change.remove?'移出应收名单':'加入应收名单'} description={`${change.candidate.studentName} · ${change.candidate.className||'未分班'}。此操作会重新计算本次进度和规划师待办，历史提交仍保留。`}
      onClose={()=>setChange(undefined)} onConfirm={async reason=>{await examCollectionApi.adjust(task.id,{revision:task.revision,addServiceIds:change.remove?[]:[change.candidate.serviceRelationId],removeServiceIds:change.remove?[change.candidate.serviceRelationId]:[],reason});setReload(v=>v+1);onChanged()}}/>}
  </>;
}
const eventLabels:Record<string,string>={NOTIFY:'通知班级',COMPLETE:'完成任务',REOPEN:'重新开启',ADJUST_MEMBERS:'调整名单',RESOLVE:'人工关联',CORRECT_BINDING:'纠正归属',INVALIDATE:'标记无效',REMINDER_SEEN:'已读提醒'};
export function ExamEvents({taskId}:{taskId:number}){
  const [page,setPage]=useState(1),[rows,setRows]=useState<ExamEvent[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  useEffect(()=>{let active=true;setLoading(true);setError('');void examCollectionApi.events(taskId,{pageNo:page,pageSize:20}).then(v=>{if(active){setRows(v.list);setTotal(v.total)}}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[taskId,page,reload]);
  const columns:ProColumns<ExamEvent>[]=[{title:'时间',dataIndex:'time',width:180,render:(_,r)=>formatTimestamp(r.time)},{title:'操作',dataIndex:'action',width:150,render:(_,r)=>eventLabels[r.action]||r.action},{title:'操作人',dataIndex:'operatorName',width:140},{title:'原因 / 说明',dataIndex:'reason',width:320,ellipsis:false}];
  return <BusinessTable<ExamEvent> mode="compact" tableKey="exam-events" rowKey="id" columns={columns} dataSource={rows} loading={loading} error={error} onReload={()=>setReload(v=>v+1)} pagination={{current:page,pageSize:20,total,showSizeChanger:false,onChange:setPage}}/>;
}
