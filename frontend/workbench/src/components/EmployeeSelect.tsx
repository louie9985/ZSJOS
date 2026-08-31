import { Select, type SelectProps } from 'antd'
import EmployeeAvatar from './EmployeeAvatar'
import type { SalesUser } from '../services/api'

export function employeeOptionLabel(user: SalesUser) {
  return `${user.nickname}${user.deptName ? ` · ${user.deptName}` : ''}`
}

type EmployeeSelectProps = Omit<SelectProps<number>, 'options'> & { users: SalesUser[] }

export default function EmployeeSelect({ users, ...props }: EmployeeSelectProps) {
  const options = users.map(user => ({ value: user.id, label: employeeOptionLabel(user), user }))
  return <Select<number>
    {...props}
    options={options}
    optionRender={option => {
      const user = option.data.user
      return <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
        <EmployeeAvatar avatar={user.avatar} name={user.nickname} size={28} />
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{option.label}</span>
      </div>
    }}
    labelRender={option => {
      const user = options.find(item => item.value === option.value)?.user
      return user ? <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}><EmployeeAvatar avatar={user.avatar} name={user.nickname} size={24} /><span>{option.label}</span></div> : option.label
    }}
  />
}
