import { http, unwrap, type ManagedLead, type ManagedLeadPageParams, type PageResult, type LeadInboxFilterProfile } from './api'
export type OperationsMember = { userId: number; name: string; accountStatus: number; leadCount: number }
export const myOperationsApi = {
  members: async () => unwrap<OperationsMember[]>(await http.get('/zsjos/my-operations/members')),
  leads: async (id: number, params: ManagedLeadPageParams) => unwrap<PageResult<ManagedLead>>(await http.post(`/zsjos/my-operations/${id}/leads/search-page`, params)),
  lead: async (memberId: number, id: number) => unwrap<ManagedLead>(await http.get(`/zsjos/my-operations/${memberId}/leads/${id}`)),
  profile: async () => unwrap<LeadInboxFilterProfile>(await http.get('/zsjos/my-operations/leads/filter-profile')),
}
