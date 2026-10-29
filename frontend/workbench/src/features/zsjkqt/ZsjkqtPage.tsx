import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { Alert, Button, Input, InputNumber, Modal, Result, Space } from 'antd'
import BusinessTable from '../../components/BusinessTable'
import { useWorkbenchPageGuard } from '../../components/WorkbenchPageNavigation'
import { APP_ROUTES } from '../../constants'
import { createIdempotencyKey } from '../../services/idempotency'
import { ZSJKQT_ACCESS, zsjkqtApi, type ZsjkqtDraft, type ZsjkqtSnapshot, type ZsjkqtConfig } from '../../services/zsjkqt'
import type { DepartmentScheduleSettings } from './core/departmentSchedules'
import type { PersonnelProfiles } from './core/models'
import { copyDraftName, ZsjkqtDraftSession } from './draftSession'
import { App as Editor } from './ui/App'
import './styles/zsjkqt.css'

export interface ZsjkqtEditorProps {
  canReadHomeRestDays?: boolean
  initial?: ZsjkqtSnapshot
  schedules: DepartmentScheduleSettings
  profiles: PersonnelProfiles
  onSnapshot: (snapshot: ZsjkqtSnapshot) => void
  onSave: (snapshot: ZsjkqtSnapshot) => Promise<void>
  onClose: () => Promise<boolean>
  beforeImport: (hash: string, year: number, month: number) => Promise<boolean>
  onExport: (snapshot: ZsjkqtSnapshot, kind: 'report' | 'issues', filename: string) => Promise<void>
  saveSchedules: (settings: DepartmentScheduleSettings) => Promise<void>
  onActivity: (busy: boolean, unsavedSettings: boolean) => void
  toolbar: ReactNode
}
const errorText = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试'
function confirm(title: string, content: string, okText = '确定', cancelText = '取消') {
  return new Promise<boolean>(resolve => Modal.confirm({ title, content, okText, cancelText, onOk: () => resolve(true), onCancel: () => resolve(false) }))
}

