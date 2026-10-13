import dayjs from 'dayjs'
import { describe,it,expect } from 'vitest'
import { calendarViewRange, layoutGridDay, moveCalendarView, touchesGridDay } from './calendarViewLayout'
const date=dayjs('2026-10-13')
const event=(id:number,start:string,end:string) => ({ id,title:String(id),start:dayjs(start),end:dayjs(end) })
describe('calendar day/week layout',()=>{
 it('queries the complete Monday week even across month/year boundaries',()=>{
  const range=calendarViewRange(dayjs('2027-01-01'),'week')
  expect(range.start.format('YYYY-MM-DD')).toBe('2026-12-28')
  expect(range.end.format('YYYY-MM-DD')).toBe('2027-01-04')
  expect(calendarViewRange(date,'day').end.diff(date.startOf('day'),'hour')).toBe(24)
 })
 it('keeps navigation inside its selected view',()=>{
  expect(moveCalendarView(date,'day',1).format('YYYY-MM-DD')).toBe('2026-10-14')
  expect(moveCalendarView(date,'week',-1).format('YYYY-MM-DD')).toBe('2026-10-06')
  expect(moveCalendarView(dayjs('2026-01-31'),'month',1).format('YYYY-MM-DD')).toBe('2026-02-01')
 })
 it('clips cross-midnight events and excludes an exclusive midnight end',()=>{
  const e=event(1,'2026-10-12T23:00','2026-10-13T01:30')
  expect(layoutGridDay([e],date)[0]).toMatchObject({start:0,end:90})
  expect(touchesGridDay(event(2,'2026-10-12T23:00','2026-10-13T00:00'),date)).toBe(false)
 })
 it('uses consistent columns across an entire connected overlap group',()=>{
  const rows=layoutGridDay([event(1,'2026-10-13T09:00','2026-10-13T12:00'),event(2,'2026-10-13T09:00','2026-10-13T10:00'),event(3,'2026-10-13T10:00','2026-10-13T11:00'),event(4,'2026-10-13T12:00','2026-10-13T13:00')],date)
  expect(rows.map(r=>r.columns)).toEqual([2,2,2,1])
  expect(rows.map(r=>r.column)).toEqual([0,1,1,0])
 })
 it('retains point events and excludes all-day records from timed geometry',()=>{
  expect(layoutGridDay([event(1,'2026-10-13T09:00','2026-10-13T09:00')],date)[0]).toMatchObject({start:540,end:541})
  expect(layoutGridDay([{...event(2,'2026-10-13','2026-10-14'),allDay:true}],date)).toEqual([])
 })
})
