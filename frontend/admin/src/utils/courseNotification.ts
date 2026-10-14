import type { Router } from 'vue-router'
import type { NotifyMessageVO } from '@/api/system/notify/message'
import { getCourseCalendarEvent } from '@/api/zsjos/calendar'

export const isCourseNotification = (message: NotifyMessageVO) => message.actionType === 'business_detail'
  && message.bizType === 'calendar' && message.sceneCode === 'zsjos.calendar.course'
  && Number.isSafeInteger(message.bizId) && Number(message.bizId) > 0

export async function openCourseNotification(router: Router, message: NotifyMessageVO) {
  if (!isCourseNotification(message)) throw new Error('课程通知链接无效')
  // Find the authorized renderer; do not invent a route or grant menu access.
  const route = router.getRoutes().find(item => item.name === 'ZsjosCourseCalendar')
  if (!route) throw new Error('当前账号没有课程日历菜单权限')
  await getCourseCalendarEvent(Number(message.bizId))
  await router.push({ name: route.name!, query: { courseId: String(message.bizId) } })
}
