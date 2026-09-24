import { BookOutlined, GlobalOutlined, LinkOutlined, PlayCircleOutlined, CustomerServiceOutlined } from '@ant-design/icons'
import { Tag, Tooltip } from 'antd'
import type { DictData, MediaStudentAccountSummary, MediaStudentListItem } from '../services/api'
import { APP_ROUTES } from '../constants'
import { NameAvatar } from './LeadDetailOverview'

export const ACCOUNT_PLATFORM_DICT = 'zsjos_account_platform'
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
const tagColors: Record<string, string> = { primary: 'blue', danger: 'red', success: 'green', warning: 'orange', info: 'default' }
function platformIcon(value?: string) {
  switch (value) {
    case 'douyin': return <CustomerServiceOutlined />
    case 'xiaohongshu': return <BookOutlined />
    case 'shipinhao': return <PlayCircleOutlined />
    default: return <GlobalOutlined />
  }
}

export default function MediaStudentInboxCard({ student, selected, collapsed, canOpenAccount, platforms, onOpen }: {
  student: MediaStudentListItem; selected: boolean; collapsed: boolean; canOpenAccount: boolean;
  platforms: DictData[]; onOpen: (accountId?: number) => void;
}) {
  const label = `${student.name || '未填写姓名'} · ${student.personNo || '暂无学员编号'}`
  return <article className={`lead-inbox-item media-students-item media-students-account-card${selected ? ' active' : ''}`}
    onClick={event => { if (!(event.target as Element).closest('a,button')) onOpen() }}>
    <Tooltip title={collapsed ? label : undefined}>
      <button type="button" className="media-students-card-select" aria-label={label} aria-current={selected ? 'true' : undefined} onClick={() => onOpen()}>
        <NameAvatar name={student.name || '学员'} seed={student.personNo} size={36} subjectType="student" />
        {!collapsed && <span className="media-students-item-copy"><span className="media-students-item-heading"><strong>{student.name || '未填写姓名'}</strong><span>{student.personNo || '暂无学员编号'}</span></span></span>}
      </button>
    </Tooltip>
    {!collapsed && <div className="media-students-card-accounts">
      {!Array.isArray(student.accounts) ? <span className="media-students-account-empty">账号信息未加载，请刷新重试</span>
        : student.accounts.length ? student.accounts.map(account => {
          const color = platforms.find(item => item.value === account.platformValue)?.colorType
          const name = inboxAccountName(account, student.accounts)
          return <div className="media-students-account-row" key={account.id}>
            <span className="media-students-account-platform">{platformIcon(account.platformValue)}<Tag color={color ? tagColors[color] || color : 'blue'}>{account.platformLabel?.trim() || '平台未记录'}</Tag></span>
            {canOpenAccount ? <a className="media-students-account-link" href={mediaStudentAccountHref(student.personId, account.id)}
              onClick={event => { if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return; event.preventDefault(); event.stopPropagation(); onOpen(account.id) }}>
              <LinkOutlined /><span>{name}</span></a> : <span className="media-students-account-name">{name}</span>}
          </div>
        }) : <span className="media-students-account-empty">暂无可见账号</span>}
    </div>}
  </article>
}
