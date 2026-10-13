import { http, unwrap } from './api'
export type CalendarColorType = 'PERSONAL' | 'COURSE'
export type CalendarColor = { id: string; name: string; color: string }
export type CalendarPalette = { presets: CalendarColor[]; colors: CalendarColor[] }
const root = '/zsjos/calendar-colors'
export const calendarColors = {
  get: async (type: CalendarColorType) => unwrap<CalendarPalette>(await http.get(root + '/' + type)),
  save: async (type: CalendarColorType, data: { name: string; color: string }, id?: string) =>
    unwrap<CalendarPalette>(id ? await http.put(root + '/' + type + '/' + id, data) : await http.post(root + '/' + type, data)),
  remove: async (type: CalendarColorType, id: string) => unwrap<CalendarPalette>(await http.delete(root + '/' + type + '/' + id))
}
