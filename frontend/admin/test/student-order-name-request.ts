export const useUserStore=()=>({dataAccess:{tenantReadAll:false},getPermissions:new Set()})
export const checkPermi=()=>false
export const useRoute=()=>({query:{},fullPath:'/zsjos/my-students'})
const rows=[1,2,3].map(id=>({personId:1,serviceRelationId:id,name:id===1?'首次订单姓名':id===2?'独立服务快照姓名':null,services:[],service:{serviceRelationId:id,courseName:'课程'+id,status:'active',orderNo:id===2?undefined:'ORDER-'+id}}))
export default {get:async({url,params}:any)=>url.endsWith('-page')?{list:rows.filter(r=>!params?.keyword||r.name?.includes(params.keyword)),total:rows.length}:structuredClone(rows.find(r=>r.serviceRelationId===Number(url.split('/').at(-1)))),post:async()=>{throw Error('No writes')}}
