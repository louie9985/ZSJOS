import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Input, Modal, Space, Spin, Typography } from 'antd';
import { useBusinessOverlay } from '../OverlayCoordinator';
import { examCollectionApi, type ExamCandidate, type ExamCorrectionPreview } from '../../services/examCollection';
import { studentInfoError } from '../../services/studentInfo';
import { ExamCandidatePicker } from './ExamCollectionDialogs';
import '../../styles/components/student-info-panel.css';
import '../../styles/pages/exam-collection.css';

export default function ExamCorrectionDialog({taskId,submissionId,onClose,onSaved}:{taskId:number;submissionId:number;onClose:()=>void;onSaved:()=>void}) {
  useBusinessOverlay(true);
  const [target,setTarget]=useState<ExamCandidate>(),[picker,setPicker]=useState(false),[preview,setPreview]=useState<ExamCorrectionPreview>();
  const [reason,setReason]=useState(''),[confirmed,setConfirmed]=useState(false),[loading,setLoading]=useState(false),[busy,setBusy]=useState(false);
  const [error,setError]=useState(''),[reload,setReload]=useState(0),[discard,setDiscard]=useState(false);
  const dirty=Boolean(target||reason.trim());
  useEffect(()=>{if(!dirty&&!busy)return;const guard=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue=''};window.addEventListener('beforeunload',guard);return()=>window.removeEventListener('beforeunload',guard)},[dirty,busy]);
  useEffect(()=>{let active=true;setPreview(undefined);setConfirmed(false);setError('');if(!target)return;setLoading(true);
    void examCollectionApi.correctionPreview(taskId,submissionId,target.serviceRelationId).then(value=>{if(active)setPreview(value)}).catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[taskId,submissionId,target,reload]);
  const close=()=>{if(busy)return;if(dirty)setDiscard(true);else onClose()};
  const save=async()=>{
    if(!preview||!target||!confirmed||!reason.trim()||busy)return;
    setBusy(true);setError('');
    try{await examCollectionApi.correct(taskId,submissionId,{revision:preview.revision,taskRevision:preview.taskRevision,bindingRevision:preview.bindingRevision,
      latestSubmissionId:preview.latestSubmissionId,sourceArchiveRevision:preview.sourceArchive.revision,targetArchiveRevision:preview.targetArchive.revision,
      serviceRelationId:target.serviceRelationId,reason:reason.trim()});onSaved();}
    catch(e){setError(studentInfoError(e));setPreview(undefined);setConfirmed(false)}finally{setBusy(false)}
  };
  return <>
    <Modal open title="纠正已归档资料的归属" width={820} style={{maxWidth:'calc(100vw - 32px)',top:16}}
      styles={{container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column'},body:{overflowY:'auto',minHeight:0}}}
      onCancel={close} closable={!busy} maskClosable={false} footer={<Space wrap><Button disabled={busy} onClick={close}>取消</Button><Button danger type="primary" loading={busy} disabled={!preview||!confirmed||!reason.trim()||loading} onClick={()=>void save()}>确认纠正归属</Button></Space>}>
      <Space orientation="vertical" className="exam-collection-stack">
        <Alert type="warning" showIcon title="此操作会调整已有档案及收集进度" description="同一身份证的有效提交将整体转到新学员服务，最新一份更新目标档案。原归属恢复受影响内容，后续有效修改保留，所有原始资料和操作历史继续留存。"/>
        <Button disabled={busy||loading} onClick={()=>setPicker(true)}>{target?'重新选择正确学员':'选择正确学员及服务'}</Button>
        {target&&<Typography.Text strong>正确归属：{target.studentName} · {target.className||'未分班'}</Typography.Text>}
        {loading&&<Spin/>}
        {error&&<Alert type="error" title={error}/>}
        {target&&<Button disabled={busy||loading} onClick={()=>setReload(v=>v+1)}>刷新纠正预览</Button>}
        {preview&&<section className="student-info-panel" aria-label="归属纠正预览">
          <dl className="student-info-fields">
            <div className="student-info-field"><dt>原服务学员</dt><dd>{preview.sourceStudentName||'未记录'}</dd></div>
            <div className="student-info-field"><dt>目标服务学员</dt><dd>{preview.targetStudentName||'未记录'}</dd></div>
            <div className="student-info-field"><dt>原档案姓名</dt><dd>{preview.sourceArchive.values.name||'未填写'}</dd></div>
            <div className="student-info-field"><dt>恢复后姓名</dt><dd>{preview.restoredArchive.values.name||'未填写'}</dd></div>
            <div className="student-info-field"><dt>目标档案姓名</dt><dd>{preview.targetArchive.values.name||'未填写'}</dd></div>
            <div className="student-info-field"><dt>关联提交</dt><dd>{preview.submissionCount} 次，以最新有效提交归档</dd></div>
          </dl>
          <p>原档案恢复字段：{preview.restoredFields.join('、')||'无字段需要恢复'}</p>
          {!!preview.retainedFields.length&&<p>保留后续有效修改：{preview.retainedFields.join('、')}</p>}
          <p>姓名和手机号不作一致性校验；班级外学员不会增加应收人数。操作期间档案发生变化时，需重新确认。</p>
        </section>}
        <Input.TextArea aria-label="归属纠正原因" placeholder="填写正确归属的依据及纠正原因" value={reason} onChange={e=>setReason(e.target.value)} maxLength={500} showCount rows={3} disabled={busy}/>
        <Checkbox checked={confirmed} disabled={!preview||busy||loading} onChange={e=>setConfirmed(e.target.checked)}>已核对原归属、正确学员服务及档案恢复范围</Checkbox>
      </Space>
    </Modal>
    {picker&&<ExamCandidatePicker taskId={taskId} onClose={()=>setPicker(false)} onSelect={value=>{setTarget(value);setPicker(false)}}/>}
    <Modal open={discard} title="放弃此次归属纠正？" onCancel={()=>setDiscard(false)} onOk={onClose} okText="放弃修改" cancelText="继续核对"><p>本次选择和原因尚未提交。</p></Modal>
  </>;
}
