import type { Timestamp } from './time'
import { http, unwrap, type AdvancedFilterGroup, type ManagedLead, type PageResult, type AssignmentUser } from './api'
export type EducationTeamMember = {
 userId:number; name:string; username:string; mobile?:string; avatar?:string; accountStatus:number;
 deptId?:number; departmentName?:string; postNames?:string[]; educationOwnershipSupported?:boolean; orderCreationSupported?:boolean;
 todayNewIntentionCount?:number; todayOldIntentionFollowUpRecordCount?:number; todayStudentContactCount?:number;
 todayPendingCount:number; todayFollowUpTotalCount?:number; todayFollowUpRemainingCount?:number;
 todayReceivedCount?:number; todayQualifiedCount?:number; todayFollowUpRecordCount?:number; todayOrderAmount?:number;
 pendingQualificationCount?:number; todayFollowUpStatus:string; firstFollowTimeoutCount:number; suspendedLeadCount:number;
 validLeadCount:number; convertedLeadCount:number; effectiveOrderCount:number; effectiveOrderAmount:number;
 categoryCounts:Array<{value:string;label:string;count:number;configured:boolean}>
}
export type EducationTeamTask = {id:number;taskType:string;leadId:number;leadNo?:string;leadName?:string;dueAt?:Timestamp;overdue:boolean}
export type EducationTeamAction = 'transfer'|'restore'|'recycle'|'overturn-valid'
export type EducationTeamBatch = {successCount:number;failureCount:number;items:Array<{leadId:number;leadNo?:string;success:boolean;code:string;message:string}>}
export type EducationTeamLead = Omit<ManagedLead,'availableActions'> & {availableActions:Array<{code:string;enabled:boolean;qualificationToken?:string}>}
export type EducationTeamEvidence = {fileId?:number;infraFileId?:number;fileUrl:string;originalName:string;contentType?:string;fileSize?:number;sort?:number}
const BASE='/zsjos/education-team'
export const educationTeamApi = {
 page: async (params:{pageNo:number;pageSize:number;keyword?:string;accountStatus?:number;advancedFilter?:AdvancedFilterGroup}) => unwrap<PageResult<EducationTeamMember>>(await http.post(`${BASE}/search-page`,params)),
 overview: async (id:number) => unwrap<EducationTeamMember>(await http.get(`${BASE}/${id}/overview`)),
 leads: async (id:number,params:{pageNo:number;pageSize:number;keyword?:string;recycledOnly?:boolean;advancedFilter?:AdvancedFilterGroup}) => unwrap<PageResult<EducationTeamLead>>(await http.post(`${BASE}/${id}/leads/search-page`,params)),
 lead: async (id:number) => unwrap<EducationTeamLead>(await http.get(`${BASE}/leads/${id}`)),
 tasks: async (id:number,params:{pageNo:number;pageSize:number;bucket?:string}) => unwrap<PageResult<EducationTeamTask>>(await http.get(`${BASE}/${id}/tasks`,{params})),
 candidates: async (ids:number[]) => unwrap<AssignmentUser[]>(await http.get(`${BASE}/batch-transfer-candidates`,{params:{leadIds:ids.join(',')}})),
 account: async (id:number,status:number,reason:string) => unwrap<boolean>(await http.put(`${BASE}/${id}/account-status`,{status,reason})),
 batch: async (action:Exclude<EducationTeamAction,'overturn-valid'>,leadIds:number[],data:{reason:string;targetUserId?:number;idempotencyKey:string}) => unwrap<EducationTeamBatch>(await http.post(`${BASE}/leads/batch-${action}`,{leadIds,...data})),
 overturn: async (id:number,data:{reason:string;qualificationToken:string;idempotencyKey:string;attachments:EducationTeamEvidence[]}) => unwrap<boolean>(await http.post(`${BASE}/leads/${id}/overturn-valid`,data)),
 upload: async (file:File) => {const data=new FormData();data.append('file',file);return unwrap<EducationTeamEvidence>(await http.post(`${BASE}/overturn-attachment/upload`,data))},
}
export const educationMoney=(value?:number)=>value==null?'—':new Intl.NumberFormat('zh-CN',{style:'currency',currency:'CNY'}).format(value)
export function appendEducationTeamRows(old:EducationTeamMember[],rows:EducationTeamMember[]){const result=new Map(old.map(row=>[row.userId,row]));rows.forEach(row=>result.set(row.userId,row));return [...result.values()]}