export default function ZsjkqtPage({ permissions }: { permissions: string[] }) {
  const allowed = permissions.includes(ZSJKQT_ACCESS) || permissions.includes('*:*:*')
  const [rows, setRows] = useState<ZsjkqtDraft[]>([]), [total, setTotal] = useState(0)
  const [page, setPage] = useState(1), [keyword, setKeyword] = useState(''), [year, setYear] = useState<number | null>(null), [month, setMonth] = useState<number | null>(null)
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [saveError, setSaveError] = useState(''), [status, setStatus] = useState('')
  const [editorBusy, setEditorBusy] = useState(false)
  const unsavedSettings = useRef(false), blockedAutosave = useRef(false)
  const [editor, setEditor] = useState<{ key: string; initial?: ZsjkqtSnapshot } | null>(null)
  const viewport = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    const element = viewport.current
    if (!element) return
    const host = element.closest('.ant-layout-content') || element.parentElement
    const fit = () => {
      const top = Math.max(0, element.getBoundingClientRect().top)
      const bottom = Math.min(window.innerHeight, host?.getBoundingClientRect().bottom || window.innerHeight)
      // The host already excludes Workbench chrome; standalone previews use the visible viewport.
      const available = bottom > top ? bottom - top : window.innerHeight - top
      const height = `${Math.max(0, Math.floor(available))}px`
      if (element.style.height !== height) element.style.height = height
    }
    fit()
    const observer = new ResizeObserver(fit)
    if (host) observer.observe(host)
    window.addEventListener('resize', fit)
    return () => { observer.disconnect(); window.removeEventListener('resize', fit) }
  }, [!!editor])
  const [config, setConfig] = useState<ZsjkqtConfig<DepartmentScheduleSettings> | null>(null), [profiles, setProfiles] = useState<PersonnelProfiles>({})
  const session = useRef<ZsjkqtDraftSession | null>(null), timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined), generation = useRef(0), configRef = useRef(config)
  configRef.current = config
  const load = useCallback(async () => {
    const seq = ++generation.current; setLoading(true); setError('')
    try { const result = await zsjkqtApi.list({ pageNo: page, pageSize: 20, keyword: keyword || undefined, year: year ?? undefined, month: month ?? undefined }); if (seq === generation.current) { setRows(result.list); setTotal(result.total) } }
    catch (e) { if (seq === generation.current) setError(errorText(e)) } finally { if (seq === generation.current) setLoading(false) }
  }, [page, keyword, year, month])
  useEffect(() => { if (allowed && !editor) void load() }, [allowed, editor, load])
  useEffect(() => () => { clearTimeout(timer.current); generation.current++ }, [])
  const save = useCallback(async (snapshot?: ZsjkqtSnapshot) => {
    const current = session.current; if (!current?.latest && !snapshot) return
    clearTimeout(timer.current); setStatus('保存中'); setSaveError(''); blockedAutosave.current = false
    try { await current!.save(snapshot); if (session.current === current) setStatus(current!.dirty ? '有未保存修改' : '已保存') }
    catch (e) { if (session.current === current) { blockedAutosave.current = true; setSaveError(errorText(e)); setStatus('保存失败，修改已保留') } throw e }
  }, [])
  const onSnapshot = useCallback((snapshot: ZsjkqtSnapshot) => {
    const current = session.current; if (!current) return
    current.update(snapshot); clearTimeout(timer.current)
    if (current.dirty && !blockedAutosave.current) { setStatus('有未保存修改'); timer.current = setTimeout(() => { void save().catch(() => undefined) }, 900) }
  }, [save])
  const onActivity = useCallback((busy: boolean, settingsDirty: boolean) => { setEditorBusy(busy); unsavedSettings.current = settingsDirty }, [])
  const leave = useCallback(async () => {
    if (editorBusy) return false
    if (unsavedSettings.current && !await confirm('部门时段尚未保存', '离开将放弃尚未保存的部门时段修改。是否继续？', '放弃修改')) return false
    if (session.current?.dirty) { try { await save() } catch { return false } }
    return true
  }, [save, editorBusy])
  useWorkbenchPageGuard(APP_ROUTES.ZSJKQT, leave, !!editor)
  useEffect(() => {
    const guard = (e: BeforeUnloadEvent) => { if (session.current?.dirty || unsavedSettings.current) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', guard); return () => window.removeEventListener('beforeunload', guard)
  }, [])
  const open = async (row?: ZsjkqtDraft) => {
    if (!await leave()) return
    const seq = ++generation.current
    clearTimeout(timer.current)
    setLoading(true); setError('')
    try {
      const [schedules, personnel, detail] = await Promise.all([zsjkqtApi.schedules(), zsjkqtApi.profiles(), row ? zsjkqtApi.get(row.id) : Promise.resolve(undefined)])
      if (seq !== generation.current) return
      blockedAutosave.current = false; unsavedSettings.current = false; setEditorBusy(false)
      const next = new ZsjkqtDraftSession(zsjkqtApi.save, detail);session.current = next;setConfig(schedules);setProfiles(personnel.value);setSaveError('');setStatus(detail ? '已保存' : '导入后自动保存');setEditor({ key: next.clientId, initial: detail?.snapshot || undefined })
    } catch (e) { if (seq === generation.current) setError(errorText(e)) } finally { if (seq === generation.current) setLoading(false) }
  }
  const close = async () => { if (!await leave()) return false; clearTimeout(timer.current);generation.current++;setEditor(null);session.current = null;unsavedSettings.current=false;blockedAutosave.current=false;setEditorBusy(false);return false }
  const exportFile = async (snapshot: ZsjkqtSnapshot, kind: 'report' | 'issues', filename: string) => {
    await save(snapshot); const current = session.current!; if (!current.row) throw new Error('草稿尚未保存');await zsjkqtApi.export(current.row.id, current.row.version, kind, filename)
  }
  const copy = async () => {
    const current = session.current; if (!current?.latest || editorBusy) return
    const next = new ZsjkqtDraftSession(zsjkqtApi.save);next.name = copyDraftName(current.name);next.update(current.latest)
    try { await next.save();clearTimeout(timer.current);session.current = next;setEditor({ key: next.clientId, initial: next.latest });setStatus('副本已保存');setSaveError('');blockedAutosave.current=false } catch (e) { setSaveError(errorText(e)) }
  }
  const reloadDraft = async () => { const current = session.current;if(!current?.row)return;if(await confirm('重新加载草稿','重新加载会放弃当前页面尚未保存的修改，是否继续？')) {try{const row=await zsjkqtApi.get(current.row.id);clearTimeout(timer.current);session.current=new ZsjkqtDraftSession(zsjkqtApi.save,row);setEditor({key:createIdempotencyKey(),initial:row.snapshot||undefined});setSaveError('');setStatus('已加载最新草稿');blockedAutosave.current=false}catch(e){setSaveError(errorText(e))}} }
  const beforeImport = async (hash: string, y: number, m: number) => {
    let p=1
    while(true) {const result=await zsjkqtApi.list({pageNo:p,pageSize:100,year:y,month:m});const match=result.list.find(d=>d.sourceFileHash===hash&&hash);if(match){if(await confirm('发现相同源文件的草稿','选择“打开已有草稿”继续编辑，或选择“新建副本”保留独立项目。','打开已有草稿','新建副本')){const row=await zsjkqtApi.get(match.id);clearTimeout(timer.current);session.current=new ZsjkqtDraftSession(zsjkqtApi.save,row);setEditor({key:createIdempotencyKey(),initial:row.snapshot||undefined});setSaveError('');setStatus('已加载已有草稿');blockedAutosave.current=false;return false}break}if(p*100>=result.total)break;p++}
    return true
  }
  if (!allowed) return <Result status="403" title="没有中世健考勤通访问权限" />
  const toolbar = <div className="zsjkqt-savebar">{error && <span role="alert" className="zsjkqt-error">{error}</span>}<Button disabled={editorBusy || loading} onClick={() => void close()}>返回草稿箱</Button><span role="status">{status}</span><Button disabled={editorBusy || loading} onClick={() => void save().catch(() => undefined)}>保存草稿／重试</Button><Button disabled={editorBusy || loading} onClick={() => void copy()}>另存副本</Button>{saveError && <><span role="alert" className="zsjkqt-error">{saveError}</span><Button disabled={editorBusy || loading} onClick={() => void reloadDraft()}>重新加载最新草稿</Button></>}</div>
  if (editor && config) return <div ref={viewport} className="zsjkqt-viewport"><div className="zsjkqt-root"><Editor canReadHomeRestDays={permissions.includes('zsjos:business-task:query') || permissions.includes('*:*:*')} key={editor.key} initial={editor.initial} schedules={config.value} profiles={profiles} onActivity={onActivity} onSnapshot={onSnapshot} onSave={save} onClose={close} onExport={exportFile} beforeImport={beforeImport} toolbar={toolbar} saveSchedules={async settings => { const current=configRef.current!;const result=await zsjkqtApi.saveSchedules({version:current.version,value:settings});setConfig(result) }} /></div></div>
  return <section className="workspace-page">
    <Space wrap><h2>中世健考勤通 · 草稿箱</h2><Button type="primary" loading={loading} onClick={() => void open()}>新建考勤</Button></Space>
    <p>草稿由获授权人员共享；同一月份可以保留多个项目。导出后仍可继续修改。</p>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>重试</Button>} />}
    <BusinessTable<ZsjkqtDraft> tableKey="zsjkqt-drafts" columnMode="native" rowKey="id" dataSource={rows} loading={loading} error={error} onReload={load} emptyText="暂无考勤草稿，点击“新建考勤”开始" filters={<Space wrap><Input placeholder="搜索草稿名称" value={keyword} onChange={e=>{setKeyword(e.target.value);setPage(1)}} allowClear /><InputNumber placeholder="年份" min={2000} max={2099} value={year} onChange={v=>{setYear(v);setPage(1)}} /><InputNumber placeholder="月份" min={1} max={12} value={month} onChange={v=>{setMonth(v);setPage(1)}} /></Space>} pagination={{current:page,pageSize:20,total,onChange:setPage,showSizeChanger:false}} columns={[
      {title:'草稿名称',dataIndex:'name'}, {title:'年月',render:(_,d)=>`${d.year}年${d.month}月`}, {title:'源文件',dataIndex:'sourceFileName'}, {title:'确认进度',render:(_,d)=>`${d.confirmedCount}/${d.employeeCount}`},
      {title:'最后修改时间',render:(_,d)=>d.updateTime?new Date(d.updateTime).toLocaleString('zh-CN'):'—'}, {title:'最后修改人',dataIndex:'updaterName',render:(value:string)=>value||'—'},
      {title:'操作',render:(_,d)=><Space><Button type="link" onClick={()=>void open(d)}>打开</Button><Button type="link" onClick={()=>{const name=window.prompt('草稿名称',d.name);if(name?.trim())void zsjkqtApi.rename(d.id,d.version,name.trim()).then(load).catch(e=>setError(errorText(e)))}}>重命名</Button><Button danger type="link" onClick={()=>void confirm('删除草稿',`确认删除“${d.name}”？其他草稿及公共配置不受影响。`).then(async ok=>{if(ok)try{await zsjkqtApi.remove(d.id,d.version);await load()}catch(e){setError(errorText(e))}})}>删除</Button></Space>}
    ]} />
  </section>
}
