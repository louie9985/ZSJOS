import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Space, Spin, Tag, Typography } from 'antd'
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { studentInfoApi, studentInfoError, studentInfoStatus, type StudentInfoDetail, type StudentInfoField, type StudentInfoImage } from '../services/studentInfo'
import { formatTimestamp } from '../services/time'
import AttachmentCard, { type AttachmentResource } from './AttachmentCard'
import '../styles/components/student-info-panel.css'

const groupTitles: Record<string, string> = {
  basic: '基本信息', education: '学历与报考', career: '职业与学习目的', materials: '证明材料',
}

export default function StudentInfoPanel({ caseId }: { caseId: number }) {
  return <StudentInfoPanelContent key={caseId} caseId={caseId} />
}

function StudentInfoPanelContent({ caseId }: { caseId: number }) {
  const [data, setData] = useState<StudentInfoDetail>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true), [exporting, setExporting] = useState(false)
  const generation = useRef(0)

  const load = async () => {
    const current = ++generation.current
    setData(undefined); setBusy(true); setError('')
    try {
      const detail = await studentInfoApi.detail(caseId)
      if (current !== generation.current) return
      // The server owns sensitive-read access; render once the authorized projection is complete.
      if (detail.status === 'SUBMITTED' && detail.canReadSensitive) {
        const complete = await studentInfoApi.detail(caseId, true)
        if (current !== generation.current) return
        if (!complete.canReadSensitive) throw new Error('资料查看权限已变化，请刷新重试')
        setData(complete)
      } else setData(detail)
    } catch (cause) {
      if (current === generation.current) {
        setError(studentInfoError(cause))
        setData(undefined)
      }
    } finally {
      if (current === generation.current) setBusy(false)
    }
  }
  useEffect(() => {
    void load()
    return () => { generation.current++ }
  }, [caseId])

  const attachment = (image: StudentInfoImage): AttachmentResource => {
    const current = generation.current
    const metadata = { name: image.name, type: image.contentType, size: image.size }
    const loadContent = async (): Promise<AttachmentResource> => {
      if (current !== generation.current) throw new Error('资料已刷新或关闭，请重新打开附件')
      const url = await studentInfoApi.image(caseId, image)
      if (current !== generation.current) {
        URL.revokeObjectURL(url)
        throw new Error('资料已刷新或关闭，请重新打开附件')
      }
      return { ...metadata, url, loadContent }
    }
    return { ...metadata, loadContent }
  }
  const download = async () => {
    const current = generation.current
    setExporting(true); setError('')
    try { await studentInfoApi.export(caseId) }
    catch (cause) { if (current === generation.current) setError(studentInfoError(cause)) }
    finally { setExporting(false) }
  }

  const groups = new Map<string, StudentInfoField[]>()
  for (const field of [...(data?.fields || [])].sort((a, b) => a.sort - b.sort)) {
    groups.set(field.group, [...(groups.get(field.group) || []), field])
  }
  return <section className="student-info-panel">
    <div className="student-info-toolbar">
      {data && <Tag color={data.status === 'SUBMITTED' ? 'success' : undefined}>{studentInfoStatus[data.status] || data.status}</Tag>}
      <Button icon={<ReloadOutlined />} loading={busy} onClick={() => void load()}>刷新</Button>
      {data?.status === 'SUBMITTED' && data.canExport && <Button icon={<DownloadOutlined />} loading={exporting} disabled={busy} onClick={() => void download()}>导出</Button>}
    </div>
    {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    {busy ? <div className="student-info-loading" role="status"><Space><Spin /><span>正在加载报名资料…</span></Space></div>
      : data?.status !== 'SUBMITTED' ? !error && <Empty description="暂无已提交的学员信息" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        : <div className="student-info-layout">
          <div className="student-info-main">{[...groups].map(([group, fields]) => <section className="student-info-section" key={group}>
            <h4>{groupTitles[group] || '其他资料'}</h4>
            <dl className="student-info-fields">{fields.map(field => {
              const value = data.values[field.key]
              const images = data.images.filter(image => image.fieldKey === field.key)
              const wide = field.type === 'textarea' || field.type === 'images' || (value?.length || 0) > 120
              return <div key={field.key} className={`student-info-field${wide ? ' is-wide' : ''}`}>
                <dt>{field.label}</dt>
                <dd>{field.type === 'images' ? images.length ? <>
                  <div className="student-info-attachments">{images.map(image => data.canReadSensitive
                    ? <AttachmentCard key={`${generation.current}:${image.id}`} name={image.name} load={async () => attachment(image)} />
                    : <div className="student-info-attachment-name" key={image.id}>{image.name}</div>)}</div>
                  {!data.canReadSensitive && <Typography.Text type="secondary">暂无材料附件查看权限</Typography.Text>}
                </> : <Typography.Text type="secondary">未提交附件</Typography.Text>
                  : value == null || value === '' ? <Typography.Text type="secondary">未填写</Typography.Text>
                    : field.type === 'dict' || field.type === 'area' ? <Tag color="cyan" className="student-info-option">{value}</Tag> : value}
                  {field.note && <div className="student-info-field-note">{field.note}</div>}
                </dd>
              </div>
            })}</dl>
          </section>)}</div>
          <aside className="student-info-aside" aria-label="资料信息">
            <section className="student-info-section"><h4>资料信息</h4>
              <dl className="student-info-fields">
                <div className="student-info-field"><dt>提交时间</dt><dd>{formatTimestamp(data.submittedAt)}<div className="student-info-field-note">北京时间</div></dd></div>
                <div className="student-info-field"><dt>表单版本</dt><dd>{data.configVersion == null ? '未记录' : `V${data.configVersion}`}</dd></div>
              </dl>
            </section>
          </aside>
        </div>}
  </section>
}
