import { Tooltip } from 'antd'
import { UserOutlined } from '@ant-design/icons'
import type { MediaStudentAccountSummary, MediaStudentListItem } from '../services/api'
import { APP_ROUTES } from '../constants'
import { NameAvatar } from './LeadDetailOverview'
import MediaPlatformIcon from './MediaPlatformIcon'
import { resourceTarget } from './ResourceLink'

export function mediaStudentAccountHref(personId: number, accountId?: number) {
  const params = new URLSearchParams({ personId: String(personId) })
  if (accountId) params.set('accountId', String(accountId))
  return `${APP_ROUTES.MEDIA_STUDENTS}?${params}`
}
export function inboxAccountName(account: MediaStudentAccountSummary, accounts: MediaStudentAccountSummary[]) {
  const name = account.nickname?.trim() || '未命名账号'
  const duplicate = accounts.filter(item => item.nickname?.trim() === account.nickname?.trim()
    && item.platformValue === account.platformValue).length > 1
  return (!account.nickname?.trim() || duplicate) && account.accountNo ? `${name} · ${account.accountNo}` : name
}
export type StudentOperator = { id: number; name: string }
export function studentOperators(student: MediaStudentListItem): StudentOperator[] {
  const operators = new Map<number, StudentOperator>()
  for (const service of student.services || []) {
    if (service.operatorUserId == null) continue
    const current = operators.get(service.operatorUserId)
    if (!current || current.name === '姓名未提供') operators.set(service.operatorUserId, {
      id: service.operatorUserId, name: service.operatorUserName?.trim() || '姓名未提供',
    })
  }
  return [...operators.values()]
}

export default function MediaStudentInboxCard({ student, selected, collapsed, canOpenAccount, onOpen, onOperatorFilter, operatorFilterId, filteringDisabled }: {
  student: MediaStudentListItem; selected: boolean; collapsed: boolean; canOpenAccount: boolean;
  onOpen: (accountId?: number) => void;
  onOperatorFilter?: (operator: StudentOperator) => void; operatorFilterId?: number; filteringDisabled?: boolean;
}) {
  const label = `${student.name || '未填写姓名'} · ${student.personNo || '暂无学员编号'}`
  const operators = studentOperators(student)
  return <article className={`lead-inbox-item media-students-item media-students-account-card${selected ? ' active' : ''}`}
    onClick={event => { if (!(event.target as Element).closest('a,button,.media-students-account-row,.media-students-card-operators')) onOpen() }}>
    <div className="media-students-card-header"><Tooltip title={collapsed ? label : undefined}>
      <button type="button" className="media-students-card-select" aria-label={label} aria-current={selected ? 'true' : undefined} onClick={() => onOpen()}>
        <NameAvatar name={student.name || '学员'} seed={student.personNo} size={36} subjectType="student" />
        {!collapsed && <span className="media-students-item-copy"><span className="media-students-item-heading"><strong>{student.name || '未填写姓名'}</strong><span>{student.personNo || '暂无学员编号'}</span></span></span>}
      </button>
    </Tooltip>
    {!collapsed && <div className="media-students-card-operators" aria-label="当前运营">
      {operators.length ? operators.map(operator => <button key={operator.id} type="button"
        className="media-students-operator" disabled={filteringDisabled || !onOperatorFilter}
        aria-label={`筛选运营：${operator.name}`} aria-pressed={operatorFilterId === operator.id}
        title={`当前运营：${operator.name}`}
        onClick={event => { event.stopPropagation(); onOperatorFilter?.(operator) }}><UserOutlined /><span>{operator.name}</span></button>) : <span className="media-students-operator-unassigned"><UserOutlined />未指派</span>}
    </div>}</div>
    {!collapsed && <div className="media-students-card-accounts">
      {!Array.isArray(student.accounts) ? <span className="media-students-account-empty">账号信息未加载，请刷新重试</span>
        : student.accounts.length ? student.accounts.map(account => {
          const name = inboxAccountName(account, student.accounts)
          const target = account.homepageUrl ? resourceTarget(account.homepageUrl) : undefined
          const href = canOpenAccount && target && !target.internal ? target.href : undefined
          const platform = account.platformLabel?.trim() || '平台未记录'
          const logo = <MediaPlatformIcon platform={account.platformValue} />
          return <div className="media-students-account-row" key={account.id}>

            {href ? <a className="media-students-platform-icon media-students-external-link" href={href} target="_blank" rel="noopener noreferrer"
              aria-label={`打开${platform}外站主页：${name}（新标签页）`} title={`打开${platform}外站主页`}
              onClick={event => event.stopPropagation()}>{logo}</a>
              : <span className="media-students-platform-icon" title={`${platform} · 未提供可用外站链接`} role="img" aria-label={platform}>{logo}</span>}

            {canOpenAccount ? <a className="media-students-account-link" href={mediaStudentAccountHref(student.personId, account.id)}
              aria-label={`查看本站账号：${name}`} title="查看本站账号"
              onClick={event => {
                event.stopPropagation()
                if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
                event.preventDefault(); onOpen(account.id)
              }}><span>{name}</span></a> : <span className="media-students-account-name">{name}</span>}
          </div>
        }) : <span className="media-students-account-empty">暂无可见账号</span>}
    </div>}
  </article>
}
