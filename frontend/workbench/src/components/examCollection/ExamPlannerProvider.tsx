import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react';
import { Alert, Button, Empty, Input, Modal, Select, Space, Spin, Tag, Typography } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import { examMemberColumns } from './ExamMemberColumns';
import { ApiError, AuthenticationError } from '../../services/api';
import { examPlannerApi, EXAM_COLLECTION_PROGRESS_EVENT, type ExamPlannerMember, type ExamPlannerState, type ExamTodo } from '../../services/examCollection';
import ExamCollectionShare from './ExamCollectionShare';
import BusinessTable from '../BusinessTable';
import { useBusinessOverlay } from '../OverlayCoordinator';
import { useExamReminderVisibility } from './useExamReminderVisibility';
import '../../styles/pages/exam-collection.css';

type PlannerContext={pending:ExamTodo[];loading:boolean;error:string;loaded:boolean;refresh:()=>Promise<void>;markSeen:(ids:number[])=>Promise<void>;openTask:(id?:number)=>void};
const Context=createContext<PlannerContext|undefined>(undefined);
export const useExamPlanner=()=>useContext(Context);
const isDenied=(e:unknown)=>e instanceof AuthenticationError||e instanceof ApiError&&[401,403,1900170002].includes(e.code);
const errorText=(e:unknown)=>e instanceof Error?e.message:'考试资料待办加载失败，请重试';
const modalStyles={container:{maxHeight:'calc(100dvh - 32px)',display:'flex',flexDirection:'column' as const},body:{overflowY:'auto' as const,minHeight:0},footer:{flexShrink:0}};

