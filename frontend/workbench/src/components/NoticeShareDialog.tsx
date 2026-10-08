import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Checkbox, Input, Modal, Popconfirm, QRCode, Space, Spin, Typography } from 'antd'
import { noticeShareApi, type NoticeShare } from '../services/noticeShare'
import type { ManagedNotice } from '../services/noticeManagement'
import SafeRichText from './SafeRichText'

export default function NoticeShareDialog({ notice, onClose }: { notice: ManagedNotice; onClose: () => void }) {
  const [share, setShare] = useState<NoticeShare>()
  const [selected, setSelected] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const qr = useRef<HTMLDivElement>(null)
  const sequence = useRef(0)
  const load = useCallback(async () => {
    const current = ++sequence.current
    setBusy(true); setError(''); setShare(undefined)
    try {
      const result = await noticeShareApi.get(notice.id)
      if (current === sequence.current) { setShare(result); setSelected(result.active ? result.attachmentIds : []) }
    } catch (e) { if (current === sequence.current) setError(e instanceof Error ? e.message : '分享配置加载失败') }
    finally { if (current === sequence.current) setBusy(false) }
  }, [notice.id])
  useEffect(() => { void load(); return () => { sequence.current++ } }, [load])
  const mutate = async () => {
    if (!share || busy) return
    setBusy(true); setError(''); setFeedback('')
    try {
      if (share.active && share.version != null) {
        await noticeShareApi.close(notice.id, share.version)
        setShare({ active: false, attachmentIds: [] }); setSelected([]); setFeedback('分享已关闭，旧链接已失效')
      } else {
        const result = await noticeShareApi.open(notice.id, selected)
        setShare(result); setFeedback('分享已开启')
      }
    } catch (e) { setShare(undefined); setError(e instanceof Error ? e.message : '分享操作失败，请刷新配置') }
    finally { setBusy(false) }
  }
  const copy = async () => {
    if (!share?.url) return
    try { await navigator.clipboard.writeText(share.url); setFeedback('链接已复制') }
    catch { setFeedback('复制失败，请在链接框中选中并复制') }
  }
  const download = () => {
    const canvas = qr.current?.querySelector('canvas')
    if (!canvas) return
    const link = document.createElement('a')
    link.download = '中世健公告二维码.png'; link.href = canvas.toDataURL('image/png'); link.click()
  }
  return <Modal title="对外分享" open width={680} onCancel={() => !busy && onClose()} closable={!busy} maskClosable={!busy} footer={<Button disabled={busy} onClick={onClose}>返回公告</Button>}>
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Alert type="warning" showIcon title="获得链接的人均可查看及转发；正文内图片、视频也会公开。" description={notice.audienceType === 'TARGET' ? '此公告原为指定部门／人员可见，开启后外部访问不受原接收范围限制。' : undefined} />
      {notice.audienceType === 'TARGET' && <Typography.Text>原内部接收范围：{notice.targetDeptIds?.length || 0} 个部门，{notice.targetUserIds?.length || 0} 名指定人员{notice.recipientCount != null ? `，发布时接收人数 ${notice.recipientCount}` : ''}。</Typography.Text>}
      <Typography.Text type="secondary">关闭后不能收回已保存的内容；已签发的文件地址可能暂时有效。调整附件需关闭后重新开启，旧二维码不会恢复。</Typography.Text>
      <details><summary>预览对外正文</summary><Typography.Title level={4}>{notice.title}</Typography.Title><SafeRichText announcementTables html={notice.content || ''} /></details>
      {error && <Alert type="error" title={error} action={<Button disabled={busy} onClick={() => void load()}>刷新重试</Button>} />}
      {feedback && <Typography.Text role="status">{feedback}</Typography.Text>}
      {busy && <Spin />}
      {share && <>
        {share.active && !share.url && <Alert type="warning" title="外部阅读地址未正确配置，仍可关闭分享。请联系管理员。" />}
        <Typography.Text strong>公开附件（默认不选择）</Typography.Text>
        {notice.attachments?.length ? <Checkbox.Group value={selected} disabled={busy || share.active} onChange={values => setSelected(values as number[])}>
          <Space orientation="vertical">{notice.attachments.map(file => <Checkbox key={file.infraFileId} value={file.infraFileId}>{file.fileName}</Checkbox>)}</Space>
        </Checkbox.Group> : <Typography.Text type="secondary">本公告没有附件</Typography.Text>}
        {share.active && share.url && <>
          <Input readOnly aria-label="公开链接" value={share.url} />
          <div ref={qr}><QRCode value={share.url} type="canvas" size={200} bgColor="#ffffff" color="#000000" /></div>
          <Space wrap><Button onClick={() => void copy()}>复制链接</Button><Button onClick={download}>下载二维码</Button></Space>
        </>}
        {share.active ? <Popconfirm title="关闭分享后，原链接和二维码将失效，是否继续？" onConfirm={mutate}>
          <Button danger disabled={busy}>关闭分享</Button>
        </Popconfirm> : <Button type="primary" loading={busy} disabled={notice.publishStatus !== 'PUBLISHED'} onClick={() => void mutate()}>开启分享</Button>}
      </>}
    </Space>
  </Modal>
}
