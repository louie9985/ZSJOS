import type { StudentInfoField } from '../../services/studentInfo';

export const EXAM_ATTENDANCE_KEY = 'exam_attendance', EXAM_ABSENCE_REASON_KEY = 'absence_reason';
const fixed = (key: string, label: string, type: StudentInfoField['type'] = 'text', options?: string[]): StudentInfoField =>
  ({ key, label, type, options, group: 'basic', enabled: true, required: true, sensitive: false, sort: 0 });
/** Fields every new exam task freezes first; the server snapshot enforces the same set and the 是/否 gate. */
export const FIXED_EXAM_FIELDS: StudentInfoField[] = [fixed('name', '姓名'), fixed('mobile', '手机号'), fixed('id_card', '身份证号码'),
  fixed(EXAM_ATTENDANCE_KEY, '是否参加本次考试', 'radio', ['是', '否']), fixed(EXAM_ABSENCE_REASON_KEY, '您不参加本次考试的原因', 'textarea')];

/** Mirrors the H5 form grouping so the create dialog, preview and public form show one order. */
export function examFieldGroups<T extends Pick<StudentInfoField, 'type'>>(fields: T[]) {
  return { basic: fields.filter(field => field.type !== 'images'), materials: fields.filter(field => field.type === 'images') };
}