function PlannerDialog({initialTaskId,onClose}:{initialTaskId?:number;onClose:()=>void}){
  useBusinessOverlay(true);
  const planner=useExamPlanner()!;
  const [selectedId,setSelectedId]=useState<number|undefined>(initialTaskId);
  const selected=selectedId===undefined?planner.pending[0]:planner.pending.find(t=>t.collectionTaskId===selectedId);
  const [keyword,setKeyword]=useState(''),[searchText,setSearchText]=useState(''),[filled,setFilled]=useState<boolean>(),[page,setPage]=useState(1),[size,setSize]=useState(20);
  const [rows,setRows]=useState<ExamPlannerMember[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [receiptError,setReceiptError]=useState('');
  const [sort,setSort]=useState<{sortField?:string;sortOrder?:'asc'|'desc'}>({});
  useEffect(()=>{setPage(1);setKeyword('');setSearchText('');setFilled(undefined)},[selected?.todoId]);
  useEffect(()=>{let active=true;setReceiptError('');if(selected)void planner.markSeen([selected.todoId]).catch(e=>{if(active){setReceiptError(errorText(e));if(isDenied(e))void planner.refresh()}});return()=>{active=false}},[selected?.todoId,planner.markSeen,planner.refresh]);
  useEffect(()=>{let active=true;setRows([]);setTotal(0);setError('');if(!selected){setLoading(false);return}setLoading(true);
    void examPlannerApi.members(selected.collectionTaskId,{pageNo:page,pageSize:size,keyword,filled,...sort})
      .then(v=>{if(active){setRows(v.list);setTotal(v.total)}}).catch(e=>{if(active)setError(errorText(e))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[selected?.todoId,selected?.collectionTaskId,selected?.collected,selected?.expected,page,size,keyword,filled,sort,reload]);
  const columns = examMemberColumns<ExamPlannerMember>(row => row.filled).map(column => ({
    ...column, sorter: true,
    sortOrder: sort.sortField === (column.key || column.dataIndex) ? (sort.sortOrder === 'asc' ? 'ascend' as const : 'descend' as const) : null,
  }));
  return <Modal open title="我的考试资料收集待办" width={940} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles} onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>}>
    <Space orientation="vertical" className="exam-collection-stack">
      {planner.error&&<Alert type="error" title={planner.error} action={<Button onClick={()=>void planner.refresh()}>重试</Button>}/>}
      {!planner.loaded&&planner.loading?<Spin/>:!selected?<Empty description="该待办已收齐、已结束或不再需要你跟进"/>:<>
        {planner.pending.length>1&&<Select className="exam-collection-full" aria-label="切换考试收集待办" value={selected.collectionTaskId} options={planner.pending.map(t=>({value:t.collectionTaskId,label:t.title}))} onChange={setSelectedId}/>}
        <Typography.Title level={5}>{selected.title}</Typography.Title>
        <Typography.Text>本次负责班级：{selected.classes.join('、')}</Typography.Text>
        {receiptError&&<Alert type="warning" title="提醒已读状态尚未保存" description={receiptError} action={<Button size="small" onClick={()=>void planner.markSeen([selected.todoId]).then(()=>setReceiptError('')).catch(e=>setReceiptError(errorText(e)))}>重试</Button>}/>}
        <Space wrap><Tag color="processing">已收齐 {selected.collected}/{selected.expected} 人</Tag></Space>
        <ExamCollectionShare key={selected.url} url={selected.url}/>
        <BusinessTable<ExamPlannerMember> mode="full" options={{reload:false}} tableKey="exam-planner-members" rowKey="serviceRelationId" columns={columns} dataSource={rows} loading={loading} error={error}
          onChange={(_, __, sorter, extra)=>{if(extra.action!=='sort')return;const current=Array.isArray(sorter)?sorter[0]:sorter;setSort(current.order?{sortField:String(current.columnKey),sortOrder:current.order==='ascend'?'asc':'desc'}:{});setPage(1)}} onReload={()=>setReload(v=>v+1)}
          filters={<div className="exam-collection-filters"><Input.Search aria-label="搜索本人应收学员" placeholder="姓名、手机号" allowClear value={searchText} maxLength={200} onChange={e=>setSearchText(e.target.value)} onSearch={v=>{setKeyword(v);setPage(1)}}/><Select aria-label="填写状态" placeholder="全部填写状态" allowClear value={filled===undefined?undefined:String(filled)} options={[{value:'false',label:'未填写'},{value:'true',label:'已填写'}]} onChange={v=>{setFilled(v===undefined?undefined:v==='true');setPage(1)}}/></div>}
          pagination={{current:page,pageSize:size,total,showSizeChanger:true,onChange:(p,s)=>{setPage(s!==size?1:p);setSize(s)}}}/>
        <Typography.Text type="secondary">仅列出本次分配给你的学员。收齐后待办自动移除；关闭窗口不会完成任务。</Typography.Text>
      </>}
    </Space>
  </Modal>;
}

export default function ExamPlannerProvider({enabled,children}:PropsWithChildren<{enabled:boolean}>){
  const [state,setState]=useState<ExamPlannerState>(),[loading,setLoading]=useState(enabled),[error,setError]=useState('');
  const [opened,setOpened]=useState<{taskId?:number}>(),[acknowledging,setAcknowledging]=useState(false),[ackError,setAckError]=useState(''),[snoozed,setSnoozed]=useState(false);
  const [ackErrorIds,setAckErrorIds]=useState<number[]>([]);
  const stateRef=useRef<ExamPlannerState|undefined>(undefined),sequence=useRef(0),flight=useRef<Promise<void>|undefined>(undefined);
  const mounted=useRef(true),enabledRef=useRef(enabled);enabledRef.current=enabled;
  const confirmedSeen=useRef(new Set<number>());
  const refresh=useCallback(()=>{
    if(flight.current)return flight.current;
    if(!enabledRef.current||!mounted.current)return Promise.resolve();
    const current=++sequence.current;setLoading(true);
    const request:Promise<void>=examPlannerApi.state().then(received=>{
      if(current!==sequence.current)return;
      // A poll may have started before an acknowledgement committed; do not resurrect its unread IDs.
      const next={...received,unreadTodoIds:received.unreadTodoIds.filter(id=>!confirmedSeen.current.has(id))};
      const signature=(value:ExamPlannerState|undefined)=>JSON.stringify(value?.pending.map(t=>[t.todoId,t.expected,t.collected])||[]);
      const changed=signature(stateRef.current)!==signature(next);
      stateRef.current=next;setState(next);setError('');
      if(changed)window.dispatchEvent(new Event(EXAM_COLLECTION_PROGRESS_EVENT));
    }).catch(e=>{if(current===sequence.current){setError(errorText(e));if(isDenied(e)){const hadPending=Boolean(stateRef.current?.pending.length);const empty={pending:[],unreadTodoIds:[]};stateRef.current=empty;setState(empty);if(hadPending)window.dispatchEvent(new Event(EXAM_COLLECTION_PROGRESS_EVENT))}}})
      .finally(()=>{if(current===sequence.current)setLoading(false);if(flight.current===request)flight.current=undefined});
    flight.current=request;return request;
  },[enabled]);
  useEffect(()=>{
    mounted.current=true;
    if(!enabled){stateRef.current=undefined;flight.current=undefined;confirmedSeen.current.clear();setState(undefined);setError('');setLoading(false);setOpened(undefined);setSnoozed(false);return()=>{mounted.current=false;sequence.current++};}
    void refresh();const focus=()=>{if(document.visibilityState==='visible')void refresh()};
    const timer=window.setInterval(focus,30000);window.addEventListener('focus',focus);document.addEventListener('visibilitychange',focus);
    return()=>{mounted.current=false;sequence.current++;flight.current=undefined;window.clearInterval(timer);window.removeEventListener('focus',focus);document.removeEventListener('visibilitychange',focus)};
  },[enabled,refresh]);
  useEffect(()=>{if(!snoozed)return;const timer=window.setTimeout(()=>setSnoozed(false),60000);return()=>window.clearTimeout(timer)},[snoozed]);
  const unread=useMemo(()=>state?.pending.filter(t=>state.unreadTodoIds.includes(t.todoId))||[],[state]);
  const activeAckError=unread.some(t=>ackErrorIds.includes(t.todoId))?ackError:'';
  const visible=useExamReminderVisibility(enabled&&unread.length>0&&!error&&!snoozed);
  const markSeen=useCallback(async(requested:number[])=>{
    if(!mounted.current||!enabledRef.current)return;
    const ids=[...new Set(requested)].filter(id=>stateRef.current?.unreadTodoIds.includes(id)&&!confirmedSeen.current.has(id));
    if(!ids.length)return;
    await examPlannerApi.acknowledge(ids);
    if(!mounted.current||!enabledRef.current)return;
    ids.forEach(id=>confirmedSeen.current.add(id));
    const current=stateRef.current;if(current){const next={...current,unreadTodoIds:current.unreadTodoIds.filter(id=>!ids.includes(id))};stateRef.current=next;setState(next)}
  },[]);
  const dismiss=async(taskId?:number)=>{
    if(acknowledging||!mounted.current)return;
    const ids=(taskId===undefined?unread.slice(0,100):unread.filter(t=>t.collectionTaskId===taskId)).map(t=>t.todoId);
    if(!ids.length){if(taskId!==undefined)setOpened({taskId});return;}
    setAcknowledging(true);setAckError('');setAckErrorIds([]);
    try{
      await markSeen(ids);
      if(!mounted.current||!enabledRef.current)return;
      if(taskId!==undefined)setOpened({taskId});
    }catch(e){
      await refresh();if(!mounted.current)return;
      // The receipt may have committed before its response was lost; the refreshed unread set resolves that outcome.
      const stillUnread=stateRef.current?.unreadTodoIds.some(id=>ids.includes(id));
      if(stillUnread){setAckError(errorText(e));setAckErrorIds(ids);}
      else if(taskId!==undefined&&stateRef.current?.pending.some(t=>t.collectionTaskId===taskId))setOpened({taskId});
    }
    finally{if(mounted.current)setAcknowledging(false)}
  };
  const context=useMemo<PlannerContext>(()=>({pending:enabled?state?.pending||[]:[],loading,error,loaded:state!==undefined,refresh,markSeen,openTask:(taskId)=>{if(enabledRef.current&&mounted.current)setOpened({taskId})}}),[enabled,state,loading,error,refresh,markSeen]);
  return <Context.Provider value={context}>{children}
    <Modal title="考试资料收集待办" open={visible} rootClassName="exam-collection-notice-overlay" width={720} style={{maxWidth:'calc(100vw - 32px)',top:16}} styles={modalStyles}
      onCancel={()=>void dismiss()} maskClosable={false} footer={<Space>{activeAckError&&<Button disabled={acknowledging} onClick={()=>setSnoozed(true)}>稍后重试</Button>}<Button type="primary" loading={acknowledging} onClick={()=>void dismiss()}>已知晓，稍后跟进</Button></Space>}>
      <Space orientation="vertical" className="exam-collection-stack">
        <Typography.Paragraph>请通知所负责班级填写，并跟进收齐。关闭提醒不会完成任务。</Typography.Paragraph>
        {unread.length>100&&<Typography.Text type="secondary">当前显示前 100 个提醒，确认后继续显示其余提醒。</Typography.Text>}
        {activeAckError&&<Alert type="error" title={activeAckError} action={<Button onClick={()=>void dismiss()}>重试</Button>}/>}
        {unread.slice(0,100).map(todo=><section key={todo.todoId} className="exam-planner-reminder-item"><Typography.Text strong>{todo.title}</Typography.Text><p>{todo.classes.join('、')}</p><Space wrap><Tag color="processing">已收齐 {todo.collected}/{todo.expected} 人</Tag><Button disabled={acknowledging} onClick={()=>void dismiss(todo.collectionTaskId)}>查看并跟进</Button></Space><ExamCollectionShare key={todo.url} url={todo.url}/></section>)}
      </Space>
    </Modal>
    {enabled&&opened&&<PlannerDialog initialTaskId={opened.taskId} onClose={()=>setOpened(undefined)}/>}
  </Context.Provider>;
}

export function ExamPlannerNotice(){
  const planner=useExamPlanner();if(!planner)return null;
  if(planner.error)return <Alert className="exam-planner-notice" type="warning" showIcon title="考试资料待办暂不可用" description={planner.error} action={<Button size="small" onClick={()=>void planner.refresh()}>重试</Button>}/>;
  if(!planner.loaded&&planner.loading)return <div className="exam-planner-notice"><Spin size="small"/> 正在检查考试收集待办</div>;
  if(!planner.pending.length)return null;
  return <Alert className="exam-planner-notice" type="info" showIcon icon={<FileTextOutlined/>} title={`考试资料收集 · ${planner.pending.length} 个任务待跟进`}
    description="请转发统一链接并跟进收齐，待办会自动更新。" action={<Button type="primary" size="small" onClick={()=>planner.openTask()}>查看收集待办</Button>}/>;
}
