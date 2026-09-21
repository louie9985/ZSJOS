import { Input } from 'antd'
import type { InputProps, InputRef } from 'antd'
import { forwardRef, useState } from 'react'
import { LinkOutlined } from '@ant-design/icons'
import ResourceLink, { resourceTarget } from './ResourceLink'

/** Keep the submitted text intact; validation remains owned by the field's business contract. */
export default forwardRef<InputRef, InputProps>(function ResourceLinkInput(inputProps, ref) {
  const { value, defaultValue, onChange, ...props } = inputProps
  const [draft, setDraft] = useState(defaultValue ?? '')
  const text = String('value' in inputProps ? value ?? '' : draft)
  const target = resourceTarget(text)
  return <div style={{ minWidth: 0, width: '100%' }}>
    <Input ref={ref} prefix={<LinkOutlined />} allowClear placeholder="请输入链接"
      {...props} value={text} onChange={event => { setDraft(event.target.value); onChange?.(event) }} />
    {target && <ResourceLink href={text} variant="resource" />}
  </div>
})
