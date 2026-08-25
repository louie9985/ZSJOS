import { useState, useCallback, useRef } from 'react'
import { Select, type SelectProps } from 'antd'
import { api, type HrmPortableEmployee } from '../services/api'

/** HRM 员工选择器：远程搜索员工名/工号，用于绩效范围、员工档案筛选等场景。 */
export default function HrmEmployeePicker({ ...props }: Omit<SelectProps<number>, 'options' | 'onSearch' | 'filterOption' | 'showSearch' | 'labelInValue'>) {
  const [options, setOptions] = useState<Array<{ value: number; label: string }>>([])
  const [loading, setLoading] = useState(false)
  const searchVersion = useRef(0)

  const search = useCallback(async (keyword: string) => {
    const version = ++searchVersion.current
    setLoading(true)
    try {
      const result = await api.hrm.employeeSimplePage({ pageNo: 1, pageSize: 20, name: keyword || undefined })
      if (version !== searchVersion.current) return
      setOptions(result.list.map((employee: HrmPortableEmployee) => ({
        value: employee.id,
        label: `${employee.name || ''}${employee.jobNumber ? `（${employee.jobNumber}）` : ''}${employee.deptName ? ` · ${employee.deptName}` : ''}`
      })))
    } catch {
      if (version === searchVersion.current) setOptions([])
    } finally {
      if (version === searchVersion.current) setLoading(false)
    }
  }, [])

  return <Select<number>
    {...props}
    showSearch
    filterOption={false}
    loading={loading}
    options={options}
    onSearch={search}
    placeholder={props.placeholder || '搜索员工姓名或工号'}
    labelInValue={false}
    onFocus={() => void search('')}
  />
}
