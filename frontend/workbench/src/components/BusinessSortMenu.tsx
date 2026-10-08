import { Button, Dropdown } from 'antd'
import { SortAscendingOutlined } from '@ant-design/icons'
import type { BusinessSort } from '../services/businessListSort'

export default function BusinessSortMenu({ label, value, fields, onChange }: {
  label: string
  value: BusinessSort
  fields: { field: string; label: string }[]
  onChange: (sort: BusinessSort) => void
}) {
  return <Dropdown trigger={['click']} menu={{
    style: { maxHeight: '70vh', overflowY: 'auto' },
    selectedKeys: [value.sortField ? `${value.sortField}:${value.sortOrder}` : 'default'],
    items: [{ key: 'default', label: '默认排序' }, ...fields.map(item => ({ key: item.field, label: item.label, children: [
      { key: `${item.field}:ascend`, label: '升序' }, { key: `${item.field}:descend`, label: '降序' },
    ] }))],
    onClick: ({ key }) => {
      if (key === 'default') onChange({})
      else {
        const [sortField, sortOrder] = key.split(':')
        if (fields.some(item => item.field === sortField) && (sortOrder === 'ascend' || sortOrder === 'descend')) onChange({ sortField, sortOrder })
      }
    },
  }}><Button aria-label={`选择${label}排序`} icon={<SortAscendingOutlined />}>排序</Button></Dropdown>
}
