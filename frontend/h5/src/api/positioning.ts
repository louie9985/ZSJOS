import axios from 'axios'

export interface PositioningField { key:string; title:string; enabled?:boolean; type?:string }
export interface PositioningConfirmation {
  state:'ready'|'processed'
  accountName?:string
  platformLabel?:string
  submittedAt?:string|number
  trialEndDate?:string
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
