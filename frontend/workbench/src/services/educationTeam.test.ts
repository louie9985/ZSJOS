import { beforeEach,describe,it,expect,vi } from 'vitest'
import { readFileSync,readdirSync } from 'node:fs'
const mock=vi.hoisted(()=>({get:vi.fn(),post:vi.fn(),put:vi.fn()}))
vi.mock('./api',()=>({http:mock,unwrap:(value:unknown)=>value}))
import { educationTeamApi,appendEducationTeamRows,type EducationTeamMember } from './educationTeam'
beforeEach(()=>{Object.values(mock).forEach(fn=>fn.mockReset().mockResolvedValue([]))})
describe('education team isolation',()=>{
 it('routes reads and commands exclusively to its own backend',async()=>{await educationTeamApi.page({pageNo:1,pageSize:20});await educationTeamApi.overview(1);await educationTeamApi.leads(1,{pageNo:1,pageSize:20});await educationTeamApi.tasks(1,{pageNo:1,pageSize:20});await educationTeamApi.lead(5);await educationTeamApi.candidates([5]);await educationTeamApi.account(1,0,'reason');await educationTeamApi.batch('restore',[5],{reason:'reason',idempotencyKey:'key'});await educationTeamApi.overturn(5,{reason:'reason',idempotencyKey:'key',qualificationToken:'token',attachments:[]});for(const fn of Object.values(mock))for(const [path] of fn.mock.calls)expect(path).toContain('/zsjos/education-team/')})
 it('preserves idempotency and selected scope in batch command',async()=>{await educationTeamApi.batch('transfer',[5,6],{reason:'说明',targetUserId:2,idempotencyKey:'stable'});expect(mock.post).toHaveBeenCalledWith('/zsjos/education-team/leads/batch-transfer',{leadIds:[5,6],reason:'说明',targetUserId:2,idempotencyKey:'stable'})})
 it('deduplicates pagination without losing refreshed records',()=>{const first={userId:1,name:'old'} as EducationTeamMember,next={userId:1,name:'new'} as EducationTeamMember;expect(appendEducationTeamRows([first],[next])).toEqual([next])})
 it('does not import subordinate business code or expose sales settings',()=>{for(const path of ['src/pages/EducationTeamPage.tsx',...readdirSync('src/pages/educationTeam').map(name=>'src/pages/educationTeam/'+name),'src/components/EducationTeamCard.tsx','src/components/EducationTeamStatusFilter.tsx','src/services/educationTeam.ts']){const s=readFileSync(path,'utf8');expect(s,path).not.toContain('subordinate-sales');expect(s,path).not.toContain('subordinateSales');expect(s,path).not.toContain('SubordinateSales');expect(s,path).not.toContain('newcomer-pool');expect(s,path).not.toContain('dispatch-mode')}})
})
