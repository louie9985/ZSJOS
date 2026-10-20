import { Tag, Tooltip } from 'antd'
import EmployeeAvatar from './EmployeeAvatar'
import { educationMoney, type EducationTeamMember } from '../services/educationTeam'
export default function EducationTeamCard({member,selected,onSelect}:{member:EducationTeamMember;selected:boolean;onSelect:()=>void}){
 const remaining=member.todayFollowUpRemainingCount,total=member.todayFollowUpTotalCount,ready=remaining!=null&&total!=null
 const metrics=[['新意向',member.todayNewIntentionCount],['老意向跟进次数',member.todayOldIntentionFollowUpRecordCount],['接收客资数',member.todayReceivedCount],['今日成交额',educationMoney(member.todayOrderAmount)],['今日待跟进未完成',remaining],['今日学员联系数',member.todayStudentContactCount]]
 return <button type="button" className={`education-team-card ${selected?'is-active':''}`} aria-pressed={selected} onClick={onSelect}>
  <div className="education-team-person"><EmployeeAvatar avatar={member.avatar} name={member.name} size={32}/><strong>{member.name}</strong><Tooltip title="仅表示今日客资跟进任务：剩余待跟进客资数 / 今日待跟进客资总数"><Tag color={ready?(remaining===0?'success':'warning'):'default'}>{ready?(remaining===0?'已完成':`${remaining} / ${total}`):'状态待更新'}</Tag></Tooltip></div>
  <div>{member.departmentName||'部门信息缺失'} · {member.postNames?.join('、')||'岗位信息缺失'}</div>
  <div className="education-team-muted">{member.username} · {member.mobile||'未填写手机号'} <Tag>{member.accountStatus===0?'启用':'停用'}</Tag></div>
  <div className="education-team-muted" style={{marginTop:'var(--crm-sp-3)'}}>今日状态</div>
  <div className="education-team-metrics">{metrics.map(([label,value])=><div key={String(label)}><span>{label}</span><strong>{value??'—'}</strong></div>)}</div>
 </button>
}
