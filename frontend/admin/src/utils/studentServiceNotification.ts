import type {Router} from "vue-router"
import type {NotifyMessageVO} from "@/api/system/notify/message"
import {getMyStudentService, getMyStudentServicePage} from "@/api/zsjos/registration"
export const isStudentServiceNotification = (m: NotifyMessageVO) => m.actionType === "business_detail"
  && Number(m.bizId) > 0 && (m.bizType === "student" || (m.bizType === "student_service"
  && !m.sceneCode?.startsWith("media.") && !["zsjos.student.operator_assigned", "zsjos.registration.director_assigned"].includes(m.sceneCode || "")))
export async function openStudentServiceNotification(router: Router, m: NotifyMessageVO) {
  const route=router.getRoutes().find(r=>r.name === "ZsjosMyStudents")
  if (!route) throw Error("当前账号没有学生交付菜单权限")
  if (m.bizType === "student_service") {
    await getMyStudentService(Number(m.bizId))
    await router.push({name:route.name!,query:{serviceRelationId:String(m.bizId)}})
  } else {
    const result=await getMyStudentServicePage({pageNo:1,pageSize:1,personId:Number(m.bizId)})
    if (!result.total) throw Error("当前没有可访问的课程服务")
    await router.push({name:route.name!,query:result.total===1 ? {serviceRelationId:String(result.list[0].serviceRelationId)} : {personId:String(m.bizId)}})
  }
}
