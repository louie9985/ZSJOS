import { type FollowUpTimeShortcut } from './leadFollowUp'

const SHORT: FollowUpTimeShortcut[] = [
  { key: '30-minutes', label: '+30 分钟', amount: 30, unit: 'minute' },
  { key: '1-hour', label: '+1 小时', amount: 60, unit: 'minute' },
]
const FULL: FollowUpTimeShortcut[] = [...SHORT,
  { key: '1-day', label: '+1 天', amount: 1, unit: 'day' },
  { key: '2-days', label: '+2 天', amount: 2, unit: 'day' },
  { key: '3-days', label: '+3 天', amount: 3, unit: 'day' },
]
export function studentContactSchedule(type: string, successful: boolean, planCompleted: boolean,
  firstContactMinutes = 0, studyPlanMinutes = 0) {
  const nextType = type === 'student_first_contact' ? (successful ? (planCompleted ? 'student_contact' : 'student_study_plan') : type)
    : type === 'student_study_plan' ? (successful ? 'student_contact' : type) : 'student_contact'
  return { nextType, required: nextType !== 'student_contact',
    timeout: nextType === 'student_first_contact' ? firstContactMinutes : nextType === 'student_study_plan' ? studyPlanMinutes : 0,
    shortcuts: nextType === 'student_first_contact' ? SHORT : FULL }
}
