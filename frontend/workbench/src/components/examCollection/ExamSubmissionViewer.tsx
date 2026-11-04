import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Input, Modal, Space, Spin, Tag, Typography } from 'antd';
import StudentInfoPanel, { type StudentInfoReader } from '../StudentInfoPanel';
import { useBusinessOverlay } from '../OverlayCoordinator';
import { examCollectionApi, examExceptionLabels, examSubmissionLabels, type ExamArchive, type ExamCandidate, type ExamSubmissionDetail } from '../../services/examCollection';
import { studentInfoError } from '../../services/studentInfo';
import { formatTimestamp } from '../../services/time';
import { ExamCandidatePicker, ExamReasonDialog } from './ExamCollectionDialogs';

export default function ExamSubmissionViewer({taskId,submissionId,canResolve,onClose,onChanged}:{taskId:number;submissionId:number;canResolve:boolean;onClose:()=>void;onChanged:()=>void}){
  useBusinessOverlay(true);
  const [viewId,setViewId]=useState(submissionId),[data,setData]=useState<ExamSubmissionDetail>(),[loading,setLoading]=useState(true),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [picker,setPicker]=useState(false),[target,setTarget]=useState<ExamCandidate>(),[archive,setArchive]=useState<ExamArchive>();
  const [targetLoading,setTargetLoading]=useState(false),[targetError,setTargetError]=useState(''),[targetReload,setTargetReload]=useState(0);
  const [reason,setReason]=useState(''),[busy,setBusy]=useState(false),[operationError,setOperationError]=useState(''),[invalidating,setInvalidating]=useState(false);
  useEffect(()=>{let active=true;setLoading(true);setError('');setData(undefined);setTarget(undefined);setArchive(undefined);setReason('');setOperationError('');
    void examCollectionApi.submission(taskId,viewId).then(v=>{if(active)setData(v)}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[taskId,viewId,reload]);
  useEffect(()=>{let active=true;setArchive(undefined);setTargetError('');if(!target)return;setTargetLoading(true);
    void examCollectionApi.archive(taskId,target.serviceRelationId).then(v=>{if(active)setArchive(v)}).catch(e=>{if(active)setTargetError(studentInfoError(e))}).finally(()=>{if(active)setTargetLoading(false)});return()=>{active=false}},[taskId,target,targetReload]);
  const reader=useMemo<StudentInfoReader|undefined>(()=>data?{
    detail:async()=>({id:data.id,status:'SUBMITTED',submittedAt:data.submittedAt,fields:data.fields,values:data.values,images:data.attachments,canReadSensitive:true,canExport:false}),
    image:(_,file)=>examCollectionApi.attachment(taskId,data.id,file),
    export:async()=>{throw new Error('请从收集情况页面导出');},
  }:undefined,[taskId,data]);
  const latest=Boolean(data&&(!data.latestPendingSubmissionId||data.latestPendingSubmissionId===data.id));
  const resolve=async()=>{
    if(!data||!archive||!target||busy)return;if(!reason.trim()){setOperationError('请填写人工关联原因');return}
    setBusy(true);setOperationError('');try{await examCollectionApi.resolve(taskId,data.id,{revision:data.revision,serviceRelationId:target.serviceRelationId,archiveRevision:archive.revision,reason:reason.trim()});onChanged();onClose();}
    catch(e){setOperationError(studentInfoError(e))}finally{setBusy(false)}
  };
  return <>
    <Modal open title="原始填写资料" width={1040} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={{container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column'},body:{overflowY:'auto',minHeight:0},footer:{flexShrink:0}}}
      onCancel={()=>{if(!busy)onClose()}} maskClosable={false} footer={<Space wrap>
        {canResolve&&data?.status==='EXCEPTION'&&<><Button danger disabled={busy} onClick={()=>setInvalidating(true)}>标记无效</Button><Button disabled={busy||!latest} onClick={()=>setPicker(true)}>{target?'重新选择归属':'选择学员归属'}</Button><Button type="primary" loading={busy} disabled={!archive||targetLoading||!latest} onClick={()=>void resolve()}>确认关联并更新档案</Button></>}
        <Button disabled={busy} onClick={onClose}>关闭</Button>
      </Space>}>
      <Space orientation="vertical" className="exam-collection-stack">
        {loading&&<Spin/>}{error&&<Alert type="error" title={error} action={<Button onClick={()=>setReload(v=>v+1)}>重试</Button>}/>}
        {data&&<><Space wrap><Tag color={data.status==='EXCEPTION'?'warning':data.status==='ARCHIVED'?'success':'default'}>{examSubmissionLabels[data.status]}</Tag><Typography.Text type="secondary">提交于 {formatTimestamp(data.submittedAt)}</Typography.Text></Space>
          {data.status==='EXCEPTION'&&<Alert type="warning" showIcon title={examExceptionLabels[data.exceptionCode||'']||'需要人工确认归属'} description={data.pendingGroupCount>1?`同一身份证有 ${data.pendingGroupCount} 次待处理提交，确认后以最新一次更新档案，全部保留历史。`:undefined}
            action={!latest?<Button onClick={()=>setViewId(data.latestPendingSubmissionId!)}>先查看最新提交</Button>:undefined}/>}
          {reader&&<StudentInfoPanel caseId={data.id} reader={reader}/>}</>}
        {target&&<section className="exam-collection-target" aria-label="确认学员归属"><Typography.Title level={5}>将关联到：{target.studentName} · {target.className||'未分班'}</Typography.Title>
          {targetLoading&&<Spin/>}{targetError&&<Alert type="error" title={targetError}/>}
          <Button size="small" disabled={busy||targetLoading} onClick={()=>setTargetReload(v=>v+1)}>刷新目标档案</Button>
          {archive&&<dl><dt>当前档案姓名</dt><dd>{archive.values.name||'未填写'}</dd><dt>当前档案手机号</dt><dd>{archive.values.mobile||'未填写'}</dd><dt>当前档案身份证</dt><dd>{archive.values.id_card||'未填写'}</dd></dl>}
          <p>姓名、手机号不同不阻止归档；本次字段将更新到所选服务的学员档案，原值保留在修改历史中。</p>
          <Input.TextArea aria-label="人工关联原因" placeholder="请填写确认归属的依据" value={reason} maxLength={500} showCount disabled={busy} onChange={e=>setReason(e.target.value)}/>
        </section>}
        {operationError&&<Alert type="error" title={operationError}/>}
      </Space>
    </Modal>
    {picker&&<ExamCandidatePicker taskId={taskId} onClose={()=>setPicker(false)} onSelect={v=>{setTarget(v);setPicker(false);setOperationError('')}}/>}
    {invalidating&&data&&<ExamReasonDialog title="标记为无效提交" description="原始填写内容和材料仍会保留，此提交不再计入待处理异常。" onClose={()=>setInvalidating(false)} onConfirm={async reason=>{await examCollectionApi.invalidate(taskId,data.id,{revision:data.revision,reason});onChanged();onClose()}}/>}
  </>;
}
