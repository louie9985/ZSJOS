import request from './request'
export interface PositioningCard { id:number; cardNo:string; status:string; professionalRisk:boolean; layer1Json?:string; layer2Json?:string; formulaJson?:string; version:number }
export interface PositioningConfirmation { state:'ready'|'pending_student_link'|'pending_partner_account'; card?:PositioningCard }
const unwrap=<T>(r:any):T=>r?.data?.data??r?.data??r
export const getPositioningCard=async(id:number)=>unwrap<PositioningConfirmation>(await request.get(`/zsjos/positioning-confirmation/${id}`))
export const confirmPositioning=async(id:number,version:number,comment?:string)=>unwrap<boolean>(await request.post(`/zsjos/positioning-confirmation/${id}/confirm`,{version,comment}))
export const rejectPositioning=async(id:number,version:number,comment:string)=>unwrap<boolean>(await request.post(`/zsjos/positioning-confirmation/${id}/reject`,{version,comment}))
