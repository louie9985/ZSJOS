import { Alert, Button, Descriptions, Space, Typography } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { weeklyDiagnosisApi, type WeeklyDiagnosisNode, type WeeklyDiagnosisPart, type WeeklyDiagnosisPreview } from '../services/mediaAccountProfile';
import { Fields, Section, type HistoryField } from './AccountReviewHistoryContent';
import { formatTimestamp } from '../services/time';

export function WeeklyDiagnosisMetrics({ node, submitted = false, extra }: {node: WeeklyDiagnosisNode; submitted?: boolean; extra?:(part:WeeklyDiagnosisPart)=>ReactNode}) {
  return <Space orientation="vertical" style={{width:'100%'}}>
    <Typography.Paragraph strong>第 {node.week} 次周诊断 · 本次包含：{node.parts.map(p=>`${p.days}天诊断`).join('、')}</Typography.Paragraph>
    <Typography.Paragraph>原定截止：{formatTimestamp(node.dueAt)}（北京时间）。各时间段开始包含、结束不包含；提前提交不改变后续排期。</Typography.Paragraph>
    {node.parts.map(part => {
      const m=part.metric;
      return <section key={part.days}>
        <Typography.Title level={5}>{part.days}天诊断 · {formatTimestamp(part.from)} 至 {formatTimestamp(part.to)}</Typography.Title>
        {m.state==='OPEN' && <Alert type="info" showIcon message={part.to>Date.now() ? '周期未结束，自动指标继续更新；人工内容保留本次提交时的判断。' : '周期已结束，等待后台结算；当前展示供核对的统计结果。'} />}
        {m.reason && <Alert type="warning" showIcon message={m.reason} />}
        <Typography.Paragraph type="secondary">数据查询时间：{formatTimestamp(m.asOf)}{m.settledAt ? `；周期已结算：${formatTimestamp(m.settledAt)}` : ''}</Typography.Paragraph>
        <Descriptions size="small" column={{xs:1,sm:2}} items={[
          ...(part.days===7 || part.days===28 ? [{key:'published',label:`${part.days}天发布条数`,children:m.publishedCount ?? '无法统计'}] : []),
          ...(part.days>7 ? [
            {key:'valid',label:`${part.days}天有效客资条数`,children:m.validLeads ?? '无法统计'},
            {key:'deals',label:submitted?'当前成交客资条数':'成交客资条数',children:m.deals ?? '无法统计'},
            {key:'rate',label:submitted?'当前客资转化率':'客资转化率',children:m.conversionRate == null ? '—（有效客资为零或无法统计）' : `${(m.conversionRate*100).toFixed(2)}%`},
          ] : []),
          ...(part.days===28 ? [{key:'likes',label:'最高点赞量',children:`${m.highestLikes ?? '未记录'}（已取得 ${m.likesCoverage ?? 0}/4 次7天记录，包含本次填写）`}] : []),
        ]} />
        {(part.days===7 || part.days===28) && <Typography.Paragraph type="secondary">{m.publicationExplanation}{m.missingPublishTimeCount ? ` 另有 ${m.missingPublishTimeCount} 条缺少预计发布时间，未纳入。` : ''}</Typography.Paragraph>}
        {part.days>7 && <Typography.Paragraph type="secondary">{m.conversionExplanation}</Typography.Paragraph>}
        {extra?.(part)}
      </section>;
    })}
  </Space>;
}

