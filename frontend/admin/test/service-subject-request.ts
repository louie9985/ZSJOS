export const useUserStore=()=>({dataAccess:{tenantReadAll:false},getPermissions:new Set()})
export const checkPermi=()=>false
const rows=[1,2].map(id=>({personId:7,name:'同一学员',serviceRelationId:id,service:{serviceRelationId:id,courseName:'课程'+id,className:'班级'+id,status:'active'}}))
export default {get:async({url}:any)=>url.endsWith('my-service-page')?{list:rows,total:2}:structuredClone(rows.find(r=>r.serviceRelationId===Number(url.split('/').at(-1))))}
