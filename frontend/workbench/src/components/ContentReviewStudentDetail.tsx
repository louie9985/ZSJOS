import { Alert, Button, Empty, Skeleton, Space, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { contentReviewStudentApi as reviewApi, type ReviewStudent } from '../services/contentReviewStudent'
import type { MyStudent } from '../services/api'
import StudentDetail from './StudentDetail'
import AccountMaintenancePanel from './AccountMaintenancePanel'
import AccountPositioningHistory from './AccountPositioningHistory'
import ServicePositioningCard from './ServicePositioningCard'
import PositioningInterviewDialog from './PositioningInterviewDialog'
import { MediaStudentReviewContext } from './MediaStudentReviewContext'
import { useWorkbenchPageNavigation } from './WorkbenchPageNavigation'
import { APP_ROUTES } from '../constants'

/** Coordinates restricted transport; the existing student components own presentation. */
export default function ContentReviewStudentDetail({ batchId, personId, initialAccountId }: {
  batchId: number; personId: number; initialAccountId?: number
}) {
  const navigation = useWorkbenchPageNavigation()
  const [student, setStudent] = useState<ReviewStudent>()
  const [activeTab, setActiveTab] = useState(initialAccountId ? `account-${initialAccountId}` : 'overview')
  const [loading, setLoading] = useState(true), [error, setError] = useState(''), [revision, setRevision] = useState(0)
  const [interview, setInterview] = useState(false)
  const generation = useRef(0)
  const load = useCallback(async () => {
    const run = ++generation.current
    setLoading(true); setError(''); setStudent(undefined); setInterview(false)
    try {
      if (![batchId, personId].every(id => Number.isSafeInteger(id) && id > 0)) throw new Error('审批上下文无效，请从审批详情重新进入')
      const value = await reviewApi.get(batchId,personId)
      if (initialAccountId && !value.accounts.some(a=>a.id === initialAccountId)) throw new Error('该账号不在本次审批范围内，请从审批详情重新进入')
      if (run === generation.current) { setStudent(value); setRevision(n=>n+1) }
    } catch (e) { if(run === generation.current) setError(e instanceof Error ? e.message : '学员资料读取失败') }
    finally { if(run === generation.current) setLoading(false) }
  }, [batchId,personId,initialAccountId])
  useEffect(()=>{ void load(); return ()=>{generation.current++} },[load])
  useEffect(()=>{const focus=()=>{void load()};window.addEventListener('focus',focus);return ()=>window.removeEventListener('focus',focus)},[load])
  const selected = student?.accounts.find(a=>`account-${a.id}`===activeTab) || student?.accounts.find(a=>a.id===initialAccountId) || student?.accounts[0]
  const person: MyStudent | undefined = student ? {personId:student.personId,personNo:student.personNo,name:student.name,mobile:student.mobile,wechatId:student.wechatId,services:[]} : undefined
  const profileContent = student && <section className="lead-card student-overview-profile"><Typography.Title level={5}>学员资料</Typography.Title><dl className="student-overview-fields">
    {[['学员编号',student.personNo],['姓名',student.name],['联系方式',student.mobile],['微信',student.wechatId]].map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value || '未填写'}</dd></div>)}
  </dl></section>
  const overview = selected?.serviceRelationId ? <ServicePositioningCard serviceRelationId={selected.serviceRelationId} canQuery refresh={revision} onEdit={()=>undefined}
    statusContent={<Typography.Text type="secondary">本次内容审批只读查看</Typography.Text>} profileContent={profileContent}
    interviewContent={<section className="student-overview-interview"><Typography.Text strong>定位访谈</Typography.Text><Button onClick={()=>setInterview(true)}>查看定位访谈及材料</Button></section>} />
    : <div className="student-overview-grid"><section className="lead-card student-overview-main"><Empty description="暂无关联课程服务" /></section><aside className="student-overview-aside">{profileContent}</aside></div>
  return <section className="workspace-page media-students-page">
    <Space orientation="vertical" style={{width:'100%'}}>
      <Alert type="info" showIcon message="本次内容审批只读查看" description="仅展示本次相关账号及资料。整轮审批结束后，额外查看权限失效。" />
      <Space><Button onClick={()=>void navigation?.open(`${APP_ROUTES.CONTENT_REVIEW}?batchId=${batchId}`)}>返回审批</Button><Button loading={loading} onClick={()=>void load()}>刷新资料</Button></Space>
    </Space>
    {loading ? <Skeleton active /> : error ? <Alert type="error" showIcon message={error} action={<Button onClick={()=>void load()}>重试</Button>} /> : student && person && selected ?
      <MediaStudentReviewContext.Provider value={{batchId,accountId:selected.id}}>
        <StudentDetail student={person} overviewOnly activeTab={activeTab} onTabChange={setActiveTab} overviewContent={overview}
          extraTabs={student.accounts.map(account=>({key:`account-${account.id}`,label:`${account.nickname || account.accountNo} · ${account.platformLabel || '平台未记录'}`,
            children:<MediaStudentReviewContext.Provider value={{batchId,accountId:account.id}}><div className="media-account-workspace-shell"><div className="media-account-workspace">
              <AccountMaintenancePanel key={`${revision}-${account.id}`} account={{...account,availableActions:[]}} student={person}
                serviceRelationId={account.serviceRelationId} canQuery canQueryPositioning canReadInterview canMaintain={false} onSaved={load} />
              <section className="account-history-full" aria-label="历史定位与采访记录"><Typography.Title level={5}>历史定位与采访记录</Typography.Title>
                <AccountPositioningHistory key={`${revision}-${account.id}`} relationId={account.serviceRelationId} canReadInterview />
              </section>
            </div></div></MediaStudentReviewContext.Provider>}))} />
        {interview && selected.serviceRelationId && <PositioningInterviewDialog forceReadOnly relationId={selected.serviceRelationId} onClose={()=>setInterview(false)} onChanged={()=>setInterview(false)} />}
      </MediaStudentReviewContext.Provider> : <Empty />}
  </section>
}
