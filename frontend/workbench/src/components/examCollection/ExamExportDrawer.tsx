import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Collapse, Drawer, Empty, Form, Input, Radio, Select, Space, Spin, Switch, Tag, Typography } from 'antd';
import { examCollectionApi, type ExamClass, type ExamExportPreview, type ExamExportRequest, type ExamExportSettings, type ExamMember, type ExamTaskDetail } from '../../services/examCollection';
import { studentInfoError } from '../../services/studentInfo';
import '../../styles/pages/exam-export.css';

// These are ZIP layout/filename protocol commands, independent of business dictionaries.
const tokens = [
  { value:'name', label:'姓名', example:'示例学员' },
  { value:'mobile', label:'手机号', example:'13800000000' },
  { value:'idCard', label:'身份证号', example:'身份证号码' },
  { value:'material', label:'资料名称', example:'身份证' },
  { value:'side', label:'证件正面或反面', example:'正面' },
  { value:'sequence', label:'同一种资料的第几张', example:'01' },
  { value:'submittedAt', label:'提交时间', example:'2026_11_08 14_51_55' },
  { value:'original', label:'上传时的文件名', example:'IMG_001' },
];
function NamingEditor({ value, onChange, materialLabel }: { value:string[]; onChange:(value:string[])=>void; materialLabel:string }) {
  const [text,setText]=useState('');
  const example=value.map(token=>token.startsWith('text:')?token.slice(5):token==='material'?materialLabel:tokens.find(item=>item.value===token)?.example).filter(Boolean).join('_');
  const options=[...tokens.map(token=>({value:token.value,label:`${token.label}（如 ${token.value==='material'?materialLabel:token.example}）`})),
    ...Array.from(new Set(value.filter(token=>token.startsWith('text:')))).map(token=>({value:token,label:token.slice(5)||'自定义文字'}))];
  return <div className="exam-export-naming">
    <Select mode="multiple" aria-label="文件名包含的信息" style={{width:'100%'}} value={value} options={options} maxCount={12}
      placeholder="选择文件名中要包含的信息" onChange={next=>{if(next.length)onChange(next)}}
      optionFilterProp="label" tagRender={({value:token,closable,onClose})=><Tag closable={closable&&value.length>1} onClose={onClose}
        onMouseDown={e=>{e.preventDefault();e.stopPropagation()}}>
        {String(token).startsWith('text:')?String(token).slice(5):tokens.find(item=>item.value===token)?.label}
      </Tag>}/>

    <Typography.Text type="secondary">按选择顺序组合，文件名示例</Typography.Text>
    <Typography.Text code style={{overflowWrap:'anywhere'}}>{example}.jpg</Typography.Text>
    <Collapse className="exam-export-disclosure" expandIconPlacement="end" ghost size="small" items={[{key:'text',label:'添加自定义文字',children:<Input.Search aria-label="自定义文件名文字" placeholder="例如：报名材料" maxLength={75} value={text} onChange={e=>setText(e.target.value)} enterButton="添加"
      onSearch={()=>{const token=`text:${text.trim()}`;if(text.trim()&&value.length<12&&!value.includes(token)){onChange([...value,token]);setText('')}}}/>}]} />
  </div>;
}
export default function ExamExportDrawer({task,onClose}:{task:ExamTaskDetail;onClose:()=>void}) {
  const {message}=App.useApp();
  const [settings,setSettings]=useState<ExamExportSettings>();
  const [classes,setClasses]=useState<ExamClass[]>([]),[members,setMembers]=useState<ExamMember[]>([]);
  const [mode,setMode]=useState<ExamExportRequest['mode']>('ARCHIVE'),[classIds,setClassIds]=useState<number[]>([]),[serviceIds,setServiceIds]=useState<number[]>([]);
  const [fieldKeys,setFieldKeys]=useState<string[]>([]),[keyword,setKeyword]=useState(''),[filled,setFilled]=useState<boolean>(),[includeAdditional,setIncludeAdditional]=useState(false);
  const [loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reload,setReload]=useState(0);
  const [memberSearch,setMemberSearch]=useState(''),[memberError,setMemberError]=useState(''),[memberLoading,setMemberLoading]=useState(false);
  const [preview,setPreview]=useState<ExamExportPreview>();
  const previewElement=useRef<HTMLElement>(null);
  const materials=task.fields.filter(field=>field.type==='images');
  useEffect(()=>{let active=true;setLoading(true);setError('');
    void Promise.all([examCollectionApi.exportSettings(task.id),examCollectionApi.classOptions(task.id)])
      .then(([saved,options])=>{if(active){setSettings(saved);setClasses(options)}})
      .catch(e=>{if(active)setError(studentInfoError(e))}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[task.id,reload]);
  useEffect(()=>{let active=true;setMemberLoading(true);setMemberError('');
    const timer=setTimeout(()=>{void examCollectionApi.members(task.id,{pageNo:1,pageSize:100,active:true,keyword:memberSearch})
      .then(result=>{if(active)setMembers(previous=>[...previous.filter(m=>serviceIds.includes(m.serviceRelationId)),...result.list.filter(m=>!previous.some(p=>serviceIds.includes(p.serviceRelationId)&&p.serviceRelationId===m.serviceRelationId))])})
      .catch(e=>{if(active)setMemberError(studentInfoError(e))}).finally(()=>{if(active)setMemberLoading(false)})},250);
    return()=>{active=false;clearTimeout(timer)};
  },[task.id,memberSearch,serviceIds]);
  useEffect(()=>setPreview(undefined),[settings,mode,classIds,serviceIds,fieldKeys,keyword,filled,includeAdditional]);
  useEffect(()=>{if(preview)previewElement.current?.scrollIntoView({behavior:'smooth',block:'start'})},[preview]);
  const request=():ExamExportRequest=>({settings:settings!,mode,classIds,serviceIds,fieldKeys,keyword,filled,includeAdditional});
  const run=async(action:()=>Promise<void>)=>{setBusy(true);setError('');try{await action()}catch(e){setError(studentInfoError(e))}finally{setBusy(false)}};
  const override=(key:string,value:string[]|undefined)=>{
    if(!settings)return; const next={...settings.overrides};if(value)next[key]=value;else delete next[key];setSettings({...settings,overrides:next});
  };
  return <Drawer title="导出考试资料" open onClose={busy?undefined:onClose} closable={!busy} maskClosable={!busy} keyboard={!busy}
    size="large" extra={<Button disabled={busy} onClick={onClose}>关闭</Button>}
    footer={<Space wrap>
      <Button disabled={loading||!settings||busy} onClick={()=>void run(async()=>{setSettings(await examCollectionApi.saveExportSettings(task.id,settings!));void message.success('已保存本任务的目录和命名设置')})}>保存任务设置</Button>
      <Button disabled={loading||!settings||busy} onClick={()=>void run(async()=>setPreview(await examCollectionApi.exportPreview(task.id,request())))}>预览导出</Button>
      <Button type="primary" disabled={!preview?.people||busy} loading={busy} onClick={()=>void run(async()=>{await examCollectionApi.createMaterialExport(task.id,{...request(),fingerprint:preview!.fingerprint});void message.success('已加入异步导出，可在异步导出页面查看状态并下载');onClose()})}>提交异步导出</Button>
    </Space>}>
    {error&&<Alert type="error" showIcon title={error} action={!settings?<Button onClick={()=>setReload(v=>v+1)}>重试</Button>:undefined}/>}
    {loading?<Spin/>:settings&&<Form className="exam-export-form" layout="vertical" disabled={busy}>
      <section className="exam-export-section" aria-label="导出范围">
      <Form.Item label="班级范围"><Select mode="multiple" placeholder="全部任务班级" value={classIds} onChange={setClassIds} options={classes.filter(c=>task.classIds.includes(c.id)).map(c=>({value:c.id,label:c.name}))}/></Form.Item>
      <Form.Item label="导出哪些资料"><Select mode="multiple" placeholder="全部本次收集材料" value={fieldKeys} onChange={setFieldKeys} options={materials.map(f=>({value:f.key,label:f.label}))}/></Form.Item>
      <Collapse className="exam-export-disclosure" expandIconPlacement="end" ghost items={[{key:'filters',label:'更多筛选'+(mode!=='ARCHIVE'||serviceIds.length||keyword||filled!==undefined||includeAdditional?'（已设置）':''),children:<>
      <Form.Item label="导出哪份资料" extra={mode==='ARCHIVE'?'包含已有报名材料及档案修订，只取本任务材料范围。':'每次提交独立分组，包含异常和已标无效记录。'}><Select value={mode} onChange={setMode} options={[{value:'ARCHIVE',label:'当前有效资料'},{value:'SUBMISSIONS',label:'原始提交'}]}/></Form.Item>

      <Form.Item label="指定学员" extra="留空导出全部筛选结果。搜索姓名或手机号，每次显示最多 100 条候选。">
        <Select mode="multiple" showSearch={{filterOption:false,onSearch:setMemberSearch}} loading={memberLoading} placeholder="搜索并勾选应收学员" value={serviceIds} onChange={setServiceIds}
          options={members.map(m=>({value:m.serviceRelationId,label:`${m.archiveName||m.studentName} · ${m.className||'未分班'}`}))}/>
        {memberError&&<Alert type="error" title={memberError} action={<Button onClick={()=>setMemberSearch(v=>v+' ')}>重试</Button>}/>}
      </Form.Item>
      <Form.Item label="资料关键词"><Input placeholder="搜索导出版本中的姓名、手机号或其他资料值" maxLength={100} value={keyword} onChange={e=>setKeyword(e.target.value)}/></Form.Item>
      <Form.Item label="本任务填写状态"><Select allowClear placeholder="全部状态" value={filled} onChange={setFilled} options={[{value:true,label:'已填写'},{value:false,label:'未填写'}]}/></Form.Item>
      <Form.Item label="包含额外关联人员" extra="原始提交模式同时包含未匹配记录。班级和指定学员筛选继续生效。"><Switch checked={includeAdditional} onChange={setIncludeAdditional}/></Form.Item>
      </>}]} />
      </section>
      <section className="exam-export-section" aria-label="文件夹设置">
      <Form.Item label="文件怎么分文件夹" extra={mode==='SUBMISSIONS'?'每次提交会单独建文件夹，下面的设置用于整理其中的文件。':undefined}>
        <Radio.Group value={settings.layout} onChange={e=>setSettings({...settings,layout:e.target.value})}>
          <Space orientation="vertical">
            <Radio value="MATERIAL">同一种资料放一起</Radio>
            <Radio value="STUDENT">同一学员的资料放一起</Radio>
            <Radio value="FLAT">所有文件放一起</Radio>
          </Space>
        </Radio.Group>
        <div className="exam-export-example"><Typography.Text type="secondary">例如 </Typography.Text><Typography.Text code style={{overflowWrap:'anywhere'}}>
          {settings.layout==='MATERIAL'?`${materials[0]?.label||'身份证'}/示例学员_${materials[0]?.label||'身份证'}.jpg`:settings.layout==='STUDENT'?`示例学员/示例学员_${materials[0]?.label||'身份证'}.jpg`:`示例学员_${materials[0]?.label||'身份证'}.jpg`}
        </Typography.Text></div>
      </Form.Item>
      <div className="exam-export-switch-row"><span>先按班级分文件夹</span><Switch aria-label="先按班级分文件夹" checked={settings.groupByClass} onChange={groupByClass=>setSettings({...settings,groupByClass})}/></div>
      </section>
      <section className="exam-export-section" aria-label="文件命名设置">
      <Form.Item label="文件怎么命名"><NamingEditor materialLabel={materials[0]?.label||'身份证'} value={settings.naming} onChange={naming=>setSettings({...settings,naming})}/></Form.Item>
      <Collapse className="exam-export-disclosure" expandIconPlacement="end" ghost items={[{key:'overrides',label:'为某种资料单独设置文件名',children:<Collapse items={materials.map(field=>({key:field.key,label:field.label,children:<Space orientation="vertical" style={{width:'100%'}}>
        <Space><Switch checked={Boolean(settings.overrides[field.key])} onChange={checked=>override(field.key,checked?[...settings.naming]:undefined)}/>单独设置命名规则</Space>
        {settings.overrides[field.key]&&<NamingEditor materialLabel={field.label} value={settings.overrides[field.key]} onChange={value=>override(field.key,value)}/>}
      </Space>}))}/> }]} />
      </section>
      {preview&&<section className="exam-export-section" ref={previewElement} aria-label="导出预览">
        <Typography.Title level={5}>{preview.people} {mode==='SUBMISSIONS'?'份提交':'人'} · {preview.files} 个附件 · {(preview.bytes/1024/1024).toFixed(2)} MB</Typography.Title>
        {preview.warnings.length>0&&<Alert type="warning" showIcon title="请核对资料范围与缺失项" description={<div style={{maxHeight:180,overflow:'auto'}}>{preview.warnings.map((warning,index)=><div key={index}>{warning}</div>)}</div>}/>}
        {preview.paths.length?<pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{preview.paths.join('\n')}</pre>:<Empty description="当前范围没有可导出的附件"/>}
        {preview.files>preview.paths.length&&<Typography.Text type="secondary">预览显示前 {preview.paths.length} 个路径，下载包含全部 {preview.files} 个附件。</Typography.Text>}
      </section>}
    </Form>}
  </Drawer>;
}
