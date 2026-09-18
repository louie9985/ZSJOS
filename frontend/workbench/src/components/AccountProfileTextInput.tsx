import { Input } from 'antd'
import { memo, useEffect, useState } from 'react'

/** Keystrokes stay local; the parent receives every value synchronously for immediate saves. */
export default memo(function AccountProfileTextInput({ fieldKey, label, value, multiline, date, maxLength, disabled, onChange, onBlur }: {
  fieldKey: string; label: string; value: string; multiline: boolean; date: boolean; maxLength: number; disabled: boolean;
  onChange: (key: string, value: string) => void; onBlur: () => void;
}) {
  const [text, setText] = useState(value)
  useEffect(() => setText(value), [value])
  const props = { 'aria-label': label, value: text, maxLength, disabled, onBlur,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setText(event.target.value)
      onChange(fieldKey, event.target.value)
    } }
  return multiline ? <Input.TextArea {...props} rows={3} showCount />
    : <Input {...props} type={date ? 'date' : 'text'} placeholder="待补充，可留空" />
})
