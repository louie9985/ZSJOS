import axios from 'axios'

export interface PositioningField { key:string; title:string; enabled?:boolean; type?:string }
export interface PositioningConfirmation {
  state:'ready'|'processed'
  cardNo?:string
  serviceLabel?:string
  accountName?:string
  platformLabel?:string
  submittedAt?:string|number
  fields?:PositioningField[]
  values?:Record<string,unknown>
  dictSnapshots?:Record<string,unknown>
  legacySections?:Record<string,Record<string,unknown>>
}

const publicRequest=axios.create({baseURL:'/public-api',timeout:15000})
const unwrap=<T>(response:{data?:{code?:number;data?:T;msg?:string}}):T=>{
  if(response.data?.code!==0)throw new Error(response.data?.msg||'请求失败')
  return response.data.data as T
}
const headers=(token:string)=>({'X-Positioning-Token':token})

export const getPositioningCard=async(token:string)=>unwrap<PositioningConfirmation>(
  await publicRequest.get('/zsjos/positioning-confirmation/detail',{headers:headers(token)}))
export const decidePositioning=async(token:string,decision:'agree'|'request_changes',comment?:string)=>unwrap<boolean>(
  await publicRequest.post('/zsjos/positioning-confirmation/decision',{decision,comment},{headers:headers(token)}))

export interface PositioningFile { id:number; name:string; type:string; size:number; url?:string }
export interface PositioningMaterial {
  id:number; title:string; fields:Array<{key:string;label:string;type:string}>;
  values:Record<string,unknown>; dictSnapshot:Record<string,unknown>;
  files:Array<{fileId:number;fieldKey:string;name:string;contentType:string;size:number;previewUrl?:string}>;
}
export const getPositioningAttachment=async(token:string,id:number)=>unwrap<PositioningFile>(
  await publicRequest.get(`/zsjos/positioning-confirmation/attachments/${id}`,{headers:headers(token)}))
export const getPositioningMaterial=async(token:string,id:number)=>unwrap<PositioningMaterial>(
  await publicRequest.get(`/zsjos/positioning-confirmation/materials/${id}`,{headers:headers(token)}))