function HistoryMetrics({part, submitted = false}: {part: WeeklyDiagnosisPart; submitted?: boolean}) {
  const m = part.metric;
  const number = (value?: number) => value == null ? '无法统计' : String(value);
  const fields: HistoryField[] = [
    {label:'统计区间', value:`${formatTimestamp(part.from)} 至 ${formatTimestamp(part.to)}（北京时间，开始包含、结束不包含）`, wide:true},
    {label:'统计状态', value:{OPEN:'周期未结算',SETTLED:'周期已结算',UNAVAILABLE:'无法统计'}[m.state], optionColor:'cyan'},
    {label:'数据查询时间', value:formatTimestamp(m.asOf)},
    ...(m.settledAt ? [{label:'结算时间',value:formatTimestamp(m.settledAt)}] : []),
    ...(part.days===7 || part.days===28 ? [{label:`${part.days}天发布条数`,value:number(m.publishedCount),optionColor:'geekblue'}] : []),
    ...(part.days>7 ? [
      {label:`${part.days}天有效客资条数`,value:number(m.validLeads),optionColor:'geekblue'},
      {label:submitted?'当前成交客资条数':'成交客资条数',value:number(m.deals),optionColor:'geekblue'},
      {label:submitted?'当前客资转化率':'客资转化率',value:m.conversionRate == null ? '—（有效客资为零或无法统计）' : `${(m.conversionRate*100).toFixed(2)}%`,optionColor:'cyan'},
    ] : []),
    ...(part.days===28 ? [{label:'最高点赞量',value:`${m.highestLikes ?? '未记录'}（已取得 ${m.likesCoverage ?? 0}/4 次7天记录，包含本次填写）`,optionColor:'geekblue'}] : []),
    ...(part.days===7 || part.days===28 ? [{label:'发布统计口径',value:`${m.publicationExplanation}${m.missingPublishTimeCount ? ` 另有 ${m.missingPublishTimeCount} 条缺少预计发布时间，未纳入。` : ''}`,wide:true}] : []),
    ...(part.days>7 ? [{label:'转化统计口径',value:m.conversionExplanation,wide:true}] : []),
  ];
  return <>
    {m.state==='OPEN' && <Alert type="info" showIcon message={part.to>Date.now() ? '周期未结束，自动指标继续更新；人工内容保留本次提交时的判断。' : '周期已结束，等待后台结算；当前展示供核对的统计结果。'} />}
    {m.reason && <Alert type="warning" showIcon message={m.reason} />}
    <Fields fields={fields} />
  </>;
}

export function WeeklyDiagnosisHistory({accountId,entryId,snapshot,fieldKey}: {accountId:number;entryId:number;snapshot:WeeklyDiagnosisNode;fieldKey?:string}) {
  const [data,setData]=useState<WeeklyDiagnosisPreview>();
  const [error,setError]=useState('');const [attempt,setAttempt]=useState(0);
  useEffect(()=>{let active=true;setData(undefined);setError('');
    void weeklyDiagnosisApi.history(accountId,entryId).then(r=>{if(active)setData(r);}).catch(e=>{if(active)setError(e instanceof Error?e.message:'指标加载失败');});
    const timer=window.setInterval(()=>setAttempt(x=>x+1),60000);
    return()=>{active=false;window.clearInterval(timer);};
  },[accountId,entryId,attempt]);
  const template = fieldKey==='adjustment_28d' ? 'diagnosis_28d' : fieldKey;
  const parts = snapshot.parts.filter(part=>!template || part.templateType===template);
  return <>
    <Fields fields={[
      {label:'周诊断节点',value:`第 ${snapshot.week} 次`},
      {label:'原定截止',value:`${formatTimestamp(snapshot.dueAt)}（北京时间）`},
    ]} />
    {parts.map(part => {
      const current=data?.node.parts.find(p=>p.templateType===part.templateType && p.cycle===part.cycle);
      return <Section key={part.days} title={`${part.days}天周期统计 · 第 ${part.cycle} 轮`}>
        <Section title="提交时指标快照"><HistoryMetrics part={part} /></Section>
        <Section title="周期结算与当前成交进展">
          {error ? <Alert type="error" message={error} action={<Button onClick={()=>setAttempt(x=>x+1)}>重试</Button>} />
            : data ? current ? <HistoryMetrics part={current} submitted /> : <Alert type="warning" message="未返回本周期指标，请刷新重试" />
              : <Typography.Paragraph>正在加载当前指标…</Typography.Paragraph>}
        </Section>
      </Section>;
    })}
    <Button onClick={()=>setAttempt(x=>x+1)}>刷新当前指标</Button>
  </>;
}
