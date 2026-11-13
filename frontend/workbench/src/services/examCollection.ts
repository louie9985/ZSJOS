import { http, unwrap } from './api';
import type { StudentInfoField, StudentInfoImage } from './studentInfo';
import type { SummaryColumn, SummaryQuery, SummaryRow } from './studentSummary';
import type { Timestamp } from './time';

export interface ExamTask { id:number; title:string; description:string; content?:string; status:'OPEN'|'COMPLETED'; revision:number; createdAt?:Timestamp; completedAt?:Timestamp; completionReason?:string; expected:number; collected:number; exceptions:number; additional:number }
export interface ExamCreateCommand {title:string;description?:string;content?:string;selectedFields:string[];uploadLimits?:Record<string,{maxSizeMb:number;maxImages:number}>;idempotencyKey:string}
export interface ExamTaskDetail extends ExamTask { fields:StudentInfoField[]; classIds:number[]; url:string }
export interface ExamClass { id:number; name:string; groupId?:number; groupName?:string; plannerId?:number; plannerName?:string; selectable?:boolean }
export interface ExamSubmission { id:number; serviceRelationId?:number; sequenceNo:number; revision:number; status:'ARCHIVED'|'EXCEPTION'|'INVALID'; exceptionCode?:string; submittedAt:Timestamp; values:Record<string,string> }
export interface ExamSubmissionDetail extends ExamSubmission { fields:StudentInfoField[]; attachments:StudentInfoImage[]; pendingGroupCount:number; latestPendingSubmissionId?:number; latestPendingSubmittedAt?:Timestamp }
export interface ExamCandidate { serviceRelationId:number; personId:number; studentName:string; mobile?:string; classId?:number; className?:string; serviceStatus:string }
export interface ExamMember extends Omit<ExamCandidate,'serviceStatus'> { archiveName?:string; archiveMobile?:string; archiveIdCard?:string; plannerName?:string; active:boolean; lastSubmissionId?:number }
export interface ExamArchive { serviceRelationId:number; revision:number; fields:StudentInfoField[]; values:Record<string,string>; attachments:(StudentInfoImage & {source:'registration'|'exam'|'service'})[] }
export interface ExamCorrectionPreview {sourceStudentName?:string;targetStudentName?:string;revision:number;taskRevision:number;bindingRevision:number;latestSubmissionId:number;submissionCount:number;sourceArchive:ExamArchive;restoredArchive:ExamArchive;targetArchive:ExamArchive;restoredFields:string[];retainedFields:string[]}
export interface ExamCorrectionCommand {revision:number;taskRevision:number;bindingRevision:number;latestSubmissionId:number;sourceArchiveRevision:number;targetArchiveRevision:number;serviceRelationId:number;reason:string}
export interface ExamEvent { id:number; submissionId?:number; action:string; reason:string; operatorName:string; time:Timestamp }
export interface ExamTodo { todoId:number; collectionTaskId:number; title:string; classes:string[]; expected:number; collected:number; url:string }
export interface ExamPlannerState { pending:ExamTodo[]; unreadTodoIds:number[] }
export interface ExamPlannerMember { archiveName?:string; archiveMobile?:string; archiveIdCard?:string; serviceRelationId:number; studentName:string; mobile?:string; className:string; filled:boolean }
export const EXAM_COLLECTION_PROGRESS_EVENT='zsjos:exam-collection-progress';
export interface Page<T> { list:T[]; total:number }
export interface Paging { pageNo:number; pageSize:number }
export interface ExamExportSettings { revision:number; layout:'MATERIAL'|'STUDENT'|'FLAT'; groupByClass:boolean; naming:string[]; overrides:Record<string,string[]> }
export interface ExamExportRequest { settings:ExamExportSettings; mode:'ARCHIVE'|'SUBMISSIONS'; classIds:number[]; serviceIds:number[]; keyword?:string; filled?:boolean; includeAdditional:boolean; fieldKeys:string[]; fingerprint?:string }
export interface ExamExportPreview { people:number; files:number; bytes:number; paths:string[]; warnings:string[]; fingerprint:string }
const base='/zsjos/exam-collection';
const task=(id:number)=>`${base}/${id}`;
const get=<T>(url:string,params?:object)=>http.get(url,{params,paramsSerializer:{indexes:null}}).then(r=>unwrap<T>(r));
const post=<T>(url:string,data:object)=>http.post(url,data).then(r=>unwrap<T>(r));
export const examCollectionApi={
  exportSettings:(id:number)=>get<ExamExportSettings>(`${task(id)}/material-export/settings`),
  saveExportSettings:(id:number,data:ExamExportSettings)=>http.put(`${task(id)}/material-export/settings`,data).then(r=>unwrap<ExamExportSettings>(r)),
  exportPreview:(id:number,data:ExamExportRequest)=>post<ExamExportPreview>(`${task(id)}/material-export/preview`,data),
  createMaterialExport:(id:number,data:ExamExportRequest)=>post<number>(`${task(id)}/material-export/tasks`,data),
  exportMaterials:async(id:number,data:ExamExportRequest)=>{
    const response=await http.post(`${task(id)}/material-export/download`,data,{responseType:'blob',timeout:300000});
    if(response.data.type.includes('json')){const error=JSON.parse(await response.data.text()) as {msg?:string};throw new Error(error.msg||'导出失败，请重试');}
    const disposition = String(response.headers['content-disposition'] ?? '');
    const encodedName = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
    const filename = encodedName ? decodeURIComponent(encodedName) : '考试资料.zip';
    const url = URL.createObjectURL(response.data);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  uploadContent: async (file: File, kind: 'image' | 'video') => {
    const data = new FormData(); data.append('file', file); data.append('directory', `exam-collection-content-${kind}`);
    return unwrap<string>(await http.post('/infra/file/upload', data));
  },
  fields:()=>get<StudentInfoField[]>(`${base}/fields`),
  classes:()=>get<ExamClass[]>(`${base}/classes`),
  classOptions:(id:number)=>get<ExamClass[]>(`${task(id)}/class-options`),
  adjustClasses:(id:number,revision:number,classIds:number[],reason:string)=>post<boolean>(`${task(id)}/classes`,{revision,classIds,reason}),
  create:(data:ExamCreateCommand)=>post<ExamTaskDetail>(`${base}/create`,data),
  page:(params:Paging & {keyword?:string;status?:string})=>get<Page<ExamTask>>(`${base}/page`,params),
  detail:(id:number)=>get<ExamTaskDetail>(`${base}/detail`,{id}),
  notify:(id:number,revision:number,classIds:number[])=>post<boolean>(`${task(id)}/notify`,{revision,classIds}),
  complete:(id:number,revision:number,reason:string)=>post<boolean>(`${task(id)}/complete`,{revision,reason}),
  reopen:(id:number,revision:number,reason:string)=>post<boolean>(`${task(id)}/reopen`,{revision,reason}),
  members:(id:number,params:Paging & {keyword?:string;active?:boolean;filled?:boolean})=>get<Page<ExamMember>>(`${task(id)}/members`,params),
  adjust:(id:number,data:{revision:number;addServiceIds:number[];removeServiceIds:number[];reason:string})=>post<boolean>(`${task(id)}/members`,data),
  submissions:(id:number,params:Paging & {status?:string;serviceRelationId?:number})=>get<Page<ExamSubmission>>(`${task(id)}/submissions`,params),
  submission:(id:number,submissionId:number)=>get<ExamSubmissionDetail>(`${task(id)}/submissions/${submissionId}`),
  candidates:(id:number,params:Paging & {keyword?:string;classId?:number})=>get<Page<ExamCandidate>>(`${task(id)}/candidates`,params),
  archive:(id:number,relationId:number)=>get<ExamArchive>(`${task(id)}/archive/${relationId}`),
  resolve:(id:number,submissionId:number,data:{revision:number;serviceRelationId:number;archiveRevision:number;reason:string})=>post<boolean>(`${task(id)}/submissions/${submissionId}/resolve`,data),
  correctionPreview:(id:number,submissionId:number,serviceRelationId:number)=>get<ExamCorrectionPreview>(`${task(id)}/submissions/${submissionId}/correction-preview`,{serviceRelationId}),
  correct:(id:number,submissionId:number,data:ExamCorrectionCommand)=>post<boolean>(`${task(id)}/submissions/${submissionId}/correct`,data),
  invalidate:(id:number,submissionId:number,data:{revision:number;reason:string})=>post<boolean>(`${task(id)}/submissions/${submissionId}/invalidate`,data),
  events:(id:number,params:Paging)=>get<Page<ExamEvent>>(`${task(id)}/events`,params),
  attachment:async(id:number,submissionId:number,file:StudentInfoImage)=>{
    const response=await http.get(`${task(id)}/submissions/${submissionId}/attachments/${file.id}`,{responseType:'blob'});
    if(response.data.type.includes('json'))throw new Error('材料读取失败或没有权限');
    return URL.createObjectURL(new Blob([response.data],{type:file.contentType}));
  },
};
const planner='/zsjos/exam-collection-planner';
export const examPlannerApi={
  state:()=>get<ExamPlannerState>(`${planner}/state`),
  pending:()=>get<ExamTodo[]>(`${planner}/pending`),reminders:()=>get<ExamTodo[]>(`${planner}/reminders`),
  members:(id:number,params:Paging & {keyword?:string;filled?:boolean;sortField?:string;sortOrder?:'asc'|'desc'})=>get<Page<ExamPlannerMember>>(`${planner}/${id}/members`,params),
  acknowledge:(ids:number[])=>post<boolean>(`${planner}/acknowledge`,{ids}),
};
export function examSummaryApi(id:number){
  const prefix=`${task(id)}/summary`;
  return {
    columns:()=>get<SummaryColumn[]>(`${prefix}/columns`),classes:()=>get<{value:number;label:string}[]>(`${prefix}/classes`),
    page:(params:SummaryQuery)=>get<Page<SummaryRow>>(`${prefix}/page`,params),coverage:(params:SummaryQuery)=>get<{submitted:number;members:number}>(`${prefix}/coverage`,params),
    export:async(query:SummaryQuery,columns:string[])=>{
      const response=await http.post(`${prefix}/export`,{...query,columns},{responseType:'blob',timeout:300000});
      if(response.data.type.includes('json')){const error=JSON.parse(await response.data.text()) as {msg?:string};throw new Error(error.msg||'导出失败，请重试');}
      const url=URL.createObjectURL(response.data),anchor=document.createElement('a');anchor.href=url;anchor.download='考试资料收集汇总.xlsx';anchor.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
    },
  };
}
// Labels describe the non-configurable lifecycle in the exam collection API contract.
export const examSubmissionLabels:Record<ExamSubmission['status'],string>={ARCHIVED:'已归档',EXCEPTION:'待处理异常',INVALID:'已标记无效'};
export const examExceptionLabels:Record<string,string>={UNMATCHED:'未匹配到应收学员',AMBIGUOUS:'存在多个服务归属',INVALID_BINDING:'已确认的归属不可用',SERVICE_CLOSED:'学员课程服务已关闭'};
