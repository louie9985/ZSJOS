import request from '@/config/axios'
export interface StudentDeliveryConfig { s0Days:number;s1Days:number;s2Days:number;s3Days:number;s4Days:number;s5Days:number;s6Days:number }
export const getStudentDeliveryConfig=()=>request.get({url:'/zsjos/student-delivery/config'})
export const updateStudentDeliveryConfig=(data:StudentDeliveryConfig)=>request.put({url:'/zsjos/student-delivery/config',data})
