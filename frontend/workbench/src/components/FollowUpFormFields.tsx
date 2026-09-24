import { useId, useState } from 'react'
import { Alert, Button, DatePicker, Form, Input, Spin, type FormInstance } from 'antd'
import dayjs from 'dayjs'
import type { LeadAttachment, ManagedLead } from '../services/api'
import type { useFollowUpDictionaries } from '../services/useFollowUpDictionaries'
import type { useLeadSalesStages } from '../services/useLeadSalesStages'
import { applyFollowUpTimeShortcut, FOLLOW_UP_TIME_SHORTCUTS } from '../services/leadFollowUp'
import { appendFollowUpNote, followUpOptionsWithSnapshot, FOLLOW_UP_REMARK_LIMIT, FOLLOW_UP_VISIBLE_OPTIONS, type FollowUpValues } from '../services/followUpForm'
import type { DeferredUploadItem } from '../services/deferredUpload'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import FollowUpChoiceGroup from './FollowUpChoiceGroup'

export default function FollowUpFormFields({ lead, form, dictionaries, stages, images, onImagesChange, disabled, onModified }: {
  lead: ManagedLead; form: FormInstance<FollowUpValues>; dictionaries: ReturnType<typeof useFollowUpDictionaries>; stages: ReturnType<typeof useLeadSalesStages>
  images: DeferredUploadItem<LeadAttachment>[]; onImagesChange: (items: DeferredUploadItem<LeadAttachment>[]) => void; disabled: boolean; onModified?: () => void
}) {
  const [notesExpanded, setNotesExpanded] = useState(false)
  const [noteError, setNoteError] = useState('')
  const [shortcut, setShortcut] = useState<{ key: string; time: number }>()
  const time = Form.useWatch('nextFollowUpAt', form)
  const notesId = useId()
  const locked = disabled || dictionaries.loading || Boolean(dictionaries.error) || stages.loading || Boolean(stages.error)
  const categories = followUpOptionsWithSnapshot(dictionaries.categories, lead.leadCategory, lead.leadCategoryLabelSnapshot)
  return <div className="follow-up-fields">
    {dictionaries.loading && <div role="status"><Spin size="small"/> 正在加载跟进选项</div>}
    {dictionaries.error && <Alert type="error" showIcon title={`跟进字典加载失败：${dictionaries.error}`} action={<Button size="small" disabled={disabled} onClick={() => void dictionaries.reload()}>重试字典</Button>}/>}
    {stages.error && <Alert type="error" showIcon title={stages.error} action={<Button size="small" disabled={disabled} onClick={() => void stages.reload()}>重试销售阶段</Button>}/>}
    {stages.loading && <div role="status"><Spin size="small"/> 正在加载销售阶段</div>}
    <div className="follow-up-field-grid">
      <Form.Item name="method" label="跟进方式" rules={[{ required: true, message: '请选择跟进方式' }]}><FollowUpChoiceGroup label="跟进方式" options={dictionaries.methods} disabled={locked}/></Form.Item>
      <Form.Item name="result" label="跟进结果" rules={[{ required: true, message: '请选择跟进结果' }]}><FollowUpChoiceGroup label="跟进结果" options={dictionaries.results} disabled={locked}/></Form.Item>
      <Form.Item name="salesStage" label="跟进后销售阶段" rules={[{ required: true, message: '请选择销售阶段' }]}><FollowUpChoiceGroup label="销售阶段" options={stages.options} disabled={locked}/></Form.Item>
      <Form.Item name="leadCategory" label="客资分类"><FollowUpChoiceGroup label="客资分类" allowClear options={categories} disabled={locked}/></Form.Item>
      <div className="follow-up-full-row">
        <div className="follow-up-hint">快捷备注 · 点击追加</div>
        <div id={notesId} className="follow-up-notes">
          {(notesExpanded ? dictionaries.quickNotes : dictionaries.quickNotes.slice(0, FOLLOW_UP_VISIBLE_OPTIONS)).map(note => <Button size="small" key={note.value} disabled={locked} onClick={() => {
            const next = appendFollowUpNote(form.getFieldValue('remark') || '', note.label)
            if (next === undefined) { setNoteError('追加后超过 2000 字，请先精简备注。'); return }
            form.setFieldValue('remark', next); setNoteError(''); onModified?.()
          }}>{note.label}</Button>)}
        </div>
        {dictionaries.quickNotes.length > FOLLOW_UP_VISIBLE_OPTIONS && <Button type="link" size="small" disabled={locked} aria-controls={notesId} aria-expanded={notesExpanded} onClick={() => setNotesExpanded(value => !value)}>{notesExpanded ? '收起' : `展开全部（共 ${dictionaries.quickNotes.length} 项）`}</Button>}
        {!dictionaries.loading && !dictionaries.error && !dictionaries.quickNotes.length && <div className="follow-up-hint">暂无快捷备注</div>}
        {noteError && <div role="alert" className="follow-up-note-error">{noteError}</div>}
        <Form.Item name="remark" label="跟进备注" rules={[{ required: true, whitespace: true, message: '请输入跟进备注' }, { max: FOLLOW_UP_REMARK_LIMIT, message: '跟进备注不能超过 2000 字' }]}><Input.TextArea rows={3} maxLength={FOLLOW_UP_REMARK_LIMIT} showCount disabled={locked} onChange={() => setNoteError('')}/></Form.Item>
      </div>
      <div>
        <Form.Item name="nextFollowUpAt" label="下次跟进时间" extra={lead.status === 'won' ? '选填，不填写则不安排下次跟进' : undefined} rules={[{ required: lead.status !== 'won', message: '请选择下次跟进时间' }, { validator: (_, value) => !value || value.isAfter(dayjs()) ? Promise.resolve() : Promise.reject(new Error('下次跟进时间必须晚于当前时间')) }]}>
          <DatePicker showTime format="YYYY-MM-DD HH:mm" disabled={locked} onChange={() => setShortcut(undefined)} disabledDate={date => date.endOf('day').isBefore(dayjs())}/>
        </Form.Item>
        <div className="follow-up-time-shortcuts">
          {FOLLOW_UP_TIME_SHORTCUTS.map(item => <Button size="small" key={item.key} disabled={locked} aria-pressed={shortcut?.key === item.key && time?.valueOf() === shortcut.time} onClick={() => {
            const next = applyFollowUpTimeShortcut(item); form.setFieldValue('nextFollowUpAt', next); setShortcut({ key: item.key, time: next.valueOf() }); onModified?.()
          }}>{item.label}</Button>)}
        </div>
      </div>
      <Form.Item label={`跟进图片${images.some(image => image.status === 'uploading') ? '（上传中）' : ''}`}><DeferredAttachmentPicker value={images} onChange={items => { onImagesChange(items); onModified?.() }} disabled={locked} accept="image/jpeg,image/png,image/webp"/></Form.Item>
    </div>
  </div>
}
